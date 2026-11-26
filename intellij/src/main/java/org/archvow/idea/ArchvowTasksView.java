package org.archvow.idea;

import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * The component's Tasks view (1.2.1), as on the VS Code page: a sentence on what there is to build, then the tasks
 * and the columns to classify as a list, left, and the selected one's details, right (ArchvowSplitView: side by side,
 * stacked when the tab is narrow); under them, the columns and the personal data. A component with no task shows
 * the sentence alone - no empty list, no detail pane, no button without a label. Plain Swing: what to copy, classify,
 * open or edit goes back through {@link Actions}.
 */
final class ArchvowTasksView extends JPanel {
    /** What the view asks the panel to do. */
    interface Actions {
        void act(ArchvowDataView.Item item);   // a task: copy its prompt; a column: classify it
        void openCode();
        void editDescription();
    }

    private final DefaultListModel<ArchvowDataView.Item> model = new DefaultListModel<>();
    private final JList<ArchvowDataView.Item> list = new JList<>(model);
    private final ArchvowWrapLabel heading = new ArchvowWrapLabel();
    private final JButton act = ArchvowSplitView.primary("");
    private final JButton openCode = new JButton();
    private final JButton editDescription = new JButton();
    private final JLabel detailTitle = new JLabel();
    private final JTextArea detail = new JTextArea();
    private final JTextPane summary = ArchvowDataView.create();
    private final JPanel resultSlot = new JPanel(new BorderLayout());
    private final CardLayout cards = new CardLayout();
    private final JPanel none = new JPanel(new BorderLayout());
    private final JPanel south = new JPanel(new BorderLayout(0, 4));
    private JScrollPane summaryScroll;
    private final JPanel body = new JPanel(cards);
    private boolean french;
    private String component = "";

    ArchvowTasksView(Actions actions) {
        super(new BorderLayout(0, 6));
        setName("archvow.tasks");
        list.setName("archvow.tasks.list");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        list.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showSelected(); });
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && list.getSelectedValue() != null) actions.act(list.getSelectedValue());
            }
        });
        act.setName("archvow.tasks.act");
        act.addActionListener(e -> { if (list.getSelectedValue() != null) actions.act(list.getSelectedValue()); });
        openCode.addActionListener(e -> actions.openCode());
        editDescription.addActionListener(e -> actions.editDescription());
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        heading.setName("archvow.tasks.heading");
        heading.setOpaque(false);
        heading.setForeground(UIManager.getColor("Label.foreground"));
        heading.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setBorder(BorderFactory.createEmptyBorder());
        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.setOpaque(false);
        left.add(listScroll, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new ArchvowWrapLayout(FlowLayout.LEFT, 6, 2));
        buttons.setOpaque(false);
        buttons.add(act);
        buttons.add(openCode);
        left.add(buttons, BorderLayout.SOUTH);

        detail.setEditable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        detail.setOpaque(false);
        detail.setFont(UIManager.getFont("Label.font"));
        detailTitle.setFont(detailTitle.getFont().deriveFont(Font.BOLD));
        JPanel answer = new JPanel(new BorderLayout(0, 6));
        answer.add(detailTitle, BorderLayout.NORTH);
        JScrollPane detailScroll = new JScrollPane(detail);
        detailScroll.setBorder(BorderFactory.createEmptyBorder());
        answer.add(detailScroll, BorderLayout.CENTER);
        JPanel edit = new JPanel(new ArchvowWrapLayout(FlowLayout.LEFT, 0, 2));
        edit.setOpaque(false);
        edit.add(editDescription);
        answer.add(edit, BorderLayout.SOUTH);

        body.add(ArchvowSplitView.create(list, left, answer), "items");
        none.setOpaque(false);
        body.add(none, "none");

        summaryScroll = new JScrollPane(summary);
        summaryScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground") == null
            ? new Color(128, 128, 128, 80) : UIManager.getColor("Separator.foreground")));
        summaryScroll.setName("archvow.tasks.summary");
        south.setOpaque(false);
        south.add(resultSlot, BorderLayout.NORTH);
        south.add(summaryScroll, BorderLayout.CENTER);
        resultSlot.setOpaque(false);

        add(heading, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        setFrench(false);
    }

    JList<ArchvowDataView.Item> items() { return list; }
    JPanel resultSlot() { return resultSlot; }
    ArchvowDataView.Item selected() { return list.getSelectedValue(); }
    String headingText() { return heading.getText(); }
    boolean showsItems() { return !model.isEmpty(); }

    void setFrench(boolean value) {
        french = value;
        openCode.setText(value ? "Ouvrir le code" : "Open code");
        editDescription.setText(value ? "Modifier la description du composant…" : "Edit component description…");
        detailTitle.setText(value ? "Détails et description" : "Details and description");
        showSelected();
    }

    /** The component's tasks and columns (show --json), pending first; the selection kept when it still exists. */
    void show(String componentId, JsonObject view) {
        component = componentId == null ? "" : componentId;
        ArchvowDataView.Item before = list.getSelectedValue();
        model.clear();
        List<ArchvowDataView.Item> items = ArchvowDataView.items(view);
        for (ArchvowDataView.Item item : items) if (item.pending()) model.addElement(item);
        for (ArchvowDataView.Item item : items) if (!item.pending()) model.addElement(item);
        heading.setText(heading(items, component, french));
        cards.show(body, items.isEmpty() ? "none" : "items");
        int keep = 0;
        for (int i = 0; i < model.size(); i++)
            if (before != null && model.get(i).kind() == before.kind() && model.get(i).id().equals(before.id())) keep = i;
        if (!model.isEmpty()) list.setSelectedIndex(keep);
        ArchvowDataView.render(summary, view, french);
        // no task: the columns and personal data right under the sentence; tasks: under the list, a third at most
        if (items.isEmpty()) {
            none.add(summaryScroll, BorderLayout.NORTH);
            summaryScroll.setPreferredSize(null);
        } else {
            south.add(summaryScroll, BorderLayout.CENTER);
            summary.setSize(Math.max(200, getWidth()), Short.MAX_VALUE);
            summaryScroll.setPreferredSize(new Dimension(10, Math.min(summary.getPreferredSize().height + 4, Math.max(140, getHeight() / 3))));
        }
        showSelected();
        revalidate();
        repaint();
    }

    /** No tasks is not "every task is done": a small component has none, the AI builds it in one go. */
    static String heading(List<ArchvowDataView.Item> items, String component, boolean french) {
        long tasks = items.stream().filter(i -> i.kind() == ArchvowDataView.Kind.TASK).count();
        long toBuild = items.stream().filter(i -> i.kind() == ArchvowDataView.Kind.TASK && i.pending()).count();
        if (toBuild > 0) return french
            ? toBuild + (toBuild == 1 ? " tâche" : " tâches") + " à construire : l'IA (ou toi) écrit le code et ses tests marqués. Chacune est faite quand ils existent."
            : toBuild + (toBuild == 1 ? " task" : " tasks") + " to build: the AI (or you) writes the code and its marked tests. Each one is done when they exist.";
        if (tasks > 0) return french ? "Rien à construire : chaque tâche est faite." : "Nothing to build: every task is done.";
        return french ? "Pas de tâches séparées : l'IA construit " + component + " d'un seul tenant, depuis sa spec."
            : "No separate tasks: the AI builds " + component + " in one go, from its spec.";
    }

    private void showSelected() {
        ArchvowDataView.Item item = list.getSelectedValue();
        act.setVisible(item != null);
        openCode.setVisible(item != null && item.kind() != ArchvowDataView.Kind.COLUMN);
        if (item == null) { detail.setText(""); return; }
        boolean column = item.kind() == ArchvowDataView.Kind.COLUMN;
        act.setText(column ? (french ? "Classifier la colonne…" : "Classify column…") : (french ? "Copier la tâche" : "Copy task prompt"));
        String text = item.detail() == null || item.detail().isBlank()
            ? (french ? "Aucune description supplémentaire n'est enregistrée pour cet élément dans la spec du composant."
                : "No further description is recorded for this item in the component spec.")
            : item.detail();
        detail.setText(item.title() + "  ·  " + word(item, french) + "\n\n" + text);
        detail.setCaretPosition(0);
    }

    static String word(ArchvowDataView.Item item, boolean french) {
        if (item.kind() == ArchvowDataView.Kind.COLUMN)
            return item.pending() ? (french ? "À classer" : "To classify") : (french ? "Classée" : "Classified");
        return item.pending() ? (french ? "À construire" : "To build") : (french ? "Faite" : "Done");
    }

    /** "▸ T1 · To build": blue to build, orange to classify (a person's decision), green done. */
    private final class Renderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> owner, Object value, int index, boolean selected, boolean focused) {
            super.getListCellRendererComponent(owner, value, index, selected, focused);
            if (!(value instanceof ArchvowDataView.Item item)) return this;
            boolean column = item.kind() == ArchvowDataView.Kind.COLUMN;
            setText((column ? "◉ " : "▸ ") + item.title() + "  ·  " + word(item, french));
            Color status = !item.pending() ? ArchvowStatusPalette.statusColor(false)
                : column ? ArchvowStatusPalette.statusColor(true) : ArchvowMotion.accent();
            if (!selected) setBackground(ArchvowStatusPalette.rowBackground(owner.getBackground(), status, false, ArchvowMotion.hoverLevel(owner, index)));
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, selected ? 4 : 3, 0, 0, status),
                BorderFactory.createEmptyBorder(5, 7, 5, 7)));
            return this;
        }
    }
}
