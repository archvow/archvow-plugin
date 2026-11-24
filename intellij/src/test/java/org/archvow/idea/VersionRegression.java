package org.archvow.idea;

/** The plugin knows the archvow it is tested with, the contract it speaks and the range of archvow versions it works with. */
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
        ArchvowCompat.Range range = ArchvowCompat.RANGE_OF_PLUGIN;
        check("in".equals(ArchvowCompat.place(tested, range)), "the archvow the plugin is tested with is in its range " + range.text());
        check(range.low()[0] == ArchvowVersion.parseContract(wanted)[0], "the range is the contract's MAJOR");
        check("ok".equals(ArchvowCompat.of(said(tested, wanted), range, wanted, tested, null, false).state()), "the tested archvow is ok");
        check(ArchvowCompat.header(tested, "ok", range, false).equals("archvow " + tested + " ✓ · this plugin works with archvow " + range.text()), "header");
        System.out.println("OK: archvow " + tested + " tested, contract " + wanted + " spoken, range " + ArchvowCompat.RANGE_OF_PLUGIN.text());
    }
}
