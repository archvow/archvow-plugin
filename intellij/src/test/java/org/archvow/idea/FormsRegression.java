package org.archvow.idea;

import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Design A (0.14.0): the forms of both IDEs come from the same contract/forms/forms.json - plain labels, the
 * essential fields first in its order, the verb and what archvow will do; the live preview is the command
 * line Run sends; archvow's refusal and doubts are read as VS Code's alerts() reads them.
 */
public final class FormsRegression {
    public static void main(String[] args) throws Exception {
        ArchvowGrammar.Command invariant = command("invariant add");
        check("Always true".equals(ArchvowForms.label("invariant add", "statement", false)), "label en");
        check("Toujours vrai".equals(ArchvowForms.label("invariant add", "statement", true)), "label fr");
        check("Add invariant".equals(ArchvowForms.verb("invariant add", false)) && "Ajouter l'invariant".equals(ArchvowForms.verb("invariant add", true)), "verb");
        check(!ArchvowForms.does("invariant add", false).isBlank(), "does");
        List<String> order = ArchvowForms.ordered(invariant).stream().map(p -> p.name).toList();
        check(order.subList(0, 4).equals(List.of("component", "prevents", "statement", "from_decision")), "order " + order);
        for (ArchvowGrammar.Param p : invariant.parameters)
            if (p.hidden) check(!ArchvowForms.essential("invariant add", p), "expert " + p.name + " under More options");
        ArchvowGrammar.Command add = command("component add");
        for (ArchvowGrammar.Param p : add.parameters)
            check(ArchvowForms.essential("component add", p) == List.of("type", "kind", "name", "description", "spec", "uses").contains(p.name), "essential " + p.name);
        check(ArchvowForms.verb("catalog", false).isEmpty() && "item".equals(ArchvowForms.label("x", "item", false).isEmpty() ? "" : "item"), "fallback");
        check(ArchvowForms.label("catalog", "no_such_param", false).equals("no such param"), "archvow's own name");
        // the preview: what Run sends, without --json; a required field left empty is named
        check(ArchvowForms.commandLine(invariant, Map.of("component", "c", "statement", "order_id is unique", "prevents", "c.H1"), false)
            .equals("archvow invariant add c \"order_id is unique\" --prevents c.H1"), "preview");
        check(ArchvowForms.commandLine(invariant, Map.of(), true).equals("Champ obligatoire : component"), "missing");
        // every name in forms.json is one of archvow's
        JsonObject data = ArchvowForms.DATA;
        for (String param : data.getAsJsonObject("labels").keySet())
            check(ArchvowGrammar.COMMANDS.stream().anyMatch(c -> c.parameters.stream().anyMatch(p -> p.name.equals(param))), "unknown " + param);
        for (String name : data.getAsJsonObject("commands").keySet()) command(name);
        // refusal in red, doubts in amber (as VS Code's alerts())
        check(ArchvowForms.refusal(2, "✖ archvow: error: \"none\" protects nothing.\n").equals("\"none\" protects nothing."), "refusal");
        check(ArchvowForms.refusal(0, "x").isEmpty(), "no refusal on success");
        check(ArchvowForms.warnings("{\"id\": \"c.INV1\", \"warnings\": [\"weak (ARCH037)\"]}").equals(List.of("weak (ARCH037)")), "json warnings");
        check(ArchvowForms.warnings("✔ Changed\n! c.INV1 is proven by an example_test (ARCH037)\n").equals(List.of("c.INV1 is proven by an example_test (ARCH037)")), "text warnings");
        // the panel uses them, with one Spec view, covered questions, choices and the AI's components
        String panel = Files.readString(Path.of("src/main/java/org/archvow/idea/ArchvowPanel.java"), StandardCharsets.UTF_8);
        for (String piece : List.of("ArchvowForms.ordered(command)", "ArchvowForms.commandLine(", "showFormAlerts(result)", "\"More options\"",
                "\"Spec\", specPanel()", "ArchvowSafetyState.coveringDecision(row)", "ArchvowInlineCommands.editHazard(", "decisionChoices", "\"component accept\""))
            check(panel.contains(piece), "panel: " + piece);
        check(!panel.contains("\"Full vow\""), "no Full vow");
        System.out.println("OK: design A forms (labels, order, More options, preview, refusal), Spec view, covered questions, choices, AI components");
    }

    private static ArchvowGrammar.Command command(String name) {
        return ArchvowGrammar.COMMANDS.stream().filter(c -> c.label.equals(name)).findFirst()
            .orElseThrow(() -> new AssertionError("no command " + name));
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
