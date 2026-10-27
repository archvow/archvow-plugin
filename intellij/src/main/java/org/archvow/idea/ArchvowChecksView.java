package org.archvow.idea;

import com.google.gson.JsonObject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Native Swing display for `archvow check --json`, independent of Swing's limited HTML renderer. */
final class ArchvowChecksView extends JPanel {
    record Diagnostic(String code, String severity, String message) {}
    private final JLabel heading = new JLabel();
    private final JLabel totals = new JLabel();
    private final JLabel byRule = new JLabel();
    private final JLabel hint = new JLabel();
    private final DefaultListModel<Diagnostic> model = new DefaultListModel<>();
    private final JList<Diagnostic> list = new JList<>(model);
    private final JTextArea details = new JTextArea();
    private final JButton reference = new JButton();
    private final JButton allReferences = new JButton();
    private Consumer<String> onCode = code -> {};
    private Runnable onAll = () -> {};
    private boolean french;

    ArchvowChecksView() {
        super(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(10, 12, 10, 12));
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 18f));
        totals.setFont(totals.getFont().deriveFont(Font.BOLD, 13f));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        totals.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        byRule.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(heading);
        top.add(Box.createVerticalStrut(7));
        top.add(totals);
        top.add(Box.createVerticalStrut(3));
        top.add(byRule);
        top.add(Box.createVerticalStrut(5));
        top.add(hint);
        add(top, BorderLayout.NORTH);

        list.setCellRenderer(new DiagnosticRenderer());
        list.setFixedCellHeight(65);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showSelected(); });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) openSelectedRule();
            }
        });
        add(new JScrollPane(list), BorderLayout.CENTER);

        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setRows(4);
        details.setBorder(new EmptyBorder(8, 8, 8, 8));
        JPanel bottom = new JPanel(new BorderLayout(0, 5));
        JScrollPane info = new JScrollPane(details);
        info.setBorder(BorderFactory.createTitledBorder("Details"));
        bottom.add(info, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        reference.addActionListener(e -> openSelectedRule());
        allReferences.addActionListener(e -> onAll.run());
        actions.add(reference);
        actions.add(allReferences);
        bottom.add(actions, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
        showData(null, "", false);
    }

    void onOpenCode(Consumer<String> handler) { onCode = handler == null ? code -> {} : handler; }
    void onOpenAll(Runnable handler) { onAll = handler == null ? () -> {} : handler; }

    void showData(JsonObject check, String failure, boolean fr) {
        french = fr;
        heading.setText(fr ? "Contrôles et validations" : "Check & Gates");
        reference.setText(fr ? "Voir la règle…" : "Open rule…");
        allReferences.setText(fr ? "Référence des contrôles" : "Check reference");
        model.clear();
        if (check == null) {
            totals.setText(fr ? "Résultat indisponible" : "No check result available");
            totals.setForeground(errorColor());
            byRule.setText("");
            byRule.setVisible(false);
            hint.setText(fr ? "Exécute le contrôle ou vérifie le CLI." : "Run Check or verify the CLI.");
            details.setText(failure == null || failure.isBlank() ?
                (fr ? "Aucun diagnostic reçu." : "No diagnostics received.") : failure);
        } else {
            boolean ok = ArchvowGrammar.flag(check, "ok");
            int errors = count(check, "errors");
            int warnings = count(check, "warnings");
            totals.setText((ok ? (fr ? "VALIDÉ" : "PASSED") : (fr ? "NON VALIDÉ" : "FAILED"))
                + "  ·  " + errors + (fr ? " erreurs" : " errors") + "  ·  "
                + warnings + (fr ? " avertissements" : " warnings"));
            totals.setForeground(ok ? successColor() : errorColor());
            byRule.setText(byRule(check, fr));
            byRule.setVisible(!byRule.getText().isEmpty());
            hint.setText(fr ? "Sélectionne un diagnostic pour voir son détail ; double-clique pour ouvrir la règle."
                : "Select a diagnostic for details; double-click to open its rule.");
            for (JsonObject item : ArchvowGrammar.objects(check, "violations")) {
                String code = ArchvowGrammar.str(item, "code");
                String severity = ArchvowGrammar.str(item, "severity");
                String message = ArchvowGrammar.str(item, "message");
                if (message.isBlank()) message = item.toString();
                model.addElement(new Diagnostic(code, severity, message));
            }
            details.setText(model.isEmpty() ? (fr ? "Aucun diagnostic détaillé." : "No detailed diagnostics.") : "");
        }
        if (!model.isEmpty()) list.setSelectedIndex(0);
        else showSelected();
        revalidate();
        repaint();
    }
    /** "By rule: ARCH001 146 · SPK008 415" from check's `counts` (most first, as archvow sorts them); empty
     *  when archvow gives no counts or only one rule fired (the totals already say it). */
    static String byRule(JsonObject check, boolean fr) {
        if (check == null || !check.has("counts") || !check.get("counts").isJsonObject()) return "";
        JsonObject counts = check.getAsJsonObject("counts");
        if (counts.size() < 2) return "";
        StringBuilder text = new StringBuilder(fr ? "Par règle : " : "By rule: ");
        String separator = "";
        for (var entry : counts.entrySet()) {
            text.append(separator).append(entry.getKey()).append(' ').append(entry.getValue().getAsString());
            separator = "  ·  ";
        }
        return text.toString();
    }
    String byRuleText() { return byRule.getText(); }
    private static int count(JsonObject item, String name) {
        try { return Integer.parseInt(ArchvowGrammar.str(item, name)); }
        catch (NumberFormatException ignored) { return 0; }
    }
    int diagnosticCount() { return model.size(); }
    String diagnosticCode(int i) { return model.get(i).code(); }
    String statusText() { return totals.getText(); }

    private void showSelected() {
        Diagnostic selected = list.getSelectedValue();
        reference.setEnabled(selected != null && !selected.code().isBlank());
        if (selected == null) return;
        String help = ArchvowRuleDocs.explain(ArchvowRuleDocs.canonical(selected.code()), french);
        details.setText(selected.code() + "\n\n" + selected.message()
            + (help.isBlank() ? "" : "\n\n" + (french ? "Explication : " : "Explanation: ") + help));
        details.setCaretPosition(0);
    }
    private void openSelectedRule() {
        Diagnostic selected = list.getSelectedValue();
        if (selected != null && !selected.code().isBlank()) onCode.accept(selected.code());
    }
    private static boolean isDark() {
        Color c = UIManager.getColor("Panel.background");
        return c != null && c.getRed() * .299 + c.getGreen() * .587 + c.getBlue() * .114 < 128;
    }
    private static Color errorColor() { return isDark() ? new Color(248,153,160) : new Color(179,37,57); }
    private static Color successColor() { return isDark() ? new Color(113,222,164) : new Color(24,125,77); }
    private static class DiagnosticRenderer extends JPanel implements ListCellRenderer<Diagnostic> {
        private final JLabel header = new JLabel();
        private final JLabel summary = new JLabel();
        DiagnosticRenderer() {
            super(new BorderLayout(0,4));
            setBorder(new EmptyBorder(7,10,7,10));
            header.setFont(header.getFont().deriveFont(Font.BOLD, 13f));
            add(header, BorderLayout.NORTH);
            add(summary, BorderLayout.SOUTH);
        }
        @Override public Component getListCellRendererComponent(JList<? extends Diagnostic> list, Diagnostic row,
                int index, boolean selected, boolean focus) {
            header.setText((row.code().isBlank() ? "CHECK" : row.code()) + "  ·  " +
                ("warning".equalsIgnoreCase(row.severity()) ? "WARNING" : "ERROR"));
            String message = row.message().replace('\n', ' ');
            summary.setText(message.length() > 160 ? message.substring(0,157) + "…" : message);
            Color base = UIManager.getColor("List.background");
            if (base == null) base = Color.WHITE;
            setBackground(selected ? list.getSelectionBackground() : base);
            header.setForeground(selected ? list.getSelectionForeground()
                : ("warning".equalsIgnoreCase(row.severity()) ?
                    (isDark() ? new Color(248,193,114) : new Color(158,104,27)) : errorColor()));
            summary.setForeground(selected ? list.getSelectionForeground() : UIManager.getColor("Label.foreground"));
            return this;
        }
    }
}
