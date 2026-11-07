package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

/** The Review step must keep every value in the three-column card presentation. */
public final class FormReviewGridRegression {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (boolean dark : new boolean[]{true, false}) {
                var values = List.of(
                    Map.entry("Package", "test_pipe"), Map.entry("Folder", ""),
                    Map.entry("What it must do", "Read data"), Map.entry("Template", "pyspark-pipeline"),
                    Map.entry("language", "fr"), Map.entry("ticket_pattern", "[A-Z]+-[0-9]+"),
                    Map.entry("custom", "<script>&\"'"));
                String html = ArchvowFormReviewGrid.html(values, dark, 3);
                check(html.startsWith("<html>"), "Review is HTML");
                check(html.contains("<table width='100%'"), "Review is a fluid card grid");
                check(html.contains("<b>Package</b>"), "Friendly field labels");
                check(html.contains("[A-Z]+-[0-9]+"), "Template answers are retained");
                check(html.contains("&lt;script&gt;&amp;&quot;&#39;"), "Value must be escaped");
                check(!html.contains("<script>"), "HTML is safe");
                check(html.contains("—"), "Empty fields are displayed");
                check(html.split("<tr>", -1).length > 2, "Multiple card rows");
                JEditorPane pane = new JEditorPane("text/html", html);
                pane.setSize(new Dimension(840, 600));
                check(pane.getPreferredSize().height > 0, "Swing can render review cards");
                for (int width : new int[]{380, 540, 840}) {
                    int columns = ArchvowFormReviewGrid.columnsForWidth(width);
                    String responsive = ArchvowFormReviewGrid.html(values, dark, columns);
                    pane.setText(responsive);
                    pane.setSize(new Dimension(width, 900));
                    check(pane.getPreferredSize().width <= width + 12,
                        "Review must fit a " + width + "px editor (got " + pane.getPreferredSize().width + ")");
                }
            }
        });
        System.out.println("OK: Review cards render in Swing, in light and dark themes");
    }
}
