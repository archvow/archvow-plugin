package org.archvow.idea;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Text sizes (0.11.4): the HTML the plugin shows is sized from the IDE's font, in pt (Swing reads "12px"
 * as 15.6 pt, and h1-h6 larger still: the doctor's result was far larger than the IDE's text); the
 * branch advice goes on several lines rather than being cut.
 */
public final class TextSizeRegression {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        UIManager.put("Label.font", new javax.swing.plaf.FontUIResource("Dialog", java.awt.Font.PLAIN, 13));
        check(ArchvowHtml.base() == 13, "the IDE's size");
        check(ArchvowHtml.size(1).equals("font-size:13pt") && ArchvowHtml.size(1.1).equals("font-size:14pt"), ArchvowHtml.size(1.1));
        check(ArchvowHtml.body("#111", null).contains("font-size:13pt") && !ArchvowHtml.body("#111", null).contains("px"), "the body");
        check(!ArchvowHtml.wellSized("<html><body style='font-size:12px'>") && !ArchvowHtml.wellSized("<h3 style='x'>a</h3>"), "the check");

        String doctor = "[{\"name\":\"archvow command\",\"status\":\"FAIL\",\"detail\":\"not on PATH\",\"fix\":\"add it to PATH\"},"
            + "{\"name\":\"template\",\"status\":\"WARN\",\"detail\":\"the project uses pyspark-pipeline@1.11.0\"},"
            + "{\"name\":\"python\",\"status\":\"OK\",\"detail\":\"3.12\"}]";
        for (boolean dark : new boolean[] {false, true}) {
            String html = ArchvowResultFormatter.render("doctor", 1, doctor, false, "python3 -m archvow doctor --json", false, dark);
            check(ArchvowHtml.wellSized(html), "doctor: " + html.substring(0, Math.min(300, html.length())));
            check(html.contains("font-size:13pt"), "doctor at the IDE's size");
        }
        Path contract = Path.of(System.getProperty("archvow.contract", "../contract/cli"));
        Path checkJson = contract.resolve("pyspark-pipeline/check.json");
        if (Files.isRegularFile(checkJson)) {
            String html = ArchvowResultFormatter.render("check", 1, Files.readString(checkJson), false, "archvow check --json", false, false);
            check(ArchvowHtml.wellSized(html), "check");
        }
        check(ArchvowHtml.wellSized(ArchvowResultFormatter.render("next", 0, "", false, "archvow next", true, false)), "an empty result");

        // the branch advice: as many lines as the width needs
        ArchvowWrapLabel advice = new ArchvowWrapLabel();
        advice.setText("⚠ Branche main : crée une branche de fonctionnalité (ex. feature/ma-modification) avant de développer.");
        advice.setSize(260, 10);
        int narrow = advice.getPreferredSize().height;
        advice.setSize(2000, 10);
        int wide = advice.getPreferredSize().height;
        check(narrow > wide && advice.getPreferredSize().width == 2000, "wrapped at 260 px: " + narrow + " > " + wide);
        check(!advice.isEditable() && !advice.isFocusable(), "a label, not a field");
        System.out.println("OK: HTML sized from the IDE's font, the branch advice wrapped");
    }
}
