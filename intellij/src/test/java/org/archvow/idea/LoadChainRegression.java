package org.archvow.idea;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 0.17.0: ArchvowCli runs one archvow command at a time and refuses a second one while the first runs. The
 * project's load (show, check, next, catalog, every component's show, brief show) is therefore one chain: each
 * command starts in the callback of the one before. 0.16.0 started `brief show` beside `check`: `check` was
 * refused, and next, the catalog and the components' `show` never ran - no counts in the tree, every component
 * "resolved", Architecture "0". This reads the load method and requires every command to sit deeper than the
 * one before it (inside its callback), never beside it.
 */
public final class LoadChainRegression {
    public static void main(String[] args) throws Exception {
        String panel = Files.readString(Path.of("src/main/java/org/archvow/idea/ArchvowPanel.java"), StandardCharsets.UTF_8);
        int start = panel.indexOf("cli.execute(\"Architecture\", List.of(\"show\", \"--json\")");
        if (start < 0) throw new AssertionError("the project's load (show --json) is not found");
        int end = endOfCall(panel, start);
        String chain = panel.substring(start, end);
        List<String> labels = new ArrayList<>();
        List<Integer> depths = new ArrayList<>();
        int depth = 0;
        for (int i = 0; i < chain.length(); i++) {
            char c = chain.charAt(i);
            if (chain.startsWith("cli.execute(\"", i) || chain.startsWith("fetchComponentSnapshots(", i)) {
                int q = chain.indexOf('(', i) + 1;
                labels.add(chain.substring(q, Math.min(chain.length(), q + 24)).split(",")[0]);
                depths.add(depth);
            }
            if (c == '"') {  // a string literal: its brackets are words, not code
                for (i++; i < chain.length() && chain.charAt(i) != '"'; i++) if (chain.charAt(i) == '\\') i++;
            } else if (c == '{' || c == '(') depth++;
            else if (c == '}' || c == ')') depth--;
        }
        for (String needed : List.of("\"Architecture\"", "\"Check\"", "\"Next\"", "\"Catalog\"", "listComponentIds()", "\"Briefs\""))
            if (!labels.contains(needed)) throw new AssertionError("the load chain has no " + needed + ": " + labels);
        for (int k = 1; k < depths.size(); k++)
            if (depths.get(k) <= depths.get(k - 1))
                throw new AssertionError(labels.get(k) + " starts beside " + labels.get(k - 1)
                    + ", not in its callback: ArchvowCli refuses it while the other one runs (" + labels + ")");
        System.out.println("OK: the project's load is one chain " + labels);
    }

    /** The end of the call that starts at `from` (its parenthesis closed). */
    private static int endOfCall(String text, int from) {
        int depth = 0;
        for (int i = text.indexOf('(', from); i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return i + 1;
        }
        throw new AssertionError("unbalanced load call");
    }
}
