package org.archvow.idea;

import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;

/** Compact, selectable decision detail. Never changes the recorded vow. */
final class ArchvowDecisionView {
    private ArchvowDecisionView() { }

    static JTextPane create() {
        JTextPane result = new JTextPane();
        result.setEditable(false);
        result.setFont(UIManager.getFont("Label.font"));
        result.setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
        return result;
    }

    static void display(JTextPane pane, JsonObject row, boolean fr, boolean dark) {
        StyledDocument doc = pane.getStyledDocument();
        Color body = dark ? new Color(225, 231, 240) : new Color(42, 52, 67);
        Color secondary = dark ? new Color(164, 177, 192) : new Color(93, 106, 123);
        Color blue = dark ? new Color(128, 187, 249) : new Color(35, 102, 188);
        Color green = dark ? new Color(116, 213, 167) : new Color(15, 132, 81);
        Color amber = dark ? new Color(244, 193, 110) : new Color(167, 104, 16);
        try {
            doc.remove(0, doc.getLength());
            if (row == null) {
                add(doc, fr ? "Sélectionne une décision." : "Select a decision.", secondary, false, 13);
                return;
            }
            boolean pending = ArchvowGrammar.str(row, "answer").isBlank();
            add(doc, (fr ? "DÉTAILS" : "DETAILS") + "   ", blue, true, 15);
            add(doc, "D" + ArchvowGrammar.str(row, "number") + "   ", body, true, 13);
            add(doc, pending ? (fr ? "● EN ATTENTE" : "● PENDING") : (fr ? "● TRAITÉ" : "● RESOLVED"), pending ? amber : green, true, 12);
            add(doc, "\n\n" + (fr ? "QUESTION" : "QUESTION") + "\n", secondary, true, 11);
            add(doc, ArchvowGrammar.str(row, "question"), body, false, 14);
            String answer = ArchvowGrammar.str(row, "answer");
            if (!answer.isBlank()) {
                add(doc, "\n\n" + (fr ? "RÉPONSE ENREGISTRÉE" : "RECORDED ANSWER") + "\n", green, true, 11);
                add(doc, answer, body, false, 13);
            }
            String proposed = ArchvowGrammar.str(row, "proposed");
            if (!proposed.isBlank()) {
                add(doc, "\n\n" + (fr ? "PROPOSITION IA" : "AI PROPOSAL") + "\n", blue, true, 11);
                add(doc, proposed, body, false, 13);
            }
        } catch (BadLocationException ignored) {
            pane.setText(fr ? "Impossible d'afficher la décision." : "Unable to show decision.");
        }
        pane.setCaretPosition(0);
    }

    private static void add(StyledDocument doc, String value, Color foreground, boolean bold, int size)
            throws BadLocationException {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setForeground(attributes, foreground);
        StyleConstants.setBold(attributes, bold);
        StyleConstants.setFontSize(attributes, size);
        doc.insertString(doc.getLength(), value, attributes);
    }
}
