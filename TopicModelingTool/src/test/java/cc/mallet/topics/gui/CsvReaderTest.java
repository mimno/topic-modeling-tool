package cc.mallet.topics.gui;

import cc.mallet.topics.gui.util.CsvReader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvReaderTest {

    @Test
    void parsesQuotedAndMultilineCells() {
        CsvReader reader = new CsvReader(Path.of("src/test/resources/data/csvtest"), ",", 1);
        assertArrayEquals(new String[] {"here", "is", "a", "header"}, reader.getHeaders().get(0));

        List<String[]> rows = new ArrayList<>();
        for (String[] row : reader) {
            rows.add(row);
        }
        assertEquals(6, rows.size());
        assertArrayEquals(new String[] {"here", "are", "some", "values"}, rows.get(0));
        assertEquals("sp ♤ ced \nutf-8", rows.get(1)[2]);
        assertEquals("and, some, values, quoted", rows.get(2)[0]);
        assertEquals("[missing cell]", rows.get(2)[3]);
    }
}
