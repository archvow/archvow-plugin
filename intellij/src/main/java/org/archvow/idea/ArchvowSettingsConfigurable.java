package org.archvow.idea;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Settings > Tools > Archvow (0.26.1): in one language, the one chosen here (English by default, as archvow); the
 * Pythons of this computer listed, each with its version and archvow; and which one runs archvow, and why.
 */
public final class ArchvowSettingsConfigurable implements Configurable {
    private final Project project;
    private JPanel panel;
    private final JTextField command = new JTextField();
    private final JComboBox<Object> python = new JComboBox<>();
    private final JTextField timeout = new JTextField();
    private final JTextField installFrom = new JTextField();
    private final JCheckBox useProjectInterpreter = new JCheckBox();
    private final JComboBox<String> languageMode = new JComboBox<>(ArchvowI18n.modes());
    private final JLabel runs = new JLabel();
    /** The Pythons found ({@link ArchvowPythons}), null while they are looked for. */
    private List<ArchvowPythons.Found> found;
    /** What runs archvow with these settings, once tried: {program, where, version} or null (not found). */
    private String[] running;
    private boolean searching;
    private boolean filling;

    /** "Automatic" in the Python list: archvow is looked for (ArchvowProcessCommand.candidates). */
    private static final Object AUTOMATIC = new Object();

    public ArchvowSettingsConfigurable(Project project) { this.project = project; }
    @Override public @Nls String getDisplayName() { return "Archvow"; }

    /** French when the language chosen on this page (not yet applied) says so. */
    private boolean fr() {
        String mode = String.valueOf(languageMode.getSelectedItem());
        if ("FR".equals(mode)) return true;
        if ("EN".equals(mode)) return false;
        return java.util.Locale.getDefault().getLanguage().startsWith("fr");
    }

    @Override public @Nullable JComponent createComponent() {
        panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        python.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list, label(value), index, selected, focus);
            }
        });
        python.addActionListener(e -> { if (!filling) { running = null; showRunning(); whichRuns(); } });
        languageMode.addActionListener(e -> { if (!filling) build(); });
        reset();
        return panel;
    }

    /** The page, in the language chosen on it. */
    private void build() {
        boolean fr = fr();
        panel.removeAll();
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder(ArchvowSettingsText.runsTitle(fr)));
        int y = 0;
        runs.setFont(runs.getFont().deriveFont(Font.BOLD));
        JButton test = new JButton(ArchvowSettingsText.test(fr));
        test.addActionListener(e -> testInterpreter(test));
        row(form, y++, ArchvowSettingsText.runsLabel(fr), runs, test, null);
        JButton browse = new JButton(ArchvowSettingsText.browse(fr));
        browse.addActionListener(e -> choosePython());
        JButton again = new JButton(ArchvowSettingsText.findAgain(fr));
        again.addActionListener(e -> discover());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttons.add(browse);
        buttons.add(again);
        row(form, y++, "Python", python, buttons, ArchvowSettingsText.pythonHint(fr));
        useProjectInterpreter.setText(ArchvowSettingsText.useIde(fr));
        row(form, y++, "", useProjectInterpreter, null, ArchvowSettingsText.automaticOrder(fr, ArchvowInterpreter.projectPython(project)));
        row(form, y++, ArchvowSettingsText.executable(fr), command, null, ArchvowSettingsText.executableHint(fr));

        JPanel other = new JPanel(new GridBagLayout());
        other.setBorder(BorderFactory.createTitledBorder(ArchvowSettingsText.otherTitle(fr)));
        int z = 0;
        row(other, z++, ArchvowSettingsText.language(fr), languageMode, null, ArchvowSettingsText.languageHint(fr));
        row(other, z++, ArchvowSettingsText.timeout(fr), timeout, null, ArchvowSettingsText.timeoutHint(fr));
        JButton browseWheels = new JButton(ArchvowSettingsText.browse(fr));
        browseWheels.addActionListener(e -> chooseWheels());
        row(other, z++, ArchvowSettingsText.installFrom(fr), installFrom, browseWheels, ArchvowSettingsText.installFromHint(fr));

        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.add(form);
        column.add(Box.createVerticalStrut(10));
        column.add(other);
        panel.add(column, BorderLayout.NORTH);
        showRunning();
        python.repaint();
        panel.revalidate();
        panel.repaint();
    }

    private void row(JPanel form, int y, String label, JComponent field, JComponent extra, String hint) {
        GridBagConstraints l = new GridBagConstraints();
        l.gridx = 0; l.gridy = y; l.anchor = GridBagConstraints.NORTHWEST;
        l.insets = new Insets(5, 5, 2, 10);
        form.add(new JLabel(label), l);

        JPanel right = new JPanel(new BorderLayout(6, 4));
        right.add(field, BorderLayout.CENTER);
        if (extra != null) right.add(extra, BorderLayout.EAST);
        if (hint != null && !hint.isBlank()) {
            JTextArea area = new JTextArea(hint);
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setOpaque(false);
            area.setFont(UIManager.getFont("Label.font").deriveFont(11f));
            area.setForeground(UIManager.getColor("Label.disabledForeground"));
            right.add(area, BorderLayout.SOUTH);
        }
        GridBagConstraints v = new GridBagConstraints();
        v.gridx = 1; v.gridy = y; v.weightx = 1; v.fill = GridBagConstraints.HORIZONTAL;
        v.insets = new Insets(5, 0, 5, 0);
        form.add(right, v);
    }

    private String label(Object value) {
        boolean fr = fr();
        if (value == AUTOMATIC) return ArchvowSettingsText.automatic(fr);
        if (value instanceof ArchvowPythons.Found f) return ArchvowPythons.label(f, fr);
        if (value == null) return "";
        return value + (searching ? "" : "  (" + ArchvowSettingsText.chosenByHand(fr) + ")");
    }

    /** The Python chosen in the list: "" for Automatic. */
    private String chosenPython() {
        Object value = python.getSelectedItem();
        if (value instanceof ArchvowPythons.Found f) return f.path();
        if (value == null || value == AUTOMATIC) return "";
        return value.toString().trim();
    }

    /** The list: Automatic, the Pythons found, and the Python of the settings when it is not among them. */
    private void fill(String selected) {
        filling = true;
        try {
            python.removeAllItems();
            python.addItem(AUTOMATIC);
            Object pick = AUTOMATIC;
            if (found != null) for (ArchvowPythons.Found f : found) {
                python.addItem(f);
                if (f.path().equals(selected)) pick = f;
            }
            if (!selected.isBlank() && pick == AUTOMATIC) { python.addItem(selected); pick = selected; }
            python.setSelectedItem(pick);
        } finally {
            filling = false;
        }
    }

    /** Looks for the Pythons of this computer, in the background; never in a project not trusted (safe mode). */
    private void discover() {
        if (!ArchvowTrust.trusted(project)) { found = List.of(); fill(chosenPython()); return; }
        String keep = chosenPython();
        String root = project.getBasePath();
        String ide = ArchvowInterpreter.projectPython(project);
        List<String> sdks = ArchvowInterpreter.idePythons();
        searching = true;
        running = null;
        showRunning();
        new SwingWorker<List<ArchvowPythons.Found>, Void>() {
            @Override protected List<ArchvowPythons.Found> doInBackground() {
                return ArchvowPythons.probe(ArchvowPythons.placesHere(ide, root, sdks), ArchvowWsl.windows());
            }
            @Override protected void done() {
                searching = false;
                try { found = get(); } catch (Exception e) { found = List.of(); }
                if (panel == null) return;
                fill(keep);
                whichRuns();
            }
        }.execute();
    }

    /** Which archvow these settings run (the same search as the commands), shown at the top. */
    private void whichRuns() {
        if (panel == null || !ArchvowTrust.trusted(project)) { showRunning(); return; }
        ArchvowSettings.Values values = pending();
        String root = project.getBasePath();
        boolean fr = fr();
        new SwingWorker<String[], Void>() {
            @Override protected String[] doInBackground() {
                ArchvowCli.Probe probe = ArchvowCli.probe(ArchvowCli.candidates(project, values, root), root, fr);
                if (probe.chosen() == null) return new String[0];
                String version = "";
                for (ArchvowPythons.Found f : found == null ? List.<ArchvowPythons.Found>of() : found)
                    if (f.path().equals(probe.chosen().python())) version = f.archvow();
                return new String[] {probe.chosen().program(), probe.chosen().source(), version};
            }
            @Override protected void done() {
                try { running = get(); } catch (Exception e) { running = new String[0]; }
                showRunning();
            }
        }.execute();
    }

    private void showRunning() {
        boolean fr = fr();
        if (!ArchvowTrust.trusted(project)) runs.setText(ArchvowTrust.message(fr));
        else if (searching || running == null) runs.setText(ArchvowSettingsText.looking(fr));
        else if (running.length == 0) runs.setText(ArchvowSettingsText.notFound(fr));
        else runs.setText(ArchvowSettingsText.runs(fr, running[0], running[1], running[2]));
    }

    /** These settings as they are on the page (not yet applied). */
    private ArchvowSettings.Values pending() {
        ArchvowSettings.Values v = new ArchvowSettings.Values();
        v.executable = command.getText().trim();
        v.python = chosenPython();
        v.useProjectInterpreter = useProjectInterpreter.isSelected();
        return v;
    }

    private void choosePython() {
        String chosen = ArchvowChooser.choose(project, ArchvowChooser.Kind.FILE, "Python", chosenPython());
        if (chosen == null) return;
        fill(chosen);
        running = null;
        whichRuns();
    }

    private void chooseWheels() {
        String chosen = ArchvowChooser.choose(project, ArchvowChooser.Kind.FILE_OR_FOLDER, ArchvowSettingsText.installFrom(fr()),
            installFrom.getText());
        if (chosen != null) installFrom.setText(chosen);
    }

    /** Test: the same search as the commands, with what the page says; its report in a dialog. */
    private void testInterpreter(JButton test) {
        boolean fr = fr();
        ArchvowSettings.Values values = pending();
        String root = project.getBasePath();
        test.setEnabled(false);
        String[] command = {""};
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() {
                try {
                    if (root == null || !new File(root).isDirectory()) return ArchvowSettingsText.noFolder(fr, root);
                    if (!ArchvowTrust.trusted(project)) return ArchvowTrust.message(fr);  // safe mode: the Test runs nothing either
                    ArchvowCli.Probe probe = ArchvowCli.probe(ArchvowCli.candidates(project, values, root), root, fr);
                    if (probe.chosen() == null) return probe.report();
                    List<String> argv = ArchvowProcessCommand.build(probe.chosen().executable(), probe.chosen().python(),
                        root, List.of("--version"));
                    command[0] = ArchvowTextDialog.commandLine(argv);
                    ProcessBuilder builder = new ProcessBuilder(argv).redirectErrorStream(true);
                    ArchvowCli.prepare(builder, argv, root);
                    Process process = builder.start();
                    if (!process.waitFor(10, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                        return ArchvowSettingsText.timedOut(fr);
                    }
                    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    return ArchvowSettingsText.runs(fr, probe.chosen().program(), probe.chosen().source(), "") + "\n"
                        + ArchvowSettingsText.commandLine(fr) + " " + command[0] + "\n\n" + output.strip();
                } catch (Exception error) {
                    return error.getClass().getSimpleName() + ": " + error.getMessage();
                }
            }
            @Override protected void done() {
                test.setEnabled(true);
                String text;
                try { text = get(); } catch (Exception e) { text = e.toString(); }
                ArchvowTextDialog.show(panel, ArchvowSettingsText.test(fr), text, command[0], fr);
            }
        }.execute();
    }

    private ArchvowSettings.Values values() { return ArchvowSettings.get(project).getState(); }
    @Override public boolean isModified() {
        var s = values();
        return !command.getText().equals(s.executable) || !chosenPython().equals(s.python == null ? "" : s.python.trim())
            || !timeout.getText().equals(Integer.toString(s.timeoutSeconds))
            || !String.valueOf(languageMode.getSelectedItem()).equals(s.languageMode)
            || useProjectInterpreter.isSelected() != s.useProjectInterpreter
            || !installFrom.getText().trim().equals(s.installFrom == null ? "" : s.installFrom);
    }
    @Override public void apply() throws ConfigurationException {
        boolean fr = fr();
        final int seconds;
        try { seconds = Integer.parseInt(timeout.getText().trim()); }
        catch (NumberFormatException e) { throw new ConfigurationException(ArchvowSettingsText.timeoutNotNumber(fr)); }
        if (seconds < 5 || seconds > 3600) throw new ConfigurationException(ArchvowSettingsText.timeoutHint(fr));
        var s = values();
        s.executable = command.getText().trim();
        s.python = chosenPython();
        s.timeoutSeconds = seconds;
        s.languageMode = String.valueOf(languageMode.getSelectedItem());
        s.useProjectInterpreter = useProjectInterpreter.isSelected();
        s.installFrom = installFrom.getText().trim();
    }
    @Override public void reset() {
        var s = values();
        filling = true;
        try {
            command.setText(s.executable);
            timeout.setText(Integer.toString(s.timeoutSeconds));
            languageMode.setSelectedItem(s.languageMode == null || s.languageMode.isBlank() ? "EN" : s.languageMode);
            useProjectInterpreter.setSelected(s.useProjectInterpreter);
            installFrom.setText(s.installFrom == null ? "" : s.installFrom);
        } finally {
            filling = false;
        }
        fill(s.python == null ? "" : s.python.trim());
        if (panel != null) build();
        if (found == null) discover(); else whichRuns();
    }
    @Override public void disposeUIResources() { panel = null; }

    /** For the tests: the page's texts, in one language. */
    static List<String> texts(boolean fr) {
        List<String> out = new ArrayList<>(ArchvowSettingsText.all(fr));
        out.add(ArchvowTrust.message(fr));
        return out;
    }
}
