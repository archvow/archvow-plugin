package org.archvow.idea;

import com.google.gson.JsonObject;
import java.util.Map;

/** Each diagnostic only offers a grounded command form or a named source document. */
public final class CheckActionsRegression {
    private static void expect(boolean ok, String description) {
        if (!ok) throw new AssertionError(description);
    }

    public static void main(String[] args) {
        expect("Open vow".equals(ArchvowCheckActions.fileLabel(".archvow/spec.vow", false)), "vow label");
        expect("Open spec".equals(ArchvowCheckActions.fileLabel(".archvow/components/x/spec.md", false)), "spec label");
        expect("Open source".equals(ArchvowCheckActions.fileLabel("src/reader.py", false)), "source label");
        expect("Open document".equals(ArchvowCheckActions.fileLabel("CLAUDE.md", false)), "document label");
        expect("Open configuration".equals(ArchvowCheckActions.fileLabel(".claude/settings.json", false)), "config file label");
        expect("Show configuration folder".equals(ArchvowCheckActions.fileLabel(".claude", false)), "configuration directory label");
        expect("Show configuration folder".equals(ArchvowCheckActions.fileLabel(".cursor", false)), "other configuration directory label");
        expect("Afficher le dossier de configuration".equals(ArchvowCheckActions.fileLabel(".claude", true)), "translated folder label");
        expect(!ArchvowCheckActions.configurationFolder("CLAUDE.md"), "a markdown document is not a directory");
        expect("Ouvrir la vow".equals(ArchvowCheckActions.fileLabel(".archvow/spec.vow", true)), "translated label");

        JsonObject diagnostic = new JsonObject();
        diagnostic.addProperty("code", "ARCH019");
        diagnostic.addProperty("component", "orders_reader");
        diagnostic.addProperty("message", "Reader schema missing");
        var fix = ArchvowCheckActions.fix(diagnostic, null);
        expect(fix != null && "component add".equals(fix.name()) && "schema".equals(fix.values().get("type")), "schema form");

        diagnostic.addProperty("code", "ARCH040");
        diagnostic.addProperty("message", "CLAUDE.md configures unapproved AI assistant");
        expect(ArchvowCheckActions.fix(diagnostic, null) == null, "no guessed command");
        var suggestion = new ArchvowGuidance.GuideCommand("init", Map.of("force", "true"),
            "archvow init --force", java.util.List.of(), false);
        var advice = new ArchvowGuidance.Guidance("ARCH012", "", "", "", "", "", suggestion, null);
        expect("init".equals(ArchvowCheckActions.fix(diagnostic, advice).name()), "prefill from guidance");
        System.out.println("OK: diagnostic-specific fixes and file labels");
    }
}
