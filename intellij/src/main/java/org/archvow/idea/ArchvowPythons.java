package org.archvow.idea;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The Pythons on this computer (0.26.1), for Settings > Tools > Archvow's list: where each is, its version and the
 * archvow it has, as IntelliJ's own interpreter list shows them. Plain Java ({@code PythonsRegression}); the VS Code
 * extension has the same list ({@code src/pythons.ts}).
 */
final class ArchvowPythons {
    private ArchvowPythons() { }

    /** A Python found: its path, where it was found, its version and archvow's ("" when it has none). */
    record Found(String path, String where, String version, String archvow) {
        boolean hasArchvow() { return !archvow.isBlank(); }
        /** archvow 2.x needs Python 3.11 or later. */
        boolean recentEnough() {
            String[] parts = version.split("\\.");
            try { return parts.length >= 2 && (Integer.parseInt(parts[0]) > 3 || (Integer.parseInt(parts[0]) == 3 && Integer.parseInt(parts[1]) >= 11)); }
            catch (NumberFormatException e) { return false; }
        }
    }

    /** Prints "3.12.8|2.6.2" (no archvow: "3.12.8|"). Runs from an empty folder: nothing there can be imported. */
    static final String PROBE = "import sys\n"
        + "try:\n    import importlib.metadata as m\n    v = m.version('archvow')\nexcept Exception:\n    v = ''\n"
        + "print('%d.%d.%d|%s' % (sys.version_info[0], sys.version_info[1], sys.version_info[2], v))";

    /**
     * Where to look, in order, only what exists, each once ({@code canonical}: the same Python by two names is one):
     * the project's interpreter, the project's .venv, the IDE's interpreters, then the usual places of a Python
     * (python.org, Homebrew, the system, pyenv, conda, the PATH). Each: {path, where}.
     */
    static List<String[]> places(String projectPython, String root, List<String> idePythons, String home, boolean windows,
                                 String pathVariable, Function<String, List<String>> children, Predicate<String> exists,
                                 Function<String, String> canonical) {
        Map<String, String[]> out = new LinkedHashMap<>();
        java.util.function.BiConsumer<String, String> add = (path, where) -> {
            if (path == null || path.isBlank()) return;
            String p = path.trim();
            boolean wsl = ArchvowWsl.program(p, windows) != null || ArchvowDevContainer.is(p);
            if (!wsl && !exists.test(p)) return;
            out.putIfAbsent(wsl ? p : canonical.apply(p), new String[] {p, where});
        };
        add.accept(projectPython, "project");
        if (root != null && !root.isBlank()) for (String venv : ArchvowInstall.venvPythons(root, windows)) add.accept(venv, "venv");
        for (String sdk : idePythons) add.accept(sdk, "ide");
        String sep = windows ? "\\" : "/";
        List<String> usual = new ArrayList<>();
        if (windows) {
            String local = System.getenv("LOCALAPPDATA");
            if (local != null) for (String dir : sorted(children.apply(local + "\\Programs\\Python")))
                usual.add(local + "\\Programs\\Python\\" + dir + "\\python.exe");
            for (String dir : sorted(children.apply("C:\\"))) if (dir.matches("(?i)python3\\d*")) usual.add("C:\\" + dir + "\\python.exe");
        } else {
            for (String dir : List.of("/opt/homebrew/bin", "/usr/local/bin", "/usr/bin")) {
                usual.add(dir + "/python3");
                for (int minor = 15; minor >= 9; minor--) usual.add(dir + "/python3." + minor);
            }
            if (home != null && !home.isBlank()) {
                for (String v : sorted(children.apply(home + "/.pyenv/versions"))) usual.add(home + "/.pyenv/versions/" + v + "/bin/python");
                for (String conda : List.of("miniconda3", "anaconda3", "miniforge3")) usual.add(home + "/" + conda + "/bin/python");
            }
            usual.add("/opt/anaconda3/bin/python");
        }
        for (String u : usual) add.accept(u, "system");
        if (pathVariable != null)
            for (String dir : pathVariable.split(windows ? ";" : ":"))
                if (!dir.isBlank()) for (String name : windows ? List.of("python.exe") : List.of("python3", "python"))
                    add.accept(dir + sep + name, "path");
        return new ArrayList<>(out.values());
    }

    private static List<String> sorted(List<String> names) {
        List<String> copy = new ArrayList<>(names == null ? List.of() : names);
        copy.sort(Comparator.reverseOrder());  // the newest version first
        return copy;
    }

    /** What {@link #PROBE} printed, read; null when it did not answer as a Python does. */
    static Found parse(String path, String where, String output) {
        if (output == null) return null;
        for (String line : output.strip().lines().toList()) {
            int bar = line.indexOf('|');
            if (bar > 0 && line.substring(0, bar).matches("\\d+\\.\\d+\\.\\d+"))
                return new Found(path, where, line.substring(0, bar), line.substring(bar + 1).strip());
        }
        return null;
    }

    /** The ones with archvow first, then the others; the order of {@link #places} kept within each. */
    static List<Found> ordered(List<Found> found) {
        List<Found> out = new ArrayList<>(found.stream().filter(Found::hasArchvow).toList());
        out.addAll(found.stream().filter(f -> !f.hasArchvow()).toList());
        return out;
    }

    /** One line for the list: "Python 3.12.8 · archvow 2.6.2 — /usr/local/bin/python3.12 (the project's interpreter)". */
    static String label(Found f, boolean fr) {
        String archvow = f.hasArchvow() ? "archvow " + f.archvow()
            : f.version.isBlank() ? (fr ? "non vérifié" : "not checked")
            : !f.recentEnough() ? (fr ? "trop ancien pour archvow (3.11+)" : "too old for archvow (3.11+)")
            : (fr ? "sans archvow" : "no archvow");
        return (f.version.isBlank() ? "Python" : "Python " + f.version) + " · " + archvow + " — " + f.path + " (" + where(f.where, fr) + ")";
    }

    static String where(String where, boolean fr) {
        return switch (where) {
            case "project" -> fr ? "interpréteur du projet" : "the project's interpreter";
            case "venv" -> fr ? ".venv du projet" : "the project's .venv";
            case "ide" -> fr ? "interpréteur de l'IDE" : "an interpreter of the IDE";
            case "path" -> fr ? "dans le PATH" : "on the PATH";
            default -> fr ? "installé sur l'ordinateur" : "installed on this computer";
        };
    }

    /** Runs {@link #PROBE} with each Python (5 s each) and reads them; a Python in WSL is listed, not run. */
    static List<Found> probe(List<String[]> places, boolean windows) {
        List<Found> out = new ArrayList<>();
        File empty;
        try { empty = Files.createTempDirectory("archvow-python").toFile(); }
        catch (java.io.IOException e) { return out; }
        try {
            for (String[] place : places) {
                if (ArchvowWsl.program(place[0], windows) != null || ArchvowDevContainer.is(place[0])) { out.add(new Found(place[0], place[1], "", "")); continue; }
                try {
                    Process process = new ProcessBuilder(place[0], "-c", PROBE).directory(empty).redirectErrorStream(true).start();
                    if (!process.waitFor(5, TimeUnit.SECONDS)) { process.destroyForcibly(); continue; }
                    Found f = parse(place[0], place[1], new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                    if (f != null) out.add(f);
                } catch (java.io.IOException e) {
                    // not a Python after all: not listed
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            empty.delete();
        }
        return ordered(out);
    }

    /** {@link #places} on this computer. */
    static List<String[]> placesHere(String projectPython, String root, List<String> idePythons) {
        boolean windows = ArchvowWsl.windows();
        return places(projectPython, root, idePythons, System.getProperty("user.home", ""), windows, System.getenv("PATH"),
            dir -> { String[] names = new File(dir).list(); return names == null ? List.of() : List.of(names); },
            path -> new File(path).canExecute() && new File(path).isFile(),
            ArchvowPythons::sameKey);
    }

    /** The same Python by two names is one ({@code python3} and {@code python3.12}); a venv's Python is its own, even
     *  as a link to the Python it was made from (its pyvenv.cfg makes it another environment). */
    static String sameKey(String path) {
        File file = new File(path);
        File env = file.getParentFile() == null ? null : file.getParentFile().getParentFile();
        if (env != null && new File(env, "pyvenv.cfg").isFile()) return file.getAbsolutePath();
        try { return file.getCanonicalPath(); } catch (java.io.IOException e) { return path; }
    }
}
