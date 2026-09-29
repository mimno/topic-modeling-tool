package cc.mallet.topics.gui;

import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;

/** The main window of the Topic Modeling Tool. */
public class TopicModelingToolGUI {

    public static final String APP_NAME = "Topic Modeling Tool";

    private static final String PREF_INPUT = "inputDir";
    private static final String PREF_OUTPUT = "outputDir";

    private final Preferences prefs = Preferences.userNodeForPackage(TopicModelingToolGUI.class);
    private final TrainingOptions options = new TrainingOptions();

    private final JFrame frame = new JFrame(APP_NAME);
    private final JTextField inputField = new JTextField(32);
    private final JTextField outputField = new JTextField(32);
    private final JSpinner numTopics = new JSpinner(new SpinnerNumberModel(10, 2, 1000, 1));
    private final JButton settingsButton = new JButton("Optional Settings…");
    private final JButton trainButton = new JButton("Learn Topics");
    private final JProgressBar progress = new JProgressBar();
    private final LogConsole console = new LogConsole();
    private final JButton openResultsButton = new JButton("Open Results");
    private final JButton showFolderButton = new JButton("Show Folder");

    private TopicModelingRun.Result lastResult;

    public TopicModelingToolGUI(String input, String output, String metadata) {
        inputField.setText(input != null ? input : prefs.get(PREF_INPUT, ""));
        outputField.setText(output != null ? output : prefs.get(PREF_OUTPUT, defaultOutputDir().toString()));
        if (metadata != null) {
            options.metadataFile = Path.of(metadata);
        }
    }

    /** ~/Documents/Topic Modeling Tool, or the home folder if there is no Documents folder. */
    static Path defaultOutputDir() {
        Path home = Path.of(System.getProperty("user.home"));
        Path documents = home.resolve("Documents");
        return (Files.isDirectory(documents) ? documents : home).resolve(APP_NAME);
    }

    private void build() {
        console.install();

        JLabel intro = new JLabel("<html><div style='width:520px'>Choose a folder of plain-text (.txt) files. By default, files longer than 500 words are "
                + "split into 500-word passages; you can change this in Optional Settings. "
                + "Your files stay on this computer.</div></html>");
        intro.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        inputField.setEditable(false);
        outputField.setEditable(false);

        JPanel form = new JPanel(new GridBagLayout());
        addRow(form, 0, "Input folder", inputField, chooseFolderButton(inputField, "Choose Input Folder"));
        addRow(form, 1, "Output folder", outputField, chooseFolderButton(outputField, "Choose Output Folder"));

        JPanel topicsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        topicsRow.add(numTopics);
        topicsRow.add(Box.createHorizontalStrut(16));
        topicsRow.add(settingsButton);
        addRow(form, 2, "Number of topics", topicsRow, null);

        settingsButton.addActionListener(e -> new SettingsDialog(frame, options).setVisible(true));

        trainButton.setFont(trainButton.getFont().deriveFont(Font.BOLD));
        trainButton.addActionListener(e -> train());
        progress.setStringPainted(true);
        progress.setString("");

        JPanel trainRow = new JPanel(new BorderLayout(12, 0));
        trainRow.setBorder(BorderFactory.createEmptyBorder(12, 0, 8, 0));
        trainRow.add(trainButton, BorderLayout.WEST);
        trainRow.add(progress, BorderLayout.CENTER);

        JPanel top = new JPanel(new BorderLayout());
        top.add(intro, BorderLayout.NORTH);
        top.add(form, BorderLayout.CENTER);
        top.add(trainRow, BorderLayout.SOUTH);

        JScrollPane consoleScroll = new JScrollPane(console);
        consoleScroll.setBorder(BorderFactory.createTitledBorder("Console"));

        openResultsButton.setEnabled(false);
        openResultsButton.addActionListener(e -> openResults());
        showFolderButton.setEnabled(false);
        showFolderButton.addActionListener(e -> open(lastResult.outputDir()));
        JButton clearButton = new JButton("Clear Console");
        clearButton.addActionListener(e -> console.setText(""));

        JPanel bottom = new JPanel(new BorderLayout());
        JPanel resultButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        resultButtons.add(openResultsButton);
        resultButtons.add(Box.createHorizontalStrut(8));
        resultButtons.add(showFolderButton);
        bottom.add(resultButtons, BorderLayout.WEST);
        bottom.add(clearButton, BorderLayout.EAST);
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        JPanel main = new JPanel(new BorderLayout());
        main.setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        main.add(top, BorderLayout.NORTH);
        main.add(consoleScroll, BorderLayout.CENTER);
        main.add(bottom, BorderLayout.SOUTH);

        frame.setContentPane(main);
        frame.getRootPane().setDefaultButton(trainButton);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setMinimumSize(frame.getSize());
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void addRow(JPanel form, int row, String label, JComponent field, JComponent button) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(3, 0, 3, 8);
        c.anchor = GridBagConstraints.LINE_END;
        form.add(new JLabel(label), c);

        c = new GridBagConstraints();
        c.gridy = row;
        c.gridx = 1;
        c.weightx = 1;
        c.insets = new Insets(3, 0, 3, 0);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.LINE_START;
        form.add(field, c);

        if (button != null) {
            c = new GridBagConstraints();
            c.gridy = row;
            c.gridx = 2;
            c.insets = new Insets(3, 8, 3, 0);
            form.add(button, c);
        }
    }

    private JButton chooseFolderButton(JTextField field, String title) {
        JButton button = new JButton("Choose…");
        button.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(title);
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (!field.getText().isEmpty()) {
                Path current = Path.of(field.getText());
                chooser.setCurrentDirectory((Files.isDirectory(current) ? current : current.getParent()).toFile());
            }
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                field.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        return button;
    }

    private void train() {
        if (inputField.getText().isBlank()) {
            showError(frame, "Please choose an input folder.", "It should contain plain-text (.txt) files.");
            return;
        }
        if (outputField.getText().isBlank()) {
            showError(frame, "Please choose an output folder.", "Results will be saved there.");
            return;
        }

        options.inputDir = Path.of(inputField.getText());
        options.outputDir = Path.of(outputField.getText());
        options.numTopics = (Integer) numTopics.getValue();
        prefs.put(PREF_INPUT, inputField.getText());
        prefs.put(PREF_OUTPUT, outputField.getText());

        setRunning(true);
        progress.setIndeterminate(true);
        progress.setString("Starting…");

        new SwingWorker<TopicModelingRun.Result, Object>() {
            @Override
            protected TopicModelingRun.Result doInBackground() throws Exception {
                return TopicModelingRun.run(options, new TopicModelingRun.Listener() {
                    @Override
                    public void status(String message) {
                        publish(message);
                    }
                    @Override
                    public void iteration(int iteration, int total) {
                        publish(new int[] {iteration, total});
                    }
                });
            }

            @Override
            protected void process(List<Object> updates) {
                Object last = updates.get(updates.size() - 1);
                if (last instanceof String message) {
                    progress.setIndeterminate(!message.startsWith("Training"));
                    progress.setValue(0);
                    progress.setString(message);
                } else if (last instanceof int[] it) {
                    progress.setIndeterminate(false);
                    progress.setMaximum(it[1]);
                    progress.setValue(it[0]);
                    progress.setString("Iteration " + it[0] + " of " + it[1]);
                }
            }

            @Override
            protected void done() {
                setRunning(false);
                progress.setIndeterminate(false);
                try {
                    lastResult = get();
                    progress.setValue(progress.getMaximum());
                    progress.setString("Done");
                    openResultsButton.setEnabled(lastResult.htmlIndex() != null);
                    showFolderButton.setEnabled(true);
                    if (lastResult.htmlIndex() != null) {
                        frame.getRootPane().setDefaultButton(openResultsButton);
                    }
                } catch (InterruptedException | ExecutionException e) {
                    Throwable cause = e instanceof ExecutionException ? e.getCause() : e;
                    progress.setValue(0);
                    progress.setString("Stopped with an error");
                    console.appendLine("");
                    console.appendLine("Error: " + cause.getMessage());
                    StringWriter trace = new StringWriter();
                    cause.printStackTrace(new PrintWriter(trace));
                    console.append(trace.toString());
                    showError(frame, "Something went wrong.", String.valueOf(cause.getMessage()));
                }
            }
        }.execute();
    }

    private void setRunning(boolean running) {
        trainButton.setEnabled(!running);
        settingsButton.setEnabled(!running);
        numTopics.setEnabled(!running);
        openResultsButton.setEnabled(false);
        showFolderButton.setEnabled(false);
        frame.getRootPane().setDefaultButton(trainButton);
    }

    private void openResults() {
        if (lastResult != null && lastResult.htmlIndex() != null) {
            try {
                Desktop.getDesktop().browse(lastResult.htmlIndex().toUri());
            } catch (IOException | UnsupportedOperationException e) {
                showError(frame, "Could not open a web browser.", "Open " + lastResult.htmlIndex() + " yourself.");
            }
        }
    }

    private void open(Path path) {
        try {
            Desktop.getDesktop().open(path.toFile());
        } catch (IOException | UnsupportedOperationException e) {
            showError(frame, "Could not open the folder.", path.toString());
        }
    }

    static void showError(Component parent, String message, String detail) {
        JOptionPane.showMessageDialog(parent,
                "<html><b>" + HtmlReport.escape(message) + "</b><br><br>" + HtmlReport.escape(detail) + "</html>",
                APP_NAME, JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Arguments (all optional): input folder, output folder, metadata file.
     * With {@code --batch} as the first argument, train without a window;
     * see {@link TopicModelingRun#runBatch}.
     */
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--batch")) {
            System.exit(TopicModelingRun.runBatch(Arrays.copyOfRange(args, 1, args.length)));
        }

        System.setProperty("apple.awt.application.name", APP_NAME);
        System.setProperty("apple.laf.useScreenMenuBar", "true");

        String input = args.length > 0 ? args[0] : null;
        String output = args.length > 1 ? args[1] : null;
        String metadata = args.length > 2 ? args[2] : null;

        SwingUtilities.invokeLater(() -> {
            if (System.getProperty("os.name", "").startsWith("Mac")) {
                FlatMacLightLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
            new TopicModelingToolGUI(input, output, metadata).build();
        });
    }
}
