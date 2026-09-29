package cc.mallet.topics.gui;

import cc.mallet.pipe.CharSequence2TokenSequence;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.TokenSequence2FeatureSequence;
import cc.mallet.pipe.TokenSequenceLowercase;
import cc.mallet.pipe.TokenSequenceRemoveStopwords;
import cc.mallet.topics.gui.util.CsvReader;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A folder of text files, read into a Mallet {@link InstanceList} along with
 * the bookkeeping needed for reports: document names, a short excerpt of
 * each document, and any metadata supplied in a CSV file.
 */
public class Corpus {

    private static final Logger log = Logger.getLogger(Corpus.class.getName());

    /** Characters of each document kept in memory for the HTML report. */
    static final int EXCERPT_LENGTH = 2000;

    /** One modeled document: either a whole file or a segment of one. */
    public record Document(int id, String name, Path source, String excerpt, List<String> metadata) {}

    private final InstanceList instances;
    private final List<Document> documents = new ArrayList<>();
    private final List<String> metadataHeader = new ArrayList<>();

    private Corpus(InstanceList instances) {
        this.instances = instances;
    }

    public InstanceList getInstances() { return instances; }
    public List<Document> getDocuments() { return Collections.unmodifiableList(documents); }
    public List<String> getMetadataHeader() { return Collections.unmodifiableList(metadataHeader); }
    public int size() { return documents.size(); }

    /** Build the Mallet import pipeline: tokenize, lowercase, remove stopwords, index. */
    static Pipe buildPipe(TrainingOptions options) {
        List<Pipe> pipes = new ArrayList<>();
        pipes.add(new CharSequence2TokenSequence(Pattern.compile(options.tokenRegex)));
        if (!options.preserveCase) {
            pipes.add(new TokenSequenceLowercase());
        }
        if (options.stoplistFile != null) {
            pipes.add(new TokenSequenceRemoveStopwords(options.stoplistFile.toFile(),
                    "UTF-8", options.removeDefaultStopwords, false, false));
        } else if (options.removeDefaultStopwords) {
            pipes.add(new TokenSequenceRemoveStopwords(false, false));
        }
        pipes.add(new TokenSequence2FeatureSequence());
        return new SerialPipes(pipes);
    }

    /** Read every text file under {@code options.inputDir}. */
    public static Corpus load(TrainingOptions options) throws IOException {
        Path inputDir = options.inputDir;
        if (inputDir == null || !Files.isDirectory(inputDir)) {
            throw new IOException("Input folder does not exist: " + inputDir);
        }

        Corpus corpus = new Corpus(new InstanceList(buildPipe(options)));
        Map<String, String[]> metadata = corpus.readMetadata(options);
        boolean segmenting = options.segmentWords > 0;
        if (segmenting) {
            if (metadata == null) {
                corpus.metadataHeader.add("filename");
            }
            corpus.metadataHeader.add("Word Count");
        }

        List<Path> files = listTextFiles(inputDir);
        for (Path file : files) {
            String name = inputDir.relativize(file).toString().replace('\\', '/');
            String text = readText(file);
            if (text == null) {
                continue;
            }

            String[] metaRow = null;
            if (metadata != null) {
                metaRow = metadata.getOrDefault(name, metadata.get(file.getFileName().toString()));
                if (metaRow == null) {
                    log.warning("No metadata row for " + name);
                    metaRow = missingMetadata(corpus.metadataHeader.size() - (segmenting ? 1 : 0));
                }
            }

            if (segmenting) {
                List<TextSegmenter.Segment> segments = TextSegmenter.split(text, options.segmentWords);
                for (int i = 0; i < segments.size(); i++) {
                    TextSegmenter.Segment segment = segments.get(i);
                    // Files short enough to stay whole keep their own name.
                    String segmentName = segments.size() == 1 ? name : TextSegmenter.segmentName(name, i + 1);
                    List<String> row = new ArrayList<>();
                    if (metaRow != null) {
                        row.addAll(Arrays.asList(metaRow));
                        row.set(0, segmentName);
                    } else {
                        row.add(segmentName);
                    }
                    row.add(Integer.toString(segment.wordCount()));
                    corpus.add(segmentName, file, segment.text(), row);
                }
            } else {
                corpus.add(name, file, text, metaRow == null ? List.of() : Arrays.asList(metaRow));
            }
        }

        if (corpus.size() == 0) {
            throw new IOException("No text files found in " + inputDir);
        }
        log.info("Read " + files.size() + " files as " + corpus.size() + " documents.");
        return corpus;
    }

    private void add(String name, Path source, String text, List<String> metadata) {
        int id = documents.size();
        instances.addThruPipe(new Instance(text, null, name, source.toString()));
        String excerpt = text.length() > EXCERPT_LENGTH ? text.substring(0, EXCERPT_LENGTH) : text;
        documents.add(new Document(id, name, source, excerpt, List.copyOf(metadata)));
    }

    /** Returns filename -> metadata row, or null if no metadata file was given. */
    private Map<String, String[]> readMetadata(TrainingOptions options) {
        if (options.metadataFile == null) {
            return null;
        }
        CsvReader reader = new CsvReader(options.metadataFile,
                TrainingOptions.unescapeDelimiter(options.metadataDelimiter), 1);
        metadataHeader.addAll(Arrays.asList(reader.getHeaders().get(0)));

        Map<String, String[]> rows = new HashMap<>();
        for (String[] row : reader) {
            if (row != null && row.length > 0 && !row[0].isBlank()) {
                rows.put(row[0].trim(), row);
            }
        }
        return rows;
    }

    private static String[] missingMetadata(int columns) {
        String[] row = new String[Math.max(columns, 1)];
        Arrays.fill(row, "[missing metadata]");
        row[0] = "[filename not found in metadata]";
        return row;
    }

    /** All regular, non-hidden files under {@code dir}, sorted by path. */
    static List<Path> listTextFiles(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> !isHidden(dir.relativize(p)))
                    .sorted()
                    .toList();
        }
    }

    private static boolean isHidden(Path relative) {
        for (Path part : relative) {
            if (part.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Read a file as UTF-8, falling back to Windows-1252 for files that are
     * not valid UTF-8. Returns null (with a warning) for binary files such as
     * PDFs or Word documents, which need to be converted to text first.
     */
    static String readText(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        for (int i = 0; i < Math.min(bytes.length, 8192); i++) {
            if (bytes[i] == 0) {
                log.warning("Skipping " + file.getFileName() + ": not a plain text file.");
                return null;
            }
        }

        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            log.warning(file.getFileName() + " is not valid UTF-8; reading it as Windows-1252.");
            text = new String(bytes, Charset.forName("windows-1252"));
        }
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        return text;
    }
}
