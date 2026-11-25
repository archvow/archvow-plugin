package org.archvow.idea;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 1.2.1, headless: the views drawn from real contract data at the widths a tool window and an editor tab have
 * (320 to 1280 px), checked for what a person saw at some width - a button with no label (Tasks), fields a few
 * pixels wide (the answer table), a part past the edge, a label cut, a bar drawn as a grey block. With
 * ARCHVOW_SHOTS=<folder>, a picture of each.
 */
public final class ResponsiveViewsRegression {
    private static final int[] WIDTHS = {320, 480, 760, 1280};
    private static final List<String> problems = new ArrayList<>();

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static Path contract() {
        for (Path p = Path.of("").toAbsolutePath(); p != null; p = p.getParent())
            if (Files.isDirectory(p.resolve("contract/cli"))) return p.resolve("contract");
        throw new AssertionError("contract/ not found");
    }

    private static JsonObject show(String example, String component) throws Exception {
        Path file = contract().resolve("cli/" + example + "/show/" + component + ".json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void invalidateTree(Container parent) {
        parent.invalidate();
        for (Component child : parent.getComponents()) if (child instanceof Container nested) invalidateTree(nested);
    }

    private static void layoutTree(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents())
            if (child instanceof Container nested) layoutTree(nested);
    }

    /** Lays the view out at a width (twice: a wrapping row knows its height once its width is set). */
    private static void at(JComponent view, int width, int height) {
        view.setSize(width, height);
        for (int i = 0; i < 3; i++) { invalidateTree(view); view.validate(); layoutTree(view); }
    }

    /** In a vertical scroll (the skill form, the answer table): as tall as it asks at that width. */
    private static void grown(JComponent view, int width) {
        at(view, width, 400);
        for (int i = 0; i < 3; i++) at(view, width, Math.max(200, view.getPreferredSize().height));
    }

    private static boolean scrolls(Component c) {
        return SwingUtilities.getAncestorOfClass(JViewport.class, c) != null
            && SwingUtilities.getAncestorOfClass(JViewport.class, c) != c;
    }

    /** What is wrong at this width, if anything. */
    private static void probe(String name, Container root, Container at) {
        for (Component c : at.getComponents()) {
            if (!c.isVisible()) continue;
            Rectangle r = c.getBounds();
            String what = name + ": " + c.getClass().getSimpleName() + (c.getName() == null ? "" : " " + c.getName());
            if (c instanceof JButton b && !c.getClass().getName().startsWith("javax.swing.plaf") && b.getText().isBlank() && b.getIcon() == null && r.width > 0)
                problems.add(what + " is a button with no label");
            if (c instanceof JTextField f && f.isShowing() == f.isShowing() && r.width > 0 && r.width < 48 && !scrolls(c))
                problems.add(what + " is " + r.width + " px wide");
            if (!(at instanceof JViewport) && r.width > 0 && r.x + r.width > at.getWidth() + 2 && at.getWidth() > 0 && !(at instanceof JLayeredPane))
                problems.add(what + " goes " + (r.x + r.width - at.getWidth()) + " px past its parent " + at.getClass().getSimpleName());
            if (!(at instanceof JViewport) && r.height > 0 && r.y + r.height > at.getHeight() + 2 && at.getHeight() > 0 && !(at instanceof JLayeredPane)
                && at != root && !scrolls(c))
                problems.add(what + " goes " + (r.y + r.height - at.getHeight()) + " px below its parent " + at.getClass().getSimpleName());
            if (c instanceof JLabel l && l.getText() != null && !l.getText().isBlank() && !l.getText().startsWith("<html")
                && r.width > 0 && l.getPreferredSize().width > r.width + 2 && !scrolls(c))
                problems.add(what + " \"" + l.getText() + "\" is cut (" + r.width + " of " + l.getPreferredSize().width + " px)");
            if (c instanceof Container nested) probe(name, root, nested);
        }
    }

    private static void shot(JComponent view, String name) throws Exception {
        String folder = System.getenv("ARCHVOW_SHOTS");
        if (folder == null) return;
        BufferedImage image = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(UIManager.getColor("Panel.background") == null ? Color.DARK_GRAY : UIManager.getColor("Panel.background"));
        g.fillRect(0, 0, view.getWidth(), view.getHeight());
        view.printAll(g);
        g.dispose();
        new File(folder).mkdirs();
        ImageIO.write(image, "png", new File(folder, "ij-" + name + ".png"));
    }

    private static void everyWidth(String name, JComponent view, int height) throws Exception {
        for (int width : WIDTHS) {
            at(view, width, height);
            probe(name + " @" + width, view, view);
            shot(view, name + "-" + width);
        }
    }

    public static void main(String[] args) throws Exception {
        UIManager.put("Panel.background", new Color(43, 45, 48));
        UIManager.put("Label.foreground", new Color(223, 225, 229));
        SwingUtilities.invokeAndWait(() -> {
            try { run(); } catch (Exception e) { throw new RuntimeException(e); }
        });
        if (!problems.isEmpty()) throw new AssertionError(problems.size() + " layout problems:\n  " + String.join("\n  ", problems));
        System.out.println("OK: Tasks, the answer table, the skill form, Examples, Search, Checks, the Overview cards and the progress bar at 240 to 1280 px");
    }

    private static void run() throws Exception {
        // Tasks: a component with no task (the sentence alone), one with tasks to build, one all done
        ArchvowTasksView tasks = new ArchvowTasksView(new ArchvowTasksView.Actions() {
            @Override public void act(ArchvowDataView.Item item) { }
            @Override public void openCode() { }
            @Override public void editDescription() { }
        });
        tasks.show("config", show("pyspark-pipeline", "config"));
        check(tasks.headingText().equals("No separate tasks: the AI builds config in one go, from its spec."), "no task: " + tasks.headingText());
        check(!tasks.showsItems(), "no task: no list, no detail pane, no button");
        everyWidth("tasks-none", tasks, 520);
        tasks.show("clean_orders", show("pyspark-pipeline", "clean_orders"));
        check(tasks.headingText().startsWith("1 task to build") || tasks.headingText().contains("tasks to build"), "tasks to build: " + tasks.headingText());
        check(tasks.showsItems() && tasks.selected() != null, "the first task is selected");
        everyWidth("tasks-build", tasks, 620);
        tasks.show("create_order", show("fastapi-service", "create_order"));
        check(tasks.headingText().equals("Nothing to build: every task is done."), "all done: " + tasks.headingText());
        check(ArchvowDataView.flows(show("fastapi-service", "create_order"), false).equals(List.of("Handles: customer (personal)")),
            "personal data: only what it holds " + ArchvowDataView.flows(show("fastapi-service", "create_order"), false));
        check(ArchvowDataView.flows(show("pyspark-pipeline", "config"), false).isEmpty(), "no personal data: no empty lines");
        everyWidth("tasks-done", tasks, 620);

        // the answer table: parameters (config 1) and columns, stacked when narrow
        JsonObject config = show("pyspark-pipeline", "config");
        JTextArea answer = new JTextArea();
        ArchvowColumnAnswerPanel table = new ArchvowColumnAnswerPanel(answer);
        table.showFor(config, ArchvowGrammar.objects(config, "decisions").get(0), false);
        check(table.isVisible(), "the parameter table shows for config's decision 1");
        for (int width : WIDTHS) {
            at(table, width, 420);
            table.fitWidth();
            grown(table, width);
            check(table.stackedForTest() == (width < ArchvowColumnAnswerPanel.STACK_BELOW), "stacked below " + ArchvowColumnAnswerPanel.STACK_BELOW + " px: " + width);
            probe("parameters @" + width, table, table);
            shot(table, "parameters-" + width);
        }
        JsonObject schema = show("pyspark-pipeline", "orders_schema");
        for (JsonObject d : ArchvowGrammar.objects(schema, "decisions"))
            if (ArchvowColumnAnswerPanel.modeFor(ArchvowGrammar.str(d, "question")) == ArchvowColumnAnswerPanel.Mode.COLUMNS) {
                ArchvowColumnAnswerPanel columns = new ArchvowColumnAnswerPanel(new JTextArea());
                columns.showFor(schema, d, false);
                for (int width : WIDTHS) {
                    at(columns, width, 420);
                    columns.fitWidth();
                    grown(columns, width);
                    probe("columns @" + width, columns, columns);
                    shot(columns, "columns-" + width);
                }
                break;
            }

        // the skill form: its steps, the field and "What will happen"
        JsonObject skills = JsonParser.parseString(Files.readString(contract().resolve("forms/forms.json")))
            .getAsJsonObject().getAsJsonObject("ai_skills");
        ArchvowSkillForm form = new ArchvowSkillForm(skills, "clarify", Map.of("component", "config"), List.of("config", "spark"),
            List.of(), List.of("claude-code", "copilot"), c -> null, false, (text, values) -> { }, () -> { });
        for (int width : WIDTHS) {
            grown(form, width);
            probe("skill @" + width, form, form);
            shot(form, "skill-" + width);
        }

        // the Examples view (python-app's failing example), the Search panel, the Checks list
        Path examplesDir = contract().resolve("cli/python-app/examples");
        try (var files = Files.list(examplesDir)) {
            for (Path file : files.sorted().limit(1).toList()) {
                ArchvowExamplesView examples = new ArchvowExamplesView(new ArchvowExamplesView.Actions() {
                    @Override public void openFile(String relative, int line) { }
                    @Override public void openBrief(String ref) { }
                    @Override public void copy(String text) { }
                    @Override public void record(String component) { }
                });
                examples.show(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject(), false);
                everyWidth("examples", examples, 620);
            }
        }
        try (var files = Files.list(contract().resolve("cli/python-app/search"))) {
            for (Path file : files.sorted().limit(1).toList()) {
                ArchvowSearchView search = new ArchvowSearchView(new ArchvowSearchView.Actions() {
                    @Override public void openFile(String relative, int line) { }
                    @Override public void showComponent(String id) { }
                    @Override public void openRule(String code) { }
                    @Override public void search(String name) { }
                });
                search.show(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject(),
                    file.getFileName().toString().replace(".json", ""), contract().resolve("archvow/examples/python-app").toString(), false);
                everyWidth("search", search, 620);
            }
        }
        ArchvowChecksView checks = new ArchvowChecksView();
        checks.showData(JsonParser.parseString(Files.readString(contract().resolve("cli/fastapi-service/check.json"), StandardCharsets.UTF_8)).getAsJsonObject(), "", false);
        everyWidth("checks", checks, 620);

        // the Overview's cards, in a tool window 240 to 420 px wide
        JsonObject spec = JsonParser.parseString(Files.readString(contract().resolve("cli/pyspark-pipeline/workspace.json"))).getAsJsonObject();
        ArchvowOverview.FirstSteps steps = ArchvowOverview.firstSteps(spec.getAsJsonArray("first_steps"));
        ArchvowOverviewCards.Actions none = new ArchvowOverviewCards.Actions() {
            @Override public void openComponents() { }
            @Override public void openItems(int list) { }
            @Override public void openProjectItems() { }
            @Override public void openChecks() { }
            @Override public void openFile(String path, int line) { }
            @Override public void step(ArchvowOverview.FirstStep step) { }
            @Override public void tryExample() { }
        };
        for (int width : new int[] {240, 320, 420}) {
            JPanel first = ArchvowOverviewCards.firstSteps(steps, false, none);
            grown(first, width);
            probe("first steps @" + width, first, first);
            shot(first, "first-steps-" + width);
            JPanel arch = ArchvowOverviewCards.architecture(11, 45, 25, 2, false, false, none);
            JPanel checksCard = ArchvowOverviewCards.checks(10, 20, null, false, none);
            grown(checksCard, width);
            probe("checks card @" + width, checksCard, checksCard);
            shot(checksCard, "checks-card-" + width);
            grown(arch, width);
            probe("architecture @" + width, arch, arch);
            shot(arch, "architecture-" + width);
        }

        // the progress bar: thin and as wide as the pane, not a 120 px grey block
        JEditorPane header = new JEditorPane("text/html", "<html><body>" + ArchvowProgress.html(config, false, true) + "</body></html>");
        for (int width : new int[] {320, 900}) {
            header.setSize(width, 200);
            Dimension size = header.getPreferredSize();
            header.setSize(width, size.height);
            BufferedImage image = new BufferedImage(width, size.height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            header.paint(g);
            g.dispose();
            int rows = 0, wide = 0, first = -1;
            for (int y = 0; y < size.height; y++) {
                int rgb = image.getRGB(width / 4, y) & 0xffffff;
                if (rgb == 0x3C3F41 || rgb == 0x5FB865 || rgb == 0xE0A33A) { rows++; if (first < 0) first = y; }
            }
            if (first >= 0) for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, first) & 0xffffff;
                if (rgb == 0x3C3F41 || rgb == 0x5FB865 || rgb == 0xE0A33A) wide++;
            }
            check(rows > 0 && rows <= 8, "the bar is a thin line at " + width + " px: " + rows + " px tall");
            check(wide > width * 0.8, "the bar spans the pane at " + width + " px: " + wide + " px");
        }
    }
}
