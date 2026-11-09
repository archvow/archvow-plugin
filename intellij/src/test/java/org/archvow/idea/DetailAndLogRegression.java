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
        decision.addProperty("by", "architect@example.com");
        decision.addProperty("on", "2026-10-08T13:06:23.938480+00:00");
        JTextPane view = ArchvowDecisionView.create();
        ArchvowDecisionView.display(view, decision, false, false);
        String actual = view.getText();
        // the answer panel's heading (0.11.0): what is answered, the question, who recorded the answer and when
        if (!actual.startsWith("DECISION 2") || !actual.contains("ANSWERED") || !actual.contains("What happens when the path is empty?")
            || !actual.contains("Recorded by architect@example.com · 2026-10-08"))
            throw new IllegalStateException("Incomplete English decision heading: " + actual);
        ArchvowDecisionView.display(view, decision, true, true);
        actual = view.getText();
        if (!actual.startsWith("DÉCISION 2") || !actual.contains("TRAITÉE") || !actual.contains("Enregistrée par architect@example.com"))
            throw new IllegalStateException("Incomplete French decision heading: " + actual);
        decision.addProperty("answer", "");
        decision.addProperty("suggested", "Fail with an error naming the path");
        ArchvowDecisionView.display(view, decision, true, false);
        actual = view.getText();
        if (!actual.startsWith("RÉPONDRE À LA DÉCISION 2") || actual.contains("Enregistrée par") || !actual.contains("SUGGÉRÉ"))
            throw new IllegalStateException("Open decision heading: " + actual);
        Object project = new Object();
        ArchvowRunLog.record(project, new ArchvowRunLog.Call(java.util.List.of("check"), 2, "", "boom", false), true, 24);
        String log = ArchvowRunLog.snapshot(project, false);
        if (!log.contains("exit 2") || !log.contains("24 ms") || log.contains("--root"))
            throw new IllegalStateException("Unexpected log: " + log);
        System.out.println("OK: 4 detail and log checks");
    }
}
