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
    private final JComboBox<String> languageMode = new JComboBox<>(ArchvowI18n.modes());

    public ArchvowSettingsConfigurable(Project project) { this.project = project; }
    @Override public @Nls String getDisplayName() { return "Archvow"; }

    @Override public @Nullable JComponent createComponent() {
        panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Archvow configuration / Configuration Archvow"));
        row(form, 0, "Archvow executable / Exécutable Archvow", command, null,
            "Optional. Use this for a standalone Archvow executable. / Optionnel. À utiliser si Archvow est un exécutable autonome.");
        JButton browsePython = new JButton("Browse…");
        browsePython.addActionListener(e -> choosePython());
        row(form, 1, "Python executable / Exécutable Python", python, browsePython,
            "Preferred mode. Enter only the Python executable path, for example /path/to/venv/bin/python3. / Mode recommandé. Saisir uniquement le chemin de l'exécutable Python.");
        row(form, 2, "Timeout (seconds) / Délai (secondes)", timeout, null,
            "Allowed range: 5 to 3600. / Valeur autorisée : 5 à 3600.");
        row(form, 3, "Language / Langue", languageMode, null,
            "IDE = follow IntelliJ or PyCharm language. / IDE = suit la langue de l'IDE.");

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
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (!python.getText().isBlank()) chooser.setSelectedFile(new File(python.getText().trim()));
        if (chooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
            python.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void explainPythonField() {
        JOptionPane.showMessageDialog(panel,
            "Recommended value / Valeur recommandée:\n\n"
                + "  /path/to/venv/bin/python3\n\n"
                + "Accepted for backward compatibility / Accepté pour compatibilité :\n\n"
                + "  python3 -m archvow\n\n"
                + "Do not add other arguments in this field. / Ne pas ajouter d'autres arguments dans ce champ.",
            "Archvow Python configuration", JOptionPane.INFORMATION_MESSAGE);
    }

    private void testInterpreter(JButton test) {
        String configuredExecutable = command.getText().trim();
        String configuredPython = python.getText().trim();
        String root = project.getBasePath();
        test.setEnabled(false);
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() {
                try {
                    if (root == null || !new File(root).isDirectory())
                        return "IntelliJ project folder not found / Dossier projet introuvable: " + root;
                    List<String> argv = ArchvowProcessCommand.build(
                        configuredExecutable, configuredPython, root, List.of("--version"));
                    ProcessBuilder builder = new ProcessBuilder(argv).directory(new File(root))
                        .redirectErrorStream(true);
                    builder.environment().put("NO_COLOR", "1");
                    builder.environment().put("PYTHONIOENCODING", "utf-8");
                    Process process = builder.start();
                    if (!process.waitFor(10, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                        return "Timeout after 10 seconds / Délai dépassé après 10 secondes.";
                    }
                    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    return "Command / Commande: " + String.join(" ", argv) + "\n"
                        + "Exit code / Code: " + process.exitValue() + "\n\n"
                        + output.strip();
                } catch (Exception error) {
                    return error.getClass().getSimpleName() + " : " + error.getMessage()
                        + "\n\nCheck the absolute Python path. / Vérifie le chemin absolu vers Python.";
                }
            }
            @Override protected void done() {
                test.setEnabled(true);
                try { JOptionPane.showMessageDialog(panel, get(), "Test Archvow", JOptionPane.INFORMATION_MESSAGE); }
                catch (Exception e) { JOptionPane.showMessageDialog(panel, e.toString(), "Test Archvow", JOptionPane.ERROR_MESSAGE); }
            }
        }.execute();
    }

    private ArchvowSettings.Values values() { return ArchvowSettings.get(project).getState(); }
    @Override public boolean isModified() {
        var s = values();
        return !command.getText().equals(s.executable) || !python.getText().equals(s.python)
            || !timeout.getText().equals(Integer.toString(s.timeoutSeconds))
            || !String.valueOf(languageMode.getSelectedItem()).equals(s.languageMode);
    }
    @Override public void apply() throws ConfigurationException {
        final int seconds;
        try { seconds = Integer.parseInt(timeout.getText().trim()); }
        catch (NumberFormatException e) { throw new ConfigurationException("Timeout must be an integer / Le délai doit être un entier."); }
        if (seconds < 5 || seconds > 3600) throw new ConfigurationException("Allowed timeout: 5 to 3600 seconds / Délai autorisé : 5 à 3600 secondes.");
        if (python.getText().isBlank() && command.getText().isBlank())
            throw new ConfigurationException("Define an Archvow executable or a Python executable / Définis un exécutable Archvow ou Python.");
        var s = values();
        s.executable = command.getText().trim();
        s.python = python.getText().trim();
        s.timeoutSeconds = seconds;
        s.languageMode = String.valueOf(languageMode.getSelectedItem());
    }
    @Override public void reset() {
        var s = values();
        command.setText(s.executable);
        python.setText(s.python);
        timeout.setText(Integer.toString(s.timeoutSeconds));
        languageMode.setSelectedItem(s.languageMode == null || s.languageMode.isBlank() ? "IDE" : s.languageMode);
    }
    @Override public void disposeUIResources() { panel = null; }
}
