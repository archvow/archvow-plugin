package org.archvow.idea;

import java.util.List;

/** archvow 1.23's output read apart: its log lines on stderr, its error envelope on stdout, one failure message. */
public final class OutputRegression {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        String stderr = "archvow 20:12:37.383 info    start show <value> --json (archvow 1.23.0)\n"
            + "✖ archvow: error: no .archvow/spec.vow found in /tmp/p or its parents\n"
            + "archvow 20:12:37.385 debug   swallowed: OSError\n"
            + "Traceback (most recent call last):\n"
            + "  File \"x.py\", line 1, in f\n"
            + "OSError: gone\n"
            + "archvow 20:12:37.386 info    done exit 2 (2 ms)\n";
        ArchvowOutput.Split split = ArchvowOutput.split(stderr);
        check(split.log().size() == 6, "log lines: " + split.log());
        check(split.said().strip().equals("✖ archvow: error: no .archvow/spec.vow found in /tmp/p or its parents"), "said: " + split.said());
        check(ArchvowOutput.split("a warning\n").log().isEmpty(), "a plain stderr read as a log");
        check(ArchvowOutput.split("Traceback (most recent call last):\n  File x\nValueError\n").log().isEmpty(), "a crash without a log line read as a log");

        String envelope = "{\"contract\": \"1.0\", \"error\": {\"type\": \"ManifestError\", \"message\": \"no spec\", \"hint\": \"run archvow init\", \"exit\": 2}}";
        ArchvowOutput.Error error = ArchvowOutput.error(envelope);
        check(error != null && error.type().equals("ManifestError") && error.exit() == 2, "envelope: " + error);
        check(error.text().equals("no spec\nrun archvow init"), "envelope text: " + error.text());
        check(ArchvowOutput.error("{\"contract\": \"1.0\", \"error\": {\"type\": \"X\", \"message\": \"m\", \"hint\": null, \"exit\": 2}}").text().equals("m"), "a null hint");
        check(ArchvowOutput.error("{\"violations\": []}") == null && ArchvowOutput.error("[1]") == null && ArchvowOutput.error("{broken") == null, "not an envelope");

        check(ArchvowForms.refusal(2, "", error).equals("no spec\nrun archvow init"), "refusal from the envelope");
        check(ArchvowForms.refusal(2, "✖ archvow: error: bad\n", null).equals("bad"), "refusal from stderr");
        check(ArchvowForms.refusal(1, "", error).isEmpty(), "a refusal without exit 2");
        ArchvowRunLog.Call call = new ArchvowRunLog.Call(List.of("show", "--json"), 2, "", split.said(), false, split.log(), error.text());
        check(ArchvowRunLog.failureText(call).equals("no spec | run archvow init"), "failure text: " + ArchvowRunLog.failureText(call));
        check(ArchvowRunLog.failureText(new ArchvowRunLog.Call(List.of("x"), 2, "", "a\nb\nc\nd\n", false)).equals("b | c | d"), "the last lines");
        System.out.println("OK: archvow's log lines and error envelope read apart");
    }
}
