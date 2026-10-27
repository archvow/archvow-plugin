package org.archvow.idea;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parse only grounded, documented Archvow fixes. Never execute text from a violation. */
final class ArchvowIssueAction {
    record Fix(String command, Map<String, String> values, String label) {}
    private ArchvowIssueAction() {}
    private static final Pattern QUOTED = Pattern.compile("'([A-Za-z_][A-Za-z0-9_.-]*)'");
    private static final Pattern SIGNATURE = Pattern.compile("archvow\\s+component\\s+edit\\s+([A-Za-z_][A-Za-z0-9_.-]*)\\s+--signature\\s+\\\"([^\\\"]+)\\\"");
    private static final Pattern INVARIANT = Pattern.compile("archvow\\s+invariant\\s+add\\s+([A-Za-z_][A-Za-z0-9_.-]*)\\s+\\\"[^\\\"]*\\\"\\s+--prevents\\s+([A-Za-z_][A-Za-z0-9_.-]*)");
    static String quotedComponent(String message, java.util.Set<String> known) {
        Matcher matches = QUOTED.matcher(message == null ? "" : message);
        while (matches.find()) if (known.contains(matches.group(1))) return matches.group(1);
        return "";
    }
    static Fix fix(String code, String message, String component) {
        String text = message == null ? "" : message;
        if ("ARCH019".equalsIgnoreCase(code) && !component.isBlank())
            return new Fix("component add", Map.of("type", "schema"), "Create schema / Créer un schéma");
        if ("ARCH004".equalsIgnoreCase(code)) {
            Matcher m = SIGNATURE.matcher(text);
            if (m.find()) return new Fix("component edit", Map.of("component", m.group(1), "signature", m.group(2)),
                "Review signature / Vérifier la signature");
        }
        if ("ARCH030".equalsIgnoreCase(code)) {
            Matcher m = INVARIANT.matcher(text);
            if (m.find()) return new Fix("invariant add", Map.of("component", m.group(1), "prevents", m.group(2)),
                "Add invariant / Ajouter un invariant");
        }
        return null;
    }
}
