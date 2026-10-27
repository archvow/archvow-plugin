package org.archvow.idea;

/** The plugin knows the archvow of its contract, and says when the installed one is another. */
public final class VersionRegression {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(ArchvowVersion.KNOWN.matches("\\d+\\.\\d+"), "contract version not read: " + ArchvowVersion.KNOWN);
        String known = ArchvowVersion.KNOWN;
        check(ArchvowVersion.warning("archvow " + known + ".0", known, false).isEmpty(), "same version warned");
        check(ArchvowVersion.warning("archvow " + known + ".7", known, true).isEmpty(), "a patch release warned");
        check(ArchvowVersion.warning("archvow 1.12.0", "1.13", false).contains("older"), "older not said");
        check(ArchvowVersion.warning("archvow 1.14.0", "1.13", true).contains("plus récent"), "newer not said");
        check(ArchvowVersion.warning("archvow 2.0.1", "1.13", false).contains("update the plugin"), "major not said");
        check(ArchvowVersion.warning("command not found", "1.13", false).contains("installed"), "missing not said");
        System.out.println("OK: archvow " + known + " known, other versions said");
    }
}
