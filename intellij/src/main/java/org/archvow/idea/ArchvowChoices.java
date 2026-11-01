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
import java.util.function.Function;

/**
 * What a command form offers to choose from, so nobody has to know an id by heart: the project's
 * components, a component's questions (with their number), its proposals, assumptions, examples, safety
 * questions and tasks, the project's hazards, invariants, rules and dependencies, the types and kinds of
 * the project's template, the templates to extend, the branches to compare with. Choosing one can fill
 * other fields (Edit spec: the component's description, ticket, uses...; rule edit: the rule's text;
 * decision make: the answer recorded or suggested). Pure, from what archvow printed: {@code show --json},
 * {@code show <component> --json}, {@code catalog --json} (+ archvow/kinds.json for an archvow 1.17) and
 * the repository's refs. Exactly the VS Code extension's choices.ts (ChoicesRegression compares both on
 * contract/choices/expected.json).
 */
final class ArchvowChoices {
    private ArchvowChoices() {}

    /** One thing to choose: the value the command gets, what the person reads, the fields it fills (or null). */
    record Choice(String value, String label, Map<String, String> fill) {}

    /**
     * A field of a form: {@code select} (only these values), {@code suggest} (these, or anything typed),
     * {@code note} (the usual field, with a line under it). {@code by}: the list depends on the form's
     * component or type. Absent parts are null.
     */
    static final class Field {
        final String kind;
        String by;
        List<Choice> choices;
        Map<String, List<Choice>> lists;
        String empty;
        String note;
        Map<String, String> notes;

        Field(String kind) { this.kind = kind; }
    }

    /** What the book is made from. */
    record Input(JsonObject spec, Map<String, JsonObject> snapshots, JsonObject catalog, JsonObject kinds,
                 List<String> refs, String base, boolean fr) {}

    private static final int LABEL = 110;

    private static String tr(boolean fr, String english, String french) { return fr ? french : english; }

    /** Like choices.ts: only ASCII white space is collapsed, code units are counted. */
    static String shorten(String s) {
        String flat = s.replaceAll("[ \\t\\n\\r\\f\\x0B]+", " ").replaceAll("^ | $", "");
        return flat.length() <= LABEL ? flat : flat.substring(0, LABEL - 1) + "…";
    }

    private static Choice item(String value, String text, Map<String, String> fill) {
        return new Choice(value, shorten(text.isEmpty() ? value : value + " — " + text),
            fill != null && !fill.isEmpty() ? fill : null);
    }

    private static Choice item(String value, String text) { return item(value, text, null); }

    /** archvow's JSON as text, as choices.ts's str(): '' for absent or null, numbers as written. */
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

    private static boolean isTrue(JsonObject o, String key) {
        JsonElement v = o == null ? null : o.get(key);
        return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean() && v.getAsBoolean();
    }

    private static boolean isBoolean(JsonObject o, String key) {
        JsonElement v = o == null ? null : o.get(key);
        return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean();
    }

    /** The components of the spec, in its order. */
    private static List<JsonObject> components(JsonObject spec) {
        List<JsonObject> out = new ArrayList<>();
        for (JsonObject c : objects(spec, "containers")) out.addAll(objects(c, "components"));
        return out;
    }

    /** The templates the project was made from (pyspark-pipeline@1.17.0 -> pyspark-pipeline). */
    static List<String> projectTemplates(JsonObject spec) {
        Set<String> out = new LinkedHashSet<>();
        for (JsonObject c : objects(spec, "containers")) {
            String t = str(c, "template").split("@", -1)[0];
            if (!t.isEmpty()) out.add(t);
        }
        return new ArrayList<>(out);
    }

    /** The kinds of a type: the catalog's (archvow 1.18), else archvow/kinds.json (archvow 1.17). */
    private static List<JsonObject> kindsOf(String template, String type, JsonObject definition, JsonObject kinds) {
        if (definition != null && definition.get("kinds") != null && definition.get("kinds").isJsonArray()) return objects(definition, "kinds");
        JsonElement all = kinds == null ? null : kinds.get("kinds");
        if (all == null || !all.isJsonObject()) return List.of();
        JsonElement byTemplate = all.getAsJsonObject().get(template);
        if (byTemplate == null || !byTemplate.isJsonObject()) return List.of();
        JsonElement list = byTemplate.getAsJsonObject().get(type);
        return list != null && list.isJsonArray() ? objects(byTemplate.getAsJsonObject(), type) : List.of();
    }

    /** The types a person can add in this project (its template's), each with its kinds. */
    private static void types(Input input, List<Choice> type, Map<String, List<Choice>> kinds) {
        List<String> wanted = projectTemplates(input.spec());
        List<JsonObject> all = objects(input.catalog(), "templates");
        List<JsonObject> used = new ArrayList<>();
        if (!wanted.isEmpty()) for (JsonObject t : all) if (wanted.contains(str(t, "name"))) used.add(t);
        List<JsonObject> from = used.isEmpty() ? all : used;  // a template the catalog does not know: every known type
        for (JsonObject template : from) {
            JsonElement declared = template.get("component_types");
            if (declared == null || !declared.isJsonObject()) continue;
            for (Map.Entry<String, JsonElement> e : declared.getAsJsonObject().entrySet()) {
                String name = e.getKey();
                JsonObject definition = e.getValue().isJsonObject() ? e.getValue().getAsJsonObject() : null;
                if (definition == null || !isTrue(definition, "addable") || type.stream().anyMatch(c -> c.value().equals(name))) continue;
                type.add(item(name, str(definition, "description")));
                List<Choice> offered = new ArrayList<>();
                for (JsonObject k : kindsOf(str(template, "name"), name, definition, input.kinds()))
                    offered.add(item(str(k, "name"), (isTrue(k, "default") ? tr(input.fr(), "(default) ", "(par défaut) ") : "") + str(k, "description")));
                kinds.put(name, offered);
            }
        }
        type.sort((a, b) -> a.value().compareTo(b.value()));
    }

    /**
     * The branch a change is compared with: on a feature branch of a Git flow project, develop; otherwise
     * what origin's HEAD names, then main, master, develop - remote ones first, only refs that exist here,
     * never the branch itself. '' when there is none (a repository with one branch, no remote).
     */
    static String bestBase(List<String> refs, String current, boolean gitflow, String remoteHead) {
        boolean feature = current != null && current.matches("^(feature|bugfix|hotfix)/.*");
        List<String> order = new ArrayList<>();
        if (gitflow && feature) { order.add("origin/develop"); order.add("develop"); }
        order.add(remoteHead == null ? "" : remoteHead);
        order.addAll(List.of("origin/main", "origin/master", "main", "master", "origin/develop", "develop"));
        for (String r : order) if (!r.isEmpty() && refs.contains(r) && !r.equals(current)) return r;
        return "";
    }

    /** {@code git for-each-ref --format=%(refname)} as the names a person types: main, origin/main, v1.2 (no origin/HEAD). */
    static List<String> refNames(String output) {
        List<String> names = new ArrayList<>();
        for (String line : (output == null ? "" : output).split("\\r?\\n")) {
            String ref = line.trim();
            String name = ref.startsWith("refs/heads/") ? ref.substring(11) : ref.startsWith("refs/tags/") ? ref.substring(10)
                : ref.startsWith("refs/remotes/") && !ref.endsWith("/HEAD") ? ref.substring(13) : "";
            if (!name.isEmpty() && !names.contains(name)) names.add(name);
        }
        return names;
    }

    /** Why a base cannot be compared with, and what exists instead ('' when it exists). */
    static String missingRef(String ref, List<String> refs, boolean fr) {
        String value = ref == null ? "" : ref.trim();
        if (value.isEmpty() || refs.contains(value)) return "";
        String remote = value.contains("/") ? value.split("/", -1)[0] : "";
        Set<String> remotes = new LinkedHashSet<>();
        for (String r : refs) if (r.contains("/")) remotes.add(r.split("/", -1)[0]);
        String why = !remote.isEmpty() && !remotes.contains(remote)
            ? tr(fr, " (no remote '" + remote + "' here, or it was never fetched: git fetch " + remote + ")",
                " (pas de remote « " + remote + " » ici, ou jamais récupéré : git fetch " + remote + ")")
            : "";
        List<String> existing = refs.stream().filter(r -> !r.endsWith("/HEAD")).limit(12).toList();
        String branches = String.join(", ", existing);
        return tr(fr, "'" + value + "' is not a branch of this repository" + why + ". ", "« " + value + " » n'est pas une branche de ce dépôt" + why + ". ")
            + (!branches.isEmpty() ? tr(fr, "Choose one that exists: " + branches + ".", "Choisis-en une qui existe : " + branches + ".")
                : tr(fr, "This repository has no other branch yet.", "Ce dépôt n'a pas encore d'autre branche."));
    }

    private static Field perComponent(Map<String, List<Choice>> lists, String empty) {
        Field f = new Field("select");
        f.by = "component"; f.lists = lists; f.empty = empty;
        return f;
    }

    private static Field select(List<Choice> choices, String empty) {
        Field f = new Field("select");
        f.choices = choices; f.empty = empty;
        return f;
    }

    /** What every command form offers, for this project: command -> parameter -> field. */
    static Map<String, Map<String, Field>> book(List<ArchvowGrammar.Command> commands, Input input) {
        boolean fr = input.fr();
        List<JsonObject> all = components(input.spec());
        List<String> ids = all.stream().map(c -> str(c, "id")).toList();
        Function<String, JsonObject> view = id -> input.snapshots().getOrDefault(id, new JsonObject());
        Function<Function<String, List<Choice>>, Map<String, List<Choice>>> byComponent = make -> {
            Map<String, List<Choice>> out = new LinkedHashMap<>();
            for (String id : ids) out.put(id, make.apply(id));
            return out;
        };
        Function<String, List<JsonObject>> decisions = id -> objects(view.apply(id), "decisions");
        Function<JsonObject, String> answered = d -> str(d, "answer").isEmpty() ? "" : tr(fr, " (answered)", " (répondue)");
        List<Choice> type = new ArrayList<>();
        Map<String, List<Choice>> kinds = new LinkedHashMap<>();
        types(input, type, kinds);
        List<JsonObject> hazards = objects(input.spec(), "hazards");
        List<String> refs = input.refs().stream().filter(r -> !r.endsWith("/HEAD")).toList();
        String base = input.base() == null ? "" : input.base();
        String baseNote = !refs.isEmpty()
            ? tr(fr, "The branches and tags of this repository" + (base.isEmpty() ? "" : "; a merge request is compared with " + base) + ".",
                "Les branches et tags de ce dépôt" + (base.isEmpty() ? "" : " ; une merge request se compare à " + base) + ".")
            : tr(fr, "No other branch in this repository yet: nothing to compare with.", "Pas encore d'autre branche dans ce dépôt : rien à comparer.");
        List<String> templates = projectTemplates(input.spec());

        Map<String, Map<String, Field>> book = new LinkedHashMap<>();
        for (ArchvowGrammar.Command command : commands) {
            String name = command.label;
            Map<String, Field> fields = new LinkedHashMap<>();
            for (ArchvowGrammar.Param param : command.parameters) {
                String p = param.name;
                Field field = null;
                if ("component".equals(p) && !"component add".equals(name)) {
                    List<Choice> choices = new ArrayList<>();
                    for (JsonObject c : all) {
                        String id = str(c, "id");
                        Map<String, String> fill = null;
                        if ("component edit".equals(name)) {
                            fill = new LinkedHashMap<>(ArchvowCommandPrefill.prepare("component edit", Map.of("component", id), "",
                                input.snapshots().get(id), c));
                            fill.remove("component");
                        }
                        choices.add(item(id, str(c, "type"), fill));
                    }
                    field = select(choices, tr(fr, "No component yet: add one with component add.", "Aucun composant : en ajouter un avec component add."));
                } else if ("decision make".equals(name) && "question".equals(p)) {
                    field = perComponent(byComponent.apply(id -> {
                        List<JsonObject> rows = decisions.apply(id);
                        List<JsonObject> open = new ArrayList<>();
                        for (JsonObject d : rows) if (str(d, "answer").isEmpty()) open.add(d);
                        for (JsonObject d : rows) if (!str(d, "answer").isEmpty()) open.add(d);
                        List<Choice> out = new ArrayList<>();
                        for (JsonObject d : open) out.add(item(str(d, "number"), str(d, "question") + answered.apply(d),
                            Map.of("answer", first(str(d, "answer"), str(d, "proposed"), str(d, "suggested")))));
                        for (JsonObject q : objects(view.apply(id), "questions")) out.add(item(str(q, "key"), str(q, "question") + answered.apply(q),
                            Map.of("answer", first(str(q, "answer"), str(q, "suggested")))));
                        return out;
                    }), tr(fr, "No question on this component.", "Aucune question sur ce composant."));
                } else if ("decision propose".equals(name) && "question".equals(p)) {
                    field = perComponent(byComponent.apply(id -> {
                        List<Choice> out = new ArrayList<>();
                        for (JsonObject d : decisions.apply(id)) if (str(d, "answer").isEmpty())
                            out.add(item(str(d, "number"), str(d, "question"), Map.of("answer", first(str(d, "proposed"), str(d, "suggested")))));
                        return out;
                    }), tr(fr, "Every question of this component is answered.", "Toutes les questions de ce composant ont une réponse."));
                } else if ("decision accept".equals(name) && "number".equals(p)) {
                    field = perComponent(byComponent.apply(id -> {
                        List<Choice> out = new ArrayList<>();
                        for (JsonObject d : decisions.apply(id)) if (!str(d, "proposed").isEmpty() && str(d, "answer").isEmpty())
                            out.add(item(str(d, "number"), str(d, "question") + " → " + str(d, "proposed")));
                        return out;
                    }), tr(fr, "No AI proposal waiting on this component.", "Aucune proposition IA en attente sur ce composant."));
                } else if ("question remove".equals(name) && "number".equals(p)) {
                    field = perComponent(byComponent.apply(id -> {
                        List<JsonObject> rows = decisions.apply(id);
                        boolean flagged = rows.stream().anyMatch(d -> isBoolean(d, "template"));
                        List<Choice> out = new ArrayList<>();
                        for (JsonObject d : rows) if (!flagged || !isTrue(d, "template"))
                            out.add(item(str(d, "number"), str(d, "question") + answered.apply(d)));
                        return out;
                    }), tr(fr, "No question a person added: the template's questions stay (answer them with decision make).",
                        "Aucune question ajoutée par une personne : celles du modèle restent (y répondre avec decision make)."));
                    field.note = tr(fr, "Only a question a person added can be removed, with its answer; archvow refuses the template's own.",
                        "Seule une question ajoutée par une personne peut être supprimée, avec sa réponse ; archvow refuse celles du modèle.");
                } else if (("question add".equals(name) || "decision add".equals(name)) && "question".equals(p)) {
                    field = new Field("note");
                    field.by = "component";
                    field.notes = new LinkedHashMap<>();
                    for (String id : ids) {
                        int next = decisions.apply(id).size() + 1;
                        field.notes.put(id, tr(fr, "No number to give: archvow numbers it - it becomes question " + next + " of " + id + ".",
                            "Pas de numéro à donner : archvow la numérote - elle devient la question " + next + " de " + id + "."));
                    }
                } else if (("assumption accept".equals(name) || "assumption reject".equals(name)) && "number".equals(p)) {
                    field = perComponent(byComponent.apply(id -> {
                        List<Choice> out = new ArrayList<>();
                        for (JsonObject a : objects(view.apply(id), "assumptions")) {
                            String status = str(a, "status");
                            if (!"accepted".equals(status) && !"rejected".equals(status)) out.add(item(str(a, "number"), str(a, "text")));
                        }
                        return out;
                    }), tr(fr, "No assumption waiting for a review on this component.", "Aucune hypothèse à revoir sur ce composant."));
                } else if ("example remove".equals(name) && "name".equals(p)) {
                    field = perComponent(byComponent.apply(id -> {
                        List<Choice> out = new ArrayList<>();
                        for (JsonObject e : objects(view.apply(id), "examples")) out.add(item(str(e, "name"), ""));
                        return out;
                    }), tr(fr, "No business example on this component.", "Aucun exemple métier sur ce composant."));
                } else if (("hazard add".equals(name) || "hazard dismiss".equals(name)) && "guide_word".equals(p)) {
                    field = perComponent(byComponent.apply(id -> {
                        List<JsonObject> rows = objects(view.apply(id), "safety");
                        List<JsonObject> open = new ArrayList<>();
                        for (JsonObject q : rows) if ("open".equals(str(q, "status"))) open.add(q);
                        for (JsonObject q : rows) if (!"open".equals(str(q, "status"))) open.add(q);
                        List<Choice> out = new ArrayList<>();
                        for (JsonObject q : open) out.add(item(str(q, "guide_word"), "S" + str(q, "number") + " " + str(q, "question")
                            + ("open".equals(str(q, "status")) ? "" : " (" + str(q, "status") + ")")));
                        return out;
                    }), tr(fr, "No safety question for this component.", "Aucune question de sécurité pour ce composant."));
                } else if ("invariant add".equals(name) && "prevents".equals(p)) {
                    field = perComponent(byComponent.apply(id -> {
                        List<Choice> out = new ArrayList<>();
                        for (JsonObject h : hazards) if (id.equals(str(h, "component"))) out.add(item(str(h, "id"), hazardText(h)));
                        return out;
                    }), tr(fr, "No hazard declared on this component yet.", "Aucun danger déclaré sur ce composant."));
                } else if ("prompt".equals(name) && "task".equals(p)) {
                    field = perComponent(byComponent.apply(id -> {
                        List<Choice> out = new ArrayList<>();
                        for (JsonObject t : objects(view.apply(id), "tasks"))
                            out.add(item(str(t, "task"), str(t, "summary") + (isTrue(t, "done") ? tr(fr, " (done)", " (fait)") : "")));
                        return out;
                    }), tr(fr, "No task for this component.", "Aucune tâche pour ce composant."));
                } else if ("hazard remove".equals(name) && "id".equals(p)) {
                    List<Choice> choices = new ArrayList<>();
                    for (JsonObject h : hazards) choices.add(item(str(h, "id"), hazardText(h)));
                    field = select(choices, tr(fr, "No hazard declared in this project.", "Aucun danger déclaré dans ce projet."));
                } else if ("invariant remove".equals(name) && "id".equals(p)) {
                    List<Choice> choices = new ArrayList<>();
                    for (JsonObject i : objects(input.spec(), "invariants")) choices.add(item(str(i, "id"), str(i, "statement")));
                    field = select(choices, tr(fr, "No invariant in this project.", "Aucun invariant dans ce projet."));
                } else if (("rule edit".equals(name) || "rule remove".equals(name)) && "id".equals(p)) {
                    List<Choice> choices = new ArrayList<>();
                    for (JsonObject r : objects(input.spec(), "project_rules"))
                        choices.add(item(str(r, "id"), str(r, "text"), "rule edit".equals(name) ? Map.of("text", str(r, "text")) : null));
                    field = select(choices, tr(fr, "No rule written for this project.", "Aucune règle écrite pour ce projet."));
                } else if ("dependency remove".equals(name) && "name".equals(p)) {
                    List<Choice> choices = new ArrayList<>();
                    for (JsonObject d : objects(input.spec(), "dependencies")) choices.add(item(str(d, "name"), str(d, "supplier")));
                    field = select(choices, tr(fr, "No dependency declared.", "Aucune dépendance déclarée."));
                } else if ("template new".equals(name) && "extends".equals(p)) {
                    field = new Field("suggest");
                    field.choices = new ArrayList<>();
                    for (JsonObject t : objects(input.catalog(), "templates")) field.choices.add(item(str(t, "name"), str(t, "description")));
                    field.note = tr(fr, "Its settings are filled in below: change what the company wants different; what the base imposes cannot be changed.",
                        "Ses réglages sont remplis ci-dessous : changer ce que l'entreprise veut autrement ; ce que la base impose ne se change pas.");
                } else if ("component add".equals(name) && "type".equals(p)) {
                    field = new Field("select");
                    field.choices = type;
                    field.note = !templates.isEmpty()
                        ? tr(fr, "The types of " + String.join(", ", templates) + ", the template of this project.",
                            "Les types de " + String.join(", ", templates) + ", le modèle de ce projet.")
                        : tr(fr, "The types of the installed templates.", "Les types des modèles installés.");
                } else if ("component add".equals(name) && "kind".equals(p)) {
                    field = new Field("select");
                    field.by = "type"; field.lists = kinds;
                    field.empty = tr(fr, "This type has no kinds: leave it empty.", "Ce type n'a pas de sortes : laisser vide.");
                } else if ("base".equals(p) || ("changes".equals(name) && ("start".equals(p) || "end".equals(p)))) {
                    field = new Field("suggest");
                    field.choices = new ArrayList<>();
                    for (String r : refs) field.choices.add(item(r, ""));
                    field.note = baseNote;
                }
                if (field != null) fields.put(p, field);
            }
            if (!fields.isEmpty()) book.put(name, fields);
        }
        return book;
    }

    private static String hazardText(JsonObject h) {
        String description = str(h, "description");
        return str(h, "severity") + ": " + (description.isEmpty() ? str(h, "guide_word") : description);
    }

    private static String first(String... values) {
        for (String v : values) if (!v.isEmpty()) return v;
        return "";
    }

    /** The choices a field offers now: its list, or the one of the form's component / type. */
    static List<Choice> choicesNow(Field field, Map<String, String> values) {
        if (field == null) return List.of();
        if (field.by == null) return field.choices == null ? List.of() : field.choices;
        String key = values.getOrDefault(field.by, "").trim();
        List<Choice> list = field.lists == null ? null : field.lists.get(key);
        return list == null ? List.of() : list;
    }

    /** The line under a field for this form ({@code note} fields by component; the others' note). */
    static String noteNow(Field field, Map<String, String> values) {
        if (field == null) return "";
        if ("note".equals(field.kind) && field.by != null) {
            String note = field.notes == null ? null : field.notes.get(values.getOrDefault(field.by, "").trim());
            return note == null ? "" : note;
        }
        return field.note == null ? "" : field.note;
    }

    // ------------------------------------------------------------------ as JSON (ChoicesRegression)

    static JsonObject json(Choice c) {
        JsonObject o = new JsonObject();
        o.addProperty("value", c.value());
        o.addProperty("label", c.label());
        if (c.fill() != null) {
            JsonObject fill = new JsonObject();
            c.fill().forEach(fill::addProperty);
            o.add("fill", fill);
        }
        return o;
    }

    private static JsonArray json(List<Choice> choices) {
        JsonArray a = new JsonArray();
        for (Choice c : choices) a.add(json(c));
        return a;
    }

    static JsonObject json(Field f) {
        JsonObject o = new JsonObject();
        o.addProperty("kind", f.kind);
        if (f.by != null) o.addProperty("by", f.by);
        if (f.choices != null) o.add("choices", json(f.choices));
        if (f.lists != null) {
            JsonObject lists = new JsonObject();
            f.lists.forEach((k, v) -> lists.add(k, json(v)));
            o.add("lists", lists);
        }
        if (f.empty != null) o.addProperty("empty", f.empty);
        if (f.note != null) o.addProperty("note", f.note);
        if (f.notes != null) {
            JsonObject notes = new JsonObject();
            f.notes.forEach(notes::addProperty);
            o.add("notes", notes);
        }
        return o;
    }

    static JsonObject json(Map<String, Map<String, Field>> book) {
        JsonObject o = new JsonObject();
        book.forEach((command, fields) -> {
            JsonObject f = new JsonObject();
            fields.forEach((name, field) -> f.add(name, json(field)));
            o.add(command, f);
        });
        return o;
    }
}
