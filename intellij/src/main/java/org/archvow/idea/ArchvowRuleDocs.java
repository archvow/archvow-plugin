package org.archvow.idea;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Links check codes to the real project checks and Archvow's bundled rule reference. */
final class ArchvowRuleDocs {
    private ArchvowRuleDocs() { }

    record Reference(Path path, int offset, String description) { }
    private static final Pattern CODE = Pattern.compile("[A-Z]{2,12}[0-9]{3}");

    static String canonical(String input) {
        if (input == null) return "";
        String code = input.trim().toUpperCase(Locale.ROOT);
        return CODE.matcher(code).matches() ? code : "";
    }

    static Reference findProjectReference(Path root, String candidate) {
        String code = canonical(candidate);
        if (root == null || code.isBlank()) return null;
        for (String relative : List.of(".archvow/doc/project.md", "docs/rules.fr.md", "docs/rules.md",
                "docs/project.md", "project.md", ".archvow/README.md")) {
            Path file = root.resolve(relative).normalize();
            if (!file.startsWith(root.normalize()) || !Files.isRegularFile(file)) continue;
            try {
                // Projects can be large; avoid reading huge documents from the UI.
                if (Files.size(file) > 2_000_000) continue;
                String markdown = Files.readString(file, StandardCharsets.UTF_8);
                int offset = findCodeOffset(markdown, code);
                if (offset >= 0) return new Reference(file, offset, rowDescription(markdown, code));
            } catch (IOException | SecurityException ignored) { /* next supported project document */ }
        }
        return null;
    }

    static String explain(String candidate, boolean french) {
        String code = canonical(candidate);
        if (code.isBlank()) return "";
        String resource = french ? "/archvow/rules.fr.md" : "/archvow/rules.md";
        try (InputStream input = ArchvowRuleDocs.class.getResourceAsStream(resource)) {
            if (input == null) return "";
            return rowDescription(new String(input.readAllBytes(), StandardCharsets.UTF_8), code);
        } catch (IOException ignored) { return ""; }
    }

    static int findCodeOffset(String markdown, String code) {
        if (markdown == null || canonical(code).isBlank()) return -1;
        Pattern match = Pattern.compile("(?m)^\\s*\\|\\s*" + Pattern.quote(code) + "\\s*\\|");
        var m = match.matcher(markdown);
        if (m.find()) return m.start();
        // Custom rules may be headings rather than rows.
        m = Pattern.compile("(?m)^#{1,6}\\s+" + Pattern.quote(code) + "(?:\\s|$)").matcher(markdown);
        return m.find() ? m.start() : -1;
    }

    static String rowDescription(String markdown, String code) {
        int start = findCodeOffset(markdown, code);
        if (start < 0) return "";
        int end = markdown.indexOf('\n', start);
        String row = markdown.substring(start, end < 0 ? markdown.length() : end).trim();
        String[] cells = row.split("\\|", -1);
        StringBuilder description = new StringBuilder();
        for (int i = 2; i < cells.length - 1; i++) {
            String cell = cells[i].trim();
            if (!cell.isBlank()) {
                if (description.length() > 0) description.append(" — ");
                description.append(cell);
            }
        }
        return description.toString();
    }
}
