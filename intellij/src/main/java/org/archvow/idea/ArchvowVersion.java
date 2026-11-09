package org.archvow.idea;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The archvow the plugin is tested with and installs (../contract/cli/version.txt), the contract it speaks
 * (../contract/cli/contract.txt, MAJOR.MINOR) and what to say when the installed archvow does not speak it:
 * archvow 1.23 says its contract (archvow version --json); the same MAJOR and a MINOR at least the plugin's is
 * accepted. The same rules as the VS Code extension (model.ts contractWarning).
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

    /** A sentence when the installed archvow does not speak the contract {@code wanted}, else "". */
    static String warning(Said installed, String wanted, String tested, boolean french) {
        if (installed == null || installed.version().isBlank()) {
            return french ? "archvow n'a pas donné sa version (archvow --version) : est-il installé ?"
                : "archvow did not say its version (archvow --version): is it installed?";
        }
        int[] have = parseContract(installed.contract());
        int[] need = parseContract(wanted);
        if (need == null) need = new int[] {0, 0};
        boolean older = have == null || have[0] < need[0] || (have[0] == need[0] && have[1] < need[1]);
        if (!older && have[0] == need[0]) return "";
        String v = installed.version();
        if (older) {
            return french ? "archvow " + v + " est plus ancien que ce qu'il faut à ce plugin (archvow " + tested + ") : mets archvow à jour."
                : "archvow " + v + " is older than this plugin needs (archvow " + tested + "): update archvow.";
        }
        return french ? "archvow " + v + " est plus récent que ce que ce plugin connaît (contrat " + installed.contract()
                + ", plugin fait pour le contrat " + wanted + ") : mets le plugin à jour."
            : "archvow " + v + " is newer than this plugin knows (contract " + installed.contract() + ", the plugin speaks "
                + wanted + "): update the plugin.";
    }
}
