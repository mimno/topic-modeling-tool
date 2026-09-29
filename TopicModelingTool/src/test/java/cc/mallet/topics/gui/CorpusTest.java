package cc.mallet.topics.gui;

import cc.mallet.types.FeatureSequence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorpusTest {

    @TempDir
    Path dir;

    private TrainingOptions options(Path input) {
        TrainingOptions options = new TrainingOptions();
        options.inputDir = input;
        options.segmentWords = 0;
        return options;
    }

    @Test
    void readsFilesSkippingHiddenAndBinary() throws Exception {
        Files.writeString(dir.resolve("b.txt"), "The whale swam.");
        Files.writeString(dir.resolve("a.txt"), "Ships sail the sea.");
        Files.writeString(dir.resolve(".DS_Store"), "junk");
        Files.write(dir.resolve("paper.pdf"), new byte[] {'%', 'P', 'D', 'F', 0, 1, 2});
        Files.createDirectories(dir.resolve("sub"));
        Files.writeString(dir.resolve("sub/c.txt"), "Harpoons and rope.");

        Corpus corpus = Corpus.load(options(dir));

        assertEquals(List.of("a.txt", "b.txt", "sub/c.txt"),
                corpus.getDocuments().stream().map(Corpus.Document::name).toList());
        assertEquals(3, corpus.getInstances().size());
        assertTrue(corpus.getMetadataHeader().isEmpty());
    }

    @Test
    void removesStopwordsAndLowercases() throws Exception {
        Files.writeString(dir.resolve("a.txt"), "The Whale and the SEA");
        Corpus corpus = Corpus.load(options(dir));
        FeatureSequence tokens = (FeatureSequence) corpus.getInstances().get(0).getData();
        List<Object> words = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            words.add(tokens.getObjectAtPosition(i));
        }
        assertEquals(List.of("whale", "sea"), words);
    }

    @Test
    void fallsBackToWindows1252() throws Exception {
        Files.write(dir.resolve("a.txt"), "café crème".getBytes(Charset.forName("windows-1252")));
        Corpus corpus = Corpus.load(options(dir));
        assertEquals("café crème", corpus.getDocuments().get(0).excerpt());
    }

    @Test
    void segmentsFilesAndCarriesMetadata() throws Exception {
        Path input = Files.createDirectories(dir.resolve("input"));
        Files.writeString(input.resolve("one.txt"), "a b c d e f g");
        Files.writeString(input.resolve("two.txt"), "h i");
        Path meta = dir.resolve("meta.csv");
        Files.writeString(meta, "filename,year\none.txt,1851\n");

        TrainingOptions options = options(input);
        options.metadataFile = meta;
        options.segmentWords = 3;
        Corpus corpus = Corpus.load(options);

        assertEquals(List.of("one-1.txt", "one-2.txt", "one-3.txt", "two.txt"),
                corpus.getDocuments().stream().map(Corpus.Document::name).toList());
        assertEquals(List.of("filename", "year", "Word Count"), corpus.getMetadataHeader());
        assertEquals(List.of("one-2.txt", "1851", "3"), corpus.getDocuments().get(1).metadata());
        assertEquals(List.of("two.txt", "[missing metadata]", "2"), corpus.getDocuments().get(3).metadata());
    }
}
