package org.archvow.idea;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Offline reference: `archvow catalog --json` of the archvow the plugin was built for
 * (../contract/cli/catalog.json, copied into the plugin by the build).
 * Use the installed CLI result whenever possible (especially for company packs).
 * The reference makes init questions accessible even on PyCharm Welcome Screen
 * before a Python interpreter has been configured for that pseudo-project.
 */
final class ArchvowInitCatalog {
    private ArchvowInitCatalog() { }

    static JsonObject bundled() {
        try (InputStream source = ArchvowInitCatalog.class.getResourceAsStream(
                 "/archvow/catalog.json")) {
            if (source == null) return null;
            JsonObject data = JsonParser.parseReader(new InputStreamReader(source, StandardCharsets.UTF_8))
                .getAsJsonObject();
            return hasTemplates(data) ? data : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * The kinds of each component type (component add -k), as archvow/kinds.json records them for an archvow
     * whose catalog does not list them (1.17); null when absent.
     */
    static JsonObject kinds() {
        try (InputStream source = ArchvowInitCatalog.class.getResourceAsStream("/archvow/kinds.json")) {
            if (source == null) return null;
            return JsonParser.parseReader(new InputStreamReader(source, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ignored) {
            return null;
        }
    }

    static boolean hasTemplates(JsonObject data) {
        return data != null && data.has("templates")
            && data.get("templates").isJsonArray()
            && data.getAsJsonArray("templates").size() > 0;
    }
}
