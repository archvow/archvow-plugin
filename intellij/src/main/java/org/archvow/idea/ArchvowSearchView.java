package org.archvow.idea;

import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The search in the editor tab (0.18.0), as on the VS Code page: archvow show <name> --json. Left the groups with
 * their counts (a click filters), middle the places, right the selected one's lines with "Open at line N",
 * "Show <component>" and, for a problem, "Rule ARCHnnn"; a name found nowhere gives its close names as buttons
 * that search again. Double-click a place: the file at its line. Plain Swing: actions go back through {@link Actions}.
 */
final class ArchvowSearchView extends JPanel {
    interface Actions {
        void openFile(String relative, int line);
        void showComponent(String id);
        void openRule(String code);
        void search(String name);
    }

    private final Actions actions;
    private final JLabel name = new JLabel();
    private final JLabel heading = new JLabel();
    private final DefaultListModel<String> groupModel = new DefaultListModel<>();
    private final JList<String> groupList = new JList<>(groupModel);
    private final DefaultListModel<ArchvowSearch.Hit> hitModel = new DefaultListModel<>();
    private final JList<ArchvowSearch.Hit> hitList = new JList<>(hitModel);
    private final JEditorPane preview = new JEditorPane("text/html", "");
    private final JButton open = new JButton();
    private final JButton component = new JButton();
    private final JButton rule = new JButton();
    private final JPanel nowhere = new JPanel(new BorderLayout(0, 8));
    private final JPanel closeNames = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    private final JLabel nowhereText = new JLabel();
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final List<String> groupKeys = new ArrayList<>();
    private JsonObject result;
    private String base;
    private boolean french;
    private boolean filling;

    ArchvowSearchView(Actions actions) {
        super(new BorderLayout(0, 8));
        this.actions = actions;
        setName("archvow.search");
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        name.setFont(new Font(Font.MONOSPACED, Font.BOLD, Math.round(ArchvowHtml.base() * 1.3f)));
        heading.setForeground(UIManager.getColor("Label.disabledForeground"));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        top.add(name);
        top.add(heading);
        add(top, BorderLayout.NORTH);
        groupList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        groupList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting() && !filling) fillHits(); });
        hitList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        hitList.setCellRenderer(new HitRenderer());
        hitList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showHit(); });
        hitList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                ArchvowSearch.Hit hit = hitList.getSelectedValue();
                if (e.getClickCount() == 2 && hit != null && hit.openable()) actions.openFile(hit.file(), hit.line());
            }
        });
        preview.setEditable(false);
        preview.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        open.addActionListener(e -> { ArchvowSearch.Hit h = hitList.getSelectedValue(); if (h != null && h.openable()) actions.openFile(h.file(), h.line()); });
        component.addActionListener(e -> { ArchvowSearch.Hit h = hitList.getSelectedValue(); if (h != null) actions.showComponent(h.component()); });
        rule.addActionListener(e -> { ArchvowSearch.Hit h = hitList.getSelectedValue(); if (h != null) actions.openRule(h.part()); });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        buttons.add(open);
        buttons.add(component);
        buttons.add(rule);
        JPanel right = new JPanel(new BorderLayout(0, 6));
        right.add(new JScrollPane(preview), BorderLayout.CENTER);
        right.add(buttons, BorderLayout.SOUTH);
        JScrollPane groups = new JScrollPane(groupList);
        groups.setPreferredSize(new Dimension(150, 0));
        JSplitPane middle = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(hitList), right);
        middle.setResizeWeight(.6);
        middle.setBorder(BorderFactory.createEmptyBorder());
        JPanel found = new JPanel(new BorderLayout(8, 0));
        found.add(groups, BorderLayout.WEST);
        found.add(middle, BorderLayout.CENTER);
        body.add(found, "found");
        nowhere.add(nowhereText, BorderLayout.NORTH);
        nowhere.add(closeNames, BorderLayout.CENTER);
        JPanel nowhereBox = new JPanel(new BorderLayout());
        nowhereBox.add(nowhere, BorderLayout.NORTH);
        body.add(nowhereBox, "nowhere");
        add(body, BorderLayout.CENTER);
        setFrench(false);
    }

    void setFrench(boolean value) {
        french = value;
        component.setText(value ? "Afficher le composant" : "Show component");
        nowhereText.setForeground(new Color(176, 118, 26));
    }

    /** A search result (archvow show <name> --json) in the project at {@code projectBase}. */
    void show(JsonObject data, String wanted, String projectBase, boolean frenchText) {
        result = data;
        base = projectBase;
        setFrench(frenchText);
        name.setText(wanted);
        if (ArchvowGrammar.number(data, "total") == 0) {
            heading.setText("");
            nowhereText.setText(ArchvowSearch.nowhere(wanted, french));
            closeNames.removeAll();
            List<String[]> close = ArchvowSearch.close(data);
            if (!close.isEmpty()) closeNames.add(new JLabel(french ? "Noms proches :" : "Close names:"));
            for (String[] c : close) {
                JButton b = new JButton(c[0] + " · " + c[1]);
                b.addActionListener(e -> actions.search(c[0]));
                closeNames.add(b);
            }
            cards.show(body, "nowhere");
            revalidate();
            repaint();
            return;
        }
        heading.setText(ArchvowSearch.heading(data, french) + "  ·  archvow show " + wanted + " --json");
        filling = true;
        groupModel.clear();
        groupKeys.clear();
        groupModel.addElement((french ? "Tout" : "All") + "  " + ArchvowGrammar.number(data, "total"));
        groupKeys.add("");
        for (String[] g : ArchvowSearch.groups(data)) {
            groupModel.addElement(ArchvowSearch.title(g[0], french) + "  " + g[1]);
            groupKeys.add(g[0]);
        }
        groupList.setSelectedIndex(0);
        filling = false;
        fillHits();
        cards.show(body, "found");
    }

    private void fillHits() {
        int g = groupList.getSelectedIndex();
        String only = g >= 0 && g < groupKeys.size() ? groupKeys.get(g) : "";
        hitModel.clear();
        for (ArchvowSearch.Hit hit : ArchvowSearch.hits(result, only)) hitModel.addElement(hit);
        if (!hitModel.isEmpty()) hitList.setSelectedIndex(0);
        showHit();
    }

    private void showHit() {
        ArchvowSearch.Hit hit = hitList.getSelectedValue();
        open.setVisible(hit != null && hit.openable());
        component.setVisible(hit != null && !hit.component().isBlank() && !hit.component().equals("project"));
        rule.setVisible(hit != null && hit.group().equals("problems"));
        if (hit == null) { preview.setText(""); return; }
        open.setText(hit.line() > 0 ? (french ? "Ouvrir à la ligne " : "Open at line ") + hit.line() : (french ? "Ouvrir" : "Open"));
        component.setText((french ? "Afficher " : "Show ") + hit.component());
        rule.setText((french ? "Règle " : "Rule ") + hit.part());
        preview.setText(previewHtml(hit, ArchvowSearch.around(base, hit), french));
        preview.setCaretPosition(0);
    }

    /** The selected place: its group and component, its part, its file and line, the lines around it. */
    static String previewHtml(ArchvowSearch.Hit hit, List<String[]> around, boolean french) {
        StringBuilder out = new StringBuilder(ArchvowHtml.body(null, null));
        out.append("<div style='color:gray; ").append(ArchvowHtml.size(.85)).append("'>")
            .append(ArchvowExamples.esc(ArchvowSearch.title(hit.group(), french).toUpperCase(java.util.Locale.ROOT) + " · "
                + (hit.component().isBlank() ? (french ? "aucun composant" : "no component") : hit.component()))).append("</div>");
        out.append(ArchvowHtml.heading(ArchvowExamples.esc(hit.label()), 1.05, "margin-top:2px"));
        out.append("<div style='font-family:monospace; color:#2e5aac'>").append(ArchvowExamples.esc(hit.where())).append("</div>");
        if (around.isEmpty()) out.append("<p>").append(ArchvowExamples.esc(hit.text())).append("</p>");
        else {
            out.append("<pre style='margin-top:6px'>");
            for (String[] line : around) {
                boolean here = line[0].equals(Integer.toString(hit.line()));
                out.append(here ? "<b>" : "").append(String.format("%4s  ", line[0])).append(ArchvowExamples.esc(line[1])).append(here ? "</b>" : "").append("\n");
            }
            out.append("</pre>");
        }
        if (hit.group().equals("problems")) out.append("<div style='color:#cf222e'><b>").append(ArchvowExamples.esc(hit.part()))
            .append("</b> ").append(ArchvowExamples.esc(hit.text())).append("</div>");
        return out.append("</body></html>").toString();
    }

    /** A place: "component · part (what)" over "text · file:line". */
    private static final class HitRenderer extends JPanel implements ListCellRenderer<ArchvowSearch.Hit> {
        private final JLabel first = new JLabel();
        private final JLabel second = new JLabel();

        HitRenderer() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            add(first);
            add(second);
        }

        @Override public Component getListCellRendererComponent(JList<? extends ArchvowSearch.Hit> list, ArchvowSearch.Hit hit,
                                                                int index, boolean selected, boolean focused) {
            boolean problem = hit != null && hit.group().equals("problems");
            first.setText(hit == null ? "" : (hit.component().isBlank() ? "—" : hit.component()) + "  ·  " + hit.label());
            second.setText(hit == null ? "" : hit.text() + "   " + hit.where());
            first.setForeground(problem ? new Color(207, 34, 46) : list.getForeground());
            second.setForeground(UIManager.getColor("Label.disabledForeground"));
            setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            setOpaque(true);
            return this;
        }
    }

    // regression-test seams
    int hitCount() { return hitModel.getSize(); }
    int groupCount() { return groupModel.getSize(); }
    void selectGroup(int index) { groupList.setSelectedIndex(index); }
    boolean showsNowhere() { return ArchvowGrammar.number(result, "total") == 0; }
    int closeButtons() { int n = 0; for (Component c : closeNames.getComponents()) if (c instanceof JButton) n++; return n; }
    String previewText() { return preview.getText(); }
}
