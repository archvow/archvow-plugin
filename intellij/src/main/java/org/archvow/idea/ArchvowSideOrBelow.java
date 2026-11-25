package org.archvow.idea;

import java.awt.*;

/**
 * 1.2.1: two parts, the second beside the first while the first keeps the room it needs, else under it - a card's
 * title and its link, a step and its button. Side by side they overlapped or the first was cut ("Add the feature's
 * comp…") in a narrow tool window. The first part is the container's first child, the second its second (left out
 * when it is hidden). Plain AWT: tested headless ({@code ResponsiveViewsRegression}).
 */
final class ArchvowSideOrBelow implements LayoutManager {
    private final int gap;
    private final int mainNeeds;

    /** {@code mainNeeds}: the width the first part keeps beside the second; below 0, its own preferred width. */
    ArchvowSideOrBelow(int gap, int mainNeeds) {
        this.gap = gap;
        this.mainNeeds = mainNeeds;
    }

    @Override public void addLayoutComponent(String name, Component comp) { }
    @Override public void removeLayoutComponent(Component comp) { }

    private static Component part(Container c, int i) {
        return c.getComponentCount() > i && c.getComponent(i).isVisible() ? c.getComponent(i) : null;
    }

    private static int width(Container c) {
        if (c.getWidth() > 0) return c.getWidth();
        Container parent = c.getParent();
        while (parent != null && parent.getWidth() == 0) parent = parent.getParent();
        return parent == null ? 400 : parent.getWidth();
    }

    private boolean beside(Component main, Component side, int width) {
        if (side == null) return true;
        int needs = mainNeeds < 0 ? main.getPreferredSize().width : mainNeeds;
        return needs + gap + side.getPreferredSize().width <= width;
    }

    /** The part's height at a width: a wrapped text grows as the width shrinks. */
    private static int heightAt(Component part, int width) {
        if (part.getWidth() != width) part.setSize(width, Math.max(1, part.getHeight()));
        if (part instanceof Container nested) nested.doLayout();
        return part.getPreferredSize().height;
    }

    @Override public Dimension preferredLayoutSize(Container c) {
        synchronized (c.getTreeLock()) {
            Insets in = c.getInsets();
            int width = Math.max(1, width(c) - in.left - in.right);
            Component main = part(c, 0), side = part(c, 1);
            if (main == null) return new Dimension(in.left + in.right, in.top + in.bottom);
            int height;
            if (side == null) height = heightAt(main, width);
            else if (beside(main, side, width)) height = Math.max(heightAt(main, width - gap - side.getPreferredSize().width), side.getPreferredSize().height);
            else height = heightAt(main, width) + gap / 2 + side.getPreferredSize().height;
            int natural = main.getPreferredSize().width + (side == null ? 0 : gap + side.getPreferredSize().width);
            return new Dimension(Math.min(natural, width) + in.left + in.right, height + in.top + in.bottom);
        }
    }

    @Override public Dimension minimumLayoutSize(Container c) {
        Dimension preferred = preferredLayoutSize(c);
        return new Dimension(0, preferred.height);
    }

    @Override public void layoutContainer(Container c) {
        synchronized (c.getTreeLock()) {
            Insets in = c.getInsets();
            int width = Math.max(1, c.getWidth() - in.left - in.right);
            Component main = part(c, 0), side = part(c, 1);
            if (main == null) return;
            if (side == null) {
                main.setBounds(in.left, in.top, width, heightAt(main, width));
                return;
            }
            Dimension s = side.getPreferredSize();
            if (beside(main, side, width)) {
                int mainWidth = width - gap - s.width;
                int h = heightAt(main, mainWidth);
                main.setBounds(in.left, in.top, mainWidth, h);
                // the side part centred on the first line's height, at most the main part's
                side.setBounds(in.left + width - s.width, in.top + Math.max(0, (Math.min(h, s.height * 2) - s.height) / 2), s.width, s.height);
            } else {
                int h = heightAt(main, width);
                main.setBounds(in.left, in.top, width, h);
                side.setBounds(in.left, in.top + h + gap / 2, Math.min(s.width, width), s.height);
            }
        }
    }
}
