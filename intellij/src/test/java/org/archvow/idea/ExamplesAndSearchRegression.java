package org.archvow.idea;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 0.18.0 (archvow 1.21): the Examples view and the search, on what archvow really prints
 * (contract/cli/python-app/examples, contract/cli/<example>/search). Each example's state is the same as on the VS Code
 * page (contract/forms/examples.expected.json, written by its examples.test.ts); the view, the search panel,
 * the views' order (Examples after Rules) and the editor action's word, headless.
 */
public final class ExamplesAndSearchRegression {
    public static void main(String[] args) throws Exception {
        Path contract = contract();
        // the same states as VS Code, in both languages
        JsonObject expected = JsonParser.parseString(read(contract.resolve("forms/examples.expected.json"))).getAsJsonObject();
        for (String state : List.of("passing", "failing", "no-test")) {
            JsonObject example = ArchvowGrammar.objects(view(contract, state), "examples").get(0);
            for (boolean french : List.of(false, true)) {
                JsonObject want = expected.getAsJsonObject(state + "." + (french ? "fr" : "en"));
                ArchvowExamples.State got = ArchvowExamples.state(example, french);
                check(got.result().equals(ArchvowGrammar.str(want, "result")) && got.mark().equals(ArchvowGrammar.str(want, "mark"))
                    && got.tone().equals(ArchvowGrammar.str(want, "tone")) && got.text().equals(ArchvowGrammar.str(want, "text")),
                    state + (french ? " fr" : " en") + ": " + got + " is not " + want);
            }
        }
        // the chip, the groups by rule (the shipped pyspark-pipeline example), the cards
        JsonObject failing = view(contract, "failing");
        check(ArchvowExamples.label(failing, false).equals("Examples 1 · 1 ✖"), "label " + ArchvowExamples.label(failing, false));
        check(ArchvowExamples.label(view(contract, "passing"), true).equals("Exemples 1 ✓"), "French label");
        JsonObject none = JsonParser.parseString("{\"component\":\"spark\",\"examples\":[]}").getAsJsonObject();
        check(ArchvowExamples.label(none, true).equals("Exemples") && ArchvowExamples.label(none, false).equals("Examples"), "no example: just the name");
        String panel = read(contract.getParent().resolve("intellij/src/main/java/org/archvow/idea/ArchvowPanel.java"));
        check(!panel.contains("chip.setVisible("), "the Examples chip is always shown, as Decisions and Questions");
        JsonObject clean = JsonParser.parseString(read(contract.resolve("cli/pyspark-pipeline/show/clean_orders.json"))).getAsJsonObject();
        check(new ArrayList<>(ArchvowExamples.groups(clean).keySet()).equals(List.of("clean_orders.INV1", "")), "groups " + ArchvowExamples.groups(clean).keySet());
        ArchvowItemCards.Card card = ArchvowExamples.card(clean, ArchvowExamples.ordered(clean).get(0), false);
        check(card.number().equals("○") && card.open() && card.by().startsWith("clean_orders.INV1 - every input row"), "card " + card);
        check(ArchvowExamples.testFile(failing, "US-001-pay-a-sent-invoice").equals("tests/p4_entrypoints/test_invoices_cli.py"), "test file");
        // the detail: the files side by side, the differences marked, what to do
        String detail = ArchvowExamples.detailHtml(failing, ArchvowGrammar.objects(failing, "examples").get(0), false);
        check(detail.contains("<b>−</b></td><td bgcolor='#f3e3bf'>F-7 Ada 9.90 paid</td>"), "missing line: " + detail);
        check(detail.contains("<b>+</b></td><td bgcolor='#f8d7d5'>F-7 Ada 9.90 sent</td>"), "extra line");
        check(detail.contains("Fix the code, not the example.") && detail.contains("archvow example add invoices_cli US-001-pay-a-sent-invoice"), "fix");
        check(detail.contains("href='brief:briefs/invoices.md#paying-a-sent-invoice'") && detail.contains("href='open:tests/examples/invoices_cli/US-001-pay-a-sent-invoice/actual/stdout.txt'"), "links");
        check(ArchvowHtml.wellSized(detail), "text sizes");
        JsonObject table = JsonParser.parseString("{\"name\":\"orders.csv\",\"kind\":\"table\",\"state\":\"differs\",\"differences\":1,"
            + "\"header\":{\"expected\":[\"order_id\",\"amount\"],\"actual\":[\"order_id\",\"amount\"]},"
            + "\"rows\":[{\"op\":\"changed\",\"expected\":[\"A1\",\"5.00\"],\"actual\":[\"A1\",\"5.0\"],\"cells\":[1]}]}").getAsJsonObject();
        String cells = ArchvowExamples.fileHtml(table, JsonParser.parseString("{\"actual\":true}").getAsJsonObject(), false);
        check(cells.contains("<td bgcolor='#a9c8f5'><b>5.00</b></td>") && cells.contains("<td bgcolor='#dbe9fb'>A1</td>"), "changed cell: " + cells);
        // the view: cards, the failing one selected, Copy command for it only
        ArchvowExamplesView examples = new ArchvowExamplesView(new NoActions());
        examples.show(failing, false);
        check(examples.count() == 1 && examples.copyVisible() && examples.detailText().contains("F-7 Ada 9.90 sent"), "view");
        examples.show(view(contract, "passing"), false);
        check(!examples.copyVisible(), "no Copy command for a passing example");
        examples.show(JsonParser.parseString("{\"component\":\"x\",\"examples\":[]}").getAsJsonObject(), false);
        check(examples.showsEmpty(), "empty view");
        // the search: groups, places, a name found nowhere
        JsonObject found = JsonParser.parseString(read(contract.resolve("cli/python-app/search/InvoiceNotFoundError.json"))).getAsJsonObject();
        List<ArchvowSearch.Hit> hits = ArchvowSearch.hits(found, "");
        check(hits.size() == ArchvowGrammar.number(found, "total") && hits.get(0).group().equals("spec"), "hits");
        check(ArchvowSearch.hits(found, "code").stream().allMatch(h -> h.group().equals("code")), "one group");
        check(ArchvowSearch.heading(found, false).startsWith("found in " + hits.size() + " places · a class of invoice_not_found_error"), ArchvowSearch.heading(found, false));
        ArchvowSearchView search = new ArchvowSearchView(new NoSearch());
        search.show(found, "InvoiceNotFoundError", contract.resolve("archvow/examples/python-app").toString(), false);
        check(search.groupCount() == 1 + ArchvowSearch.groups(found).size() && search.hitCount() == hits.size(), "search panel");
        check(search.previewText().contains("symbol InvoiceNotFoundError</b>"), "preview reads the lines around: " + search.previewText());
        search.selectGroup(1);
        check(search.hitCount() == Integer.parseInt(ArchvowSearch.groups(found).get(0)[1]), "group filter");
        JsonObject nowhere = JsonParser.parseString(read(contract.resolve("cli/python-app/search/mark_payd.json"))).getAsJsonObject();
        search.show(nowhere, "mark_payd", null, false);
        check(search.showsNowhere() && search.closeButtons() == 2, "close names");
        // a removed component's name is searched: its result is never stored as that component's view
        check(ArchvowGrammar.componentView(read(contract.resolve("cli/python-app/search/mark_payd.json")), "mark_payd") == null
            && ArchvowGrammar.componentView(read(contract.resolve("cli/python-app/show/mark_paid.json")), "mark_paid") != null
            && ArchvowGrammar.componentView(read(contract.resolve("cli/python-app/show/mark_paid.json")), "invoices_cli") == null, "componentView");
        check(ArchvowSearch.nowhere("x", false).equals("'x' is not a component, a file or an item, and is nowhere in the project."), "nowhere");
        // the views' order: Examples (view 8) after Rules; the menu gives back the view's index
        List<Integer> chosen = new ArrayList<>();
        ArchvowCompactViews bar = new ArchvowCompactViews(chosen::add);
        for (String name : List.of("D", "Q", "S", "A", "R", "T", "Spec", "O")) bar.addView(new javax.swing.JToggleButton(name));
        javax.swing.JToggleButton ex = new javax.swing.JToggleButton("E");
        bar.addView(ex, 5);
        bar.setActive(8);
        check(ex.isSelected(), "view 8 selected");
        bar.forcePrimaryLimitForTest(6);
        check(ex.getParent() == bar && bar.hasOverflow(), "Examples is the sixth chip, after Rules");
        bar.forcePrimaryLimitForTest(5);
        check(ex.getParent() == null && bar.hasOverflow(), "narrow: Examples under More");
        System.out.println("OK: examples (states as VS Code, view, detail), search (panel, close names), views order");
    }

    private static JsonObject view(Path contract, String state) throws Exception {
        return JsonParser.parseString(read(contract.resolve("cli/python-app/examples/" + state + ".json"))).getAsJsonObject();
    }

    private static Path contract() {
        for (Path p = Path.of("").toAbsolutePath(); p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("contract/cli/python-app/examples"))) return p.resolve("contract");
        }
        throw new AssertionError("contract/cli/python-app/examples not found from " + Path.of("").toAbsolutePath());
    }

    private static String read(Path file) throws Exception {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static final class NoActions implements ArchvowExamplesView.Actions {
        @Override public void openFile(String relative, int line) { }
        @Override public void openBrief(String ref) { }
        @Override public void copy(String text) { }
        @Override public void record(String component) { }
    }

    private static final class NoSearch implements ArchvowSearchView.Actions {
        @Override public void openFile(String relative, int line) { }
        @Override public void showComponent(String id) { }
        @Override public void openRule(String code) { }
        @Override public void search(String name) { }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
