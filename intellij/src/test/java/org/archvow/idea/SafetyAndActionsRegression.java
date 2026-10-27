package org.archvow.idea;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.Set;

/** Offline regression using fixture Gson primitives; does not mock Archvow business rules. */
public final class SafetyAndActionsRegression {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static JsonObject safety(String severity, boolean invariant) {
        JsonObject q = new JsonObject();
        q.addProperty("status", "answered");
        JsonArray hazards = new JsonArray();
        JsonObject hazard = new JsonObject();
        hazard.addProperty("id", "reader.H1");
        hazard.addProperty("severity", severity);
        hazard.addProperty("description", "Incorrect data reaches users");
        JsonArray prevents = new JsonArray();
        if (invariant) {
            JsonObject inv = new JsonObject();
            inv.addProperty("id", "reader.INV1");
            inv.addProperty("statement", "Reject malformed data");
            inv.addProperty("tested", false);
            prevents.add(inv);
        }
        hazard.add("prevented_by", prevents);
        hazards.add(hazard);
        q.add("hazards", hazards);
        return q;
    }
    public static void main(String[] args) {
        JsonObject high = safety("high", false);
        check(ArchvowSafetyState.pending(high), "High without invariant must remain pending");
        check("NEEDS INVARIANT".equals(ArchvowSafetyState.status(high)), "Wrong hazard status");
        check("reader.H1".equals(ArchvowSafetyState.unprotectedHazard(high)), "Missing hazard reference");
        check(ArchvowSafetyState.preview(high).contains("ARCH030"), "Missing invariant warning");
        JsonObject critical = safety("critical", false);
        check(ArchvowSafetyState.pending(critical), "Critical hazard unprotected");
        JsonObject protectedHigh = safety("high", true);
        check(!ArchvowSafetyState.pending(protectedHigh), "Invariant makes hazard protected");
        check(ArchvowSafetyState.preview(protectedHigh).contains("test not verified"), "Missing evidence warning");
        check(!ArchvowSafetyState.pending(safety("medium", false)), "Medium hazard needs no invariant by rule");
        check(!ArchvowSafetyState.pending(safety("low", false)), "Low hazard needs no invariant by rule");
        JsonObject open = new JsonObject();
        open.addProperty("status", "open");
        JsonObject suggestions = new JsonObject();
        suggestions.addProperty("hazard", "Duplicates leak through");
        suggestions.addProperty("invariant", "One row per key");
        open.add("suggestion", suggestions);
        check(ArchvowSafetyState.pending(open), "Open safety pending");
        check(ArchvowSafetyState.preview(open).contains("One row per key"), "Invariant suggestion not exposed");
        check(ArchvowSafetyState.suggestedHazard(open).equals("Duplicates leak through"), "Hazard suggestion absent");

        String arch019 = "'sales_reader' uses no schema: archvow component add schema <name> -d \"...\", then archvow component edit sales_reader -u <name>_schema";
        check("sales_reader".equals(ArchvowIssueAction.quotedComponent(arch019, Set.of("sales_reader"))), "Component extraction");
        ArchvowIssueAction.Fix schema = ArchvowIssueAction.fix("ARCH019", arch019, "sales_reader");
        check(schema != null && "component add".equals(schema.command()), "Schema fix missing");
        check("schema".equals(schema.values().get("type")), "Schema type not prefilled");
        check(!schema.values().containsKey("name"), "Cannot invent a schema name");
        ArchvowIssueAction.Fix signature = ArchvowIssueAction.fix("ARCH004",
            "archvow component edit sales_reader --signature \"(spark: SparkSession, path: str, run_date: date) -> DataFrame\"", "sales_reader");
        check(signature != null && signature.values().get("signature").contains("run_date"), "Signature prefilling");
        ArchvowIssueAction.Fix invariant = ArchvowIssueAction.fix("ARCH030",
            "'reader': H1 high -> archvow invariant add reader \"...\" --prevents reader.H1", "reader");
        check(invariant != null && "reader.H1".equals(invariant.values().get("prevents")), "Invariant target prefilling");
        check(ArchvowIssueAction.fix("ARCH999", "unknown", "reader") == null, "Unknown issue must not mutate spec");
        System.out.println("OK: " + checks + " safety and issue-action checks");
    }
}
