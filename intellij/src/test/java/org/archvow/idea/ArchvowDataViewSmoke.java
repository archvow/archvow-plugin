package org.archvow.idea;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;

/** Java 17 regression: the badge, the pending section, and component rows agree. */
public final class ArchvowDataViewSmoke {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        JsonObject data = new JsonObject();
        JsonArray cols = new JsonArray();
        JsonObject column = new JsonObject();
        column.addProperty("name", "customer_id");
        column.add("class", JsonNull.INSTANCE);
        cols.add(column);
        data.add("columns", cols);

        JsonArray tasks = new JsonArray();
        JsonObject task = new JsonObject();
        task.addProperty("task", "T1");
        task.addProperty("done", false);
        task.addProperty("summary", "Check the reader contract");
        tasks.add(task);
        data.add("tasks", tasks);

        JsonArray rules = new JsonArray();
        JsonObject rule = new JsonObject();
        rule.addProperty("id", "RULE1");
        rule.addProperty("text", "No sensitive data in logs");
        JsonObject scope = new JsonObject();
        scope.addProperty("kind", "component");
        scope.addProperty("id", "sales_reader");
        rule.add("scope", scope); // Rules may have a structured scope, not a string.
        rules.add(rule);
        data.add("rules", rules);

        check(ArchvowDataView.items(data).size() == 2, "Expected two classified work items");
        check(ArchvowDataView.pendingCount(data) == 2, "Expected two pending work items");
        for (Color background : new Color[] {new Color(245, 247, 249), new Color(35, 37, 41)}) {
            UIManager.put("Panel.background", background);
            UIManager.put("Label.foreground", background.getRed() > 100 ? Color.BLACK : Color.WHITE);
            JTextPane viewer = ArchvowDataView.create();
            ArchvowDataView.render(viewer, data, true);
            String text = viewer.getDocument().getText(0, viewer.getDocument().getLength());
            check(text.contains("Actions en attente : 2"), "Badge count is not reflected in text");
            check(text.contains("customer_id"), "Pending column invisible");
            check(text.contains("Check the reader contract"), "Pending task invisible");
            JTextPane rulesViewer = ArchvowDataView.create();
            ArchvowDataView.renderRules(rulesViewer, data, true);  // rules have their own view
            String rulesText = rulesViewer.getDocument().getText(0, rulesViewer.getDocument().getLength());
            check(rulesText.contains("No sensitive data in logs"), "Complex rule not visible");
            check(rulesText.contains("sales_reader"), "Structured rule scope not visible");
            check(!viewer.isEditable(), "Viewer must remain read-only and selectable");
            check(viewer.getDocument().getLength() > 200, "Unexpected empty viewer");
        }
        column.addProperty("class", "personal");
        task.addProperty("done", true);
        check(ArchvowDataView.pendingCount(data) == 0, "Updates must clear the pending count");
        System.out.println("Archvow Rules & data: consistency, rendering and theme smoke test passed");
    }
}
