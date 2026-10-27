package org.archvow.idea;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RuleDocsRegression {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("archvow-docs");
        Path dir = Files.createDirectories(root.resolve(".archvow"));
        Files.writeString(dir.resolve("spec.md"), "# Checks\n\n| Code | Level | Meaning |\n|---|---|---|\n| ARCH019 | warning | Reader lacks a schema |\n| GDPR001 | error | Missing classification |\n", StandardCharsets.UTF_8);
        var match = ArchvowRuleDocs.findProjectReference(root, "ARCH019");
        if (match == null || !match.path().endsWith("spec.md") || match.offset() < 35)
            throw new AssertionError("Project checks reference missing");
        if (ArchvowRuleDocs.findProjectReference(root, "SPK001") != null)
            throw new AssertionError("Unexpected missing rule reference");
        if (!ArchvowRuleDocs.explain("SPK001", true).contains("Spark"))
            throw new AssertionError("Missing bundled FR PySpark description");
        if (!ArchvowRuleDocs.explain("GDPR001", false).toLowerCase().contains("column"))
            throw new AssertionError("Missing bundled EN GDPR description");
        if (!ArchvowRuleDocs.explain("ARCH004", true).contains("signature"))
            throw new AssertionError("Missing bundled FR ARCH004 description");
        if (!ArchvowRuleDocs.canonical("../../etc/passwd").isBlank())
            throw new AssertionError("Unsafe diagnostic code accepted");
        System.out.println("OK: 6 documentation link and language cases");
    }
}
