package org.archvow.idea;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** A component's "Uses ...  ·  Used by ..." line (archvow show --json `uses`, `used_by`), each name a link,
 *  as the VS Code workspace shows it; and the argument of archvow show for a file (Show Archvow for This File). */
public final class UsesAndFileRegression {
    public static void main(String[] args) {
        JsonObject view = new JsonObject();
        JsonArray uses = new JsonArray();
        uses.add("orders_schema");
        JsonArray usedBy = new JsonArray();
        usedBy.add("my_pipeline_job");
        view.add("uses", uses);
        view.add("used_by", usedBy);
        check("Uses: orders_schema  ·  Used by: my_pipeline_job".equals(ArchvowLinks.line(view, false)),
            "English line: " + ArchvowLinks.line(view, false));
        check("Utilise: orders_schema  ·  Utilisé par: my_pipeline_job".equals(ArchvowLinks.line(view, true)),
            "French line: " + ArchvowLinks.line(view, true));
        String html = ArchvowLinks.html(view, false);
        check(html.contains("<a href='component:orders_schema'>orders_schema</a>")
            && html.contains("<a href='component:my_pipeline_job'>my_pipeline_job</a>"), "links: " + html);
        check("my_pipeline_job".equals(ArchvowLinks.component("component:my_pipeline_job")), "link target");
        check(ArchvowLinks.component("https://example.com").isEmpty(), "another link is not a component");
        check(ArchvowLinks.line(new JsonObject(), false).isEmpty() && ArchvowLinks.html(new JsonObject(), true).isEmpty(),
            "no uses, no users: no line");
        check("src/sales/p1_domain/x.py".equals(ArchvowLinks.relative("/work/sales", "/work/sales/src/sales/p1_domain/x.py")),
            "relative path");
        check("src/x.py".equals(ArchvowLinks.relative("C:\\work\\sales\\", "C:\\work\\sales\\src\\x.py")), "Windows path");
        check(ArchvowLinks.relative("/work/sales", "/work/other/x.py").isEmpty(), "outside the project");
        JsonObject withExamples = new JsonObject();
        JsonArray examples = new JsonArray();
        JsonObject ok = new JsonObject();
        ok.addProperty("name", "SALES-5-negative");
        ok.addProperty("ticket", "SALES-5");
        ok.addProperty("folder", "tests/examples/clean_orders/SALES-5-negative");
        ok.addProperty("level", "unit");
        ok.addProperty("tested", true);
        ok.addProperty("unchanged", true);
        ok.addProperty("result", "passing");
        JsonObject changed = ok.deepCopy();
        changed.addProperty("name", "SALES-7-late");
        changed.addProperty("unchanged", false);
        examples.add(ok);
        examples.add(changed);
        withExamples.add("examples", examples);
        String shown = ArchvowLinks.examplesHtml(withExamples, false);
        check(shown.contains("✔ SALES-5-negative</b> (SALES-5) - tests/examples/clean_orders/SALES-5-negative/ · unit · passing"),
            "passing example: " + shown);
        check(shown.contains("✖ SALES-7-late</b>") && shown.contains("files changed since recorded (ARCH055)"), "changed files");
        check(ArchvowLinks.examplesHtml(withExamples, true).contains("Exemples métier"), "French title");
        check(ArchvowLinks.examplesHtml(new JsonObject(), false).isEmpty(), "no examples: nothing");
        check("src/x.py:42".equals(ArchvowLinks.showArgument("src/x.py", 42))
            && "src/x.py".equals(ArchvowLinks.showArgument("src/x.py", 0)), "show argument");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
