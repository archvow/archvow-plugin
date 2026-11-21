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
    static Fix fix(String code, String message, String component) { return fix(code, message, component, false); }

    /** The fix, its label in one language (0.26.1). */
    static Fix fix(String code, String message, String component, boolean fr) {
        String text = message == null ? "" : message;
        if ("ARCH019".equalsIgnoreCase(code) && !component.isBlank())
            return new Fix("component add", Map.of("type", "schema"), (fr ? "Créer un schéma" : "Create schema"));
        if ("ARCH004".equalsIgnoreCase(code)) {
            Matcher m = SIGNATURE.matcher(text);
            if (m.find()) return new Fix("component edit", Map.of("component", m.group(1), "signature", m.group(2)),
                (fr ? "Vérifier la signature" : "Review signature"));
        }
        if ("ARCH039".equalsIgnoreCase(code)) {
            // drawn from a folder no longer declared: the architect adds it; a brief changed: accept it once reviewed
            ArchvowBriefs.AddFolder folder = ArchvowBriefs.addFolder(text);
            if (folder != null) return new Fix("init", folder.values(), (fr ? "Ajouter ce dossier (architecte)" : "Add this folder (architect)"));
            String ref = ArchvowBriefs.acceptRef(text);
            if (!ref.isEmpty()) return new Fix("brief accept", Map.of("brief", ref), (fr ? "Accepter (relue)" : "Accept (reviewed)"));
        }
        if ("ARCH030".equalsIgnoreCase(code)) {
            Matcher m = INVARIANT.matcher(text);
            if (m.find()) return new Fix("invariant add", Map.of("component", m.group(1), "prevents", m.group(2)),
                (fr ? "Ajouter un invariant" : "Add invariant"));
        }
        return null;
    }
}
