package org.archvow.idea;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The AI skills and the origin of each item (0.16.0, archvow 1.20), from one component's {@code show --json}:
 * where each decision, hazard and rule came from (the tag on its card), what the AI added that no person kept
 * yet ("To review"), which skill to suggest next and why a skill cannot run yet. The same as the VS Code
 * extension's {@code vscode/src/aiSkills.ts} ({@code AiSkillsRegression} on
 * {@code contract/forms/ai-skills.expected.json}). Plain JSON: no IntelliJ state.
 */
final class ArchvowAiSkills {
    static final List<String> SKILLS = List.of("draft-spec", "clarify", "analyze", "implement", "review");

    private ArchvowAiSkills() { }

    /** Where an item came from: tag template | project | brief | ai | "" (a person's own). */
    record Origin(String tag, boolean toReview, String who, String why, String section, boolean proposal) {
        static Origin none() { return new Origin("", false, "", "", "", false); }
        Origin reviewing(boolean waiting) { return new Origin(tag, waiting, who, why, section, proposal); }
    }

    /** One suggestion: a skill id, or "to-review" when what the AI added waits first; its reason [en, fr]. */
    record Suggestion(String skill, int count, String[] reason) { }

    /** Everything about AI for one component. */
    static final class View {
        final List<Origin> decisions = new ArrayList<>();
        final Map<String, Origin> hazards = new LinkedHashMap<>();
        final List<Boolean> safety = new ArrayList<>();
        final Map<String, Origin> rules = new LinkedHashMap<>();
        final Map<String, Integer> toReview = new LinkedHashMap<>();
        final Map<String, String[]> blocked = new LinkedHashMap<>();
        Suggestion suggest;

        int count(String key) { return toReview.getOrDefault(key, 0); }
    }

    private static String s(JsonObject o, String key) {
        JsonElement v = o == null ? null : o.get(key);
        return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isString() ? v.getAsString() : "";
    }

    private static boolean yes(JsonObject o, String key) {
        JsonElement v = o == null ? null : o.get(key);
        return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean() && v.getAsBoolean();
    }

    private static Origin aiOrigin(JsonObject item) {
        if (s(item, "added_by").isEmpty()) return null;
        return new Origin("ai", yes(item, "to_accept"), s(item, "added_by"), s(item, "why"), "", false);
    }

    private static Origin briefOrigin(JsonObject item) {
        JsonElement source = item.get("source");
        if (source == null || !source.isJsonObject() || s(source.getAsJsonObject(), "file").isEmpty()) return null;
        return new Origin("brief", false, "", "", s(source.getAsJsonObject(), "section"), false);
    }

    /** A decision: the AI's question or proposal first, then the project's answer, the brief, the template. */
    static Origin decision(JsonObject d) {
        Origin ai = aiOrigin(d);
        if (ai != null) return ai;
        if (!s(d, "proposed").isEmpty() && s(d, "answer").isEmpty()) return new Origin("ai", true, "", "", "", true);
        if ("type".equals(s(d, "origin"))) return new Origin("project", false, "", "", "", false);
        Origin brief = briefOrigin(d);
        if (brief != null) return brief;
        return yes(d, "template") ? new Origin("template", false, "", "", "", false) : Origin.none();
    }

    /** A hazard: the AI's (its own or one of the invariants it added to prevent it), or the template's words. */
    static Origin hazard(JsonObject h) {
        Origin ai = aiOrigin(h);
        List<JsonObject> guards = ArchvowGrammar.objects(h, "prevented_by").stream().filter(i -> yes(i, "to_accept")).toList();
        if (ai != null) return ai.reviewing(ai.toReview() || !guards.isEmpty());
        if (!guards.isEmpty()) return new Origin("ai", true, s(guards.get(0), "added_by"), s(guards.get(0), "why"), "", false);
        return yes(h, "suggested") ? new Origin("template", false, "", "", "", false) : Origin.none();
    }

    static Origin rule(JsonObject r) {
        Origin ai = aiOrigin(r);
        if (ai != null) return ai;
        Origin brief = briefOrigin(r);
        return brief != null ? brief : Origin.none();
    }

    private static String plural(int n) { return n == 1 ? "" : "s"; }

    /** Everything the views need about AI for one component; {@code launched}: the skills a person started on it here. */
    static View view(JsonObject data, List<String> launched) {
        View out = new View();
        for (String key : List.of("decisions", "safety", "rules", "assumptions", "component", "total")) out.toReview.put(key, 0);
        if (data == null) return out;
        for (JsonObject d : ArchvowGrammar.objects(data, "decisions")) out.decisions.add(decision(d));
        for (JsonObject q : ArchvowGrammar.objects(data, "safety")) {
            boolean waiting = false;
            for (JsonObject h : ArchvowGrammar.objects(q, "hazards")) {
                Origin o = hazard(h);
                out.hazards.put(s(h, "id"), o);
                waiting = waiting || o.toReview();
            }
            out.safety.add(waiting);
        }
        for (JsonObject r : ArchvowGrammar.objects(data, "rules")) out.rules.put(s(r, "id"), rule(r));
        int decisions = (int) out.decisions.stream().filter(Origin::toReview).count();
        int safety = (int) out.safety.stream().filter(b -> b).count();
        int rules = (int) out.rules.values().stream().filter(Origin::toReview).count();
        int assumptions = (int) ArchvowGrammar.objects(data, "assumptions").stream()
            .filter(a -> s(a, "status").toLowerCase(Locale.ROOT).equals("proposed")).count();
        int component = "ai".equals(s(data, "origin")) ? 1 : 0;
        int total = decisions + safety + rules + assumptions + component;
        out.toReview.put("decisions", decisions);
        out.toReview.put("safety", safety);
        out.toReview.put("rules", rules);
        out.toReview.put("assumptions", assumptions);
        out.toReview.put("component", component);
        out.toReview.put("total", total);
        int open = (int) ArchvowGrammar.objects(data, "decisions").stream()
            .filter(d -> s(d, "answer").isEmpty() && s(d, "proposed").isEmpty()).count();
        if (total > 0) out.blocked.put("implement", new String[] {
            "Not yet: " + total + " item" + plural(total) + " from the AI to review",
            "Pas encore : " + total + " élément" + plural(total) + " de l'IA à relire"});
        else if (open > 0) out.blocked.put("implement", new String[] {
            "Not yet: " + open + " decision" + plural(open) + " still to make",
            "Pas encore : " + open + " décision" + plural(open) + " encore à prendre"});
        if ("planned".equals(s(data, "status")))
            out.blocked.put("review", new String[] {"Nothing to review yet: no code", "Rien à relire encore : pas de code"});
        out.suggest = suggest(data, total, launched == null ? List.of() : launched, open);
        return out;
    }

    /** One suggestion at a time, from the component's state: what the AI added first, then the work in order. */
    private static Suggestion suggest(JsonObject data, int total, List<String> launched, int decisions) {
        if (total > 0) return new Suggestion("to-review", total, new String[] {
            total + " item" + plural(total) + " added by the AI wait" + (total == 1 ? "s" : "") + " for you: keep, change or remove each one.",
            total + " élément" + plural(total) + " ajouté" + plural(total) + " par l'IA " + (total == 1 ? "t'attend" : "t'attendent")
                + " : garder, changer ou retirer chacun."});
        boolean planned = "planned".equals(s(data, "status"));
        if (!planned) return launched.contains("review") ? null : new Suggestion("review", 0, new String[] {
            "The code exists: the AI reviews it against the spec and writes a report.",
            "Le code existe : l'IA le relit par rapport à la spec et écrit un rapport."});
        boolean asked = ArchvowGrammar.objects(data, "decisions").stream().anyMatch(d -> !s(d, "added_by").isEmpty());
        if (!launched.contains("clarify") && !asked) return new Suggestion("clarify", 0, new String[] {
            "No one has looked for what the spec leaves open yet: the AI adds up to 5 questions, you answer them.",
            "Personne n'a encore cherché ce que la spec laisse ouvert : l'IA ajoute jusqu'à 5 questions, tu y réponds."});
        if (decisions > 0) return null;
        int safety = (int) ArchvowGrammar.objects(data, "safety").stream().filter(q -> "open".equals(s(q, "status"))).count();
        if (safety > 0) return launched.contains("analyze") ? null : new Suggestion("analyze", safety, new String[] {
            safety + " safety question" + (safety == 1 ? " is" : "s are") + " open. The AI proposes a hazard and an invariant for each; nothing counts until you keep it.",
            safety + " question" + plural(safety) + " de sûreté " + (safety == 1 ? "est ouverte" : "sont ouvertes")
                + ". L'IA propose un hazard et un invariant pour chacune ; rien ne compte tant que tu ne l'as pas gardé."});
        return new Suggestion("implement", 0, new String[] {
            "Nothing is open: the AI can write the code and the tests.", "Rien n'est ouvert : l'IA peut écrire le code et les tests."});
    }

    private static final Pattern HOLE = Pattern.compile("\\{(\\w+)}");

    /** A self-contained handoff for any assistant, including one without Archvow slash commands. */
    static String request(JsonObject skills, String skill, String tool, Map<String, String> values, boolean french) {
        JsonObject def = skillDefinition(skills, skill);
        String targetName = "brief".equals(s(def, "target")) ? "brief" : "component";
        String target = values.getOrDefault(targetName, "").trim();
        StringBuilder text = new StringBuilder();
        if (french) {
            text.append("Tâche Archvow : ").append(pair(def, "label", true))
                .append("\nSkill, s'il est installé : archvow-").append(skill)
                .append("\nRépertoire de travail : le projet actuellement ouvert dans l'IDE")
                .append("\n").append("brief".equals(targetName) ? "Brief / section : " : "Composant : ")
                .append(target);
            if ("clarify".equals(skill)) text.append("\nNombre maximum de questions : ").append(values.getOrDefault("max", "5"));
            text.append("\n\nObjectif : ").append(pair(def, "does", true))
                .append("\nÀ lire : ").append(pair(def, "reads", true))
                .append("\nModifications ou propositions possibles : ").append(pair(def, "may_add", true))
                .append("\nValidation humaine : ").append(pair(def, "you", true))
                .append("\n\nConsignes :\n")
                .append("1. Lis le brief ou composant ciblé dans le dépôt, ainsi que les spécifications Archvow et les instructions locales pertinentes. Si le chemin contient #, utilise cette section précise.\n")
                .append("2. Si le skill archvow-").append(skill).append(" est disponible, utilise-le. Sinon, réalise l'objectif décrit, sans inventer de commande Archvow.\n")
                .append("3. Respecte le mode Archvow du projet et les validations humaines ; ne présente jamais une proposition comme acceptée.\n")
                .append("4. Si tu ne peux pas lire les fichiers du projet, demande leur contenu avant de travailler ; un chemin seul n'est pas leur contenu.\n")
                .append("5. Résume les changements, propositions et points restant à valider.");
        } else {
            text.append("Archvow task: ").append(pair(def, "label", false))
                .append("\nSkill, if installed: archvow-").append(skill)
                .append("\nWorkspace: the project currently open in the IDE")
                .append("\n").append("brief".equals(targetName) ? "Brief / section: " : "Component: ")
                .append(target);
            if ("clarify".equals(skill)) text.append("\nMaximum questions: ").append(values.getOrDefault("max", "5"));
            text.append("\n\nGoal: ").append(pair(def, "does", false))
                .append("\nRead first: ").append(pair(def, "reads", false))
                .append("\nYou may add or propose: ").append(pair(def, "may_add", false))
                .append("\nHuman approval: ").append(pair(def, "you", false))
                .append("\n\nInstructions:\n")
                .append("1. Read the targeted brief or component in this repository, the Archvow specs and relevant local instructions. If the path includes #, use that specific section.\n")
                .append("2. If the archvow-").append(skill).append(" skill is installed, use it. Otherwise perform the described goal without inventing Archvow commands.\n")
                .append("3. Respect the project's Archvow mode and human approval gates; do not treat a proposal as accepted.\n")
                .append("4. If you cannot access the project files, ask me for their contents before acting; the path alone is not the document.\n")
                .append("5. Summarize changes, proposals and items requiring human review.");
        }
        return text.toString();
    }

    /** Optional shorthand only for agents with this project's installed slash-command skill. */
    static String skillCommand(JsonObject skills, String skill, String tool, Map<String, String> values) {
        JsonObject def = skillDefinition(skills, skill);
        JsonObject req = def == null || !def.has("request") ? null : def.getAsJsonObject("request");
        String raw = s(req, tool);
        if (raw.isEmpty()) return "";
        Matcher m = HOLE.matcher(raw);
        StringBuilder out = new StringBuilder();
        while (m.find()) m.appendReplacement(out, Matcher.quoteReplacement(values.getOrDefault(m.group(1), "")));
        m.appendTail(out);
        return out.toString();
    }

    private static JsonObject skillDefinition(JsonObject skills, String skill) {
        JsonObject all = skills == null ? null : skills.getAsJsonObject("skills");
        return all == null || !all.has(skill) ? null : all.getAsJsonObject(skill);
    }

    private static String pair(JsonObject def, String key, boolean french) {
        JsonElement value = def == null ? null : def.get(key);
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() == 0) return "";
        JsonArray words = value.getAsJsonArray();
        return words.get(french && words.size() > 1 ? 1 : 0).getAsString();
    }

    // ------------------------------------------------------------------ the shape both plugins compare

    static JsonObject toJson(Origin o) {
        JsonObject j = new JsonObject();
        j.addProperty("tag", o.tag());
        j.addProperty("toReview", o.toReview());
        j.addProperty("who", o.who());
        j.addProperty("why", o.why());
        j.addProperty("section", o.section());
        j.addProperty("proposal", o.proposal());
        return j;
    }

    private static JsonArray pair(String[] p) {
        JsonArray a = new JsonArray();
        a.add(new JsonPrimitive(p[0]));
        a.add(new JsonPrimitive(p[1]));
        return a;
    }

    /** The same keys as the VS Code extension's AiView, for ai-skills.expected.json. */
    static JsonObject toJson(View v) {
        JsonObject j = new JsonObject();
        JsonArray decisions = new JsonArray();
        v.decisions.forEach(o -> decisions.add(toJson(o)));
        j.add("decisions", decisions);
        JsonObject hazards = new JsonObject();
        v.hazards.forEach((k, o) -> hazards.add(k, toJson(o)));
        j.add("hazards", hazards);
        JsonArray safety = new JsonArray();
        v.safety.forEach(b -> safety.add(new JsonPrimitive(b)));
        j.add("safety", safety);
        JsonObject rules = new JsonObject();
        v.rules.forEach((k, o) -> rules.add(k, toJson(o)));
        j.add("rules", rules);
        JsonObject counts = new JsonObject();
        v.toReview.forEach(counts::addProperty);
        j.add("toReview", counts);
        if (v.suggest == null) j.add("suggest", com.google.gson.JsonNull.INSTANCE);
        else {
            JsonObject sg = new JsonObject();
            sg.addProperty("skill", v.suggest.skill());
            sg.addProperty("count", v.suggest.count());
            sg.add("reason", pair(v.suggest.reason()));
            j.add("suggest", sg);
        }
        JsonObject blocked = new JsonObject();
        v.blocked.forEach((k, p) -> blocked.add(k, pair(p)));
        j.add("blocked", blocked);
        return j;
    }
}
