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
            view.showFailure("CLI unavailable");
            require(view.componentCount() == 0, "failure state clears stale components");
            view.showData(manifest(), null, Map.of(), Map.of(), 0, 0, false);
            require(view.componentCount() == 2, "recovery after failure");
        });
        System.out.println("OK: 8");
    }
}
