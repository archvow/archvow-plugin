package org.archvow.idea;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The component's progress (0.12.0), read from {@code archvow show <component> --json}, no engine change:
 * decisions made, safety answered, tasks done, examples tested; a part with nothing to count is left out.
 * ONE bar under the component's name, a segment per part, green when complete, amber otherwise, the counts
 * in its tooltip. The VS Code page shows the same bar ({@code media/workspace.js}, {@code progress()}).
 * No IntelliJ class here: tested headless ({@code ProgressRegression}).
 */
final class ArchvowProgress {
    record Part(String key, String label, int done, int total) {
        boolean complete() { return done == total; }
        String said() { return label + ": " + done + "/" + total; }
        /** 10 % steps, as the VS Code page: never full when not complete, never empty when started. */
        int fill() {
            if (complete()) return 100;
            return Math.min(90, Math.max(done > 0 ? 10 : 0, Math.round(10f * done / total) * 10));
        }
    }

    private ArchvowProgress() { }

    static List<Part> parts(JsonObject view, boolean french) {
        List<Part> out = new ArrayList<>();
        add(out, "decisions", french ? "Décisions prises" : "Decisions made", view, "decisions",
            d -> d.has("answer") && !d.get("answer").isJsonNull() && !d.get("answer").getAsString().isBlank());
        add(out, "safety", french ? "Sûreté traitée" : "Safety answered", view, "safety",
            q -> "answered".equals(text(q, "status")));
        add(out, "tasks", french ? "Tâches faites" : "Tasks done", view, "tasks", t -> flag(t, "done", false));
        add(out, "examples", french ? "Exemples réussis" : "Examples passing", view, "examples",
            e -> ArchvowExamples.state(e, false).result().equals("passing") && flag(e, "unchanged", true));
        return out;
    }

    /** "Decisions made: 9/9 · Safety answered: 1/5 · Tasks done: 0/2". */
    static String tooltip(List<Part> parts) {
        List<String> said = new ArrayList<>();
        for (Part p : parts) said.add(p.said());
        return String.join(" · ", said);
    }

    /** The bar as Swing HTML (tables: the editor kit has no CSS width on divs); "" when nothing to count. */
    static String html(JsonObject view, boolean french, boolean dark) {
        List<Part> parts = parts(view, french);
        if (parts.isEmpty()) return "";
        String track = dark ? "#3C3F41" : "#E3E5E8";
        // 1.2.1: the parts share the width (as on the VS Code page) and each bar is 6 px: an empty cell of the
        // editor kit is a line tall, so each holds a 1 pt space (it was a grey block, 120 px wide)
        StringBuilder out = new StringBuilder("<table class='progress' width='100%' title='").append(escape(tooltip(parts)))
            .append("' cellspacing='0' cellpadding='0' style='margin-top:6px; margin-bottom:4px'><tr>");
        int share = 100 / parts.size();
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i);
            String color = p.complete() ? (dark ? "#5FB865" : "#388A34") : (dark ? "#E0A33A" : "#D18616");
            out.append("<td valign='top' width='").append(share).append("%' style='padding-right:").append(i == parts.size() - 1 ? 0 : 14)
                .append("px' title='").append(escape(p.said())).append("'>")
                .append("<div style='color:").append(dark ? "#B4B8BF" : "#5A5F66").append("'>").append(escape(p.label()))
                .append(" <b>").append(p.done()).append("/").append(p.total()).append("</b></div>")
                .append("<table data-part='").append(p.key()).append("' width='100%' cellspacing='0' cellpadding='0'><tr>");
            if (p.fill() > 0) out.append(cell(p.fill(), color));
            if (p.fill() < 100) out.append(cell(100 - p.fill(), track));
            out.append("</tr></table></td>");
        }
        return out.append("</tr></table>").toString();
    }

    private static String cell(int percent, String color) {
        return "<td width='" + percent + "%' height='6' bgcolor='" + color + "' style='font-size:1pt'>&nbsp;</td>";
    }

    private static void add(List<Part> out, String key, String label, JsonObject view, String field, Predicate<JsonObject> done) {
        if (view == null || !view.has(field) || !view.get(field).isJsonArray()) return;
        int total = 0;
        int count = 0;
        for (JsonElement e : view.getAsJsonArray(field)) {
            if (e == null || !e.isJsonObject()) continue;
            total++;
            if (done.test(e.getAsJsonObject())) count++;
        }
        if (total > 0) out.add(new Part(key, label, count, total));
    }

    private static boolean flag(JsonObject o, String key, boolean otherwise) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsBoolean() : otherwise;
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;");
    }
}
