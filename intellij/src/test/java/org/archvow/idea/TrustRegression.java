package org.archvow.idea;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Archvow starts no process in a project opened in safe mode (0.26.1). Review of 0.26.0: the plugin ran archvow when
 * an archvow project opened - with the project's .venv Python, and `python -m archvow` from the project's root, where
 * an archvow/ folder of the repository ran instead - and never asked whether the project was trusted. Read from the
 * sources, as InstallRegression reads the panel's: each place that starts archvow or pip asks ArchvowTrust first,
 * and every python command line carries -P.
 */
public final class TrustRegression {
    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    /** The text of `method` from its signature to the first `marker` after it. */
    private static String before(String source, String signature, String marker) {
        int start = source.indexOf(signature);
        check(start >= 0, "not found: " + signature);
        int end = source.indexOf(marker, start);
        check(end > start, "not found after " + signature + ": " + marker);
        return source.substring(start, end);
    }

    public static void main(String[] args) throws Exception {
        Path sources = Path.of(System.getProperty("archvow.sources", "src/main/java/org/archvow/idea"));
        if (!Files.isDirectory(sources)) {
            System.out.println("OK (the sources not found from " + Path.of("").toAbsolutePath() + ": not read)");
            return;
        }
        String cli = Files.readString(sources.resolve("ArchvowCli.java"));
        check(before(cli, "private Result invoke(", "new ProcessBuilder(").contains("ArchvowTrust.require(project)"),
            "ArchvowCli.invoke starts archvow before asking whether the project is trusted");
        check(before(cli, "private ArchvowProcessCommand.Candidate choose(", "probe(candidates").length() > 0
            && cli.indexOf("ArchvowTrust.require(project)") < cli.indexOf("Candidate chosen = choose(opts"),
            "the candidates (the project's .venv among them) are tried before the trust check");
        String settings = Files.readString(sources.resolve("ArchvowSettingsConfigurable.java"));
        check(before(settings, "private void testInterpreter(", "ArchvowCli.probe(").contains("ArchvowTrust.trusted(project)"),
            "the settings' Test runs the project's Python before asking whether the project is trusted");
        String panel = Files.readString(sources.resolve("ArchvowPanel.java"));
        check(before(panel, "private void runInstall(", "new ProcessBuilder(job)").contains("ArchvowTrust.trusted(project)"),
            "Install runs pip with the project's Python before asking whether the project is trusted");
        // no other place starts a process (git, in the panel, reads the repository and runs nothing of it)
        try (var files = Files.list(sources)) {
            for (Path file : files.toList()) {
                String text = Files.readString(file);
                Matcher m = Pattern.compile("new ProcessBuilder\\(").matcher(text);
                int count = 0;
                while (m.find()) count++;
                String name = file.getFileName().toString();
                int allowed = switch (name) {
                    case "ArchvowCli.java", "ArchvowPanel.java" -> 2;
                    case "ArchvowSettingsConfigurable.java" -> 1;
                    default -> 0;
                };
                check(count == allowed, name + " starts " + count + " process(es), " + allowed + " reviewed for trust");
            }
        }
        // -P: the project's root, where archvow runs, is never first on sys.path
        check(ArchvowProcessCommand.build("archvow", "/venv/bin/python3", "/work/p", List.of("next"))
            .subList(0, 4).equals(List.of("/venv/bin/python3", "-P", "-m", "archvow")), "python without -P");
        check(ArchvowInstall.pipArgs("2.6.2", ArchvowInstall.source("")).get(0).equals("-P"), "pip without -P");
        System.out.println("OK: trust asked before each process; -P on every python command line");
    }
}
