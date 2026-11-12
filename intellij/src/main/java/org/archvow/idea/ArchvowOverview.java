package org.archvow.idea;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Overview's cards show (0.25.0): the first steps of a new project, the Architecture card's own items and
 * the Checks card's first finding. The same reading as the VS Code extension's overview.ts; both are checked against
 * contract/overview/expected.json (OverviewRegression).
 */
final class ArchvowOverview {
    private ArchvowOverview() {}

    record FirstStep(String step, String title, String detail, String command, boolean done) {}

    /** The steps, how many are done, and the first not done: the one with the action button. */
    record FirstSteps(List<FirstStep> steps, int done, int current) {}

    /** A finding as the Checks card shows it: its rule in words, then "ARCH005 · compute_vat.py:16". */
    record Finding(String title, String code, String severity, String file, int line, String where) {}

    /** archvow's `first_steps` (workspace --json, contract 2.1), or null when the card has nothing left to show. */
    static FirstSteps firstSteps(JsonArray raw) {
        List<FirstStep> steps = new ArrayList<>();
        if (raw != null) for (JsonElement e : raw) {
            if (!e.isJsonObject()) continue;
            JsonObject s = e.getAsJsonObject();
            boolean done = s.has("done") && s.get("done").isJsonPrimitive() && s.get("done").getAsJsonPrimitive().isBoolean()
                && s.get("done").getAsBoolean();
            steps.add(new FirstStep(ArchvowGrammar.str(s, "step"), ArchvowGrammar.str(s, "title"), ArchvowGrammar.str(s, "detail"),
                ArchvowGrammar.str(s, "command"), done));
        }
        int done = (int) steps.stream().filter(FirstStep::done).count();
        if (steps.isEmpty() || done == steps.size()) return null;
        int current = 0;
        while (steps.get(current).done()) current++;
        return new FirstSteps(List.copyOf(steps), done, current);
    }

    /** The first of check's findings, or null. */
    static Finding firstFinding(JsonObject check) {
        List<JsonObject> violations = ArchvowGrammar.objects(check, "violations");
        if (violations.isEmpty()) return null;
        JsonObject v = violations.get(0);
        String file = ArchvowGrammar.str(v, "file");
        int line = ArchvowGrammar.number(v, "line");
        String normal = file.replace('\\', '/');
        String name = normal.substring(normal.lastIndexOf('/') + 1);
        String code = ArchvowGrammar.str(v, "code");
        String title = ArchvowGrammar.str(v, "title");
        return new Finding(title.isBlank() ? code : title, code, ArchvowGrammar.str(v, "severity"), file, line,
            code + (name.isBlank() ? "" : " · " + name + (line > 0 ? ":" + line : "")));
    }

    /** "4 of the 9 decisions" (contract 2.1: `suggested`, what `specify` offers an answer for). As formatter.ts. */
    static String suggested(int n, String readiness, boolean fr) {
        if (n <= 0) return fr ? "aucune" : "none";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+) decisions? to (make|propose)").matcher(readiness);
        int total = m.find() ? Integer.parseInt(m.group(1)) : n;
        String s = total == 1 ? "" : "s";
        return fr ? n + " des " + total + " décision" + s : n + " of the " + total + " decision" + s;
    }

    /** "45 of 70 items done" and the bar's share: the Architecture card's own items, never the check findings. */
    static int percent(int done, int total) {
        return total <= 0 ? 0 : Math.round(done * 100f / total);
    }
}
