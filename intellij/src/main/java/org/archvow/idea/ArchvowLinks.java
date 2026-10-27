package org.archvow.idea;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * What a component uses and what uses it (archvow show --json: {@code uses}, {@code used_by}): the line
 * under its name, each name a link to that component - what a change to it may break. The VS Code
 * workspace shows the same line ({@code media/workspace.js}, {@code links()}). No IntelliJ class here:
 * tested headless ({@code UsesAndFileRegression}).
 */
final class ArchvowLinks {
    static final String SCHEME = "component:";

    private ArchvowLinks() {}

    static List<String> names(JsonObject view, String key) {
        List<String> out = new ArrayList<>();
        if (view == null || !view.has(key) || !view.get(key).isJsonArray()) return out;
        for (JsonElement e : view.getAsJsonArray(key)) {
            if (e != null && e.isJsonPrimitive()) out.add(e.getAsString());
        }
        return out;
    }

    /** "Uses: a, b  ·  Used by: c", in the project's language; "" when it has neither. */
    static String line(JsonObject view, boolean french) {
        List<String> parts = new ArrayList<>();
        List<String> uses = names(view, "uses");
        List<String> usedBy = names(view, "used_by");
        if (!uses.isEmpty()) parts.add((french ? "Utilise" : "Uses") + ": " + String.join(", ", uses));
        if (!usedBy.isEmpty()) parts.add((french ? "Utilisé par" : "Used by") + ": " + String.join(", ", usedBy));
        return String.join("  ·  ", parts);
    }

    /** The same line as HTML for the component's details: each name a link {@code component:<id>}. */
    static String html(JsonObject view, boolean french) {
        List<String> parts = new ArrayList<>();
        List<String> uses = names(view, "uses");
        List<String> usedBy = names(view, "used_by");
        if (!uses.isEmpty()) parts.add(escape(french ? "Utilise" : "Uses") + ": " + links(uses));
        if (!usedBy.isEmpty()) parts.add(escape(french ? "Utilisé par" : "Used by") + ": " + links(usedBy));
        return parts.isEmpty() ? "" : "<p style='color:#6B7280'>" + String.join("&nbsp;&nbsp;·&nbsp;&nbsp;", parts) + "</p>";
    }

    /** The component a link opens ("component:clean_orders" -> "clean_orders"), or "" for another link. */
    static String component(String link) {
        return link != null && link.startsWith(SCHEME) ? link.substring(SCHEME.length()) : "";
    }

    /** A file's path from the project root, with / ("src/x/y.py"), or "" when it is outside the project. */
    static String relative(String root, String file) {
        if (root == null || file == null) return "";
        String base = root.replace('\\', '/');
        String path = file.replace('\\', '/');
        if (!base.endsWith("/")) base += "/";
        return path.startsWith(base) ? path.substring(base.length()) : "";
    }

    /** The argument of archvow show for a file, with its line when one is known (1-based). */
    static String showArgument(String relative, int line) {
        return line > 0 ? relative + ":" + line : relative;
    }

    private static String links(List<String> ids) {
        List<String> out = new ArrayList<>();
        for (String id : ids) out.add("<a href='" + SCHEME + escape(id) + "'>" + escape(id) + "</a>");
        return String.join(", ", out);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;");
    }
}
