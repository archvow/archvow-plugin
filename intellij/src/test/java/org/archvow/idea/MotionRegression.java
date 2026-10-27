package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;

/** Headless regression: theme-aware hover is weaker than selection and tabs stay navigable. */
public final class MotionRegression {
    public static void main(String[] args) throws Exception {
        for (Color theme : new Color[] {new Color(245, 247, 250), new Color(38, 40, 47)}) {
            UIManager.put("Panel.background", theme);
            UIManager.put("Component.focusColor", new Color(65, 122, 224));
            JList<String>[] listHolder = new JList[1];
            JTabbedPane[] tabsHolder = new JTabbedPane[1];
            SwingUtilities.invokeAndWait(() -> {
                JTabbedPane tabs = new JTabbedPane();
                tabs.addTab("Architecture", new JPanel());
                tabs.addTab("Check & Gates", new JPanel());
                tabs.addTab("Quick", new JPanel());
                ArchvowMotion.installTabs(tabs);
                tabs.setSize(560, 330);
                tabs.doLayout();
                tabs.setSelectedIndex(2);
                BufferedImage image = new BufferedImage(560, 330, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = image.createGraphics();
                tabs.paint(graphics);
                graphics.dispose();
                if (tabs.getSelectedIndex() != 2) throw new AssertionError("Tab navigation lost selected index");
                tabsHolder[0] = tabs;

                JList<String> list = new JList<>(new String[] {"Pending", "Resolved"});
                list.setBackground(theme);
                list.setFixedCellHeight(36);
                list.setSize(240, 85);
                list.setCellRenderer(new ArchvowMotion.CommandRenderer());
                list.setSelectedIndex(1);
                ArchvowMotion.installList(list);
                MouseEvent move = new MouseEvent(list, MouseEvent.MOUSE_MOVED,
                    System.currentTimeMillis(), 0, 12, 14, 0, false);
                for (MouseMotionListener listener : list.getMouseMotionListeners()) listener.mouseMoved(move);
                listHolder[0] = list;
            });
            Thread.sleep(300);
            SwingUtilities.invokeAndWait(() -> {
                JList<String> list = listHolder[0];
                Color hovered = ((JLabel) list.getCellRenderer().getListCellRendererComponent(
                    list, "Pending", 0, false, false)).getBackground();
                Color selected = ((JLabel) list.getCellRenderer().getListCellRendererComponent(
                    list, "Resolved", 1, true, false)).getBackground();
                double hoverDistance = dist(hovered, theme);
                double selectedDistance = dist(selected, theme);
                if (hoverDistance < 5 || selectedDistance <= hoverDistance)
                    throw new AssertionError("Selection should be visually stronger than hover: " + selectedDistance + " <= " + hoverDistance);
                if (tabsHolder[0].getTabCount() != 3) throw new AssertionError("Unexpected tab count");
            });
        }
        System.out.println("MotionRegression passed for light and dark themes");
    }

    private static double dist(Color a, Color b) {
        int dr = a.getRed() - b.getRed();
        int dg = a.getGreen() - b.getGreen();
        int db = a.getBlue() - b.getBlue();
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }
}
