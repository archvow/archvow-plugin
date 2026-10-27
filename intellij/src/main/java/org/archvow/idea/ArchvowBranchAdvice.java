package org.archvow.idea;

import java.util.Locale;

/** Advice only: branch policies belong to the repository and CI. */
final class ArchvowBranchAdvice {
    private ArchvowBranchAdvice() {}
    static boolean needsFeatureBranch(String name) {
        if (name == null) return false;
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "main", "master", "develop", "development" -> true;
            default -> false;
        };
    }
}
