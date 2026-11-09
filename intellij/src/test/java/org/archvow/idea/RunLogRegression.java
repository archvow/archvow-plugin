package org.archvow.idea;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 0.19.0, the same as VS Code (vscode/src/test/runlog.test.ts): an action in full with archvow's answer, a
 * refresh in one line, check finding errors is not a failure, a failure says what archvow said, slow calls
 * marked, the support text with a person's own words hidden, and the component an action touches.
 */
public final class RunLogRegression {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static ArchvowRunLog.Call call(List<String> args, int exit, String stdout, String stderr) {
        return new ArchvowRunLog.Call(args, exit, stdout, stderr, false);
    }

    public static void main(String[] args) {
        List<String> idea = new ArrayList<>();
        ArchvowRunLog.sink(new ArchvowRunLog.Sink() {
            @Override public void info(String line) { idea.add("info " + line); }
            @Override public void debug(String line) { idea.add("debug " + line); }
            @Override public void warn(String line) { idea.add("warn " + line); }
        });
        Object project = new Object();
        ArchvowRunLog.header(project, new java.util.LinkedHashMap<>(Map.of("plugin", "0.19.0")));
        ArchvowRunLog.record(project, call(List.of("decision", "make", "settings", "2", "the secret is hunter2"), 0,
            "detail\n✔ Decision 2 of settings made\n", ""), true, 300);
        String log = ArchvowRunLog.snapshot(project, false);
        check(log.contains("▶ archvow decision make settings 2 \"the secret is hunter2\"  ·  exit 0  ·  300 ms  -  ✔ Decision 2 of settings made"), log);

        ArchvowRunLog.refreshStarted(project, "Refresh");
        ArchvowRunLog.record(project, call(List.of("show", "--json"), 0, "{}", ""), false, 100);
        ArchvowRunLog.record(project, call(List.of("check", "--json"), 1, "{\"violations\": []}", ""), false, 100);
        ArchvowRunLog.record(project, call(List.of("show", "nope", "--json"), 2, "", "Traceback\nSpecError: unknown component nope"), false, 100);
        ArchvowRunLog.refreshEnded(project);
        log = ArchvowRunLog.snapshot(project, false);
        check(log.contains("✖ archvow show nope --json  ·  exit 2  ·  100 ms  -  Traceback | SpecError: unknown component nope"), log);
        check(log.contains("Refresh  ·  3 reads  ·  ") && log.contains("·  1 failed"), "one line for the refresh: " + log);
        check(!log.contains("archvow check --json"), "check exiting 1 is an answer, told in the refresh line: " + log);
        check(idea.contains("debug archvow check --json  ·  exit 1  ·  100 ms"), "each read in idea.log at debug level: " + idea);

        ArchvowRunLog.record(project, call(List.of("check"), 0, "", ""), true, 4200);
        check(ArchvowRunLog.snapshot(project, false).contains("exit 0  ·  4.2 s  ·  slow"), "slow call marked");

        String hidden = ArchvowRunLog.supportText(project, true);
        check(hidden.startsWith("Session: plugin 0.19.0\n"), hidden);
        check(hidden.contains("archvow decision make settings 2 \"<text>\"") && !hidden.contains("hunter2"), hidden);
        check(ArchvowRunLog.supportText(project, false).contains("hunter2"), "as it is on request");
        check(ArchvowRunLog.masked(List.of("hazard", "dismiss", "t", "leak", "why")).equals(List.of("hazard", "dismiss", "t", "leak", "<text>")), "masked");

        check("settings".equals(ArchvowGrammar.touchedComponent(List.of("decision", "make", "settings", "1", "x"), Set.of("settings"))), "touched");
        check(ArchvowGrammar.touchedComponent(List.of("decision", "make", "gone", "1", "x"), Set.of("settings")) == null, "unknown component");
        check(ArchvowGrammar.touchedComponent(List.of("component", "remove", "settings"), Set.of("settings")) == null, "component commands");
        System.out.println("OK: run log");
    }
}
