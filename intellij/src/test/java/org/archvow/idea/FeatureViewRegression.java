package org.archvow.idea;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** A feature's page (0.24.0, contract 2.0) on the recorded workspace of the fastapi example, headless. */
public final class FeatureViewRegression {
    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    private static javax.swing.JButton find(java.awt.Container in, String name) {
        for (java.awt.Component c : in.getComponents()) {
            if (c instanceof javax.swing.JButton b && name.equals(b.getName())) return b;
            if (c instanceof java.awt.Container inner) {
                javax.swing.JButton found = find(inner, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        Path contract = Path.of(System.getProperty("archvow.contract.root", "../contract"));
        JsonObject workspace = JsonParser.parseString(Files.readString(contract.resolve("cli/fastapi-service/workspace.json"))).getAsJsonObject();
        JsonObject spec = workspace.getAsJsonObject("project");
        JsonObject orders = workspace.getAsJsonObject("features").getAsJsonObject("orders");
        String html = ArchvowFeatureView.html(orders, spec, Map.of("create_order", 2), Map.of("create_order", "decision 3: what if the customer has no address?"), false);
        check(ArchvowHtml.wellSized(html), "no px size, no h1-h6");
        check(html.contains("orders") && html.contains("2 open"), "its name and what is open");
        check(html.contains("<a href='component:orders_router'>orders_router</a>"), "each component opens");
        check(html.contains("p4_api") && html.indexOf("p4_api") < html.indexOf("p1_domain"), "by layer, the entry points first");
        check(html.contains("Used by <a href='feature:shared'>shared</a>"), "the main of shared uses orders: " + html);
        check(html.contains("decision 3: what if the customer has no address?"), "what is open in it");
        check(html.contains("Where it is") && html.contains("orders_router.vow") && html.contains("&nbsp;&nbsp;&nbsp;&nbsp;└──&nbsp;")
            && html.contains("<a href='file:src/orders_api/p4_api/orders_router.py'>orders_router.py</a>"), "its files as a tree, the code a link");
        check(ArchvowFilePlan.componentFiles(spec, "create_order", false).stream().map(ArchvowFilePlan.Row::path).toList().equals(java.util.List.of(
            ".archvow/features/orders/components/create_order.vow", "src/orders_api/p3_services/create_order.py", "tests/p3_services/test_create_order.py")),
            "where a component is: its vow, its code, its test");
        JsonObject shared = workspace.getAsJsonObject("features").getAsJsonObject("shared");
        String fr = ArchvowFeatureView.html(shared, spec, Map.of(), Map.of(), true);
        check(fr.contains("Composants, par couche") && fr.contains("Rien d'ouvert"), "in French");
        check(fr.contains("Utilise <a href='feature:orders'>orders</a>"), "shared's entry point uses orders");
        check(ArchvowFeatureView.html(null, spec, Map.of(), Map.of(), false).contains("Choose a feature"), "no feature chosen");
        // contract 2.3: a feature an AI agent added says so, and waits for a person to accept it (feature accept)
        check(!ArchvowFeatureView.addedByAi(orders) && !html.contains("ARCH043"), "a person's feature: nothing to accept");
        JsonObject refunds = orders.deepCopy();
        refunds.addProperty("feature", "refunds");
        refunds.addProperty("origin", "ai");
        refunds.addProperty("created_by", "claude-code");
        check(ArchvowFeatureView.addedByAi(refunds), "an AI agent's feature: Accept… shows");
        check(ArchvowFeatureView.html(refunds, spec, Map.of(), Map.of(), false).contains("added by claude-code: a person accepts it (ARCH043 until then)"), "who added it");
        check(ArchvowFeatureView.html(refunds, spec, Map.of(), Map.of(), true).contains("ajoutée par claude-code : une personne l'accepte"), "who added it, in French");
        // archvow 2.8.0: Check before coding, after Copy AI prompt - archvow prompt --feature <feature> --clarify
        check(ArchvowFeatureView.promptArgs("orders", true).equals(java.util.List.of("prompt", "--feature", "orders", "--clarify")), "the check");
        check(ArchvowFeatureView.promptArgs("orders", false).equals(java.util.List.of("prompt", "--feature", "orders")), "the prompt, unchanged");
        java.util.List<String> copied = new java.util.ArrayList<>();
        ArchvowFeatureView view = new ArchvowFeatureView(new ArchvowFeatureView.Actions() {
            @Override public void showComponent(String id) { }
            @Override public void showFeature(String name) { }
            @Override public void openFile(String relative) { }
            @Override public void openCommand(String command, Map<String, String> values) { }
            @Override public void openDoc(String feature) { }
            @Override public void copyPrompt(String feature, boolean check) { copied.add(feature + (check ? " check" : " prompt")); }
            @Override public void nextSteps(String feature) { }
        });
        view.show(orders, spec, Map.of(), Map.of(), false);
        javax.swing.JButton button = find(view, "archvow.feature.check");
        check(button != null && "Check before coding".equals(button.getText()), "its label");
        check(button.getToolTipText().startsWith("archvow prompt --feature orders --clarify: the AI checks the whole feature before any code")
            && button.getToolTipText().endsWith("It writes no code."), "what it does, on hover: " + button.getToolTipText());
        java.awt.Container row = button.getParent();
        java.util.List<String> labels = new java.util.ArrayList<>();
        for (java.awt.Component c : row.getComponents()) if (c instanceof javax.swing.JButton b) labels.add(b.getText());
        check(labels.indexOf("Check before coding") == labels.indexOf("Copy AI prompt") + 1, "right after Copy AI prompt: " + labels);
        for (java.awt.event.ActionListener l : button.getActionListeners()) l.actionPerformed(new java.awt.event.ActionEvent(button, 0, ""));
        check(copied.equals(java.util.List.of("orders check")), "it copies the check of this feature: " + copied);
        view.show(orders, spec, Map.of(), Map.of(), true);
        check("Vérifier avant de coder".equals(button.getText()) && button.getToolTipText().contains("l'IA vérifie toute la fonctionnalité"), "in French");
        System.out.println("OK: a feature's page - head, map, components by layer, what is open, an AI agent's to accept, Check before coding (EN and FR)");
    }
}
