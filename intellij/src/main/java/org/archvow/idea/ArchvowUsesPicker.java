package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read/write the original comma-separated --uses value, with optional choices from the current project.
 * Text stays editable for imported/unknown dependency IDs; this widget does not change CLI semantics. */
final class ArchvowUsesPicker extends JPanel {
    private final JTextField text;
    private final JButton choose;

    ArchvowUsesPicker(String value, Map<String, String> available, String self, boolean french, Runnable changed) {
        super(new BorderLayout(5, 0));
        text = new JTextField(value == null ? "" : value, 24);
        text.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { changed.run(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { changed.run(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { changed.run(); }
        });
        choose = new JButton(french ? "Choisir…" : "Select…");
        choose.setEnabled(available != null && available.keySet().stream().anyMatch(id -> !id.equals(self)));
        choose.addActionListener(e -> {
            JPopupMenu menu = new JPopupMenu();
            Set<String> selected = selected();
            for (String id : available.keySet()) {
                if (id == null || id.isBlank() || id.equals(self)) continue;
                JCheckBoxMenuItem item = new JCheckBoxMenuItem(available.getOrDefault(id, id), selected.contains(id));
                item.addActionListener(event -> {
                    Set<String> now = selected();
                    if (item.isSelected()) now.add(id); else now.remove(id);
                    text.setText(String.join(",", now));
                });
                menu.add(item);
            }
            menu.show(choose, 0, choose.getHeight());
        });
        add(text, BorderLayout.CENTER);
        add(choose, BorderLayout.EAST);
        setToolTipText(french ? "Composants déclarés (saisie manuelle possible)" : "Declared components (manual entry remains possible)");
    }

    String value() { return text.getText().trim(); }
    private Set<String> selected() {
        Set<String> out = new LinkedHashSet<>();
        for (String id : text.getText().split(",")) if (!id.isBlank()) out.add(id.trim());
        return out;
    }
}
