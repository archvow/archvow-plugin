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

    ArchvowOverflowBar(String moreText) {
        super(new FlowLayout(FlowLayout.LEFT, GAP, 0));
        setOpaque(false);
        more = new JButton(moreText);
        ArchvowUiIcons.explicit(more, "dots");
        more.setFocusPainted(false);
        more.addActionListener(e -> {
            populateMenu();
            overflow.show(more, 0, more.getHeight());
        });
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { refresh(); }
        });
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
            if (width > 0 && requiredWidth(n) > width) {
                count = 0;
                int budget = width - more.getPreferredSize().width - (GAP * 3);
                int used = 0;
                for (AbstractButton button : buttons) {
                    int add = button.getPreferredSize().width + (count == 0 ? 0 : GAP);
                    if (used + add > budget) break;
                    used += add;
                    count++;
                }
            }
            visibleCount = count;
            removeAll();
            for (int i = 0; i < count; i++) add(buttons.get(i));
            if (count < n) add(more);
            revalidate();
            repaint();
        } finally {
            updating = false;
        }
    }

    private int requiredWidth(int n) {
        int width = GAP * 2;
        for (int i = 0; i < n; i++) width += buttons.get(i).getPreferredSize().width + (i > 0 ? GAP : 0);
        return width;
    }

    private void populateMenu() {
        overflow.removeAll();
        for (int i = visibleCount; i < buttons.size(); i++) {
            AbstractButton original = buttons.get(i);
            JMenuItem item = new JMenuItem(original.getText());
            item.setEnabled(original.isEnabled());
            item.setIcon(original.getIcon());
            item.setToolTipText(original.getToolTipText());
            item.addActionListener(e -> original.doClick());
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
