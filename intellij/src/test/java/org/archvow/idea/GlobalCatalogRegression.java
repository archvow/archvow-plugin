package org.archvow.idea;

import java.util.List;

/** Archvow catalog is independent of IDE projects and uses the installed CLI. */
public final class GlobalCatalogRegression {
    public static void main(String[] args) {
        List<String> expected = List.of("python3", "-m", "archvow", "catalog", "--json");
        List<String> global = ArchvowProcessCommand.buildWithoutProject(
            "archvow", "python3", List.of("catalog", "--json"));
        if (!expected.equals(global)) throw new AssertionError(global);
        if (global.contains("--root")) throw new AssertionError("Catalog must not have project root");
        List<String> nativeCli = ArchvowProcessCommand.buildWithoutProject(
            "archvow", "", List.of("catalog", "--json"));
        if (!nativeCli.equals(List.of("archvow", "catalog", "--json"))) throw new AssertionError(nativeCli);
        List<String> legacy = ArchvowProcessCommand.buildWithoutProject(
            "python3 -m archvow", "", List.of("catalog", "--json"));
        if (!legacy.equals(expected)) throw new AssertionError(legacy);
        List<String> normal = ArchvowProcessCommand.build("archvow", "python3", "/tmp/work", List.of("check"));
        if (!normal.contains("--root")) throw new AssertionError("Project commands must retain --root");
        System.out.println("OK: project-free catalog CLI and project-bound commands");
    }
}
