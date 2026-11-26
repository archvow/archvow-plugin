package org.archvow.idea;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import java.util.Map;

/**
 * 1.0.0: Overview › Templates (archvow 3.0). The row's line names the project's chain, bases first, and the copies
 * changed by hand (ARCH060); the list shows each template with what it extends, its source and state, Update and New
 * template, then the project's own additions with Add a question / Add a rule (target `project`). VS Code checks the
 * same in generic-ui-regression.js (templatesSummary / templatesView).
 */
public final class TemplatesRegression {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        JsonArray templates = JsonParser.parseString("""
            [{"name": "python-base", "version": "3.0.0", "description": "Shared answers", "extends": [], "source": "archvow",
              "state": "ok", "why": ""},
             {"name": "acme-app", "version": "1.0.0", "description": "ACME's apps", "extends": ["python-app", "acme"],
              "source": "../company-templates", "state": "ok", "why": ""},
             {"name": "bank", "version": "0.1.0", "description": "", "extends": [], "source": "bank_templates",
              "state": "changed", "why": "its copy was edited by hand"},
             {"name": "project", "version": null, "source": ".archvow/template", "state": "ok", "why": "",
              "rule_families": ["TEAM"], "agents": []}]
            """).getAsJsonArray();
        check(ArchvowTemplates.changed(templates) == 1, "changed");
        check("python-base ← acme-app ← bank · 1 changed by hand".equals(ArchvowTemplates.summary(templates, false)),
            ArchvowTemplates.summary(templates, false));
        check("python-base ← acme-app ← bank · 1 modifiés à la main".equals(ArchvowTemplates.summary(templates, true)), "fr");
        check("none".equals(ArchvowTemplates.summary(new JsonArray(), false)) && "aucun".equals(ArchvowTemplates.summary(null, true)), "none");

        String html = ArchvowTemplates.html(templates, false);
        for (String part : new String[] {"acme-app", "1.0.0", "ACME&#39;s apps", "Extends: python-app, acme",
                "Source: ../company-templates (a folder, from the project&#39;s root)", "Source: archvow<",
                "Source: bank_templates (an installed package)", "its copy was edited by hand (ARCH060)", "rules: TEAM",
                "archvow-template:update", "archvow-template:new", "archvow-template:question", "archvow-template:rule",
                "archvow-template:diff", "changed by hand", "as copied", "This project&#39;s own additions"})
            check(html.contains(part), "the list lacks " + part);
        check(html.indexOf("archvow-template:diff") > html.indexOf("bank"), "See the change only for the changed copy");
        check(ArchvowTemplates.html(new JsonArray(), true).contains("Aucun template copié dans ce projet"), "empty, French");
        check(ArchvowTemplates.source("/home/alice/company-templates", false).contains("an absolute path: true on this machine only"), "absolute");
        check(ArchvowTemplates.source("C:\\templates\\acme", true).contains("un chemin absolu"), "absolute, Windows, French");
        check(ArchvowTemplates.source("", false).equals("Source: unknown"), "none");

        check(ArchvowTemplates.command("archvow-template:update").equals(Map.entry("template update", Map.of())), "update");
        check(ArchvowTemplates.command("archvow-template:new").equals(Map.entry("template new", Map.of())), "new");
        check(ArchvowTemplates.command("archvow-template:question").equals(Map.entry("template question", Map.of("template", "project"))), "question");
        check(ArchvowTemplates.command("archvow-template:rule").equals(Map.entry("template rule", Map.of("template", "project"))), "rule");
        check(ArchvowTemplates.command("archvow-template:diff").equals(Map.entry("diff", Map.of())), "diff");
        check(ArchvowTemplates.command("archvow-template:nope") == null && ArchvowTemplates.command("https://x") == null
            && ArchvowTemplates.command(null) == null, "not a template link");
        for (String command : new String[] {"template update", "template new", "template question", "template rule", "diff"})
            check(ArchvowGrammar.COMMANDS.stream().anyMatch(c -> c.label.equals(command)), command + " is not in the grammar");
        System.out.println("OK: Overview › Templates");
    }
}
