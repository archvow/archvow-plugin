package org.archvow.idea;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The archvow the plugin is tested with and installs (../contract/cli/version.txt), the contract it speaks
 * (../contract/cli/contract.txt, MAJOR.MINOR), and what an archvow says of itself (archvow version --json). What to
 * say when the installed archvow is not one the plugin works with is ArchvowCompat's (1.0.0).
 */
final class ArchvowVersion {
    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");
    private static final Pattern CONTRACT_NUMBER = Pattern.compile("\\s*(\\d+)\\.(\\d+)\\s*");

    /** "1.23.0": the archvow the plugin is tested with (what Install puts). */
    static final String KNOWN = known();
    /** "1.0": the contract the plugin speaks. */
    static final String CONTRACT = resource("/archvow/contract.txt").strip();

    private ArchvowVersion() { }

    /** What `archvow version --json` said: its version and contract ("" each when it said none). */
    record Said(String version, String contract) { }

    /** This plugin's version (the <version> of META-INF/plugin.xml, set from gradle.properties at build): the session log's header. */
    static String plugin() {
        Matcher m = Pattern.compile("<version>([^<]+)</version>").matcher(resource("/META-INF/plugin.xml"));
        return m.find() ? m.group(1).trim() : "";
    }

    private static String known() {
        int[] v = parse(resource("/archvow/version.txt"));
        return v == null ? "?" : v[0] + "." + v[1] + "." + v[2];
    }

    private static String resource(String name) {
        try (InputStream in = ArchvowVersion.class.getResourceAsStream(name)) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /** {@code archvow 1.14.0} -> {1, 14, 0}; null when the text holds no version. */
    static int[] parse(String text) {
        if (text == null) return null;
        Matcher m = VERSION.matcher(text);
        if (!m.find()) return null;
        return new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))};
    }

    /** "1.23.0" from what --version printed, or "". */
    static String number(String versionOutput) {
        int[] v = parse(versionOutput);
        return v == null ? "" : v[0] + "." + v[1] + "." + v[2];
    }

    /** "1.0" -> {1, 0}; null when it is not a contract number. */
    static int[] parseContract(String text) {
        Matcher m = CONTRACT_NUMBER.matcher(text == null ? "" : text);
        return m.matches() ? new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))} : null;
    }

    /** The version and contract on stdout of `archvow version --json`; empty for an older archvow (no such command). */
    static Said said(String stdout) {
        try {
            com.google.gson.JsonElement e = com.google.gson.JsonParser.parseString(stdout == null ? "" : stdout.strip());
            if (!e.isJsonObject()) return new Said("", "");
            com.google.gson.JsonObject o = e.getAsJsonObject();
            return new Said(o.has("archvow") ? o.get("archvow").getAsString() : "", o.has("contract") ? o.get("contract").getAsString() : "");
        } catch (RuntimeException notJson) {
            return new Said("", "");
        }
    }
}
