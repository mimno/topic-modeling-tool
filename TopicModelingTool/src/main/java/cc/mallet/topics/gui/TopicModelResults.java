package cc.mallet.topics.gui;

import cc.mallet.topics.ParallelTopicModel;
import cc.mallet.topics.TopicAssignment;
import cc.mallet.types.Alphabet;
import cc.mallet.types.IDSorter;
import cc.mallet.types.LabelSequence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.IntStream;

/**
 * The numbers every report needs, extracted once from a trained model:
 * top words per topic, topic counts and proportions per document, and
 * which topics tend to appear in the same documents.
 */
public class TopicModelResults {

    /** A word and the number of tokens of it assigned to a topic. */
    public record WordCount(String word, int count) {}

    /** A document's share of a topic, used to rank documents within a topic. */
    public record DocumentScore(int doc, int count, double proportion) {}

    /** A pair of topics and how strongly they co-occur (pointwise mutual information). */
    public record TopicLink(int topic, double pmi) {}

    /** Documents must have at least this share of a topic to count as "containing" it. */
    static final double COOCCURRENCE_THRESHOLD = 0.1;

    public final int numTopics;
    public final int numDocs;
    public final List<List<WordCount>> topWords;
    public final int[][] docTopicCounts;
    public final double[][] docTopicProportions;
    public final int[] docLengths;
    public final int[] tokensPerTopic;
    public final double[] alpha;
    public final double[][] topicPmi;

    public TopicModelResults(ParallelTopicModel model, int numTopWords) {
        numTopics = model.getNumTopics();
        List<TopicAssignment> data = model.getData();
        numDocs = data.size();
        alpha = model.alpha.clone();

        topWords = extractTopWords(model, numTopWords);

        docTopicCounts = new int[numDocs][numTopics];
        docTopicProportions = new double[numDocs][];
        docLengths = new int[numDocs];
        tokensPerTopic = new int[numTopics];
        for (int d = 0; d < numDocs; d++) {
            LabelSequence topics = data.get(d).topicSequence;
            docLengths[d] = topics.getLength();
            for (int position = 0; position < topics.getLength(); position++) {
                int topic = topics.getIndexAtPosition(position);
                docTopicCounts[d][topic]++;
                tokensPerTopic[topic]++;
            }
            docTopicProportions[d] = model.getTopicProbabilities(d);
        }

        topicPmi = computeTopicPmi();
    }

    private static List<List<WordCount>> extractTopWords(ParallelTopicModel model, int n) {
        Alphabet alphabet = model.getAlphabet();
        List<List<WordCount>> result = new ArrayList<>();
        for (TreeSet<IDSorter> sorted : model.getSortedWords()) {
            List<WordCount> words = new ArrayList<>();
            for (IDSorter entry : sorted) {
                if (words.size() >= n) {
                    break;
                }
                words.add(new WordCount((String) alphabet.lookupObject(entry.getID()),
                        (int) entry.getWeight()));
            }
            result.add(words);
        }
        return result;
    }

    /**
     * Pointwise mutual information between each pair of topics, based on how
     * often both appear in the same document, as in jsLDA's topic
     * correlation view. Positive values mean "more often than chance".
     */
    private double[][] computeTopicPmi() {
        int[] docsWithTopic = new int[numTopics];
        int[][] docsWithBoth = new int[numTopics][numTopics];
        int[] present = new int[numTopics];

        for (int d = 0; d < numDocs; d++) {
            int n = 0;
            for (int t = 0; t < numTopics; t++) {
                if (docLengths[d] > 0 && docTopicCounts[d][t] >= COOCCURRENCE_THRESHOLD * docLengths[d]) {
                    present[n++] = t;
                    docsWithTopic[t]++;
                }
            }
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    docsWithBoth[present[i]][present[j]]++;
                }
            }
        }

        double[][] pmi = new double[numTopics][numTopics];
        for (int a = 0; a < numTopics; a++) {
            for (int b = 0; b < numTopics; b++) {
                if (a == b || docsWithBoth[a][b] == 0) {
                    pmi[a][b] = Double.NEGATIVE_INFINITY;
                } else {
                    pmi[a][b] = Math.log((double) numDocs * docsWithBoth[a][b]
                            / ((double) docsWithTopic[a] * docsWithTopic[b]));
                }
            }
        }
        return pmi;
    }

    /** The top words of a topic joined with spaces. */
    public String topicLabel(int topic, int words) {
        return String.join(" ", topWords.get(topic).stream()
                .limit(words).map(WordCount::word).toList());
    }

    /** Fraction of all tokens in the corpus assigned to each topic. */
    public double topicShare(int topic) {
        long total = Arrays.stream(tokensPerTopic).sum();
        return total == 0 ? 0 : (double) tokensPerTopic[topic] / total;
    }

    /** Documents ranked by the number of tokens they assign to {@code topic}. */
    public List<DocumentScore> topDocuments(int topic, int limit) {
        return IntStream.range(0, numDocs)
                .filter(d -> docTopicCounts[d][topic] > 0)
                .mapToObj(d -> new DocumentScore(d, docTopicCounts[d][topic], docTopicProportions[d][topic]))
                .sorted(Comparator.comparingInt(DocumentScore::count).reversed()
                        .thenComparing(Comparator.comparingDouble(DocumentScore::proportion).reversed()))
                .limit(limit)
                .toList();
    }

    /** A document's topics, largest proportion first. */
    public List<Integer> topicsByProportion(int doc) {
        double[] p = docTopicProportions[doc];
        return IntStream.range(0, numTopics).boxed()
                .sorted(Comparator.comparingDouble((Integer t) -> p[t]).reversed())
                .toList();
    }

    /** Topics that co-occur with {@code topic} more often than chance. */
    public List<TopicLink> relatedTopics(int topic, int limit) {
        return IntStream.range(0, numTopics)
                .filter(t -> t != topic && topicPmi[topic][t] > 0)
                .mapToObj(t -> new TopicLink(t, topicPmi[topic][t]))
                .sorted(Comparator.comparingDouble(TopicLink::pmi).reversed())
                .limit(limit)
                .toList();
    }
}
