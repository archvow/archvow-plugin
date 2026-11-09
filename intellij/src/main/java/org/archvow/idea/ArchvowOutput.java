package org.archvow.idea;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * What archvow printed, read apart (archvow 1.23): its own log lines on stderr (ARCHVOW_LOG=info, for the
 * session log, never shown as its answer) and, with --json, its error as an object on stdout. The same rules
 * as the VS Code extension (cli.ts: splitLog, errorOf, failure). IDE-free: the tests run it without IntelliJ.
 */
final class ArchvowOutput {
    /** A line of archvow's text log: {@code archvow 20:12:37.383 info    start show ...}. */
    private static final Pattern LOG_LINE = Pattern.compile("^archvow \\d\\d:\\d\\d:\\d\\d\\.\\d{3} (debug|info|warning|error|critical)\\b.*");

    private ArchvowOutput() { }

    /** stderr apart: archvow's log lines, and what it said. */
    record Split(List<String> log, String said) { }

    /** archvow's error envelope: {@code {"error": {"type", "message", "hint", "exit"}}}. */
    record Error(String type, String message, String hint, int exit) {
        /** The message, then the hint on its own line when there is one. */
        String text() { return hint.isEmpty() ? message : message + "\n" + hint; }
    }

    /** A log line, and the traceback right after one (debug only: its indented lines and the exception's line). */
    static Split split(String stderr) {
        List<String> log = new ArrayList<>();
        List<String> said = new ArrayList<>();
        boolean traceback = false;
        for (String line : (stderr == null ? "" : stderr).split("\\r?\\n", -1)) {
            if (LOG_LINE.matcher(line).matches()) {
                log.add(line);
                traceback = false;
            } else if (!log.isEmpty() && (line.equals("Traceback (most recent call last):") || (traceback && !line.isEmpty() && Character.isWhitespace(line.charAt(0))))) {
                log.add(line);
                traceback = true;
            } else if (traceback && !line.isBlank()) {
                log.add(line);  // "ValueError: ...": the end of the traceback
                traceback = false;
            } else {
                said.add(line);
                traceback = false;
            }
        }
        return new Split(List.copyOf(log), String.join("\n", said));
    }

    /** The error envelope on stdout, or null (any other output, a list, a broken JSON). */
    static Error error(String stdout) {
        String text = stdout == null ? "" : stdout.strip();
        if (!text.startsWith("{")) return null;
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) return null;
            JsonElement e = parsed.getAsJsonObject().get("error");
            if (e == null || !e.isJsonObject()) return null;
            JsonObject o = e.getAsJsonObject();
            JsonElement message = o.get("message");
            if (message == null || !message.isJsonPrimitive()) return null;
            return new Error(string(o, "type"), message.getAsString(), string(o, "hint"),
                o.has("exit") && o.get("exit").isJsonPrimitive() ? o.get("exit").getAsInt() : 2);
        } catch (RuntimeException notJson) {  // JsonParseException, a number that is not one
            return null;
        }
    }

    /** What a person reads when a command failed: archvow's error and hint, else the last lines of stderr (else stdout). */
    static String failure(String stdout, String stderr, Error error, int lines) {
        if (error != null) return error.text();
        String text = ((stderr == null || stderr.isBlank()) ? (stdout == null ? "" : stdout) : stderr).strip();
        String[] all = text.split("\n");
        return String.join("\n", java.util.Arrays.asList(all).subList(Math.max(0, all.length - lines), all.length));
    }

    private static String string(JsonObject o, String key) {
        JsonElement v = o.get(key);
        return v == null || v.isJsonNull() || !v.isJsonPrimitive() ? "" : v.getAsString();
    }
}
