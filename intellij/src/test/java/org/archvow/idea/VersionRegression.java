package org.archvow.idea;

/** The plugin knows the archvow it is tested with and the contract it speaks, and says when the installed one does not speak it. */
public final class VersionRegression {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static ArchvowVersion.Said said(String version, String contract) {
        return new ArchvowVersion.Said(version, contract);
    }

    public static void main(String[] args) {
        String tested = ArchvowVersion.KNOWN;
        String wanted = ArchvowVersion.CONTRACT;
        check(tested.matches("\\d+\\.\\d+\\.\\d+"), "tested version not read: " + tested);
        check(ArchvowVersion.parseContract(wanted) != null, "contract not read: " + wanted);
        check(ArchvowVersion.parseContract("1.0.0") == null, "a version read as a contract");
        check(ArchvowVersion.said("{\"contract\": \"1.0\", \"archvow\": \"1.23.0\", \"python\": \"3.13.1\"}").equals(said("1.23.0", "1.0")), "version --json");
        check(ArchvowVersion.said("usage: archvow ... invalid choice: 'version'").equals(said("", "")), "an older archvow");
        check(ArchvowVersion.number("archvow 1.22.0\n").equals("1.22.0"), "--version");
        check(ArchvowVersion.warning(said(tested, wanted), wanted, tested, false).isEmpty(), "same contract warned");
        check(ArchvowVersion.warning(said("1.24.3", "1.4"), "1.0", tested, true).isEmpty(), "a later minor contract warned");
        check(ArchvowVersion.warning(said("1.24.0", "1.0"), "1.2", tested, false).contains("older"), "older minor not said");
        String none = ArchvowVersion.warning(said("1.22.0", ""), "1.0", tested, false);
        check(none.contains("1.22.0 is older") && none.contains("archvow " + tested) && none.contains("update archvow"), "no contract: " + none);
        check(ArchvowVersion.warning(said("1.22.0", ""), "1.0", tested, true).contains("plus ancien"), "older not said (FR)");
        check(ArchvowVersion.warning(said("2.0.1", "2.0"), "1.0", tested, false).contains("update the plugin"), "major not said");
        check(ArchvowVersion.warning(said("2.1.0", "2.1"), "1.0", tested, true).contains("plus récent"), "newer not said (FR)");
        check(ArchvowVersion.warning(said("", ""), wanted, tested, false).contains("installed"), "missing not said");
        System.out.println("OK: archvow " + tested + " tested, contract " + wanted + " spoken, other contracts said");
    }
}
