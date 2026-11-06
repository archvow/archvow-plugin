package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * A native Swing action bar that shows all actions when space allows.
 * It moves ONLY overflowing actions to More, without guessing a window-size breakpoint.
 * Hidden actions still use the original button listener, enabled state, and tooltip.
 *
 * Layout is managed on the EDT. No scrollbar, fixed-size breaks or animation timers.
 */
final class ArchvowOverflowBar extends JPanel {
    private static final int GAP = 5;
    private final List<AbstractButton> buttons = new ArrayList<>();
    private final JButton more;
    private final JPopupMenu overflow = new JPopupMenu();
    private int visibleCount;
    private boolean updating;
    private boolean separated;
    private boolean alwaysShowMore;
    private final List<String> extraLabels = new ArrayList<>();
    private final List<Runnable> extraActions = new ArrayList<>();

    ArchvowOverflowBar(String moreText) {
        super(new FlowLayout(FlowLayout.LEFT, GAP, 0));
        setOpaque(false);
        more = new JButton(moreText);
        ArchvowUiIcons.explicit(more, "dots");
        more.setFocusPainted(false);
        more.setText("");
        more.setToolTipText("More project commands");
        more.getAccessibleContext().setAccessibleName("More project commands");
        more.setPreferredSize(new Dimension(37, 31));
        more.addActionListener(e -> {
            populateMenu();
            overflow.show(more, 0, more.getHeight());
        });
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { refresh(); }
        });
    }

    void setSeparated(boolean value) { separated = value; refresh(); }

    void setAlwaysShowMore(boolean value) { alwaysShowMore = value; refresh(); }

    void addMenuAction(String label, Runnable action) {
        extraLabels.add(label);
        extraActions.add(action);
        refresh();
    }

    void addAction(AbstractButton button) {
        ArchvowUiIcons.decorate(button);
        buttons.add(button);
        refresh();
    }

    /** Useful after a localized label or selected Git base changes. */
    void refresh() {
        if (updating) return;
        updating = true;
        try {
            int width = getWidth();
            int n = buttons.size();
            int count = n;
            // While the component is unlaid out, retain all buttons; recalculate when sized.
            if (width > 0 && requiredWidth(n) + (alwaysShowMore ? more.getPreferredSize().width + GAP : 0) > width) {
                count = 0;
                int budget = width - more.getPreferredSize().width - (GAP * 3);
                int used = 0;
                for (AbstractButton button : buttons) {
                    int add = button.getPreferredSize().width + (count == 0 ? 0 : GAP + (separated ? 8 : 0));
                    if (used + add > budget) break;
                    used += add;
                    count++;
                }
            }
            visibleCount = count;
            removeAll();
            for (int i = 0; i < count; i++) {
                if (separated && i > 0) {
                    JSeparator divider = new JSeparator(SwingConstants.VERTICAL);
                    divider.setPreferredSize(new Dimension(1, 18));
                    add(divider);
                }
                add(buttons.get(i));
            }
            if (count < n || alwaysShowMore || !extraLabels.isEmpty()) add(more);
            revalidate();
            repaint();
        } finally {
            updating = false;
        }
    }

    private int requiredWidth(int n) {
        int width = GAP * 2;
        for (int i = 0; i < n; i++) width += buttons.get(i).getPreferredSize().width + (i > 0 ? GAP : 0);
        return width + (separated ? Math.max(0, n - 1) * (8 + GAP) : 0);
    }

    private void populateMenu() {
        overflow.removeAll();
        for (int i = visibleCount; i < buttons.size(); i++) {
            AbstractButton original = buttons.get(i);
            String name = original.getText();
            if (name == null || name.isBlank()) name = original.getAccessibleContext().getAccessibleName();
            if (name == null || name.isBlank()) name = original.getToolTipText();
            JMenuItem item = new JMenuItem(name);
            item.setEnabled(original.isEnabled());
            item.setIcon(original.getIcon());
            item.setToolTipText(original.getToolTipText());
            item.addActionListener(e -> original.doClick());
            overflow.add(item);
        }
        if (!extraLabels.isEmpty() && visibleCount < buttons.size()) overflow.addSeparator();
        for (int i = 0; i < extraLabels.size(); i++) {
            final Runnable action = extraActions.get(i);
            JMenuItem item = new JMenuItem(extraLabels.get(i));
            item.addActionListener(e -> action.run());
            overflow.add(item);
        }
    }

    @Override public Dimension getMinimumSize() {
        Dimension preferred = more.getPreferredSize();
        return new Dimension(Math.max(55, preferred.width + GAP * 2), preferred.height);
    }

    int visibleActionCount() { return visibleCount; }
    boolean hasOverflow() { return visibleCount < buttons.size(); }
    JPopupMenu menuForTest() { populateMenu(); return overflow; }
}
