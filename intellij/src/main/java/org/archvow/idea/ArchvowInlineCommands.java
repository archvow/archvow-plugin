package org.archvow.idea;

import java.util.List;

/** Small, explicit CLI argument builder for in-place human decisions. No shell involved. */
final class ArchvowInlineCommands {
    private ArchvowInlineCommands() { }

    static List<String> decide(String component, String number, String answer) {
        return List.of("decision", "make", required(component, "component"),
            required(number, "question number"), required(answer, "answer"), "--json");
    }

    static List<String> propose(String component, String number, String answer) {
        return List.of("decision", "propose", required(component, "component"),
            required(number, "question number"), required(answer, "proposal"), "--json");
    }

    static List<String> accept(String component, String number) {
        return List.of("decision", "accept", required(component, "component"),
            required(number, "question number"), "--json");
    }

    static List<String> hazard(String component, String guideWord, String severity, String description) {
        List<String> base = List.of("hazard", "add", required(component, "component"),
            required(guideWord, "safety question"), required(severity, "severity"));
        String text = description == null ? "" : description.trim();
        if (text.isEmpty()) return List.of(base.get(0), base.get(1), base.get(2), base.get(3), base.get(4), "--json");
        return List.of(base.get(0), base.get(1), base.get(2), base.get(3), base.get(4), text, "--json");
    }

    /** Without a reason: the decision that says there is nothing to protect is the reason (archvow 1.19). */
    static List<String> dismiss(String component, String guideWord, String reason) {
        String text = reason == null ? "" : reason.trim();
        if (text.isEmpty()) return List.of("hazard", "dismiss", required(component, "component"), required(guideWord, "safety question"), "--json");
        return List.of("hazard", "dismiss", required(component, "component"), required(guideWord, "safety question"), text, "--json");
    }

    /** {@code hazard edit <component>.H<n> ["words"] [--severity s]}: same id, its invariants stay (and become to review). */
    static List<String> editHazard(String component, String id, String words, String severity) {
        List<String> args = new java.util.ArrayList<>(List.of("hazard", "edit",
            ArchvowCommandPrefill.itemId(required(component, "component"), required(id, "hazard"))));
        if (words != null && !words.isBlank()) args.add(words.trim());
        if (severity != null && !severity.isBlank()) { args.add("--severity"); args.add(severity.trim()); }
        args.add("--json");
        return List.copyOf(args);
    }

    /** {@code hazard remove <component>.H<n> --yes}: show --json gives the qualified id, a short H1 gets the component. */
    static List<String> removeHazard(String component, String id) {
        return List.of("hazard", "remove", ArchvowCommandPrefill.itemId(required(component, "component"), required(id, "hazard")),
            "--yes", "--json");
    }

    static List<String> invariant(String component, String statement, String hazard) {
        return List.of("invariant", "add", required(component, "component"), required(statement, "invariant"), "--prevents",
            ArchvowCommandPrefill.itemId(component.trim(), required(hazard, "hazard")), "--json");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + name);
        return value.trim();
    }
}
