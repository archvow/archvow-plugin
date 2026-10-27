package org.archvow.idea;

import com.google.gson.JsonObject;
import javax.swing.*;
import java.util.Objects;

public class DetailAndLogRegression {
    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        JsonObject decision = new JsonObject();
        decision.addProperty("number", "2");
        decision.addProperty("question", "What happens when the path is empty?");
        decision.addProperty("answer", "Fail fast");
        JTextPane view = ArchvowDecisionView.create();
        ArchvowDecisionView.display(view, decision, false, false);
        String actual = view.getText();
        if (!actual.contains("DETAILS") || !actual.contains("RESOLVED") || !actual.contains("Fail fast"))
            throw new IllegalStateException("Incomplete English decision detail: " + actual);
        ArchvowDecisionView.display(view, decision, true, true);
        actual = view.getText();
        if (!actual.contains("DÉTAILS") || !actual.contains("TRAITÉ") || !actual.contains("RÉPONSE ENREGISTRÉE"))
            throw new IllegalStateException("Incomplete French decision detail: " + actual);
        decision.addProperty("answer", "");
        ArchvowDecisionView.display(view, decision, true, false);
        if (!view.getText().contains("EN ATTENTE")) throw new IllegalStateException("Pending status not shown");
        Object project = new Object();
        ArchvowRunLog.record(project, "check", 2, false, 24);
        String log = ArchvowRunLog.snapshot(project, false);
        if (!log.contains("exit 2") || !log.contains("24 ms") || log.contains("--root"))
            throw new IllegalStateException("Unexpected log: " + log);
        System.out.println("OK: 4 detail and log checks");
    }
}
