package org.archvow.idea;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders Rules separately from Data & tasks; the latter's pending counter
 * uses exactly the same classified columns and tasks shown in the panel.
 * Native styled text remains selectable in light and dark themes.
 */
final class ArchvowDataView {
    private ArchvowDataView() {}

    enum Kind { COLUMN, TASK }

    record Item(Kind kind, String id, String title, String detail, boolean pending) {}

    static List<Item> items(JsonObject component) {
        List<Item> items = new ArrayList<>();
        for (JsonObject column : ArchvowGrammar.objects(component, "columns")) {
            String name = ArchvowGrammar.str(column, "name");
            if (name.isBlank()) name = "(unnamed column)";
            String classification = ArchvowGrammar.str(column, "class");
            boolean pending = classification.isBlank();
            items.add(new Item(Kind.COLUMN, name, name,
                pending ? "Classification missing" : "Classification: " + classification, pending));
        }
        for (JsonObject task : ArchvowGrammar.objects(component, "tasks")) {
            String id = ArchvowGrammar.str(task, "task");
            String description = ArchvowGrammar.str(task, "summary");
            if (description.isBlank()) description = id.isBlank() ? "Unnamed task" : "Task " + id;
            boolean pending = !ArchvowGrammar.flag(task, "done");
            items.add(new Item(Kind.TASK, id, description, taskDetails(task), pending));
        }
        return List.copyOf(items);
    }

    static int pendingCount(JsonObject component) {
        int count = 0;
        for (Item item : items(component)) if (item.pending()) count++;
        return count;
    }

    private static String taskDetails(JsonObject task) {
        List<String> details = new ArrayList<>();
        for (JsonObject item : ArchvowGrammar.objects(task, "items")) {
            String key = ArchvowGrammar.str(item, "key");
            String text = ArchvowGrammar.str(item, "text");
            String kind = ArchvowGrammar.str(item, "kind");
            String content = (kind.isBlank() ? "" : kind + " ")
                + (key.isBlank() ? "" : key + ": ") + text;
            if (!content.isBlank()) details.add((ArchvowGrammar.flag(item, "done") ? "✓ " : "• ") + content.trim());
        }
        return String.join("\n", details);
    }

    static JTextPane create() {
        JTextPane pane = new JTextPane();
        pane.setEditable(false);
        pane.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        pane.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        pane.setBackground(UIManager.getColor("Panel.background"));
        pane.setForeground(UIManager.getColor("Label.foreground"));
        return pane;
    }

    static void render(JTextPane pane, JsonObject component, boolean french) {
        boolean dark = isDark();
        Color main = dark ? new Color(222, 229, 237) : new Color(35, 47, 66);
        Color muted = dark ? new Color(162, 174, 190) : new Color(91, 105, 125);
        Color pending = dark ? new Color(248, 188, 99) : new Color(161, 104, 15);
        Color done = dark ? new Color(120, 220, 167) : new Color(17, 121, 76);
        Color heading = dark ? new Color(171, 200, 247) : new Color(38, 91, 167);
        StyledDocument document = new DefaultStyledDocument();
        pane.setDocument(document);
        pane.setBackground(UIManager.getColor("Panel.background"));
        pane.setForeground(main);
        pane.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        SimpleAttributeSet plain = style(main, false, 13);
        SimpleAttributeSet sub = style(muted, false, 12);
        SimpleAttributeSet header = style(heading, true, 16);
        SimpleAttributeSet section = style(main, true, 14);
        SimpleAttributeSet warning = style(pending, true, 13);
        SimpleAttributeSet success = style(done, true, 13);

        try {
            List<Item> items = items(component);
            long outstanding = items.stream().filter(Item::pending).count();
            append(document, french ? "Données et tâches\n" : "Data & tasks\n", header);
            append(document, (french ? "Actions en attente : " : "Pending actions: ") + outstanding + "\n", outstanding > 0 ? warning : success);
            if (outstanding == 0) {
                append(document, french ? "Aucune action en attente dans cette vue.\n" : "No pending actions in this view.\n", sub);
            } else {
                for (Item item : items) {
                    if (!item.pending()) continue;
                    String kind = item.kind() == Kind.COLUMN ? (french ? "Colonne" : "Column") : (french ? "Tâche" : "Task");
                    append(document, "  ● " + kind + " : " + item.title() + "\n", warning);
                    if (!item.detail().isBlank()) append(document, "     " + localizedDetail(item, french) + "\n", sub);
                }
            }

            append(document, "\n" + (french ? "Colonnes et classifications" : "Columns and classifications") + "\n", section);
            boolean columns = false;
            for (Item item : items) {
                if (item.kind() != Kind.COLUMN) continue;
                columns = true;
                append(document, "  " + (item.pending() ? "● " : "✓ ") + item.title() + " — " + localizedDetail(item, french) + "\n",
                    item.pending() ? warning : success);
            }
            if (!columns) append(document, french ? "Aucune colonne déclarée.\n" : "No columns declared.\n", sub);

            append(document, "\n" + (french ? "Tâches" : "Tasks") + "\n", section);
            boolean tasks = false;
            for (Item item : items) {
                if (item.kind() != Kind.TASK) continue;
                tasks = true;
                append(document, "  " + (item.pending() ? "● " : "✓ ") + item.title() + "\n", item.pending() ? warning : success);
                if (!item.detail().isBlank()) append(document, "    " + item.detail() + "\n", plain);
            }
            if (!tasks) append(document, french ? "Aucune tâche pour ce composant.\n" : "No tasks for this component.\n", sub);

            append(document, "\n" + (french ? "Données personnelles" : "Personal data") + "\n", section);
            JsonElement personal = component == null ? null : component.get("personal");
            if (personal != null && personal.isJsonObject() && !personal.getAsJsonObject().entrySet().isEmpty()) {
                for (Map.Entry<String, JsonElement> entry : personal.getAsJsonObject().entrySet()) {
                    append(document, "  " + entry.getKey() + ": " + readable(entry.getValue()) + "\n", plain);
                }
            } else append(document, french ? "Aucun flux de données personnelles déclaré.\n" : "No personal data flows declared.\n", sub);
        } catch (BadLocationException ex) {
            pane.setText((french ? "Affichage indisponible : " : "Unable to render: ") + ex.getMessage());
        }
        pane.setCaretPosition(0);
    }

    static void renderRules(JTextPane pane, JsonObject component, boolean french) {
        boolean dark = isDark();
        Color main = dark ? new Color(222,229,237) : new Color(35,47,66);
        StyledDocument document = new DefaultStyledDocument();
        pane.setDocument(document);
        pane.setBackground(UIManager.getColor("Panel.background"));
        SimpleAttributeSet heading = style(dark ? new Color(171,200,247) : new Color(38,91,167), true, 16);
        SimpleAttributeSet plain = style(main, false, 13);
        SimpleAttributeSet muted = style(dark ? new Color(162,174,190) : new Color(91,105,125), false, 12);
        try {
            append(document, french ? "Règles applicables\n" : "Applicable rules\n", heading);
            List<JsonObject> rules = ArchvowGrammar.objects(component, "rules");
            if (rules.isEmpty()) append(document, french ?
                "Aucune règle applicable.\n" : "No applicable rules.\n", muted);
            for (JsonObject rule : rules) {
                String id = ArchvowGrammar.str(rule,"id");
                String text = ArchvowGrammar.str(rule,"text");
                String scope = readable(rule.get("scope"));
                append(document, "\n" + (id.isBlank() ? "•" : id) + "  " + text + "\n", plain);
                if (!scope.isBlank() && !"—".equals(scope))
                    append(document, (french ? "Portée : " : "Scope: ") + scope + "\n", muted);
            }
        } catch (BadLocationException e) { pane.setText(e.getMessage()); }
        pane.setCaretPosition(0);
    }

    private static String localizedDetail(Item item, boolean french) {
        if (!french || item.kind() != Kind.COLUMN) return item.detail();
        return item.pending() ? "Classification à renseigner" : item.detail().replace("Classification:", "Classification :");
    }

    private static String readable(JsonElement value) {
        if (value == null || value.isJsonNull()) return "—";
        if (value.isJsonArray()) {
            List<String> texts = new ArrayList<>();
            for (JsonElement element : value.getAsJsonArray()) texts.add(readable(element));
            return String.join(", ", texts);
        }
        if (value.isJsonObject()) {
            List<String> texts = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet())
                texts.add(entry.getKey() + "=" + readable(entry.getValue()));
            return String.join("; ", texts);
        }
        return value.getAsString();
    }

    private static SimpleAttributeSet style(Color color, boolean bold, int size) {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setForeground(s, color);
        StyleConstants.setBold(s, bold);
        StyleConstants.setFontFamily(s, Font.SANS_SERIF);
        StyleConstants.setFontSize(s, size);
        StyleConstants.setSpaceAbove(s, 1);
        StyleConstants.setSpaceBelow(s, 2);
        return s;
    }

    private static void append(StyledDocument document, String text, SimpleAttributeSet style) throws BadLocationException {
        document.insertString(document.getLength(), text, style);
    }

    private static boolean isDark() {
        Color c = UIManager.getColor("Panel.background");
        return c != null && (c.getRed() * .299 + c.getGreen() * .587 + c.getBlue() * .114) < 128;
    }
}
