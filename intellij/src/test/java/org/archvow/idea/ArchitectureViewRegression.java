package org.archvow.idea;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.util.Map;

public final class ArchitectureViewRegression {
    private static JsonObject item(String... kv) {
        JsonObject o = new JsonObject();
        for (int i = 0; i < kv.length; i += 2) o.addProperty(kv[i], kv[i + 1]);
        return o;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static JsonObject manifest() {
        JsonObject project = new JsonObject();
        project.add("system", item("name", "acoss_pipeline"));
        JsonObject container = item("id", "acoss_pipeline");
        JsonArray components = new JsonArray();
        components.add(item("id", "config", "type", "foundation", "layer", "p0_common"));
        components.add(item("id", "spark", "type", "foundation", "layer", "p0_common"));
        container.add("components", components);
        JsonArray containers = new JsonArray(); containers.add(container);
        project.add("containers", containers);
        return project;
    }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ArchvowArchitectureView view = new ArchvowArchitectureView();
            // `show --json` is the source of truth even if `check.components` is absent.
            view.showData(manifest(), item("ok", "true"), Map.of(), Map.of("config", 1), 1, 1, false);
            require(view.componentCount() == 2, "manifest components should render without check status data");
            require("config".equals(view.componentAt(0)), "first component");
            require("Not evaluated".equals(view.stateAt(0)), "status fallback");
            require(view.pendingAt(0) == 1, "pending indicator");
            require(view.inventoryTable().getColumnCount() == 6, "columns should show layer and container");

            JsonObject check = new JsonObject();
            JsonArray statuses = new JsonArray();
            statuses.add(item("component", "config", "status", "implemented"));
            check.add("components", statuses);
            view.showData(manifest(), check, Map.of(), Map.of("config", 1), 1, 1, true);
            require("Implémenté".equals(view.stateAt(0)), "translated check status");
            require("Non évalué".equals(view.stateAt(1)), "partial status list must not hide spark");
            require("Composant".equals(view.inventoryTable().getColumnName(0)), "localized headings");
            // a narrow tool window (0.11.1): Component, State and Pending keep their width; Container goes
            // first, then Layer, then Type; narrower than the three, they stay whole (the table scrolls sideways)
            int[] natural = {120, 100, 90, 110, 90, 80};
            int[] wide = ArchvowArchitectureView.columnWidths(800, natural);
            require(java.util.Arrays.stream(wide).sum() == 800 && wide[3] >= 110 && wide[0] >= 120, "wide: " + java.util.Arrays.toString(wide));
            int[] medium = ArchvowArchitectureView.columnWidths(500, natural);
            require(medium[3] == 0 && medium[1] >= 100 && medium[2] >= 90 && medium[4] == 90 && medium[5] == 80,
                "500: no Container: " + java.util.Arrays.toString(medium));
            int[] smaller = ArchvowArchitectureView.columnWidths(420, natural);
            require(smaller[2] == 0 && smaller[3] == 0 && smaller[1] >= 100, "420: Type only: " + java.util.Arrays.toString(smaller));
            int[] narrow = ArchvowArchitectureView.columnWidths(300, natural);
            require(narrow[1] == 0 && narrow[2] == 0 && narrow[3] == 0 && narrow[0] + narrow[4] + narrow[5] == 300,
                "300: Component, State, Pending: " + java.util.Arrays.toString(narrow));
            int[] small = ArchvowArchitectureView.columnWidths(240, natural);
            require(small[0] == 80, "240: Component at its minimum: " + java.util.Arrays.toString(small));
            int[] fits = ArchvowArchitectureView.columnWidths(270, natural);
            require(fits[0] == 100 && fits[4] == 90 && fits[5] == 80, "270: Component narrows, State and Pending whole: " + java.util.Arrays.toString(fits));
            int[] tiny = ArchvowArchitectureView.columnWidths(200, natural);
            require(tiny[0] == ArchvowArchitectureView.COMPONENT_MIN && tiny[4] == 90 && tiny[5] == 80,
                "200: Component at its minimum, then the table scrolls: " + java.util.Arrays.toString(tiny));
            JTable table = view.inventoryTable();
            require(table.getAutoResizeMode() == JTable.AUTO_RESIZE_OFF, "widths set by the view");
            view.showFailure("CLI unavailable");
            require(view.componentCount() == 0, "failure state clears stale components");
            view.showData(manifest(), null, Map.of(), Map.of(), 0, 0, false);
            require(view.componentCount() == 2, "recovery after failure");
        });
        System.out.println("OK: 13");
    }
}
