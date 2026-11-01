package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Small theme-aware line icons for existing Swing actions. No external image files,
 * font glyphs or icon-library dependencies. Button labels remain visible.
 */
final class ArchvowUiIcons {
    private ArchvowUiIcons() { }

    /** Repeated calls are safe; useful after a language or look-and-feel switch. */
    static void decorateTree(Component root) {
        if (root instanceof AbstractButton button) decorate(button);
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) decorateTree(child);
        }
    }

    static void decorate(AbstractButton button) {
        if (button == null) return;
        if (button instanceof JButton pushButton) ArchvowMotion.installButton(pushButton);
        String kind = kindForLabel(button.getText());
        if (kind == null) return; // Not every text needs a decorative pictogram.
        Object custom = button.getClientProperty("archvow.icon.kind");
        if (kind.equals(custom) && button.getIcon() instanceof LineIcon) return;
        // Do not replace logos or icons intentionally assigned by other components.
        if (button.getIcon() != null && !(button.getIcon() instanceof LineIcon)) return;
        button.setIcon(new LineIcon(button, kind));
        button.setIconTextGap(6);
        button.putClientProperty("archvow.icon.kind", kind);
    }

    static void explicit(AbstractButton button, String kind) {
        if (button instanceof JButton pushButton) ArchvowMotion.installButton(pushButton);
        button.setIcon(new LineIcon(button, kind));
        button.setIconTextGap(6);
        button.putClientProperty("archvow.icon.kind", kind);
    }

    static String kindForLabel(String label) {
        if (label == null || label.isBlank() || label.equals("×")) return null;
        String key = Normalizer.normalize(label.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").trim();
        if (key.contains("raw output") || key.contains("sortie brute") || key.contains("full vow") || key.contains("vow du projet") || key.contains("project vow")) return "document";
        if (key.contains("copy") || key.contains("copier")) return "copy";
        if (key.contains("refresh") || key.contains("recharger") || key.contains("retry") || key.contains("reessayer") || key.contains("actualiser")) return "refresh";
        if (key.contains("check") || key.contains("controle") || key.contains("validation") || key.contains("verifier")) return "check";
        if (key.contains("doctor")) return "health";
        if (key.contains("audit")) return "search";
        if (key.contains("next") || key.contains("suivant") || key.contains("suite")) return "arrow";
        if (key.contains("diff") || key.contains("review") || key.contains("revue")) return "compare";
        if (key.contains("open in") || key.contains("ouvrir dans") || key.contains("panneau lateral")) return "layout";
        if (key.contains("save") || key.contains("enregistrer") || key.contains("accept") || key.contains("accepter") || key.contains("confirm") || key.contains("confirmer")) return "check";
        if (key.contains("decision") || key.contains("question") || key.contains("hypothese") || key.contains("assumption")) return "list";
        if (key.contains("pending") || key.contains("en attente")) return "shield";
        if (key.contains("resolved") || key.contains("traite")) return "check";
        if (key.contains("settings") || key.contains("parametres") || key.contains("configurer")) return "settings";
        if (key.contains("ouvrir la spec") || key.contains("open spec") || key.contains("vow")) return "document";
        if (key.contains("open code") || key.contains("ouvrir le code")) return "code";
        if (key.contains("modifier") || key.contains("edit") || key.contains("repondre") || key.contains("answer") || key.contains("clarif")) return "edit";
        if (key.contains("ajouter") || key.contains("add ") || key.contains("creer") || key.contains("create") || key.contains("initialize") || key.contains("initialiser")) return "plus";
        if (key.contains("remove") || key.contains("supprimer") || key.contains("reject") || key.contains("rejeter")) return "minus";
        if (key.contains("undo") || key.contains("annuler les modifications")) return "undo";
        if (key.contains("expand") || key.contains("agrandir") || key.contains("reduce") || key.contains("reduire")) return "expand";
        if (key.contains("annuler") || key.contains("cancel")) return "close";
        if (key.contains("prompt") || key.contains("instruction ia") || key.contains("suggestion") || key.contains("propos")) return "spark";
        if (key.contains("invariant") || key.contains("safety") || key.contains("securite") || key.contains("hazard") || key.contains("danger")) return "shield";
        if (key.contains("branche") || key.contains("branch") || key.contains("base:")) return "branch";
        if (key.contains("browse") || key.contains("parcourir") || key.contains("dossier")) return "folder";
        if (key.contains("logs") || key.contains("journal")) return "list";
        if (key.contains("overview") || key.contains("vue d'ensemble") || key.contains("architecture")) return "layout";
        if (key.contains("composants") || key.contains("components") || key.contains("component")) return "boxes";
        if (key.contains("commandes") || key.contains("commands") || key.contains("executer") || key.contains("run command")) return "terminal";
        if (key.contains("more") || key.contains("plus") || key.contains("autres actions")) return "dots";
        if (key.contains("rules") || key.contains("regles") || key.contains("tasks") || key.contains("taches")) return "list";
        return null;
    }

    static Icon iconFor(Component component, String kind) { return new LineIcon(component, kind); }

    static final class LineIcon implements Icon {
        private final Component owner;
        private final String kind;
        LineIcon(Component owner, String kind) { this.owner = owner; this.kind = kind; }
        @Override public int getIconWidth() { return 14; }
        @Override public int getIconHeight() { return 14; }
        @Override public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.translate(x, y);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color ink = owner.isEnabled() ? owner.getForeground() : UIManager.getColor("Label.disabledForeground");
                if (ink == null) ink = owner.getForeground();
                if (ink == null) ink = Color.GRAY;
                g.setColor(ink);
                g.setStroke(new BasicStroke(1.55f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                switch (kind) {
                    case "refresh" -> {
                        // a circle turning clockwise: open at the top right, its arrowhead at the top (0.11.3;
                        // the former one read as a "C"). The same drawing as the VS Code page's REFRESH icon.
                        g.draw(new java.awt.geom.Arc2D.Double(2, 2, 10, 10, 20, -300, java.awt.geom.Arc2D.OPEN));
                        Path2D head = new Path2D.Double();
                        head.moveTo(10.23, 2.50);
                        head.lineTo(7.63, 0.01);
                        head.lineTo(6.93, 3.95);
                        head.closePath();
                        g.fill(head);
                    }
                    case "check" -> { circle(g, 1.8, 1.8, 10.3); line(g, 4.5, 7, 6.6, 9); line(g, 6.6, 9, 10, 5.2); }
                    case "arrow" -> { line(g, 2, 7, 11, 7); line(g, 8, 4, 11, 7); line(g, 8, 10, 11, 7); }
                    case "document" -> { g.drawRoundRect(2, 1, 10, 12, 1, 1); line(g, 4, 5, 10, 5); line(g, 4, 8, 10, 8); }
                    case "folder" -> { g.drawRoundRect(1, 4, 12, 8, 2, 2); line(g, 2, 4, 2, 2); line(g, 2, 2, 6, 2); line(g, 6, 2, 7.5, 4); }
                    case "edit" -> { line(g, 3, 11, 10.5, 3); line(g, 10.5, 3, 12, 4.5); line(g, 12, 4.5, 4.5, 12); line(g, 3, 11, 4.5, 12); }
                    case "plus" -> { circle(g, 1.8, 1.8, 10.3); line(g, 7, 4, 7, 10); line(g, 4, 7, 10, 7); }
                    case "minus" -> { circle(g, 1.8, 1.8, 10.3); line(g, 4, 7, 10, 7); }
                    case "close" -> { line(g, 3, 3, 11, 11); line(g, 11, 3, 3, 11); }
                    case "undo" -> {
                        // a curve going back to the left, its arrowhead at the start (0.12.1: Undo changes)
                        g.draw(new java.awt.geom.Arc2D.Double(3, 3, 9, 9, 160, -250, java.awt.geom.Arc2D.OPEN));
                        line(g, 3.3, 5.5, 2.5, 2.2); line(g, 3.3, 5.5, 6.4, 4.6);
                    }
                    case "expand" -> { line(g, 2, 5, 2, 2); line(g, 2, 2, 5, 2); line(g, 12, 9, 12, 12); line(g, 12, 12, 9, 12);
                        line(g, 2, 2, 6, 6); line(g, 12, 12, 8, 8); }
                    case "search" -> { circle(g, 2, 1.5, 8); line(g, 9, 9, 12, 12); }
                    case "terminal" -> { g.drawRoundRect(1, 2, 12, 10, 2, 2); line(g, 3, 5, 5.5, 7); line(g, 5.5, 7, 3, 9); line(g, 7.5, 9.5, 11, 9.5); }
                    case "copy" -> { g.drawRect(4, 1, 9, 9); line(g, 10, 12, 1, 12); line(g, 1, 12, 1, 4); line(g, 1, 4, 3, 4); }
                    case "code" -> { line(g, 5, 3, 2, 7); line(g, 2, 7, 5, 11); line(g, 9, 3, 12, 7); line(g, 12, 7, 9, 11); }
                    case "branch" -> { line(g, 4, 2, 4, 11); line(g, 4, 7, 10, 7); line(g, 10, 7, 10, 3); circle(g, 2.5, 0.8, 3); circle(g, 2.5, 9.7, 3); circle(g, 8.5, 0.8, 3); }
                    case "shield" -> { Path2D p = new Path2D.Float(); p.moveTo(7, 1); p.lineTo(12, 3); p.lineTo(11, 9); p.lineTo(7, 13); p.lineTo(3, 9); p.lineTo(2, 3); p.closePath(); g.draw(p); line(g, 5, 7, 6.7, 9); line(g, 6.7, 9, 9.5, 5.5); }
                    case "spark" -> { line(g, 7, 1, 7, 13); line(g, 1, 7, 13, 7); line(g, 3, 3, 11, 11); line(g, 11, 3, 3, 11); }
                    case "list" -> { for (int yy : new int[] {3,7,11}) { g.fill(new Ellipse2D.Float(2, yy-1, 1.6f, 1.6f)); line(g, 5, yy, 12, yy); } }
                    case "layout" -> { g.drawRoundRect(1, 1, 12, 12, 2, 2); line(g, 1, 4.5, 13, 4.5); line(g, 5, 4.5, 5, 13); }
                    case "boxes" -> { g.drawRect(1, 1, 5, 5); g.drawRect(8, 1, 5, 5); g.drawRect(1, 8, 5, 5); g.drawRect(8, 8, 5, 5); }
                    case "compare" -> { line(g, 1, 4, 12, 4); line(g, 9, 1, 12, 4); line(g, 9, 7, 12, 4); line(g, 13, 10, 2, 10); line(g, 5, 7, 2, 10); line(g, 5, 13, 2, 10); }
                    case "dots" -> { g.fillOval(2, 6, 2, 2); g.fillOval(6, 6, 2, 2); g.fillOval(10, 6, 2, 2); }
                    case "settings" -> { circle(g, 1, 1, 12); circle(g, 5.2, 5.2, 3.6); }
                    case "health" -> { line(g, 7, 2, 7, 12); line(g, 2, 7, 12, 7); }
                    default -> circle(g, 2, 2, 10);
                }
            } finally { g.dispose(); }
        }
        private static void circle(Graphics2D g, double x, double y, double d) {
            g.draw(new Ellipse2D.Double(x, y, d, d));
        }
        private static void line(Graphics2D g, double x1, double y1, double x2, double y2) {
            g.drawLine((int) Math.round(x1), (int) Math.round(y1),
                (int) Math.round(x2), (int) Math.round(y2));
        }
    }
}
