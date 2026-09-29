package cc.mallet.topics.gui;

import cc.mallet.topics.ParallelTopicModel;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Font;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * A read-only text area that shows log messages from the tool and from
 * Mallet, plus anything written to System.out or System.err.
 */
public class LogConsole extends JTextArea {

    public LogConsole() {
        super(18, 80);
        setEditable(false);
        setLineWrap(false);
        setFont(new Font(Font.MONOSPACED, Font.PLAIN, getFont().getSize()));
    }

    /** Append text from any thread. */
    public void append(String text) {
        if (SwingUtilities.isEventDispatchThread()) {
            super.append(text);
            setCaretPosition(getDocument().getLength());
        } else {
            SwingUtilities.invokeLater(() -> append(text));
        }
    }

    public void appendLine(String line) {
        append(line + "\n");
    }

    /**
     * Route java.util.logging and the standard streams to this console.
     * Output is also copied to the original stderr so it is still visible
     * when the tool is launched from a terminal.
     */
    public void install() {
        // Loading Mallet's logger applies Mallet's logging configuration,
        // which we then override.
        Logger malletLogger = ParallelTopicModel.logger;

        Logger root = Logger.getLogger("");
        for (Handler h : root.getHandlers()) {
            if (h instanceof ConsoleHandler) {
                root.removeHandler(h);
            }
        }

        PrintStream originalErr = System.err;
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (!isLoggable(record)) {
                    return;
                }
                String message = record.getMessage();
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    message = "Warning: " + message;
                }
                appendLine(message);
                originalErr.println(message);
            }
            @Override public void flush() {}
            @Override public void close() {}
        };
        handler.setLevel(Level.INFO);
        root.addHandler(handler);
        malletLogger.setLevel(Level.INFO);

        System.setOut(new PrintStream(new LineOutputStream(originalErr), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new LineOutputStream(originalErr), true, StandardCharsets.UTF_8));
    }

    /** Buffers bytes until a newline so multi-byte UTF-8 characters are decoded intact. */
    private class LineOutputStream extends OutputStream {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final PrintStream copy;

        LineOutputStream(PrintStream copy) {
            this.copy = copy;
        }

        @Override
        public synchronized void write(int b) {
            buffer.write(b);
            if (b == '\n') {
                flush();
            }
        }

        @Override
        public synchronized void flush() {
            if (buffer.size() > 0) {
                String text = buffer.toString(StandardCharsets.UTF_8);
                buffer.reset();
                append(text);
                copy.print(text);
            }
        }
    }
}
