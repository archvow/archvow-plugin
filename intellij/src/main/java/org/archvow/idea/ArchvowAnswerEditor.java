package org.archvow.idea;

import javax.swing.*;
import java.awt.*;

/**
 * The editor under a decision, a question or a safety question: a heading, the text field, then the
 * buttons with the slot where the inline confirmation appears. Its height follows its content, so the
 * field keeps its height and the heading stays above the buttons when the confirmation opens.
 */
final class ArchvowAnswerEditor {
    private ArchvowAnswerEditor() { }

    /** {@code buttons} then {@code confirmSlot} under the field; {@code field} at its preferred height. */
    static JPanel create(JComponent heading, JComponent field, JComponent buttons, JComponent confirmSlot) {
        JPanel actions = new JPanel(new BorderLayout(0, 3));
        actions.add(buttons, BorderLayout.NORTH);
        actions.add(confirmSlot, BorderLayout.SOUTH);
        JPanel editor = new JPanel(new BorderLayout(4, 5));
        editor.add(heading, BorderLayout.NORTH);
        editor.add(field, BorderLayout.CENTER);
        editor.add(actions, BorderLayout.SOUTH);
        return editor;
    }
}
