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
        /** archvow hides it from --help (argparse.SUPPRESS): an expert option, shown apart. */
        final boolean hidden;
        /** The plugin's own field (forms.json extra_parameters), never on archvow's line: `output` (0.15.0). */
        final boolean plugin;
        /** With `output`: the report is written as Markdown (--markdown added). */
        final boolean markdown;
        Param(JsonObject obj) {
            plugin = false; markdown = false;
            String text = str(obj, "help");
            hidden = flag(obj, "hidden") || "==SUPPRESS==".equals(text);
            name = str(obj, "name"); help = "==SUPPRESS==".equals(text) ? "" : text; metavar = str(obj, "metavar");
            flags = strings(obj, "flags"); choices = strings(obj, "choices");
            required = flag(obj, "required"); bool = flag(obj, "boolean");
            append = flag(obj, "append"); multiToken = flag(obj, "multiToken");
            optionalValue = flag(obj, "optionalValue");
        }
        /** A field of the plugin itself, as VS Code's withPluginParameters makes it. */
        Param(String name, String help, boolean markdown) {
            this.name = name; this.help = help; metavar = name.toUpperCase(java.util.Locale.ROOT);
            flags = List.of(); choices = List.of();
            required = false; bool = false; append = false; multiToken = false; optionalValue = false; hidden = false;
            plugin = true; this.markdown = markdown;
        }
        String option() { return flags.isEmpty() ? "" : flags.get(flags.size() - 1); }
    }
    static final class Command {
        final List<String> path;
        final String label, description;
        final boolean json;
        final List<Param> parameters;
        Command(JsonObject obj) { this(obj, null); }
        /** With the plugins' own fields of forms.json `extra_parameters` (output on changes and diff). */
        Command(JsonObject obj, JsonObject extra) {
            path = strings(obj, "path"); label = String.join(" ", path);
            description = str(obj, "description"); json = flag(obj, "json");
            List<Param> ps = new ArrayList<>();
            for (JsonObject p : objects(obj, "parameters")) ps.add(new Param(p));
            if (extra != null && extra.has(label) && extra.get(label).isJsonArray()) {
                for (JsonElement e : extra.getAsJsonArray(label)) {
                    if (!e.isJsonObject()) continue;
                    JsonObject x = e.getAsJsonObject();
                    String name = str(x, "name");
                    if (ps.stream().anyMatch(p -> p.name.equals(name))) continue;
                    JsonArray hint = x.getAsJsonArray("hint");
                    Param param = new Param(name, hint != null && hint.size() > 0 ? hint.get(0).getAsString() : "", flag(x, "markdown"));
                    int at = -1;
                    for (int i = 0; i < ps.size(); i++) if (ps.get(i).name.equals(str(x, "after"))) at = i;
                    ps.add(at >= 0 ? at + 1 : ps.size(), param);
                }
            }
            parameters = Collections.unmodifiableList(ps);
        }
        @Override public String toString() { return label; }
    }

    static final List<Command> COMMANDS = load();

    private static List<Command> load() {
        try (InputStream source = Objects.requireNonNull(ArchvowGrammar.class.getResourceAsStream("/archvow/commands.json"), "Missing commands.json")) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(source, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject extra = null;
            try (InputStream forms = ArchvowGrammar.class.getResourceAsStream("/archvow/forms.json")) {
                if (forms != null) {
                    JsonObject f = JsonParser.parseReader(new InputStreamReader(forms, StandardCharsets.UTF_8)).getAsJsonObject();
                    if (f.has("extra_parameters") && f.get("extra_parameters").isJsonObject()) extra = f.getAsJsonObject("extra_parameters");
                }
            }
            List<Command> result = new ArrayList<>();
            for (JsonObject obj : objects(root, "commands")) result.add(new Command(obj, extra));
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
    /** {@code archvow show <id> --json} as that component's view, or null: since archvow 1.21 a name that is no longer
     *  a component (removed, renamed) is searched instead, and the search result (no {@code component}) is not a view. */
    static JsonObject componentView(String output, String id) {
        JsonObject view = parseObject(output);
        return view != null && id != null && id.equals(str(view, "component")) ? view : null;
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
