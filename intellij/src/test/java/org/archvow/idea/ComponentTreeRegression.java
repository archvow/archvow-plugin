package org.archvow.idea;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;

/**
 * 0.17.4: the Components tree as VS Code's - the name and its muted type (no "(2)" nor "✓" in the text), a bar in
 * the status colour left of a component, its count in a pill or a ✓ at the right edge, a ⊕ on each layer that
 * answers a click. Painted headless in dark colours (pass a folder to keep the picture).
 */
public final class ComponentTreeRegression {
    /** A component node, as the panel's NodeComponent: "id  ·  type", drawn as its type. */
    private record Comp(String id, String type) implements ArchvowTreeIcons.Kinded {
        @Override public String toString() { return id + "  ·  " + type; }
        @Override public String iconKind() { return ArchvowTreeIcons.typeKind(type); }
    }

    public static void main(String[] args) throws Exception {
        Color bg = new Color(43, 45, 48), fg = new Color(223, 225, 229);
        UIManager.put("Panel.background", bg);
        UIManager.put("Tree.textForeground", fg); UIManager.put("Tree.textBackground", bg); UIManager.put("Tree.background", bg);
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(new ArchvowTreeIcons.Node("sales_pipeline", "system", ""));
        DefaultMutableTreeNode p0 = new DefaultMutableTreeNode(new ArchvowTreeIcons.Node("p0_common", "layer", "p0_common"));
        DefaultMutableTreeNode p1 = new DefaultMutableTreeNode(new ArchvowTreeIcons.Node("p1_domain", "layer", "p1_domain"));
        root.add(p0); root.add(p1);
        p0.add(new DefaultMutableTreeNode(new Comp("config", "foundation")));
        p0.add(new DefaultMutableTreeNode(new Comp("logging_setup", "foundation")));
        p0.add(new DefaultMutableTreeNode(new Comp("spark", "foundation")));
        p1.add(new DefaultMutableTreeNode(new ArchvowTreeIcons.Node("Pas encore de composant", "add", "p1_domain")));
        ArchvowComponentTree tree = new ArchvowComponentTree(root);
        tree.setBackground(bg); tree.setForeground(fg);
        tree.setRowHeight(26);
        tree.putClientProperty(ArchvowComponentTree.PENDING, Map.of("config", 2, "spark", 1));
        tree.putClientProperty(ArchvowComponentTree.RESOLVED, Map.of("logging_setup", 3, "config", 1));
        ArchvowMotion.installTree(tree);
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
        int width = 460;
        tree.setSize(width, tree.getRowCount() * 26);
        tree.doLayout();

        // the text: the name and its type, no count nor ✓
        check(tree.getRowCount() == 7, "rows: " + tree.getRowCount());
        String config = text(tree, 2), done = text(tree, 3), empty = text(tree, 6);
        if (!config.contains("config") || !config.contains("· foundation") || config.contains("(2)") || config.contains("✓"))
            throw new AssertionError("config's text: " + config);
        if (done.contains("✓")) throw new AssertionError("✓ is painted at the right, not in the text: " + done);
        check(ArchvowComponentTree.componentId(tree.getPathForRow(2).getLastPathComponent()).equals("config"), "config's id");
        check(ArchvowComponentTree.componentId(tree.getPathForRow(1).getLastPathComponent()).isEmpty(), "a layer is no component");
        Component emptyCell = cell(tree, 6);
        check(emptyCell instanceof JLabel l && l.getIcon() == null && l.getFont().isItalic() && empty.contains("Pas encore"),
            "the empty line: italic, no drawing");

        // painted: the bar, the pill, the ✓, the ⊕
        BufferedImage image = new BufferedImage(width, tree.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(bg); g.fillRect(0, 0, width, tree.getHeight());
        tree.paint(g);
        g.dispose();
        if (args.length > 0) javax.imageio.ImageIO.write(image, "png", new File(args[0], "component-tree.png"));
        Rectangle configRow = tree.getRowBounds(2), doneRow = tree.getRowBounds(3), layerRow = tree.getRowBounds(1);
        int mid = configRow.y + configRow.height / 2;
        check(near(new Color(image.getRGB(configRow.x - 6, mid)), ArchvowComponentTree.pendingColor()), "config's bar is orange");
        check(near(new Color(image.getRGB(doneRow.x - 6, doneRow.y + doneRow.height / 2)), ArchvowComponentTree.doneColor()), "logging_setup's bar is green");
        check(anyNear(image, width - 40, width - 8, configRow, ArchvowMotion.blend(bg, fg, .16)), "config's count is in a pill at the right");
        check(anyNear(image, width - 30, width - 8, doneRow, ArchvowComponentTree.doneColor()), "logging_setup's ✓ at the right");
        check(!anyNear(image, width - 40, width - 8, layerRow, ArchvowComponentTree.pendingColor()), "no status on a layer");
        check(anyNear(image, width - 30, width - 8, layerRow, tree.muted()), "the layer's ⊕ at the right");

        // the ⊕ answers: on a layer, at the right edge only
        int layerMid = layerRow.y + layerRow.height / 2;
        check(tree.addLayerAt(new Point(width - 16, layerMid)).equals("p0_common"), "⊕ of p0_common");
        check(tree.addLayerAt(new Point(layerRow.x + 5, layerMid)).isEmpty(), "the layer's name is not its ⊕");
        check(tree.addLayerAt(new Point(width - 16, mid)).isEmpty(), "a component has no ⊕");
        check(tree.addLayerAt(new Point(width - 16, tree.getRowBounds(6).y + 10)).isEmpty(), "the empty line has no ⊕");
        System.out.println("OK: the component tree as VS Code's - name and muted type, bar, pill, ✓, ⊕");
    }

    private static Component cell(JTree tree, int row) {
        return tree.getCellRenderer().getTreeCellRendererComponent(tree, tree.getPathForRow(row).getLastPathComponent(),
            false, true, tree.getModel().isLeaf(tree.getPathForRow(row).getLastPathComponent()), row, false);
    }

    private static String text(JTree tree, int row) {
        return ((DefaultTreeCellRenderer) cell(tree, row)).getText();
    }

    private static boolean anyNear(BufferedImage image, int x0, int x1, Rectangle row, Color wanted) {
        for (int x = Math.max(0, x0); x < Math.min(image.getWidth(), x1); x++)
            for (int y = row.y + 2; y < row.y + row.height - 2; y++)
                if (near(new Color(image.getRGB(x, y)), wanted)) return true;
        return false;
    }

    private static boolean near(Color a, Color b) {
        return Math.abs(a.getRed() - b.getRed()) + Math.abs(a.getGreen() - b.getGreen()) + Math.abs(a.getBlue() - b.getBlue()) < 30;
    }

    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
