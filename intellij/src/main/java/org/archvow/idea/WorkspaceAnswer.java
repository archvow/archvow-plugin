package org.archvow.idea;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code archvow workspace --json} (archvow 1.25, contract 1.1), read: what the plugin shows of a project, from one
 * process - the spec ({@code show --json}), each component's view ({@code show <id> --json}), the build plan
 * ({@code next --json}), the findings ({@code check --json}), the briefs ({@code brief show --json}) and the templates
 * ({@code catalog --json}), each in its own command's shape. VS Code reads it the same way (model.ts readWorkspace).
 */
final class WorkspaceAnswer {
    /** Given back with --since: archvow answers {@code unchanged} while no file of the project changed. */
    final String digest;
    /** The earlier answer still holds (--since): nothing else is filled. */
    final boolean unchanged;
    final JsonObject spec;
    /** Each component's view; one that is not its own (a search result) is left out, as with {@code show <id>}. */
    final Map<String, JsonObject> snapshots;
    /** The build plan, as {@code next --json} prints it (the JSON text). */
    final String next;
    final JsonObject check;
    final JsonObject briefs;
    final JsonObject catalog;

    private WorkspaceAnswer(String digest, boolean unchanged, JsonObject spec, Map<String, JsonObject> snapshots, String next,
                            JsonObject check, JsonObject briefs, JsonObject catalog) {
        this.digest = digest;
        this.unchanged = unchanged;
        this.spec = spec;
        this.snapshots = snapshots;
        this.next = next;
        this.check = check;
        this.briefs = briefs;
        this.catalog = catalog;
    }

    /** What archvow printed, or null when it is not an answer (a failure: its error envelope, or nothing). */
    static WorkspaceAnswer read(String output) {
        JsonObject answer = output == null ? null : ArchvowGrammar.parseObject(output);
        if (answer == null || !answer.has("digest") || !answer.get("digest").isJsonPrimitive()) return null;
        String digest = answer.get("digest").getAsString();
        if (answer.has("unchanged") && answer.get("unchanged").isJsonPrimitive() && answer.get("unchanged").getAsBoolean())
            return new WorkspaceAnswer(digest, true, null, Map.of(), "", null, null, null);
        JsonObject spec = object(answer, "project");
        JsonObject check = object(answer, "check");
        JsonObject views = object(answer, "components");
        JsonElement next = answer.get("next");
        if (spec == null || check == null || !check.has("violations") || !check.get("violations").isJsonArray()
                || views == null || next == null || !next.isJsonArray()) return null;
        Map<String, JsonObject> snapshots = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : views.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject view = entry.getValue().getAsJsonObject();
            if (entry.getKey().equals(ArchvowGrammar.str(view, "component"))) snapshots.put(entry.getKey(), view);
        }
        return new WorkspaceAnswer(digest, false, spec, snapshots, next.toString(), check, object(answer, "briefs"),
            object(answer, "catalog"));
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }
}
