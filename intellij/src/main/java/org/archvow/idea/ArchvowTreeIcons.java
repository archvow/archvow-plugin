package org.archvow.idea;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * The trees' icons (Components, Commands) and the component add shortcuts, from archvow/icons.json
 * (../contract/forms/icons.json, the same drawings as VS Code's page): a 14 x 14 grid painted with
 * Java2D in the text's colour, or a component's status colour. Unknown kinds draw nothing.
 */
final class ArchvowTreeIcons {
    private ArchvowTreeIcons() {}

    static final JsonObject DATA = load();

    private static JsonObject load() {
        try (InputStream in = ArchvowTreeIcons.class.getResourceAsStream("/archvow/icons.json")) {
            if (in == null) return new JsonObject();
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static JsonObject section(String key) {
        JsonElement e = DATA.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }

    static boolean has(String kind) { return section("shapes").has(kind); }

    /** A tree node that has a drawing: the root, a container, a layer, a component (its type). */
    interface Kinded { String iconKind(); }

    /** A tree node that is only a name: Architecture, a container, a layer (`layer` set for a layer). */
    record Node(String text, String kind, String layer) implements Kinded {
        @Override public String iconKind() { return kind; }
        @Override public String toString() { return text; }
    }

    /** A component type's drawing (schema, reader...), or a page for a type the icons do not know. */
    static String typeKind(String type) { return has(type) ? type : "component"; }

    /** A command group's drawing (components, decisions, safety...). */
    static String groupKind(String group) {
        JsonElement e = section("groups").get(group);
        return e == null ? "other" : e.getAsString();
    }

    /** A command's drawing: its own (init, check...), else its verb's (add, edit, remove...), as VS Code's commandIcon. */
    static String commandKind(List<String> path) {
        JsonObject own = section("commands"), verbs = section("verbs");
        String name = String.join(" ", path);
        if (own.has(name)) return own.get(name).getAsString();
        if (!path.isEmpty() && own.has(path.get(0))) return own.get(path.get(0)).getAsString();
        if (!path.isEmpty() && verbs.has(path.get(path.size() - 1))) return verbs.get(path.get(path.size() - 1)).getAsString();
        return "other";
    }

    /** The icon of a kind, in `tint` (null: the component's foreground when painted). */
    static Icon icon(String kind, Color tint) { return new ShapeIcon(kind, tint, 16); }

    static final class ShapeIcon implements Icon {
        final String kind;
        final Color tint;
        private final int size;
        ShapeIcon(String kind, Color tint, int size) { this.kind = kind; this.tint = tint; this.size = size; }
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
        @Override public void paintIcon(Component c, Graphics graphics, int x, int y) {
            JsonElement shapes = section("shapes").get(kind);
            if (shapes == null || !shapes.isJsonArray()) return;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.translate(x + (size - 14) / 2.0, y + (size - 14) / 2.0);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                Color ink = tint != null ? tint : c != null && c.isEnabled() ? c.getForeground() : UIManager.getColor("Label.disabledForeground");
                g.setColor(ink == null ? Color.GRAY : ink);
                float stroke = DATA.has("stroke") ? DATA.get("stroke").getAsFloat() : 1.4f;
                g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (JsonElement raw : shapes.getAsJsonArray()) paint(g, raw.getAsJsonArray());
            } finally {
                g.dispose();
            }
        }
    }

    private static double n(JsonArray a, int i) { return a.size() > i ? a.get(i).getAsDouble() : 0; }

    /** One shape: l line, c circle, C filled circle, e ellipse, r rectangle, p path, P filled path. */
    static void paint(Graphics2D g, JsonArray s) {
        String k = s.get(0).getAsString();
        switch (k) {
            case "l" -> g.draw(new Line2D.Double(n(s, 1), n(s, 2), n(s, 3), n(s, 4)));
            case "c" -> g.draw(new Ellipse2D.Double(n(s, 1) - n(s, 3), n(s, 2) - n(s, 3), 2 * n(s, 3), 2 * n(s, 3)));
            case "C" -> g.fill(new Ellipse2D.Double(n(s, 1) - n(s, 3), n(s, 2) - n(s, 3), 2 * n(s, 3), 2 * n(s, 3)));
            case "e" -> g.draw(new Ellipse2D.Double(n(s, 1) - n(s, 3), n(s, 2) - n(s, 4), 2 * n(s, 3), 2 * n(s, 4)));
            case "r" -> g.draw(new RoundRectangle2D.Double(n(s, 1), n(s, 2), n(s, 3), n(s, 4), 2 * n(s, 5), 2 * n(s, 5)));
            case "p" -> g.draw(path(s.get(1).getAsString()));
            case "P" -> g.fill(path(s.get(1).getAsString()));
            default -> { }
        }
    }

    /** An SVG path with absolute M, L, Q, C and Z only (what icons.json allows). */
    static Path2D path(String d) {
        Path2D p = new Path2D.Double();
        String[] tokens = d.trim().replaceAll("([MLQCZ])", " $1 ").trim().split("[\\s,]+");
        int i = 0;
        String command = "M";
        while (i < tokens.length) {
            String t = tokens[i];
            if (t.matches("[MLQCZ]")) { command = t; i++; if ("Z".equals(command)) { p.closePath(); } continue; }
            switch (command.toUpperCase(Locale.ROOT)) {
                case "M" -> { p.moveTo(Double.parseDouble(tokens[i]), Double.parseDouble(tokens[i + 1])); i += 2; command = "L"; }
                case "L" -> { p.lineTo(Double.parseDouble(tokens[i]), Double.parseDouble(tokens[i + 1])); i += 2; }
                case "Q" -> { p.quadTo(Double.parseDouble(tokens[i]), Double.parseDouble(tokens[i + 1]),
                    Double.parseDouble(tokens[i + 2]), Double.parseDouble(tokens[i + 3])); i += 4; }
                case "C" -> { p.curveTo(Double.parseDouble(tokens[i]), Double.parseDouble(tokens[i + 1]), Double.parseDouble(tokens[i + 2]),
                    Double.parseDouble(tokens[i + 3]), Double.parseDouble(tokens[i + 4]), Double.parseDouble(tokens[i + 5])); i += 6; }
                default -> i++;
            }
        }
        return p;
    }
}
