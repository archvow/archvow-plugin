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
        return prepare(command, requested, selectedComponent, componentSnapshot, null);
    }

    /**
     * As above; {@code specEntry} is the component in the spec (show --json): its ticket, owners, group,
     * brief... which `show <component>` does not give. Edit spec opens with every field the component
     * already has, so a person sees what is there and changes what he wants (0.11.3).
     */
    static Map<String, String> prepare(String command, Map<String, String> requested,
                                       String selectedComponent, JsonObject componentSnapshot, JsonObject specEntry) {
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
                // field of the form <- where its value is: show <component>, then the spec's entry
                String[][] fields = {{"description", "description"}, {"spec", "spec"}, {"signature", "signature"},
                    {"uses", "uses"}, {"group", "group"}, {"owners", "owners"}, {"source", "from"}, {"source", "brief"}};
                for (String[] field : fields) {
                    if (data.containsKey(field[0])) continue;
                    String known = printable(componentSnapshot.get(field[1]));
                    if (known.isBlank() && specEntry != null) known = printable(specEntry.get(field[1]));
                    if (!known.isBlank()) data.put(field[0], known);
                }
            }
        }
        return Map.copyOf(data);
    }

    /**
     * The id {@code hazard remove} / {@code invariant remove} takes: {@code show --json} already gives it
     * with its component (clean_orders.H1), kept as it is; a bare H1 / INV1 gets the component.
     */
    static String itemId(String component, String id) {
        String value = id == null ? "" : id.trim();
        if (value.matches("(?i)[A-Za-z_][A-Za-z0-9_]*\\.(H|INV)\\d+")) return value;
        return component == null || component.isBlank() ? value : component + "." + value;
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
