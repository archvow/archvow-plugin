package org.archvow.idea;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A component's business examples (archvow 1.21 `show --json` `examples`), as the VS Code page reads them:
 * each one's state from its test's own verdict (`result`: the test decides, archvow only shows), the
 * examples grouped by the rule they show, and each output file expected/ | actual/ side by side by kind
 * (`files`: a table row by row, a document or a text line by line). Plain Java, tested headless.
 */
final class ArchvowExamples {
    private ArchvowExamples() { }

    /** One example's state: its verdict, its mark, its tone (good, bad, warn, muted) and its words. */
    record State(String result, String mark, String tone, String text) { }

    static State state(JsonObject e, boolean french) {
        String result = ArchvowGrammar.str(e, "result");
        if (result.isBlank()) result = ArchvowGrammar.flag(e, "tested") ? "not_run" : "no_test";
        List<String> differ = new ArrayList<>();
        for (JsonObject f : ArchvowGrammar.objects(e, "files")) {
            String s = ArchvowGrammar.str(f, "state");
            String name = ArchvowGrammar.str(f, "name");
            int n = ArchvowGrammar.number(f, "differences");
            boolean table = isTable(f);
            if (s.equals("missing")) differ.add(french ? name + " absent d'actual/" : name + " missing in actual/");
            else if (s.equals("extra")) differ.add(french ? name + " seulement dans actual/" : name + " only in actual/");
            else if (s.equals("differs")) differ.add(french ? name + " : " + n + " ligne" + (n == 1 ? " diffère" : "s diffèrent")
                : name + ": " + n + " " + (table ? "row" : "line") + (n == 1 ? " differs" : "s differ"));
        }
        String files = String.join("; ", differ);
        return switch (result) {
            case "passing" -> new State(result, "✔", "good", (french ? "réussi" : "passing")
                + (files.isEmpty() ? "" : " · " + (french ? "actual/ ne diffère que par la forme" : "actual/ differs in form only")));
            case "failing" -> new State(result, "✖", "bad", (french ? "en échec" : "failing")
                + (!files.isEmpty() ? " - " + files : e.has("actual") && !ArchvowGrammar.flag(e, "actual")
                    ? " - " + (french ? "actual/ pas écrit par le test" : "actual/ not written by the test") : ""));
            case "no_test" -> new State(result, "○", "warn", french ? "pas encore de test marqué (ARCH056)" : "no marked test yet (ARCH056)");
            case "skipped" -> new State(result, "·", "muted", french ? "sauté dans test-results.xml" : "skipped in test-results.xml");
            default -> ArchvowGrammar.str(e, "run_on").isBlank()
                ? new State(result, "·", "muted", french ? "pas encore lancé (make test écrit test-results.xml)"
                    : "not run yet (make test writes test-results.xml)")
                : new State(result, "·", "muted", french ? "absent du dernier lancement (test-results.xml du " + ArchvowGrammar.str(e, "run_on") + ")"
                    : "not in the last run (test-results.xml of " + ArchvowGrammar.str(e, "run_on") + ")");
        };
    }

    /** [total, passing, failing]: the Examples view's label and the head's badge. */
    static int[] counts(JsonObject view) {
        int total = 0, passing = 0, failing = 0;
        for (JsonObject e : ArchvowGrammar.objects(view, "examples")) {
            total++;
            String r = state(e, false).result();
            if (r.equals("passing")) passing++;
            if (r.equals("failing")) failing++;
        }
        return new int[] {total, passing, failing};
    }

    /** "Examples 4 · 1 ✖", "Examples 2 ✓", "Examples 3": the view's chip, as on the VS Code page. */
    static String label(JsonObject view, boolean french) {
        int[] c = counts(view);
        String name = french ? "Exemples" : "Examples";
        return name + " " + c[0] + (c[2] > 0 ? " · " + c[2] + " ✖" : c[1] == c[0] && c[0] > 0 ? " ✓" : "");
    }

    /** The examples by the rule they show (the scenario's Rule:), in their order; no rule ("") last. */
    static Map<String, List<JsonObject>> groups(JsonObject view) {
        Map<String, List<JsonObject>> named = new LinkedHashMap<>();
        List<JsonObject> none = new ArrayList<>();
        for (JsonObject e : ArchvowGrammar.objects(view, "examples")) {
            String rule = ArchvowGrammar.str(e, "rule");
            if (rule.isBlank()) none.add(e);
            else named.computeIfAbsent(rule, k -> new ArrayList<>()).add(e);
        }
        if (!none.isEmpty()) named.put("", none);
        return named;
    }

    /** The words of a rule an example names (show --json `rule_texts`). */
    static String ruleText(JsonObject view, String rule) {
        JsonElement texts = view == null ? null : view.get("rule_texts");
        return texts != null && texts.isJsonObject() ? ArchvowGrammar.str(texts.getAsJsonObject(), rule) : "";
    }

    /** One example as a card of the view: its mark, its name, its state; its rule as the note. */
    static ArchvowItemCards.Card card(JsonObject view, JsonObject e, boolean french) {
        State s = state(e, french);
        String rule = ArchvowGrammar.str(e, "rule");
        String detail = s.result().equals("passing")
            ? String.join(" · ", nonBlank(ArchvowGrammar.str(e, "level"), ArchvowItemCards.byOn(e))) : s.text();
        String note = rule.isBlank() ? (french ? "aucune règle nommée" : "no rule named")
            : rule + (ruleText(view, rule).isBlank() ? "" : " - " + ruleText(view, rule));
        // the card draws "mark · name", then the state (open: amber "● ...", passing: green), then the rule
        return new ArchvowItemCards.Card(s.mark(), ArchvowGrammar.str(e, "name"), !s.result().equals("passing"),
            detail, detail, note, "");
    }

    /** The examples in the view's order: by rule group, then as recorded. */
    static List<JsonObject> ordered(JsonObject view) {
        List<JsonObject> out = new ArrayList<>();
        for (List<JsonObject> items : groups(view).values()) out.addAll(items);
        return out;
    }

    /** The test of an example, from show --json `evidence` ("example NAME" -> ["file::test"]). */
    static String testFile(JsonObject view, String name) {
        for (JsonObject row : ArchvowGrammar.objects(view, "evidence")) {
            if (!ArchvowGrammar.str(row, "item").equals("example " + name)) continue;
            List<String> tests = ArchvowGrammar.strings(row, "tests");
            if (!tests.isEmpty()) return tests.get(0).split("::")[0];
        }
        return "";
    }

    static boolean isTable(JsonObject f) {
        String kind = ArchvowGrammar.str(f, "kind");
        return kind.equals("table") || kind.equals("records");
    }

    /** One output file as HTML: expected/ | actual/ side by side, + only in actual, − only in expected, ~ changed
     *  (the changed cells marked); links "open expected/" and "open actual/" (href open:<path>). */
    static String fileHtml(JsonObject f, JsonObject e, boolean french) {
        boolean table = isTable(f);
        String state = ArchvowGrammar.str(f, "state");
        String name = ArchvowGrammar.str(f, "name");
        String folder = ArchvowGrammar.str(e, "folder");
        int differences = ArchvowGrammar.number(f, "differences");
        String summary = switch (state) {
            case "no_actual" -> french ? "expected seulement : le test n'a pas encore écrit actual/" : "expected only: the test has not written actual/ yet";
            case "same" -> french ? "identique à expected/" : "same as expected/";
            case "missing" -> french ? "absent d'actual/" : "missing in actual/";
            case "extra" -> french ? "seulement dans actual/" : "only in actual/";
            default -> differences + (french ? " différence" : " difference") + (differences == 1 ? "" : "s");
        };
        StringBuilder out = new StringBuilder();
        out.append("<div style='margin-top:8px'><b><code>").append(esc(name)).append("</code></b> <span style='color:gray'>")
            .append(esc(ArchvowGrammar.str(f, "kind"))).append(" · ").append(esc(summary)).append("</span>");
        if (!state.equals("extra")) out.append(" &nbsp;<a href='open:").append(esc(folder + "/expected/" + name)).append("'>")
            .append(french ? "ouvrir expected/" : "open expected/").append("</a>");
        if (ArchvowGrammar.flag(e, "actual") && !state.equals("missing")) out.append(" &nbsp;<a href='open:")
            .append(esc(folder + "/actual/" + name)).append("'>").append(french ? "ouvrir actual/" : "open actual/").append("</a>");
        out.append("</div>");
        String note = ArchvowGrammar.str(f, "note");
        if (!note.isBlank()) out.append("<div style='color:#b07e24'>").append(esc(note)).append("</div>");
        if (state.equals("no_actual")) return out.toString();
        List<JsonObject> rows = ArchvowGrammar.objects(f, "rows");
        List<String> head = new ArrayList<>();
        JsonElement header = f.get("header");
        if (table && header != null && header.isJsonObject()) {
            JsonObject h = header.getAsJsonObject();
            head = values(h.has("expected") ? h.get("expected") : h.get("actual"));
        }
        int width = Math.max(1, head.size());
        for (JsonObject r : rows) width = Math.max(width, Math.max(values(r.get("expected")).size(), values(r.get("actual")).size()));
        int colspan = table ? width : 1;
        out.append("<table cellspacing='0' cellpadding='3' style='border-collapse:collapse; margin-top:4px; font-family:monospace'>");
        out.append("<tr><th></th><th colspan='").append(colspan).append("' align='left'>expected/ <span style='color:gray'>· ")
            .append(french ? "verrouillé" : "locked").append("</span></th><th width='6'></th><th></th><th colspan='").append(colspan)
            .append("' align='left'>actual/ <span style='color:gray'>· ").append(esc(ArchvowGrammar.flag(e, "actual")
                ? (french ? "dernier lancement" : "last run") : (french ? "pas encore" : "none yet"))).append("</span></th></tr>");
        if (table && !head.isEmpty()) {
            out.append("<tr><td></td>");
            for (int i = 0; i < width; i++) out.append("<td style='color:gray'>").append(i < head.size() ? esc(head.get(i)) : "").append("</td>");
            out.append("<td></td><td></td>");
            for (int i = 0; i < width; i++) out.append("<td style='color:gray'>").append(i < head.size() ? esc(head.get(i)) : "").append("</td>");
            out.append("</tr>");
        }
        for (JsonObject r : rows) {
            String op = ArchvowGrammar.str(r, "op");
            String background = switch (op) {
                case "extra" -> "#f8d7d5"; case "missing" -> "#f3e3bf"; case "changed" -> "#dbe9fb"; default -> "";
            };
            String bg = background.isEmpty() ? "" : " bgcolor='" + background + "'";
            String mark = switch (op) { case "extra" -> "+"; case "missing" -> "−"; case "changed" -> "~"; default -> ""; };
            List<Integer> changed = new ArrayList<>();
            JsonElement cells = r.get("cells");
            if (cells != null && cells.isJsonArray()) for (JsonElement c : cells.getAsJsonArray()) changed.add(c.getAsInt());
            out.append("<tr><td").append(op.equals("extra") ? "" : bg).append("><b>").append(op.equals("extra") ? "" : mark).append("</b></td>");
            appendSide(out, r.get("expected"), table, width, changed, op.equals("extra") ? "" : bg);
            out.append("<td></td><td").append(op.equals("missing") ? "" : bg).append("><b>").append(op.equals("missing") ? "" : mark).append("</b></td>");
            appendSide(out, r.get("actual"), table, width, changed, op.equals("missing") ? "" : bg);
            out.append("</tr>");
        }
        out.append("</table>");
        return out.toString();
    }

    private static void appendSide(StringBuilder out, JsonElement value, boolean table, int width, List<Integer> changed, String bg) {
        if (!table) {
            out.append("<td").append(bg).append(">").append(value == null || value.isJsonNull() ? "" : esc(value.getAsString())).append("</td>");
            return;
        }
        if (value == null || !value.isJsonArray()) {
            out.append("<td colspan='").append(width).append("'></td>");
            return;
        }
        List<String> cells = values(value);
        for (int i = 0; i < width; i++) {
            String cellBg = changed.contains(i) ? " bgcolor='#a9c8f5'" : bg;
            out.append("<td").append(cellBg).append(">").append(changed.contains(i) ? "<b>" : "")
                .append(i < cells.size() ? esc(cells.get(i)) : "").append(changed.contains(i) ? "</b>" : "").append("</td>");
        }
    }

    private static List<String> values(JsonElement element) {
        List<String> out = new ArrayList<>();
        if (element == null || !element.isJsonArray()) return out;
        JsonArray array = element.getAsJsonArray();
        for (JsonElement v : array) out.add(v == null || v.isJsonNull() ? "" : v.getAsString());
        return out;
    }

    /** The selected example as HTML: its state, rule, brief, scenario, then each output file. */
    static String detailHtml(JsonObject view, JsonObject e, boolean french) {
        State s = state(e, french);
        String color = switch (s.tone()) { case "good" -> "#2ea043"; case "bad" -> "#cf222e"; case "warn" -> "#b07e24"; default -> "gray"; };
        StringBuilder out = new StringBuilder(ArchvowHtml.body(null, null));
        String ticket = ArchvowGrammar.str(e, "ticket");
        out.append("<div style='color:gray; ").append(ArchvowHtml.size(.85)).append("'>").append(french ? "EXEMPLE" : "EXAMPLE")
            .append(ticket.isBlank() ? "" : " · " + esc(ticket)).append("</div>");
        out.append(ArchvowHtml.heading(esc(ArchvowGrammar.str(e, "name")), 1.15, "margin-top:2px"));
        out.append("<div><b style='color:").append(color).append("'>").append(esc(s.mark() + " " + s.text())).append("</b></div>");
        String rule = ArchvowGrammar.str(e, "rule");
        List<String> meta = nonBlank(rule.isBlank() ? (french ? "aucune règle nommée" : "no rule named") : (french ? "règle " : "rule ") + rule,
            ArchvowGrammar.str(e, "level"), ArchvowItemCards.byOn(e),
            ArchvowGrammar.str(e, "run_on").isBlank() ? "" : (french ? "dernier lancement " : "last run ") + ArchvowGrammar.str(e, "run_on"));
        out.append("<div style='color:gray'>").append(esc(String.join(" · ", meta))).append("</div>");
        JsonElement source = e.get("source");
        if (source != null && source.isJsonObject()) {
            JsonObject src = source.getAsJsonObject();
            String ref = ArchvowGrammar.str(src, "file") + (ArchvowGrammar.str(src, "section").isBlank() ? "" : "#" + ArchvowGrammar.str(src, "section"));
            out.append("<div style='color:gray'>").append(french ? "Tiré de : " : "Drawn from: ").append("<a href='brief:").append(esc(ref)).append("'>")
                .append(esc(ref)).append("</a></div>");
        }
        if (e.has("unchanged") && !ArchvowGrammar.flag(e, "unchanged")) out.append("<div style='color:#cf222e'>").append(esc(french
            ? "Ses fichiers ont changé depuis leur enregistrement (ARCH055) : les remettre, ou une personne les enregistre à nouveau."
            : "Its files changed since they were recorded (ARCH055): put them back, or a person records them again.")).append("</div>");
        String scenario = ArchvowGrammar.str(e, "scenario");
        if (!scenario.isBlank()) out.append("<pre style='margin:6px 0'>").append(esc(scenario)).append("</pre>");
        for (JsonObject f : ArchvowGrammar.objects(e, "files")) out.append(fileHtml(f, e, french));
        out.append("<div style='color:gray; margin-top:6px; ").append(ArchvowHtml.size(.85)).append("'>")
            .append(french ? "+ seulement dans actual (le code l'a gardé) · − seulement dans expected (le code l'a écarté) · ~ même place, autre valeur"
                : "+ only in actual (the code kept it) · − only in expected (the code dropped it) · ~ same place, another value").append("</div>");
        if (s.result().equals("failing")) out.append("<div style='margin-top:8px; color:#cf222e'><b>")
            .append(french ? "Corrige le code, pas l'exemple." : "Fix the code, not the example.").append("</b></div><div>")
            .append(esc(french ? "L'IA a la même consigne. Si le métier dit que le résultat attendu est faux, une personne remplace les fichiers et les enregistre à nouveau :"
                : "The AI is told the same. If the business team says the expected result is wrong, a person replaces the files and records them again:"))
            .append("</div><pre>").append(esc(command(view, e))).append("</pre>");
        if (s.result().equals("no_test")) out.append("<div style='margin-top:8px; color:#b07e24'><b>")
            .append(french ? "Pas encore de test, donc pas d'actual/." : "No test yet, so no actual/.").append("</b></div><div>")
            .append(esc(french ? "Demande à l'IA : implémente le test de l'exemple " + ArchvowGrammar.str(e, "name") + "."
                : "Ask the AI: implement the test of example " + ArchvowGrammar.str(e, "name") + ".")).append("</div>");
        return out.append("</body></html>").toString();
    }

    /** The command a person runs to record the example again: archvow example add <component> <example>. */
    static String command(JsonObject view, JsonObject e) {
        return "archvow example add " + ArchvowGrammar.str(view, "component") + " " + ArchvowGrammar.str(e, "name");
    }

    private static List<String> nonBlank(String... values) {
        List<String> out = new ArrayList<>();
        for (String v : values) if (v != null && !v.isBlank()) out.add(v);
        return out;
    }

    static String esc(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;");
    }
}
