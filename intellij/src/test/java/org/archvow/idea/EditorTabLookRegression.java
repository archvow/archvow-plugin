package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

/** Checks the shared editor-like navigation style without requiring the IntelliJ SDK. */
public final class EditorTabLookRegression {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (Color surface : List.of(new Color(246, 248, 250), new Color(35, 37, 42))) {
                UIManager.put("Panel.background", surface);
                UIManager.put("Label.foreground", surface.getRed() < 125 ? Color.WHITE : Color.BLACK);
                // All top-level / overview / pending tab strips use the same UI delegate.
                for (int layer=0; layer<3; layer++) {
                    JTabbedPane tabs = new JTabbedPane();
                    tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
                    tabs.addTab("Overview", new JPanel());
                    tabs.addTab("Components", new JPanel());
                    tabs.addTab("Commands", new JPanel());
                    ArchvowMotion.installTabs(tabs);
                    if (!tabs.getUI().getClass().getSimpleName().equals("MotionTabsUI"))
                        throw new AssertionError("Wrong tab renderer");
                    tabs.setSize(510, 130);
                    for (int i=0; i<3; i++) {
                        tabs.setSelectedIndex(i);
                        BufferedImage image = new BufferedImage(510, 130, BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = image.createGraphics();
                        g.setColor(surface); g.fillRect(0, 0, 510, 130);
                        tabs.paint(g); g.dispose();
                        if (tabs.getSelectedIndex() != i) throw new AssertionError("Tab switch failed");
                    }
                }
                for (int i=0; i<6; i++) {
                    ArchvowMotion.EditorTabButton button = new ArchvowMotion.EditorTabButton("View " + i);
                    ArchvowMotion.installViewButton(button);
                    button.setSize(130, 38);
                    button.setSelected(true);
                    ArchvowMotion.updateViewButton(button);
                    Color active = pixel(button, surface, 9, 19);
                    button.setSelected(false);
                    ArchvowMotion.updateViewButton(button);
                    Color idle = pixel(button, surface, 9, 19);
                    if (distance(surface, active) <= distance(surface, idle) + 5)
                        throw new AssertionError("Active view not visually distinct from idle");
                }
            }
        });
        System.out.println("OK: editor-like tab navigation in all three layers and six component views");
    }

    private static Color pixel(JComponent component, Color surface, int x, int y) {
        BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();
        g.setColor(surface); g.fillRect(0, 0, image.getWidth(), image.getHeight());
        component.paint(g); g.dispose();
        return new Color(image.getRGB(x, y));
    }
    private static int distance(Color a, Color b) {
        return Math.abs(a.getRed()-b.getRed())+Math.abs(a.getGreen()-b.getGreen())+Math.abs(a.getBlue()-b.getBlue());
    }
}
