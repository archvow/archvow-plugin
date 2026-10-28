package org.archvow.idea;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** The component's progress bar (0.12.0) on the contract's recorded show JSON; the same as VS Code's progress(). */
public final class ProgressRegression {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static String said(List<ArchvowProgress.Part> parts) {
        StringBuilder out = new StringBuilder();
        for (ArchvowProgress.Part p : parts) out.append(p.key()).append(' ').append(p.done()).append('/').append(p.total()).append(p.complete() ? "+" : "-").append(';');
        return out.toString();
    }

    public static void main(String[] args) throws Exception {
        Path contract = Path.of(System.getProperty("archvow.contract", "../contract/cli"));
        Path file = contract.resolve("pyspark-pipeline/show/clean_orders.json");
        if (Files.isRegularFile(file)) {
            JsonObject view = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            List<ArchvowProgress.Part> parts = ArchvowProgress.parts(view, false);
            check(said(parts).equals("decisions 9/9+;safety 1/5-;tasks 0/2-;"), "clean_orders: " + said(parts));
            check(ArchvowProgress.tooltip(parts).equals("Decisions made: 9/9 · Safety answered: 1/5 · Tasks done: 0/2"), "tooltip");
            check(ArchvowProgress.tooltip(ArchvowProgress.parts(view, true)).startsWith("Décisions prises: 9/9 · Sûreté traitée: 1/5"), "tooltip FR");
            String html = ArchvowProgress.html(view, false, false);
            check(html.contains("data-part='decisions'") && html.contains("width='100%' height='6' bgcolor='#388A34'"), "decisions complete green");
            check(html.contains("width='20%' height='6' bgcolor='#D18616'"), "safety 1/5 amber at 20 %");
        } else {
            System.out.println("(contract not found from " + Path.of("").toAbsolutePath() + ": recorded component not read)");
        }
        JsonObject made = JsonParser.parseString("{\"decisions\":[{\"answer\":null},{\"answer\":\"yes\"}],\"safety\":[],"
            + "\"examples\":[{\"name\":\"a\",\"tested\":true,\"unchanged\":true},{\"name\":\"b\",\"tested\":true,\"unchanged\":false},"
            + "{\"name\":\"c\",\"tested\":false}]}").getAsJsonObject();
        check(said(ArchvowProgress.parts(made, false)).equals("decisions 1/2-;examples 1/3-;"), "examples counted, empty parts left out");
        check(new ArchvowProgress.Part("tasks", "Tasks done", 19, 20).fill() == 90, "nearly done shown full");
        check(new ArchvowProgress.Part("tasks", "Tasks done", 1, 50).fill() == 10, "started shown empty");
        check(ArchvowProgress.html(new JsonObject(), false, false).isEmpty(), "a bar with nothing to count");
        System.out.println("OK: progress bar (decisions, safety, tasks, examples)");
    }
}
