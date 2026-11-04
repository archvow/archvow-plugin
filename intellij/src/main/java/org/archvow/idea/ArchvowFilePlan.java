package org.archvow.idea;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The files a command writes, shown under the command in the form (Files): what is new, updated, removed or
 * kept, and the code and test files a new component plans (archvow never writes code: the AI or a developer
 * writes them later). From forms.json `files` and what the project says (`show --json`, `catalog --json`).
 * The same as VS Code's filePlan.ts: FilePlanRegression compares both on contract/forms/file-plans.expected.json.
 */
final class ArchvowFilePlan {
    private ArchvowFilePlan() {}

    record Row(String change, String path, String note) {}
    record Addable(String type, String description) {}

    private static final int MAX_COVERED = 4;

    /** archvow's JSON as text, as filePlan.ts's str(): '' for absent or null, objects as JSON. */
    private static String str(JsonObject o, String key) {
        if (o == null) return "";
        JsonElement v = o.get(key);
        if (v == null || v.isJsonNull()) return "";
        return v.isJsonPrimitive() ? v.getAsString() : v.toString();
    }

    private static List<JsonObject> objects(JsonObject o, String key) {
        List<JsonObject> out = new ArrayList<>();
        JsonElement v = o == null ? null : o.get(key);
        if (v != null && v.isJsonArray()) for (JsonElement e : v.getAsJsonArray()) if (e.isJsonObject()) out.add(e.getAsJsonObject());
        return out;
    }

    private static String value(Map<String, String> values, String key) {
        String v = values.get(key);
        return v == null ? "" : v.trim();
    }

    /** {name} orders_clean, {NAME} ORDERS_CLEAN, {Name} OrdersClean: archvow's manifest.name_values. */
    static Map<String, String> nameValues(String name) {
        StringBuilder camel = new StringBuilder();
        for (String part : name.split("_", -1)) if (!part.isEmpty()) camel.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        Map<String, String> out = new LinkedHashMap<>();
        out.put("name", name); out.put("NAME", name.toUpperCase(java.util.Locale.ROOT)); out.put("Name", camel.toString());
        return out;
    }

    private static String format(String pattern, Map<String, String> v) {
        return pattern.replace("{name}", v.get("name")).replace("{NAME}", v.get("NAME")).replace("{Name}", v.get("Name"));
    }

    /** archvow's manifest.source_path: <container path>/<layer>/[<group>/]<id>.py, from the module. */
    static String sourcePath(JsonObject container, JsonObject comp) {
        String module = str(comp, "module");
        if (module.isEmpty()) return "";
        String[] parts = module.split("\\.", -1);
        return str(container, "path") + "/" + String.join("/", java.util.Arrays.copyOfRange(parts, 1, parts.length)) + ".py";
    }

    /** archvow's manifest.test_path: tests/<layer>/[<group>/]test_<id>.py. */
    static String testPath(JsonObject comp) {
        String group = str(comp, "group").isEmpty() ? "" : str(comp, "group") + "/";
        return "tests/" + str(comp, "layer") + "/" + group + "test_" + str(comp, "id") + ".py";
    }

    private record Placed(JsonObject comp, JsonObject container) {}

    private static List<Placed> components(JsonObject spec) {
        List<Placed> out = new ArrayList<>();
        for (JsonObject container : objects(spec, "containers"))
            for (JsonObject comp : objects(container, "components")) out.add(new Placed(comp, container));
        return out;
    }

    private static JsonObject typeOf(JsonObject catalog, JsonObject container, String type) {
        String wanted = container == null ? "" : str(container, "template").split("@", -1)[0];
        List<JsonObject> templates = objects(catalog, "templates");
        List<JsonObject> ordered = new ArrayList<>();
        for (JsonObject t : templates) if (str(t, "name").equals(wanted)) ordered.add(t);
        for (JsonObject t : templates) if (!str(t, "name").equals(wanted)) ordered.add(t);
        for (JsonObject t : ordered) {
            JsonElement types = t.get("component_types");
            if (types != null && types.isJsonObject() && types.getAsJsonObject().get(type) instanceof JsonObject def) return def;
        }
        return null;
    }

    /** The component component add would make, as archvow makes it (specs.add_component). */
    private static Placed newComponent(JsonObject spec, JsonObject catalog, Map<String, String> values) {
        String type = value(values, "type"), name = value(values, "name");
        if (type.isEmpty() || name.isEmpty()) return null;
        List<JsonObject> all = objects(spec, "containers");
        String wanted = value(values, "container");
        JsonObject container = null;
        for (JsonObject c : all) if (str(c, "id").equals(wanted)) { container = c; break; }
        if (container == null && !all.isEmpty()) container = all.get(0);
        if (container == null) return null;
        JsonObject def = typeOf(catalog, container, type);
        if (def == null || str(def, "module").isEmpty() || str(def, "layer").isEmpty()) return null;
        Map<String, String> v = nameValues(name);
        String id = format(str(def, "module"), v);
        String group = value(values, "group");
        String layer = str(def, "layer");
        List<String> module = new ArrayList<>(List.of(str(container, "package"), layer));
        if (!group.isEmpty()) module.add(group);
        module.add(id);
        JsonObject comp = new JsonObject();
        comp.addProperty("id", id); comp.addProperty("layer", layer); comp.addProperty("group", group);
        comp.addProperty("module", String.join(".", module)); comp.addProperty("type", type);
        JsonArray symbols = new JsonArray();
        for (JsonObject s : objects(def, "symbols")) {
            JsonObject copy = s.deepCopy();
            copy.addProperty("name", format(str(s, "name"), v));
            copy.addProperty("signature", format(str(s, "signature"), v));
            symbols.add(copy);
        }
        String signature = value(values, "signature");
        if (!signature.isEmpty() && symbols.size() > 0) symbols.get(0).getAsJsonObject().addProperty("signature", signature);
        comp.add("symbols", symbols);
        return new Placed(comp, container);
    }

    /** What the planned code declares: read_orders(spark: SparkSession, path: str) -> DataFrame, class JobConfig... */
    private static String symbolsNote(JsonObject comp, String routes) {
        List<String> parts = new ArrayList<>();
        for (JsonObject s : objects(comp, "symbols")) {
            String name = str(s, "name"), kind = str(s, "kind");
            if ("class".equals(kind)) parts.add("class " + name);
            else if ("route".equals(kind)) parts.add(name);
            else parts.add(name + (str(s, "signature").isEmpty() ? "(...)" : str(s, "signature")));
        }
        for (String r : routes.split("\n", -1)) if (!r.trim().isEmpty()) parts.add(r.trim());
        return String.join(", ", parts);
    }

    /** The components a rule covers: rule add (words, --type, --all), or rule edit / remove (its scope). */
    private static List<String> covered(JsonObject spec, String name, Map<String, String> values) {
        List<JsonObject> all = new ArrayList<>();
        for (Placed p : components(spec)) all.add(p.comp());
        List<String> ids = new ArrayList<>();
        if ("rule add".equals(name)) {
            if ("true".equals(values.getOrDefault("all", ""))) { for (JsonObject c : all) ids.add(str(c, "id")); }
            else if (!value(values, "type").isEmpty()) { for (JsonObject c : all) if (str(c, "type").equals(value(values, "type"))) ids.add(str(c, "id")); }
            else {
                List<String> lines = new ArrayList<>();
                for (String l : values.getOrDefault("words", "").split("\n", -1)) if (!l.trim().isEmpty()) lines.add(l.trim());
                Set<String> known = new LinkedHashSet<>();
                for (JsonObject c : all) known.add(str(c, "id"));
                if (lines.size() > 1) for (String x : lines.get(0).split(",", -1)) if (known.contains(x.trim())) ids.add(x.trim());
            }
        } else {
            JsonObject scope = null;
            for (JsonObject r : objects(spec, "project_rules"))
                if (str(r, "id").equals(value(values, "id")) && r.get("scope") instanceof JsonObject s) { scope = s; break; }
            if (scope != null && scope.get("all") != null && scope.get("all").isJsonPrimitive() && scope.get("all").getAsJsonPrimitive().isBoolean()
                && scope.get("all").getAsBoolean()) { for (JsonObject c : all) ids.add(str(c, "id")); }
            else if (scope != null && !str(scope, "type").isEmpty()) { for (JsonObject c : all) if (str(c, "type").equals(str(scope, "type"))) ids.add(str(c, "id")); }
            else if (scope != null && scope.get("components") != null && scope.get("components").isJsonArray())
                for (JsonElement e : scope.getAsJsonArray("components")) ids.add(e.isJsonPrimitive() ? e.getAsString() : e.toString());
        }
        return ids;
    }

    /** The rows of the Files block for a command and what its form holds now. */
    static List<Row> plan(String name, Map<String, String> values, JsonObject spec, JsonObject catalog, JsonObject forms,
                          boolean fr, boolean outputExists) {
        List<Row> rows = new ArrayList<>();
        JsonElement files = forms == null ? null : forms.get("files");
        JsonElement table = files != null && files.isJsonObject() ? files.getAsJsonObject().get(name) : null;
        if (table == null || !table.isJsonArray()) return rows;
        String component = value(values, "component");
        Placed known = null;
        for (Placed p : components(spec)) if (str(p.comp(), "id").equals(component)) { known = p; break; }
        Placed made = "component add".equals(name) ? newComponent(spec, catalog, values) : null;
        for (JsonElement raw : table.getAsJsonArray()) {
            if (!raw.isJsonArray()) continue;
            JsonArray entry = raw.getAsJsonArray();
            String change = entry.get(0).getAsString();
            String pattern = entry.size() > 1 && !entry.get(1).isJsonNull() ? entry.get(1).getAsString() : "";
            String note = "";
            if (entry.size() > 2 && entry.get(2).isJsonArray()) {
                JsonArray pair = entry.get(2).getAsJsonArray();
                note = pair.get(fr && pair.size() > 1 ? 1 : 0).getAsString();
            }
            List<String> paths = new ArrayList<>();
            Placed target = made != null ? made : known;
            if ("{code}".equals(pattern)) {
                if ("component add".equals(name) && !value(values, "file").isEmpty()) {
                    paths.add(value(values, "file"));
                    change = "kept";
                    note = fr ? "le fichier existant, adopté là où il est" : "the existing file, adopted where it is";
                } else {
                    if (target != null && !sourcePath(target.container(), target.comp()).isEmpty()) paths.add(sourcePath(target.container(), target.comp()));
                    if (note.contains("{symbols}")) note = target != null ? symbolsNote(target.comp(), values.getOrDefault("route", "")) : "";
                }
            } else if ("{test}".equals(pattern)) {
                if ("component add".equals(name) && !value(values, "file").isEmpty()) continue;
                if (target != null) paths.add(testPath(target.comp()));
            } else if ("{covered}".equals(pattern)) {
                List<String> ids = covered(spec, name, values);
                for (String id : ids.subList(0, Math.min(MAX_COVERED, ids.size()))) paths.add(".archvow/components/" + id + "/spec.md");
                if (ids.size() > MAX_COVERED) {
                    int more = ids.size() - MAX_COVERED;
                    note = (note.isEmpty() ? "" : note + " - ") + (fr ? "et " + more + " autres" : "and " + more + " more");
                }
            } else if ("{output}".equals(pattern)) {
                if (!value(values, "output").isEmpty()) paths.add(value(values, "output"));
                if (outputExists) change = "updated";
            } else if ("{dir}".equals(pattern)) {
                String template = value(values, "name");
                String dir = value(values, "dir").replaceAll("/+$", "");
                if (!template.isEmpty()) paths.add((dir.isEmpty() ? ".archvow/templates" : dir) + "/" + template + "/");
            } else {
                String id = pattern.contains("{new}") ? (made != null ? str(made.comp(), "id") : "") : component;
                if ((pattern.contains("{new}") || pattern.contains("{component}")) && id.isEmpty()) continue;
                if (pattern.contains("{name}") && value(values, "name").isEmpty()) continue;
                paths.add(pattern.replaceFirst(java.util.regex.Pattern.quote("{new}"), java.util.regex.Matcher.quoteReplacement(id))
                    .replaceFirst(java.util.regex.Pattern.quote("{component}"), java.util.regex.Matcher.quoteReplacement(id))
                    .replaceFirst(java.util.regex.Pattern.quote("{name}"), java.util.regex.Matcher.quoteReplacement(value(values, "name"))));
            }
            for (String p : paths) {
                rows.add(new Row(change, p, note));
                note = "";  // said once, on the first file of the entry
            }
        }
        return rows;
    }

    private static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    /** The Files block of the form pane, as VS Code's filesBlock: a folder line, its files with what happens to them, then the planned code. */
    static String html(List<Row> rows, boolean fr) {
        if (rows.isEmpty()) return "";
        Map<String, String> word = Map.of("new", fr ? "nouveau" : "new", "updated", fr ? "modifié" : "updated",
            "removed", fr ? "retiré" : "removed", "kept", fr ? "gardé" : "kept", "planned", fr ? "prévu" : "planned");
        Map<String, String> colour = Map.of("new", "#3fa55b", "updated", "#3794ff", "removed", "#e5534b", "kept", "#8b8b8b", "planned", "#8b8b8b");
        StringBuilder out = new StringBuilder();
        for (boolean later : new boolean[] {false, true}) {
            List<Row> part = new ArrayList<>();
            for (Row r : rows) if ("planned".equals(r.change()) == later) part.add(r);
            if (part.isEmpty()) continue;
            out.append("<div style='color:#8b8b8b;margin-top:6px'>").append(esc(later
                ? (fr ? "Prévus - écrits plus tard par l'IA ou un développeur (archvow n'écrit jamais de code)"
                      : "Planned - written later by the AI or a developer (archvow never writes code)")
                : (fr ? "Fichiers" : "Files"))).append("</div>");
            String folder = null;
            for (Row r : part) {
                String path = r.path();
                boolean dir = path.endsWith("/");
                int cut = dir ? path.substring(0, path.length() - 1).lastIndexOf('/') : path.lastIndexOf('/');
                String parent = cut >= 0 ? path.substring(0, cut + 1) : "";
                String leaf = cut >= 0 ? path.substring(cut + 1) : path;
                if (!parent.equals(folder)) {
                    out.append("<div style='font-family:monospace'>▸ ").append(esc(parent.isEmpty() ? "./" : parent)).append("</div>");
                    folder = parent;
                }
                out.append("<div style='margin-left:14px'><code>").append(esc(leaf)).append("</code> <span style='color:")
                    .append(colour.getOrDefault(r.change(), "#8b8b8b")).append("'>").append(esc(word.getOrDefault(r.change(), r.change()))).append("</span>");
                if (!r.note().isEmpty()) out.append("<div style='color:#8b8b8b;margin-left:8px'>").append(esc(r.note())).append("</div>");
                out.append("</div>");
            }
        }
        return out.toString();
    }

    /** The types a person can add in each layer of the project's template, the layers in the template's order. */
    static Map<String, List<Addable>> addableByLayer(JsonObject spec, JsonObject catalog) {
        Map<String, List<Addable>> out = new LinkedHashMap<>();
        List<JsonObject> containers = objects(spec, "containers");
        String wanted = containers.isEmpty() ? "" : str(containers.get(0), "template").split("@", -1)[0];
        JsonObject template = null;
        for (JsonObject t : objects(catalog, "templates")) if (str(t, "name").equals(wanted)) { template = t; break; }
        if (template == null || !(template.get("component_types") instanceof JsonObject types)) return out;
        for (JsonObject layer : objects(template, "layers")) out.put(str(layer, "name"), new ArrayList<>());
        for (Map.Entry<String, JsonElement> e : types.entrySet()) {
            if (!(e.getValue() instanceof JsonObject def)) continue;
            JsonElement addable = def.get("addable");
            if (addable == null || !addable.isJsonPrimitive() || !addable.getAsJsonPrimitive().isBoolean() || !addable.getAsBoolean()) continue;
            if (str(def, "layer").isEmpty()) continue;
            out.computeIfAbsent(str(def, "layer"), k -> new ArrayList<>()).add(new Addable(e.getKey(), str(def, "description")));
        }
        return out;
    }
}
