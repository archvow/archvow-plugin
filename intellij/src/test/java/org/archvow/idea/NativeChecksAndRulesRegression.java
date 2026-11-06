package org.archvow.idea;
import com.google.gson.*;
import javax.swing.*;

public final class NativeChecksAndRulesRegression {
    private static boolean containsList(java.awt.Container parent) {
        for (java.awt.Component child : parent.getComponents()) {
            if (child instanceof JList<?>) return true;
            if (child instanceof java.awt.Container nested && containsList(nested)) return true;
        }
        return false;
    }

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
        ArchvowChecksView navigation = new ArchvowChecksView(false);
        if (!view.showsInlineList() || !navigation.showsInlineList())
            throw new AssertionError("Every check view must expose all diagnostic rows");
        if (view.getComponentCount() != 2 || navigation.getComponentCount() != 3)
            throw new AssertionError("Editor must have its summary and the list-plus-detail panel");
        if (!containsList(view) || !containsList(navigation))
            throw new AssertionError("Editor and navigation must contain a visible diagnostics list");
        view.showData(check, "", true);
        if (view.diagnosticCount() != 2) throw new AssertionError("Diagnostics missing");
        if (!view.statusText().contains("3 erreurs") || !view.statusText().contains("1 avertissements"))
            throw new AssertionError("Counts missing: " + view.statusText());
        if (!"ARCH019".equals(view.diagnosticCode(0))) throw new AssertionError("Code mismatch");
        view.selectDiagnostic(1);
        if (!"Invariant missing".equals(view.selectedDiagnosticMessage()))
            throw new AssertionError("Editor did not select the requested diagnostic index");
        view.showData(check, "", false);
        if (!"Invariant missing".equals(view.selectedDiagnosticMessage()))
            throw new AssertionError("Selection lost on locale refresh");
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
        JsonObject third = new JsonObject();
        third.addProperty("code", "ARCH019"); // two errors with the same code must stay separate rows
        third.addProperty("severity", "error");
        third.addProperty("message", "Second component schema missing");
        violations.add(third);
        view.showData(check, "", false);
        if (view.diagnosticCount() != 3) throw new AssertionError("All three violations must appear in the editor");
        view.selectDiagnostic(2);
        if (!"Second component schema missing".equals(view.selectedDiagnosticMessage()))
            throw new AssertionError("Third check must be selectable by its own row");
        System.out.println("OK native diagnostics 10");
    }
}
