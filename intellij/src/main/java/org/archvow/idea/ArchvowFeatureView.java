package org.archvow.idea;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A feature's page in the editor tab (0.24.0, contract 2.0), as on the VS Code page: what the product does in
 * it, how far it is, what it uses and what uses it (worked out from the components' uses), its components by
 * layer, and what is open in it. Its actions: add a component to it, its doc ({@code archvow doc <feature>}),
 * its AI prompt ({@code archvow prompt --feature}), its next steps, rename. The page is HTML ({@link #html}, plain
 * Java, tested headless by {@code FeatureViewRegression}); links and buttons go back through {@link Actions}.
 */
final class ArchvowFeatureView extends JPanel {
    interface Actions {
        void showComponent(String id);
        void showFeature(String name);
        void openFile(String relative);
        /** A command form, prefilled: component add (feature), feature rename, feature remove. */
        void openCommand(String command, Map<String, String> values);
        void openDoc(String feature);
        void copyPrompt(String feature);
        void nextSteps(String feature);
    }

    private final Actions actions;
    private final JEditorPane page = new JEditorPane("text/html", "");
    private final JButton add = ArchvowSplitView.primary("Add component");
    private final JButton doc = new JButton("Open feature doc");
    private final JButton prompt = new JButton("Copy AI prompt");
    private final JButton next = new JButton("Next steps");
    private final JButton rename = new JButton("Rename…");
    private final JButton remove = new JButton("Remove…");
    private String feature = "";

    ArchvowFeatureView(Actions actions) {
        super(new BorderLayout(0, 8));
        this.actions = actions;
        setName("archvow.feature");
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        page.setEditable(false);
        page.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        page.addHyperlinkListener(e -> {
            if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED || e.getDescription() == null) return;
            String target = e.getDescription();
            int colon = target.indexOf(':');
            if (colon < 0) return;
            String kind = target.substring(0, colon);
            String value = target.substring(colon + 1);
            switch (kind) {
                case "component" -> actions.showComponent(value);
                case "feature" -> actions.showFeature(value);
                case "file" -> actions.openFile(value);
                default -> { }
            }
        });
        add.addActionListener(e -> actions.openCommand("component add", Map.of("feature", feature)));
        doc.addActionListener(e -> actions.openDoc(feature));
        prompt.addActionListener(e -> actions.copyPrompt(feature));
        next.addActionListener(e -> actions.nextSteps(feature));
        rename.addActionListener(e -> actions.openCommand("feature rename", Map.of("feature", feature)));
        remove.addActionListener(e -> actions.openCommand("feature remove", Map.of("feature", feature)));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        for (JButton b : List.of(add, doc, prompt, next, rename, remove)) buttons.add(b);
        add(buttons, BorderLayout.NORTH);
        add(new JScrollPane(page), BorderLayout.CENTER);
    }

    /** The feature {@code view} ({@code show <feature> --json}); {@code pending}: open items per component. */
    void show(JsonObject view, JsonObject spec, Map<String, Integer> pending, Map<String, String> openItems, boolean french) {
        feature = view == null ? "" : ArchvowGrammar.str(view, "feature");
        add.setText(french ? "Ajouter un composant" : "Add component");
        doc.setText(french ? "Ouvrir la doc" : "Open feature doc");
        doc.setToolTipText((french ? "archvow doc " + feature + " : sa page, écrite dans .archvow/doc/ (ignoré par Git)"
            : "archvow doc " + feature + ": its page, written into .archvow/doc/ (ignored by Git)"));
        prompt.setText(french ? "Copier le prompt IA" : "Copy AI prompt");
        prompt.setToolTipText(french ? "archvow prompt --feature " + feature + " : la fonctionnalité en quelques lignes, puis la tâche de son prochain composant"
            : "archvow prompt --feature " + feature + ": the feature in a few lines, then the task of its next component");
        next.setText(french ? "Prochaines étapes" : "Next steps");
        rename.setText(french ? "Renommer…" : "Rename…");
        remove.setText(french ? "Retirer…" : "Remove…");
        boolean shared = "shared".equals(feature);
        rename.setVisible(!shared);
        remove.setVisible(!shared && components(view).isEmpty());
        page.setText(html(view, spec, pending, openItems, french));
        page.setCaretPosition(0);
    }

    private static List<JsonObject> components(JsonObject view) {
        return ArchvowGrammar.objects(view, "components");
    }

    private static String esc(String text) {
        return (text == null ? "" : text).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String link(String kind, String value, String text) {
        return "<a href='" + kind + ":" + esc(value) + "'>" + esc(text) + "</a>";
    }

    /** "orders (create_order → get_current_user, ...)": the features a feature uses, or is used by, with why. */
    private static String pairs(JsonObject byFeature) {
        List<String> out = new ArrayList<>();
        if (byFeature == null) return "";
        for (Map.Entry<String, JsonElement> e : byFeature.entrySet()) {
            List<String> why = new ArrayList<>();
            if (e.getValue() instanceof JsonArray list)
                for (JsonElement p : list) if (p instanceof JsonObject pair)
                    why.add(ArchvowGrammar.str(pair, "component") + " → " + ArchvowGrammar.str(pair, "uses"));
            out.add(link("feature", e.getKey(), e.getKey()) + " <span style='color:#8c8c8c'>(" + esc(String.join(", ", why)) + ")</span>");
        }
        return String.join(" · ", out);
    }

    /** The page: head, progress, map, components by layer, what is open. Plain Java: no IntelliJ needed. */
    static String html(JsonObject view, JsonObject spec, Map<String, Integer> pending, Map<String, String> openItems, boolean fr) {
        if (view == null) return ArchvowHtml.body(null, null) + esc(fr ? "Choisis une fonctionnalité dans la fenêtre Archvow." : "Choose a feature in the Archvow tool window.") + "</body></html>";
        String name = ArchvowGrammar.str(view, "feature");
        List<JsonObject> comps = components(view);
        int open = 0, verified = 0;
        for (JsonObject c : comps) {
            open += pending.getOrDefault(ArchvowGrammar.str(c, "id"), 0);
            if ("verified".equals(ArchvowGrammar.str(c, "status"))) verified++;
        }
        List<String> tickets = new ArrayList<>();
        for (JsonObject container : ArchvowGrammar.objects(spec, "containers"))
            for (JsonObject c : ArchvowGrammar.objects(container, "components"))
                if (name.equals(ArchvowGrammar.str(c, "feature")) && !ArchvowGrammar.str(c, "spec").isEmpty() && !tickets.contains(ArchvowGrammar.str(c, "spec")))
                    tickets.add(ArchvowGrammar.str(c, "spec"));
        StringBuilder out = new StringBuilder(ArchvowHtml.body(null, null));
        out.append("<div style='color:#8c8c8c; ").append(ArchvowHtml.size(.85)).append("'>ARCHVOW · ").append(fr ? "FONCTIONNALITÉ" : "FEATURE").append("</div>");
        out.append(ArchvowHtml.heading(esc(name) + (open > 0 ? " <span style='color:#d29922; " + ArchvowHtml.size(.8) + "'>"
            + esc(fr ? open + " ouvert" + (open > 1 ? "s" : "") : open + " open") + "</span>" : ""), 1.6, "margin-top:4px"));
        List<String> meta = new ArrayList<>();
        meta.add(comps.size() + (fr ? (comps.size() == 1 ? " composant" : " composants") : (comps.size() == 1 ? " component" : " components")));
        String brief = ArchvowGrammar.str(view, "brief");
        if (!brief.isEmpty()) meta.add("brief " + link("file", brief, brief));
        if (!tickets.isEmpty()) meta.add("tickets " + esc(String.join(", ", tickets)));
        out.append("<p style='color:#8c8c8c'>").append(String.join(" · ", meta)).append("</p>");
        String description = ArchvowGrammar.str(view, "description");
        if (!description.isEmpty()) out.append("<p>").append(esc(description)).append("</p>");
        out.append("<p style='color:#8c8c8c'>").append(esc(fr ? verified + " composants vérifiés sur " + comps.size()
            : verified + " of " + comps.size() + " components verified")).append("</p>");
        JsonObject uses = view.get("uses") instanceof JsonObject u ? u : new JsonObject();
        JsonObject usedBy = view.get("used_by") instanceof JsonObject u ? u : new JsonObject();
        if (uses.size() > 0 || usedBy.size() > 0) {
            out.append("<p>");
            if (uses.size() > 0) out.append(esc(fr ? "Utilise " : "Uses ")).append(pairs(uses));
            if (uses.size() > 0 && usedBy.size() > 0) out.append(" &nbsp;·&nbsp; ");
            if (usedBy.size() > 0) out.append(esc(fr ? "Utilisée par " : "Used by ")).append(pairs(usedBy));
            out.append("</p>");
        }
        // the components by layer, the entry points first
        Map<String, List<JsonObject>> byLayer = new TreeMap<>(java.util.Comparator.reverseOrder());
        for (JsonObject c : comps) byLayer.computeIfAbsent(ArchvowGrammar.str(c, "layer"), k -> new ArrayList<>()).add(c);
        out.append(ArchvowHtml.heading(esc(fr ? "Composants, par couche" : "Components, by layer"), 1.05, "margin-top:10px"));
        if (comps.isEmpty()) {
            out.append("<p style='color:#8c8c8c'>").append(esc(fr ? "Pas encore de composant. Ajoute le premier : son type, son nom, ce qu'il doit faire."
                : "No component yet. Add the first one: its type, its name, what it must do.")).append("</p>");
        } else {
            out.append("<table cellspacing='0' cellpadding='4' width='100%'><tr style='color:#8c8c8c'><td>")
                .append(esc(fr ? "Composant" : "Component")).append("</td><td>Type</td><td>Code</td><td>").append(esc(fr ? "Statut" : "Status")).append("</td></tr>");
            for (Map.Entry<String, List<JsonObject>> layer : byLayer.entrySet()) {
                out.append("<tr><td colspan='4' style='color:#8c8c8c; font-family:monospace'>").append(esc(layer.getKey())).append("</td></tr>");
                for (JsonObject c : layer.getValue()) {
                    String id = ArchvowGrammar.str(c, "id");
                    int p = pending.getOrDefault(id, 0);
                    String status = p > 0 ? (fr ? p + " ouvert" + (p > 1 ? "s" : "") : p + " open") : ArchvowGrammar.str(c, "status");
                    String colour = p > 0 ? "#d29922" : "verified".equals(ArchvowGrammar.str(c, "status")) ? "#3fb950" : "#8c8c8c";
                    String file = ArchvowGrammar.str(c, "file");
                    String[] parts = file.split("/");
                    String shortFile = parts.length >= 2 ? parts[parts.length - 2] + "/" + parts[parts.length - 1] : file;
                    out.append("<tr><td>").append(link("component", id, id)).append("</td><td style='color:#8c8c8c'>").append(esc(ArchvowGrammar.str(c, "type")))
                        .append("</td><td style='font-family:monospace'>").append(link("file", file, shortFile)).append("</td><td style='color:")
                        .append(colour).append("'>").append(esc(status)).append("</td></tr>");
                }
            }
            out.append("</table>");
        }
        // where it is (0.24.1): its vow, its components' vows, their code and tests, as one tree
        List<ArchvowFilePlan.Row> files = new ArrayList<>();
        files.add(new ArchvowFilePlan.Row("here", ".archvow/features/" + name + "/" + name + ".vow", ""));
        for (JsonObject c : comps)
            for (ArchvowFilePlan.Row r : ArchvowFilePlan.componentFiles(spec, ArchvowGrammar.str(c, "id"), fr))
                if (files.stream().noneMatch(x -> x.path().equals(r.path()))) files.add(r);
        out.append(ArchvowHtml.heading(esc(fr ? "Où elle est" : "Where it is"), 1.05, "margin-top:12px"));
        out.append("<div style='font-family:monospace'>");
        for (ArchvowFilePlan.TreeLine l : ArchvowFilePlan.treeLines(files, false)) {
            String shown = l.row() != null && !l.dir() && l.row().path().endsWith(".py") ? link("file", l.row().path(), l.name()) : esc(l.name());
            out.append("<div><span style='color:#8c8c8c'>").append(esc(l.guide()).replace(" ", "&nbsp;")).append("</span>")
                .append(l.dir() ? "<span style='color:#8c8c8c'>" + shown + "</span>" : shown).append("</div>");
        }
        out.append("</div>");
        // the map, as text: what it uses ← it ← what uses it
        out.append(ArchvowHtml.heading(esc(fr ? "Carte des fonctionnalités" : "Feature map"), 1.05, "margin-top:12px"));
        List<String> chain = new ArrayList<>();
        for (String f : uses.keySet()) chain.add(link("feature", f, f));
        String self = "<b>" + esc(name) + "</b>";
        String left = chain.isEmpty() ? "" : String.join(", ", chain) + " ← ";
        List<String> right = new ArrayList<>();
        for (String f : usedBy.keySet()) right.add(link("feature", f, f));
        out.append("<p>").append(left).append(self).append(right.isEmpty() ? "" : " ← " + String.join(", ", right)).append("</p>");
        out.append("<p style='color:#8c8c8c'>").append(esc(fr ? "Déduite de ce qu'utilisent les composants. Jamais de boucle entre fonctionnalités (ARCH061) ; shared n'utilise une fonctionnalité que depuis un point d'entrée (ARCH062)."
            : "Worked out from the components' uses. Never a loop between features (ARCH061); shared uses a feature only from an entry point (ARCH062).")).append("</p>");
        out.append(ArchvowHtml.heading(esc(fr ? "Ouvert dans cette fonctionnalité" : "Open in this feature"), 1.05, "margin-top:12px"));
        Map<String, String> items = new LinkedHashMap<>();
        for (JsonObject c : comps) {
            String id = ArchvowGrammar.str(c, "id");
            if (openItems.containsKey(id)) items.put(id, openItems.get(id));
        }
        if (items.isEmpty()) out.append("<p style='color:#3fb950'>").append(esc(fr ? "Rien d'ouvert : chaque décision est prise." : "Nothing open: every decision is made.")).append("</p>");
        else {
            out.append("<ul>");
            for (Map.Entry<String, String> e : items.entrySet())
                out.append("<li>").append(link("component", e.getKey(), e.getKey())).append(" <span style='color:#8c8c8c'>· ").append(esc(e.getValue())).append("</span></li>");
            out.append("</ul>");
        }
        return out.append("</body></html>").toString();
    }
}
