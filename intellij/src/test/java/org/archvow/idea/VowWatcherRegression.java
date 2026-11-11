package org.archvow.idea;

/** Which changed files reload the workspace: the project's vows only. */
public final class VowWatcherRegression {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        String base = "/work/orders";
        check(ArchvowVowWatcher.concerns(base, "/work/orders/.archvow/project.vow"), "project vow");
        check(ArchvowVowWatcher.concerns(base + "/", "/work/orders/.archvow/features/vat_report/components/clean_orders.vow"),
            "component vow, base with a slash");
        check(ArchvowVowWatcher.concerns("C:\\work\\orders", "C:/work/orders/.archvow/features/vat_report/proposals.vow"), "Windows paths");
        check(!ArchvowVowWatcher.concerns(base, "/work/orders/.archvow/doc/vat_report.md"), "a doc to read");
        check(!ArchvowVowWatcher.concerns(base, "/work/orders/src/orders/clean_orders.py"), "code");
        check(!ArchvowVowWatcher.concerns(base, "/work/orders-2/.archvow/project.vow"), "another project");
        check(!ArchvowVowWatcher.concerns(null, "/work/orders/.archvow/project.vow"), "no base");
        System.out.println("OK: 7 vow watcher checks");
    }
}
