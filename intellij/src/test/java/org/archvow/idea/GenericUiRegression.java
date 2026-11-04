package org.archvow.idea;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import javax.swing.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Shared contract, dependency choices and the optional column-answer helper, headless. */
public final class GenericUiRegression {
    private static void check(boolean ok, String reason) {
        if (!ok) throw new AssertionError(reason);
    }

    public static void main(String[] args) throws Exception {
        List<ArchvowForms.Group> groups = ArchvowForms.commandGroups(false);
        check(!groups.isEmpty(), "Command groups must come from the shared forms.json contract");
        Set<String> labels = new HashSet<>();
        for (ArchvowGrammar.Command command : ArchvowGrammar.COMMANDS) {
            int count = 0;
            for (ArchvowForms.Group group : groups)
                if (group.prefixes().contains(command.path.get(0))) count++;
            check(count == 1, "Command belongs to exactly one group: " + command.label);
            check(labels.add(command.label), "Duplicate command: " + command.label);
        }
        SwingUtilities.invokeAndWait(() -> {
            ArchvowUsesPicker picker = new ArchvowUsesPicker("schema,imported_dependency",
                java.util.Map.of("schema", "schema · schema", "config", "config · foundation", "reader", "reader · reader"), "reader", false, () -> {});
            check("schema,imported_dependency".equals(picker.value()),
                "Unknown imported dependency is not silently removed by the multi-select");

            JTextArea target = new JTextArea();
            ArchvowColumnAnswerPanel panel = new ArchvowColumnAnswerPanel(target);
            JsonObject view = JsonParser.parseString("""
                {"known_columns":[{"component":"model","columns":["id","comment"]}],
                 "columns":[{"name":"comment","class":"personal"}]}
                """).getAsJsonObject();
            JsonObject question = JsonParser.parseString("""
                {"question":"Which columns are nullable or hold personal data?"}
                """).getAsJsonObject();
            panel.showFor(view, question, false);
            check(panel.isVisible(), "Column properties question offers a structured assistant");
            String draft = panel.answer();
            check(draft.contains("id: nullability unknown, personal-data classification unknown"),
                "Unspecified type and classification must not be invented");
            check(draft.contains("comment: nullability unknown, personal data"),
                "Existing personal classification may be reused");
            check(target.getText().isBlank(), "Opening a helper must not modify the user answer");
            panel.showFor(view, JsonParser.parseString("{\"question\":\"What is the timeout?\"}").getAsJsonObject(), false);
            check(!panel.isVisible(), "Unrelated decisions keep the normal text answer");
        });
        System.out.println("GenericUiRegression: command groups, uses and structured draft OK");
    }
}
