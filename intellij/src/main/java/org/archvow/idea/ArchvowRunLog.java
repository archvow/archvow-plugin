package org.archvow.idea;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * The session log, per project (any object identifies it: the IntelliJ project): what the plugin asked
 * archvow. A person's action is one line with its whole command, its result and archvow's answer; a
 * refresh's reads are one summary line ("Refresh · 9 reads · 2.4 s"); a failure says what archvow said;
 * a call over a second is marked slow. The same lines go to idea.log (reads at debug level). "Copy for
 * support" gives the session header and the lines, a person's own words hidden on request (0.19.0).
 * No IDE API here (tested by RunLogRegression): the bottom "Archvow Output" tool window that shows these
 * lines is {@link ArchvowRunLogView}, told of each change through {@link #onChange}.
 */
final class ArchvowRunLog {
    static final long SLOW_MS = 1000;
    private static final int MAX_EVENTS = 300;
    /** Where the lines also go: idea.log, set by the tool window (ArchvowPanel); nowhere in the tests. */
    interface Sink {
        void info(String line);
        void debug(String line);
        void warn(String line);
    }
    private static volatile Sink LOG = new Sink() {
        @Override public void info(String line) { }
        @Override public void debug(String line) { }
        @Override public void warn(String line) { }
    };
    static void sink(Sink sink) { LOG = sink; }

    /** Told which project's lines changed (the tool window refreshes its view); nothing in the tests. */
    private static volatile Consumer<Object> changed = project -> { };
    static void onChange(Consumer<Object> listener) { changed = listener; }

    /** One archvow call, as ArchvowCli ran it: archvow's own log lines and its error envelope's text (1.23) too. */
    record Call(List<String> arguments, int exitCode, String stdout, String stderr, boolean interrupted,
                List<String> log, String error) {
        Call {
            arguments = List.copyOf(arguments);
            stdout = stdout == null ? "" : stdout;
            stderr = stderr == null ? "" : stderr;
            log = log == null ? List.of() : List.copyOf(log);
            error = error == null ? "" : error;
        }
        Call(List<String> arguments, int exitCode, String stdout, String stderr, boolean interrupted) {
            this(arguments, exitCode, stdout, stderr, interrupted, List.of(), "");
        }
    }
    private static final Map<Object, Session> SESSIONS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    /** Where each command keeps a person's own words (an answer, a rationale...): hidden for support. */
    private static final Map<String, Integer> OWN_WORDS = Map.of(
        "decision make", 4, "decision propose", 4, "decision add", 4, "question add", 3, "assumption add", 3,
        "hazard add", 5, "hazard dismiss", 4, "invariant add", 3, "rule add", 3);
    private static final List<String> TEXT_OPTIONS = List.of("--answer", "--rationale", "--statement", "--text", "--description", "-d");

    private ArchvowRunLog() { }

    /** One line: shown as it is, and as it is with the person's words hidden. */
    record Line(String time, String text, String hidden) { }

    static final class Session {
        final ArrayDeque<Line> lines = new ArrayDeque<>();
        String header = "";
        String refreshName;
        int reads;
        int failed;
        long refreshStarted;
    }

    private static Session session(Object project) {
        return SESSIONS.computeIfAbsent(project, unused -> new Session());
    }

    /** Once per session: the plugin, archvow, the Python used, the project, the IDE. */
    static void header(Object project, Map<String, String> fields) {
        List<String> parts = new ArrayList<>();
        fields.forEach((k, v) -> { if (v != null && !v.isBlank()) parts.add(k + " " + v); });
        String line = String.join("  ·  ", parts);
        synchronized (SESSIONS) { session(project).header = line; }
        LOG.info("Session: " + line);
        changed.accept(project);
    }

    /** A refresh starts: its reads are counted, and told in one line at the end. */
    static void refreshStarted(Object project, String name) {
        synchronized (SESSIONS) {
            Session s = session(project);
            s.refreshName = name; s.reads = 0; s.failed = 0; s.refreshStarted = System.currentTimeMillis();
        }
    }

    static void refreshEnded(Object project) {
        String line;
        boolean failures;
        synchronized (SESSIONS) {
            Session s = session(project);
            if (s.refreshName == null) return;
            String name = s.refreshName;
            s.refreshName = null;
            if (s.reads == 0) return;
            long ms = System.currentTimeMillis() - s.refreshStarted;
            failures = s.failed > 0;
            line = name + "  ·  " + s.reads + (s.reads > 1 ? " reads" : " read") + "  ·  " + duration(ms)
                + (failures ? "  ·  " + s.failed + " failed" : "");
            keep(s, line, line);
        }
        if (failures) LOG.warn(line); else LOG.info(line);
        changed.accept(project);
    }

    /** One archvow call: {@code action} when a person asked for it, a read otherwise. */
    static void record(Object project, Call r, boolean action, long millis) {
        String call = display(r.arguments()) + "  ·  " + outcome(r) + "  ·  " + duration(millis)
            + (millis > SLOW_MS ? "  ·  slow" : "");
        String hiddenCall = call.replace(display(r.arguments()), display(masked(r.arguments())));
        synchronized (SESSIONS) {
            Session s = session(project);
            if (failed(r)) {
                String said = failureText(r);
                keep(s, "✖ " + call + "  -  " + said, "✖ " + hiddenCall + "  -  " + said);
                if (!action && s.refreshName != null) { s.reads++; s.failed++; }
                LOG.warn("✖ " + hiddenCall + "  -  " + said);
                // what archvow did before it failed (its own log: values already hidden by archvow)
                for (String line : r.log()) {
                    keep(s, "  " + line, "  " + line);
                    LOG.debug(line);
                }
            } else if (action) {
                String said = resultLine(r);
                String tail = said.isEmpty() ? "" : "  -  " + said;
                keep(s, "▶ " + call + tail, "▶ " + hiddenCall + tail);
                LOG.info("▶ " + hiddenCall + tail);
            } else {
                if (s.refreshName != null) s.reads++;
                else if (millis > SLOW_MS) keep(s, call, hiddenCall);
                LOG.debug(hiddenCall);  // a read: told by the refresh's line
                for (String line : r.log()) LOG.debug(line);
            }
        }
        changed.accept(project);
    }

    static String display(List<String> args) {
        List<String> words = new ArrayList<>();
        words.add("archvow");
        for (String a : args) words.add(a.matches("[A-Za-z0-9_@%+=:,./-]+") ? a : '"' + a.replace("\"", "\\\"") + '"');
        return String.join(" ", words);
    }

    static List<String> masked(List<String> args) {
        List<String> out = new ArrayList<>(args);
        Integer at = out.size() > 1 ? OWN_WORDS.get(out.get(0) + " " + out.get(1)) : null;
        if (at != null && out.size() > at) out.set(at, "<text>");
        for (int i = 0; i < out.size() - 1; i++) if (TEXT_OPTIONS.contains(out.get(i))) out.set(i + 1, "<text>");
        return out;
    }

    /** archvow's answer to an action: its last line, when it is text (not JSON). */
    static String resultLine(Call r) {
        String text = r.stdout().strip();
        if (text.isEmpty() || text.startsWith("{") || text.startsWith("[")) return "";
        String last = "";
        for (String l : text.split("\n")) if (!l.isBlank()) last = l.strip();
        return last.length() > 160 ? last.substring(0, 157) + "…" : last;
    }

    /** What archvow said when it failed, on one line: its error and hint (a crash report's path), else the last
     *  lines of stderr (else stdout). The same rule as every failure message (ArchvowOutput.failure). */
    static String failureText(Call r) {
        if (r.interrupted()) return "cancelled, or stopped after the time-out";
        String text = r.error().isEmpty() ? ArchvowOutput.failure(r.stdout(), r.stderr(), null, 3) : r.error();
        List<String> lines = new ArrayList<>();
        for (String l : text.split("\n")) if (!l.isBlank()) lines.add(l.strip());
        return String.join(" | ", lines);
    }

    /** {@code check} exits 1 when it finds errors: an answer, not a failure. */
    static boolean failed(Call r) {
        if (r.interrupted()) return true;
        if (r.exitCode() == 0) return false;
        return !(r.exitCode() == 1 && !r.arguments().isEmpty() && "check".equals(r.arguments().get(0)));
    }

    private static String outcome(Call r) {
        return r.interrupted() ? "cancelled" : r.exitCode() < 0 ? "did not start" : "exit " + r.exitCode();
    }

    static String duration(long ms) {
        return ms < SLOW_MS ? ms + " ms" : String.format(java.util.Locale.ROOT, "%.1f s", ms / 1000.0);
    }

    private static void keep(Session s, String text, String hidden) {
        if (s.lines.size() == MAX_EVENTS) s.lines.removeFirst();
        s.lines.addLast(new Line(LocalDateTime.now().format(TIME), text, hidden));
    }

    /** Empties a project's lines (the view's Clear button); the session header stays. */
    static void clear(Object project) {
        synchronized (SESSIONS) { Session s = SESSIONS.get(project); if (s != null) s.lines.clear(); }
        changed.accept(project);
    }

    static String snapshot(Object project, boolean fr) {
        synchronized (SESSIONS) {
            Session s = SESSIONS.get(project);
            if (s == null || s.lines.isEmpty()) return fr ? "Aucune commande exécutée pendant cette session." : "No commands executed in this session.";
            List<String> out = new ArrayList<>();
            for (Line l : s.lines) out.add(l.time() + "  " + l.text());
            return String.join("\n", out);
        }
    }

    /** The header and the lines with their time: what a person pastes into a support request. */
    static String supportText(Object project, boolean hideOwnWords) {
        synchronized (SESSIONS) {
            Session s = SESSIONS.get(project);
            if (s == null) return "";
            List<String> out = new ArrayList<>();
            if (!s.header.isEmpty()) out.add("Session: " + s.header);
            for (Line l : s.lines) out.add(l.time() + " " + (hideOwnWords ? l.hidden() : l.text()));
            return String.join("\n", out) + "\n";
        }
    }
}
