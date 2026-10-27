package org.archvow.idea;
import com.google.gson.*;
import javax.swing.*;

public final class NativeChecksAndRulesRegression {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        JsonObject check = new JsonObject();
        check.addProperty("ok", false);
        check.addProperty("errors", "3");
        check.addProperty("warnings", "1");
        JsonArray violations = new JsonArray();
        JsonObject v = new JsonObject();
        v.addProperty("code", "ARCH019");
        v.addProperty("severity", "error");
        v.addProperty("message", "Reader schema missing");
        violations.add(v);
        JsonObject w = new JsonObject();
        w.addProperty("code", "ARCH030");
        w.addProperty("severity", "warning");
        w.addProperty("message", "Invariant missing");
        violations.add(w);
        check.add("violations", violations);
        ArchvowChecksView view = new ArchvowChecksView();
        view.showData(check, "", true);
        if (view.diagnosticCount() != 2) throw new AssertionError("Diagnostics missing");
        if (!view.statusText().contains("3 erreurs") || !view.statusText().contains("1 avertissements"))
            throw new AssertionError("Counts missing: " + view.statusText());
        if (!"ARCH019".equals(view.diagnosticCode(0))) throw new AssertionError("Code mismatch");
        view.showData(check, "", false);
        if (!view.statusText().contains("3 errors")) throw new AssertionError("English missing");
        view.showData(null, "missing CLI", true);
        if (!view.statusText().contains("indisponible")) throw new AssertionError("Unavailable missing");
        if (!view.byRuleText().isEmpty()) throw new AssertionError("No counts from archvow: no line");
        JsonObject counts = new JsonObject();
        counts.addProperty("ARCH019", 2);
        counts.addProperty("ARCH030", 1);
        check.add("counts", counts);
        view.showData(check, "", false);
        if (!"By rule: ARCH019 2  ·  ARCH030 1".equals(view.byRuleText()))
            throw new AssertionError("By rule missing: " + view.byRuleText());
        view.showData(check, "", true);
        if (!view.byRuleText().startsWith("Par règle : ARCH019 2")) throw new AssertionError("French by rule");
        System.out.println("OK native diagnostics 9");
    }
}
