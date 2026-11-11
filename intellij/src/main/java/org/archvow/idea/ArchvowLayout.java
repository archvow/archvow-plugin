package org.archvow.idea;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Where archvow 2.0 keeps a project's spec (contract 2.0), as VS Code's layout.ts says it. Plain Java, tested headless:
 * <pre>
 *   .archvow/project.vow                                  the project
 *   .archvow/features/&lt;feature&gt;/&lt;feature&gt;.vow             a feature: what the product does
 *   .archvow/features/&lt;feature&gt;/components/&lt;id&gt;.vow       a component
 *   .archvow/features/&lt;feature&gt;/proposals.vow             the AI's proposals for the feature's components
 *   .archvow/doc/                                         archvow doc writes it (ignored by Git)
 * </pre>
 */
final class ArchvowLayout {
    static final String PROJECT_VOW = ".archvow/project.vow";

    private ArchvowLayout() { }

    /** The vow of a component in {@code root}, wherever its feature is; null when it has none. */
    static Path componentVow(Path root, String id) {
        if (root == null || id == null || id.isBlank()) return null;
        Path features = root.resolve(".archvow").resolve("features");
        if (!Files.isDirectory(features)) return null;
        try (Stream<Path> folders = Files.list(features)) {
            List<Path> sorted = folders.sorted().toList();
            for (Path feature : sorted) {
                Path vow = feature.resolve("components").resolve(id + ".vow");
                if (Files.isRegularFile(vow)) return vow;
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    /** The component a project-relative path is the vow of, or null. */
    static String componentOfPath(String relative) {
        String p = relative == null ? "" : relative.replace('\\', '/');
        var m = java.util.regex.Pattern.compile("^\\.archvow/features/[^/]+/components/([^/]+)\\.vow$").matcher(p);
        return m.matches() ? m.group(1) : null;
    }

    /** The page {@code archvow doc <feature>} writes ({@code project} for the whole project), project-relative. */
    static String docPage(String feature) {
        return ".archvow/doc/" + (feature == null || feature.isBlank() ? "project" : feature) + ".md";
    }
}
