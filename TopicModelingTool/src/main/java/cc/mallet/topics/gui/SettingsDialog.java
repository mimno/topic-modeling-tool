package cc.mallet.topics.gui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The "Optional Settings" window. Everything here has a sensible default;
 * nobody needs to open this window to get useful results.
 */
public class SettingsDialog extends JDialog {

    private final TrainingOptions target;

    private final JTextField metadataFile = pathField();
    private final JTextField metadataDelimiter = new JTextField(4);
    private final JSpinner segmentWords = intSpinner(0, 0, 1_000_000, 100);
    private final JCheckBox removeStopwords = new JCheckBox("Remove common English words (the, and, of, …)");
    private final JTextField stoplistFile = pathField();
    private final JCheckBox preserveCase = new JCheckBox("Keep upper and lower case distinct");
    private final JTextField tokenRegex = new JTextField(24);

    private final JSpinner iterations = intSpinner(400, 10, 1_000_000, 100);
    private final JSpinner threads = intSpinner(1, 1, 256, 1);
    private final JSpinner optimizeInterval = intSpinner(10, 0, 10_000, 10);
    private final JSpinner alphaSum = doubleSpinner(50, 0.01, 10_000, 1);
    private final JSpinner beta = doubleSpinner(0.01, 0.0001, 100, 0.01);
    private final JSpinner randomSeed = intSpinner(0, -1, Integer.MAX_VALUE, 1);

    private final JSpinner topWords = intSpinner(20, 1, 1000, 5);
    private final JTextField outputDelimiter = new JTextField(4);
    private final JCheckBox generateHtml = new JCheckBox("Create web pages for browsing topics and documents");
    private final JCheckBox saveMalletFiles = new JCheckBox("Also save raw MALLET files");
    private final JCheckBox timestampOutput = new JCheckBox("Save each run in its own dated folder");

    private int row = 0;

    public SettingsDialog(JFrame owner, TrainingOptions target) {
        super(owner, "Optional Settings", true);
        this.target = target;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));

        section(form, "Input");
        field(form, "Metadata file", fileRow(metadataFile, "Choose a CSV metadata file"),
                "Optional CSV file whose first column is the file name of each text file. "
                + "Its other columns are copied into topics-metadata.csv.");
        field(form, "Metadata delimiter", metadataDelimiter,
                "The character separating columns in the metadata file. Type \\t for tab.");
        field(form, "Split files into chunks of", withSuffix(segmentWords, "words (0 = don't split)"),
                "Topic models work best on passages of a few hundred words. "
                + "Use this to split long texts such as novels into chunks.");
        field(form, "", removeStopwords,
                "Remove Mallet's standard list of very common English words.");
        field(form, "Extra stopword file", fileRow(stoplistFile, "Choose a stopword file"),
                "A plain-text file with one word per line to remove from every document. "
                + "If the box above is unchecked, only these words are removed.");
        field(form, "", preserveCase, "By default all words are converted to lower case.");
        field(form, "Word pattern", tokenRegex,
                "Regular expression describing a single word. The default matches runs of "
                + "letters, allowing punctuation inside a word (don't, well-known).");

        section(form, "Training");
        field(form, "Iterations", iterations,
                "Number of passes through the corpus. More iterations take longer and give more stable topics.");
        field(form, "Threads", threads, "Number of processor cores to use while training.");
        field(form, "Optimize every", withSuffix(optimizeInterval, "iterations (0 = never)"),
                "How often to re-estimate how common each topic is. Usually improves topics.");
        field(form, "Topic density (alpha)", alphaSum,
                "Initial total Dirichlet parameter for document-topic distributions.");
        field(form, "Word density (beta)", beta,
                "Initial Dirichlet parameter for topic-word distributions.");
        field(form, "Random seed", randomSeed,
                "The same seed and settings give the same topics. Use -1 for a different result every time.");

        section(form, "Output");
        field(form, "Words per topic", topWords, "Number of top words listed for each topic.");
        field(form, "Output CSV delimiter", outputDelimiter,
                "The character separating columns in the output CSV files. Type \\t for tab.");
        field(form, "", generateHtml, "Write output_html/index.html, viewable in any web browser.");
        field(form, "", saveMalletFiles,
                "Save the imported corpus, sampling state and trained model for further work in MALLET.");
        field(form, "", timestampOutput,
                "Keep earlier results instead of overwriting them. Folders are named after the input "
                + "folder and the date and time of the run.");

        JButton defaults = new JButton("Restore Defaults");
        defaults.addActionListener(e -> load(new TrainingOptions()));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        JButton ok = new JButton("OK");
        ok.addActionListener(e -> {
            if (validateFields()) {
                store(this.target);
                dispose();
            }
        });

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
        left.add(defaults);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        right.add(cancel);
        right.add(ok);
        JPanel buttons = new JPanel(new BorderLayout());
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 10));
        buttons.add(left, BorderLayout.WEST);
        buttons.add(right, BorderLayout.EAST);

        getContentPane().add(form, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(ok);

        load(target);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private void load(TrainingOptions o) {
        metadataFile.setText(o.metadataFile == null ? "" : o.metadataFile.toString());
        metadataDelimiter.setText(TrainingOptions.escapeDelimiter(o.metadataDelimiter));
        segmentWords.setValue(o.segmentWords);
        removeStopwords.setSelected(o.removeDefaultStopwords);
        stoplistFile.setText(o.stoplistFile == null ? "" : o.stoplistFile.toString());
        preserveCase.setSelected(o.preserveCase);
        tokenRegex.setText(o.tokenRegex);
        iterations.setValue(o.numIterations);
        threads.setValue(o.numThreads);
        optimizeInterval.setValue(o.optimizeInterval);
        alphaSum.setValue(o.alphaSum);
        beta.setValue(o.beta);
        randomSeed.setValue(o.randomSeed);
        topWords.setValue(o.numTopWords);
        outputDelimiter.setText(TrainingOptions.escapeDelimiter(o.outputDelimiter));
        generateHtml.setSelected(o.generateHtml);
        saveMalletFiles.setSelected(o.saveMalletFiles);
        timestampOutput.setSelected(o.timestampOutput);
    }

    private void store(TrainingOptions o) {
        o.metadataFile = pathOrNull(metadataFile.getText());
        o.metadataDelimiter = TrainingOptions.unescapeDelimiter(metadataDelimiter.getText());
        o.segmentWords = (Integer) segmentWords.getValue();
        o.removeDefaultStopwords = removeStopwords.isSelected();
        o.stoplistFile = pathOrNull(stoplistFile.getText());
        o.preserveCase = preserveCase.isSelected();
        o.tokenRegex = tokenRegex.getText();
        o.numIterations = (Integer) iterations.getValue();
        o.numThreads = (Integer) threads.getValue();
        o.optimizeInterval = (Integer) optimizeInterval.getValue();
        o.alphaSum = (Double) alphaSum.getValue();
        o.beta = (Double) beta.getValue();
        o.randomSeed = (Integer) randomSeed.getValue();
        o.numTopWords = (Integer) topWords.getValue();
        o.outputDelimiter = TrainingOptions.unescapeDelimiter(outputDelimiter.getText());
        o.generateHtml = generateHtml.isSelected();
        o.saveMalletFiles = saveMalletFiles.isSelected();
        o.timestampOutput = timestampOutput.isSelected();
    }

    private boolean validateFields() {
        try {
            Pattern.compile(tokenRegex.getText());
        } catch (PatternSyntaxException e) {
            TopicModelingToolGUI.showError(this, "The word pattern is not a valid regular expression.",
                    e.getDescription());
            return false;
        }
        if (metadataDelimiter.getText().isEmpty() || outputDelimiter.getText().isEmpty()) {
            TopicModelingToolGUI.showError(this, "Delimiters cannot be empty.", "Use , for commas or \\t for tabs.");
            return false;
        }
        int words = (Integer) segmentWords.getValue();
        if (words > 0 && words < 100) {
            TopicModelingToolGUI.showError(this, "Chunks must be at least 100 words long.",
                    "Use 0 to keep each file as a single document.");
            return false;
        }
        return true;
    }

    // Layout helpers

    private void section(JPanel form, String title) {
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        GridBagConstraints c = constraints(0, row++);
        c.gridwidth = 2;
        c.insets = new Insets(row == 1 ? 0 : 14, 0, 4, 0);
        form.add(label, c);
    }

    private void field(JPanel form, String label, JComponent component, String help) {
        component.setToolTipText(wrapTooltip(help));
        JLabel l = new JLabel(label);
        l.setToolTipText(component.getToolTipText());
        GridBagConstraints c = constraints(0, row);
        c.anchor = GridBagConstraints.LINE_END;
        c.insets = new Insets(2, 12, 2, 8);
        form.add(l, c);
        c = constraints(1, row++);
        c.anchor = GridBagConstraints.LINE_START;
        boolean compact = component instanceof JSpinner
                || component == metadataDelimiter || component == outputDelimiter;
        c.fill = compact ? GridBagConstraints.NONE : GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        form.add(component, c);
    }

    private static GridBagConstraints constraints(int x, int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.anchor = GridBagConstraints.LINE_START;
        return c;
    }

    private JPanel fileRow(JTextField field, String chooserTitle) {
        JButton choose = new JButton("Choose…");
        choose.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(field.getText().isEmpty() ? null : field.getText());
            chooser.setDialogTitle(chooserTitle);
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                field.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        JButton clear = new JButton("Clear");
        clear.addActionListener(e -> field.setText(""));

        JPanel panel = new JPanel(new BorderLayout(4, 0));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttons.add(choose);
        buttons.add(clear);
        panel.add(field, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.EAST);
        return panel;
    }

    private static JPanel withSuffix(JComponent component, String suffix) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        panel.add(component);
        JLabel label = new JLabel("  " + suffix);
        panel.add(label);
        return panel;
    }

    private static JTextField pathField() {
        JTextField field = new JTextField(24);
        field.setEditable(false);
        return field;
    }

    private static JSpinner intSpinner(int value, int min, int max, int step) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setColumns(7);
        return spinner;
    }

    private static JSpinner doubleSpinner(double value, double min, double max, double step) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "0.0####"));
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setColumns(7);
        return spinner;
    }

    private static Path pathOrNull(String text) {
        return text == null || text.isBlank() ? null : Path.of(text);
    }

    private static String wrapTooltip(String text) {
        return "<html><div style='width:280px'>" + text.replace("<", "&lt;") + "</div></html>";
    }
}
