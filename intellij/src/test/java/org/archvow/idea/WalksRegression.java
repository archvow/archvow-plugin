package org.archvow.idea;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 1.1.0: the walks - each feature's Copy AI prompt, the Specify walks (the foundations, each feature, from each
 * component), the Build walks - are VS Code's on every case of ../contract/walks/expected.json (written by
 * UPDATE_WALKS=1 npm test): the same examples, the same modes and variants, the same answers. Then the strip itself,
 * headless: its words and buttons.
 */
public final class WalksRegression {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static JsonObject workspace(Path contract, String example, String mode, String variant) throws Exception {
        JsonObject w = JsonParser.parseString(Files.readString(contract.resolve("cli").resolve(example).resolve("workspace.json"))).getAsJsonObject();
        if (!mode.isEmpty()) w.getAsJsonObject("project").addProperty("mode", mode);
        JsonArray rows = w.getAsJsonArray("next");
        if ("blocked".equals(variant))
            for (JsonElement r : rows)
                if (!"shared".equals(ArchvowWalks.featureOf(w, r.getAsJsonObject().get("component").getAsString())))
                    r.getAsJsonObject().addProperty("readiness", "blocked: 1 decision to make");
        if ("budget".equals(variant) && !rows.isEmpty())
            rows.get(rows.size() - 1).getAsJsonObject().addProperty("readiness", "next merge request (change budget reached)");
        return w;
    }

    private static JsonArray steps(List<ArchvowWalks.Step> steps) {
        JsonArray out = new JsonArray();
        for (ArchvowWalks.Step s : steps) {
            JsonObject o = new JsonObject();
            o.addProperty("component", s.component());
            o.addProperty("open", s.open());
            out.add(o);
        }
        return out;
    }

    private static JsonArray strings(List<String> ids) {
        JsonArray out = new JsonArray();
        for (String id : ids) out.add(new JsonPrimitive(id));
        return out;
    }

    private static JsonObject walks(JsonObject w) {
        List<String> features = new ArrayList<>(w.getAsJsonObject("features").keySet());
        features.sort(null);
        List<String> components = new ArrayList<>();
        for (String f : features) components.addAll(ArchvowWalks.membersOf(w, f));
        JsonObject prompt = new JsonObject(), byFeature = new JsonObject(), byComponent = new JsonObject(), build = new JsonObject(), featureOf = new JsonObject();
        for (String f : features) {
            ArchvowWalks.PromptState p = ArchvowWalks.promptState(w, f);
            JsonObject o = new JsonObject();
            o.addProperty("state", p.state());
            o.addProperty("component", p.component());
            o.addProperty("open", p.open());
            prompt.add(f, o);
            byFeature.add(f, steps(ArchvowWalks.specifyQueue(w, "feature", f)));
            build.add(f, strings(ArchvowWalks.buildQueue(w, f)));
        }
        for (String id : components) {
            byComponent.add(id, steps(ArchvowWalks.specifyQueue(w, "component", id)));
            featureOf.addProperty(id, ArchvowWalks.featureOf(w, id));
        }
        JsonObject specify = new JsonObject();
        specify.add("foundations", steps(ArchvowWalks.specifyQueue(w, "foundations", "")));
        specify.add("features", byFeature);
        specify.add("components", byComponent);
        JsonObject out = new JsonObject();
        out.add("prompt", prompt);
        out.add("specify", specify);
        out.add("build", build);
        out.add("featureOf", featureOf);
        out.addProperty("budget", ArchvowWalks.budgetReached(w));
        out.add("ready", strings(ArchvowWalks.readyToBuild(w)));
        return out;
    }

    public static void main(String[] args) throws Exception {
        Path contract = Path.of(System.getProperty("archvow.contract.root", "../contract"));
        JsonObject file = JsonParser.parseString(Files.readString(contract.resolve("walks/expected.json"))).getAsJsonObject();
        int count = 0;
        for (JsonElement element : file.getAsJsonArray("cases")) {
            JsonObject c = element.getAsJsonObject();
            String example = c.get("example").getAsString(), mode = c.get("mode").getAsString(), variant = c.get("variant").getAsString();
            JsonObject got = walks(workspace(contract, example, mode, variant));
            check(got.equals(c.getAsJsonObject("walks")), example + " " + mode + " " + variant + ": " + got + "\n  wanted " + c.get("walks"));
            count++;
        }
        check(count >= 27, "every example, mode and variant: " + count);

        // the strip, headless: what the end of a component's Specify says, and its buttons
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            ArchvowWalkStrip strip = new ArchvowWalkStrip();
            strip.show(ArchvowWalkStrip.Tone.DONE, "Specifying the foundations · 1 of 3", "✓ config specified",
                "Next: logging_setup · 1 decision", List.of(new ArchvowWalkStrip.Action("Specify next ▸", true, () -> { }),
                    new ArchvowWalkStrip.Action("Stop", false, () -> { })));
            check(strip.isVisible() && strip.titleText().equals("✓ config specified"), strip.titleText());
            check(strip.buttonLabels().equals(List.of("Specify next ▸", "Stop")), String.valueOf(strip.buttonLabels()));
            check(strip.progressText().contains("1 of 3"), strip.progressText());
            boolean[] ran = {false};
            strip.show(ArchvowWalkStrip.Tone.INFO, "", "orders_schema", "1 · The code ✓ copied   2 · Check   3 · Next",
                List.of(new ArchvowWalkStrip.Action("Build next ▸", true, false, () -> ran[0] = true)));
            check(!strip.button("Build next ▸").isEnabled(), "Build next waits for a passing Check");
            strip.close();
            check(!strip.isVisible(), "Stop closes the strip");
            // the feature page: the main button follows what Copy AI prompt does
            ArchvowFeatureView view = new ArchvowFeatureView(new ArchvowFeatureView.Actions() {
                @Override public void showComponent(String id) { }
                @Override public void showFeature(String name) { }
                @Override public void openFile(String relative) { }
                @Override public void openCommand(String command, java.util.Map<String, String> values) { }
                @Override public void openDoc(String feature) { }
                @Override public void copyPrompt(String feature, boolean check) { }
                @Override public void nextSteps(String feature) { }
                @Override public void specify(String feature) { }
            });
            JsonObject feature = JsonParser.parseString("{\"feature\":\"orders\",\"components\":[]}").getAsJsonObject();
            view.show(feature, new JsonObject(), java.util.Map.of(), java.util.Map.of(), new ArchvowWalks.PromptState("ready", "orders_schema", 12), false);
            check(view.primaryText().equals("Copy AI prompt · build orders_schema") && view.specifyShown(), view.primaryText());
            view.show(feature, new JsonObject(), java.util.Map.of(), java.util.Map.of(), new ArchvowWalks.PromptState("specify-first", "", 18), false);
            check(view.primaryText().equals("Specify") && !view.promptEnabled(), "strict, nothing ready: Specify leads, Copy AI prompt waits");
            check(view.stateLine().startsWith("Nothing to build yet: 18 decisions to make."), view.stateLine());
            view.show(feature, new JsonObject(), java.util.Map.of(), java.util.Map.of(), new ArchvowWalks.PromptState("propose-first", "orders_schema", 18), true);
            check(view.primaryText().equals("Copier le prompt IA · orders_schema") && view.specifyShown(), view.primaryText());
            view.show(feature, new JsonObject(), java.util.Map.of(), java.util.Map.of(), new ArchvowWalks.PromptState("built", "", 0), false);
            check(view.primaryText().equals("Add component") && view.promptEnabled() && view.stateLine().isEmpty(), view.primaryText());
        });
        System.out.println("WalksRegression: " + count + " cases as VS Code's, the strip and the feature page OK");
    }
}
