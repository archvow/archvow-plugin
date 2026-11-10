package org.archvow.idea;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import java.util.Map;

/**
 * 0.22.0: Overview › Packs (archvow 1.26). The row's line counts the packs and those changed by hand (ARCH060); the
 * list shows each pack with what it brings, its source and Update / Remove / Check; each link opens its command's
 * form with the pack's name filled in; without a pack the list says the templates come from archvow. VS Code checks
 * the same in workspace.js (packsSummary / packsView).
 */
public final class PacksRegression {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        JsonArray packs = JsonParser.parseString("""
            [{"name": "acme", "version": "1.2.0", "description": "ACME's templates", "archvow": ">=1.26", "builtin": false,
              "templates": ["acme-service"], "agents": ["acme-bot"], "rule_families": ["naming"],
              "source": "/home/me/packs/acme", "state": "ok", "why": ""},
             {"name": "bank", "version": "0.1.0", "description": "", "archvow": "", "builtin": false,
              "templates": [], "agents": [], "rule_families": [],
              "source": "git@example.invalid:bank/pack.git", "state": "changed", "why": "rules/naming.yaml differs"}]
            """).getAsJsonArray();
        check(ArchvowPacks.changed(packs) == 1, "changed");
        check("2 packs · 1 changed by hand".equals(ArchvowPacks.summary(packs, false)), ArchvowPacks.summary(packs, false));
        check("2 packs · 1 modifiés à la main".equals(ArchvowPacks.summary(packs, true)), ArchvowPacks.summary(packs, true));
        check("0 packs".equals(ArchvowPacks.summary(new JsonArray(), false)), "none");
        check("0 packs".equals(ArchvowPacks.summary(null, false)), "null");

        String html = ArchvowPacks.html(packs, false);
        for (String part : new String[] {"acme", "1.2.0", "ACME&#39;s templates", "templates: acme-service", "agents: acme-bot",
                "rules: naming", "Source: /home/me/packs/acme", "Nothing yet.", "rules/naming.yaml differs (ARCH060)",
                "archvow-pack:update:acme", "archvow-pack:remove:bank", "archvow-pack:check:acme", "archvow-pack:diff",
                "archvow-pack:add", "archvow-pack:new", "changed by hand", "as added"})
            check(html.contains(part), "the list lacks " + part);
        check(html.indexOf("archvow-pack:diff") > html.indexOf("bank"), "See the change only for the changed pack");
        check(ArchvowPacks.html(new JsonArray(), true).contains("Aucun pack dans ce projet"), "empty, French");

        check(ArchvowPacks.command("archvow-pack:update:acme").equals(Map.entry("pack update", Map.of("name", "acme"))), "update");
        check(ArchvowPacks.command("archvow-pack:remove:bank").equals(Map.entry("pack remove", Map.of("name", "bank"))), "remove");
        check(ArchvowPacks.command("archvow-pack:check:acme").equals(Map.entry("pack check", Map.of("pack", "acme"))), "check");
        check(ArchvowPacks.command("archvow-pack:add").equals(Map.entry("pack add", Map.of())), "add");
        check(ArchvowPacks.command("archvow-pack:new").equals(Map.entry("pack new", Map.of())), "new");
        check(ArchvowPacks.command("archvow-pack:diff").equals(Map.entry("diff", Map.of())), "diff");
        check(ArchvowPacks.command("archvow-pack:nope") == null && ArchvowPacks.command("https://x") == null
            && ArchvowPacks.command(null) == null, "not a pack link");
        // every command a link opens is one archvow has
        for (String command : new String[] {"pack add", "pack new", "pack update", "pack remove", "pack check", "diff"})
            check(ArchvowGrammar.COMMANDS.stream().anyMatch(c -> c.label.equals(command)), command + " is not in the grammar");
        System.out.println("OK: Overview › Packs");
    }
}
