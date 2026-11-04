package org.archvow.idea;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * How a command's form is laid out (design A, 0.14.0), from archvow/forms.json (../contract/forms, the same
 * file as VS Code's): the labels in plain words, the essential fields first (the rest under More options), the
 * button's verb, what archvow will do. Also the live preview of the command line and what a result says in
 * place: archvow's refusal (exit code 2, nothing written) and its doubts (warnings, written anyway). Pure: the
 * workspace builds the Swing form from it; WorkspaceTests checks it.
 */
final class ArchvowForms {
    private ArchvowForms() {}

    static final JsonObject DATA = load();

    private static JsonObject load() {
        try (InputStream source = Objects.requireNonNull(ArchvowForms.class.getResourceAsStream("/archvow/forms.json"), "Missing forms.json")) {
            return JsonParser.parseReader(new InputStreamReader(source, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("archvow/forms.json: " + e.getMessage(), e);
        }
    }

    private static JsonObject entry(String command) {
        JsonObject commands = DATA.getAsJsonObject("commands");
        return commands != null && commands.has(command) ? commands.getAsJsonObject(command) : new JsonObject();
    }

    /** [English, French] in the page's language; '' when absent. */
    private static String pair(JsonElement element, boolean fr) {
        if (element == null || !element.isJsonArray()) return "";
        JsonArray array = element.getAsJsonArray();
        return array.size() == 2 ? array.get(fr ? 1 : 0).getAsString() : "";
    }

    private static JsonElement member(JsonObject object, String key) {
        return object == null || !object.has(key) ? null : object.get(key);
    }

    /** A parameter in plain words; archvow's own name (underscores as spaces) when forms.json has none. */
    static String label(String command, String param, boolean fr) {
        String own = pair(member(entry(command).getAsJsonObject("labels"), param), fr);
        if (!own.isEmpty()) return own;
        String shared = pair(member(DATA.getAsJsonObject("labels"), param), fr);
        return shared.isEmpty() ? param.replace('_', ' ') : shared;
    }

    static String title(String command, boolean fr) { return pair(entry(command).get("title"), fr); }
    static String verb(String command, boolean fr) { return pair(entry(command).get("verb"), fr); }
    static String does(String command, boolean fr) { return pair(entry(command).get("does"), fr); }
    static String hint(String command, String param, boolean fr) { return pair(member(entry(command).getAsJsonObject("hints"), param), fr); }

    private static List<String> essentials(String command) {
        List<String> out = new ArrayList<>();
        JsonElement list = entry(command).get("essential");
        if (list != null && list.isJsonArray()) for (JsonElement e : list.getAsJsonArray()) out.add(e.getAsString());
        return out;
    }

    /** Groups defined once for both IDEs in forms.json, never inferred from a project template. */
    static List<Group> commandGroups(boolean french) {
        List<Group> result = new ArrayList<>();
        JsonArray groups = DATA.getAsJsonArray("command_groups");
        if (groups != null) for (JsonElement raw : groups) {
            JsonObject group = raw.getAsJsonObject();
            String label = pair(group.get("label"), french);
            List<String> prefixes = new ArrayList<>();
            for (JsonElement value : group.getAsJsonArray("prefixes")) prefixes.add(value.getAsString());
            result.add(new Group(group.get("id").getAsString(), label, prefixes));
        }
        return result;
    }

    record Group(String id, String label, List<String> prefixes) {}

    /** Trigger based on the current question, not on a hard-coded component type or package. */
    static boolean supportsColumnAnswer(String question) {
        if (question == null) return false;
        String q = question.toLowerCase(java.util.Locale.ROOT);
        boolean aboutColumns = q.contains("column") || q.contains("field") || q.contains("colonne") || q.contains("champ");
        boolean aboutProperties = q.contains("null") || q.contains("person") || q.contains("priv") || q.contains("required")
            || q.contains("optional") || q.contains("obligat") || q.contains("facultat") || q.contains("gdpr") || q.contains("rgpd") || q.contains("type");
        if (!aboutColumns || !aboutProperties) return false;
        JsonObject smart = DATA.getAsJsonObject("smart_answers");
        if (smart == null) return false;
        JsonObject schema = smart.getAsJsonObject("schema_columns");
        if (schema == null) return false;
        JsonArray words = schema.getAsJsonArray("keywords");
        if (words == null) return false;
        for (JsonElement word : words) if (q.contains(word.getAsString().toLowerCase(java.util.Locale.ROOT))) return true;
        return false;
    }


    /** Shown first: the command's essential fields and every required one; the others under More options. */
    static boolean essential(String command, ArchvowGrammar.Param param) {
        return param.required || !entry(command).has("essential") || essentials(command).contains(param.name);
    }

    /** The parameters in the order the form shows them: the essentials as forms.json lists them, then the rest. */
    static List<ArchvowGrammar.Param> ordered(ArchvowGrammar.Command command) {
        List<String> order = essentials(command.label);
        List<ArchvowGrammar.Param> params = new ArrayList<>(command.parameters);
        params.sort((a, b) -> Integer.compare(rank(order, a.name), rank(order, b.name)));  // stable: archvow's order otherwise
        return params;
    }

    private static int rank(List<String> order, String name) {
        int i = order.indexOf(name);
        return i < 0 ? order.size() : i;
    }

    /**
     * The command line the form asks for, as it is typed (the live preview): what Run sends, without the
     * options archvow adds for the plugin (--json, --yes, --no-input). A required field still empty is named
     * instead, as VS Code's planForm says it.
     */
    static String commandLine(ArchvowGrammar.Command command, Map<String, String> values, boolean fr) {
        List<String> args = new ArrayList<>(command.path);
        for (ArchvowGrammar.Param param : command.parameters) {
            String text = values.getOrDefault(param.name, "").trim();
            if (text.isEmpty()) {
                if (param.required) return (fr ? "Champ obligatoire : " : "Required field: ") + param.name;
                continue;
            }
            if (param.bool) {
                if ("true".equals(text)) args.add(param.option());
            } else if (param.multiToken) {
                for (String item : text.split("\\R")) if (!item.isBlank()) args.add(item.trim());
            } else if (param.append) {
                for (String item : text.split("\\R")) if (!item.isBlank()) {
                    if (!param.flags.isEmpty()) args.add(param.option());
                    args.add(item.trim());
                }
            } else {
                if (!param.flags.isEmpty()) args.add(param.option());
                args.add(text);
            }
        }
        StringBuilder line = new StringBuilder("archvow");
        for (String a : args) {
            line.append(' ');
            line.append(a.contains(" ") || a.contains("\n") ? '"' + a.replace("\n", "\\n").replace("\"", "\\\"") + '"' : a);
        }
        return line.toString();
    }

    /** archvow said no (exit code 2): its words, without "✖ archvow: error:"; '' otherwise. As VS Code's alerts(). */
    static String refusal(int exitCode, String stderr) {
        if (exitCode != 2 || stderr == null) return "";
        List<String> lines = new ArrayList<>();
        for (String line : stderr.split("\\R")) {
            String text = line.replaceFirst("^\\s*✖\\s*", "").replaceFirst("^archvow: error:\\s*", "").trim();
            if (!text.isEmpty()) lines.add(text);
        }
        return String.join("\n", lines);
    }

    /** What archvow wrote with a doubt: the "warnings" of a --json entry, else the "!" lines of the text. */
    static List<String> warnings(String stdout) {
        List<String> out = new ArrayList<>();
        if (stdout == null || stdout.isBlank()) return out;
        try {
            JsonElement parsed = JsonParser.parseString(stdout);
            if (parsed.isJsonObject()) {
                JsonElement list = parsed.getAsJsonObject().get("warnings");
                if (list != null && list.isJsonArray()) for (JsonElement w : list.getAsJsonArray()) out.add(w.getAsString());
                return out;
            }
        } catch (RuntimeException notJson) {
            // the text: its "!" lines
        }
        for (String line : stdout.split("\\R")) {
            String text = line.trim();
            if (text.startsWith("!")) out.add(text.replaceFirst("^!\\s*", ""));
        }
        return out;
    }
}
