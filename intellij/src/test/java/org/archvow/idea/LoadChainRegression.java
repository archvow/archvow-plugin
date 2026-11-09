package org.archvow.idea;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ArchvowCli runs one archvow command at a time and refuses a second one while the first runs. 0.16.0 started
 * `brief show` beside `check`: `check` was refused, and next, the catalog and the components' `show` never ran - no
 * counts in the tree, every component "resolved", Architecture "0"; 0.17.0 chained the six reads. 0.21.0 reads the
 * project with one command, `archvow workspace --json` (archvow 1.25): this requires the load to be that one call,
 * with no other archvow command started inside it, and the old chain gone.
 */
public final class LoadChainRegression {
    public static void main(String[] args) throws Exception {
        String panel = Files.readString(Path.of("src/main/java/org/archvow/idea/ArchvowPanel.java"), StandardCharsets.UTF_8);
        int start = panel.indexOf("cli.execute(\"Workspace\", arguments");
        if (start < 0) throw new AssertionError("the project's load (workspace --json) is not found");
        if (!panel.contains("new ArrayList<>(List.of(\"workspace\", \"--json\"))"))
            throw new AssertionError("the load does not run `archvow workspace --json`");
        String load = panel.substring(start + 1, endOfCall(panel, start));
        if (load.contains("cli.execute(") || load.contains("catalogCli.execute("))
            throw new AssertionError("the load starts another archvow command: ArchvowCli refuses it while the load runs");
        for (String old : new String[] {"cli.execute(\"Architecture\"", "fetchComponentSnapshots(", "cli.execute(\"Briefs\""})
            if (panel.contains(old)) throw new AssertionError("the old load chain is back: " + old);
        System.out.println("OK: the project's load is one `archvow workspace --json`");
    }

    /** The end of the call that starts at `from` (its parenthesis closed); string literals skipped. */
    private static int endOfCall(String text, int from) {
        int depth = 0;
        for (int i = text.indexOf('(', from); i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                for (i++; i < text.length() && text.charAt(i) != '"'; i++) if (text.charAt(i) == '\\') i++;
            } else if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return i + 1;
        }
        throw new AssertionError("unbalanced load call");
    }
}
