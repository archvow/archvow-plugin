package org.archvow.idea;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Answer with a table (optional, 0.15.0): a reversible plain-text draft for two kinds of decisions, the
 * same as VS Code's page. Columns (nullable, personal data, type): one row per column with Never null /
 * Nullable / Unknown and Yes / No / Unknown as buttons, notes, and a bin to remove it - only what the
 * question asks about is shown. Parameters (`name: type = default`, the config and settings questions):
 * one row per parameter, no default = required. It never saves: "Use this answer" fills the answer field.
 */
final class ArchvowColumnAnswerPanel extends JPanel {
    enum Mode { NONE, COLUMNS, PARAMETERS }

    /** One row of the table: column (name, type, nullable, personal, note) or parameter (name, type, default, note). */
    static final class Row {
        String name = "", type = "", nullable = "unknown", personal = "unknown", value = "", note = "";
    }

    private final JTextArea target;
    private final List<Row> rows = new ArrayList<>();
    private final JPanel grid = new JPanel(new GridBagLayout());
    private final JTextArea preview = new JTextArea(3, 30);
    private final JButton add = new JButton();
    private final JButton use = new JButton();
    private final JLabel note = new JLabel();
    private final JLabel previewLabel = new JLabel();
    private boolean french;
    private String question = "";
    private Mode mode = Mode.NONE;
    private boolean asksType, asksNullable, asksPersonal;

    ArchvowColumnAnswerPanel(JTextArea target) {
        super(new BorderLayout(0, 5));
        this.target = target;
        grid.setOpaque(false);
        add.addActionListener(e -> { rows.add(blank()); rebuild(); });
        use.addActionListener(e -> {
            String result = answer();
            if (!result.isBlank()) { target.setText(result); target.requestFocusInWindow(); }
        });
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        actions.setOpaque(false);
        actions.add(add);
        actions.add(use);
        preview.setEditable(false);
        preview.setLineWrap(true);
        preview.setWrapStyleWord(true);
        preview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, preview.getFont().getSize()));
        note.setForeground(UIManager.getColor("Label.disabledForeground"));
        previewLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.setOpaque(false);
        top.add(note, BorderLayout.NORTH);
        top.add(grid, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        bottom.setOpaque(false);
        bottom.add(actions, BorderLayout.NORTH);
        JPanel shown = new JPanel(new BorderLayout(0, 2));
        shown.setOpaque(false);
        shown.add(previewLabel, BorderLayout.NORTH);
        shown.add(new JScrollPane(preview), BorderLayout.CENTER);
        bottom.add(shown, BorderLayout.CENTER);
        add(top, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        setBorder(BorderFactory.createTitledBorder("Answer with a table (optional)"));
        setVisible(false);
    }

    private String t(String en, String fr) { return french ? fr : en; }

    private Row blank() {
        Row r = new Row();
        if (mode == Mode.PARAMETERS) r.type = "str";
        return r;
    }

    /** Which table the question asks for, if any (the same rules as VS Code's canEditParameters / canEditColumns). */
    static Mode modeFor(String question) {
        if (question == null) return Mode.NONE;
        String q = question.toLowerCase(Locale.ROOT);
        JsonObject parameters = ArchvowForms.section("smart_answers").getAsJsonObject("parameters");
        JsonArray markers = parameters == null ? null : parameters.getAsJsonArray("markers");
        List<String> words = new ArrayList<>();
        if (markers != null) for (JsonElement m : markers) words.add(m.getAsString().toLowerCase(Locale.ROOT));
        if (words.isEmpty()) words = List.of("name: type", "nom: type");
        for (String m : words) if (q.contains(m)) return Mode.PARAMETERS;
        return ArchvowForms.supportsColumnAnswer(question) ? Mode.COLUMNS : Mode.NONE;
    }

    void showFor(JsonObject view, JsonObject row, boolean fr) {
        french = fr;
        question = ArchvowGrammar.str(row, "question");
        mode = modeFor(question);
        setVisible(mode != Mode.NONE);
        if (mode == Mode.NONE) return;
        String q = question.toLowerCase(Locale.ROOT);
        asksType = Pattern.compile("(\\btypes?\\b|datatype|data type)").matcher(q).find();
        asksNullable = Pattern.compile("(null|requir|option|obligat|facultat)").matcher(q).find();
        asksPersonal = Pattern.compile("(person|privat|gdpr|rgpd)").matcher(q).find();
        rows.clear();
        if (mode == Mode.COLUMNS) {
            Map<String, Row> names = new LinkedHashMap<>();
            for (JsonObject group : ArchvowGrammar.objects(view, "known_columns"))
                for (String name : ArchvowGrammar.strings(group, "columns")) if (!name.isBlank()) names.computeIfAbsent(name.trim(), n -> { Row r = new Row(); r.name = n; return r; });
            for (JsonObject col : ArchvowGrammar.objects(view, "columns")) {
                String name = ArchvowGrammar.str(col, "name");
                if (name.isBlank()) continue;
                Row r = names.computeIfAbsent(name, n -> { Row x = new Row(); x.name = n; return x; });
                String cls = ArchvowGrammar.str(col, "class");
                r.personal = "personal".equals(cls) || "special".equals(cls) ? "yes" : "none".equals(cls) ? "no" : "unknown";
                if (!ArchvowGrammar.str(col, "type").isBlank()) r.type = ArchvowGrammar.str(col, "type");
            }
            int count = 0;
            for (Row r : names.values()) if (++count <= 60) rows.add(r);
        } else {
            String answer = ArchvowGrammar.str(row, "answer");
            if (answer.isBlank()) answer = ArchvowGrammar.str(row, "proposed");
            if (answer.isBlank()) answer = ArchvowGrammar.str(row, "suggested");
            List<Row> parsed = parseParameters(answer);
            if (parsed != null) rows.addAll(parsed);
        }
        if (rows.isEmpty()) rows.add(blank());
        setBorder(BorderFactory.createTitledBorder(t("Answer with a table (optional)", "Répondre avec un tableau (facultatif)")));
        add.setText(mode == Mode.COLUMNS ? t("+ Add column", "+ Ajouter une colonne") : t("+ Add parameter", "+ Ajouter un paramètre"));
        use.setText(t("Use this answer", "Utiliser cette réponse"));
        previewLabel.setText(t("Answer preview", "Aperçu de la réponse"));
        note.setText(mode == Mode.COLUMNS
            ? t("One row per column: what is not known stays Unknown. Review the answer before saving.",
                "Une ligne par colonne : ce qui n'est pas connu reste Inconnu. Relire la réponse avant d'enregistrer.")
            : t("One row per parameter. No default = required. Notes go in brackets after it.",
                "Une ligne par paramètre. Sans défaut = obligatoire. Les notes vont entre parenthèses après."));
        rebuild();
    }

    Mode mode() { return mode; }
    int rowCount() { return rows.size(); }
    Row row(int i) { return rows.get(i); }
    void removeRow(int i) { rows.remove(i); rebuild(); }
    List<String> headers() {
        List<String> out = new ArrayList<>();
        for (Component c : grid.getComponents()) if (c instanceof JLabel l && Boolean.TRUE.equals(l.getClientProperty("archvow.head"))) out.add(l.getText());
        return out;
    }
    /** The bins of the rows (one per row), for the tests. */
    List<JButton> bins() {
        List<JButton> out = new ArrayList<>();
        for (Component c : grid.getComponents()) if (c instanceof JButton b && Boolean.TRUE.equals(b.getClientProperty("archvow.bin"))) out.add(b);
        return out;
    }

    private void rebuild() {
        grid.removeAll();
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0; c.insets = new Insets(2, 0, 2, 6); c.anchor = GridBagConstraints.WEST; c.fill = GridBagConstraints.HORIZONTAL;
        List<String> heads = new ArrayList<>();
        if (mode == Mode.COLUMNS) {
            heads.add(t("Column", "Colonne"));
            if (asksType) heads.add("Type");
            if (asksNullable) heads.add(t("Nullable?", "Nullable ?"));
            if (asksPersonal) heads.add(t("Personal data?", "Donnée personnelle ?"));
        } else {
            heads.add(t("Parameter", "Paramètre")); heads.add("Type"); heads.add(t("Default", "Défaut")); heads.add("");
        }
        heads.add("Notes");
        heads.add("");
        for (int i = 0; i < heads.size(); i++) {
            JLabel h = new JLabel(heads.get(i));
            h.setForeground(UIManager.getColor("Label.disabledForeground"));
            h.putClientProperty("archvow.head", Boolean.TRUE);
            c.gridx = i; c.weightx = 0;
            grid.add(h, c);
        }
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            final int index = r;
            c.gridy = r + 1;
            int x = 0;
            c.gridx = x++; c.weightx = 1;
            grid.add(field(row.name, v -> row.name = v, mode == Mode.PARAMETERS ? "input_path" : t("column", "colonne")), c);
            if (mode == Mode.COLUMNS) {
                if (asksType) { c.gridx = x++; c.weightx = .6; grid.add(typeField(row, List.of("string", "integer", "long", "float", "double", "decimal", "boolean", "date", "timestamp")), c); }
                c.weightx = 0;
                if (asksNullable) {
                    c.gridx = x++;
                    grid.add(segments(List.of(new String[] {"no", t("Never null", "Jamais nul")}, new String[] {"yes", "Nullable"},
                        new String[] {"unknown", t("Unknown", "Inconnu")}), row.nullable, v -> row.nullable = v), c);
                }
                if (asksPersonal) {
                    c.gridx = x++;
                    grid.add(segments(List.of(new String[] {"yes", t("Yes", "Oui")}, new String[] {"no", t("No", "Non")},
                        new String[] {"unknown", t("Unknown", "Inconnu")}), row.personal, v -> row.personal = v), c);
                }
            } else {
                c.gridx = x++; c.weightx = .5;
                JsonObject parameters = ArchvowForms.section("smart_answers").getAsJsonObject("parameters");
                List<String> types = parameters != null && parameters.has("types") ? ArchvowGrammar.strings(parameters, "types") : List.of("str", "int", "float", "bool", "date");
                grid.add(typeField(row, types), c);
                JLabel required = new JLabel();
                Runnable chip = () -> {
                    boolean optional = !row.value.isBlank();
                    required.setText(optional ? t("optional", "facultatif") : t("required", "obligatoire"));
                    required.setForeground(optional ? UIManager.getColor("Label.disabledForeground") : ArchvowStatusPalette.statusColor(true));
                };
                c.gridx = x++; c.weightx = 1;
                grid.add(field(row.value, v -> { row.value = v; chip.run(); }, t("none: required", "aucun : obligatoire")), c);
                chip.run();
                c.gridx = x++; c.weightx = 0;
                grid.add(required, c);
            }
            c.gridx = x++; c.weightx = 1;
            grid.add(field(row.note, v -> row.note = v, t("optional", "facultatif")), c);
            JButton bin = new JButton(ArchvowTreeIcons.icon("remove", null));
            bin.putClientProperty("archvow.bin", Boolean.TRUE);
            bin.setToolTipText(mode == Mode.COLUMNS ? t("Remove this column from the table", "Retirer cette colonne du tableau")
                : t("Remove this parameter from the table", "Retirer ce paramètre du tableau"));
            bin.getAccessibleContext().setAccessibleName(t("Remove", "Retirer"));
            bin.setBorderPainted(false); bin.setContentAreaFilled(false); bin.setFocusPainted(false);
            bin.setMargin(new Insets(1, 3, 1, 3));
            bin.addActionListener(e -> removeRow(index));
            c.gridx = x; c.weightx = 0; c.fill = GridBagConstraints.NONE;
            grid.add(bin, c);
            c.fill = GridBagConstraints.HORIZONTAL;
        }
        refreshPreview();
        grid.revalidate(); grid.repaint();
        revalidate(); repaint();
    }

    private interface Setter { void set(String value); }

    private JTextField field(String value, Setter setter, String placeholder) {
        JTextField f = new JTextField(value, 8);
        f.setToolTipText(placeholder);
        f.putClientProperty("JTextField.placeholderText", placeholder);
        f.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() { setter.set(f.getText()); refreshPreview(); }
            @Override public void insertUpdate(DocumentEvent e) { changed(); }
            @Override public void removeUpdate(DocumentEvent e) { changed(); }
            @Override public void changedUpdate(DocumentEvent e) { changed(); }
        });
        return f;
    }

    private JComboBox<String> typeField(Row row, List<String> types) {
        List<String> offered = new ArrayList<>();
        offered.add("");
        offered.addAll(types);
        JComboBox<String> box = new JComboBox<>(offered.toArray(String[]::new));
        box.setEditable(true);  // suggestions only: a template's own types are typed freely
        box.setSelectedItem(row.type);
        box.addActionListener(e -> { row.type = String.valueOf(box.getSelectedItem() == null ? "" : box.getSelectedItem()); refreshPreview(); });
        if (box.getEditor().getEditorComponent() instanceof JTextField text) {
            text.getDocument().addDocumentListener(new DocumentListener() {
                private void changed() { row.type = text.getText(); refreshPreview(); }
                @Override public void insertUpdate(DocumentEvent e) { changed(); }
                @Override public void removeUpdate(DocumentEvent e) { changed(); }
                @Override public void changedUpdate(DocumentEvent e) { changed(); }
            });
        }
        return box;
    }

    /** Three fixed answers as buttons, the chosen one pressed; `options` are [value, label]. */
    private ArchvowSegmented segments(List<String[]> options, String value, Setter setter) {
        List<String> labels = new ArrayList<>();
        String initial = "";
        for (String[] o : options) { labels.add(o[1]); if (o[0].equals(value)) initial = o[1]; }
        ArchvowSegmented s = new ArchvowSegmented(labels, initial, true);
        s.onChange(() -> {
            for (String[] o : options) if (o[1].equals(s.value())) setter.set(o[0]);
            refreshPreview();
        });
        return s;
    }

    private void refreshPreview() {
        String text = answer();
        preview.setText(text.isBlank() ? (mode == Mode.PARAMETERS ? t("Add a parameter to prepare a response.", "Ajoute un paramètre pour préparer la réponse.")
            : t("Add a column to prepare a response.", "Ajoute une colonne pour préparer la réponse.")) : text);
        use.setEnabled(!text.isBlank());
    }

    /** The answer the table writes: as VS Code's structuredAnswer / parametersAnswer, word for word. */
    String answer() {
        List<String> out = new ArrayList<>();
        for (Row r : rows) {
            String name = r.name.trim();
            if (name.isEmpty()) continue;
            String note = r.note.trim();
            if (mode == Mode.PARAMETERS) {
                String type = r.type.trim().isEmpty() ? "str" : r.type.trim();
                String value = r.value.trim();
                note = note.replaceAll("[()]", "");
                out.add(name + ": " + type + (value.isEmpty() ? "" : " = " + value) + (note.isEmpty() ? "" : " (" + note + ")"));
                continue;
            }
            List<String> parts = new ArrayList<>();
            if (asksType) parts.add(r.type.trim().isEmpty() ? t("type unknown", "type inconnu") : r.type.trim());
            if (asksNullable) parts.add("yes".equals(r.nullable) ? "nullable" : "no".equals(r.nullable) ? t("not null", "non nullable") : t("nullability unknown", "nullabilité inconnue"));
            if (asksPersonal) parts.add("yes".equals(r.personal) ? t("personal data", "donnée personnelle") : "no".equals(r.personal) ? t("not personal data", "non personnel")
                : t("personal-data classification unknown", "classification des données inconnue"));
            out.add(name + ": " + String.join(", ", parts) + (note.isEmpty() ? "" : " (" + note + ")"));
        }
        return String.join(mode == Mode.PARAMETERS ? ", " : "\n", out);
    }

    private static final Pattern PARAMETER = Pattern.compile("^\\s*([a-z_][a-z0-9_]*)\\s*:\\s*([^=(]+?)\\s*(?:=\\s*([^()]+?))?\\s*(\\((.*)\\))?\\s*$");
    private static final Pattern TYPE = Pattern.compile("^[A-Za-z_][\\w.]*(\\[[\\w., |]*\\])?( *\\| *[A-Za-z_][\\w.]*)*$");

    /** `input_path: str, run_date: date = today (the day)` -> rows; null for an answer in sentences (as VS Code's parseParameters). */
    static List<Row> parseParameters(String text) {
        String flat = text == null ? "" : text.trim().replaceAll("\\.$", "");
        List<Row> out = new ArrayList<>();
        if (flat.isEmpty() || flat.matches("(?i)none|aucun|aucune|no|non|-")) return out;
        for (String part : flat.split("[,;\\n]\\s*(?=[a-z_][a-z0-9_]*\\s*:)")) {
            Matcher m = PARAMETER.matcher(part);
            if (!m.matches() || !TYPE.matcher(m.group(2).trim()).matches()) return null;
            Row r = new Row();
            r.name = m.group(1); r.type = m.group(2).trim(); r.value = m.group(3) == null ? "" : m.group(3).trim();
            String note = m.group(5) == null ? "" : m.group(5);
            r.note = note.matches("(?i)required|obligatoire") ? "" : note.trim();
            out.add(r);
        }
        return out;
    }
}
