package org.archvow.idea;
import com.google.gson.*;
import javax.swing.*;
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
            pack.add("questions", questions); templates.add(pack); catalog.add("templates", templates);
            StringBuilder templateSelection = new StringBuilder();
            ArchvowInitQuestions wizard = new ArchvowInitQuestions(false,
                () -> {}, name -> templateSelection.append(name));
            wizard.setCatalog(catalog, "");
            if (!templateSelection.toString().equals("python-app")) throw new AssertionError(templateSelection);
            if (!wizard.answers().contains("language=fr")) throw new AssertionError(wizard.answers());
            if (!wizard.answers().contains("rules=ARCH,GDPR")) throw new AssertionError(wizard.answers());
            System.out.println("OK: template questions and defaults shown in native form");
        });
    }
}
