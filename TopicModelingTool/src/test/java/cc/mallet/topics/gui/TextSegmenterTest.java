package cc.mallet.topics.gui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextSegmenterTest {

    @Test
    void splitsIntoChunksPreservingSpacing() {
        List<TextSegmenter.Segment> segments = TextSegmenter.split("  one two\nthree  four five ", 2);
        assertEquals(3, segments.size());
        assertEquals("one two", segments.get(0).text());
        assertEquals("three  four", segments.get(1).text());
        assertEquals("five", segments.get(2).text());
        assertEquals(1, segments.get(2).wordCount());
    }

    @Test
    void emptyTextHasNoSegments() {
        assertEquals(0, TextSegmenter.split(" \n ", 5).size());
    }

    @Test
    void segmentNameInsertsNumberBeforeExtension() {
        assertEquals("moby-dick-3.txt", TextSegmenter.segmentName("moby-dick.txt", 3));
        assertEquals("README-1", TextSegmenter.segmentName("README", 1));
        assertEquals("a.b/notes-2", TextSegmenter.segmentName("a.b/notes", 2));
        assertEquals(".profile-1", TextSegmenter.segmentName(".profile", 1));
    }
}
