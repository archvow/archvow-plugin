package org.archvow.idea;

import javax.swing.JEditorPane;
import javax.swing.UIManager;
import javax.swing.text.View;
import javax.swing.text.html.HTML;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;

/**
 * Paint the result's rounded cards with Swing, not unsupported HTML CSS.
 * The JEditorPane still owns and renders all selectable text, links and file icons.
 * Only specifically marked result tables receive the decoration.
 */
final class ArchvowResultPane extends JEditorPane {
    ArchvowResultPane() {
        super("text/html", "");
        setOpaque(false);
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean dark = darkTheme();
            g.setColor(dark ? new Color(39, 43, 51) : new Color(249, 251, 254));
            g.fillRect(0, 0, getWidth(), getHeight());
            if (getUI() != null) {
                View root = getUI().getRootView(this);
                java.awt.Insets insets = getInsets();
                int w = Math.max(1, getWidth() - insets.left - insets.right);
                int h = Math.max(1, getHeight() - insets.top - insets.bottom);
                root.setSize(w, h);
                paintResultSurfaces(g, root,
                    new Rectangle(insets.left, insets.top, w, h), dark, false);
            }
        } finally {
            g.dispose();
        }
        super.paintComponent(graphics);
    }

    private static boolean darkTheme() {
        Color panel = UIManager.getColor("Panel.background");
        return panel != null && (panel.getRed() * .299 + panel.getGreen() * .587 + panel.getBlue() * .114) < 128;
    }

    private static void paintResultSurfaces(Graphics2D g, View view, Shape shape, boolean dark,
                                            boolean insideMarkedCard) {
        if (view == null || shape == null) return;
        boolean enteredCard = insideMarkedCard;
        if (view.getElement() != null && "table".equals(view.getElement().getName())) {
            Object id = view.getElement().getAttributes().getAttribute(HTML.Attribute.ID);
            String type = id == null ? "" : id.toString();
            Rectangle area = shape.getBounds();
            if (!insideMarkedCard && (type.startsWith("av-heading-") || type.startsWith("av-card-")
                || "av-footer".equals(type))) {
                enteredCard = true;
                Color top = dark ? new Color(50, 55, 64) : new Color(247, 249, 252);
                Color bottom = dark ? new Color(55, 61, 70) : new Color(239, 244, 249);
                if ("av-footer".equals(type)) {
                    top = dark ? new Color(44, 49, 58) : new Color(244, 247, 251);
                    bottom = dark ? new Color(44, 49, 58) : new Color(244, 247, 251);
                }
                int x = area.x + 1, y = area.y + 1;
                int w = Math.max(0, area.width - 3), h = Math.max(0, area.height - 3);
                if (w > 0 && h > 0) {
                    g.setPaint(new GradientPaint(x, y, top, x + w, y + h, bottom));
                    g.fillRoundRect(x, y, w, h, 10, 10);
                    g.setColor(dark ? new Color(73, 80, 92) : new Color(214, 223, 233));
                    g.drawRoundRect(x, y, w, h, 10, 10);
                    if (!"av-footer".equals(type)) {
                        Color accent = accent(type.substring(type.lastIndexOf('-') + 1), dark);
                        g.setColor(accent);
                        g.fillRoundRect(x + 1, y + 1, 5, Math.max(0, h - 1), 5, 5);
                    }
                }
            }
        }
        if (view.getElement() != null && "td".equals(view.getElement().getName())) {
            Object id = view.getElement().getAttributes().getAttribute(HTML.Attribute.ID);
            if ("av-chip".equals(id)) {
                Rectangle area = shape.getBounds();
                g.setColor(dark ? new Color(55, 61, 71) : new Color(239, 243, 248));
                g.fillRoundRect(area.x + 1, area.y + 1, Math.max(0, area.width - 2),
                    Math.max(0, area.height - 2), 9, 9);
            }
        }
        for (int i = 0; i < view.getViewCount(); i++) {
            Shape child = view.getChildAllocation(i, shape);
            if (child != null) paintResultSurfaces(g, view.getView(i), child, dark, enteredCard);
        }
    }

    private static Color accent(String kind, boolean dark) {
        return switch (kind) {
            case "good" -> dark ? new Color(115, 221, 170) : new Color(25, 138, 85);
            case "warn" -> dark ? new Color(247, 199, 91) : new Color(185, 119, 30);
            case "bad" -> dark ? new Color(248, 133, 149) : new Color(190, 45, 73);
            default -> dark ? new Color(124, 177, 248) : new Color(44, 104, 179);
        };
    }
}
