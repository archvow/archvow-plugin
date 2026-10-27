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
        check("src/x.py:42".equals(ArchvowLinks.showArgument("src/x.py", 42))
            && "src/x.py".equals(ArchvowLinks.showArgument("src/x.py", 0)), "show argument");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
