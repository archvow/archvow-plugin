package org.archvow.idea;

import com.google.gson.JsonObject;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Optional, reversible plain-text draft builder for column-property decisions.
 * Never changes the Vow directly, never assumes nullability, and works for any component type. */
final class ArchvowColumnAnswerPanel extends JPanel {
    private final DefaultTableModel rows = new DefaultTableModel(new Object[] {"Column", "Type", "Nullable", "Personal"}, 0);
    private final JTable table = new JTable(rows);
    private final JTextArea preview = new JTextArea(3, 30);
    private final JButton add = new JButton();
    private final JButton use = new JButton();
    private final JLabel note = new JLabel();
    private boolean french;
    private String question = "";

    ArchvowColumnAnswerPanel(JTextArea target) {
        super(new BorderLayout(0, 5));
        table.setFillsViewportHeight(true);
        table.setRowHeight(Math.max(25, table.getRowHeight()));
        installTypeEditor();
        JComboBox<String> options = new JComboBox<>(new String[] {"unknown", "no", "yes"});
        table.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(options));
        table.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(new JComboBox<>(new String[] {"unknown", "no", "yes"})));
        JScrollPane scroller = new JScrollPane(table);
        scroller.setPreferredSize(new Dimension(180, 128));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        add.addActionListener(e -> rows.addRow(new Object[] {"", "", "unknown", "unknown"}));
        use.addActionListener(e -> {
            if (table.isEditing()) table.getCellEditor().stopCellEditing();
            String result = answer();
            if (!result.isBlank()) { target.setText(result); target.requestFocusInWindow(); }
        });
        actions.add(add);
        actions.add(use);
        preview.setEditable(false);
        preview.setLineWrap(true);
        preview.setWrapStyleWord(true);
        rows.addTableModelListener(e -> preview.setText(answer()));
        note.setForeground(UIManager.getColor("Label.disabledForeground"));
        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.add(note, BorderLayout.NORTH);
        top.add(scroller, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        bottom.add(actions, BorderLayout.NORTH);
        bottom.add(new JScrollPane(preview), BorderLayout.CENTER);
        add(top, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        setBorder(BorderFactory.createTitledBorder("Structured answer (optional)"));
        setVisible(false);
    }

    void showFor(JsonObject view, JsonObject row, boolean fr) {
        french = fr;
        question = ArchvowGrammar.str(row, "question").toLowerCase(java.util.Locale.ROOT);
        boolean relevant = ArchvowForms.supportsColumnAnswer(question);
        setVisible(relevant);
        if (!relevant) return;
        setBorder(BorderFactory.createTitledBorder(fr ? "Réponse structurée (facultatif)" : "Structured answer (optional)"));
        add.setText(fr ? "Ajouter une colonne" : "Add column");
        use.setText(fr ? "Utiliser cette réponse" : "Use structured answer");
        note.setText(fr ? "Valeurs inconnues conservées ; la réponse reste modifiable avant enregistrement."
            : "Unknown values stay unknown; review the draft before saving.");
        rows.setColumnIdentifiers(fr ? new String[] {"Colonne", "Type", "Nullable", "Personnel"}
            : new String[] {"Column", "Type", "Nullable", "Personal"});
        installTypeEditor();
        table.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(
            new JComboBox<>(new String[] {"unknown", "no", "yes"})));
        table.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(
            new JComboBox<>(new String[] {"unknown", "no", "yes"})));
        rows.setRowCount(0);
        Map<String, String> personal = new LinkedHashMap<>();
        for (JsonObject col : ArchvowGrammar.objects(view, "columns")) {
            String name = ArchvowGrammar.str(col, "name");
            String cls = ArchvowGrammar.str(col, "class");
            if (!name.isBlank()) personal.put(name, "personal".equals(cls) || "special".equals(cls) ? "yes" : "none".equals(cls) ? "no" : "unknown");
        }
        Map<String, String> names = new LinkedHashMap<>();
        for (JsonObject group : ArchvowGrammar.objects(view, "known_columns"))
            for (String name : ArchvowGrammar.strings(group, "columns")) if (!name.isBlank()) names.put(name, "");
        for (String name : personal.keySet()) names.putIfAbsent(name, "");
        int count = 0;
        for (String name : names.keySet()) {
            if (++count > 60) break;
            rows.addRow(new Object[] { name, "", "unknown", personal.getOrDefault(name, "unknown") });
        }
        preview.setText(answer());
        revalidate();
    }

    private void installTypeEditor() {
        // Suggestions only: template-specific data types are always allowed through free entry.
        JComboBox<String> types = new JComboBox<>(new String[] {
            "", "string", "integer", "float", "double", "decimal", "boolean", "date", "timestamp", "object" });
        types.setEditable(true);
        table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(types));
    }

    String answer() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < rows.getRowCount(); i++) {
            String name = str(i, 0).trim();
            if (name.isBlank()) continue;
            String type = str(i, 1).trim();
            String nullable = str(i, 2);
            String personal = str(i, 3);
            List<String> parts = new ArrayList<>();
            if (question.contains("type")) parts.add(type.isBlank() ? (french ? "type inconnu" : "type unknown") : type);
            if (question.contains("null") || question.contains("requir") || question.contains("option")
                || question.contains("obligat") || question.contains("facultat"))
                parts.add("yes".equals(nullable) ? "nullable" : "no".equals(nullable) ? (french ? "non nullable" : "not null") : (french ? "nullabilité inconnue" : "nullability unknown"));
            if (question.contains("person") || question.contains("priv") || question.contains("gdpr") || question.contains("rgpd"))
                parts.add("yes".equals(personal) ? (french ? "donnée personnelle" : "personal data") : "no".equals(personal) ? (french ? "non personnel" : "not personal data") : (french ? "classification des données inconnue" : "personal-data classification unknown"));
            lines.add(name + ": " + String.join(", ", parts));
        }
        return String.join("\n", lines);
    }

    private String str(int row, int col) {
        Object value = rows.getValueAt(row, col);
        return value == null ? "" : String.valueOf(value);
    }
}
