package org.archvow.idea;

import com.google.gson.JsonObject;
import java.util.List;

/** Status follows archvow's safety rules, not only the worksheet's 'answered' flag. */
final class ArchvowSafetyState {
    private ArchvowSafetyState() {}
    static boolean requiresInvariant(JsonObject hazard) {
        String severity = ArchvowGrammar.str(hazard, "severity");
        return "high".equalsIgnoreCase(severity) || "critical".equalsIgnoreCase(severity);
    }
    static String unprotectedHazard(JsonObject question) {
        for (JsonObject hazard : ArchvowGrammar.objects(question, "hazards")) {
            if (requiresInvariant(hazard) && ArchvowGrammar.objects(hazard, "prevented_by").isEmpty())
                return ArchvowGrammar.str(hazard, "id");
        }
        return "";
    }
    static boolean pending(JsonObject question) {
        return question != null && ("open".equalsIgnoreCase(ArchvowGrammar.str(question, "status"))
            || !unprotectedHazard(question).isBlank());
    }
    static String status(JsonObject question) {
        if ("open".equalsIgnoreCase(ArchvowGrammar.str(question, "status"))) return "OPEN";
        if (!unprotectedHazard(question).isBlank()) return "NEEDS INVARIANT";
        return "RESOLVED";
    }
    static String suggestedHazard(JsonObject question) {
        return suggestion(question, "hazard");
    }
    static String suggestedInvariant(JsonObject question) {
        return suggestion(question, "invariant");
    }
    private static String suggestion(JsonObject question, String field) {
        if (question == null || !question.has("suggestion") || !question.get("suggestion").isJsonObject()) return "";
        return ArchvowGrammar.str(question.getAsJsonObject("suggestion"), field);
    }
    static String preview(JsonObject question) {
        StringBuilder text = new StringBuilder();
        String hazard = suggestedHazard(question);
        String invariant = suggestedInvariant(question);
        if (!hazard.isBlank()) text.append("Suggested hazard / Hazard suggéré: ").append(hazard).append("\n");
        if (!invariant.isBlank()) text.append("Suggested invariant / Invariant suggéré: ").append(invariant).append("\n");
        for (JsonObject h : ArchvowGrammar.objects(question, "hazards")) {
            text.append("Hazard ").append(ArchvowGrammar.str(h, "id")).append(" (")
                .append(ArchvowGrammar.str(h, "severity")).append(") — ")
                .append(ArchvowGrammar.str(h, "description")).append("\n");
            List<JsonObject> protections = ArchvowGrammar.objects(h, "prevented_by");
            for (JsonObject i : protections) {
                text.append("  ↳ Invariant ").append(ArchvowGrammar.str(i, "id")).append(": ")
                    .append(ArchvowGrammar.str(i, "statement"));
                if (i.has("tested") && !i.get("tested").isJsonNull() && !i.get("tested").getAsBoolean())
                    text.append(" [test not verified / test non vérifié]");
                text.append("\n");
            }
            if (protections.isEmpty() && requiresInvariant(h))
                text.append("  ⚠ ARCH030 — invariant required / invariant obligatoire\n");
        }
        return text.toString();
    }
}
