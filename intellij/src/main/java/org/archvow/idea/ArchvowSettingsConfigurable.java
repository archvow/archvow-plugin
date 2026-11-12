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
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ArchvowSettingsConfigurable implements Configurable {
    private final Project project;
    private JPanel panel;
    private final JTextField command = new JTextField();
    private final JTextField python = new JTextField();
    private final JTextField timeout = new JTextField();
    private final JTextField installFrom = new JTextField();
    private final JCheckBox useProjectInterpreter = new JCheckBox(
        "Use the project's Python interpreter first / Utiliser d'abord l'interpréteur Python du projet");
    private final JLabel detected = new JLabel();
    private final JComboBox<String> languageMode = new JComboBox<>(ArchvowI18n.modes());

    public ArchvowSettingsConfigurable(Project project) { this.project = project; }
    @Override public @Nls String getDisplayName() { return "Archvow"; }

    @Override public @Nullable JComponent createComponent() {
        panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Archvow configuration / Configuration Archvow"));
        String ide = ArchvowInterpreter.projectPython(project);
        detected.setText(ide.isBlank()
            ? "No Python interpreter set for this project in the IDE. / Aucun interpréteur Python défini pour ce projet dans l'IDE."
            : "IDE interpreter / Interpréteur de l'IDE : " + ide + (ArchvowWsl.program(ide, ArchvowWsl.windows()) != null
                ? "  (WSL" + (ArchvowWsl.program(ide, true).distro().isBlank() ? "" : " " + ArchvowWsl.program(ide, true).distro()) + ")" : ""));
        detected.setFont(detected.getFont().deriveFont(11f));
        JPanel interpreter = new JPanel(new BorderLayout(0, 2));
        interpreter.add(useProjectInterpreter, BorderLayout.NORTH);
        interpreter.add(detected, BorderLayout.SOUTH);
        row(form, 0, "Python of the IDE / Python de l'IDE", interpreter, null,
            "Settings > Project > Python Interpreter. When archvow is not there, the Python below, then the executable. "
                + "/ Settings > Project > Python Interpreter. Si archvow n'y est pas : le Python ci-dessous, puis l'exécutable.");
        row(form, 2, "Archvow executable / Exécutable Archvow", command, null,
            "Optional. Use this for a standalone Archvow executable. / Optionnel. À utiliser si Archvow est un exécutable autonome.");
        JButton browsePython = new JButton("Browse…");
        browsePython.addActionListener(e -> choosePython());
        row(form, 1, "Python executable / Exécutable Python", python, browsePython,
            "Enter only the Python executable path, for example /path/to/venv/bin/python3. A Python in WSL: "
                + "\\\\wsl.localhost\\Ubuntu\\home\\you\\.venv\\bin\\python3 (run through wsl.exe). / Saisir uniquement le chemin "
                + "de l'exécutable Python. Un Python dans WSL : \\\\wsl.localhost\\Ubuntu\\home\\toi\\.venv\\bin\\python3 (lancé par wsl.exe).");
        row(form, 3, "Timeout (seconds) / Délai (secondes)", timeout, null,
            "Allowed range: 5 to 3600. / Valeur autorisée : 5 à 3600.");
        row(form, 4, "Language / Langue", languageMode, null,
            "IDE = follow IntelliJ or PyCharm language. / IDE = suit la langue de l'IDE.");
        JButton browseWheels = new JButton("Browse…");
        browseWheels.addActionListener(e -> chooseWheels());
        row(form, 5, "Install archvow from / Installer archvow depuis", installFrom, browseWheels,
            "Used only by \"Install archvow\", after your confirmation. Empty = pip's own configuration (pip.conf, PIP_INDEX_URL). "
                + "Or your company's index URL, a .whl file, or a folder of wheels (pip --no-index --find-links). "
                + "/ Utilisé seulement par « Installer archvow », après ta confirmation. Vide = la configuration de pip "
                + "(pip.conf, PIP_INDEX_URL). Ou l'URL de l'index de l'entreprise, un fichier .whl, ou un dossier de wheels.");

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton test = new JButton("Test Archvow (--version)");
        test.addActionListener(e -> testInterpreter(test));
        buttons.add(test);
        JButton explain = new JButton("Usage help / Aide");
        explain.addActionListener(e -> explainPythonField());
        buttons.add(explain);

        JTextArea note = new JTextArea();
        note.setEditable(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setOpaque(false);
        note.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Important / Important"),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        note.setText("This field should contain only the Python executable path.\n"
            + "Example: /path/to/venv/bin/python3\n"
            + "Example: /Users/acme/.venv/bin/python3\n"
            + "Example (Windows, archvow in WSL): \\\\wsl.localhost\\Ubuntu\\home\\acme\\.venv\\bin\\python3\n"
            + "If you enter 'python3 -m archvow', the plugin still accepts it for backward compatibility, "
            + "but the recommended value is the executable path only.\n\n"
            + "Ce champ doit contenir uniquement le chemin de l'exécutable Python.\n"
            + "Exemple : /chemin/vers/ton/venv/bin/python3\n"
            + "Exemple : /Users/acme/.venv/bin/python3\n"
            + "Si tu saisis 'python3 -m archvow', le plugin l'accepte encore pour compatibilité, "
            + "mais la valeur recommandée reste le chemin seul vers Python.");

        panel.add(form, BorderLayout.NORTH);
        panel.add(buttons, BorderLayout.CENTER);
        panel.add(note, BorderLayout.SOUTH);
        reset();
        return panel;
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
            area.setFont(area.getFont().deriveFont(11f));
            area.setForeground(new Color(90, 90, 90));
            right.add(area, BorderLayout.SOUTH);
        }
        GridBagConstraints v = new GridBagConstraints();
        v.gridx = 1; v.gridy = y; v.weightx = 1; v.fill = GridBagConstraints.HORIZONTAL;
        v.insets = new Insets(5, 0, 5, 0);
        form.add(right, v);
    }

    private void choosePython() {
        String chosen = ArchvowChooser.choose(project, ArchvowChooser.Kind.FILE, "Python", python.getText());
        if (chosen != null) python.setText(chosen);
    }

    private void chooseWheels() {
        String chosen = ArchvowChooser.choose(project, ArchvowChooser.Kind.FILE_OR_FOLDER, "Install archvow from / Installer archvow depuis",
            installFrom.getText());
        if (chosen != null) installFrom.setText(chosen);
    }

    private void explainPythonField() {
        ArchvowTextDialog.show(panel, "Archvow Python configuration",
            "Recommended value / Valeur recommandée:\n\n"
                + "  /path/to/venv/bin/python3\n\n"
                + "Accepted for backward compatibility / Accepté pour compatibilité :\n\n"
                + "  python3 -m archvow\n\n"
                + "Do not add other arguments in this field. / Ne pas ajouter d'autres arguments dans ce champ.",
            null, ArchvowI18n.isFrench(project));
    }

    /** Test: the same search as the commands (the IDE's interpreter, the Python, the executable), with these fields. */
    private void testInterpreter(JButton test) {
        String configuredExecutable = command.getText().trim();
        String configuredPython = python.getText().trim();
        boolean useIde = useProjectInterpreter.isSelected();
        String root = project.getBasePath();
        test.setEnabled(false);
        String[] command = {""};
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() {
                try {
                    if (root == null || !new File(root).isDirectory())
                        return "IntelliJ project folder not found / Dossier projet introuvable: " + root;
                    var candidates = ArchvowProcessCommand.candidates(useIde,
                        useIde ? ArchvowInterpreter.projectPython(project) : "", configuredExecutable, configuredPython,
                        ArchvowProcessCommand.found(root, useIde ? ArchvowInterpreter.idePythons() : List.of(),
                            System.getProperty("user.home", ""), ArchvowWsl.windows(), path -> new File(path).canExecute()));
                    ArchvowCli.Probe probe = ArchvowCli.probe(candidates, root, ArchvowI18n.isFrench(project));
                    if (probe.chosen() == null) return probe.report();
                    List<String> argv = ArchvowProcessCommand.build(probe.chosen().executable(), probe.chosen().python(),
                        root, List.of("--version"));
                    command[0] = ArchvowTextDialog.commandLine(argv);
                    ProcessBuilder builder = new ProcessBuilder(argv).redirectErrorStream(true);
                    ArchvowCli.prepare(builder, argv, root);
                    Process process = builder.start();
                    if (!process.waitFor(10, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                        return "Timeout after 10 seconds / Délai dépassé après 10 secondes.";
                    }
                    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    return "Found in / Trouvé dans : " + probe.chosen().source() + " (" + probe.chosen().program() + ")\n"
                        + "Command / Commande: " + command[0] + "\n"
                        + "Exit code / Code: " + process.exitValue() + "\n\n"
                        + output.strip();
                } catch (Exception error) {
                    return error.getClass().getSimpleName() + " : " + error.getMessage()
                        + "\n\nCheck the absolute Python path. / Vérifie le chemin absolu vers Python.";
                }
            }
            @Override protected void done() {
                test.setEnabled(true);
                // a dialog sized to its text, the text selectable, the command copiable (0.11.3)
                String text;
                try { text = get(); } catch (Exception e) { text = e.toString(); }
                ArchvowTextDialog.show(panel, "Test Archvow", text, command[0], ArchvowI18n.isFrench(project));
            }
        }.execute();
    }

    private ArchvowSettings.Values values() { return ArchvowSettings.get(project).getState(); }
    @Override public boolean isModified() {
        var s = values();
        return !command.getText().equals(s.executable) || !python.getText().equals(s.python)
            || !timeout.getText().equals(Integer.toString(s.timeoutSeconds))
            || !String.valueOf(languageMode.getSelectedItem()).equals(s.languageMode)
            || useProjectInterpreter.isSelected() != s.useProjectInterpreter
            || !installFrom.getText().trim().equals(s.installFrom == null ? "" : s.installFrom);
    }
    @Override public void apply() throws ConfigurationException {
        final int seconds;
        try { seconds = Integer.parseInt(timeout.getText().trim()); }
        catch (NumberFormatException e) { throw new ConfigurationException("Timeout must be an integer / Le délai doit être un entier."); }
        if (seconds < 5 || seconds > 3600) throw new ConfigurationException("Allowed timeout: 5 to 3600 seconds / Délai autorisé : 5 à 3600 secondes.");
        if (!useProjectInterpreter.isSelected() && python.getText().isBlank() && command.getText().isBlank())
            throw new ConfigurationException("Define an Archvow executable or a Python executable / Définis un exécutable Archvow ou Python.");
        var s = values();
        s.executable = command.getText().trim();
        s.python = python.getText().trim();
        s.timeoutSeconds = seconds;
        s.languageMode = String.valueOf(languageMode.getSelectedItem());
        s.useProjectInterpreter = useProjectInterpreter.isSelected();
        s.installFrom = installFrom.getText().trim();
    }
    @Override public void reset() {
        var s = values();
        command.setText(s.executable);
        python.setText(s.python);
        timeout.setText(Integer.toString(s.timeoutSeconds));
        languageMode.setSelectedItem(s.languageMode == null || s.languageMode.isBlank() ? "EN" : s.languageMode);
        useProjectInterpreter.setSelected(s.useProjectInterpreter);
        installFrom.setText(s.installFrom == null ? "" : s.installFrom);
    }
    @Override public void disposeUIResources() { panel = null; }
}
