package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Runnable without IntelliJ SDK; checks real Swing button/card navigation in both themes. */
public final class ViewNavigationRegression {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (Color surface : List.of(new Color(245, 247, 250), new Color(38, 40, 44))) {
                UIManager.put("Panel.background", surface);
                UIManager.put("Label.foreground", surface.getRed() > 120 ? Color.BLACK : Color.WHITE);
                CardLayout layout = new CardLayout();
                JPanel cards = new JPanel(layout);
                List<JToggleButton> buttons = new ArrayList<>();
                List<JLabel> panels = new ArrayList<>();
                for (int i = 0; i < 6; i++) {
                    final int idx = i;
                    JLabel view = new JLabel("View " + idx);
                    panels.add(view);
                    cards.add(view, Integer.toString(idx));
                    JToggleButton b = new ArchvowMotion.EditorTabButton("View " + idx);
                    ArchvowMotion.installViewButton(b);
                    b.addActionListener(e -> {
                        layout.show(cards, Integer.toString(idx));
                        for (int j = 0; j < buttons.size(); j++) {
                            buttons.get(j).setSelected(j == idx);
                            ArchvowMotion.updateViewButton(buttons.get(j));
                        }
                    });
                    buttons.add(b);
                }
                for (int i = 0; i < 6; i++) {
                    buttons.get(i).doClick();
                    if (!buttons.get(i).isSelected()) throw new AssertionError("Button not selected " + i);
                    for (int j = 0; j < 6; j++) {
                        if (panels.get(j).isVisible() != (j == i))
                            throw new AssertionError("Wrong view visible " + i + " / " + j);
                    }
                }
                buttons.get(0).doClick();
                // Selection is now painted, not stored in JButton.background.
                // Test the pixels users actually see in both themes.
                Color selected = paintedBackground(buttons.get(0), surface);
                Color normal = paintedBackground(buttons.get(1), surface);
                double selectedDifference = colorDistance(surface, selected);
                double normalDifference = colorDistance(surface, normal);
                if (selectedDifference <= normalDifference + 5)
                    throw new AssertionError("Selection should be stronger than idle");
            }
        });
        System.out.println("OK: six view buttons, live cards and both themes");
    }
    private static Color paintedBackground(JToggleButton button, Color surface) {
        button.setSize(140, 40);
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(140, 40,
            java.awt.image.BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(surface);
        g.fillRect(0, 0, 140, 40);
        button.paint(g);
        g.dispose();
        return new Color(image.getRGB(10, 20)); // away from the text, inside the selected surface
    }

    private static double colorDistance(Color a, Color b) {
        return Math.abs(a.getRed() - b.getRed()) + Math.abs(a.getGreen() - b.getGreen()) + Math.abs(a.getBlue() - b.getBlue());
    }
}
