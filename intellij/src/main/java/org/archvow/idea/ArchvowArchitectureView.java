package org.archvow.idea;

import com.google.gson.JsonObject;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Native architecture summary. Do not render the inventory as HTML: Swing's HTML
 * subset may silently drop table rows/styles in different JetBrains themes.
 * This view is driven by `show --json`, not by optional `check.components` data.
 */
final class ArchvowArchitectureView extends JPanel {
    record Row(String id, String type, String layer, String container, String state, int pending) {}

    private final JLabel projectName = new JLabel();
    private final JLabel summary = new JLabel();
    private final JLabel notice = new JLabel();
    private final InventoryTableModel model = new InventoryTableModel();
    private final JTable table = new JTable(model);
    private final JScrollPane scroll;
    /** Column order: Component, Type, Layer, Container, State, Pending. */
    static final int COMPONENT = 0, STATE = 4, PENDING = 5;
    /** The narrowest Component column before the table scrolls sideways. */
    static final int COMPONENT_MIN = 80;
    /** The optional columns, the first kept longest: Type, then Layer, then Container. */
    private static final int[] OPTIONAL = {1, 2, 3};
    private boolean french;
    private Consumer<String> openComponent = id -> {};

    ArchvowArchitectureView() {
        super(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
        JPanel heading = new JPanel(new BorderLayout(0, 4));
        projectName.setFont(projectName.getFont().deriveFont(Font.BOLD, 18f));
        summary.setFont(summary.getFont().deriveFont(12f));
        notice.setFont(notice.getFont().deriveFont(12f));
        heading.add(projectName, BorderLayout.NORTH);
        JPanel details = new JPanel();
        details.setLayout(new javax.swing.BoxLayout(details, javax.swing.BoxLayout.Y_AXIS));
        details.add(summary);
        details.add(notice);
        heading.add(details, BorderLayout.SOUTH);
        add(heading, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(Math.max(30, table.getRowHeight()));
        table.setShowGrid(false);
        table.setFillsViewportHeight(true);
        // the widths follow the room (fitColumns): Component, State and Pending always whole
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setDefaultRenderer(Object.class, new StatusRenderer());
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2 || table.getSelectedRow() < 0) return;
                int row = table.convertRowIndexToModel(table.getSelectedRow());
                if (row >= 0 && row < model.rows.size()) openComponent.accept(model.rows.get(row).id());
            }
        });
        scroll = new JScrollPane(table, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { fitColumns(); }
        });
        add(scroll, BorderLayout.CENTER);
        showLoading("Loading architecture…");
    }

    void onOpenComponent(Consumer<String> listener) {
        openComponent = listener == null ? id -> {} : listener;
    }

    void showLoading(String label) {
        model.setRows(List.of());
        projectName.setText(label);
        summary.setText(" ");
        notice.setText(" ");
    }

    void showFailure(String detail) {
        model.setRows(List.of());
        projectName.setText(french ? "Architecture indisponible" : "Architecture unavailable");
        summary.setText(" ");
        notice.setText(detail == null ? "" : detail);
    }

    void showData(JsonObject manifest, JsonObject check, Map<String, JsonObject> snapshots,
                  Map<String, Integer> pendingCounts, int pendingTotal, int resolvedTotal, boolean fr) {
        french = fr;
        model.setLanguage(fr);
        Map<String, String> checkStates = new LinkedHashMap<>();
        for (JsonObject status : ArchvowGrammar.objects(check, "components")) {
            String id = ArchvowGrammar.str(status, "component");
            if (!id.isBlank()) checkStates.put(id, ArchvowGrammar.str(status, "status"));
        }

        List<Row> entries = new ArrayList<>();
        for (JsonObject container : ArchvowGrammar.objects(manifest, "containers")) {
            String containerName = ArchvowGrammar.str(container, "id");
            for (JsonObject component : ArchvowGrammar.objects(container, "components")) {
                String id = ArchvowGrammar.str(component, "id");
                if (id.isBlank()) continue;
                JsonObject snapshot = snapshots == null ? null : snapshots.get(id);
                String state = checkStates.getOrDefault(id, "");
                if (state.isBlank()) state = ArchvowGrammar.str(snapshot, "status");
                if (state.isBlank()) state = ArchvowGrammar.str(component, "status");
                if (state.isBlank()) state = fr ? "Non évalué" : "Not evaluated";
                state = localizedState(state, fr);
                int pending = pendingCounts == null ? 0 : pendingCounts.getOrDefault(id, 0);
                entries.add(new Row(id, ArchvowGrammar.str(component, "type"),
                    ArchvowGrammar.str(component, "layer"), containerName, state, pending));
            }
        }
        model.setRows(entries);
        JsonObject system = manifest != null && manifest.has("system") && manifest.get("system").isJsonObject()
            ? manifest.getAsJsonObject("system") : null;
        String name = ArchvowGrammar.str(system, "name");
        projectName.setText(name.isBlank() ? (fr ? "Architecture du projet" : "Project architecture") : name);
        summary.setText(entries.size() + (fr ? " composants  ·  " : " components  ·  ")
            + pendingTotal + (fr ? " en attente  ·  " : " pending  ·  ")
            + resolvedTotal + (fr ? " traités" : " resolved"));
        if (entries.isEmpty()) {
            notice.setText(fr
                ? "Aucun composant déclaré dans la spécification. Ouvre Composants ou crée-en un."
                : "No components declared in the specification. Open Components or create one.");
        } else {
            notice.setText(fr ? "Double-clique sur un composant pour l'ouvrir."
                : "Double-click a component to open it.");
        }
        fitColumns();
        revalidate();
        repaint();
    }

    /**
     * The widths for {@code available} pixels, from each column's natural width (its header and its
     * longest text): Component, State and Pending always at theirs; Type, then Layer, then Container
     * while they fit at theirs (a hidden one gets 0: the row's tooltip still says it); what is left goes
     * to the columns shown. Narrower than the three: Component narrows (to {@link #COMPONENT_MIN}), then the
     * table scrolls sideways.
     */
    static int[] columnWidths(int available, int[] natural) {
        int[] widths = new int[natural.length];
        widths[COMPONENT] = natural[COMPONENT];
        widths[STATE] = natural[STATE];
        widths[PENDING] = natural[PENDING];
        int used = widths[COMPONENT] + widths[STATE] + widths[PENDING];
        if (used > available) {
            // too narrow for the three: Component gives way (its names cut with "…", whole in the tooltip)
            // down to COMPONENT_MIN, so State and Pending stay in view; under that, the table scrolls
            widths[COMPONENT] = Math.max(Math.min(natural[COMPONENT], COMPONENT_MIN), available - widths[STATE] - widths[PENDING]);
            return widths;
        }
        List<Integer> shown = new ArrayList<>(List.of(COMPONENT));
        for (int column : OPTIONAL) {
            if (used + natural[column] > available) break;
            widths[column] = natural[column];
            used += natural[column];
            shown.add(column);
        }
        int extra = Math.max(0, available - used);
        for (int i = 0; i < shown.size(); i++) widths[shown.get(i)] += extra / shown.size() + (i == 0 ? extra % shown.size() : 0);
        return widths;
    }

    /** Applies {@link #columnWidths} to the table for the width of its viewport. */
    private void fitColumns() {
        TableColumnModel columns = table.getColumnModel();
        if (columns.getColumnCount() != 6) return;
        java.awt.FontMetrics body = table.getFontMetrics(table.getFont());
        java.awt.FontMetrics head = table.getTableHeader() == null ? body
            : table.getTableHeader().getFontMetrics(table.getTableHeader().getFont());
        int[] natural = new int[6];
        int[] cap = {260, 170, 150, 170, 170, 110};
        for (int c = 0; c < 6; c++) {
            int width = head.stringWidth(model.getColumnName(c));
            for (int r = 0; r < model.getRowCount(); r++) width = Math.max(width, body.stringWidth(String.valueOf(model.getValueAt(r, c))));
            natural[c] = Math.min(cap[c], width + 22);
        }
        int available = scroll.getViewport().getWidth();
        if (available <= 0) available = 640;
        int[] widths = columnWidths(available, natural);
        int total = 0;
        for (int c = 0; c < 6; c++) {
            javax.swing.table.TableColumn column = columns.getColumn(c);
            column.setMinWidth(0);
            column.setMaxWidth(widths[c] == 0 ? 0 : Integer.MAX_VALUE);
            column.setPreferredWidth(widths[c]);
            column.setWidth(widths[c]);
            total += widths[c];
        }
        table.setPreferredScrollableViewportSize(new java.awt.Dimension(total, table.getPreferredScrollableViewportSize().height));
        table.setPreferredSize(null);
        table.revalidate();
    }

    int componentCount() { return model.getRowCount(); }
    String componentAt(int index) { return model.rows.get(index).id(); }
    String stateAt(int index) { return model.rows.get(index).state(); }
    int pendingAt(int index) { return model.rows.get(index).pending(); }
    JTable inventoryTable() { return table; }

    /** Human-readable status. A drift is a detected divergence, not a completed state. */
    static String localizedState(String raw, boolean fr) {
        if (raw == null || raw.isBlank()) return fr ? "Non évalué" : "Not evaluated";
        String key = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (fr) return switch (key) {
            case "implemented" -> "Implémenté";
            case "verified" -> "Vérifié";
            case "tested" -> "Testé";
            case "drift", "drifted" -> "Écart détecté";
            case "planned" -> "Prévu";
            case "partial" -> "Partiel";
            case "missing" -> "Manquant";
            case "pending" -> "En attente";
            case "blocked" -> "Bloqué";
            case "failed", "error" -> "Échec";
            default -> raw;
        };
        return switch (key) {
            case "drifted" -> "Drift detected";
            default -> raw;
        };
    }

    private static final class InventoryTableModel extends AbstractTableModel {
        final List<Row> rows = new ArrayList<>();
        private boolean fr;
        void setLanguage(boolean french) { fr = french; fireTableStructureChanged(); }
        void setRows(List<Row> values) { rows.clear(); rows.addAll(values); fireTableDataChanged(); }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return 6; }
        @Override public String getColumnName(int column) {
            return fr
                ? new String[] {"Composant", "Type", "Couche", "Conteneur", "État", "En attente"}[column]
                : new String[] {"Component", "Type", "Layer", "Container", "State", "Pending"}[column];
        }
        @Override public Object getValueAt(int index, int column) {
            Row row = rows.get(index);
            return switch (column) {
                case 0 -> row.id();
                case 1 -> row.type();
                case 2 -> row.layer();
                case 3 -> row.container();
                case 4 -> row.state();
                default -> row.pending() == 0 ? "—" : Integer.toString(row.pending());
            };
        }
        @Override public boolean isCellEditable(int row, int column) { return false; }
    }

    private final class StatusRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable list, Object value,
                    boolean selected, boolean focused, int row, int column) {
            super.getTableCellRendererComponent(list, value, selected, focused, row, column);
            Color base = UIManager.getColor("Table.background");
            if (base == null) base = list.getBackground();
            if (!selected) {
                setBackground(row % 2 == 0 ? base : mix(base, list.getForeground(), .035));
                setForeground(list.getForeground());
                String text = value == null ? "" : value.toString().toLowerCase(java.util.Locale.ROOT);
                boolean good = column == 4 && (text.contains("verified") || text.contains("implemented") || text.contains("tested")
                    || text.contains("vérifi") || text.contains("implément") || text.contains("testé"));
                boolean pending = (column == 5 && !"—".equals(text)) ||
                    (column == 4 && (text.contains("missing") || text.contains("manquant") || text.contains("drift") || text.contains("écart") || text.contains("blocked") || text.contains("bloqu")));
                if (good) setForeground(new Color(37, 145, 100));
                if (pending) setForeground(new Color(203, 136, 44));
            }
            String state = model.rows.get(list.convertRowIndexToModel(row)).state();
            if (column == 4 && (state.equalsIgnoreCase("drift") || state.equalsIgnoreCase("drifted") || state.equals("Écart détecté"))) {
                setToolTipText(french ? "Écart entre le code et les engagements de la spécification." :
                    "The implementation no longer matches the component specification.");
                if (!selected) setForeground(french ? new Color(182, 103, 32) : new Color(203, 136, 44));
            } else {
                // the whole row, also what a hidden column holds (a narrow tool window)
                Row r = model.rows.get(list.convertRowIndexToModel(row));
                setToolTipText(String.join(" · ", java.util.stream.Stream.of(r.id(), r.type(), r.layer(), r.container(), r.state())
                    .filter(x -> x != null && !x.isBlank()).toList()));
            }
            setHorizontalAlignment(column == 5 ? SwingConstants.CENTER : SwingConstants.LEFT);
            setBorder(BorderFactory.createEmptyBorder(1, 8, 1, 8));
            return this;
        }
        private Color mix(Color a, Color b, double p) {
            return new Color((int)(a.getRed() * (1 - p) + b.getRed() * p),
                (int)(a.getGreen() * (1 - p) + b.getGreen() * p),
                (int)(a.getBlue() * (1 - p) + b.getBlue() * p));
        }
    }
}
