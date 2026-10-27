package org.archvow.idea;

import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;

/** The heading of a decision's answer panel: "ANSWER DECISION 3", the question, who recorded the answer and
 *  when, the AI proposal. Selectable; never changes the recorded vow (the answer is in the field under it). */
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
        try {
            doc.remove(0, doc.getLength());
            if (row == null) {
                add(doc, fr ? "Sélectionne une décision." : "Select a decision.", secondary, false, 13);
                return;
            }
            boolean pending = ArchvowGrammar.str(row, "answer").isBlank();
            String number = ArchvowGrammar.str(row, "number");
            // the answer panel's heading (the split view's right side): what is answered, then the question
            add(doc, pending ? (fr ? "RÉPONDRE À LA DÉCISION " : "ANSWER DECISION ") + number
                : (fr ? "DÉCISION " : "DECISION ") + number, secondary, true, 11);
            if (!pending) add(doc, "   " + (fr ? "● TRAITÉE" : "● ANSWERED"), green, true, 11);
            add(doc, "\n", secondary, false, 6);
            add(doc, ArchvowGrammar.str(row, "question"), body, false, 15);
            String recorded = ArchvowItemCards.byOn(row);
            if (!pending && !recorded.isBlank()) {
                add(doc, "\n" + (fr ? "Enregistrée par " : "Recorded by ") + recorded, secondary, false, 11);
            }
            String proposed = ArchvowGrammar.str(row, "proposed");
            if (!proposed.isBlank()) {
                add(doc, "\n\n" + (fr ? "PROPOSITION IA" : "AI PROPOSAL") + "\n", blue, true, 11);
                add(doc, proposed, body, false, 13);
            }
            String suggested = ArchvowGrammar.str(row, "suggested");
            if (pending && !suggested.isBlank()) {
                add(doc, "\n\n" + (fr ? "SUGGÉRÉ" : "SUGGESTED") + "\n", secondary, true, 11);
                add(doc, suggested, body, false, 13);
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
