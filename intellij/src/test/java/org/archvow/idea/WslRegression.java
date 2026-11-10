package org.archvow.idea;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * archvow in WSL with the IDE on Windows (0.11.2): the WSL paths a person or PyCharm gives, the command
 * through wsl.exe with Linux paths, the search order (the IDE's interpreter, the settings' Python, the
 * executable) and the first candidate that answers --version. The wsl.exe of the last check is a stand-in
 * that does what wsl.exe does with -d, --cd and --exec (POSIX systems only).
 */
public final class WslRegression {
    private static void equal(Object expected, Object actual, String name) {
        if (!expected.equals(actual)) throw new AssertionError(name + ": expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) throws Exception {
        // the forms of a WSL path
        equal(new ArchvowWsl.Target("Ubuntu", "/home/marc/.venv/bin/python3"),
            ArchvowWsl.parse("\\\\wsl.localhost\\Ubuntu\\home\\marc\\.venv\\bin\\python3"), "\\\\wsl.localhost");
        equal(new ArchvowWsl.Target("Ubuntu-22.04", "/usr/bin/python3"), ArchvowWsl.parse("\\\\wsl$\\Ubuntu-22.04\\usr\\bin\\python3"), "\\\\wsl$");
        equal(new ArchvowWsl.Target("Ubuntu", "/home/marc/p"), ArchvowWsl.parse("//wsl.localhost/Ubuntu/home/marc/p"), "IntelliJ's //wsl.localhost");
        equal(new ArchvowWsl.Target("Debian", "/opt/py/bin/python"), ArchvowWsl.parse("wsl://Debian/opt/py/bin/python"), "wsl://");
        equal(new ArchvowWsl.Target("", "/usr/bin/python3"), ArchvowWsl.parse("wsl:///usr/bin/python3"), "wsl:/// (default distribution)");
        equal(true, ArchvowWsl.parse("C:\\Python312\\python.exe") == null && ArchvowWsl.parse("/usr/bin/python3") == null, "not WSL paths");
        equal(new ArchvowWsl.Target("", "/home/marc/.venv/bin/python3"), ArchvowWsl.program("/home/marc/.venv/bin/python3", true),
            "a Linux path on Windows: the default distribution");
        equal(true, ArchvowWsl.program("/home/marc/.venv/bin/python3", false) == null, "a Linux path on Linux: itself");
        equal(true, ArchvowWsl.program("C:\\Python312\\python.exe", true) == null, "a Windows Python: itself");

        // Windows folders as WSL sees them
        equal("/home/marc/acoss_pipeline2", ArchvowWsl.linuxPath("//wsl.localhost/Ubuntu/home/marc/acoss_pipeline2"), "UNC folder");
        equal("/mnt/c/work/acoss pipeline2", ArchvowWsl.linuxPath("C:\\work\\acoss pipeline2\\"), "drive folder");
        equal("/mnt/d", ArchvowWsl.linuxPath("D:/"), "drive root");
        equal("/mnt/c/tmp/report.xml", ArchvowWsl.argument("C:\\tmp\\report.xml"), "a Windows path argument");
        equal("Fail: the header is part of the input contract", ArchvowWsl.argument("Fail: the header is part of the input contract"), "text stays");
        equal("--json", ArchvowWsl.argument("--json"), "options stay");

        // the command: wsl.exe -d <distro> --cd <linux root> --exec <linux python> -m archvow --root <linux root> ...
        String python = "\\\\wsl.localhost\\Ubuntu\\home\\marc\\.venv\\bin\\python3";
        String root = "//wsl.localhost/Ubuntu/home/marc/acoss_pipeline2";
        equal(List.of("wsl.exe", "-d", "Ubuntu", "--cd", "/home/marc/acoss_pipeline2", "--exec", "/home/marc/.venv/bin/python3",
                "-m", "archvow", "--root", "/home/marc/acoss_pipeline2", "check", "--json"),
            ArchvowProcessCommand.build("archvow", python, root, List.of("check", "--json"), true), "WSL Python, WSL project");
        equal(List.of("wsl.exe", "-d", "Ubuntu", "--cd", "/mnt/c/work/p", "--exec", "/home/marc/.venv/bin/python3",
                "-m", "archvow", "--root", "/mnt/c/work/p", "check", "--test-report", "/mnt/c/work/p/out.xml"),
            ArchvowProcessCommand.build("archvow", python + " -m archvow", "C:\\work\\p",
                List.of("check", "--test-report", "C:\\work\\p\\out.xml"), true), "WSL Python, Windows project, legacy field");
        equal(List.of("wsl.exe", "-d", "Ubuntu", "--cd", "/home/marc/p", "--exec", "/home/marc/.local/bin/archvow", "--root", "/home/marc/p", "next"),
            ArchvowProcessCommand.build("wsl://Ubuntu/home/marc/.local/bin/archvow", "", "//wsl$/Ubuntu/home/marc/p", List.of("next"), true),
            "an archvow executable in WSL");
        equal(List.of("/venv/bin/python3", "-m", "archvow", "--root", "/work/p", "next"),
            ArchvowProcessCommand.build("archvow", "/venv/bin/python3", "/work/p", List.of("next"), false), "Linux / macOS: unchanged");
        equal("PATH/l:NO_COLOR:PYTHONIOENCODING:ARCHVOW_LOG", ArchvowWsl.wslenv("PATH/l"), "WSLENV keeps what was shared");
        equal("NO_COLOR:PYTHONIOENCODING:ARCHVOW_LOG", ArchvowWsl.wslenv("NO_COLOR"), "WSLENV once each");

        // the search order: the IDE's interpreter, then the settings' Python, then the executable
        var order = ArchvowProcessCommand.candidates(true, python, "archvow", "/usr/bin/python3");
        equal(List.of("ide", "python", "executable"), order.stream().map(ArchvowProcessCommand.Candidate::source).toList(), "order");
        equal(List.of("python", "executable"), ArchvowProcessCommand.candidates(false, python, "", "/usr/bin/python3").stream()
            .map(ArchvowProcessCommand.Candidate::source).toList(), "IDE interpreter not used when unticked");
        equal(List.of("ide", "executable"), ArchvowProcessCommand.candidates(true, "/p", "", "/p").stream()
            .map(ArchvowProcessCommand.Candidate::source).toList(), "the same Python once");
        equal("archvow", ArchvowProcessCommand.candidates(true, "", "", "").get(0).executable(), "nothing set: archvow on the PATH");

        // PyCharm's target-based WSL interpreter: the distribution read from the SDK's data, by reflection
        equal("Ubuntu-22.04", ArchvowInterpreter.wslDistribution(new SdkData()), "the distribution of a WSL target");
        equal("", ArchvowInterpreter.wslDistribution(new Object()), "no target: none");
        equal("", ArchvowInterpreter.wslDistribution(null), "no data: none");

        if (File.separatorChar != '/') { System.out.println("OK: WSL paths and order (process checks skipped on Windows)"); return; }

        // the first candidate that answers --version: the IDE's interpreter has no archvow, the settings' Python has
        Path dir = Files.createTempDirectory("archvow-wsl");
        Path without = script(dir.resolve("python-without"), "echo \"No module named archvow\" >&2; exit 1");
        Path with = script(dir.resolve("python-with"), "for a; do last=$a; done; if [ \"$last\" = --version ]; then echo archvow 1.16.0; else echo \"$@\"; fi");
        ArchvowCli.Probe probe = ArchvowCli.probe(ArchvowProcessCommand.candidates(true, without.toString(), "/nonexistent/archvow",
            with.toString()), dir.toString(), false);
        equal("python", probe.chosen() == null ? "none" : probe.chosen().source(), "the settings' Python answered");
        equal(true, probe.report().contains("No module named archvow"), "what the IDE's interpreter said is kept");
        ArchvowCli.Probe none = ArchvowCli.probe(ArchvowProcessCommand.candidates(true, without.toString(), "/nonexistent/archvow", ""),
            dir.toString(), true);
        equal(true, none.chosen() == null && none.report().startsWith("Archvow introuvable") && none.report().contains("/nonexistent/archvow"),
            "none answered: each one tried, in French: " + none.report());

        // through a stand-in wsl.exe (-d, --cd, --exec as wsl.exe does them): archvow gets the Linux paths, in that folder
        Path wsl = script(dir.resolve("wsl.exe"), "while [ $# -gt 0 ]; do case \"$1\" in -d) shift 2;; --cd) cd \"$2\"; shift 2;; "
            + "--exec) shift; exec \"$@\";; *) exit 9;; esac; done");
        Path project = Files.createDirectories(dir.resolve("proj"));
        List<String> argv = new ArrayList<>(ArchvowProcessCommand.build("archvow", "wsl://Ubuntu" + with, "wsl://Ubuntu" + project,
            List.of("check", "--json"), true));
        argv.set(0, wsl.toString());
        ProcessBuilder builder = new ProcessBuilder(argv).redirectErrorStream(true);
        ArchvowCli.prepare(builder, List.of("wsl.exe"), project.toString());
        Process process = builder.start();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        equal(0, process.waitFor(), "exit");
        equal("-m archvow --root " + project + " check --json", out, "what archvow got");
        equal(true, builder.environment().get("WSLENV").contains("NO_COLOR"), "NO_COLOR handed to WSL");
        System.out.println("OK: WSL paths, the command through wsl.exe, the search order and the first answer");
    }

    /** The shape of PyTargetAwareAdditionalSdkData -> WslTargetEnvironmentConfiguration -> WSLDistribution. */
    public static final class SdkData { public Target getTargetEnvironmentConfiguration() { return new Target(); } }
    public static final class Target { public Distribution getDistribution() { return new Distribution(); } }
    public static final class Distribution { public String getMsId() { return "Ubuntu-22.04"; } }

    private static Path script(Path path, String body) throws Exception {
        Files.writeString(path, "#!/bin/sh\n" + body + "\n");
        if (!path.toFile().setExecutable(true)) throw new AssertionError("chmod " + path);
        return path;
    }
}
