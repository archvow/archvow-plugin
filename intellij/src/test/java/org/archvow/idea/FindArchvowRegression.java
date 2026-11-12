package org.archvow.idea;

import java.util.List;

/**
 * Where archvow is looked for and where it runs (0.25.1), without the IDE: the folder's .venv, the IDE's other
 * Pythons, archvow where pip and pipx put it (an IDE started from the Dock does not see the shell's PATH), and
 * init --dir running in that folder.
 */
public final class FindArchvowRegression {
    private static void equal(Object expected, Object actual, String what) {
        if (!expected.equals(actual)) throw new AssertionError(what + ": expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) {
        String python = "/home/me/.venv/bin/python";
        // 0.25.1: then the folder's .venv, the IDE's other Pythons, archvow where pip and pipx put it - only what exists
        java.util.Set<String> there = java.util.Set.of("/work/p/.venv/bin/python", "/Users/me/.local/bin/archvow", "/opt/homebrew/bin/archvow");
        var found = ArchvowProcessCommand.found("/work/p", List.of("/usr/local/bin/python3.12"), "/Users/me", false, there::contains);
        equal(List.of("venv", "sdk", "found", "found"), found.stream().map(ArchvowProcessCommand.Candidate::source).toList(), "found: what exists");
        equal(List.of("ide", "python", "venv", "executable", "sdk", "found", "found"),
            ArchvowProcessCommand.candidates(true, python, "archvow", "/usr/bin/python3", found).stream()
                .map(ArchvowProcessCommand.Candidate::source).toList(), "the order with what was found");
        equal(List.of("ide", "venv", "executable", "found", "found"),
            ArchvowProcessCommand.candidates(true, "/usr/local/bin/python3.12", "", "", found).stream()
                .map(ArchvowProcessCommand.Candidate::source).toList(), "the IDE's Python once");
        // init --dir <an existing folder> runs there; any other command, or a folder to create, in the project
        java.util.function.Predicate<String> dirs = java.util.Set.of("/Users/me/Downloads/test-init")::contains;
        equal("/Users/me/Downloads/test-init", ArchvowProcessCommand.workingFolder(List.of("init", "p", "--dir", "/Users/me/Downloads/test-init"), "/home/p", dirs), "init --dir");
        equal("/home/p", ArchvowProcessCommand.workingFolder(List.of("init", "p", "--dir", "/new/one"), "/home/p", dirs), "a folder init will create");
        equal("/home/p", ArchvowProcessCommand.workingFolder(List.of("check", "--dir", "/Users/me/Downloads/test-init"), "/home/p", dirs), "not init");
        System.out.println("FindArchvowRegression OK");
    }
}
