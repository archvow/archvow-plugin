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
