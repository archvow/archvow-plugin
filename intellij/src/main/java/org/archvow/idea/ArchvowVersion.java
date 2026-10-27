package org.archvow.idea;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The archvow version the plugin was built for (its contract, ../contract/cli/version.txt) and what to
 * say when the installed one differs: archvow keeps no backward compatibility, so another minor version
 * may print what the plugin misreads.
 */
final class ArchvowVersion {
    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    /** "1.14": major.minor of the contract. */
    static final String KNOWN = known();

    private ArchvowVersion() { }

    private static String known() {
        try (InputStream in = ArchvowVersion.class.getResourceAsStream("/archvow/version.txt")) {
            if (in == null) return "?";
            int[] v = parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            return v == null ? "?" : v[0] + "." + v[1];
        } catch (IOException e) {
            return "?";
        }
    }

    /** {@code archvow 1.14.0} -> {1, 14, 0}; null when the text holds no version. */
    static int[] parse(String text) {
        if (text == null) return null;
        Matcher m = VERSION.matcher(text);
        if (!m.find()) return null;
        return new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))};
    }

    /** A sentence when {@code archvow --version} printed another major.minor than the contract's, else "". */
    static String warning(String versionOutput, String known, boolean french) {
        int[] v = parse(versionOutput);
        if (v == null) {
            return french ? "archvow n'a pas donné sa version (archvow --version) : est-il installé ?"
                : "archvow did not say its version (archvow --version): is it installed?";
        }
        String installed = v[0] + "." + v[1] + "." + v[2];
        if ((v[0] + "." + v[1]).equals(known)) return "";
        int[] k = parse(known + ".0");
        boolean older = k != null && (v[0] < k[0] || (v[0] == k[0] && v[1] < k[1]));
        if (french) {
            return "archvow " + installed + " est " + (older ? "plus ancien" : "plus récent")
                + " que la version que ce plugin connaît (" + known + ") : "
                + (older ? "mets archvow à jour." : "mets le plugin à jour.");
        }
        return "archvow " + installed + " is " + (older ? "older" : "newer") + " than the version this plugin knows ("
            + known + "): " + (older ? "update archvow." : "update the plugin.");
    }
}
