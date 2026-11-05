package org.archvow.idea;

import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The component's Examples view (0.18.0), as on the VS Code page: the business examples as cards (✔ its test
 * passes, ✖ it fails, ○ no test yet, · not run), ordered by the rule they show, left; the selected one right -
 * its state, rule, brief and scenario, then each output file expected/ | actual/ side by side with the rows
 * that differ marked. Plain Swing: what to open, copy or record goes back through {@link Actions}.
 */
final class ArchvowExamplesView extends JPanel {
    /** What the view asks the panel to do. */
    interface Actions {
        void openFile(String relative, int line);
        void openBrief(String ref);
        void copy(String text);
        void record(String component);
    }

    private final Actions actions;
    private final DefaultListModel<ArchvowItemCards.Card> model = new DefaultListModel<>();
    private final JList<ArchvowItemCards.Card> list = new JList<>(model);
    private final JEditorPane detail = new JEditorPane("text/html", "");
    private final JButton openTest = new JButton();
    private final JButton copyCommand = new JButton();
    private final JButton record = new JButton();
    private final JLabel empty = new JLabel();
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private List<JsonObject> shown = new ArrayList<>();
    private JsonObject view;
    private boolean french;
    private String selected = "";

    ArchvowExamplesView(Actions actions) {
        super(new BorderLayout());
        this.actions = actions;
        setName("archvow.examples");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new ArchvowItemCards.Renderer());
        list.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showSelected(); });
        detail.setEditable(false);
        detail.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        detail.addHyperlinkListener(e -> {
            if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED || e.getDescription() == null) return;
            String target = e.getDescription();
            if (target.startsWith("open:")) actions.openFile(target.substring(5), 0);
            else if (target.startsWith("brief:")) actions.openBrief(target.substring(6));
        });
        openTest.addActionListener(e -> {
            JsonObject example = current();
            String file = example == null ? "" : ArchvowExamples.testFile(view, ArchvowGrammar.str(example, "name"));
            if (!file.isBlank()) actions.openFile(file, 0);
        });
        copyCommand.addActionListener(e -> {
            JsonObject example = current();
            if (example != null) actions.copy(ArchvowExamples.command(view, example));
        });
        record.addActionListener(e -> actions.record(view == null ? "" : ArchvowGrammar.str(view, "component")));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        buttons.add(openTest);
        buttons.add(copyCommand);
        JPanel answer = new JPanel(new BorderLayout(0, 6));
        JScrollPane detailScroll = new JScrollPane(detail);
        detailScroll.setBorder(BorderFactory.createEmptyBorder());
        answer.add(detailScroll, BorderLayout.CENTER);
        answer.add(buttons, BorderLayout.SOUTH);
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setBorder(BorderFactory.createEmptyBorder());
        body.add(ArchvowSplitView.create(list, listScroll, answer), "examples");
        JPanel none = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        none.add(empty);
        none.add(record);
        body.add(none, "empty");
        add(body, BorderLayout.CENTER);
        setFrench(false);
    }

    void setFrench(boolean value) {
        french = value;
        list.putClientProperty("archvow.french", value);
        openTest.setText(value ? "Ouvrir le test" : "Open test");
        copyCommand.setText(value ? "Copier la commande" : "Copy command");
        copyCommand.setToolTipText(value ? "La commande qu'une personne lance pour enregistrer l'exemple à nouveau"
            : "The command a person runs to record the example again");
        record.setText(value ? "Enregistrer un exemple…" : "Record an example…");
        empty.setText(value ? "Aucun exemple métier sur ce composant." : "No business example on this component.");
    }

    /** A component's show --json: its examples drawn; the same example stays selected (same component). */
    void show(JsonObject data, boolean frenchText) {
        boolean another = view == null || !ArchvowGrammar.str(view, "component").equals(ArchvowGrammar.str(data, "component"));
        view = data;
        setFrench(frenchText);
        shown = ArchvowExamples.ordered(data);
        model.clear();
        for (JsonObject e : shown) model.addElement(ArchvowExamples.card(data, e, french));
        cards.show(body, shown.isEmpty() ? "empty" : "examples");
        if (shown.isEmpty()) return;
        int index = -1;
        if (!another) for (int i = 0; i < shown.size(); i++) if (ArchvowGrammar.str(shown.get(i), "name").equals(selected)) index = i;
        if (index < 0) {
            index = 0;
            for (int i = 0; i < shown.size(); i++) if (ArchvowExamples.state(shown.get(i), false).result().equals("failing")) { index = i; break; }
        }
        list.setSelectedIndex(index);
        showSelected();
    }

    private JsonObject current() {
        int i = list.getSelectedIndex();
        return i >= 0 && i < shown.size() ? shown.get(i) : null;
    }

    private void showSelected() {
        JsonObject example = current();
        if (example == null) return;
        selected = ArchvowGrammar.str(example, "name");
        detail.setText(ArchvowExamples.detailHtml(view, example, french));
        detail.setCaretPosition(0);
        openTest.setVisible(!ArchvowExamples.testFile(view, selected).isBlank());
        copyCommand.setVisible(ArchvowExamples.state(example, french).result().equals("failing"));
    }

    // regression-test seams
    int count() { return model.getSize(); }
    String detailText() { return detail.getText(); }
    boolean copyVisible() { return copyCommand.isVisible(); }
    boolean showsEmpty() { return shown.isEmpty(); }
}
