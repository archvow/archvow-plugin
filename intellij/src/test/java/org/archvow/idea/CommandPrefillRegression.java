package org.archvow.idea;

import com.google.gson.*;
import java.util.Map;

/** Prefill must use real known values and never erase a diagnostic fix. */
public final class CommandPrefillRegression {
    public static void main(String[] args) {
        JsonObject component = new JsonObject();
        component.addProperty("component", "sales_reader");
        component.addProperty("description", "Read partitioned data");
        component.addProperty("signature", "(spark, path) -> DataFrame");
        JsonArray uses = new JsonArray();
        uses.add(new JsonPrimitive("config"));
        uses.add(new JsonPrimitive("sales_schema"));
        component.add("uses", uses);
        Map<String,String> generic = ArchvowCommandPrefill.prepare("component edit", Map.of("component", "sales_reader"), "other", component);
        assertEqual("Read partitioned data",generic.get("description"));
        assertEqual("config,sales_schema",generic.get("uses"));
        assertEqual("(spark, path) -> DataFrame",generic.get("signature"));
        Map<String,String> diagnostic = ArchvowCommandPrefill.prepare("component edit",
            Map.of("component", "sales_reader", "signature", "(spark,path,date) -> DataFrame"), "other", component);
        assertEqual("(spark,path,date) -> DataFrame",diagnostic.get("signature"));
        if (diagnostic.containsKey("description") || diagnostic.containsKey("uses"))
            throw new AssertionError("specific diagnostic must not add unrelated mutations");
        Map<String,String> contextual = ArchvowCommandPrefill.prepare("column set", Map.of("names", "email"), "sales_reader", component);
        assertEqual("sales_reader",contextual.get("component"));
        assertEqual("email",contextual.get("names"));
        Map<String,String> other = ArchvowCommandPrefill.prepare("invariant add", Map.of("component", "schema", "prevents", "H1"), "sales_reader", component);
        assertEqual("schema",other.get("component"));
        if (ArchvowCommandPrefill.prepare("component add", Map.of("type", "schema"), "sales_reader", component).containsKey("component"))
            throw new AssertionError("component add has no component argument");
        // Edit spec from a component (0.11.3): what show <component> does not give comes from the spec's entry
        JsonObject entry = JsonParser.parseString("{\"id\":\"sales_reader\",\"spec\":\"FATTRIB-12\",\"group\":\"readers\","
            + "\"owners\":[\"@alice\",\"@data-team\"],\"from\":\"briefs/sales.md#reader\"}").getAsJsonObject();
        Map<String,String> whole = ArchvowCommandPrefill.prepare("component edit", Map.of("component", "sales_reader"), "", component, entry);
        assertEqual("FATTRIB-12", whole.get("spec"));
        assertEqual("readers", whole.get("group"));
        assertEqual("@alice,@data-team", whole.get("owners"));
        assertEqual("briefs/sales.md#reader", whole.get("source"));
        assertEqual("Read partitioned data", whole.get("description"));
        Map<String,String> fix = ArchvowCommandPrefill.prepare("component edit",
            Map.of("component", "sales_reader", "uses", "config"), "", component, entry);
        if (fix.containsKey("spec") || fix.containsKey("owners")) throw new AssertionError("a specific fix stays specific: " + fix);
        // hazard / invariant remove: show --json gives clean_sales.H1; never clean_sales.clean_sales.H1
        assertEqual("clean_sales.H1", ArchvowCommandPrefill.itemId("clean_sales", "clean_sales.H1"));
        assertEqual("clean_sales.INV2", ArchvowCommandPrefill.itemId("clean_sales", "clean_sales.INV2"));
        assertEqual("clean_sales.H1", ArchvowCommandPrefill.itemId("clean_sales", "H1"));
        assertEqual("H1", ArchvowCommandPrefill.itemId("", "H1"));
        System.out.println("OK: 7 grounded prefill scenarios, and the ids of hazard / invariant remove");
    }
    static void assertEqual(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + "; got " + actual);
    }
}
