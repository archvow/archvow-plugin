package org.archvow.idea;

import java.util.List;

public final class InlineCommandRegression {
    public static void main(String[] args) {
        assertEq(List.of("decision", "make", "config", "1", "a new answer", "--json"),
            ArchvowInlineCommands.decide("config", "1", "a new answer"));
        assertEq(List.of("decision", "propose", "config", "1", "an AI answer", "--json"),
            ArchvowInlineCommands.propose("config", "1", "an AI answer"));
        assertEq(List.of("decision", "accept", "config", "1", "--json"),
            ArchvowInlineCommands.accept("config", "1"));
        assertEq(List.of("hazard", "add", "config", "S1", "high", "hazard text", "--json"),
            ArchvowInlineCommands.hazard("config", "S1", "high", "hazard text"));
        assertEq(List.of("hazard", "add", "config", "S1", "low", "--json"),
            ArchvowInlineCommands.hazard("config", "S1", "low", ""));
        assertEq(List.of("hazard", "dismiss", "config", "S1", "Not applicable", "--json"),
            ArchvowInlineCommands.dismiss("config", "S1", "Not applicable"));
        for (Runnable invalid : List.<Runnable>of(
            () -> ArchvowInlineCommands.decide("config", "1", ""),
            () -> ArchvowInlineCommands.propose("", "1", "answer"),
            () -> ArchvowInlineCommands.dismiss("config", "S1", " "))) {
            try { invalid.run(); throw new AssertionError("Invalid arguments accepted"); }
            catch (IllegalArgumentException expected) { /* fine */ }
        }
        System.out.println("OK: inline decision and safety commands");
    }
    private static void assertEq(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + " got " + actual);
    }
}
