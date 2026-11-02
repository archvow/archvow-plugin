package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/** Regression: translated save/cancel and result buttons remain visible at narrow editor widths. */
public final class ResponsiveActionLayoutsRegression {
    private static void check(boolean valid, String description) {
        if (!valid) throw new AssertionError(description);
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents())
            if (child instanceof Container inner) layout(inner);
    }

    private static void inside(JComponent container, Component child, String message) {
        Rectangle box = SwingUtilities.convertRectangle(child.getParent(), child.getBounds(), container);
        check(box.width > 0 && box.height > 0 && box.x >= 0 && box.y >= 0 &&
            box.x + box.width <= container.getWidth() && box.y + box.height <= container.getHeight(),
            message + ": " + box + " inside " + container.getSize());
    }

    public static void main(String[] args) {
        JPanel actions = ArchvowSplitView.actions(
            List.of(new JButton("Proposer"), new JButton("Accepter la proposition"), new JButton("Utiliser la suggestion")),
            List.of(new JButton("Annuler les modifications"), new JButton("Enregistrer la réponse")));
        int lastHeight = 0;
        for (int width : new int[] {750, 500, 370, 270, 230}) {
            actions.setSize(width, 1);
            int height = actions.getPreferredSize().height;
            actions.setSize(width, height);
            layout(actions);
            for (Component group : actions.getComponents()) {
                if (!(group instanceof Container row)) continue;
                for (Component button : row.getComponents()) if (button.isVisible()) {
                    inside(actions, button, "answer button at width " + width);
                }
            }
            check(height > 0 && (lastHeight == 0 || height >= lastHeight), "height must fit wrapped rows");
            lastHeight = height;
        }

        JLabel title = new JLabel("Résultat de la commande");
        JPanel buttons = new JPanel(new ArchvowWrapLayout(FlowLayout.RIGHT, 5, 1));
        for (String name : List.of("Réduire", "Sortie brute", "Copier", "×")) buttons.add(new JButton(name));
        ArchvowResultHeader header = new ArchvowResultHeader(title, buttons);
        lastHeight = 0;
        for (int width : new int[] {750, 500, 370, 270, 200}) {
            header.setSize(width, 1);
            int height = header.getPreferredSize().height;
            header.setSize(width, height);
            layout(header);
            inside(header, title, "result heading at width " + width);
            for (Component button : buttons.getComponents()) inside(header, button, "result button at width " + width);
            check(height > 0 && (lastHeight == 0 || height >= lastHeight), "result height must fit all controls");
            lastHeight = height;
        }
        System.out.println("OK: all answer and result buttons fit at 200-750 px");
    }
}
