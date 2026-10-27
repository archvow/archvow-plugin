package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * A component view whose items are answered (Decisions, Questions, Safety, Assumptions): the items as
 * cards on the left, the selected one's answer panel on the right, side by side from
 * {@link #SIDE_BY_SIDE} pixels, one above the other in a narrower tab. The VS Code page does the same
 * ({@code media/workspace.css}, {@code .split}). Plain Swing: tested headless ({@code SplitViewRegression}).
 */
final class ArchvowSplitView {
    /** Under this width the answer panel goes under the items. */
    static final int SIDE_BY_SIDE = 680;
    static final int GAP = 14;

    private ArchvowSplitView() { }

    /** {@code items} left (or above), {@code answer} right (or below), in a box with a border; while
     *  {@code list} has no item the answer panel is gone and the items take the whole width. */
    static JPanel create(JList<?> list, JComponent items, JComponent answer) {
        JPanel box = new JPanel(new BorderLayout());
        box.setName("archvow.answer");
        box.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ArchvowMotion.blend(UIManager.getColor("Panel.background"),
                UIManager.getColor("Label.foreground"), .18), 1, true),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        box.add(answer, BorderLayout.CENTER);
        JPanel panel = new JPanel(new Layout());
        panel.add(items);
        panel.add(box);
        Runnable follow = () -> {
            boolean any = list.getModel().getSize() > 0;
            if (box.isVisible() == any) return;
            box.setVisible(any);
            panel.revalidate();
            panel.repaint();
        };
        onChange(list, follow);
        follow.run();
        return panel;
    }

    /**
     * The cards of a view: {@code scroll} (the list in its scroll pane), or {@code emptyText} ("Nothing to
     * display.") while the list has no item, as on the VS Code page.
     */
    static JPanel cards(JList<?> list, JScrollPane scroll, java.util.function.Supplier<String> emptyText) {
        CardLayout cards = new CardLayout();
        JPanel panel = new JPanel(cards);
        panel.setOpaque(false);
        JLabel empty = new JLabel();
        empty.setName("archvow.empty");
        empty.setVerticalAlignment(SwingConstants.TOP);
        empty.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));
        Color muted = UIManager.getColor("Label.disabledForeground");
        if (muted != null) empty.setForeground(muted);
        panel.add(scroll, "cards");
        panel.add(empty, "empty");
        Runnable follow = () -> {
            boolean any = list.getModel().getSize() > 0;
            empty.setText(emptyText.get());
            cards.show(panel, any ? "cards" : "empty");
        };
        onChange(list, follow);
        follow.run();
        return panel;
    }

    /** Whether the cards of {@link #cards} show their empty text. */
    static boolean showsEmpty(JPanel cards) {
        for (Component c : cards.getComponents()) if (c.isVisible()) return "archvow.empty".equals(c.getName());
        return false;
    }

    private static void onChange(JList<?> list, Runnable follow) {
        list.getModel().addListDataListener(new javax.swing.event.ListDataListener() {
            @Override public void intervalAdded(javax.swing.event.ListDataEvent e) { follow.run(); }
            @Override public void intervalRemoved(javax.swing.event.ListDataEvent e) { follow.run(); }
            @Override public void contentsChanged(javax.swing.event.ListDataEvent e) { follow.run(); }
        });
    }

    /** Whether a split view of this width shows its two parts side by side. */
    static boolean sideBySide(int width) {
        return width >= SIDE_BY_SIDE;
    }

    /** Side by side: half each, the whole height. Stacked: the items at their height (at most 2/5), the answer under them. */
    private static final class Layout implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component comp) { }
        @Override public void removeLayoutComponent(Component comp) { }

        @Override public Dimension preferredLayoutSize(Container parent) {
            Insets in = parent.getInsets();
            Dimension items = parent.getComponent(0).getPreferredSize();
            Dimension answer = parent.getComponent(1).getPreferredSize();
            return new Dimension(items.width + GAP + answer.width + in.left + in.right,
                Math.max(items.height, answer.height) + in.top + in.bottom);
        }

        @Override public Dimension minimumLayoutSize(Container parent) {
            return new Dimension(200, 160);
        }

        @Override public void layoutContainer(Container parent) {
            if (parent.getComponentCount() < 2) return;
            Insets in = parent.getInsets();
            int x = in.left;
            int y = in.top;
            int width = Math.max(0, parent.getWidth() - in.left - in.right);
            int height = Math.max(0, parent.getHeight() - in.top - in.bottom);
            Component items = parent.getComponent(0);
            Component answer = parent.getComponent(1);
            if (!answer.isVisible()) {
                // no item: "Nothing to display." over the whole view
                items.setBounds(x, y, width, height);
            } else if (sideBySide(width)) {
                int left = (width - GAP) / 2;
                items.setBounds(x, y, left, height);
                answer.setBounds(x + left + GAP, y, width - left - GAP, height);
            } else {
                int top = Math.min(items.getPreferredSize().height, Math.max(120, height * 2 / 5));
                items.setBounds(x, y, width, top);
                answer.setBounds(x, y + top + GAP, width, Math.max(0, height - top - GAP));
            }
        }
    }

    /**
     * The answer panel's sections one under the other, each at its preferred height (it grows when the
     * inline confirmation opens), as wide as the panel; the room left stays under them, and a scroll bar
     * appears when they are taller than the panel. The stack follows the viewport's width (no button
     * cut on the right) and not its height (no field stretched to fill the panel).
     */
    static JScrollPane stack(JComponent... sections) {
        JPanel stack = new Stack();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        for (JComponent section : sections) {
            section.setAlignmentX(Component.LEFT_ALIGNMENT);
            section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
            stack.add(section);
            stack.add(Box.createVerticalStrut(7));
        }
        JScrollPane pane = new JScrollPane(stack, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.getVerticalScrollBar().setUnitIncrement(16);
        return pane;
    }

    private static final class Stack extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return visible.height; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /**
     * The buttons under an answer field: {@code main} (Cancel, Save answer) on the right, {@code others}
     * (Propose, Not applicable...) on the left of the same row when both fit, else on a row under them.
     * Hidden buttons take no room.
     */
    static JPanel actions(java.util.List<? extends JComponent> others, java.util.List<? extends JComponent> main) {
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        left.setOpaque(false);
        right.setOpaque(false);
        for (JComponent c : others) left.add(c);
        for (JComponent c : main) right.add(c);
        JPanel panel = new JPanel(new ActionsLayout());
        panel.setOpaque(false);
        panel.add(left);
        panel.add(right);
        return panel;
    }

    private static boolean anyVisible(Container c) {
        for (Component child : c.getComponents()) if (child.isVisible()) return true;
        return false;
    }

    /** One row when the two groups fit in the width, else the main group first and the others under it. */
    private static final class ActionsLayout implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component comp) { }
        @Override public void removeLayoutComponent(Component comp) { }

        private static boolean oneRow(Container parent, Dimension left, Dimension right) {
            int width = parent.getWidth() - parent.getInsets().left - parent.getInsets().right;
            return !anyVisible((Container) parent.getComponent(0)) || width <= 0 || left.width + right.width <= width;
        }

        @Override public Dimension preferredLayoutSize(Container parent) {
            Dimension left = parent.getComponent(0).getPreferredSize();
            Dimension right = parent.getComponent(1).getPreferredSize();
            Insets in = parent.getInsets();
            boolean others = anyVisible((Container) parent.getComponent(0));
            int height = !others ? right.height : oneRow(parent, left, right) ? Math.max(left.height, right.height)
                : right.height + 4 + left.height;
            return new Dimension(left.width + right.width + in.left + in.right, height + in.top + in.bottom);
        }

        @Override public Dimension minimumLayoutSize(Container parent) {
            Dimension right = parent.getComponent(1).getMinimumSize();
            return new Dimension(right.width, right.height);
        }

        @Override public void layoutContainer(Container parent) {
            Insets in = parent.getInsets();
            int width = Math.max(0, parent.getWidth() - in.left - in.right);
            Component left = parent.getComponent(0);
            Component right = parent.getComponent(1);
            Dimension l = left.getPreferredSize();
            Dimension r = right.getPreferredSize();
            boolean others = anyVisible((Container) left);
            left.setVisible(others);
            if (!others || oneRow(parent, l, r)) {
                int height = Math.max(l.height, r.height);
                left.setBounds(in.left, in.top, Math.max(0, width - r.width), height);
                right.setBounds(in.left + Math.max(0, width - r.width), in.top, Math.min(width, r.width), height);
            } else {
                right.setBounds(in.left, in.top, width, r.height);
                left.setBounds(in.left, in.top + r.height + 4, width, l.height);
            }
        }
    }

    /**
     * The panel's main button ("Save answer", "Declare hazard", "Accept"): filled with the accent colour,
     * painted here so that it reads the same in every IDE theme.
     */
    static JButton primary(String text) {
        JButton button = new JButton(text) {
            @Override public void updateUI() {
                super.updateUI();
                setContentAreaFilled(false);
                setBorderPainted(false);
                setFocusPainted(false);
                setOpaque(false);
                setForeground(Color.WHITE);
                setBorder(BorderFactory.createEmptyBorder(5, 14, 5, 14));
            }

            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Color fill = ArchvowMotion.accent();
                    if (!isEnabled()) fill = ArchvowMotion.blend(getParent() == null ? null : getParent().getBackground(), fill, .45);
                    else if (getModel().isPressed()) fill = fill.darker();
                    else if (getModel().isRollover()) fill = ArchvowMotion.blend(fill, Color.WHITE, .12);
                    g.setColor(fill);
                    g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                } finally {
                    g.dispose();
                }
                super.paintComponent(graphics);
            }
        };
        button.setRolloverEnabled(true);
        button.updateUI();
        return button;
    }
}
