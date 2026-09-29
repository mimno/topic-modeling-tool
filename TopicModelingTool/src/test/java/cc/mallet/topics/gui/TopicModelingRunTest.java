package cc.mallet.topics.gui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopicModelingRunTest {

    static final Path SUBSET = Path.of("src/test/resources/data/subset");
    static final Path METADATA = Path.of("src/test/resources/data/dos-bulletin-1953-1954-metadata.csv");

    @TempDir
    Path output;

    private TrainingOptions options() {
        TrainingOptions options = new TrainingOptions();
        options.inputDir = SUBSET;
        options.outputDir = output;
        options.numTopics = 5;
        options.numIterations = 50;
        options.numThreads = 2;
        options.segmentWords = 0;
        options.saveMalletFiles = true;
        return options;
    }

    @Test
    void writesAllOutputs() throws Exception {
        List<Integer> iterations = new ArrayList<>();
        TopicModelingRun.Result result = TopicModelingRun.run(options(), new TopicModelingRun.Listener() {
            @Override public void iteration(int iteration, int total) { iterations.add(iteration); }
        });

        Path dir = result.outputDir();
        assertTrue(dir.getFileName().toString().startsWith("subset-"), "timestamped folder");
        assertEquals(List.of(10, 20, 30, 40, 50), iterations);

        Path csv = dir.resolve(CsvReport.DIR);
        assertEquals(1 + 5, Files.readAllLines(csv.resolve(CsvReport.TOPIC_WORDS)).size());
        assertEquals(1 + 100, Files.readAllLines(csv.resolve(CsvReport.TOPICS_IN_DOCS)).size());
        assertEquals(1 + 100, Files.readAllLines(csv.resolve(CsvReport.TOPICS_METADATA)).size());
        assertTrue(Files.readAllLines(csv.resolve(CsvReport.DOCS_IN_TOPICS)).size() > 5);

        assertEquals(dir.resolve(HtmlReport.DIR).resolve(HtmlReport.INDEX), result.htmlIndex());
        assertTrue(Files.exists(result.htmlIndex()));
        assertTrue(Files.exists(dir.resolve(HtmlReport.DIR).resolve("topics/topic-4.html")));
        assertTrue(Files.exists(dir.resolve(HtmlReport.DIR).resolve("docs/doc-99.html")));

        Path mallet = dir.resolve(TopicModelingRun.MALLET_DIR);
        for (String f : List.of("topic-input.mallet", "topic-keys.txt", "doc-topics.txt",
                "words-topics-counts.txt", "output-state.gz", "model.mallet")) {
            assertTrue(Files.size(mallet.resolve(f)) > 0, f);
        }
    }

    @Test
    void proportionsSumToOneAndMetadataIsJoined() throws Exception {
        TrainingOptions options = options();
        options.metadataFile = METADATA;
        options.segmentWords = 500;
        options.generateHtml = false;
        options.saveMalletFiles = false;
        options.timestampOutput = false;

        TopicModelingRun.Result result = TopicModelingRun.run(options, new TopicModelingRun.Listener() {});
        assertEquals(output, result.outputDir());
        assertFalse(Files.exists(output.resolve(HtmlReport.DIR)));

        List<String> lines = Files.readAllLines(output.resolve(CsvReport.DIR).resolve(CsvReport.TOPICS_METADATA));
        String[] header = lines.get(0).split(",");
        assertEquals("filename", header[2]);
        assertEquals("Word Count", header[6]);
        assertTrue(lines.size() > 101, "files were split into segments");

        String[] row = lines.get(1).split(",");
        assertTrue(row[1].matches("dos-bulletin-1953-1954-\\d+(-\\d+)?\\.txt"), row[1]);
        assertTrue(lines.stream().anyMatch(l -> l.contains("-2.txt")), "long files get numbered chunks");
        assertEquals("fakedata1", row[3]);
        double sum = 0;
        for (int i = 7; i < row.length; i++) {
            sum += Double.parseDouble(row[i]);
        }
        assertEquals(1.0, sum, 1e-4);
    }
}
