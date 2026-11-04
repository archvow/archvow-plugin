package org.archvow.idea;

import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.util.List;

/**
 * Selectable, native Swing details for a safety vow. Matches the Decisions detail
 * hierarchy without relying on Swing's limited HTML/CSS support.
 *
 * The CLI remains the only source of truth for hazard/invariant state.
 */
final class ArchvowSafetyView {
    private ArchvowSafetyView() { }

    static JTextPane create() {
        JTextPane pane = new JTextPane();
        pane.setEditable(false);
        pane.setFont(UIManager.getFont("Label.font"));
        pane.setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
        return pane;
    }

    static void display(JTextPane pane, JsonObject question, boolean french, boolean dark) {
        display(pane, question, java.util.Map.of(), french, dark);
    }

    /** 0.16.0: with each hazard's origin (Template, AI · agent · To review) and why the AI added it. */
    static void display(JTextPane pane, JsonObject question, java.util.Map<String, ArchvowAiSkills.Origin> origins,
                        boolean french, boolean dark) {
        StyledDocument doc = pane.getStyledDocument();
        Color body = dark ? new Color(225, 231, 240) : new Color(42, 52, 67);
        Color secondary = dark ? new Color(164, 177, 192) : new Color(93, 106, 123);
        Color blue = dark ? new Color(128, 187, 249) : new Color(35, 102, 188);
        Color green = dark ? new Color(116, 213, 167) : new Color(15, 132, 81);
        Color amber = dark ? new Color(244, 193, 110) : new Color(167, 104, 16);
        try {
            doc.remove(0, doc.getLength());
            if (question == null) {
                add(doc, french ? "Sélectionne une question de sécurité." : "Select a safety question.",
                    secondary, false, 13);
                return;
            }
            boolean pending = ArchvowSafetyState.pending(question);
            String missingProtection = ArchvowSafetyState.unprotectedHazard(question);
            boolean needsInvariant = !missingProtection.isBlank();
            String number = ArchvowGrammar.str(question, "number");

            // Follow the Decisions detail hierarchy: identity, status, full question,
            // then only the recorded evidence and concrete missing protection.
            // the answer panel's heading (the split view's right side), as the decisions' one
            String word = ArchvowGrammar.str(question, "guide_word");
            add(doc, (french ? "SÉCURITÉ S" : "SAFETY S") + number + (word.isBlank() ? "" : " · " + word.toUpperCase(java.util.Locale.ROOT))
                + "   ", secondary, true, 11);
            add(doc, pending
                ? (needsInvariant ? (french ? "● INVARIANT REQUIS" : "● INVARIANT REQUIRED")
                                  : (french ? "● EN ATTENTE" : "● PENDING"))
                : (french ? "● TRAITÉ" : "● RESOLVED"), pending ? amber : green, true, 11);

            add(doc, "\n", secondary, false, 6);
            String prompt = ArchvowGrammar.str(question, "question");
            add(doc, prompt.isBlank() ? "—" : prompt, body, false, 15);

            String dismissal = ArchvowGrammar.str(question, "dismissed");
            if (!dismissal.isBlank()) {
                section(doc, french ? "JUSTIFICATION ENREGISTRÉE" : "RECORDED JUSTIFICATION", green);
                add(doc, dismissal, body, false, 13);
            }

            List<JsonObject> hazards = ArchvowGrammar.objects(question, "hazards");
            for (JsonObject hazard : hazards) {
                String id = ArchvowGrammar.str(hazard, "id");
                String severity = ArchvowGrammar.str(hazard, "severity");
                section(doc, (french ? "HAZARD ENREGISTRÉ" : "RECORDED HAZARD")
                    + (id.isBlank() ? "" : "  ·  " + id), blue);
                if (!severity.isBlank()) {
                    boolean serious = ArchvowSafetyState.requiresInvariant(hazard);
                    add(doc, (french ? "Gravité" : "Severity") + "  ·  " + severity + "\n",
                        serious ? amber : secondary, true, 12);
                }
                ArchvowAiSkills.Origin origin = origins.get(id);
                String tag = ArchvowItemCards.tag(origin, french);
                if (!tag.isEmpty()) add(doc, tag + "\n", "ai".equals(origin.tag()) ? blue : secondary, true, 11);
                String description = ArchvowGrammar.str(hazard, "description");
                add(doc, description.isBlank() ? "—" : description, body, false, 13);
                if (origin != null && !origin.why().isEmpty())
                    add(doc, "\n" + (french ? "Pourquoi : " : "Why: ") + origin.why(), secondary, false, 12);

                List<JsonObject> invariants = ArchvowGrammar.objects(hazard, "prevented_by");
                for (JsonObject invariant : invariants) {
                    String invariantId = ArchvowGrammar.str(invariant, "id");
                    section(doc, (french ? "INVARIANT PROTECTEUR" : "PROTECTING INVARIANT")
                        + (invariantId.isBlank() ? "" : "  ·  " + invariantId), green);
                    String statement = ArchvowGrammar.str(invariant, "statement");
                    add(doc, statement.isBlank() ? "—" : statement, body, false, 13);
                    if (invariant.has("tested") && !invariant.get("tested").isJsonNull()
                        && !invariant.get("tested").getAsBoolean()) {
                        add(doc, "\n" + (french ? "Test non vérifié" : "Test not verified"), amber, true, 12);
                    }
                }
                if (ArchvowSafetyState.requiresInvariant(hazard) && invariants.isEmpty()) {
                    section(doc, "ARCH030  ·  " + (french ? "ACTION REQUISE" : "ACTION REQUIRED"), amber);
                    add(doc, french
                        ? "Ajoute un invariant protégeant ce hazard avant de le considérer comme traité."
                        : "Add a protecting invariant before considering this hazard resolved.", body, false, 13);
                }
            }

            // archvow 1.19: a decision already answers the question - Confirm records it as that decision
            JsonObject covering = pending ? ArchvowSafetyState.coveringDecision(question) : null;
            if (covering != null) {
                String n = ArchvowGrammar.str(covering, "number");
                boolean nothing = !ArchvowSafetyState.notApplicable(question).isBlank();
                section(doc, nothing ? (french ? "LA DÉCISION " + n + " DIT QU'IL N'Y A RIEN À PROTÉGER" : "DECISION " + n + " SAYS THERE IS NOTHING TO PROTECT")
                    : (french ? "COUVERT PAR LA DÉCISION " + n : "COVERED BY DECISION " + n), green);
                add(doc, ArchvowGrammar.str(covering, "answer"), body, false, 13);
                add(doc, "\n" + (nothing
                    ? (french ? "Confirmer non applicable : la décision est la raison." : "Confirm not applicable: the decision is the reason.")
                    : (french ? "Confirmer enregistre le hazard et un invariant qui est cette décision : aucun mot recopié, son test marqué decision(" + n + ") le prouve."
                              : "Confirm records the hazard and an invariant that is this decision: no words copied, its test marked decision(" + n + ") proves it.")),
                    secondary, false, 12);
            }
            String suggestedHazard = ArchvowSafetyState.suggestedHazard(question);
            if (!suggestedHazard.isBlank()) {
                section(doc, french ? "HAZARD PROPOSÉ" : "PROPOSED HAZARD", blue);
                add(doc, suggestedHazard, body, false, 13);
            }
            String suggestedInvariant = ArchvowSafetyState.suggestedInvariant(question);
            if (!suggestedInvariant.isBlank() && covering == null) {
                section(doc, french ? "INVARIANT PROPOSÉ" : "PROPOSED INVARIANT", blue);
                add(doc, suggestedInvariant, body, false, 13);
            }
            if (pending && hazards.isEmpty() && dismissal.isBlank() && suggestedHazard.isBlank()) {
                section(doc, french ? "À FAIRE" : "NEXT STEP", amber);
                add(doc, french
                    ? "Déclare un hazard ou justifie pourquoi cette question ne s'applique pas."
                    : "Declare a hazard or justify why this question does not apply.", body, false, 13);
            }
        } catch (BadLocationException e) {
            pane.setText(french ? "Impossible d'afficher la sécurité." : "Unable to show safety details.");
        }
        pane.setCaretPosition(0);
    }

    private static void section(StyledDocument doc, String label, Color color) throws BadLocationException {
        add(doc, "\n\n" + label + "\n", color, true, 11);
    }

    private static void add(StyledDocument doc, String text, Color color, boolean bold, int size)
            throws BadLocationException {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setForeground(attributes, color);
        StyleConstants.setBold(attributes, bold);
        StyleConstants.setFontSize(attributes, size);
        doc.insertString(doc.getLength(), text, attributes);
    }
}
