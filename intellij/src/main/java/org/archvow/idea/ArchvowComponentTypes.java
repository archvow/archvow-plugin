package org.archvow.idea;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.*;

/** Reference types from the Archvow catalog, not a hard-coded invented enum. */
final class ArchvowComponentTypes {
    private static final Map<String, JsonObject> TYPES = load();
    private ArchvowComponentTypes() { }

    private static Map<String, JsonObject> load() {
        Map<String, JsonObject> result = new TreeMap<>();
        JsonObject catalog = ArchvowInitCatalog.bundled();
        for (JsonObject template : ArchvowGrammar.objects(catalog, "templates")) {
            JsonElement types = template.get("component_types");
            if (types == null || !types.isJsonObject()) continue;
            for (var entry : types.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    JsonObject definition = entry.getValue().getAsJsonObject();
                    if (ArchvowGrammar.flag(definition, "addable"))
                        result.putIfAbsent(entry.getKey(), definition);
                }
            }
        }
        return Collections.unmodifiableMap(result);
    }

    static List<String> allKnown() { return List.copyOf(TYPES.keySet()); }

    static String nameHelp(String type) {
        JsonObject def = TYPES.get(type);
        if (def == null) return "Check the selected template's component naming rules.";
        String pattern = ArchvowGrammar.str(def, "name_pattern");
        String hint = ArchvowGrammar.str(def, "name_hint");
        return type + " — " + ArchvowGrammar.str(def, "description")
            + (hint.isBlank() ? "" : " | Example: " + hint)
            + (pattern.isBlank() ? "" : " | name_pattern: " + pattern);
    }
}
