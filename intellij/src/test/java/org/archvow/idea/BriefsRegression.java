package org.archvow.idea;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The briefs folders of archvow 1.17 (0.12.0): the --from choices, archvow's heading slugs (expected values
 * are archvow's own briefs.slug / briefs.sections), "Drawn from", and archvow's refusal turned into the
 * architect's init --force form. The same rules as VS Code's briefs.test.ts.
 */
public final class BriefsRegression {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    public static void main(String[] args) throws Exception {
        // the folders
        check(ArchvowBriefs.folders(json("{\"init_answers\":{\"briefs\":\"briefs,specs\"}}")).equals(List.of("briefs", "specs")), "two folders");
        check(ArchvowBriefs.folders(json("{\"init_answers\":{}}")).equals(List.of("briefs")), "unset is briefs");
        check(ArchvowBriefs.folders(null).equals(List.of("briefs")), "no spec is briefs");
        check(ArchvowBriefs.folders(json("{\"init_answers\":{\"briefs\":\" /specs/ , briefs,specs\"}}")).equals(List.of("specs", "briefs")), "cleaned, once each");
        check(ArchvowBriefs.folders(json("{\"init_answers\":{\"briefs\":\"\"}}")).isEmpty(), "emptied is none");
        check(ArchvowBriefs.folderOf("specs/001-orders/plan.md", List.of("briefs", "specs")).equals("specs"), "folderOf");
        check(ArchvowBriefs.folderOf("specs2/x.md", List.of("specs")).isEmpty(), "a prefix is not a folder");

        // archvow's slugs
        check(ArchvowBriefs.slug("Paying an invoice").equals("paying-an-invoice"), "slug");
        check(ArchvowBriefs.slug("Données d'entrée : fichiers").equals("donnees-d-entree-fichiers"), "accents");
        check(ArchvowBriefs.slug("Ça & ÇA — Œuvre").equals("ca-ca-uvre"), "ligatures as archvow");
        check(ArchvowBriefs.slug("Straße 2026").equals("strae-2026"), "ß as archvow");
        check(ArchvowBriefs.slug("日本").isEmpty(), "no ascii");
        List<ArchvowBriefs.Section> found = ArchvowBriefs.sections("# Plan\n## Données\ntext\n## Données\n## Données ##\n### Été\n#NoSpace\n####### seven\n");
        StringBuilder slugs = new StringBuilder();
        for (ArchvowBriefs.Section s : found) slugs.append(s.slug()).append('@').append(s.line()).append(' ');
        check(slugs.toString().equals("plan@1 donnees@2 donnees-2@4 donnees-3@5 ete@6 "), "sections: " + slugs);
        check(ArchvowBriefs.sections("# 日本").get(0).slug().equals("section"), "a heading without letters");

        // the files of the declared folders only
        Path root = Files.createTempDirectory("archvow-briefs");
        for (String[] f : new String[][] {
            {"specs/001-orders/plan.md", "# Orders plan\n\n## Data model\n\n## Paying an invoice\n"},
            {"specs/001-orders/notes.txt", "# not a heading in a text file\n"},
            {"briefs/sales.md", "# Input files\n"}, {"docs/other.md", "# Not declared\n"}, {"specs/diagram.png", "x"}}) {
            Files.createDirectories(root.resolve(f[0]).getParent());
            Files.writeString(root.resolve(f[0]), f[1]);
        }
        List<ArchvowBriefs.Brief> briefs = ArchvowBriefs.files(root.toString(), List.of("briefs", "specs"));
        check(ArchvowBriefs.choices(briefs).equals(List.of("briefs/sales.md", "briefs/sales.md#input-files", "specs/001-orders/notes.txt",
            "specs/001-orders/plan.md", "specs/001-orders/plan.md#orders-plan", "specs/001-orders/plan.md#data-model",
            "specs/001-orders/plan.md#paying-an-invoice")), "choices: " + ArchvowBriefs.choices(briefs));
        check(ArchvowBriefs.line(briefs, "specs/001-orders/plan.md#paying-an-invoice") == 5, "heading line");
        check(ArchvowBriefs.line(briefs, "specs/001-orders/plan.md#gone") == 1, "a heading gone");
        check(ArchvowBriefs.files(root.toString(), List.of("missing")).isEmpty(), "a missing folder");

        // links: Drawn from, Add this folder
        JsonObject drawn = json("{\"source\":{\"file\":\"specs/001-orders/plan.md\",\"section\":\"paying-an-invoice\",\"hash\":\"x\"}}");
        check(ArchvowBriefs.drawnFrom(drawn).equals("specs/001-orders/plan.md#paying-an-invoice"), "drawn from");
        String head = ArchvowBriefs.drawnFromHtml(drawn, false);
        check(head.contains("Drawn from: <a href='brief:specs%2F001-orders%2Fplan.md%23paying-an-invoice'>"), head);
        check(ArchvowBriefs.briefOf("brief:specs%2F001-orders%2Fplan.md%23paying-an-invoice").equals("specs/001-orders/plan.md#paying-an-invoice"), "brief link");
        check(ArchvowBriefs.drawnFromHtml(new JsonObject(), true).isEmpty(), "drawn from nothing");
        String refusal = "✖ archvow: error: --from: specs/001-orders/plan.md: specs/ is not a briefs folder of this project (briefs: briefs)"
            + " -> an architect adds it: archvow init invoicing --force -a briefs=briefs,specs";
        ArchvowBriefs.AddFolder add = ArchvowBriefs.addFolder(refusal);
        check(add != null && add.values().equals(Map.of("package", "invoicing", "force", "true", "answer", "briefs=briefs,specs")), "refusal read");
        String result = ArchvowBriefs.withAddFolder("<html><body><p>error</p></body></html>", refusal, true);
        check(result.contains("Ajouter ce dossier (architecte)") && result.endsWith("</p></body></html>"), result);
        int at = result.indexOf("href='") + 6;
        ArchvowBriefs.AddFolder back = ArchvowBriefs.initOf(result.substring(at, result.indexOf('\'', at)));
        check(back != null && back.equals(add), "the link opens the same form");
        check(ArchvowBriefs.withAddFolder("<html><body>ok</body></html>", "all good", false).equals("<html><body>ok</body></html>"), "no link without");

        // ARCH039: accept once reviewed, or the architect adds the folder
        ArchvowIssueAction.Fix accept = ArchvowIssueAction.fix("ARCH039", "specs/001-orders/plan.md#paying-an-invoice changed after mark_paid"
            + " was drawn from it -> review them, then: archvow brief accept specs/001-orders/plan.md#paying-an-invoice", "");
        check(accept != null && accept.command().equals("brief accept")
            && accept.values().equals(Map.of("brief", "specs/001-orders/plan.md#paying-an-invoice")), "accept fix");
        ArchvowIssueAction.Fix folder = ArchvowIssueAction.fix("ARCH039", "mark_paid is drawn from docs/x.md, outside the briefs folders (briefs)"
            + " -> an architect adds it: archvow init invoicing --force -a briefs=briefs,docs", "");
        check(folder != null && folder.command().equals("init") && folder.values().get("answer").equals("briefs=briefs,docs"), "add-folder fix");
        check(ArchvowBriefs.foldersLine(List.of("briefs", "specs"), 3, false).equals("Brief folders (--from): briefs/, specs/ · 3 files"), "overview line");

        // the contract's recording (archvow 1.17 itself), when found
        Path contract = Path.of(System.getProperty("archvow.contract", "../contract/cli")).resolve("briefs");
        if (Files.isDirectory(contract)) {
            JsonObject spec = json(Files.readString(contract.resolve("show.json")));
            check(ArchvowBriefs.folders(spec).equals(List.of("briefs", "specs")), "contract folders");
            check(ArchvowBriefs.drawnFrom(json(Files.readString(contract.resolve("show/mark_paid.json"))))
                .equals("specs/001-orders/plan.md#paying-an-invoice"), "contract drawn from");
            check(ArchvowBriefs.addFolder(Files.readString(contract.resolve("refused.txt"))).values().get("package").equals("invoicing"), "contract refusal");
        }
        System.out.println("OK: brief folders, slugs, --from choices, Drawn from, Add this folder, ARCH039");
    }
}
