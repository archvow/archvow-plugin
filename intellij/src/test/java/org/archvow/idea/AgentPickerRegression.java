package org.archvow.idea;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The AI assistants of init (0.26.1): ai_tools and reviewers are picked from the agents archvow knows (the catalog's
 * suggestions), and any other name can be added - archvow takes any agent name. Before: one text box, "comma-separated".
 */
public final class AgentPickerRegression {
    private static void equal(Object expected, Object actual, String what) {
        if (!java.util.Objects.equals(expected, actual)) throw new AssertionError(what + ": expected " + expected + ", got " + actual);
    }

    private static <T extends java.awt.Component> java.util.List<T> all(java.awt.Container root, Class<T> type) {
        java.util.List<T> out = new java.util.ArrayList<>();
        for (java.awt.Component c : root.getComponents()) {
            if (type.isInstance(c)) out.add(type.cast(c));
            if (c instanceof java.awt.Container inner) out.addAll(all(inner, type));
        }
        return out;
    }

    public static void main(String[] args) throws Exception {
        JsonObject catalog = JsonParser.parseString(Files.readString(Path.of("../contract/cli/catalog.json"))).getAsJsonObject();
        JsonObject aiTools = null, reviewers = null, platform = null;
        for (var q : catalog.getAsJsonArray("templates").get(0).getAsJsonObject().getAsJsonArray("questions")) {
            String id = q.getAsJsonObject().get("id").getAsString();
            if (id.equals("ai_tools")) aiTools = q.getAsJsonObject();
            if (id.equals("reviewers")) reviewers = q.getAsJsonObject();
            if (id.equals("platform")) platform = q.getAsJsonObject();
        }
        equal(true, ArchvowAgentPicker.names(aiTools), "ai_tools names agents");
        equal(true, ArchvowAgentPicker.names(reviewers), "reviewers too");
        equal(false, ArchvowAgentPicker.names(platform), "platform does not");
        List<String> known = ArchvowAgentPicker.suggestions(aiTools);
        equal(true, known.containsAll(List.of("claude-code", "copilot", "cursor", "none")), "the agents archvow knows, and none");

        equal(List.of("copilot", "claude-code"), ArchvowAgentPicker.with(List.of("copilot"), "claude-code"), "add");
        equal(List.of("none"), ArchvowAgentPicker.with(List.of("copilot", "claude-code"), "none"), "none alone");
        equal(List.of("cursor"), ArchvowAgentPicker.with(List.of("none"), "cursor"), "none goes away");
        equal(List.of("cursor"), ArchvowAgentPicker.with(List.of("cursor"), "cursor"), "once");
        equal("my-agent", ArchvowAgentPicker.validName(" My-Agent "), "any agent name, lowercased");
        equal(null, ArchvowAgentPicker.validName("my agent"), "a name archvow refuses");
        equal(null, ArchvowAgentPicker.validName("-x"), "a name archvow refuses");

        ArchvowAgentPicker picker = new ArchvowAgentPicker(known, "copilot", false);
        int[] changes = {0};
        picker.onChange(() -> changes[0]++);
        JComboBox<?> add = all(picker, JComboBox.class).get(0);
        equal("Add an assistant…", add.getItemAt(0), "the list says what it does");
        boolean offersCopilot = false;
        for (int i = 0; i < add.getItemCount(); i++) offersCopilot |= "copilot".equals(add.getItemAt(i));
        equal(false, offersCopilot, "copilot chosen: not offered again");
        for (int i = 0; i < add.getItemCount(); i++) if ("claude-code".equals(add.getItemAt(i))) add.setSelectedIndex(i);
        equal("copilot,claude-code", picker.value(), "picked from the list");
        JTextField other = all(picker, JTextField.class).get(0);
        other.setText("My-Agent");
        other.postActionEvent();
        equal("copilot,claude-code,my-agent", picker.value(), "another assistant, by name");
        other.setText("my agent");
        other.postActionEvent();
        equal("copilot,claude-code,my-agent", picker.value(), "a wrong name is not added");
        boolean said = false;
        for (JLabel l : all(picker, JLabel.class)) said |= l.isVisible() && l.getText().contains("lowercase letters and digits");
        equal(true, said, "and the person is told why");
        boolean standard = false;
        for (JLabel l : all(picker, JLabel.class)) standard |= "my-agent".equals(l.getText()) && l.getToolTipText().contains("standard files");
        equal(true, standard, "an unknown one says what it gets");
        // a chip is as wide as its name and its small ×: no button's minimum width between them (0.26.1)
        for (ArchvowAgentPicker.Chip chip : all(picker, ArchvowAgentPicker.Chip.class)) {
            JLabel name = all(chip, JLabel.class).get(0);
            int extra = chip.getPreferredSize().width - name.getPreferredSize().width;
            if (extra > 30) throw new AssertionError("chip '" + name.getText() + "' is " + extra + " px wider than its name");
            if (chip.getPreferredSize().height > name.getPreferredSize().height + 6) throw new AssertionError("chip too tall: " + chip.getPreferredSize());
        }
        for (ArchvowAgentPicker.Remove r : all(picker, ArchvowAgentPicker.Remove.class)) if ("Remove copilot".equals(r.getToolTipText())) r.doClick();
        equal("claude-code,my-agent", picker.value(), "removed");
        equal(3, changes[0], "each change is the form's");
        System.out.println("AgentPickerRegression OK");
    }
}
