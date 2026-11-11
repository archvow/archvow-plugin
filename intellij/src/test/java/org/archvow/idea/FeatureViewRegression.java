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
        JsonObject shared = workspace.getAsJsonObject("features").getAsJsonObject("shared");
        String fr = ArchvowFeatureView.html(shared, spec, Map.of(), Map.of(), true);
        check(fr.contains("Composants, par couche") && fr.contains("Rien d'ouvert"), "in French");
        check(fr.contains("Utilise <a href='feature:orders'>orders</a>"), "shared's entry point uses orders");
        check(ArchvowFeatureView.html(null, spec, Map.of(), Map.of(), false).contains("Choose a feature"), "no feature chosen");
        System.out.println("OK: a feature's page - head, map, components by layer, what is open (EN and FR)");
    }
}
