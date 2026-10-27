package org.archvow.idea;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Runs without IntelliJ; validates argv handling and launches a synthetic Python module. */
public final class LaunchCommandRegression {
    static void equal(Object expected, Object actual, String name) {
        if (!expected.equals(actual))
            throw new AssertionError(name + ": expected=" + expected + ", got=" + actual);
    }

    static void assertThrows(Runnable fn) {
        try { fn.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    static void runPython(String python, String root, String expectedArguments) throws Exception {
        List<String> cmd = ArchvowProcessCommand.build("archvow", python + " -m archvow", root,
            List.of("check", "--json"));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(new File(root)).redirectErrorStream(true);
        pb.environment().put("PYTHONPATH", root);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0)
            throw new AssertionError("Python integration failed: " + output);
        equal(expectedArguments, output.strip(), "Python --root integration");
    }

    /** A folder holding a fake `archvow` module that prints the arguments it was given. */
    static String syntheticArchvow() throws Exception {
        java.nio.file.Path root = java.nio.file.Files.createTempDirectory("archvow-launch");
        java.nio.file.Path module = java.nio.file.Files.createDirectories(root.resolve("archvow"));
        java.nio.file.Files.writeString(module.resolve("__init__.py"), "");
        java.nio.file.Files.writeString(module.resolve("__main__.py"), "import sys\nprint(sys.argv[1:])\n");
        return root.toString();
    }

    public static void main(String[] args) throws Exception {
        // Arguments: <python> <root with an archvow/__main__.py>; none: python3 and a synthetic module.
        String python = args.length > 0 ? args[0] : "python3";
        String root = args.length > 1 ? args[1] : syntheticArchvow();
        List<String> expected = List.of("python3", "-m", "archvow", "--root", root, "check", "--json");
        equal(expected, ArchvowProcessCommand.build("archvow", "python3 -m archvow", root,
            List.of("check", "--json")), "legacy Python field");
        equal(expected, ArchvowProcessCommand.build("python3 -m archvow", "", root,
            List.of("check", "--json")), "legacy Executable field");
        equal(expected, ArchvowProcessCommand.build("archvow", "'python3' -m archvow", root,
            List.of("check", "--json")), "quoted executable");
        equal(List.of("archvow", "--root", root, "check"),
            ArchvowProcessCommand.build("archvow", "", root, List.of("check")), "native executable");
        equal(List.of("/tmp/My Venv/bin/python3", "-m", "archvow", "--root", root),
            ArchvowProcessCommand.build("archvow", "/tmp/My Venv/bin/python3", root, List.of()),
            "unquoted executable path with spaces");
        assertThrows(() -> ArchvowProcessCommand.build("archvow", "python3 -m another", root, List.of()));
        assertThrows(() -> ArchvowProcessCommand.build("", "", root, List.of()));
        runPython(python, root, "['--root', '" + root + "', 'check', '--json']");
        System.out.println("OK: 8 launcher tests (incl. Python execution)");
    }
}
