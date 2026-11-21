package org.archvow.idea;

import java.util.List;

/**
 * After init (0.26.1): another folder (Folder, --dir) opens as the IDE's project; PyCharm Home (PyCharmMiscProject) is
 * never initialized itself, a new project's folder follows its package.
 */
public final class InitTargetRegression {
    private static void equal(Object expected, Object actual, String what) {
        if (!java.util.Objects.equals(expected, actual)) throw new AssertionError(what + ": expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) {
        String home = "/Users/me/PyCharmMiscProject";
        equal("/Users/me/PycharmProjects/sales", ArchvowInitTarget.folderToOpen(List.of("init", "sales", "--dir", "/Users/me/PycharmProjects/sales", "--no-input"), home), "another folder");
        equal("/Users/me/PyCharmMiscProject/sales", ArchvowInitTarget.folderToOpen(List.of("init", "sales", "--dir", "sales"), home), "relative to the project");
        equal(null, ArchvowInitTarget.folderToOpen(List.of("init", "sales", "--dir", "/Users/me/PyCharmMiscProject/"), home), "the project's own folder");
        equal(null, ArchvowInitTarget.folderToOpen(List.of("init", "sales", "--no-input"), home), "no Folder");
        equal(null, ArchvowInitTarget.folderToOpen(List.of("check", "--dir", "/x"), home), "not init");
        equal(true, ArchvowInitTarget.welcomeFolder(home), "PyCharm Home's folder");
        equal(false, ArchvowInitTarget.welcomeFolder("/Users/me/PycharmProjects/sales"), "a project");
        equal("/Users/me/PycharmProjects/sales_pipeline", ArchvowInitTarget.suggestedFolder("/Users/me/PycharmProjects", "sales_pipeline"), "suggested");
        equal("", ArchvowInitTarget.suggestedFolder("/Users/me/PycharmProjects", "not a package"), "no package yet");
        System.out.println("InitTargetRegression OK");
    }
}
