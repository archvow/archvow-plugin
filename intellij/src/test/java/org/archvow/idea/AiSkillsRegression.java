package org.archvow.idea;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 0.16.0, the same as VS Code (vscode/src/test/aiSkills.test.ts): the origin of each decision, hazard and rule,
 * what the AI added that waits for a person, the next skill and why a skill cannot run yet, on every scenario of
 * ../contract/forms/ai-skills.scenarios.json, equal to ../contract/forms/ai-skills.expected.json; and the request
 * each skill sends.
 */
public final class AiSkillsRegression {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static JsonElement read(Path file) throws Exception {
        return JsonParser.parseString(Files.readString(file));
    }

    public static void main(String[] args) throws Exception {
        Path contract = Path.of(System.getProperty("archvow.contract.root", "../contract"));
        JsonArray scenarios = read(contract.resolve("forms/ai-skills.scenarios.json")).getAsJsonObject().getAsJsonArray("scenarios");
        JsonArray expected = read(contract.resolve("forms/ai-skills.expected.json")).getAsJsonArray();
        check(scenarios.size() == expected.size(), "one expected view per scenario");
        for (int i = 0; i < scenarios.size(); i++) {
            JsonObject sc = scenarios.get(i).getAsJsonObject();
            List<String> launched = new ArrayList<>();
            sc.getAsJsonArray("launched").forEach(e -> launched.add(e.getAsString()));
            JsonObject got = ArchvowAiSkills.toJson(ArchvowAiSkills.view(sc.getAsJsonObject("view"), launched));
            JsonObject want = expected.get(i).getAsJsonObject().getAsJsonObject("ai");
            check(got.equals(want), sc.get("name").getAsString() + ": IntelliJ " + got + "\n  VS Code " + want);
        }
        // the first scenario, read the way a person reads the cards
        ArchvowAiSkills.View all = ArchvowAiSkills.view(scenarios.get(0).getAsJsonObject().getAsJsonObject("view"), List.of());
        check(all.decisions.stream().map(ArchvowAiSkills.Origin::tag).toList()
            .equals(List.of("template", "project", "brief", "ai", "ai", "ai", "")), "every origin: " + all.decisions);
        check(all.count("total") == 6 && "to-review".equals(all.suggest.skill()), "what the AI added waits first");
        check(all.blocked.get("implement")[0].contains("6 items from the AI to review"), "implement waits");
        JsonObject forms = read(contract.resolve("forms/forms.json")).getAsJsonObject();
        JsonObject skills = forms.getAsJsonObject("ai_skills");
        List<String> order = new ArrayList<>();
        skills.getAsJsonArray("order").forEach(e -> order.add(e.getAsString()));
        check(order.equals(ArchvowAiSkills.SKILLS), "the five skills, in the order of the work");
        check(ArchvowAiSkills.request(skills, "clarify", "claude-code", Map.of("component", "clean_orders", "max", "3"), false)
            .equals("/archvow-clarify clean_orders (at most 3 questions)"), "Claude Code: the slash command");
        check(ArchvowAiSkills.request(skills, "analyze", "copilot", Map.of("component", "clean_orders"), true)
            .equals("Utilise le skill archvow-analyze : analyse les risques de clean_orders"), "the sentence, in French");
        check(ArchvowAiSkills.request(skills, "draft-spec", "cursor", Map.of("brief", "briefs/orders.md#pricing"), false)
            .equals("Use the archvow-draft-spec skill: draft the spec from briefs/orders.md#pricing"), "a brief section");
        System.out.println("AiSkillsRegression: OK");
    }
}
