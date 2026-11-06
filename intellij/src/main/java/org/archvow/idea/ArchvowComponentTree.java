package org.archvow.idea;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;
import java.util.function.Function;

/**
 * 0.17.4: the Components tree as VS Code's - a component's name in the text colour and its type muted (the
 * renderer, ArchvowMotion.installTree), and, painted over the rows: a thin bar in the status colour left of the
 * component (orange: something to do, green: done), on the right its count in a grey pill or a green ✓, and a ⊕
 * on each layer (a click opens the layer's Add menu). Before, the whole row was orange or green with "(2)" or "✓"
 * after the type. The counts are the tree's client properties archvow.pendingCounts / archvow.resolvedCounts
 * (component id -> number), as before. Plain Swing: painted headless (ComponentTreeRegression).
 */
final class ArchvowComponentTree extends JTree {
    static final String PENDING = "archvow.pendingCounts";
    static final String RESOLVED = "archvow.resolvedCounts";
    /** The ⊕ zone at the right edge of a layer row, and the margin on the right of the badges. */
    static final int ADD_ZONE = 26, RIGHT_MARGIN = 8;

    /** The ⊕'s tooltip for a layer (in the IDE's language), set by the panel. */
    Function<String, String> addTip = layer -> "Add a component to " + layer;

    ArchvowComponentTree(TreeNode root) { super(root); }

    /** A component's id, or "" for a row that is not a component (the root, a container, a layer, an empty line). */
    static String componentId(Object node) {
        Object user = node instanceof DefaultMutableTreeNode n ? n.getUserObject() : null;
        if (user == null || user instanceof ArchvowTreeIcons.Node || !(user instanceof ArchvowTreeIcons.Kinded)) return "";
        String text = user.toString();
        int dot = text.indexOf("  ·  ");
        return dot < 0 ? text : text.substring(0, dot);
    }

    /** The layer a row is, or "" (an empty line under a layer is not the layer: it has no ⊕). */
    static String layerOf(Object node) {
        Object user = node instanceof DefaultMutableTreeNode n ? n.getUserObject() : null;
        return user instanceof ArchvowTreeIcons.Node n && "layer".equals(n.kind()) ? n.layer() : "";
    }

    int pending(String id) { return count(PENDING, id); }
    int resolved(String id) { return count(RESOLVED, id); }

    private int count(String property, String id) {
        if (id == null || id.isEmpty()) return 0;
        if (getClientProperty(property) instanceof Map<?, ?> map && map.get(id) instanceof Number n) return n.intValue();
        return 0;
    }

    /** The layer whose ⊕ is under the point, or "". */
    String addLayerAt(Point p) {
        int row = getClosestRowForLocation(p.x, p.y);
        if (row < 0) return "";
        Rectangle bounds = getRowBounds(row);
        if (bounds == null || p.y < bounds.y || p.y >= bounds.y + bounds.height) return "";
        Rectangle visible = getVisibleRect();
        int right = visible.x + visible.width - RIGHT_MARGIN;
        if (p.x < right - ADD_ZONE + 4 || p.x > right + 4) return "";
        TreePath path = getPathForRow(row);
        return path == null ? "" : layerOf(path.getLastPathComponent());
    }

    @Override public String getToolTipText(MouseEvent event) {
        String layer = addLayerAt(event.getPoint());
        return layer.isEmpty() ? super.getToolTipText(event) : addTip.apply(layer);
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Rectangle clip = g.getClipBounds() == null ? getVisibleRect() : g.getClipBounds();
            Rectangle visible = getVisibleRect();
            int right = visible.x + visible.width - RIGHT_MARGIN;
            int first = Math.max(0, getClosestRowForLocation(clip.x, clip.y));
            int last = getClosestRowForLocation(clip.x, clip.y + clip.height);
            for (int row = first; row <= last && row < getRowCount(); row++) {
                Rectangle bounds = getRowBounds(row);
                TreePath path = getPathForRow(row);
                if (bounds == null || path == null) continue;
                Object node = path.getLastPathComponent();
                String id = componentId(node);
                if (!id.isEmpty()) {
                    int pending = pending(id), resolved = resolved(id);
                    Color status = pending > 0 ? pendingColor() : resolved > 0 ? doneColor() : null;
                    if (status != null) {
                        g2.setColor(status);
                        g2.fill(new RoundRectangle2D.Float(Math.max(0, bounds.x - 7), bounds.y + 2, 3, bounds.height - 4, 2, 2));
                    }
                    if (pending > 0) paintPill(g2, String.valueOf(pending), right, bounds);
                    else if (resolved > 0) paintCheck(g2, right, bounds);
                    // State is independent of issue count: planned may have resolved decisions,
                    // and verified may still have an open question. Draw both independently.
                    Object stateMap = getClientProperty("archvow.componentStatuses");
                    if (stateMap instanceof Map<?, ?> states && states.get(id) instanceof String state && !state.isBlank()) {
                        boolean good = state.equalsIgnoreCase("verified") || state.equalsIgnoreCase("implemented") || state.equalsIgnoreCase("tested");
                        g2.setColor(good ? doneColor() : pendingColor());
                        Font font = getFont().deriveFont(Math.max(10f, getFont().getSize2D() - 2f));
                        g2.setFont(font);
                        FontMetrics fm = g2.getFontMetrics(font);
                        int reserved = pending > 0 || resolved > 0 ? 40 : 0;
                        int x = right - reserved - fm.stringWidth(state) - 10;
                        if (x > bounds.x + Math.max(125, bounds.width / 3))
                            g2.drawString(state, x, bounds.y + (bounds.height - fm.getHeight()) / 2 + fm.getAscent());
                    }
                } else if (!layerOf(node).isEmpty()) paintAdd(g2, right, bounds);
            }
        } finally {
            g2.dispose();
        }
    }

    private void paintPill(Graphics2D g2, String text, int right, Rectangle row) {
        Font font = getFont().deriveFont(Math.max(10f, getFont().getSize2D() - 2f));
        FontMetrics fm = g2.getFontMetrics(font);
        int h = Math.min(row.height - 4, fm.getHeight() + 2), w = Math.max(h + 4, fm.stringWidth(text) + 12);
        int x = right - w, y = row.y + (row.height - h) / 2;
        g2.setColor(ArchvowMotion.blend(getBackground(), getForeground(), ArchvowMotion.dark() ? .16 : .10));
        g2.fill(new RoundRectangle2D.Float(x, y, w, h, h, h));
        g2.setColor(muted());
        g2.setFont(font);
        g2.drawString(text, x + (w - fm.stringWidth(text)) / 2, y + (h - fm.getHeight()) / 2 + fm.getAscent());
    }

    private void paintCheck(Graphics2D g2, int right, Rectangle row) {
        int s = 12, x = right - s - 3, y = row.y + (row.height - s) / 2;
        Path2D check = new Path2D.Float();
        check.moveTo(x + 1, y + s * .55); check.lineTo(x + s * .4, y + s - 1.5); check.lineTo(x + s, y + 1.5);
        g2.setColor(doneColor());
        g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(check);
    }

    private void paintAdd(Graphics2D g2, int right, Rectangle row) {
        int s = 15, x = right - s - 1, y = row.y + (row.height - s) / 2;
        g2.setColor(muted());
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(new Ellipse2D.Float(x, y, s, s));
        float c = s / 2f;
        g2.draw(new java.awt.geom.Line2D.Float(x + c, y + 4, x + c, y + s - 4));
        g2.draw(new java.awt.geom.Line2D.Float(x + 4, y + c, x + s - 4, y + c));
    }

    Color muted() { return ArchvowMotion.blend(getBackground(), getForeground(), .55); }
    static Color pendingColor() { return ArchvowMotion.dark() ? new Color(232, 145, 90) : new Color(196, 100, 40); }
    static Color doneColor() { return ArchvowMotion.dark() ? new Color(137, 209, 133) : new Color(30, 130, 70); }
}
