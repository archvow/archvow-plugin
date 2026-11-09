package org.archvow.idea;

import java.util.List;

/** ArchvowCli.Result (archvow 1.23): the log lines leave stderr, the error envelope leaves stdout; an exit 0 is never an error. */
public final class CliResultRegression {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        String envelope = "{\"contract\": \"1.0\", \"error\": {\"type\": \"ManifestError\", \"message\": \"no spec\", \"hint\": \"run archvow init\", \"exit\": 2}}";
        String stderr = "archvow 20:12:37.383 info    start show <value> --json\n✖ archvow: error: no spec\narchvow 20:12:37.386 info    done exit 2 (2 ms)\n";
        ArchvowCli.Result result = new ArchvowCli.Result(List.of("show", "--json"), 2, envelope, stderr, false);
        check(result.stdout.isEmpty() && result.error != null && result.error.text().equals("no spec\nrun archvow init"), "the envelope");
        check(result.log.size() == 2 && result.stderr.strip().equals("✖ archvow: error: no spec"), "the log: " + result.stderr);
        check(result.output.strip().equals("✖ archvow: error: no spec"), "output: " + result.output);
        ArchvowCli.Result ok = new ArchvowCli.Result(List.of("show", "--json"), 0, "{\"error\": {\"message\": \"a field\"}}", "warning\n", false);
        check(ok.error == null && !ok.stdout.isEmpty() && ok.stderr.equals("warning\n") && ok.log.isEmpty(), "an exit 0 is never an error");
        System.out.println("OK: a CLI result keeps archvow's log and error envelope apart");
    }
}
