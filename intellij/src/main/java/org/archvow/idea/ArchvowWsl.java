package org.archvow.idea;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * archvow installed in WSL, the IDE on Windows. A Linux program cannot be started through its Windows
 * path ({@code \\wsl.localhost\Ubuntu\home\marc\.venv\bin\python3}): Windows sees a file, not a
 * program. It runs through {@code wsl.exe} instead, with the Linux paths:
 * {@code wsl.exe -d Ubuntu --cd /home/marc/proj --exec /home/marc/.venv/bin/python3 -m archvow ...}.
 * Accepted forms of a WSL program: {@code \\wsl.localhost\<distro>\...}, {@code \\wsl$\<distro>\...}
 * (also with /), {@code wsl://<distro>/...}, {@code wsl:///...} and, on Windows, a Linux path
 * {@code /home/...} (the default distribution). The project folder and every argument that is a
 * Windows path are given to archvow as Linux paths ({@code C:\work\p} -> {@code /mnt/c/work/p}).
 * No shell: {@code --exec}. Plain Java: tested headless ({@code WslRegression}); the VS Code extension
 * does the same ({@code src/cli.ts}).
 */
final class ArchvowWsl {
    /** A program or a folder inside WSL: the distribution ("" = the default one) and its Linux path. */
    record Target(String distro, String path) {}

    private static final Pattern UNC = Pattern.compile("(?i)^//wsl(?:\\.localhost|\\$)/([^/]+)(/.*)?$");
    private static final Pattern URI = Pattern.compile("(?i)^wsl://([^/]*)(/.*)?$");
    private static final Pattern DRIVE = Pattern.compile("^([A-Za-z]):[\\\\/](.*)$");

    private ArchvowWsl() { }

    static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    /** The WSL distribution and Linux path of {@code path}, or null when it is not inside WSL. */
    static Target parse(String path) {
        if (path == null) return null;
        String p = path.trim().replace('\\', '/');
        Matcher m = UNC.matcher(p);
        if (m.matches()) return new Target(m.group(1), m.group(2) == null ? "/" : m.group(2));
        m = URI.matcher(p);
        if (m.matches()) return new Target(m.group(1), m.group(2) == null ? "/" : m.group(2));
        return null;
    }

    /** The program to run through wsl.exe on Windows: a WSL path, or a Linux path; else null (a Windows program). */
    static Target program(String program, boolean windows) {
        if (!windows || program == null) return null;
        Target target = parse(program);
        if (target != null) return target;
        String p = program.trim();
        return p.startsWith("/") && !p.startsWith("//") ? new Target("", p) : null;
    }

    /** A Windows path as WSL sees it: \\wsl.localhost\Ubuntu\home\x -> /home/x, C:\x -> /mnt/c/x; null for another. */
    static String linuxPath(String path) {
        if (path == null) return null;
        Target target = parse(path);
        if (target != null) return target.path();
        Matcher m = DRIVE.matcher(path.trim());
        if (m.matches()) {
            String rest = m.group(2).replace('\\', '/');
            while (rest.endsWith("/")) rest = rest.substring(0, rest.length() - 1);
            return "/mnt/" + m.group(1).toLowerCase(Locale.ROOT) + (rest.isEmpty() ? "" : "/" + rest);
        }
        String p = path.trim();
        return p.startsWith("/") && !p.startsWith("//") ? p : null;
    }

    /** An argument for archvow in WSL: a Windows path becomes its Linux path, anything else stays. */
    static String argument(String argument) {
        if (argument == null) return null;
        String a = argument.trim();
        if (DRIVE.matcher(a).matches() || parse(a) != null && (a.startsWith("\\\\") || a.startsWith("//"))) {
            String linux = linuxPath(a);
            return linux == null ? argument : linux;
        }
        return argument;
    }

    /** wsl.exe [-d distro] --cd folder --exec program args... */
    static List<String> command(String distro, String folder, List<String> linuxArgv) {
        List<String> argv = new ArrayList<>();
        argv.add("wsl.exe");
        if (distro != null && !distro.isBlank()) {
            argv.add("-d");
            argv.add(distro);
        }
        argv.add("--cd");
        argv.add(folder == null || folder.isBlank() ? "~" : folder);
        argv.add("--exec");
        argv.addAll(linuxArgv);
        return argv;
    }

    /** Whether a command line runs through wsl.exe. */
    static boolean isWsl(List<String> argv) {
        return argv != null && !argv.isEmpty() && "wsl.exe".equals(argv.get(0));
    }

    /**
     * The WSLENV value that hands NO_COLOR, PYTHONIOENCODING and ARCHVOW_TEMPLATES (a list of folders,
     * translated to Linux paths: /l) to the Linux side, after what {@code previous} already shares.
     */
    static String wslenv(String previous) {
        List<String> names = new ArrayList<>();
        if (previous != null) for (String part : previous.split(":")) if (!part.isBlank()) names.add(part);
        for (String wanted : List.of("NO_COLOR", "PYTHONIOENCODING", "ARCHVOW_TEMPLATES/l")) {
            String name = wanted.split("/")[0];
            if (names.stream().noneMatch(n -> n.split("/")[0].equals(name))) names.add(wanted);
        }
        return String.join(":", names);
    }
}
