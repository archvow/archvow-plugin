package org.archvow.idea;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** Only actual known spec values; explicit diagnostic fixes always take precedence. */
final class ArchvowCommandPrefill {
    private ArchvowCommandPrefill() {}

    static Map<String, String> prepare(String command, Map<String, String> requested,
                                       String selectedComponent, JsonObject componentSnapshot) {
        Map<String, String> data = new LinkedHashMap<>();
        if (requested != null) data.putAll(requested);
        // Never overwrite the target supplied by an issue; the currently selected tree
        // component can be different from the component referenced by a diagnostic.
        if (usesComponent(command) && empty(data.get("component")) && !empty(selectedComponent)) {
            data.put("component", selectedComponent);
        }
        String target = data.getOrDefault("component", "");
        if ("component edit".equals(command) && componentSnapshot != null &&
            target.equals(ArchvowGrammar.str(componentSnapshot, "component"))) {
            // A specific fix (signature/uses/description) must not accidentally mutate
            // additional properties. Only a generic "Edit spec" action loads existing fields.
            boolean specific = data.keySet().stream().anyMatch(k -> !"component".equals(k));
            if (!specific) {
                for (String property : new String[]{"description", "spec", "signature", "uses"}) {
                    String known = printable(componentSnapshot.get(property));
                    if (!known.isBlank()) data.put(property, known);
                }
            }
        }
        return Map.copyOf(data);
    }

    static boolean usesComponent(String command) {
        if (command == null) return false;
        return switch (command) {
            case "component edit", "component remove", "question add", "question remove",
                "decision make", "decision propose", "decision accept", "decision add",
                "hazard add", "hazard dismiss", "hazard remove", "invariant add", "invariant remove",
                "assumption add", "assumption accept", "assumption reject", "column set", "specify" -> true;
            default -> false;
        };
    }

    private static boolean empty(String text) { return text == null || text.isBlank(); }

    static String printable(JsonElement value) {
        if (value == null || value.isJsonNull()) return "";
        if (!value.isJsonObject() && !value.isJsonArray()) return value.getAsString();
        if (value.isJsonArray()) {
            StringBuilder result = new StringBuilder();
            JsonArray array = value.getAsJsonArray();
            for (JsonElement part : array) {
                if (part == null || (part.isJsonObject() || part.isJsonArray() || part.isJsonNull())) return "";
                if (result.length() > 0) result.append(',');
                result.append(part.getAsString());
            }
            return result.toString();
        }
        return ""; // Objects need human interpretation; never guess.
    }
}
