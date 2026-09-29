package cc.mallet.topics.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits long texts into chunks of roughly equal length. Topic models work
 * best on passages of a few hundred words; a whole novel treated as one
 * document tends to look like "every topic at once".
 */
public final class TextSegmenter {

    private static final Pattern WORD = Pattern.compile("\\S+");

    private TextSegmenter() {}

    /** A chunk of text and the number of whitespace-delimited words in it. */
    public record Segment(String text, int wordCount) {}

    /**
     * Split {@code text} into chunks of {@code wordsPerSegment} words. The
     * original spacing and line breaks inside each chunk are preserved.
     */
    public static List<Segment> split(String text, int wordsPerSegment) {
        if (wordsPerSegment <= 0) {
            throw new IllegalArgumentException("wordsPerSegment must be positive");
        }

        List<Segment> segments = new ArrayList<>();
        Matcher m = WORD.matcher(text);
        int start = -1;
        int end = 0;
        int words = 0;

        while (m.find()) {
            if (start < 0) {
                start = m.start();
            }
            end = m.end();
            words++;
            if (words == wordsPerSegment) {
                segments.add(new Segment(text.substring(start, end), words));
                start = -1;
                words = 0;
            }
        }
        if (words > 0) {
            segments.add(new Segment(text.substring(start, end), words));
        }
        return segments;
    }

    /**
     * Insert a segment number before the file extension:
     * {@code "moby-dick.txt"} becomes {@code "moby-dick-3.txt"}.
     */
    public static String segmentName(String name, int segmentNumber) {
        int slash = name.lastIndexOf('/');
        int dot = name.lastIndexOf('.');
        if (dot > slash + 1) {
            return name.substring(0, dot) + "-" + segmentNumber + name.substring(dot);
        }
        return name + "-" + segmentNumber;
    }
}
