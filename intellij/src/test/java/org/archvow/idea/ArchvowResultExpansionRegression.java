package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/** Regression for the two problems reported with 'Agrandir': clipped command actions and half-width results. */
public final class ArchvowResultExpansionRegression {
    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }

    private static void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents())
            if (child.isVisible() && child instanceof Container c) layout(c);
    }

    private static JPanel resultCard() {
        JLabel label = new JLabel("Résultat de la commande");
        JPanel buttons = new JPanel(new ArchvowWrapLayout(FlowLayout.RIGHT, 5, 1));
        for (String text : List.of("Réduire", "Sortie brute", "Copier", "×")) buttons.add(new JButton(text));
        JPanel result = new JPanel(new BorderLayout());
        result.add(new ArchvowResultHeader(label, buttons), BorderLayout.NORTH);
        JScrollPane output = new JScrollPane(new JTextArea("wrote .archvow/spec.md"));
        output.setPreferredSize(new Dimension(200, 225));
        result.add(output, BorderLayout.CENTER);
        return result;
    }

    private static void fullScreen(JPanel root, JPanel result, String which) {
        layout(root);
        check(result.getWidth() >= 1000, which + ": result must span both columns: " + result.getBounds());
        check(result.getHeight() >= 600, which + ": result must use workspace height: " + result.getBounds());
        check(result.getY() >= 0, which + ": no content behind the header");
        ArchvowResultHeader header = (ArchvowResultHeader) ((BorderLayout) result.getLayout()).getLayoutComponent(BorderLayout.NORTH);
        check(header.getY() >= 0 && header.getHeight() > 0, which + ": actions visible at top of result");
        for (Component child : header.getComponents()) {
            Rectangle bounds = SwingUtilities.convertRectangle(child.getParent(), child.getBounds(), result);
            check(bounds.y >= 0 && bounds.y + bounds.height <= result.getHeight(), which + ": header content not clipped: " + bounds);
            if (child instanceof JPanel row)
                for (Component button : row.getComponents()) {
                    Rectangle b = SwingUtilities.convertRectangle(button.getParent(), button.getBounds(), result);
                    check(b.y >= 0 && b.y + b.height <= result.getHeight(), which + ": action not clipped: " + b);
                }
        }
    }

    private static void commandCase() {
        CardLayout layout = new CardLayout();
        JPanel root = new JPanel(layout);
        JPanel command = new JPanel(new BorderLayout());
        command.add(new JLabel("Formulaire"), BorderLayout.NORTH);
        JPanel bottom = new JPanel(new BorderLayout());
        JPanel result = resultCard();
        bottom.add(result, BorderLayout.CENTER);
        command.add(bottom, BorderLayout.SOUTH);
        root.add(command, "workspace");
        ArchvowResultExpansion expansion = new ArchvowResultExpansion(root, layout, "workspace");
        root.setSize(1200, 760);
        check(expansion.expand(result), "command: expand succeeds");
        fullScreen(root, result, "command");
        check(expansion.collapse() == result, "command: same result restored");
        check(result.getParent() == bottom && ((BorderLayout) bottom.getLayout()).getLayoutComponent(BorderLayout.CENTER) == result,
            "command: original BorderLayout.CENTER slot restored");
        check(!expansion.isExpanded(), "command: is not expanded after Reduce");
        check(expansion.expand(result), "command: can expand again");
        expansion.collapse();
    }

    private static void splitCase() {
        CardLayout layout = new CardLayout();
        JPanel root = new JPanel(layout);
        JPanel columns = new JPanel(new GridLayout(1, 2));
        columns.add(new JScrollPane(new JList<>(new String[] {"decision 1", "decision 2"})));
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        JLabel details = new JLabel("Votre réponse ou proposition");
        JPanel result = resultCard();
        JLabel below = new JLabel("next section");
        right.add(details);
        right.add(result);
        right.add(below);
        columns.add(new JScrollPane(right));
        root.add(columns, "workspace");
        ArchvowResultExpansion expansion = new ArchvowResultExpansion(root, layout, "workspace");
        root.setSize(1200, 760);
        layout(root);
        check(columns.getComponent(0).getWidth() <= 610, "split: list is a half-width column");
        check(expansion.expand(result), "split: expand succeeds");
        fullScreen(root, result, "split");
        expansion.collapse();
        check(result.getParent() == right && right.getComponent(1) == result, "split: restored to precise BoxLayout index");
        check(right.getComponent(0) == details && right.getComponent(2) == below, "split: siblings preserved");
    }

    public static void main(String[] args) {
        commandCase();
        splitCase();
        System.out.println("OK: expanded results fill the workspace; buttons visible; Reduce restores command and split panels");
    }
}
