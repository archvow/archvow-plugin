package org.archvow.idea;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * archvow show <name> --json (archvow 1.21), as the VS Code page reads it: the groups of places where a name is
 * (spec, code, tests, examples, briefs, template, problems), each hit with its component, part, text, file and
 * line; a name found nowhere gives its close names. Plain Java, tested headless.
 */
final class ArchvowSearch {
    private ArchvowSearch() { }

    /** One place: its group, and what archvow says of it. */
    record Hit(String group, String file, int line, String component, String part, String text, String what,
               String marks, String severity) {
        /** "file:line" (the file alone for a folder or line 0). */
        String where() { return file + (line > 0 ? ":" + line : ""); }
        /** "part (what) [marks]": the hit's second column. */
        String label() { return part + (what.isBlank() ? "" : " (" + what + ")") + (marks.isBlank() ? "" : " [" + marks + "]"); }
        /** Whether a file of the project can be opened at it (not a built-in template, not "template x"). */
        boolean openable() { return !file.isBlank() && !file.startsWith("template ") && !file.startsWith("archvow/"); }
    }

    static final Map<String, String[]> TITLES = Map.of(
        "spec", new String[] {"Spec", "Spec"}, "code", new String[] {"Code", "Code"}, "tests", new String[] {"Tests", "Tests"},
        "examples", new String[] {"Examples", "Exemples"}, "briefs", new String[] {"Briefs", "Briefs"},
        "template", new String[] {"Template", "Template"}, "problems", new String[] {"Problems", "Problèmes"});

    static String title(String group, boolean french) {
        String[] t = TITLES.get(group);
        return t == null ? group : t[french ? 1 : 0];
    }

    /** The groups, in archvow's order: [group, count]. */
    static List<String[]> groups(JsonObject result) {
        List<String[]> out = new ArrayList<>();
        for (JsonObject g : ArchvowGrammar.objects(result, "groups")) {
            out.add(new String[] {ArchvowGrammar.str(g, "group"), Integer.toString(ArchvowGrammar.objects(g, "hits").size())});
        }
        return out;
    }

    /** Every hit, in its groups' order; {@code only} one group ("" or null: all). */
    static List<Hit> hits(JsonObject result, String only) {
        List<Hit> out = new ArrayList<>();
        for (JsonObject g : ArchvowGrammar.objects(result, "groups")) {
            String group = ArchvowGrammar.str(g, "group");
            if (only != null && !only.isBlank() && !only.equals(group)) continue;
            for (JsonObject h : ArchvowGrammar.objects(g, "hits")) {
                out.add(new Hit(group, ArchvowGrammar.str(h, "file").replaceAll("/$", ""), ArchvowGrammar.number(h, "line"),
                    ArchvowGrammar.str(h, "component"), ArchvowGrammar.str(h, "part"), ArchvowGrammar.str(h, "text"),
                    ArchvowGrammar.str(h, "what"), ArchvowGrammar.str(h, "marks"), ArchvowGrammar.str(h, "severity")));
            }
        }
        return out;
    }

    /** "found in 7 places · a function of clean_orders". */
    static String heading(JsonObject result, boolean french) {
        int total = ArchvowGrammar.number(result, "total");
        String summary = ArchvowGrammar.str(result, "summary");
        String found = french ? "trouvé à " + total + " endroit" + (total == 1 ? "" : "s") : "found in " + total + " place" + (total == 1 ? "" : "s");
        return found + (summary.isBlank() ? "" : " · " + summary);
    }

    /** The close names of a name found nowhere: [name, what]. */
    static List<String[]> close(JsonObject result) {
        List<String[]> out = new ArrayList<>();
        for (JsonObject c : ArchvowGrammar.objects(result, "close")) out.add(new String[] {ArchvowGrammar.str(c, "name"), ArchvowGrammar.str(c, "what")});
        return out;
    }

    /** "'x' is not a component, a file or an item, and is nowhere in the project." */
    static String nowhere(String name, boolean french) {
        return french ? "'" + name + "' n'est ni un composant, ni un fichier, ni un élément, et n'est nulle part dans le projet."
            : "'" + name + "' is not a component, a file or an item, and is nowhere in the project.";
    }

    /** Two lines before the hit, the hit, two after: "  12  text" (none for a folder, a missing file or line 0). */
    /** A pasted error (archvow 2.7.0, contract 2.5): archvow show "<the error>" --json has an "error" object. */
    static boolean isError(JsonObject data) {
        return data != null && data.has("error") && data.get("error").isJsonObject();
    }

    /** Where a pasted error broke, as a hit (group "error"): its file, line and component; null when it names no file
     *  of the project. */
    static Hit errorPlace(JsonObject data) {
        if (!isError(data) || ArchvowGrammar.str(data, "file").isBlank()) return null;
        JsonObject symbol = data.has("symbol") && data.get("symbol").isJsonObject() ? data.getAsJsonObject("symbol") : null;
        return new Hit("error", ArchvowGrammar.str(data, "file"), (int) ArchvowGrammar.number(data, "line"),
            ArchvowGrammar.str(data, "component"), symbol == null ? "" : ArchvowGrammar.str(symbol, "name"), "", "", "", "");
    }

    /**
     * A pasted error's page (2.7.0): what went wrong, the template's known errors it matches - each template rule with
     * its fix and why - then where it broke, with the lines around it, and where its lesson goes once it is fixed.
     */
    static String errorHtml(JsonObject data, List<String[]> around, boolean french) {
        JsonObject error = data.getAsJsonObject("error");
        StringBuilder out = new StringBuilder(ArchvowHtml.body(null, null));
        out.append(ArchvowHtml.heading(ArchvowExamples.esc(ArchvowGrammar.str(error, "error")), 1.1, "margin-top:2px"));
        List<JsonObject> known = new ArrayList<>();
        if (error.has("known") && error.get("known").isJsonArray())
            for (var k : error.getAsJsonArray("known")) if (k.isJsonObject()) known.add(k.getAsJsonObject());
        if (known.isEmpty()) out.append("<p style='color:gray'>").append(french
            ? "Pas une erreur connue du template de ce projet." : "Not a known error of this project's template.").append("</p>");
        for (JsonObject k : known) {
            String color = "warning".equals(ArchvowGrammar.str(k, "severity")) ? "#b0761a" : "#cf222e";
            out.append("<div style='margin:6px 0'><b style='color:").append(color).append("'>")
                .append(ArchvowExamples.esc(ArchvowGrammar.str(k, "code"))).append("</b> ")
                .append(ArchvowExamples.esc(ArchvowGrammar.str(k, "message")))
                .append(" <span style='color:gray'>").append(french ? "(une erreur connue : une règle des templates du projet)"
                    : "(a known error: a rule of the project's templates)").append("</span>");
            if (!ArchvowGrammar.str(k, "fix").isBlank()) out.append("<br>").append(french ? "Correction : " : "Fix: ")
                .append(ArchvowExamples.esc(ArchvowGrammar.str(k, "fix")));
            if (!ArchvowGrammar.str(k, "why").isBlank()) out.append("<br><span style='color:gray'>").append(french ? "Pourquoi : " : "Why: ")
                .append(ArchvowExamples.esc(ArchvowGrammar.str(k, "why"))).append("</span>");
            out.append("</div>");
        }
        Hit place = errorPlace(data);
        if (place == null) {
            out.append("<p style='color:gray'>").append(french
                ? "Aucun fichier de ce projet dedans : quand tu sais où ça a cassé, archvow show &lt;fichier&gt;:&lt;ligne&gt;."
                : "No file of this project in it: once you know where it broke, archvow show &lt;file&gt;:&lt;line&gt;.").append("</p>");
        } else {
            out.append(ArchvowHtml.heading(french ? "Où ça a cassé" : "Where it broke", 1.0, "margin-top:10px"));
            out.append("<div style='font-family:monospace; color:#2e5aac'>").append(ArchvowExamples.esc(place.where())).append("</div>");
            String owner = place.component().isBlank() ? ArchvowGrammar.str(data, "hint")
                : (french ? "code de " : "code of ") + place.component() + (place.part().isBlank() ? "" : ", " + (french ? "dans " : "inside ") + place.part());
            out.append("<p>").append(ArchvowExamples.esc(owner)).append("</p>");
            if (!around.isEmpty()) {
                out.append("<pre>");
                for (String[] line : around) {
                    boolean here = line[0].equals(Integer.toString(place.line()));
                    out.append(here ? "<b>" : "").append(String.format("%4s  ", line[0])).append(ArchvowExamples.esc(line[1])).append(here ? "</b>" : "").append("\n");
                }
                out.append("</pre>");
            }
        }
        out.append("<p style='color:gray; ").append(ArchvowHtml.size(.85)).append("'>").append(french
            ? "Corrigé ? Enregistre la leçon où elle porte : la décision ou la règle du composant, une règle de template avec cette erreur (--error), ou la page de dépannage du template. L'IA le dit à la fin de « Corrige un bug »."
            : "Fixed? Record the lesson where it reaches: the component's decision or rule, a template rule with this error (--error), or the template's troubleshooting page. The AI says which at the end of \"Fix a bug\".")
            .append("</p>");
        return out.append("</body></html>").toString();
    }

    static List<String[]> around(String base, Hit hit) {
        List<String[]> out = new ArrayList<>();
        if (base == null || hit.line() <= 0 || !hit.openable()) return out;
        Path root = Path.of(base).toAbsolutePath().normalize();
        Path file = root.resolve(hit.file()).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) return out;
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return out;
        }
        for (int n = Math.max(1, hit.line() - 2); n <= Math.min(lines.size(), hit.line() + 2); n++) {
            out.add(new String[] {Integer.toString(n), lines.get(n - 1)});
        }
        return out;
    }
}
