package org.archvow.idea;

import javax.swing.*;
import java.awt.*;

/**
 * A FlowLayout that asks for the height of every row it fills. A plain FlowLayout wraps its buttons
 * onto a second row but asks only for one: the buttons of that row were laid out outside the panel,
 * out of sight (the Safety view lost "Remove hazard…" and "Remove invariant…" that way).
 */
final class ArchvowWrapLayout extends FlowLayout {
    ArchvowWrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override public Dimension preferredLayoutSize(Container target) { return size(target, true); }

    @Override public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = size(target, false);
        minimum.width -= getHgap() + 1;
        return minimum;
    }

    private Dimension size(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int width = target.getSize().width;
            Container scroll = SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
            if (width == 0 && target.getParent() != null) width = target.getParent().getSize().width;
            if (scroll != null && scroll.getSize().width > 0) width = Math.min(width == 0 ? Integer.MAX_VALUE : width, scroll.getSize().width);
            if (width == 0) width = Integer.MAX_VALUE;
            Insets insets = target.getInsets();
            int available = width - insets.left - insets.right - getHgap() * 2;
            Dimension total = new Dimension(0, 0);
            int rowWidth = 0, rowHeight = 0;
            for (Component child : target.getComponents()) {
                if (!child.isVisible()) continue;
                Dimension d = preferred ? child.getPreferredSize() : child.getMinimumSize();
                if (rowWidth > 0 && rowWidth + getHgap() + d.width > available) {
                    total.width = Math.max(total.width, rowWidth);
                    total.height += rowHeight + getVgap();
                    rowWidth = 0;
                    rowHeight = 0;
                }
                rowWidth += (rowWidth > 0 ? getHgap() : 0) + d.width;
                rowHeight = Math.max(rowHeight, d.height);
            }
            total.width = Math.max(total.width, rowWidth) + insets.left + insets.right + getHgap() * 2;
            total.height += rowHeight + insets.top + insets.bottom + getVgap() * 2;
            return total;
        }
    }
}
