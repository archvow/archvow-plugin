package org.archvow.idea;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which archvow this plugin works with (1.0.0): one range, ../contract/archvow-range.txt (">=3.3,<4"), stated in the
 * README, both Marketplace texts and the header of the tool window, and checked against what is installed. The
 * plugin has its own version; the range says which archvow versions it works with. The same as VS Code's compat.ts:
 * both are tested on contract/compat/expected.json (CompatRegression).
 */
final class ArchvowCompat {
    private static final Pattern RANGE = Pattern.compile("\\s*>=\\s*(\\d+(?:\\.\\d+)*)\\s*,\\s*<\\s*(\\d+(?:\\.\\d+)*)\\s*");
    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");
    private static final Pattern CONTRACT = Pattern.compile("\\s*(\\d+)\\.(\\d+)\\s*");
    private static final Pattern PIN = Pattern.compile("(?:^|[\\s\"'\\[,])archvow\\s*==\\s*(\\d+\\.\\d+\\.\\d+)", Pattern.MULTILINE);

    /** A range of archvow versions: from {@code low} included to {@code high} left out. */
    record Range(String text, int[] low, int[] high) { }

    /** The project's pin: the version and the file it is in. */
    record Pin(String version, String file) { }

    /** What the plugin says of the installed archvow: its state, the sentence ("" when ok), the version Install puts
     *  ("" = none: never over a pin outside the range). */
    record Compatibility(String state, String text, String install) { }

    /** The range of this build (archvow/archvow-range.txt, packaged from ../contract). */
    static final Range RANGE_OF_PLUGIN = rangeOf(resource("/archvow/archvow-range.txt"));

    private ArchvowCompat() { }

    static Range parseRange(String text) {
        Matcher m = RANGE.matcher(text == null ? "" : text);
        return m.matches() ? new Range(text.strip(), numbers(m.group(1)), numbers(m.group(2))) : null;
    }

    private static Range rangeOf(String text) {
        Range r = parseRange(text);
        return r != null ? r : new Range(text.strip(), new int[] {0}, new int[] {Integer.MAX_VALUE});
    }

    private static int[] numbers(String dotted) {
        String[] parts = dotted.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Integer.parseInt(parts[i]);
        return out;
    }

    /** -1, 0 or 1: two versions compared number by number, a missing number being 0. */
    static int compare(int[] a, int[] b) {
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int d = (i < a.length ? a[i] : 0) - (i < b.length ? b[i] : 0);
            if (d != 0) return d < 0 ? -1 : 1;
        }
        return 0;
    }

    /** "below", "in", "above" or "unknown": where a version stands against the range. */
    static String place(String version, Range range) {
        Matcher m = VERSION.matcher(version == null ? "" : version);
        if (!m.find()) return "unknown";
        int[] v = {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))};
        if (compare(v, range.low()) < 0) return "below";
        return compare(v, range.high()) < 0 ? "in" : "above";
    }

    /** The archvow a project pins ({@code archvow==2.8.0} in requirements.txt, else pyproject.toml), or null. */
    static Pin projectPin(String root) {
        List<String[]> files = new ArrayList<>();
        for (String name : new String[] {"requirements.txt", "pyproject.toml"}) {
            try {
                files.add(new String[] {name, Files.readString(Path.of(root, name), StandardCharsets.UTF_8)});
            } catch (IOException | RuntimeException none) {
                files.add(new String[] {name, ""});
            }
        }
        return pin(files);
    }

    /** The pin in these files ({name, text}), the first that has one. */
    static Pin pin(List<String[]> files) {
        for (String[] file : files) {
            Matcher m = PIN.matcher(file[1]);
            if (m.find()) return new Pin(m.group(1), file[0]);
        }
        return null;
    }

    /**
     * The installed archvow (null, or a blank version, when it did not answer) against the range, the contract the
     * plugin speaks (the same MAJOR, a MINOR at least the plugin's), the archvow it is tested with and the pin.
     */
    static Compatibility of(ArchvowVersion.Said installed, Range range, String wanted, String tested, Pin pin, boolean fr) {
        boolean pinFits = pin != null && "in".equals(place(pin.version(), range));
        String install = pin == null ? tested : pinFits ? pin.version() : "";
        String pinned = pin == null ? "" : fr
            ? " Ce projet fixe archvow==" + pin.version() + " (" + pin.file() + ")."
            : " This project pins archvow==" + pin.version() + " (" + pin.file() + ").";
        String upgrade = fr
            ? " Ses architectes mettent le projet à jour sur une branche - pip install archvow==" + tested
                + ", puis archvow init <package> --force, relecture et merge (le CHANGELOG d'archvow dit ce qui change) ; le plugin n'installe rien par-dessus le choix du projet."
            : " Its architects upgrade the project on a branch - pip install archvow==" + tested
                + ", then archvow init <package> --force, review and merge (archvow's CHANGELOG says what changes); the plugin installs nothing over the pin.";
        if (installed == null || installed.version().isEmpty()) {
            String what = fr ? "archvow n'est pas installé pour ce projet ; ce plugin fonctionne avec archvow " + range.text() + "."
                : "archvow is not installed for this project; this plugin works with archvow " + range.text() + ".";
            return new Compatibility("missing", what + pinned + (install.isEmpty() ? upgrade : fr
                ? " L'installer : python -m pip install archvow==" + install
                : " Install it: python -m pip install archvow==" + install), install);
        }
        Matcher have = CONTRACT.matcher(installed.contract());
        Matcher need = CONTRACT.matcher(wanted == null ? "" : wanted);
        boolean both = have.matches() & need.matches();
        int contract = both ? compare(new int[] {Integer.parseInt(have.group(1)), Integer.parseInt(have.group(2))},
            new int[] {Integer.parseInt(need.group(1)), Integer.parseInt(need.group(2))}) : -1;
        String where = place(installed.version(), range);
        String v = installed.version();
        if ("above".equals(where) || (both && Integer.parseInt(have.group(1)) > Integer.parseInt(need.group(1)))) {
            return new Compatibility("newer", fr
                ? "archvow " + v + " est plus récent que ce avec quoi ce plugin fonctionne (archvow " + range.text() + ") : mets le plugin à jour."
                : "archvow " + v + " is newer than this plugin works with (archvow " + range.text() + "): update the plugin.", "");
        }
        if (!"in".equals(where) || contract < 0) {
            String what = fr ? "archvow " + v + " est plus ancien que ce avec quoi ce plugin fonctionne (archvow " + range.text() + ")."
                : "archvow " + v + " is older than this plugin works with (archvow " + range.text() + ").";
            return new Compatibility("older", what + pinned + (install.isEmpty() ? upgrade
                : fr ? " Installer archvow " + install + "." : " Install archvow " + install + "."), install);
        }
        return new Compatibility("ok", "", "");
    }

    /** The header of the tool window: "archvow 3.3.0 ✓ · this plugin works with archvow >=3.3,<4". */
    static String header(String version, String state, Range range, boolean fr) {
        String mark = "ok".equals(state) ? "✓" : "✗";
        String what = version == null || version.isEmpty() ? "archvow " + mark : "archvow " + version + " " + mark;
        return what + (fr ? " · ce plugin fonctionne avec archvow " : " · this plugin works with archvow ") + range.text();
    }

    private static String resource(String name) {
        try (InputStream in = ArchvowCompat.class.getResourceAsStream(name)) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
