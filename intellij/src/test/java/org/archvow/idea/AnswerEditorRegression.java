package org.archvow.idea;

import javax.swing.*;
import java.awt.*;

/**
 * The answer field keeps its height and its heading stays above "Save answer" when the inline
 * confirmation opens (it was squeezed to nothing and the heading drawn over the button).
 */
public final class AnswerEditorRegression {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    /** What revalidate() and the next layout do on screen (headless components have no peer to validate). */
    private static void relayout(Container c) {
        c.invalidate();
        for (Component child : c.getComponents()) if (child instanceof Container inner) relayout(inner);
    }

    private static void layout(Container c) {
        c.doLayout();
        for (Component child : c.getComponents()) if (child instanceof Container inner) layout(inner);
    }

    private static Rectangle in(Component c, Component root) {
        return SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), root);
    }

    public static void main(String[] args) {
        JLabel heading = new JLabel("Your answer or proposal");
        JScrollPane field = new JScrollPane(new JTextArea("Échouer avec une erreur qui nomme le chemin"));
        field.setPreferredSize(new Dimension(150, 75));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        JButton save = new JButton("Save answer");
        buttons.add(save);
        buttons.add(new JButton("Propose"));
        buttons.add(new JButton("Accept proposal"));
        buttons.add(new JButton("Use suggestion"));
        JPanel slot = new JPanel(new BorderLayout());
        JPanel editor = ArchvowAnswerEditor.create(heading, field, buttons, slot);

        JPanel stack = new JPanel();  // as ArchvowPanel.compactSections stacks the sections
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        editor.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        stack.add(editor);

        for (boolean confirming : new boolean[] {false, true}) {
            slot.removeAll();
            if (confirming) {
                JPanel confirmation = new JPanel();
                confirmation.setPreferredSize(new Dimension(500, 160));
                slot.add(confirmation, BorderLayout.CENTER);
            }
            relayout(stack);
            stack.setSize(new Dimension(640, stack.getPreferredSize().height));
            layout(stack);
            Rectangle h = in(heading, stack);
            Rectangle f = in(field, stack);
            Rectangle b = in(save, stack);
            String state = confirming ? "with the confirmation" : "without it";
            check(f.height >= 75, "answer field squeezed " + state + ": " + f.height + " px");
            check(h.y + h.height <= f.y, "heading over the field " + state);
            check(f.y + f.height <= b.y, "field over the buttons " + state);
            check(h.y + h.height <= b.y, "heading over Save answer " + state);
        }
        System.out.println("OK: the answer editor keeps its field and heading with the confirmation open");
    }
}
