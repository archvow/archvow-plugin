package org.archvow.idea;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 0.15.0, the same as VS Code (vscode/src/test/filePlan.test.ts): the files a form shows it will write, on every
 * example of ../contract/forms/file-plans.expected.json, in English and in French; the Components tab's types per
 * layer; Save to a file (archvow changes ... --markdown > CHANGES.md); the trees' icons; the first commit offered
 * to changes --from; and the answer tables (a bin per row, only what the question asks, parameters).
 */
public final class FilePlanRegression {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static JsonObject read(Path file) throws Exception {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static Map<String, String> values(JsonObject o) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) out.put(e.getKey(), e.getValue().getAsString());
        return out;
    }

    public static void main(String[] args) throws Exception {
        Path contract = Path.of(System.getProperty("archvow.contract.root", "../contract"));
        JsonObject expected = read(contract.resolve("forms/file-plans.expected.json"));
        JsonObject catalog = read(contract.resolve("cli/catalog.json"));
        int plans = 0;
        for (JsonElement e : expected.getAsJsonArray("plans")) {
            JsonObject want = e.getAsJsonObject();
            String command = want.get("command").getAsString();
            boolean fr = want.get("fr").getAsBoolean();
            JsonObject spec = read(contract.resolve("cli/" + want.get("example").getAsString() + "/show.json"));
            List<ArchvowFilePlan.Row> got = ArchvowFilePlan.plan(command, values(want.getAsJsonObject("values")), spec, catalog, ArchvowForms.DATA, fr, false);
            var rows = want.getAsJsonArray("rows");
            String where = command + " " + want.getAsJsonObject("values") + (fr ? " fr" : "");
            check(rows.size() == got.size(), where + ": " + got.size() + " rows, VS Code " + rows.size() + " " + got);
            for (int i = 0; i < rows.size(); i++) {
                JsonObject r = rows.get(i).getAsJsonObject();
                ArchvowFilePlan.Row g = got.get(i);
                check(r.get("change").getAsString().equals(g.change()) && r.get("path").getAsString().equals(g.path())
                    && r.get("note").getAsString().equals(g.note()), where + " row " + i + ": " + g + " vs " + r);
            }
            plans++;
        }
        check(plans >= 40, "every example, in English and in French");
        for (JsonElement e : expected.getAsJsonArray("addable")) {
            JsonObject want = e.getAsJsonObject();
            JsonObject spec = read(contract.resolve("cli/" + want.get("example").getAsString() + "/show.json"));
            Map<String, List<ArchvowFilePlan.Addable>> got = ArchvowFilePlan.addableByLayer(spec, catalog);
            JsonObject layers = want.getAsJsonObject("layers");
            check(List.copyOf(layers.keySet()).equals(List.copyOf(got.keySet())), "the layers, in the template's order: " + got.keySet());
            for (String layer : layers.keySet()) {
                var types = layers.getAsJsonArray(layer);
                check(types.size() == got.get(layer).size(), layer + ": " + got.get(layer));
                for (int i = 0; i < types.size(); i++)
                    check(types.get(i).getAsJsonObject().get("type").getAsString().equals(got.get(layer).get(i).type()), layer + " type " + i);
            }
        }
        // Save to a file: the plugin's own field, after As Markdown, never on archvow's line
        ArchvowGrammar.Command changes = ArchvowGrammar.find("changes");
        int at = -1;
        for (int i = 0; i < changes.parameters.size(); i++) if ("output".equals(changes.parameters.get(i).name)) at = i;
        check(at > 0 && changes.parameters.get(at).plugin && changes.parameters.get(at - 1).name.equals("markdown"), "output after markdown");
        for (JsonElement e : expected.getAsJsonArray("outputs")) {
            JsonObject want = e.getAsJsonObject();
            ArchvowGrammar.Command command = ArchvowGrammar.find(want.get("command").getAsString());
            Map<String, String> v = values(want.getAsJsonObject("values"));
            check(want.get("line").getAsString().equals(ArchvowForms.commandLine(command, v, false)),
                "the line: " + ArchvowForms.commandLine(command, v, false) + " vs " + want.get("line"));
            check(want.get("output").getAsString().equals(ArchvowForms.output(command, v)), "the output file");
        }
        // the first commit, offered first to changes --from (as choices.ts)
        var book = ArchvowChoices.book(ArchvowGrammar.COMMANDS, new ArchvowChoices.Input(read(contract.resolve("cli/pyspark-pipeline/show.json")),
            Map.of(), catalog, null, List.of("main", "v1.0.0"), "main", false, "a1b2c3d"));
        var start = book.get("changes").get("start");
        check(start.choices.get(0).value().equals("a1b2c3d") && start.choices.get(0).label().contains("the first commit"), "first commit first");
        check(start.choices.size() == 3, "then the refs");
        // the trees' icons: a drawing for every type, group and command
        for (JsonElement t : catalog.getAsJsonArray("templates"))
            for (String type : t.getAsJsonObject().getAsJsonObject("component_types").keySet()) check(ArchvowTreeIcons.has(type), "no icon for " + type);
        check(ArchvowTreeIcons.has("folder") && ArchvowTreeIcons.has("document"), "file preview uses VS Code's folder/document drawings");
        for (ArchvowForms.Group g : ArchvowForms.commandGroups(false)) check(ArchvowTreeIcons.has(ArchvowTreeIcons.groupKind(g.id())), "group " + g.id());
        for (ArchvowGrammar.Command c : ArchvowGrammar.COMMANDS) check(ArchvowTreeIcons.has(ArchvowTreeIcons.commandKind(c.path)), "command " + c.label);
        check("component".equals(ArchvowTreeIcons.typeKind("a_company_type")), "an unknown type: a page");
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (String kind : ArchvowTreeIcons.DATA.getAsJsonObject("shapes").keySet()) {
            java.awt.Graphics2D g = image.createGraphics();
            ArchvowTreeIcons.icon(kind, java.awt.Color.BLACK).paintIcon(null, g, 0, 0);  // every path parses and paints
            g.dispose();
        }
        int ink = 0;
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) if ((image.getRGB(x, y) >>> 24) > 0) ink++;
        check(ink > 20, "the icons draw something");
        check(ArchvowFilePlan.plan("component add", Map.of("type", "reader", "name", "orders", "description", "x"),
            read(contract.resolve("cli/pyspark-pipeline/show.json")), catalog, ArchvowForms.DATA, false, false)
            .stream().anyMatch(r -> r.path().contains("orders_reader.py")), "the Files block");
        SwingUtilities.invokeAndWait(() -> { filesView(); tables(); });
        System.out.println("OK: " + plans + " file plans identical to VS Code's, layers to add to, Save to a file, the first commit, icons, answer tables");
    }

    /** Icons, grouping and theme-independent wording in the editor tab's native Files preview. */
    private static void filesView() {
        ArchvowFilePlanView view = new ArchvowFilePlanView();
        check(!view.isVisible(), "empty Files preview is hidden");
        view.showRows(List.of(
            new ArchvowFilePlan.Row("updated", ".archvow/components/orders_reader/spec.vow", "what was recorded before becomes to review"),
            new ArchvowFilePlan.Row("updated", ".archvow/components/orders_reader/spec.md", ""),
            new ArchvowFilePlan.Row("new", ".archvow/", ""),
            new ArchvowFilePlan.Row("planned", "src/sales/readers/orders_reader.py", "written later")
        ), true);
        check(view.isVisible(), "Files preview shown when files are present");
        java.util.List<JLabel> labels = new java.util.ArrayList<>();
        collectLabels(view, labels);
        long folders = labels.stream().filter(l -> iconKind(l).equals("folder")).count();
        long documents = labels.stream().filter(l -> iconKind(l).equals("document")).count();
        check(folders == 3, "only real folder groups and directory rows, no synthetic ./ group: " + folders);
        check(documents == 3, "a document icon for each file row: " + documents);
        check(labels.stream().filter(l -> l.getText().equals(".archvow/components/orders_reader/")).count() == 1,
            "shared folder is shown once");
        check(labels.stream().anyMatch(l -> l.getText().equals("modifié")), "status localised in French");
        check(labels.stream().anyMatch(l -> l.getText().equals("orders_reader.py")), "planned files included");
        JLabel filename = labels.stream().filter(l -> l.getText().equals("spec.vow")).findFirst()
            .orElseThrow(() -> new AssertionError("Filename badge missing"));
        check(filename.getIcon() == null && filename.getBorder() != null
            && filename.getFont().getFamily().equals(java.awt.Font.MONOSPACED),
            "Filename is a separate code badge, not its icon or description");
        filename.setSize(filename.getPreferredSize());
        java.awt.image.BufferedImage preview = new java.awt.image.BufferedImage(
            filename.getWidth(), filename.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = preview.createGraphics();
        filename.paint(graphics);
        graphics.dispose();
        check((preview.getRGB(1, filename.getHeight() / 2) >>> 24) != 0,
            "Filename badge paints a visible background");
        // Descriptions are wrapping text areas (ArchvowWrapLabel), not JLabels.
        ArchvowWrapLabel note = java.util.Arrays.stream(view.getComponents())
            .filter(ArchvowWrapLabel.class::isInstance)
            .map(ArchvowWrapLabel.class::cast)
            .filter(l -> l.getText().contains("what was recorded before becomes to review"))
            .findFirst().orElseThrow(() -> new AssertionError("Description note missing"));
        check(note.getFont().getSize2D() == 10.5f,
            "Description note uses hint-sized text: " + note.getFont().getSize2D());
        // init: .archvow/ has indented children, while AGENTS.md and other root files
        // have no synthetic "./" group and align with the .archvow/ folder itself.
        view.showRows(List.of(
            new ArchvowFilePlan.Row("new", ".archvow/spec.vow", "specification"),
            new ArchvowFilePlan.Row("new", ".archvow/spec.md", "overview"),
            new ArchvowFilePlan.Row("new", "AGENTS.md", "AI rules"),
            new ArchvowFilePlan.Row("new", "README.md", "project guide")
        ), false);
        labels.clear();
        collectLabels(view, labels);
        check(labels.stream().noneMatch(l -> "./".equals(l.getText())), "root folder ./ must be hidden");
        check(labels.stream().filter(l -> ".archvow/".equals(l.getText())).count() == 1,
            "the real .archvow/ folder must appear exactly once");
        java.util.Map<String, Integer> leftInsets = new java.util.HashMap<>();
        for (java.awt.Component child : view.getComponents()) {
            if (!(child instanceof JPanel row)) continue;
            for (java.awt.Component part : row.getComponents()) {
                if (part instanceof JLabel label && List.of("spec.vow", "AGENTS.md", "README.md").contains(label.getText()))
                    leftInsets.put(label.getText(), row.getBorder().getBorderInsets(row).left);
            }
        }
        check(Integer.valueOf(14).equals(leftInsets.get("spec.vow")), "nested files are indented");
        check(Integer.valueOf(0).equals(leftInsets.get("AGENTS.md"))
            && Integer.valueOf(0).equals(leftInsets.get("README.md")),
            "root files must align with .archvow/ (no nested indentation): " + leftInsets);
        view.showRows(List.of(), false);
        check(!view.isVisible() && view.getComponentCount() == 0, "Files preview clears when the form has no files");
    }

    private static String iconKind(JLabel label) {
        return label.getIcon() instanceof ArchvowTreeIcons.ShapeIcon icon ? icon.kind : "";
    }

    private static void collectLabels(java.awt.Container panel, java.util.List<JLabel> into) {
        for (java.awt.Component c : panel.getComponents()) {
            if (c instanceof JLabel label) into.add(label);
            if (c instanceof java.awt.Container child) collectLabels(child, into);
        }
    }

    private static void tables() {
        JTextArea target = new JTextArea();
        ArchvowColumnAnswerPanel panel = new ArchvowColumnAnswerPanel(target);
        JsonObject view = JsonParser.parseString("{\"known_columns\":[{\"columns\":[\"order_id\",\"amount\",\"szsz\"]}],\"columns\":[]}").getAsJsonObject();
        panel.showFor(view, JsonParser.parseString("{\"question\":\"Which columns hold personal data (name, email...), and which outputs may receive them?\"}").getAsJsonObject(), false);
        check(panel.mode() == ArchvowColumnAnswerPanel.Mode.COLUMNS && panel.rowCount() == 3, "a row per known column");
        check(!panel.headers().contains("Nullable?") && panel.headers().contains("Personal data?"), "only what the question asks: " + panel.headers());
        check(panel.bins().size() == 3, "a bin per row (the screenshot: IntelliJ had none)");
        panel.bins().get(2).doClick();
        check(panel.rowCount() == 2 && !panel.answer().contains("szsz"), "the bin removes its row");
        check(panel.answer().equals("order_id: personal-data classification unknown\namount: personal-data classification unknown"), panel.answer());
        // the parameters of the config: name: type = default, no default = required
        panel.showFor(new JsonObject(), JsonParser.parseString("{\"question\":\"The job's parameters, name: type = default separated by commas (no default = required) - e.g. input_path: str, run_date: date, output_format: str = \\\"parquet\\\" - or none?\","
            + "\"answer\":\"input_path: str (required), run_date: date, output_format: str = \\\"parquet\\\" (the format)\"}").getAsJsonObject(), false);
        check(panel.mode() == ArchvowColumnAnswerPanel.Mode.PARAMETERS && panel.rowCount() == 3, "a row per parameter");
        check(panel.answer().equals("input_path: str, run_date: date, output_format: str = \"parquet\" (the format)"), panel.answer());
        check(ArchvowColumnAnswerPanel.parseParameters("input_path: str and output_path: str, both required") == null, "a sentence is not a list");
        check(ArchvowColumnAnswerPanel.parseParameters("none").isEmpty(), "none");
        panel.bins().get(0).doClick();
        check(panel.answer().startsWith("run_date: date"), "the bin removes a parameter");
        check(target.getText().isEmpty(), "nothing is written until Use this answer");
    }
}
