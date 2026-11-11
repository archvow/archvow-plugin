package org.archvow.idea;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Overview › Packs (0.22.0, archvow 1.26): the project's packs as {@code workspace --json} gives them ({@code packs}:
 * name, version, description, source, what each brings, its state {@code ok} or {@code changed}). IDE-free: the
 * Overview row's line, the list with its actions as links, and what a link opens. VS Code shows the same
 * (workspace.js packsSummary / packsView).
 */
final class ArchvowPacks {
    static final String LINK = "archvow-pack:";

    private ArchvowPacks() {}

    static int changed(JsonArray packs) {
        int count = 0;
        if (packs != null) for (JsonElement p : packs) if (p.isJsonObject() && "changed".equals(str(p.getAsJsonObject(), "state"))) count++;
        return count;
    }

    /** "3 packs · 1 changed by hand" (the second part only when a copy differs). */
    static String summary(JsonArray packs, boolean fr) {
        int size = packs == null ? 0 : packs.size();
        String line = size + " " + (size == 1 ? "pack" : "packs");
        int changed = changed(packs);
        return changed == 0 ? line : line + " · " + changed + (fr ? " modifiés à la main" : " changed by hand");
    }

    /** The list: each pack, what it brings, its source and state, with Update / Remove / Check; Add a pack, New pack. */
    static String html(JsonArray packs, boolean fr) {
        StringBuilder html = new StringBuilder("<h2>Packs</h2><p>")
            .append(link("add", "", fr ? "Ajouter un pack" : "Add a pack")).append(" · ")
            .append(link("new", "", fr ? "Nouveau pack" : "New pack")).append("</p><p><i>")
            .append(escape(fr ? "Un pack apporte les templates, agents et règles d'une entreprise ; sa copie dans .archvow/packs/ est ce que lisent chaque clone et la CI."
                : "A pack brings a company's templates, agents and rules; its copy in .archvow/packs/ is what every clone and the CI read."))
            .append("</i></p>");
        if (packs == null || packs.isEmpty()) {
            return html.append("<p>").append(escape(fr ? "Aucun pack dans ce projet : ses templates viennent d'archvow."
                : "No pack in this project: its templates come from archvow.")).append("</p>").toString();
        }
        for (JsonElement element : packs) {
            if (!element.isJsonObject()) continue;
            JsonObject p = element.getAsJsonObject();
            String name = str(p, "name");
            boolean changed = "changed".equals(str(p, "state"));
            html.append("<h3>").append(escape(name)).append(" <small>").append(escape(str(p, "version"))).append("</small> · ")
                .append(changed ? "<font color='#b26b00'>" + escape(fr ? "modifié à la main" : "changed by hand") + "</font>"
                    : escape(fr ? "tel qu'ajouté" : "as added")).append("</h3>");
            if (!str(p, "description").isEmpty()) html.append("<p>").append(escape(str(p, "description"))).append("</p>");
            String brings = brings(p, fr);
            html.append("<p>").append(escape(brings.isEmpty() ? (fr ? "Rien pour l'instant." : "Nothing yet.") : brings)).append("</p>");
            html.append("<p><small>").append(escape((fr ? "Source : " : "Source: ") + str(p, "source"))).append("</small></p>");
            if (changed) html.append("<p><font color='#b26b00'>").append(escape(str(p, "why"))).append(" (ARCH060)</font></p>");
            html.append("<p>").append(link("update", name, fr ? "Mettre à jour" : "Update")).append(" · ")
                .append(link("remove", name, fr ? "Retirer" : "Remove")).append(" · ")
                .append(link("check", name, fr ? "Vérifier" : "Check"));
            if (changed) html.append(" · ").append(link("diff", "", fr ? "Voir le changement" : "See the change"));
            html.append("</p>");
        }
        return html.toString();
    }

    /** What a link of the list opens: the command and its prefilled values; null when it is not one of them. */
    static Map.Entry<String, Map<String, String>> command(String link) {
        if (link == null || !link.startsWith(LINK)) return null;
        String[] parts = link.substring(LINK.length()).split(":", 2);
        String name = parts.length > 1 ? parts[1] : "";
        Map<String, String> values = new LinkedHashMap<>();
        String command = switch (parts[0]) {
            case "add" -> "pack add";
            case "new" -> "pack new";
            case "update" -> "pack update";
            case "remove" -> "pack remove";
            case "check" -> "pack check";
            case "diff" -> "diff";
            default -> null;
        };
        if (command == null) return null;
        if (!name.isEmpty()) values.put("check".equals(parts[0]) ? "pack" : "name", name);
        return Map.entry(command, values);
    }

    private static String brings(JsonObject p, boolean fr) {
        StringBuilder out = new StringBuilder();
        String[][] parts = {{"templates", fr ? "templates" : "templates"}, {"agents", "agents"}, {"rule_families", fr ? "règles" : "rules"},
            {"regimes", fr ? "régimes de protection" : "privacy regimes"}};
        for (String[] part : parts) {
            if (!p.has(part[0]) || !p.get(part[0]).isJsonArray() || p.getAsJsonArray(part[0]).isEmpty()) continue;
            if (out.length() > 0) out.append(" · ");
            out.append(part[1]).append(": ");
            JsonArray values = p.getAsJsonArray(part[0]);
            for (int i = 0; i < values.size(); i++) out.append(i == 0 ? "" : ", ").append(values.get(i).getAsString());
        }
        return out.toString();
    }

    private static String link(String action, String name, String text) {
        return "<a href='" + LINK + action + (name.isEmpty() ? "" : ":" + escape(name)) + "'>" + escape(text) + "</a>";
    }

    private static String str(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;");
    }
}
