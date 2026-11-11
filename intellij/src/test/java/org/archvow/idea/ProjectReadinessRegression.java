package org.archvow.idea;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ProjectReadinessRegression {
    private static void require(ArchvowProjectReadiness.State expected, String root) {
        ArchvowProjectReadiness.State actual = ArchvowProjectReadiness.inspect(root).state();
        if (actual != expected) throw new AssertionError("Expected " + expected + ", got " + actual + " for " + root);
    }
    public static void main(String[] args) throws Exception {
        require(ArchvowProjectReadiness.State.NO_PROJECT, null);
        require(ArchvowProjectReadiness.State.NO_PROJECT, "");
        Path temp = Files.createTempDirectory("archvow-project-readiness-");
        require(ArchvowProjectReadiness.State.ROOT_MISSING, temp.resolve("missing").toString());
        require(ArchvowProjectReadiness.State.NOT_INITIALIZED, temp.toString());
        Files.writeString(Files.createDirectories(temp.resolve(".archvow")).resolve("spec.vow"), "vow 1 project\n");
        require(ArchvowProjectReadiness.State.NOT_INITIALIZED, temp.toString());  // archvow 1.x: converted first
        Path marker = temp.resolve(".archvow").resolve("project.vow");
        Files.createDirectory(marker);
        require(ArchvowProjectReadiness.State.SPEC_INACCESSIBLE, temp.toString());
        Files.delete(marker);
        Files.writeString(marker, "");
        require(ArchvowProjectReadiness.State.SPEC_INACCESSIBLE, temp.toString());
        Files.writeString(marker, "version: 1\n");
        require(ArchvowProjectReadiness.State.READY, temp.toString());
        Files.delete(marker);
        require(ArchvowProjectReadiness.State.NOT_INITIALIZED, temp.toString());
        Files.delete(temp.resolve(".archvow").resolve("spec.vow"));
        Files.delete(temp.resolve(".archvow"));
        Files.delete(temp);
        System.out.println("OK: 9 readiness scenarios");
    }
}
