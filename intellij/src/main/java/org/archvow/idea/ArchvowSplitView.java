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
        JPanel left = new JPanel(new ArchvowWrapLayout(FlowLayout.LEFT, 5, 1));
        JPanel right = new JPanel(new ArchvowWrapLayout(FlowLayout.RIGHT, 5, 1));
        left.setOpaque(false);
        right.setOpaque(false);
        for (JComponent c : others) left.add(c);
        for (JComponent c : main) right.add(c);
        JPanel panel = new JPanel(new ActionsLayout()) {
            @Override public void setBounds(int x, int y, int width, int height) {
                boolean resized = width != getWidth();
                super.setBounds(x, y, width, height);
                // A wrapped button row changes height when the split view resizes.
                if (resized && isDisplayable()) SwingUtilities.invokeLater(this::revalidate);
            }
        };
        panel.setOpaque(false);
        panel.add(left);
        panel.add(right);
        return panel;
    }

    private static boolean anyVisible(Container c) {
        for (Component child : c.getComponents()) if (child.isVisible()) return true;
        return false;
    }

    /**
     * The right-hand buttons can themselves require two rows (long translated labels).
     * Measure their height at the *actual* available width, rather than using a FlowLayout's
     * single-row preferred height: otherwise the Save button is painted below the panel.
     */
    private static final class ActionsLayout implements LayoutManager {
        private static final int ROW_GAP = 4;
        @Override public void addLayoutComponent(String name, Component comp) { }
        @Override public void removeLayoutComponent(Component comp) { }

        private static int naturalWidth(Container group) {
            int width = 10; // two 5-pixel FlowLayout margins
            boolean first = true;
            for (Component c : group.getComponents()) {
                if (!c.isVisible()) continue;
                if (!first) width += 5;
                width += c.getPreferredSize().width;
                first = false;
            }
            return width;
        }

        private static int groupHeight(Container group, int width) {
            group.setSize(Math.max(1, width), group.getHeight());
            return group.getPreferredSize().height;
        }

        private static int available(Container parent) {
            Insets in = parent.getInsets();
            int width = parent.getWidth();
            if (width == 0 && parent.getParent() != null) width = parent.getParent().getWidth();
            return Math.max(1, width - in.left - in.right);
        }

        private static boolean onSameRow(Container parent, boolean others) {
            return others && (parent.getWidth() > 0 || parent.getParent() != null && parent.getParent().getWidth() > 0) &&
                naturalWidth((Container) parent.getComponent(0)) +
                naturalWidth((Container) parent.getComponent(1)) <= available(parent);
        }

        @Override public Dimension preferredLayoutSize(Container parent) {
            Container left = (Container) parent.getComponent(0);
            Container right = (Container) parent.getComponent(1);
            boolean others = anyVisible(left);
            int width = parent.getWidth() > 0 || parent.getParent() != null && parent.getParent().getWidth() > 0
                ? available(parent) : naturalWidth(left) + naturalWidth(right);
            int height;
            if (onSameRow(parent, others)) {
                height = Math.max(groupHeight(left, naturalWidth(left)), groupHeight(right, naturalWidth(right)));
            } else {
                height = groupHeight(right, width);
                if (others) height += ROW_GAP + groupHeight(left, width);
            }
            Insets in = parent.getInsets();
            return new Dimension(Math.max(1, width) + in.left + in.right,
                height + in.top + in.bottom);
        }

        @Override public Dimension minimumLayoutSize(Container parent) {
            return new Dimension(100, 32);
        }

        @Override public void layoutContainer(Container parent) {
            Insets in = parent.getInsets();
            int width = available(parent);
            Container left = (Container) parent.getComponent(0);
            Container right = (Container) parent.getComponent(1);
            boolean others = anyVisible(left);
            left.setVisible(others);
            if (onSameRow(parent, others)) {
                int rw = naturalWidth(right);
                int lw = width - rw;
                int h = Math.max(groupHeight(left, lw), groupHeight(right, rw));
                left.setBounds(in.left, in.top, lw, h);
                right.setBounds(in.left + lw, in.top, rw, h);
            } else {
                int rh = groupHeight(right, width);
                right.setBounds(in.left, in.top, width, rh);
                if (others) {
                    int lh = groupHeight(left, width);
                    left.setBounds(in.left, in.top + rh + ROW_GAP, width, lh);
                } else {
                    left.setBounds(in.left, in.top, 0, 0);
                }
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
