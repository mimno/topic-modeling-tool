package cc.mallet.topics.gui;

import java.nio.file.Path;

/**
 * Every setting that controls a single run of the tool. The GUI fills one
 * of these in; everything downstream (corpus loading, training, output)
 * reads from it and never touches Swing widgets.
 */
public class TrainingOptions {

    // Basic settings
    public Path inputDir;
    public Path outputDir;
    public int numTopics = 10;

    // Input
    public Path metadataFile;
    public Path stoplistFile;
    public boolean removeDefaultStopwords = true;
    public boolean preserveCase = false;
    public String tokenRegex = "\\p{L}[\\p{L}\\p{P}]*\\p{L}";
    public int segmentWords = 500;        // 0 means "do not split files"
    public String metadataDelimiter = ",";

    // Training
    public int numIterations = 400;
    public int numThreads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
    public int optimizeInterval = 10;
    public double alphaSum = 50.0;
    public double beta = 0.01;
    public int randomSeed = 0;
    public int showTopicsInterval = 100;

    // Output
    public int numTopWords = 20;
    public String outputDelimiter = ",";
    public boolean generateHtml = true;
    public boolean saveMalletFiles = false;
    public boolean timestampOutput = true;

    /** Parse delimiters typed as "\t" in the GUI into a real tab character. */
    public static String unescapeDelimiter(String delimiter) {
        return delimiter.replace("\\t", "\t");
    }

    /** Render a delimiter for display in a text field. */
    public static String escapeDelimiter(String delimiter) {
        return delimiter.replace("\t", "\\t");
    }
}
