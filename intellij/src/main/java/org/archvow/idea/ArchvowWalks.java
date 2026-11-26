package org.archvow.idea;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The two walks of the workspace (1.1.0): Specify - answer what a person must decide, component after component -
 * and Build - each ready component, its code, its check, the next one. Both read only {@code workspace --json}
 * (archvow does not change): the {@code next} rows give the build order and each component's readiness, the check
 * gives what is still open. The same reading as VS Code's walks.ts; both are checked against
 * contract/walks/expected.json (WalksRegression).
 */
final class ArchvowWalks {
    private ArchvowWalks() {}

    private static final String SHARED = "shared";

    /** One component of a walk: its id and what is still open in it (decisions, questions, safety). */
    record Step(String component, int open) { }

    /**
     * What Copy AI prompt does on a feature's page: {@code ready} (a component to build), {@code specify-first}
     * (strict, nothing ready), {@code propose-first} (guided or free, nothing ready) or {@code built}.
     */
    record PromptState(String state, String component, int open) { }

    /** What is still open in each component, as the check counts it. */
    static Map<String, Integer> openOf(JsonObject w) {
        Map<String, Integer> out = new HashMap<>();
        JsonObject check = w == null || !w.has("check") || !w.get("check").isJsonObject() ? null : w.getAsJsonObject("check");
        for (JsonObject c : ArchvowGrammar.objects(check, "components")) {
            int n = 0;
            try { n = Integer.parseInt(ArchvowGrammar.str(c, "open_questions")); } catch (NumberFormatException ignored) { }
            out.put(ArchvowGrammar.str(c, "component"), n);
        }
        return out;
    }

    /** The ids of a feature's components, in the feature's order. */
    static List<String> membersOf(JsonObject w, String feature) {
        List<String> out = new ArrayList<>();
        if (w == null || !w.has("features") || !w.get("features").isJsonObject()) return out;
        JsonObject features = w.getAsJsonObject("features");
        if (!features.has(feature) || !features.get(feature).isJsonObject()) return out;
        for (JsonObject c : ArchvowGrammar.objects(features.getAsJsonObject(feature), "components")) out.add(ArchvowGrammar.str(c, "id"));
        return out;
    }

    /** The feature a component belongs to ("" when none). */
    static String featureOf(JsonObject w, String id) {
        if (w == null || !w.has("features") || !w.get("features").isJsonObject()) return "";
        for (String name : w.getAsJsonObject("features").keySet()) if (membersOf(w, name).contains(id)) return name;
        return "";
    }

    private static List<JsonObject> rows(JsonObject w) {
        return ArchvowGrammar.objects(w, "next");
    }

    /** {@code ids} in archvow's build order (the next rows), the others after them in their own order. */
    private static List<String> inBuildOrder(JsonObject w, List<String> ids) {
        List<String> out = new ArrayList<>();
        for (JsonObject r : rows(w)) {
            String id = ArchvowGrammar.str(r, "component");
            if (ids.contains(id) && !out.contains(id)) out.add(id);
        }
        for (String id : ids) if (!out.contains(id)) out.add(id);
        return out;
    }

    /** Specify from the foundations ({@code kind} "foundations"), a feature ("feature") or a component ("component"). */
    static List<Step> specifyQueue(JsonObject w, String kind, String value) {
        Map<String, Integer> open = openOf(w);
        List<String> ids;
        if ("foundations".equals(kind)) ids = inBuildOrder(w, membersOf(w, SHARED));
        else if ("feature".equals(kind)) ids = inBuildOrder(w, membersOf(w, value));
        else {
            ids = new ArrayList<>(List.of(value));
            for (String id : inBuildOrder(w, membersOf(w, featureOf(w, value)))) if (!id.equals(value)) ids.add(id);
        }
        List<Step> out = new ArrayList<>();
        for (String id : ids) if (open.getOrDefault(id, 0) > 0) out.add(new Step(id, open.get(id)));
        return out;
    }

    /** The components a Build walk goes through: the feature's ready ones (nothing left to decide), in build order. */
    static List<String> buildQueue(JsonObject w, String feature) {
        List<String> members = membersOf(w, feature);
        List<String> out = new ArrayList<>();
        for (JsonObject r : rows(w)) {
            String id = ArchvowGrammar.str(r, "component");
            if (members.contains(id) && ArchvowGrammar.str(r, "readiness").startsWith("ready")) out.add(id);
        }
        return out;
    }

    /** True when {@code next} says the merge request is full (ARCH022's budget): Build stops there. */
    static boolean budgetReached(JsonObject w) {
        for (JsonObject r : rows(w)) if (ArchvowGrammar.str(r, "readiness").startsWith("next merge request")) return true;
        return false;
    }

    /** All the project's components ready to build (the Overview's "Ready to build: N"). */
    static List<String> readyToBuild(JsonObject w) {
        List<String> out = new ArrayList<>();
        for (JsonObject r : rows(w)) if (ArchvowGrammar.str(r, "readiness").startsWith("ready")) out.add(ArchvowGrammar.str(r, "component"));
        return out;
    }

    /** What Copy AI prompt does on a feature's page, from the readiness and the project's mode. */
    static PromptState promptState(JsonObject w, String feature) {
        List<String> members = membersOf(w, feature);
        Map<String, Integer> open = openOf(w);
        int total = 0;
        for (String id : members) total += open.getOrDefault(id, 0);
        List<String> ready = buildQueue(w, feature);
        if (!ready.isEmpty()) return new PromptState("ready", ready.get(0), total);
        // as `prompt --feature` picks one when none is ready: the feature's first planned component, in its own order
        Map<String, String> status = new LinkedHashMap<>();
        JsonObject check = w == null || !w.has("check") || !w.get("check").isJsonObject() ? null : w.getAsJsonObject("check");
        for (JsonObject c : ArchvowGrammar.objects(check, "components")) status.put(ArchvowGrammar.str(c, "component"), ArchvowGrammar.str(c, "status"));
        String first = "";
        for (String id : members) {
            String s = status.getOrDefault(id, "");
            if (s.isBlank() || "planned".equals(s)) { first = id; break; }
        }
        if (first.isEmpty()) return new PromptState("built", "", total);
        JsonObject project = w.has("project") && w.get("project").isJsonObject() ? w.getAsJsonObject("project") : null;
        String mode = ArchvowGrammar.str(project, "mode");
        if (mode.isBlank() || "strict".equals(mode)) return new PromptState("specify-first", "", total);
        return new PromptState("propose-first", first, total);
    }
}
