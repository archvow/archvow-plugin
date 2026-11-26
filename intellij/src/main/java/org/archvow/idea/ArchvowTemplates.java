package org.archvow.idea;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Overview › Templates (1.0.0, archvow 3.0): the project's templates as {@code workspace --json} gives them
 * ({@code templates}: its chain's copies, bases first - name, version, description, extends, source, state {@code ok}
 * or {@code changed} - then {@code project}, its own additions). IDE-free: the Overview row's line, the list with its
 * actions as links, and what a link opens. VS Code shows the same (workspace.js templatesSummary / templatesView).
 */
final class ArchvowTemplates {
    static final String LINK = "archvow-template:";
    static final String OWN = "project";

    private ArchvowTemplates() {}

    /** The chain, bases first, without the project's own additions. */
    static List<JsonObject> chain(JsonArray templates) {
        List<JsonObject> out = new ArrayList<>();
        if (templates != null) for (JsonElement t : templates)
            if (t.isJsonObject() && !OWN.equals(str(t.getAsJsonObject(), "name"))) out.add(t.getAsJsonObject());
        return out;
    }

    static int changed(JsonArray templates) {
        int count = 0;
        for (JsonObject t : chain(templates)) if ("changed".equals(str(t, "state"))) count++;
        return count;
    }

    /** "python-base ← pyspark-pipeline ← acme-pyspark · 1 changed by hand" (the second part only when a copy differs). */
    static String summary(JsonArray templates, boolean fr) {
        List<String> names = new ArrayList<>();
        for (JsonObject t : chain(templates)) names.add(str(t, "name"));
        String line = names.isEmpty() ? (fr ? "aucun" : "none") : String.join(" ← ", names);
        int changed = changed(templates);
        return changed == 0 ? line : line + " · " + changed + (fr ? " modifiés à la main" : " changed by hand");
    }

    /** The list: each template of the chain, what it extends, its source and state; Update, New template; then the
     *  project's own additions with Add a question / Add a rule. */
    static String html(JsonArray templates, boolean fr) {
        StringBuilder html = new StringBuilder("<h2>Templates</h2><p>")
            .append(link("update", fr ? "Mettre à jour" : "Update")).append(" · ")
            .append(link("new", fr ? "Nouveau template" : "New template")).append("</p><p><i>")
            .append(escape(fr ? "Les templates du projet, bases d'abord : leurs copies dans .archvow/templates/ sont ce que lisent chaque clone et la CI."
                : "The project's templates, bases first: their copies in .archvow/templates/ are what every clone and the CI read."))
            .append("</i></p>");
        List<JsonObject> chain = chain(templates);
        if (chain.isEmpty()) html.append("<p>").append(escape(fr ? "Aucun template copié dans ce projet : lance archvow init --force."
            : "No template copied in this project yet: run archvow init --force.")).append("</p>");
        for (JsonObject t : chain) {
            boolean changed = "changed".equals(str(t, "state"));
            html.append("<h3>").append(escape(str(t, "name"))).append(" <small>").append(escape(str(t, "version"))).append("</small> · ")
                .append(changed ? "<font color='#b26b00'>" + escape(fr ? "modifié à la main" : "changed by hand") + "</font>"
                    : escape(fr ? "tel que copié" : "as copied")).append("</h3>");
            if (!str(t, "description").isEmpty()) html.append("<p>").append(escape(str(t, "description"))).append("</p>");
            String bases = joined(t, "extends");
            if (!bases.isEmpty()) html.append("<p>").append(fr ? "Étend" : "Extends").append(": ").append(escape(bases)).append("</p>");
            html.append("<p><small>").append(escape(source(str(t, "source"), fr))).append("</small></p>");
            if (changed) html.append("<p><font color='#b26b00'>").append(escape(str(t, "why"))).append(" (ARCH060)</font> · ")
                .append(link("diff", fr ? "Voir le changement" : "See the change")).append("</p>");
        }
        JsonObject own = null;
        if (templates != null) for (JsonElement t : templates)
            if (t.isJsonObject() && OWN.equals(str(t.getAsJsonObject(), "name"))) own = t.getAsJsonObject();
        html.append("<h3>").append(escape(fr ? "Les ajouts propres à ce projet" : "This project's own additions"))
            .append(" <small>.archvow/template/</small></h3><p>");
        String rules = own == null ? "" : joined(own, "rule_families");
        String agents = own == null ? "" : joined(own, "agents");
        String brings = (rules.isEmpty() ? "" : (fr ? "règles: " : "rules: ") + rules)
            + (!rules.isEmpty() && !agents.isEmpty() ? " · " : "") + (agents.isEmpty() ? "" : "agents: " + agents);
        html.append(escape(brings.isEmpty() ? (fr ? "Aucun pour l'instant : une question ou une règle pour ce seul projet."
            : "None yet: a question or a rule for this project only.") : brings)).append("</p><p>")
            .append(link("question", fr ? "Ajouter une question" : "Add a question")).append(" · ")
            .append(link("rule", fr ? "Ajouter une règle" : "Add a rule")).append("</p>");
        return html.toString();
    }

    /** Where a template came from, as archvow 3.0 records it: archvow's own, a folder by its path from the project's
     *  root (the same in every clone laid out alike), or an installed package by its name. As VS Code's templateSource. */
    static String source(String source, boolean fr) {
        if (source == null || source.isEmpty()) return fr ? "Source : inconnue" : "Source: unknown";
        if ("archvow".equals(source)) return fr ? "Source : archvow" : "Source: archvow";
        if (source.matches("^(/|[A-Za-z]:[\\\\/]|\\\\\\\\).*")) return fr
            ? "Source : " + source + " (un chemin absolu : vrai sur cette machine seulement)"
            : "Source: " + source + " (an absolute path: true on this machine only)";
        if (source.contains("/") || source.contains("\\") || source.startsWith(".")) return fr
            ? "Source : " + source + " (un dossier, depuis la racine du projet)" : "Source: " + source + " (a folder, from the project's root)";
        return fr ? "Source : " + source + " (un paquet installé)" : "Source: " + source + " (an installed package)";
    }

    /** What a link of the list opens: the command and its prefilled values; null when it is not one of them. */
    static Map.Entry<String, Map<String, String>> command(String link) {
        if (link == null || !link.startsWith(LINK)) return null;
        String action = link.substring(LINK.length());
        Map<String, String> values = new LinkedHashMap<>();
        String command = switch (action) {
            case "update" -> "template update";
            case "new" -> "template new";
            case "question" -> "template question";
            case "rule" -> "template rule";
            case "diff" -> "diff";
            default -> null;
        };
        if (command == null) return null;
        if ("question".equals(action) || "rule".equals(action)) values.put("template", OWN);
        return Map.entry(command, values);
    }

    private static String joined(JsonObject t, String key) {
        if (!t.has(key) || !t.get(key).isJsonArray()) return "";
        List<String> out = new ArrayList<>();
        for (JsonElement e : t.getAsJsonArray(key)) if (e.isJsonPrimitive()) out.add(e.getAsString());
        return String.join(", ", out);
    }

    private static String link(String action, String text) {
        return "<a href='" + LINK + action + "'>" + escape(text) + "</a>";
    }

    private static String str(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;");
    }
}
