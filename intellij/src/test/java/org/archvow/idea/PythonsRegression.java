package org.archvow.idea;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Pythons of Settings > Tools > Archvow (0.26.1), without the IDE: where they are looked for, each once, what
 * the probe prints read back, the ones with archvow first, and each line of the list in one language.
 */
public final class PythonsRegression {
    private static void equal(Object expected, Object actual, String what) {
        if (!java.util.Objects.equals(expected, actual)) throw new AssertionError(what + ": expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) {
        Set<String> there = Set.of("/usr/local/bin/python3", "/usr/local/bin/python3.12", "/usr/bin/python3",
            "/Users/me/.pyenv/versions/3.11.9/bin/python", "/work/p/.venv/bin/python", "/opt/homebrew/bin/python3.12",
            "/Users/me/PycharmProjects/CryptoApp/venv/bin/python");
        Map<String, List<String>> dirs = Map.of("/Users/me/.pyenv/versions", List.of("3.11.9"));
        // python3 and python3.12 are the same Python: listed once, by the first name met
        Map<String, String> same = Map.of("/usr/local/bin/python3.12", "/usr/local/bin/python3");
        List<String[]> places = ArchvowPythons.places("/usr/local/bin/python3.12", "/work/p",
            List.of("/Users/me/PycharmProjects/CryptoApp/venv/bin/python", "/usr/bin/python3"), "/Users/me", false,
            "/usr/bin:/opt/homebrew/bin", d -> dirs.getOrDefault(d, List.of()), there::contains, p -> same.getOrDefault(p, p));
        equal(List.of("/usr/local/bin/python3.12 project", "/work/p/.venv/bin/python venv",
                "/Users/me/PycharmProjects/CryptoApp/venv/bin/python ide", "/usr/bin/python3 ide",
                "/opt/homebrew/bin/python3.12 system", "/Users/me/.pyenv/versions/3.11.9/bin/python system"),
            places.stream().map(p -> p[0] + " " + p[1]).toList(), "where, in order, each once");

        ArchvowPythons.Found with = ArchvowPythons.parse("/usr/local/bin/python3.12", "project", "3.12.8|2.6.2\n");
        ArchvowPythons.Found without = ArchvowPythons.parse("/usr/bin/python3", "ide", "3.9.6|");
        equal("2.6.2", with.archvow(), "archvow's version");
        equal(false, without.hasArchvow(), "no archvow");
        equal(false, without.recentEnough(), "3.9 is too old for archvow");
        equal(null, ArchvowPythons.parse("/x", "path", "Python was not found; run without arguments to install"), "not a Python");
        equal(List.of("/usr/local/bin/python3.12"), ArchvowPythons.ordered(List.of(without, with)).subList(0, 1).stream()
            .map(ArchvowPythons.Found::path).toList(), "the ones with archvow first");
        equal("Python 3.12.8 · archvow 2.6.2 — /usr/local/bin/python3.12 (the project's interpreter)", ArchvowPythons.label(with, false), "label");
        equal("Python 3.9.6 · trop ancien pour archvow (3.11+) — /usr/bin/python3 (interpréteur de l'IDE)", ArchvowPythons.label(without, true), "label fr");
        System.out.println("PythonsRegression OK");
    }
}
