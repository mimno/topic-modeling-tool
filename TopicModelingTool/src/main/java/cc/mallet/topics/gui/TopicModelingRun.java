package cc.mallet.topics.gui;

import cc.mallet.topics.ParallelTopicModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.IntConsumer;
import java.util.logging.Logger;

/**
 * One complete run of the tool, independent of the GUI: read the corpus,
 * train a model, and write CSV, HTML and (optionally) raw Mallet output.
 */
public final class TopicModelingRun {

    private static final Logger log = Logger.getLogger(TopicModelingRun.class.getName());

    public static final String MALLET_DIR = "output_mallet";

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

    /** Where the results of a finished run were written. */
    public record Result(Path outputDir, Path htmlIndex) {}

    /** Receives progress updates; all methods are called on the worker thread. */
    public interface Listener {
        default void status(String message) {}
        default void iteration(int iteration, int total) {}
    }

    private TopicModelingRun() {}

    public static Result run(TrainingOptions options, Listener listener) throws IOException {
        long start = System.currentTimeMillis();
        Path outputDir = resolveOutputDir(options);
        Files.createDirectories(outputDir);

        listener.status("Reading files…");
        log.info("Reading files from " + options.inputDir);
        Corpus corpus = Corpus.load(options);

        listener.status("Training…");
        log.info("Training " + options.numTopics + " topics for " + options.numIterations + " iterations.");
        IntConsumer progress = i -> listener.iteration(i, options.numIterations);
        ParallelTopicModel model = TopicModelTrainer.train(corpus, options, progress);

        listener.status("Writing results…");
        TopicModelResults results = new TopicModelResults(model, options.numTopWords);
        CsvReport.write(outputDir, corpus, results, options);
        Path htmlIndex = options.generateHtml ? HtmlReport.write(outputDir, corpus, results, options) : null;
        if (options.saveMalletFiles) {
            writeMalletFiles(outputDir, corpus, model, options);
        }

        double seconds = (System.currentTimeMillis() - start) / 1000.0;
        log.info(String.format("Finished in %.1f seconds. Results are in %s", seconds, outputDir));
        return new Result(outputDir, htmlIndex);
    }

    /**
     * Command-line mode: {@code INPUT_FOLDER OUTPUT_FOLDER [NUM_TOPICS]}, with
     * all other settings at their defaults. Prints the results folder and
     * returns a process exit code.
     */
    static int runBatch(String[] args) {
        if (args.length < 2 || args.length > 3) {
            System.err.println("Usage: --batch INPUT_FOLDER OUTPUT_FOLDER [NUM_TOPICS]");
            return 2;
        }
        TrainingOptions options = new TrainingOptions();
        options.inputDir = Path.of(args[0]);
        options.outputDir = Path.of(args[1]);
        try {
            if (args.length == 3) {
                options.numTopics = Integer.parseInt(args[2]);
            }
            Result result = run(options, new Listener() {});
            System.out.println(result.outputDir());
            return 0;
        } catch (NumberFormatException e) {
            System.err.println("NUM_TOPICS must be a number: " + args[2]);
            return 2;
        } catch (IOException | RuntimeException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    /** The chosen output folder, or a new timestamped subfolder of it. */
    static Path resolveOutputDir(TrainingOptions options) {
        if (!options.timestampOutput) {
            return options.outputDir;
        }
        String corpusName = options.inputDir.getFileName().toString();
        return options.outputDir.resolve(corpusName + "-" + LocalDateTime.now().format(TIMESTAMP));
    }

    /** Raw Mallet files, for people who want to continue in Mallet itself. */
    private static void writeMalletFiles(Path outputDir, Corpus corpus, ParallelTopicModel model,
                                         TrainingOptions options) throws IOException {
        Path dir = Files.createDirectories(outputDir.resolve(MALLET_DIR));
        corpus.getInstances().save(dir.resolve("topic-input.mallet").toFile());
        model.printTopWords(dir.resolve("topic-keys.txt").toFile(), options.numTopWords, false);
        model.printDocumentTopics(dir.resolve("doc-topics.txt").toFile());
        model.printTypeTopicCounts(dir.resolve("words-topics-counts.txt").toFile());
        model.printState(dir.resolve("output-state.gz").toFile());
        model.write(dir.resolve("model.mallet").toFile());
    }
}
