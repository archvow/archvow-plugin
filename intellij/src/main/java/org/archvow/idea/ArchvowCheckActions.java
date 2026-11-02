package org.archvow.idea;

import com.google.gson.JsonObject;
import java.util.Map;

/** Only offer fixes Archvow can genuinely prepare; never execute guessed commands. */
final class ArchvowCheckActions {
    record FixAction(String name, Map<String, String> values) {}
    private ArchvowCheckActions() {}

    static FixAction fix(JsonObject violation, ArchvowGuidance.Guidance guidance) {
        if (violation == null) return null;
        String code = ArchvowGrammar.str(violation, "code");
        String component = ArchvowGrammar.str(violation, "component");
        ArchvowIssueAction.Fix explicit = ArchvowIssueAction.fix(code,
            ArchvowGrammar.str(violation, "message"), component);
        if (explicit != null) {
            if ("brief accept".equals(explicit.command())) return null; // special workflow, not a command form
            if ("ARCH019".equalsIgnoreCase(code)) return new FixAction("component add", Map.of("type", "schema"));
            return new FixAction(explicit.command(), explicit.values());
        }
        ArchvowGuidance.GuideCommand command = guidance == null ? null : guidance.command();
        return command != null && !command.readOnly() ? new FixAction(command.name(), command.values()) : null;
    }

    /** Archvow may report a configuration *directory* (e.g. .claude), not a document. */
    static boolean configurationFolder(String path) {
        String normalized = (path == null ? "" : path).replace('\\', '/');
        String last = normalized.substring(normalized.lastIndexOf('/') + 1).toLowerCase(java.util.Locale.ROOT);
        return java.util.Set.of(".claude", ".cursor", ".continue", ".gemini", ".codex", ".github").contains(last);
    }

    static String fileLabel(String path, boolean french) {
        if (configurationFolder(path)) return french ? "Afficher le dossier de configuration" : "Show configuration folder";
        String p = path == null ? "" : path.toLowerCase(java.util.Locale.ROOT);
        if (p.endsWith(".vow")) return french ? "Ouvrir la vow" : "Open vow";
        if (p.endsWith("spec.md")) return french ? "Ouvrir la spec" : "Open spec";
        if (p.endsWith(".py") || p.endsWith(".java") || p.endsWith(".ts") || p.endsWith(".js"))
            return french ? "Ouvrir le code source" : "Open source";
        if (p.endsWith(".md")) return french ? "Ouvrir le document" : "Open document";
        return french ? "Ouvrir la configuration" : "Open configuration";
    }
}
