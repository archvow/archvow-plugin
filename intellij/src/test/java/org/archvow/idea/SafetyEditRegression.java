package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * The Safety view of 0.13.1: a recorded hazard can be removed and changed, the invariant a hazard needs is
 * written in place, the expert options of invariant add are apart, hazard stays a keyword in French, and a
 * language change reaches the editor tab.
 */
public final class SafetyEditRegression {
    public static void main(String[] args) throws Exception {
        // the commands behind Remove, Change (remove + add) and the invariant field
        assertEq(List.of("hazard", "remove", "orders_reader.H1", "--yes", "--json"),
            ArchvowInlineCommands.removeHazard("orders_reader", "orders_reader.H1"));
        assertEq(List.of("hazard", "remove", "orders_reader.H2", "--yes", "--json"),
            ArchvowInlineCommands.removeHazard("orders_reader", "H2"));
        assertEq(List.of("invariant", "add", "orders_reader", "every file is read once", "--prevents", "orders_reader.H1", "--json"),
            ArchvowInlineCommands.invariant("orders_reader", " every file is read once ", "orders_reader.H1"));
        for (Runnable invalid : List.<Runnable>of(
            () -> ArchvowInlineCommands.invariant("orders_reader", " ", "orders_reader.H1"),
            () -> ArchvowInlineCommands.removeHazard("orders_reader", ""))) {
            try { invalid.run(); throw new AssertionError("Invalid arguments accepted"); }
            catch (IllegalArgumentException expected) { /* fine */ }
        }

        // a row of buttons wider than its panel asks for a second row (FlowLayout hid it)
        JPanel row = new JPanel(new ArchvowWrapLayout(FlowLayout.LEFT, 5, 2));
        for (int i = 0; i < 4; i++) {
            JButton b = new JButton("Button " + i);
            b.setPreferredSize(new Dimension(90, 24));
            row.add(b);
        }
        row.setSize(new Dimension(220, 10));
        int twoRows = row.getPreferredSize().height;
        row.setSize(new Dimension(1000, 10));
        int oneRow = row.getPreferredSize().height;
        if (oneRow < 24 || twoRows < oneRow + 24) throw new AssertionError("wrap layout: one row " + oneRow + ", narrow " + twoRows);
        row.setSize(new Dimension(220, 300));
        row.doLayout();
        for (Component c : row.getComponents())
            if (c.getX() + c.getWidth() > 220) throw new AssertionError("a button is laid out outside the panel: " + c.getBounds());

        // invariant add: --verify, --evidence, --rule are archvow's hidden expert options, never "==SUPPRESS=="
        ArchvowGrammar.Command invariant = ArchvowGrammar.COMMANDS.stream().filter(c -> c.label.equals("invariant add")).findFirst().orElseThrow();
        for (ArchvowGrammar.Param p : invariant.parameters) {
            boolean expert = List.of("verify", "evidence", "rule").contains(p.name);
            if (p.hidden != expert) throw new AssertionError(p.name + " hidden=" + p.hidden);
            if (p.help.contains("SUPPRESS")) throw new AssertionError(p.name + " help " + p.help);
        }

        // French keeps the keyword: no "danger" in what the workspace says
        try (Stream<Path> files = Files.list(Path.of("src/main/java/org/archvow/idea"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("ArchvowUiIcons.java")).toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                for (String line : text.split("\n"))
                    if (line.matches(".*\"[^\"]*\\b[Dd]angers?\\b[^\"]*\".*"))
                        throw new AssertionError(file.getFileName() + ": " + line.trim());
            }
        }

        // a language change in one panel redraws the other one (the editor tab) - no close and reopen
        String panel = Files.readString(Path.of("src/main/java/org/archvow/idea/ArchvowPanel.java"), StandardCharsets.UTF_8);
        if (!panel.contains("other.languageChanged()") || !panel.contains("void languageChanged()"))
            throw new AssertionError("the language change does not reach the other panel");
        System.out.println("OK: hazard remove/change, invariant in place, expert options, hazard keyword, language");
    }

    private static void assertEq(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + " got " + actual);
    }
}
