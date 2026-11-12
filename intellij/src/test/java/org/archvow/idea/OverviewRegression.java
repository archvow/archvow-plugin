package org.archvow.idea;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The Overview's cards read archvow's examples exactly as in VS Code (0.25.0): the first steps, the first finding and
 * each row's suggested answers of ../contract/cli/<example>/workspace.json must equal ../contract/overview/expected.json,
 * which the VS Code extension's overview.ts writes and checks (vscode/src/test/overview.test.ts).
 */
public final class OverviewRegression {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    static JsonElement json(ArchvowOverview.FirstSteps f) {
        if (f == null) return JsonNull.INSTANCE;
        JsonObject o = new JsonObject();
        JsonArray steps = new JsonArray();
        for (ArchvowOverview.FirstStep s : f.steps()) {
            JsonObject step = new JsonObject();
            step.addProperty("step", s.step());
            step.addProperty("title", s.title());
            step.addProperty("detail", s.detail());
            step.addProperty("command", s.command());
            step.addProperty("done", s.done());
            steps.add(step);
        }
        o.add("steps", steps);
        o.addProperty("done", f.done());
        o.addProperty("current", f.current());
        return o;
    }

    static JsonElement json(ArchvowOverview.Finding f) {
        if (f == null) return JsonNull.INSTANCE;
        JsonObject o = new JsonObject();
        o.addProperty("title", f.title());
        o.addProperty("code", f.code());
        o.addProperty("severity", f.severity());
        o.addProperty("file", f.file());
        o.addProperty("line", f.line());
        o.addProperty("where", f.where());
        return o;
    }

    static String texts(java.awt.Component c) {
        StringBuilder all = new StringBuilder();
        if (c instanceof javax.swing.JLabel l) all.append(l.getText()).append('\n');
        if (c instanceof javax.swing.AbstractButton b) all.append(b.getText()).append('\n');
        if (c instanceof javax.swing.text.JTextComponent t) all.append(t.getText()).append('\n');
        if (c instanceof java.awt.Container k) for (java.awt.Component child : k.getComponents()) all.append(texts(child));
        return all.toString();
    }

    static void press(java.awt.Component c, String text) {
        if (c instanceof javax.swing.AbstractButton b && text.equals(b.getText())) { b.doClick(); return; }
        if (c instanceof ArchvowOverviewCards.Link l && text.equals(l.getText())) { l.click(); return; }
        if (c instanceof java.awt.Container k) for (java.awt.Component child : k.getComponents()) press(child, text);
    }

    public static void main(String[] args) throws Exception {
        Path contract = Path.of(System.getProperty("archvow.contract.root", "../contract"));
        JsonArray expected = JsonParser.parseString(Files.readString(contract.resolve("overview/expected.json"))).getAsJsonArray();
        List<String> examples = List.of("fastapi-service", "pyspark-pipeline", "python-app");
        check(expected.size() == examples.size(), "expected.json is not of these examples: UPDATE_OVERVIEW=1 npm test");
        for (int i = 0; i < examples.size(); i++) {
            String example = examples.get(i);
            JsonObject w = JsonParser.parseString(Files.readString(contract.resolve("cli/" + example + "/workspace.json"))).getAsJsonObject();
            JsonObject want = expected.get(i).getAsJsonObject();
            check(example.equals(want.get("example").getAsString()), "order of examples: " + example);
            JsonElement steps = json(ArchvowOverview.firstSteps(w.has("first_steps") ? w.getAsJsonArray("first_steps") : null));
            check(steps.equals(want.get("firstSteps")), example + " first steps: " + steps + " != " + want.get("firstSteps"));
            JsonElement finding = json(ArchvowOverview.firstFinding(w.getAsJsonObject("check")));
            check(finding.equals(want.get("firstFinding")), example + " first finding: " + finding + " != " + want.get("firstFinding"));
            JsonArray rows = new JsonArray();
            for (JsonElement e : w.getAsJsonArray("next")) {
                JsonObject r = e.getAsJsonObject();
                if (!r.has("suggested")) continue;
                JsonObject row = new JsonObject();
                row.addProperty("component", ArchvowGrammar.str(r, "component"));
                row.addProperty("en", ArchvowOverview.suggested(ArchvowGrammar.number(r, "suggested"), ArchvowGrammar.str(r, "readiness"), false));
                row.addProperty("fr", ArchvowOverview.suggested(ArchvowGrammar.number(r, "suggested"), ArchvowGrammar.str(r, "readiness"), true));
                rows.add(row);
            }
            check(rows.equals(want.get("suggested")), example + " suggested: " + rows + " != " + want.get("suggested"));
        }
        // the card goes once the five are done; the first not done is the current one
        JsonArray all = new JsonArray();
        for (String name : List.of("a", "b")) {
            JsonObject s = new JsonObject();
            s.addProperty("step", name);
            s.addProperty("done", true);
            all.add(s);
        }
        check(ArchvowOverview.firstSteps(all) == null && ArchvowOverview.firstSteps(new JsonArray()) == null, "all done: no card");
        all.get(1).getAsJsonObject().addProperty("done", false);
        ArchvowOverview.FirstSteps some = ArchvowOverview.firstSteps(all);
        check(some != null && some.current() == 1 && some.done() == 1, "the current step");
        check(ArchvowOverview.suggested(4, "blocked: 9 decisions to make", false).equals("4 of the 9 decisions"), "suggested");
        check(ArchvowOverview.percent(45, 70) == 64 && ArchvowOverview.percent(0, 0) == 0, "percent");
        // the cards draw what the VS Code page draws (ArchvowOverviewCards), from the spec-phase pyspark example
        JsonObject spec = JsonParser.parseString(Files.readString(contract.resolve("cli/pyspark-pipeline/workspace.json"))).getAsJsonObject();
        ArchvowOverview.FirstSteps steps = ArchvowOverview.firstSteps(spec.getAsJsonArray("first_steps"));
        List<String> clicked = new java.util.ArrayList<>();
        ArchvowOverviewCards.Actions actions = new ArchvowOverviewCards.Actions() {
            @Override public void openComponents() { clicked.add("components"); }
            @Override public void openItems(int list) { clicked.add("items " + list); }
            @Override public void openProjectItems() { clicked.add("project"); }
            @Override public void openChecks() { clicked.add("checks"); }
            @Override public void openFile(String path, int line) { clicked.add("file " + path + ":" + line); }
            @Override public void step(ArchvowOverview.FirstStep step) { clicked.add("step " + step.step()); }
            @Override public void tryExample() { clicked.add("try"); }
        };
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            javax.swing.JPanel first = ArchvowOverviewCards.firstSteps(steps, false, actions);
            String text = texts(first);
            check(text.contains("First steps") && text.contains("3 of 5 done") && text.contains("Build it, then Check"), "first steps: " + text);
            press(first, "Check");
            press(first, "Try archvow on an example first");
            javax.swing.JPanel arch = ArchvowOverviewCards.architecture(11, 45, 25, 2, false, false, actions);
            String a = texts(arch);
            check(a.contains("11 components") && a.contains("45 of 70") && a.contains("25 open: decisions, questions and tasks. Check findings count in Checks.")
                && a.contains("Open items (25)") && a.contains("Done items (45)") && a.contains("Project items (2)"), "architecture: " + a);
            check(!texts(ArchvowOverviewCards.architecture(3, 0, 4, 0, true, false, actions)).contains("open: decisions"), "the First steps card says it");
            press(arch, "Open items (25)");
            press(arch, "Project items (2)");
            JsonObject failing = JsonParser.parseString("{\"errors\":1,\"warnings\":0,\"violations\":[{\"code\":\"ARCH005\",\"severity\":\"error\","
                + "\"title\":\"Illegal dependency between layers\",\"file\":\"src/x/p1_domain/compute_vat.py\",\"line\":16}]}").getAsJsonObject();
            javax.swing.JPanel checks = ArchvowOverviewCards.checks(1, 0, ArchvowOverview.firstFinding(failing), false, actions);
            String c = texts(checks);
            check(c.contains("First finding") && c.contains("Illegal dependency between layers") && c.contains("ARCH005 · compute_vat.py:16")
                && c.contains("Open source"), "checks: " + c);
            press(checks, "Open source");
            javax.swing.JPanel bar = ArchvowOverviewCards.toolbar(() -> clicked.add("next"), () -> clicked.add("check"),
                List.of(new ArchvowOverviewCards.MenuCommand("health", "Doctor", () -> clicked.add("doctor"))), false, null);
            check(texts(bar).contains("Next") && texts(bar).contains("Check") && texts(bar).contains("More"), "toolbar: " + texts(bar));
        });
        check(clicked.equals(List.of("step build", "try", "items 0", "project", "file src/x/p1_domain/compute_vat.py:16")), "actions: " + clicked);
        // try's form says where the example goes (0.25.1)
        check(ArchvowOverview.tryWhere("", "", "/tmp", false).equals("/tmp/archvow-try-…/pyspark-pipeline"), "try: the temporary folder");
        check(ArchvowOverview.tryWhere("python-app", "/home/me/demo/", "/tmp", false).equals("/home/me/demo/python-app"), "try: the chosen folder");
        check(ArchvowOverview.tryWhere("python-app", "", "C:\\Temp", false).equals("C:\\Temp\\archvow-try-…\\python-app"), "try: Windows");
        System.out.println("OverviewRegression OK");
    }
}
