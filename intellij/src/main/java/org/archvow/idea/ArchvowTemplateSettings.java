package org.archvow.idea;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * {@code template new}: the settings of the template it extends, filled in with the base's values (0.12.1).
 * What the company may set - the defaults of the init answers, the folder of each layer - is editable,
 * within what the base allows (its choices only, its formats); what the base imposes - what it extends,
 * its version, its layers, its choices - is shown, not editable. Only what differs from the base becomes
 * {@code -a KEY=VALUE} and {@code --layer OLD=NEW}. The same as the VS Code page's templateWizard().
 */
final class ArchvowTemplateSettings extends JPanel {
    private final JsonObject base;
    private final boolean french;
    private final JCheckBox advanced = new JCheckBox();
    private final JPanel questions = new JPanel(new GridBagLayout());
    private final Map<String, JComponent> inputs = new LinkedHashMap<>();
    private final Map<String, String> kept = new LinkedHashMap<>();
    private final Map<String, JTextField> layers = new LinkedHashMap<>();

    ArchvowTemplateSettings(JsonObject base, boolean french) {
        super(new BorderLayout(0, 6));
        this.base = base;
        this.french = french;
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(
            (french ? "Réglages de " : "Settings of ") + str(base, "name")), BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.add(line((french ? "Imposé : " : "Imposed: ") + imposed(base, french), false));
        head.add(line(french ? "Les valeurs par défaut de l'entreprise pour les réponses d'init : un projet peut encore les changer (-a). "
            + "Seules celles qui diffèrent de la base sont écrites."
            : "The company defaults of the init answers: a project can still change them (-a). Only those that differ from the base are written.", true));
        advanced.setText(french ? "Afficher les réglages avancés" : "Show advanced settings");
        advanced.setAlignmentX(LEFT_ALIGNMENT);
        advanced.addActionListener(e -> rebuild());
        head.add(advanced);
        add(head, BorderLayout.NORTH);
        add(questions, BorderLayout.CENTER);
        JPanel folders = new JPanel(new GridBagLayout());
        folders.setBorder(BorderFactory.createTitledBorder(french
            ? "Le dossier de chaque couche (src/<package>/<dossier>/, tests/<dossier>/)"
            : "The folder of each layer (src/<package>/<folder>/, tests/<folder>/)"));
        int y = 0;
        for (JsonObject layer : objects(base, "layers")) {
            String name = str(layer, "name");
            JTextField folder = new JTextField(name, 18);
            layers.put(name, folder);
            JLabel label = new JLabel(name);
            label.setFont(new Font(Font.MONOSPACED, Font.PLAIN, label.getFont().getSize()));
            folders.add(label, cell(0, y, 0));
            JPanel right = new JPanel(new BorderLayout(0, 2));
            right.add(folder, BorderLayout.NORTH);
            right.add(line(str(layer, "description"), true), BorderLayout.CENTER);
            folders.add(right, cell(1, y++, 1));
        }
        add(folders, BorderLayout.SOUTH);
        rebuild();
    }

    /** What the base imposes, in one sentence. */
    static String imposed(JsonObject base, boolean french) {
        List<String> chain = new ArrayList<>();
        chain.add(str(base, "name") + "@" + str(base, "version"));
        JsonElement extended = base.get("extends");
        if (extended != null && extended.isJsonArray()) for (JsonElement e : extended.getAsJsonArray()) chain.add(e.getAsString());
        List<String> names = new ArrayList<>();
        for (JsonObject layer : objects(base, "layers")) names.add(str(layer, "name"));
        return french
            ? "s'appuie sur " + String.join(" → ", chain) + ". Le nouveau modèle démarre en version 0.1.0. Ses couches sont celles de la base ("
                + String.join(", ", names) + ") : seuls leurs dossiers peuvent être renommés. Les choix et formats sont ceux de la base."
            : "builds on " + String.join(" → ", chain) + ". The new template starts at version 0.1.0. Its layers are the base's ("
                + String.join(", ", names) + "): only their folders can be renamed. Choices and formats are the base's.";
    }

    private void rebuild() {
        kept.putAll(read());
        inputs.clear();
        questions.removeAll();
        int y = 0;
        for (JsonObject q : objects(base, "questions")) {
            String id = str(q, "id");
            if (id.isBlank() || !(isTrue(q, "ask") || advanced.isSelected())) continue;
            String def = str(q, "default");
            String value = kept.getOrDefault(id, def);
            List<String> choices = new ArrayList<>();
            JsonElement c = q.get("choices");
            if (c != null && c.isJsonArray()) for (JsonElement e : c.getAsJsonArray()) if (e.isJsonPrimitive()) choices.add(e.getAsString());
            JComponent field;
            if (isTrue(q, "multiple") && !choices.isEmpty()) {
                JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
                Set<String> picked = new LinkedHashSet<>(List.of(value.split(",")));
                for (String choice : choices) chips.add(new JCheckBox(choice, picked.contains(choice.trim())));
                field = chips;
            } else if (!choices.isEmpty()) {
                // only the base's choices: a value outside them cannot be typed
                JComboBox<String> combo = new JComboBox<>(choices.toArray(String[]::new));
                if (choices.contains(value)) combo.setSelectedItem(value);
                field = combo;
            } else {
                JTextField free = new JTextField(value, 24);
                String pattern = str(q, "pattern");
                Runnable mark = () -> free.setBorder(valid(pattern, free.getText().trim()) ? UIManager.getBorder("TextField.border")
                    : BorderFactory.createLineBorder(new Color(0xE0, 0x5A, 0x5A), 2));
                free.getDocument().addDocumentListener(new DocumentListener() {
                    @Override public void insertUpdate(DocumentEvent e) { mark.run(); }
                    @Override public void removeUpdate(DocumentEvent e) { mark.run(); }
                    @Override public void changedUpdate(DocumentEvent e) { mark.run(); }
                });
                mark.run();
                field = free;
            }
            inputs.put(id, field);
            JLabel label = new JLabel(id);
            label.setToolTipText(str(q, "prompt"));
            questions.add(label, cell(0, y, 0));
            JPanel block = new JPanel(new BorderLayout(0, 2));
            block.add(field, BorderLayout.NORTH);
            String hint = str(q, "prompt") + (def.isBlank() ? "" : (french ? " (base : " : " (base: ") + def + ")")
                + (str(q, "pattern_hint").isBlank() ? "" : (french ? " · Format : " : " · Format: ") + str(q, "pattern_hint"));
            block.add(line(hint, true), BorderLayout.CENTER);
            questions.add(block, cell(1, y++, 1));
        }
        questions.revalidate();
        questions.repaint();
    }

    private static boolean valid(String pattern, String value) {
        if (pattern.isBlank() || value.isEmpty()) return true;
        try { return Pattern.compile(pattern).matcher(value).find(); }
        catch (PatternSyntaxException e) { return true; }
    }

    private Map<String, String> read() {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, JComponent> e : inputs.entrySet()) {
            JComponent field = e.getValue();
            if (field instanceof JComboBox<?> combo) values.put(e.getKey(), String.valueOf(combo.getSelectedItem()));
            else if (field instanceof JTextField text) values.put(e.getKey(), text.getText().trim());
            else if (field instanceof JPanel chips) {
                List<String> picked = new ArrayList<>();
                for (Component c : chips.getComponents()) if (c instanceof JCheckBox box && box.isSelected()) picked.add(box.getText());
                values.put(e.getKey(), picked.contains("none") ? "none" : picked.contains("all") ? "all" : String.join(",", picked));
            }
        }
        return values;
    }

    /** The {@code -a KEY=VALUE} of what differs from the base. */
    List<String> answers() {
        Map<String, String> values = new LinkedHashMap<>(kept);
        values.putAll(read());
        List<String> out = new ArrayList<>();
        for (JsonObject q : objects(base, "questions")) {
            String id = str(q, "id");
            if (values.containsKey(id) && !values.get(id).equals(str(q, "default"))) out.add(id + "=" + values.get(id));
        }
        return out;
    }

    /** A value the base's format refuses, as a message ('' when every value fits). */
    String problem() {
        Map<String, String> values = new LinkedHashMap<>(kept);
        values.putAll(read());
        for (JsonObject q : objects(base, "questions")) {
            String id = str(q, "id"), value = values.getOrDefault(id, "");
            if (!value.equals(str(q, "default")) && !valid(str(q, "pattern"), value))
                return id + (french ? " : " : ": ") + (str(q, "pattern_hint").isBlank()
                    ? (french ? "pas au format de la base" : "not in the format of the base") : str(q, "pattern_hint"));
        }
        return "";
    }

    /** The {@code --layer OLD=NEW} of the folders renamed. */
    List<String> layerFolders() {
        List<String> out = new ArrayList<>();
        layers.forEach((name, field) -> {
            String folder = field.getText().trim();
            if (!folder.isEmpty() && !folder.equals(name)) out.add(name + "=" + folder);
        });
        return out;
    }

    private static ArchvowWrapLabel line(String text, boolean muted) {
        ArchvowWrapLabel label = new ArchvowWrapLabel();
        label.setText(text);
        label.setOpaque(false);
        label.setAlignmentX(LEFT_ALIGNMENT);
        if (muted) {
            label.setFont(label.getFont().deriveFont(11f));
            Color grey = UIManager.getColor("Label.disabledForeground");
            if (grey != null) label.setForeground(grey);
        }
        return label;
    }

    private static GridBagConstraints cell(int x, int y, double weight) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x; c.gridy = y; c.weightx = weight;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = weight > 0 ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        c.insets = new Insets(5, 4, 3, 8);
        return c;
    }

    private static String str(JsonObject o, String key) {
        JsonElement v = o == null ? null : o.get(key);
        return v == null || v.isJsonNull() ? "" : v.isJsonPrimitive() ? v.getAsString() : v.toString();
    }

    private static boolean isTrue(JsonObject o, String key) {
        JsonElement v = o == null ? null : o.get(key);
        return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean() && v.getAsBoolean();
    }

    private static List<JsonObject> objects(JsonObject o, String key) {
        List<JsonObject> out = new ArrayList<>();
        JsonElement v = o == null ? null : o.get(key);
        if (v != null && v.isJsonArray()) for (JsonElement e : v.getAsJsonArray()) if (e.isJsonObject()) out.add(e.getAsJsonObject());
        return out;
    }
}
