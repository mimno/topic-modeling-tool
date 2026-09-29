package cc.mallet.topics.gui;

import cc.mallet.topics.gui.util.CsvWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writes spreadsheet-friendly CSV files describing a trained model. File
 * names and columns match earlier versions of the tool so existing
 * workflows keep working.
 */
public final class CsvReport {

    public static final String DIR = "output_csv";
    public static final String TOPIC_WORDS = "topic-words.csv";
    public static final String TOPICS_IN_DOCS = "topics-in-docs.csv";
    public static final String TOPICS_METADATA = "topics-metadata.csv";
    public static final String DOCS_IN_TOPICS = "docs-in-topics.csv";

    /** Maximum number of documents listed per topic in docs-in-topics.csv. */
    static final int DOCS_PER_TOPIC = 500;

    private CsvReport() {}

    public static void write(Path outputDir, Corpus corpus, TopicModelResults results,
                             TrainingOptions options) throws IOException {
        Path dir = Files.createDirectories(outputDir.resolve(DIR));
        String delim = TrainingOptions.unescapeDelimiter(options.outputDelimiter);
        List<Corpus.Document> docs = corpus.getDocuments();

        try (CsvWriter out = new CsvWriter(dir.resolve(TOPIC_WORDS), delim)) {
            out.writeRow(new String[] {"Topic Id", "Top Words..."});
            for (int t = 0; t < results.numTopics; t++) {
                out.writeRow(new String[] {Integer.toString(t), results.topicLabel(t, options.numTopWords)});
            }
        }

        try (CsvWriter out = new CsvWriter(dir.resolve(TOPICS_IN_DOCS), delim)) {
            out.writeRow(new String[] {"docId", "filename", "toptopics..."});
            for (Corpus.Document doc : docs) {
                List<String> row = new ArrayList<>(List.of(Integer.toString(doc.id()), doc.name()));
                for (int t : results.topicsByProportion(doc.id())) {
                    row.add(Integer.toString(t));
                    row.add(format(results.docTopicProportions[doc.id()][t]));
                }
                out.writeCellRow(row);
            }
        }

        try (CsvWriter out = new CsvWriter(dir.resolve(TOPICS_METADATA), delim)) {
            List<String> header = new ArrayList<>(List.of("docId", "filename"));
            header.addAll(corpus.getMetadataHeader());
            for (int t = 0; t < results.numTopics; t++) {
                header.add(t + " " + results.topicLabel(t, 3));
            }
            out.writeCellRow(header);

            for (Corpus.Document doc : docs) {
                List<String> row = new ArrayList<>(List.of(Integer.toString(doc.id()), doc.name()));
                row.addAll(doc.metadata());
                for (double p : results.docTopicProportions[doc.id()]) {
                    row.add(format(p));
                }
                out.writeCellRow(row);
            }
        }

        try (CsvWriter out = new CsvWriter(dir.resolve(DOCS_IN_TOPICS), delim)) {
            out.writeRow(new String[] {"topicId", "rank", "docId", "filename"});
            for (int t = 0; t < results.numTopics; t++) {
                int rank = 0;
                for (TopicModelResults.DocumentScore score : results.topDocuments(t, DOCS_PER_TOPIC)) {
                    out.writeRow(new String[] {Integer.toString(t), Integer.toString(rank++),
                            Integer.toString(score.doc()), docs.get(score.doc()).name()});
                }
            }
        }
    }

    static String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
