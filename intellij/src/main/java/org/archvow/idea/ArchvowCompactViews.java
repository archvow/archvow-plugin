package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Component-vow navigation. Show every view that fits; overflow only when there
 * is not enough actual pixel width. Uses one stable CardLayout owner in ArchvowPanel.
 */
final class ArchvowCompactViews extends JPanel {
    private static final int GAP = 4;
    private final List<JToggleButton> views = new ArrayList<>();
    /** The view (card) index of each shown button: a view may be shown before others added earlier
     *  (0.18.0: Examples, view 8, right after Rules). */
    private final List<Integer> indexes = new ArrayList<>();
    private final IntConsumer selection;
    private final JToggleButton more = new ArchvowMotion.EditorTabButton("More ▾");
    private final JPopupMenu menu = new JPopupMenu();
    private int activeIndex = 0;
    private int visibleLimit;
    private int forcedLimit = -1; // regression-test seam only
    private boolean french;
    private boolean updating;

    ArchvowCompactViews(IntConsumer selection) { this(selection, false); }

    ArchvowCompactViews(IntConsumer selection, boolean french) {
        super(new FlowLayout(FlowLayout.LEFT, GAP, 0));
        this.selection = selection;
        this.french = french;
        setOpaque(false);
        ArchvowUiIcons.explicit(more, "dots");
        more.addActionListener(e -> {
            populateMenu();
            menu.show(more, 0, more.getHeight());
        });
        ArchvowMotion.installViewButton(more);
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { refresh(); }
        });
    }

    void addView(JToggleButton button) {
        addView(button, views.size());
    }

    /** The next view (its index: the number of views added before it), shown at {@code position}. */
    void addView(JToggleButton button, int position) {
        ArchvowUiIcons.decorate(button);
        int at = Math.max(0, Math.min(position, views.size()));
        indexes.add(at, views.size());
        views.add(at, button);
        refresh();
    }

    void setActive(int index) {
        if (index < 0 || index >= views.size()) return;
        activeIndex = index;
        refresh();
    }

    /** Where the view {@code index} is shown. */
    private int position(int index) {
        int at = indexes.indexOf(index);
        return at < 0 ? index : at;
    }

    void setFrench(boolean value) { french = value; refresh(); }
    int activeIndex() { return activeIndex; }
    int visiblePrimaryCount() { return visibleLimit; }
    boolean hasHorizontalScroller() { return false; }
    boolean hasOverflow() { return visibleLimit < views.size(); }

    void forcePrimaryLimitForTest(int limit) { forcedLimit = limit; refresh(); }

    void refresh() {
        if (updating) return;
        updating = true;
        try {
            int count = views.size();
            more.setText(french ? "Plus ▾" : "More ▾");
            ArchvowUiIcons.decorate(more);
            int width = getWidth();
            int totalWidth = GAP * 2;
            for (int i = 0; i < views.size(); i++) {
                totalWidth += views.get(i).getPreferredSize().width + (i > 0 ? GAP : 0);
            }
            if (forcedLimit >= 0) {
                count = Math.max(0, Math.min(forcedLimit, views.size()));
            } else if (width > 0 && totalWidth > width) {
                count = 0;
                int budget = width - more.getPreferredSize().width - GAP * 3;
                int used = 0;
                for (JToggleButton button : views) {
                    int needed = button.getPreferredSize().width + (count == 0 ? 0 : GAP);
                    if (used + needed > budget) break;
                    used += needed;
                    count++;
                }
            }
            visibleLimit = count;
            removeAll();
            for (int i = 0; i < count; i++) add(views.get(i));
            int active = position(activeIndex);
            for (int i = 0; i < views.size(); i++) {
                JToggleButton button = views.get(i);
                button.setSelected(i == active);
                ArchvowMotion.updateViewButton(button);
            }
            if (count < views.size()) {
                more.setSelected(active >= count && active < views.size());
                more.setToolTipText(more.isSelected() ? views.get(active).getText() :
                    (french ? "Autres sections" : "Other sections"));
                ArchvowMotion.updateViewButton(more);
                add(more);
            }
            revalidate();
            repaint();
        } finally {
            updating = false;
        }
    }

    private void populateMenu() {
        menu.removeAll();
        int active = position(activeIndex);
        for (int i = visibleLimit; i < views.size(); i++) {
            final int index = indexes.get(i);
            JMenuItem item = new JMenuItem((i == active ? "✓  " : "   ") + views.get(i).getText());
            item.addActionListener(e -> selection.accept(index));
            menu.add(item);
        }
    }

    @Override public Dimension getMinimumSize() {
        Dimension d = more.getPreferredSize();
        return new Dimension(Math.max(50, d.width + GAP * 2), d.height);
    }
}
