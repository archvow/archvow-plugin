package org.archvow.idea;

import com.google.gson.*;
import javax.swing.*;

/** Native safety details should match decisions and reflect invariant coverage. */
public final class SafetyDetailsRegression {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JsonObject question = new JsonObject();
            question.addProperty("number", "1");
            question.addProperty("status", "answered");
            question.addProperty("question", "Input missing?");
            JsonObject hazard = new JsonObject();
            hazard.addProperty("id", "H1");
            hazard.addProperty("severity", "high");
            hazard.addProperty("description", "Input silently dropped");
            JsonArray hazards = new JsonArray(); hazards.add(hazard);
            question.add("hazards", hazards);
            JTextPane pane = ArchvowSafetyView.create();
            ArchvowSafetyView.display(pane, question, false, false);
            String pending = pane.getText();
            if (!pending.contains("INVARIANT REQUIRED") || !pending.contains("ARCH030")
                || !pending.contains("Input silently dropped")) throw new AssertionError(pending);
            JsonObject invariant = new JsonObject();
            invariant.addProperty("id", "INV1");
            invariant.addProperty("statement", "Reject missing input");
            JsonArray protections = new JsonArray(); protections.add(invariant);
            hazard.add("prevented_by", protections);
            ArchvowSafetyView.display(pane, question, true, true);
            String resolved = pane.getText();
            if (!resolved.contains("TRAITÉ") || !resolved.contains("INVARIANT PROTECTEUR")
                || !resolved.contains("Reject missing input")) throw new AssertionError(resolved);
            System.out.println("OK: readable safety detail and invariant coverage");
        });
    }
}
