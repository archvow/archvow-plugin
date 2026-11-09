package org.archvow.idea;
import com.google.gson.*;
import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.util.List;

/** Headless regression of real catalog questions and -a KEY=VALUE generation. */
public final class InitWizardRegression {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JsonObject catalog = new JsonObject();
            JsonArray templates = new JsonArray();
            JsonObject pack = new JsonObject();
            pack.addProperty("name", "python-app");
            JsonArray questions = new JsonArray();
            JsonObject lang = new JsonObject();
            lang.addProperty("id", "language");
            lang.addProperty("ask", true);
            lang.addProperty("prompt", "Language of generated documents");
            lang.addProperty("default", "fr");
            JsonArray choices = new JsonArray();
            choices.add(new JsonPrimitive("fr")); choices.add(new JsonPrimitive("en"));
            lang.add("choices", choices); questions.add(lang);
            JsonObject rules = new JsonObject();
            rules.addProperty("id", "rules");
            rules.addProperty("ask", true);
            rules.addProperty("multiple", true);
            rules.addProperty("prompt", "Applicable checks");
            rules.addProperty("default", "ARCH,GDPR");
            JsonArray families = new JsonArray();
            for (String name : List.of("ARCH", "GDPR", "none")) families.add(new JsonPrimitive(name));
            rules.add("choices", families); questions.add(rules);
            JsonObject platform = new JsonObject();
            platform.addProperty("id", "platform");
            platform.addProperty("ask", true);
            platform.addProperty("prompt", "CI platform");
            platform.addProperty("default", "");
            questions.add(platform);
            pack.add("questions", questions); templates.add(pack); catalog.add("templates", templates);
            StringBuilder templateSelection = new StringBuilder();
            ArchvowInitQuestions wizard = new ArchvowInitQuestions(false,
                () -> {}, name -> templateSelection.append(name));
            final int[] changes = {0};
            wizard.useParentSteps(() -> changes[0]++);
            wizard.setCatalog(catalog, "");
            if (!wizard.pageNames().equals(List.of("Governance", "CI & quality")))
                throw new AssertionError("One parent row should own every question group: " + wizard.pageNames());
            if (!wizard.pageReady(0) || wizard.pageReady(1))
                throw new AssertionError("Required field completion must distinguish filled vs missing values");
            wizard.selectParentStep(1);
            if (changes[0] < 1) throw new AssertionError("Template changes must refresh parent steps");
            if (!templateSelection.toString().equals("python-app")) throw new AssertionError(templateSelection);
            if (!wizard.answers().contains("language=fr")) throw new AssertionError(wizard.answers());
            if (!wizard.answers().contains("rules=ARCH,GDPR")) throw new AssertionError(wizard.answers());

            // The shipped PySpark template has optional settings in all five groups.
            // As in VS Code they must appear in guided navigation AND in Review,
            // including empty answers (but without silently adding CLI overrides).
            JsonObject shipped = ArchvowInitCatalog.bundled();
            if (shipped == null) throw new AssertionError("Missing bundled init catalog");
            wizard.setCatalog(shipped, "pyspark-pipeline");
            List<String> pages = wizard.pageNames();
            if (!pages.equals(List.of("Governance", "AI & briefs", "CI & quality",
                    "Runtime", "Limits & integration")))
                throw new AssertionError("Missing optional-question steps: " + pages);
            var review = wizard.reviewAnswers();
            JsonObject spark = null;
            for (JsonElement item : shipped.getAsJsonArray("templates"))
                if ("pyspark-pipeline".equals(item.getAsJsonObject().get("name").getAsString()))
                    spark = item.getAsJsonObject();
            if (spark == null || review.size() != spark.getAsJsonArray("questions").size())
                throw new AssertionError("Review must contain all catalog questions: " + review);
            if (!review.contains(java.util.Map.entry("python_version", "3.11"))
                    || !review.contains(java.util.Map.entry("max_components", ""))
                    || !review.contains(java.util.Map.entry("pip_index_url", "")))
                throw new AssertionError("Optional defaults and empty fields must be visible: " + review);
            if (wizard.answers().stream().anyMatch(a -> a.startsWith("python_version=") || a.startsWith("git_hooks=")))
                throw new AssertionError("Untouched optional defaults must not change init command: " + wizard.answers());
            JComboBox<?> python = comboWith(wizard, "3.11");
            if (python == null) throw new AssertionError("Missing editable optional python_version field");
            python.setSelectedItem("3.12");
            if (!wizard.answers().contains("python_version=3.12")
                    || !wizard.reviewAnswers().contains(java.util.Map.entry("python_version", "3.12")))
                throw new AssertionError("Editing an optional answer must update the CLI and review");
            System.out.println("OK: all init catalog questions appear in steps and review; CLI defaults are preserved");
        });
    }

    private static JComboBox<?> comboWith(Container root, String value) {
        for (Component component : root.getComponents()) {
            if (component instanceof JComboBox<?> combo && value.equals(combo.getSelectedItem())) return combo;
            if (component instanceof Container nested) {
                JComboBox<?> found = comboWith(nested, value);
                if (found != null) return found;
            }
        }
        return null;
    }
}
