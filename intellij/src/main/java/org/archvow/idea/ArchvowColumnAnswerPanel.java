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
 * one row per parameter, Required ticked or a default (1.1.0: a checkbox column, no floating "required" word).
 * It never saves. 1.1.0: no preview and no "Use this answer" any more - the table writes the answer field as you
 * edit (one text, written once); once that field is edited by hand, the table stops writing over it and offers
 * "Write it from the table again".
 */
final class ArchvowColumnAnswerPanel extends JPanel {
    enum Mode { NONE, COLUMNS, PARAMETERS }

    /** One row of the table: column (name, type, nullable, personal, note) or parameter (name, type, default, note). */
    static final class Row {
        String name = "", type = "", nullable = "unknown", personal = "unknown", value = "", note = "";
        /** A parameter: no default (1.1.0, the Required column); read back from an answer with no default. */
        boolean required = true;
    }

    private final JTextArea target;
    private final List<Row> rows = new ArrayList<>();
    private final JPanel grid = new JPanel(new GridBagLayout());
    private final JButton add = new JButton();
    private final ArchvowWrapLabel note = new ArchvowWrapLabel();  // 1.2.1: it wraps (a JLabel was cut in a narrow pane)
    /** "Edited by hand: the table no longer writes over it · Write it from the table again" (1.1.0). */
    private final JPanel handLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    private final JLabel handWords = new JLabel();
    private final JButton writeAgain = ArchvowSplitView.link("");
    /** The answer field was changed by the person, not by the table: the table leaves it alone. */
    private boolean byHand;
    /** The table is writing the field (its own change, not the person's). */
    private boolean writing;
    private boolean french;
    private String question = "";
    private Mode mode = Mode.NONE;
    private boolean asksType, asksNullable, asksPersonal;

    ArchvowColumnAnswerPanel(JTextArea target) {
        super(new BorderLayout(0, 5));
        this.target = target;
        grid.setOpaque(false);
        add.addActionListener(e -> { rows.add(blank()); rebuild(); });
        writeAgain.addActionListener(e -> { byHand = false; edited(); });
        target.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                if (writing || !isVisible()) return;
                byHand = true;
                handLine.setVisible(true);
            }
            @Override public void insertUpdate(DocumentEvent e) { changed(); }
            @Override public void removeUpdate(DocumentEvent e) { changed(); }
            @Override public void changedUpdate(DocumentEvent e) { }
        });
        JPanel actions = new JPanel(new ArchvowWrapLayout(FlowLayout.LEFT, 5, 0));
        actions.setOpaque(false);
        actions.add(add);
        note.setForeground(UIManager.getColor("Label.disabledForeground"));
        note.setOpaque(false);
        handWords.setForeground(UIManager.getColor("Label.disabledForeground"));
        handLine.setOpaque(false);
        handLine.add(handWords);
        handLine.add(writeAgain);
        handLine.setVisible(false);
        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.setOpaque(false);
        top.add(note, BorderLayout.NORTH);
        top.add(grid, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        bottom.setOpaque(false);
        bottom.add(actions, BorderLayout.NORTH);
        bottom.add(handLine, BorderLayout.SOUTH);
        add(top, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        setBorder(BorderFactory.createTitledBorder("Answer with a table (optional)"));
        setVisible(false);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                if (mode != Mode.NONE && stackedFor(getWidth()) != stacked) SwingUtilities.invokeLater(ArchvowColumnAnswerPanel.this::fitWidth);
            }
        });
    }

    boolean stackedForTest() { return stacked; }

    /** Rows side by side or stacked, as the width now asks. */
    void fitWidth() {
        if (mode != Mode.NONE && stackedFor(getWidth()) != stacked) rebuild();
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
        byHand = false;
        handLine.setVisible(false);
        String q = question.toLowerCase(Locale.ROOT);
        asksType = Pattern.compile("(\\btypes?\\b|datatype|data type)").matcher(q).find();
        asksNullable = Pattern.compile("(null|requir|option|obligat|facultat)").matcher(q).find();
        asksPersonal = Pattern.compile("(person|privat|sensitiv|sensibl)").matcher(q).find();
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
        handWords.setText(t("Edited by hand: the table no longer writes over it ·", "Modifiée à la main : le tableau ne l'écrase plus ·"));
        writeAgain.setText(t("Write it from the table again", "La réécrire depuis le tableau"));
        note.setText(mode == Mode.COLUMNS
            ? t("One row per column: what is not known stays Unknown. The table writes your answer below as you edit it.",
                "Une ligne par colonne : ce qui n'est pas connu reste Inconnu. Le tableau écrit ta réponse ci-dessous au fil des modifications.")
            : t("One row per parameter: Required, or a default. The table writes your answer below as you edit it.",
                "Une ligne par paramètre : Obligatoire, ou une valeur par défaut. Le tableau écrit ta réponse ci-dessous au fil des modifications."));
        rebuild();
    }

    Mode mode() { return mode; }
    int rowCount() { return rows.size(); }
    Row row(int i) { return rows.get(i); }
    void removeRow(int i) { rows.remove(i); rebuild(); edited(); }
    /** For the tests: the field was changed by hand (the table no longer writes over it). */
    boolean byHand() { return byHand; }
    /** For the tests: a row's Required box. */
    void setRequired(int i, boolean required) { rows.get(i).required = required; rebuild(); edited(); }
    void setDefault(int i, String value) { rows.get(i).value = value; edited(); }
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
        building = true;
        try { rebuildRows(); } finally { building = false; }
    }

    /** One cell of a row: its label (the column's head), the field, how much it widens, and whether it takes a
     *  whole line when the table is stacked. */
    private record Cell(String label, JComponent field, double weight, boolean wide) { }

    /** 1.2.1: under this width the rows are stacked, each a small form with its labels (a grid of 6 columns at
     *  minimum size made the fields a few pixels wide, "Pa…", "…"). */
    static final int STACK_BELOW = 520;
    private boolean stacked;

    private boolean stackedFor(int width) { return width > 0 && width < STACK_BELOW; }

    private void rebuildRows() {
        grid.removeAll();
        stacked = stackedFor(getWidth());
        List<String> heads = new ArrayList<>();
        if (mode == Mode.COLUMNS) {
            heads.add(t("Column", "Colonne"));
            if (asksType) heads.add("Type");
            if (asksNullable) heads.add(t("Nullable?", "Nullable ?"));
            if (asksPersonal) heads.add(t("Personal data?", "Donnée personnelle ?"));
        } else {
            heads.add(t("Parameter", "Paramètre")); heads.add("Type"); heads.add(t("Required", "Obligatoire")); heads.add(t("Default", "Défaut"));
        }
        heads.add("Notes");
        heads.add("");
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0; c.insets = new Insets(2, 0, 2, 6); c.anchor = GridBagConstraints.WEST; c.fill = GridBagConstraints.HORIZONTAL;
        if (!stacked) {
            for (int i = 0; i < heads.size(); i++) {
                JLabel h = new JLabel(heads.get(i));
                h.setForeground(UIManager.getColor("Label.disabledForeground"));
                h.putClientProperty("archvow.head", Boolean.TRUE);
                c.gridx = i; c.weightx = 0;
                grid.add(h, c);
            }
        }
        for (int r = 0; r < rows.size(); r++) {
            List<Cell> cells = cells(rows.get(r), r, heads);
            if (!stacked) {
                c.gridy = r + 1;
                for (int x = 0; x < cells.size(); x++) {
                    Cell cell = cells.get(x);
                    c.gridx = x; c.weightx = cell.weight();
                    c.fill = cell.label().isEmpty() ? GridBagConstraints.NONE : GridBagConstraints.HORIZONTAL;
                    grid.add(cell.field(), c);
                }
                c.fill = GridBagConstraints.HORIZONTAL;
            } else {
                c.gridy = r; c.gridx = 0; c.weightx = 1; c.gridwidth = 1; c.insets = new Insets(0, 0, 0, 0);
                grid.add(stackedRow(cells, r), c);
            }
        }
        grid.revalidate(); grid.repaint();
        revalidate(); repaint();
    }

    /** A row stacked: two cells a line, a wide one alone on its line, the label above each, the bin at the end. */
    private JPanel stackedRow(List<Cell> cells, int index) {
        JPanel line = new JPanel(new GridBagLayout());
        line.setOpaque(false);
        line.putClientProperty("archvow.stackedRow", Boolean.TRUE);
        line.setBorder(BorderFactory.createCompoundBorder(
            index == 0 ? BorderFactory.createEmptyBorder() : BorderFactory.createMatteBorder(1, 0, 0, 0, separator()),
            BorderFactory.createEmptyBorder(6, 0, 6, 0)));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1; c.gridy = 0; c.insets = new Insets(0, 0, 0, 8);
        int x = 0;
        for (Cell cell : cells) {
            if (cell.label().isEmpty()) {  // the bin: alone at the end, on the right
                if (x != 0) { c.gridy++; x = 0; }
                c.gridx = 0; c.gridwidth = 2; c.fill = GridBagConstraints.NONE; c.anchor = GridBagConstraints.EAST;
                line.add(cell.field(), c);
                c.fill = GridBagConstraints.HORIZONTAL; c.anchor = GridBagConstraints.WEST; c.gridwidth = 1;
                continue;
            }
            if (cell.wide() && x != 0) { c.gridy++; x = 0; }
            JPanel box = new JPanel(new BorderLayout(0, 2));
            box.setOpaque(false);
            JLabel label = new JLabel(cell.label());
            label.setForeground(UIManager.getColor("Label.disabledForeground"));
            label.putClientProperty("archvow.head", Boolean.TRUE);
            box.add(label, BorderLayout.NORTH);
            JPanel left = new JPanel(new BorderLayout());
            left.setOpaque(false);
            left.add(cell.field(), cell.field() instanceof JTextField ? BorderLayout.CENTER : BorderLayout.WEST);
            box.add(left, BorderLayout.CENTER);
            c.gridx = x; c.gridwidth = cell.wide() ? 2 : 1;
            c.insets = new Insets(2, 0, 2, x == 0 && !cell.wide() ? 8 : 0);
            line.add(box, c);
            if (cell.wide() || x == 1) { c.gridy++; x = 0; } else x = 1;
        }
        return line;
    }

    private static Color separator() {
        Color color = UIManager.getColor("Separator.foreground");
        return color == null ? new Color(128, 128, 128, 90) : color;
    }

    /** The cells of one row, in the order of the heads. */
    private List<Cell> cells(Row row, int index, List<String> heads) {
        List<Cell> cells = new ArrayList<>();
        int h = 0;
        cells.add(new Cell(heads.get(h++), field(row.name, v -> row.name = v, mode == Mode.PARAMETERS ? "input_path" : t("column", "colonne")), 1, true));
        if (mode == Mode.COLUMNS) {
            if (asksType) cells.add(new Cell(heads.get(h++), typeField(row, List.of("string", "integer", "long", "float", "double", "decimal", "boolean", "date", "timestamp")), .6, false));
            if (asksNullable) cells.add(new Cell(heads.get(h++), segments(List.of(new String[] {"no", t("Never null", "Jamais nul")}, new String[] {"yes", "Nullable"},
                new String[] {"unknown", t("Unknown", "Inconnu")}), row.nullable, v -> row.nullable = v), 0, true));
            if (asksPersonal) cells.add(new Cell(heads.get(h++), segments(List.of(new String[] {"yes", t("Yes", "Oui")}, new String[] {"no", t("No", "Non")},
                new String[] {"unknown", t("Unknown", "Inconnu")}), row.personal, v -> row.personal = v), 0, true));
        } else {
            JsonObject parameters = ArchvowForms.section("smart_answers").getAsJsonObject("parameters");
            List<String> types = parameters != null && parameters.has("types") ? ArchvowGrammar.strings(parameters, "types") : List.of("str", "int", "float", "bool", "date");
            cells.add(new Cell(heads.get(h++), typeField(row, types), .5, false));
            // 1.1.0: Required is a box with its own column; ticked, the default is "—" and cannot be typed
            JCheckBox required = new JCheckBox(row.required ? t("yes", "oui") : t("no", "non"), row.required);
            required.setOpaque(false);
            required.putClientProperty("archvow.required", Boolean.TRUE);
            JTextField value = field(row.required ? "" : row.value, v -> { if (!row.required) row.value = v; }, "");
            Runnable state = () -> {
                value.setEnabled(!row.required);
                if (row.required) { if (!"—".equals(value.getText())) writingDefault(value, "—"); }
                else if ("—".equals(value.getText())) writingDefault(value, row.value);
                boolean missing = !row.required && row.value.isBlank() && !row.name.isBlank();
                value.putClientProperty("JComponent.outline", missing ? "error" : null);
                value.setToolTipText(missing ? t("a default, or tick Required", "une valeur par défaut, ou cocher Obligatoire") : null);
                value.repaint();
            };
            required.addActionListener(e -> {
                row.required = required.isSelected();
                required.setText(row.required ? t("yes", "oui") : t("no", "non"));
                state.run();
                edited();
            });
            value.getDocument().addDocumentListener(new DocumentListener() {  // the red mark follows the typing
                @Override public void insertUpdate(DocumentEvent e) { if (!building) SwingUtilities.invokeLater(state); }
                @Override public void removeUpdate(DocumentEvent e) { if (!building) SwingUtilities.invokeLater(state); }
                @Override public void changedUpdate(DocumentEvent e) { }
            });
            cells.add(new Cell(heads.get(h++), required, 0, false));
            cells.add(new Cell(heads.get(h++), value, 1, false));
            state.run();
        }
        cells.add(new Cell(heads.get(h), field(row.note, v -> row.note = v, t("optional", "facultatif")), 1, mode == Mode.COLUMNS));
        JButton bin = new JButton(ArchvowTreeIcons.icon("remove", null));
        bin.putClientProperty("archvow.bin", Boolean.TRUE);
        bin.setToolTipText(mode == Mode.COLUMNS ? t("Remove this column from the table", "Retirer cette colonne du tableau")
            : t("Remove this parameter from the table", "Retirer ce paramètre du tableau"));
        if (bin.getAccessibleContext() != null) bin.getAccessibleContext().setAccessibleName(t("Remove", "Retirer"));
        bin.setBorderPainted(false); bin.setContentAreaFilled(false); bin.setFocusPainted(false);
        bin.setMargin(new Insets(1, 3, 1, 3));
        bin.addActionListener(e -> removeRow(index));
        cells.add(new Cell("", bin, 0, false));
        return cells;
    }

    private interface Setter { void set(String value); }

    private JTextField field(String value, Setter setter, String placeholder) {
        JTextField f = new JTextField(value, 8);
        f.setToolTipText(placeholder);
        f.putClientProperty("JTextField.placeholderText", placeholder);
        f.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() { if (f.isEnabled()) { setter.set(f.getText()); edited(); } }
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
        box.addActionListener(e -> { row.type = String.valueOf(box.getSelectedItem() == null ? "" : box.getSelectedItem()); edited(); });
        if (box.getEditor().getEditorComponent() instanceof JTextField text) {
            text.getDocument().addDocumentListener(new DocumentListener() {
                private void changed() { row.type = text.getText(); edited(); }
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
            edited();
        });
        return s;
    }

    /** A change in the table: it writes the answer field, unless the person edited that field by hand (1.1.0). */
    private void edited() {
        if (byHand || building) return;
        writing = true;
        try { target.setText(answer()); } finally { writing = false; }
        handLine.setVisible(false);
    }

    /** True while the rows are being made (their fields' first text is not an edit). */
    private boolean building;

    /** The default field's own text, set without counting as the person's edit. */
    private void writingDefault(JTextField value, String text) {
        boolean was = building;
        building = true;
        try { value.setText(text); } finally { building = was; }
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
                String value = r.required ? "" : r.value.trim();
                if (!r.required && value.isEmpty()) continue;  // marked in red: left out until it has a default
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
            r.required = r.value.isEmpty();
            String note = m.group(5) == null ? "" : m.group(5);
            r.note = note.matches("(?i)required|obligatoire") ? "" : note.trim();
            out.add(r);
        }
        return out;
    }
}
