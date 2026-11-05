package org.archvow.idea;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Map;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.RoundRectangle2D;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;

/**
 * Small, EDT-only navigation effects. No external UI framework and no perpetual timers.
 * Hover is deliberately subtler than selection; colors track the active IDE theme.
 */
final class ArchvowMotion {
    private ArchvowMotion() {}

    static final int FRAME_MS = 16;
    private static final String HOVER = "archvow.motion.hover";
    private static final String HOVER_AMOUNT = "archvow.motion.hoverAmount";
    private static final String HOVER_PREVIOUS = "archvow.motion.hoverPrevious";
    private static final String HOVER_PREVIOUS_AMOUNT = "archvow.motion.hoverPreviousAmount";
    private static final String INSTALLED = "archvow.motion.installed";

    static Color accent() {
        // Use a calm, consistent accent in both themes. Relying on the IDE focus color
        // produced yellow/gold highlights in some dark themes, which looked too strong
        // for navigation tabs and buttons.
        return dark() ? new Color(88, 163, 255) : new Color(48, 116, 220);
    }

    static boolean dark() {
        Color c = UIManager.getColor("Panel.background");
        return c != null && c.getRed() * .299 + c.getGreen() * .587 + c.getBlue() * .114 < 128;
    }

    static Color blend(Color background, Color foreground, double fraction) {
        Color a = background == null ? (dark() ? new Color(44, 46, 51) : Color.WHITE) : background;
        Color b = foreground == null ? accent() : foreground;
        double t = Math.max(0, Math.min(1, fraction));
        return new Color((int) Math.round(a.getRed() * (1 - t) + b.getRed() * t),
            (int) Math.round(a.getGreen() * (1 - t) + b.getGreen() * t),
            (int) Math.round(a.getBlue() * (1 - t) + b.getBlue() * t));
    }

    static void installTabs(JTabbedPane pane) {
        if (Boolean.TRUE.equals(pane.getClientProperty(INSTALLED))) return;
        pane.putClientProperty(INSTALLED, Boolean.TRUE);
        pane.setUI(new MotionTabsUI());
    }

    static void installList(JList<?> list) {
        if (Boolean.TRUE.equals(list.getClientProperty(INSTALLED))) return;
        list.putClientProperty(INSTALLED, Boolean.TRUE);
        new HoverTracker(list).install();
    }

    static float hoverLevel(JComponent list, int index) {
        Object row = list.getClientProperty(HOVER);
        Object value = list.getClientProperty(HOVER_AMOUNT);
        if (row instanceof Integer && (Integer) row == index && value instanceof Float) return (Float) value;
        Object previous = list.getClientProperty(HOVER_PREVIOUS);
        Object previousValue = list.getClientProperty(HOVER_PREVIOUS_AMOUNT);
        return previous instanceof Integer && (Integer) previous == index && previousValue instanceof Float
            ? (Float) previousValue : 0f;
    }

    static void installTree(JTree tree) {
        if (Boolean.TRUE.equals(tree.getClientProperty(INSTALLED))) return;
        tree.putClientProperty(INSTALLED, Boolean.TRUE);
        new HoverTracker(tree).install();
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override public Component getTreeCellRendererComponent(JTree source, Object value,
                    boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(source, value, selected, expanded, leaf, row, hasFocus);
                Color background = source.getBackground();
                Color active = accent();
                float hover = hoverLevel(source, row);
                Color fill = selected ? blend(background, active, dark() ? .36 : .24)
                    : blend(background, active, (dark() ? .15 : .11) * hover);
                setBackgroundSelectionColor(fill);
                setBackgroundNonSelectionColor(fill);
                setTextSelectionColor(dark() ? Color.WHITE : new Color(25, 53, 100));
                // 0.15.0: each node draws what it is (icons.json): the system, a layer, the component's type
                Object user = value instanceof DefaultMutableTreeNode n ? n.getUserObject() : null;
                if (user instanceof ArchvowTreeIcons.Kinded kinded) setIcon(ArchvowTreeIcons.icon(kinded.iconKind(), null));
                // Status markers come from the component actions, not the raw hazard count.
                if (value instanceof DefaultMutableTreeNode node && node.isLeaf() && !(user instanceof ArchvowTreeIcons.Node)) {
                    String original = getText();
                    String id = original.contains("  ·  ") ? original.substring(0, original.indexOf("  ·  ")) : original;
                    int pending = statusCount(source, "archvow.pendingCounts", id);
                    int resolved = statusCount(source, "archvow.resolvedCounts", id);
                    Color tint = null;
                    if (pending > 0) {
                        setText(original + "  (" + pending + ")");
                        tint = dark() ? new Color(255, 193, 114) : new Color(150, 89, 17);
                    } else if (resolved > 0) {
                        setText(original + "  ✓");
                        tint = dark() ? new Color(121, 224, 158) : new Color(23, 121, 68);
                    }
                    if (tint != null && !selected) setForeground(tint);
                    // the type's drawing in the status colour, even when selected (the text says it too)
                    if (user instanceof ArchvowTreeIcons.Kinded kinded && tint != null) setIcon(ArchvowTreeIcons.icon(kinded.iconKind(), tint));
                }
                setIconTextGap(6);
                return this;
            }
        });
    }


    private static int statusCount(JTree tree, String property, String id) {
        Object value = tree.getClientProperty(property);
        if (value instanceof Map<?, ?> map) {
            Object count = map.get(id);
            if (count instanceof Number number) return number.intValue();
        }
        return 0;
    }

    /** Consistent compact command list: hover tint < selected tint. */
    static final class CommandRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            float hover = hoverLevel(list, index);
            setOpaque(true);
            setBackground(blend(list.getBackground(), accent(),
                selected ? (dark() ? .34 : .23) : (dark() ? .15 : .10) * hover));
            setForeground(selected ? (dark() ? Color.WHITE : new Color(22, 52, 102)) : list.getForeground());
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, selected ? 4 : 3, 0, 0,
                    selected ? accent() : blend(list.getBackground(), accent(), .10 * hover)),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)));
            return this;
        }
    }

    /**
     * A small, editor-tab-like control, shared by all six component views.
     * No custom Look-and-Feel, raised buttons, focus-colored underline or layout shifts.
     * It is deliberately compact and paints the same selection treatment as MotionTabsUI.
     */
    static final class EditorTabButton extends JToggleButton {
        private float hoverAmount;
        private boolean hovered;
        private Timer transition;

        EditorTabButton(String text) {
            super(text);
            setFocusable(true);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setMargin(new Insets(0, 0, 0, 0));
            setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(UIManager.getFont("Label.font") == null ? getFont()
                : UIManager.getFont("Label.font"));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true; startTransition(); }
                @Override public void mouseExited(MouseEvent e) { hovered = false; startTransition(); }
            });
        }

        private void startTransition() {
            if (transition == null) {
                transition = new Timer(FRAME_MS, e -> {
                    float target = hovered && isEnabled() ? 1f : 0f;
                    hoverAmount += (target - hoverAmount) * .35f;
                    if (Math.abs(target - hoverAmount) < .03f) hoverAmount = target;
                    repaint();
                    if (hoverAmount == target) transition.stop();
                });
            }
            if (!transition.isRunning()) transition.start();
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                paintEditorTab(g, 2, 2, Math.max(0, getWidth()-4), Math.max(0, getHeight()-4),
                    isSelected(), hoverAmount);
            } finally { g.dispose(); }
            super.paintComponent(graphics);
        }

        @Override public void updateUI() {
            super.updateUI();
            setContentAreaFilled(false);
            setOpaque(false);
            setBorderPainted(false);
            setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
        }
    }

    static void installViewButton(JToggleButton button) {
        // All navigation buttons use EditorTabButton; allow callers to keep JToggleButton API.
        if (Boolean.TRUE.equals(button.getClientProperty("archvow.view.button"))) return;
        button.putClientProperty("archvow.view.button", Boolean.TRUE);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        updateViewButton(button);
    }

    static void updateViewButton(JToggleButton button) {
        Color text = UIManager.getColor("Label.foreground");
        if (text == null) text = dark() ? new Color(224, 228, 236) : new Color(43, 49, 61);
        button.setForeground(button.isEnabled() ? text : blend(text, button.getBackground(), .45));
        button.repaint();
    }

    /** The selected tab is more distinct than hover, matching the IDE editor-tab language. */
    private static void paintEditorTab(Graphics2D g, int x, int y, int width, int height,
            boolean selected, float hover) {
        if (width < 4 || height < 4) return;
        if (selected || hover > .01f) {
            Color base = dark() ? new Color(35, 37, 42) : new Color(250, 251, 253);
            Color fill = selected
                ? (dark() ? new Color(38, 54, 80) : new Color(227, 237, 253))
                : blend(base, accent(), (dark() ? .12 : .07) * hover);
            g.setColor(fill);
            g.fill(new RoundRectangle2D.Float(x, y, width, height, 10, 10));
        }
        // An unselected tab still has a light rounded outline: its label and icon
        // remain untouched, and selection keeps the exact existing highlighted style.
        Color outline = selected
            ? (dark() ? new Color(70, 113, 184) : new Color(124, 160, 221))
            : (dark() ? new Color(91, 97, 108) : new Color(197, 204, 215));
        if (!selected && hover > 0f) outline = blend(outline, accent(), .30 * hover);
        g.setColor(outline);
        g.setStroke(new BasicStroke(selected ? 1.3f : 1.0f));
        g.draw(new RoundRectangle2D.Float(x + .65f, y + .65f,
            width - 1.3f, height - 1.3f, 10, 10));
    }

    /**
     * Install the same quiet hover behavior on ordinary action buttons, overflow actions,
     * badges and dynamically created forms. The native Look & Feel owns their idle style;
     * a short animation supplies a tinted background and a thin temporary outline.
     * Existing borders/insets, selection, enabled state and click listeners are preserved.
     */
    static void installButtons(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton button) installButton(button);
            if (child instanceof Container container) installButtons(container);
        }
    }

    static void installButton(JButton button) {
        if (Boolean.TRUE.equals(button.getClientProperty("archvow.button.hoverInstalled"))) return;
        button.putClientProperty("archvow.button.hoverInstalled", Boolean.TRUE);
        button.addMouseListener(new MouseAdapter() {
            private Timer timer;
            private float level;
            private boolean inside;
            private Color idleBackground;
            private Border idleBorder;

            @Override public void mouseEntered(MouseEvent event) {
                if (!button.isEnabled()) return;
                idleBackground = button.getBackground();
                idleBorder = button.getBorder();
                inside = true;
                button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                animate();
            }
            @Override public void mouseExited(MouseEvent event) {
                inside = false;
                button.setCursor(Cursor.getDefaultCursor());
                animate();
            }
            private void animate() {
                if (timer == null) {
                    timer = new Timer(FRAME_MS, e -> {
                        float target = inside && button.isEnabled() ? 1f : 0f;
                        level += (target - level) * .36f;
                        if (Math.abs(target - level) < .035f) level = target;
                        if (idleBackground != null) {
                            // Restore the original UIResource on exit so a live IDE theme
                            // switch still updates this native button correctly.
                            button.setBackground(level <= 0f ? idleBackground
                                : blend(idleBackground, accent(), (dark() ? .18 : .13) * level));
                        }
                        if (idleBorder != null) {
                            if (level > .01f) button.setBorder(new HoverOutlineBorder(idleBorder, level));
                            else button.setBorder(idleBorder);
                        }
                        button.repaint();
                        if (level == target) timer.stop();
                    });
                    timer.setRepeats(true);
                }
                if (!timer.isRunning()) timer.start();
            }
        });
    }

    /** Draws a hover outline without changing the insets or permanent native border. */
    private static final class HoverOutlineBorder extends AbstractBorder {
        private final Border original;
        private final float hover;
        HoverOutlineBorder(Border original, float hover) {
            this.original = original;
            this.hover = hover;
        }
        @Override public Insets getBorderInsets(Component c) { return original.getBorderInsets(c); }
        @Override public Insets getBorderInsets(Component c, Insets insets) {
            Insets source = original.getBorderInsets(c);
            insets.top = source.top; insets.left = source.left;
            insets.bottom = source.bottom; insets.right = source.right;
            return insets;
        }
        @Override public boolean isBorderOpaque() { return original.isBorderOpaque(); }
        @Override public void paintBorder(Component c, Graphics graphics, int x, int y, int width, int height) {
            original.paintBorder(c, graphics, x, y, width, height);
            if (width < 6 || height < 6) return;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color blue = accent();
                g.setColor(new Color(blue.getRed(), blue.getGreen(), blue.getBlue(),
                    Math.max(0, Math.min(255, Math.round(140 * hover)))));
                g.setStroke(new BasicStroke(1.2f));
                g.draw(new RoundRectangle2D.Float(x + 1.5f, y + 1.5f,
                    width - 3f, height - 3f, 8f, 8f));
            } finally { g.dispose(); }
        }
    }

    private static final class HoverTracker extends MouseAdapter {
        private final JComponent component;
        private int currentIndex = -1;
        private int previousIndex = -1;
        private float amount;
        private float previousAmount;
        private Timer timer;

        HoverTracker(JComponent component) { this.component = component; }

        void install() {
            component.addMouseMotionListener(this);
            component.addMouseListener(this);
            component.putClientProperty(HOVER, -1);
            component.putClientProperty(HOVER_AMOUNT, 0f);
            component.putClientProperty(HOVER_PREVIOUS, -1);
            component.putClientProperty(HOVER_PREVIOUS_AMOUNT, 0f);
        }

        @Override public void mouseMoved(MouseEvent event) {
            int index = -1;
            Point point = event.getPoint();
            if (component instanceof JList<?> list) {
                int found = list.locationToIndex(point);
                if (found >= 0 && list.getCellBounds(found, found) != null &&
                        list.getCellBounds(found, found).contains(point)) index = found;
            } else if (component instanceof JTree tree) {
                index = tree.getRowForLocation(point.x, point.y);
            }
            if (index != currentIndex) {
                previousIndex = currentIndex;
                previousAmount = amount;
                currentIndex = index;
                amount = 0f;
                component.putClientProperty(HOVER, index);
                component.putClientProperty(HOVER_PREVIOUS, previousIndex);
                component.putClientProperty(HOVER_PREVIOUS_AMOUNT, previousAmount);
                animate();
            }
            component.setCursor(index < 0 ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override public void mouseExited(MouseEvent event) {
            previousIndex = currentIndex;
            previousAmount = amount;
            currentIndex = -1;
            amount = 0f;
            component.putClientProperty(HOVER, -1);
            component.putClientProperty(HOVER_AMOUNT, 0f);
            component.putClientProperty(HOVER_PREVIOUS, previousIndex);
            component.putClientProperty(HOVER_PREVIOUS_AMOUNT, previousAmount);
            animate();
            component.setCursor(Cursor.getDefaultCursor());
            component.repaint();
        }

        private void animate() {
            if (timer == null) {
                timer = new Timer(FRAME_MS, e -> {
                    if (currentIndex >= 0) amount += (1f - amount) * .27f;
                    if (amount > .97f) amount = 1f;
                    previousAmount *= .73f;
                    if (previousAmount < .02f) {
                        previousAmount = 0f;
                        previousIndex = -1;
                    }
                    component.putClientProperty(HOVER_AMOUNT, amount);
                    component.putClientProperty(HOVER_PREVIOUS, previousIndex);
                    component.putClientProperty(HOVER_PREVIOUS_AMOUNT, previousAmount);
                    component.repaint();
                    if ((currentIndex < 0 || amount == 1f) && previousIndex < 0) timer.stop();
                });
            }
            if (!timer.isRunning()) timer.start();
        }
    }

    private static final class MotionTabsUI extends BasicTabbedPaneUI {
        private int hovered = -1;
        private float[] hoverLevels = new float[0];
        private int lastSelected = -1;
        private float selectionProgress = 1f;
        private Timer timer;
        private MouseMotionAdapter motion;
        private MouseAdapter mouse;
        private ChangeListener change;
        private final List<Component> secondaryTargets = new ArrayList<>();

        @Override protected void installListeners() {
            super.installListeners();
            hoverLevels = new float[Math.max(0, tabPane.getTabCount())];
            lastSelected = tabPane.getSelectedIndex();
            motion = new MouseMotionAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    Point point = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), tabPane);
                    int tab = tabPane.indexAtLocation(point.x, point.y);
                    if (tab != hovered) {
                        hovered = tab;
                        ensureTimer();
                    }
                    tabPane.setCursor(tab < 0 ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }
            };
            mouse = new MouseAdapter() {
                @Override public void mouseExited(MouseEvent e) {
                    hovered = -1;
                    tabPane.setCursor(Cursor.getDefaultCursor());
                    ensureTimer();
                }
            };
            change = e -> {
                int selected = tabPane.getSelectedIndex();
                if (selected != lastSelected) {
                    lastSelected = selected;
                    selectionProgress = 0f;
                    ensureTimer();
                }
            };
            tabPane.addMouseMotionListener(motion);
            tabPane.addMouseListener(mouse);
            tabPane.addChangeListener(change);
            // With SCROLL_TAB_LAYOUT Swing dispatches mouse events to an inner
            // viewport instead of JTabbedPane. Observe just those tab-strip children.
            for (Component child : tabPane.getComponents()) {
                if (child instanceof JViewport viewport) {
                    trackTabStrip(viewport);
                    if (viewport.getView() != null) trackTabStrip(viewport.getView());
                }
            }
        }

        private void trackTabStrip(Component child) {
            child.addMouseMotionListener(motion);
            child.addMouseListener(mouse);
            secondaryTargets.add(child);
        }

        @Override protected void uninstallListeners() {
            if (timer != null) timer.stop();
            for (Component child : secondaryTargets) {
                child.removeMouseMotionListener(motion);
                child.removeMouseListener(mouse);
            }
            secondaryTargets.clear();
            if (motion != null) tabPane.removeMouseMotionListener(motion);
            if (mouse != null) tabPane.removeMouseListener(mouse);
            if (change != null) tabPane.removeChangeListener(change);
            super.uninstallListeners();
        }

        private void ensureTimer() {
            if (timer == null) {
                timer = new Timer(FRAME_MS, e -> {
                    if (hoverLevels.length != tabPane.getTabCount())
                        hoverLevels = new float[tabPane.getTabCount()];
                    boolean moving = false;
                    for (int i = 0; i < hoverLevels.length; i++) {
                        float target = i == hovered ? 1f : 0f;
                        hoverLevels[i] += (target - hoverLevels[i]) * .28f;
                        if (Math.abs(target - hoverLevels[i]) < .02f) hoverLevels[i] = target;
                        if (hoverLevels[i] != target) moving = true;
                    }
                    selectionProgress = Math.min(1f, selectionProgress + .14f);
                    if (selectionProgress < 1f) moving = true;
                    tabPane.repaint();
                    if (!moving) timer.stop();
                });
            }
            if (!timer.isRunning()) timer.start();
        }

        @Override protected void paintTabBackground(Graphics graphics, int placement, int index,
                int x, int y, int width, int height, boolean selected) {
            if (hoverLevels.length != tabPane.getTabCount())
                hoverLevels = new float[tabPane.getTabCount()];
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                float hover = index < hoverLevels.length ? hoverLevels[index] : 0f;
                paintEditorTab(g, x+3, y+2, Math.max(0,width-6), Math.max(0,height-4),
                    selected, hover);
            } finally { g.dispose(); }
        }

        @Override protected void paintTabBorder(Graphics graphics, int placement, int index,
                int x, int y, int width, int height, boolean selected) {
            // The selection has its own fine outline: no secondary underline.
        }

        @Override protected void paintFocusIndicator(Graphics graphics, int placement,
                Rectangle[] rects, int tabIndex, Rectangle iconRect, Rectangle textRect,
                boolean isSelected) {
            // Focus is still visible via the selected tab, without a bright double outline.
        }

        @Override protected void paintText(Graphics g, int placement, Font font,
                FontMetrics metrics, int index, String title, Rectangle textRect, boolean selected) {
            g.setFont(font);
            Color text = UIManager.getColor("Label.foreground");
            if (text == null) text = dark() ? new Color(223, 229, 236) : new Color(45, 52, 64);
            g.setColor(tabPane.isEnabledAt(index) ? text : blend(text, tabPane.getBackground(), .55));
            g.drawString(title, textRect.x, textRect.y + metrics.getAscent());
        }

        @Override protected Insets getTabInsets(int placement, int index) {
            return new Insets(8, 15, 8, 15);
        }

        @Override protected void paintContentBorder(Graphics g, int placement, int selectedIndex) {
            // The editor-like navigation bar has no full-width border under every row.
        }

    }
}
