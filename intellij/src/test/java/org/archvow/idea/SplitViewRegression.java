package org.archvow.idea;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * The component views whose items are answered (0.11.0): the items as cards on the left, the answer
 * panel on the right (under them in a narrow tab), the buttons under the answer field, the cards' text,
 * and the first open item chosen when a component opens.
 */
public final class SplitViewRegression {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static void layout(Container c) {
        c.doLayout();
        for (Component child : c.getComponents()) if (child instanceof Container inner) layout(inner);
    }

    private static Rectangle in(Component c, Component root) {
        return SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), root);
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    public static void main(String[] args) {
        // side by side from SIDE_BY_SIDE pixels, else the answer under the items
        DefaultListModel<String> model = new DefaultListModel<>();
        for (String n : List.of("1", "2", "3")) model.addElement(n);
        JList<String> itemList = new JList<>(model);
        JScrollPane items = new JScrollPane(itemList);
        items.setPreferredSize(new Dimension(300, 150));
        JPanel answer = new JPanel();
        answer.setPreferredSize(new Dimension(300, 260));
        JPanel split = ArchvowSplitView.create(itemList, items, answer);
        Component box = split.getComponent(1);
        check("archvow.answer".equals(box.getName()) && SwingUtilities.isDescendingFrom(answer, box), "the answer panel is boxed");
        split.setSize(900, 500);
        layout(split);
        Rectangle left = in(items, split);
        Rectangle right = in(box, split);
        check(left.x == 0 && left.height == 500 && right.y == 0 && right.x >= left.x + left.width + ArchvowSplitView.GAP - 1,
            "side by side at 900: " + left + " / " + right);
        check(Math.abs(left.width - right.width) <= 1, "two halves: " + left.width + " / " + right.width);
        split.setSize(560, 700);
        layout(split);
        left = in(items, split);
        right = in(box, split);
        check(left.width == 560 && right.width == 560 && right.y >= left.y + left.height, "stacked at 560: " + left + " / " + right);
        check(left.height == 150, "the items at their height when it is under 2/5: " + left.height);
        // a view with no item: "Nothing to display." over the whole view, no answer panel (0.11.1)
        model.clear();
        split.setSize(900, 500);
        layout(split);
        check(!box.isVisible() && in(items, split).width == 900, "no item, no answer panel: " + in(items, split));
        model.addElement("1");
        layout(split);
        check(box.isVisible() && in(items, split).width < 900, "an item again: the answer panel is back");
        DefaultListModel<String> none = new DefaultListModel<>();
        JList<String> emptyList = new JList<>(none);
        JPanel cardsPanel = ArchvowSplitView.cards(emptyList, new JScrollPane(emptyList), () -> "Nothing to display.");
        check(ArchvowSplitView.showsEmpty(cardsPanel), "the cards say there is nothing");
        JLabel emptyText = null;
        for (Component c : cardsPanel.getComponents()) if (c instanceof JLabel l) emptyText = l;
        check(emptyText != null && "Nothing to display.".equals(emptyText.getText()), "the empty text");
        none.addElement("P1");
        check(!ArchvowSplitView.showsEmpty(cardsPanel), "the cards come back with an item");

        check(ArchvowSplitView.sideBySide(ArchvowSplitView.SIDE_BY_SIDE) && !ArchvowSplitView.sideBySide(ArchvowSplitView.SIDE_BY_SIDE - 1),
            "the threshold");

        // the answer panel's sections at their height, as wide as the panel (no field stretched, no button cut)
        JScrollPane heading = new JScrollPane(new JTextPane());
        heading.setPreferredSize(new Dimension(200, 60));
        JScrollPane field = new JScrollPane(new JTextArea());
        field.setPreferredSize(new Dimension(150, 96));
        JPanel wide = new JPanel();
        wide.setPreferredSize(new Dimension(900, 30));
        JScrollPane stack = ArchvowSplitView.stack(heading, field, wide);
        stack.setSize(400, 600);
        layout(stack);
        Component view = stack.getViewport().getView();
        check(view.getWidth() == stack.getViewport().getWidth(), "the stack as wide as the panel: " + view.getWidth());
        check(field.getHeight() == 96 && view.getHeight() < 300, "the field at its height: " + field.getHeight() + ", stack " + view.getHeight());

        // the buttons: Cancel and Save answer on the right, the others on the left of the same row when they fit
        JButton propose = new JButton("Propose");
        JButton accept = new JButton("Accept proposal");
        JButton cancel = new JButton("Cancel");
        JButton save = ArchvowSplitView.primary("Save answer");
        JPanel actions = ArchvowSplitView.actions(List.of(propose, accept), List.of(cancel, save));
        actions.setSize(700, actions.getPreferredSize().height);
        layout(actions);
        Rectangle s = in(save, actions);
        Rectangle p = in(propose, actions);
        check(s.y == p.y || Math.abs(s.y - p.y) < 3, "one row at 700: " + s + " / " + p);
        check(actions.getWidth() - (s.x + s.width) < 12 && p.x < 12, "Save on the right, Propose on the left: " + s + " / " + p);
        actions.setSize(300, 200);
        int twoRows = actions.getPreferredSize().height;
        actions.setSize(300, twoRows);
        layout(actions);
        s = in(save, actions);
        p = in(propose, actions);
        check(p.y > s.y + s.height - 1, "too narrow: the others under Cancel / Save answer: " + s + " / " + p);
        propose.setVisible(false);
        accept.setVisible(false);
        check(actions.getPreferredSize().height < twoRows, "hidden buttons take no room");

        // the main button is painted with the accent colour, in both themes
        save.setSize(save.getPreferredSize());
        BufferedImage image = new BufferedImage(save.getWidth(), save.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        save.paint(g);
        g.dispose();
        Color pixel = new Color(image.getRGB(4, save.getHeight() / 2), true);
        Color accent = ArchvowMotion.accent();
        check(Math.abs(pixel.getRed() - accent.getRed()) + Math.abs(pixel.getGreen() - accent.getGreen())
            + Math.abs(pixel.getBlue() - accent.getBlue()) < 30, "Save answer filled with the accent: " + pixel);

        // the cards: what archvow show --json gives (orders_reader, its decision 3 left open)
        JsonObject one = json("{\"number\":1,\"question\":\"The path does not exist or is empty: fail or return an empty DataFrame?\","
            + "\"answer\":\"Fail with a clear error: a missing input is an operations problem\",\"suggested\":null,"
            + "\"by\":\"architect@example.com\",\"on\":\"2026-10-08T13:06:23.938480+00:00\",\"proposed\":null}");
        JsonObject three = json("{\"number\":3,\"question\":\"Header row missing: infer the columns or fail?\",\"answer\":null,"
            + "\"proposed\":\"Fail: the header is part of the input contract\"}");
        ArchvowItemCards.Card answered = ArchvowItemCards.decision(one, false);
        ArchvowItemCards.Card open = ArchvowItemCards.decision(three, false);
        check(!answered.open() && answered.by().equals("architect@example.com · 2026-10-08"), "answered: " + answered);
        check(open.open() && open.note().equals("AI proposal: Fail: the header is part of the input contract"), "open: " + open);
        check(ArchvowItemCards.decision(three, true).note().startsWith("Proposition IA : "), "French note");
        List<ArchvowItemCards.Card> cards = List.of(answered, answered, open, open);
        check(ArchvowItemCards.firstOpen(cards) == 2, "a component opens on its first open item");
        check(ArchvowItemCards.firstOpen(List.of(answered, answered)) == 0, "none open: the first");
        check(ArchvowItemCards.firstOpen(List.of()) == -1, "no item: none");
        check(ArchvowItemCards.indexOf(cards, "3", 0) == 2 && ArchvowItemCards.indexOf(cards, "9", 1) == 1, "the same card kept");
        JsonObject safety = json("{\"number\":2,\"guide_word\":\"more\",\"question\":\"Too many rows: what must never happen?\","
            + "\"status\":\"dismissed\",\"dismissed\":\"one file a day, keyed\"}");
        ArchvowItemCards.Card s2 = ArchvowItemCards.safety(safety, false);
        check(s2.number().equals("S2") && !s2.open() && s2.answer().equals("Not applicable: one file a day, keyed"), "safety: " + s2);
        ArchvowItemCards.Card a1 = ArchvowItemCards.assumption(json("{\"number\":1,\"text\":\"UTF-8 files\",\"status\":\"open\"}"), true);
        check(a1.number().equals("A1") && a1.open() && a1.state().equals("À examiner"), "assumption: " + a1);

        // the renderer: "3 · question", then "● Open" (amber) or the answer (green), then who and when
        JList<ArchvowItemCards.Card> list = new JList<>(cards.toArray(new ArchvowItemCards.Card[0]));
        list.setSize(420, 300);
        ArchvowItemCards.Renderer renderer = new ArchvowItemCards.Renderer();
        JPanel cell = (JPanel) renderer.getListCellRendererComponent(list, open, 2, true, false);
        JLabel title = (JLabel) cell.getComponent(0);
        JLabel status = (JLabel) cell.getComponent(2);
        JLabel by = (JLabel) cell.getComponent(4);
        check(title.getText().equals("3 · Header row missing: infer the columns or fail?"), title.getText());
        check(status.getText().startsWith("● Open · AI proposal: "), status.getText());
        check(status.getForeground().getRed() > status.getForeground().getBlue(), "open in amber");
        renderer.getListCellRendererComponent(list, answered, 0, false, false);
        check(status.getText().equals("Fail with a clear error: a missing input is an operations problem"), status.getText());
        check(status.getForeground().getGreen() > status.getForeground().getRed(), "answered in green");
        check(by.getText().equals("architect@example.com · 2026-10-08"), by.getText());
        list.setSize(160, 300);
        renderer.getListCellRendererComponent(list, answered, 0, false, false);
        check(title.getText().endsWith("…") && title.getToolTipText().startsWith("The path does not exist"), "cut to the card: " + title.getText());
        System.out.println("OK: split view, answer buttons, cards and first open item");
    }
}
