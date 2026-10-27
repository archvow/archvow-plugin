package org.archvow.idea;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** The CLI forms are built from archvow's own parser (../contract/cli/commands.json, copied by the build). */
final class ArchvowGrammar {
    private ArchvowGrammar() {}

    static final class Param {
        final String name, help, metavar;
        final List<String> flags, choices;
        final boolean required, bool, append, multiToken, optionalValue;
        Param(JsonObject obj) {
            name = str(obj, "name"); help = str(obj, "help"); metavar = str(obj, "metavar");
            flags = strings(obj, "flags"); choices = strings(obj, "choices");
            required = flag(obj, "required"); bool = flag(obj, "boolean");
            append = flag(obj, "append"); multiToken = flag(obj, "multiToken");
            optionalValue = flag(obj, "optionalValue");
        }
        String option() { return flags.isEmpty() ? "" : flags.get(flags.size() - 1); }
    }
    static final class Command {
        final List<String> path;
        final String label, description;
        final boolean json;
        final List<Param> parameters;
        Command(JsonObject obj) {
            path = strings(obj, "path"); label = String.join(" ", path);
            description = str(obj, "description"); json = flag(obj, "json");
            List<Param> ps = new ArrayList<>();
            for (JsonObject p : objects(obj, "parameters")) ps.add(new Param(p));
            parameters = Collections.unmodifiableList(ps);
        }
        @Override public String toString() { return label; }
    }

    static final List<Command> COMMANDS = load();

    private static List<Command> load() {
        try (InputStream source = Objects.requireNonNull(ArchvowGrammar.class.getResourceAsStream("/archvow/commands.json"), "Missing commands.json")) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(source, StandardCharsets.UTF_8)).getAsJsonObject();
            List<Command> result = new ArrayList<>();
            for (JsonObject obj : objects(root, "commands")) result.add(new Command(obj));
            return Collections.unmodifiableList(result);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load Archvow command grammar", e);
        }
    }
    static Command find(String label) {
        for (Command c : COMMANDS) if (c.label.equals(label)) return c;
        throw new IllegalArgumentException("Unknown Archvow command: " + label);
    }
    static String str(JsonObject obj, String key) {
        if (obj == null) return "";
        JsonElement value = obj.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
    static int number(JsonObject obj, String key) {
        if (obj == null) return 0;
        try { JsonElement v = obj.get(key); return v == null || v.isJsonNull() ? 0 : v.getAsInt(); }
        catch (RuntimeException e) { return 0; }
    }
    static boolean flag(JsonObject obj, String key) {
        if (obj == null) return false;
        try { JsonElement v = obj.get(key); return v != null && !v.isJsonNull() && v.getAsBoolean(); }
        catch (RuntimeException e) { return false; }
    }
    static List<String> strings(JsonObject obj, String key) {
        List<String> out = new ArrayList<>();
        if (obj == null) return out;
        JsonArray arr = obj.getAsJsonArray(key);
        if (arr != null) for (JsonElement el : arr) if (el != null && !el.isJsonNull()) out.add(el.getAsString());
        return out;
    }
    static List<JsonObject> objects(JsonObject obj, String key) {
        List<JsonObject> out = new ArrayList<>();
        if (obj == null) return out;
        JsonArray arr = obj.getAsJsonArray(key);
        if (arr != null) for (JsonElement el : arr) if (el.isJsonObject()) out.add(el.getAsJsonObject());
        return out;
    }
    static JsonObject parseObject(String output) {
        try {
            JsonElement root = JsonParser.parseString(output);
            return root.isJsonObject() ? root.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            // Some supported CLI wrappers prepend warnings or progress messages to
            // an otherwise valid JSON response. Preserve strict parsing first, then
            // permit an independently valid object delimited by braces.
            if (output == null) return null;
            int first = output.indexOf('{');
            int last = output.lastIndexOf('}');
            if (first < 0 || last <= first) return null;
            try {
                JsonElement candidate = JsonParser.parseString(output.substring(first, last + 1));
                return candidate.isJsonObject() ? candidate.getAsJsonObject() : null;
            } catch (RuntimeException ignored) { return null; }
        }
    }
    static String pretty(JsonElement element) {
        return element == null ? "" : new GsonBuilder().setPrettyPrinting().create().toJson(element);
    }
}
