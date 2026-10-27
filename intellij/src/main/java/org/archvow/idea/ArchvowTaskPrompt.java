package org.archvow.idea;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adapt spec task IDs such as T2 to `archvow prompt --task 2` (the CLI requires an int). */
final class ArchvowTaskPrompt {
    private static final Pattern NUMBER = Pattern.compile("(?i)^T?([0-9]+)$");
    private ArchvowTaskPrompt() {}

    static String taskNumber(String identifier) {
        if (identifier == null) return "";
        Matcher m = NUMBER.matcher(identifier.trim());
        if (!m.matches()) return "";
        try {
            int number = Integer.parseInt(m.group(1));
            return number > 0 ? Integer.toString(number) : "";
        }
        catch (NumberFormatException e) { return ""; }
    }

    static List<String> command(String component, String taskId) {
        if (component == null || component.isBlank()) throw new IllegalArgumentException("Missing component");
        if (taskId == null || taskId.isBlank()) return List.of("prompt", component);
        String n = taskNumber(taskId);
        if (n.isEmpty()) throw new IllegalArgumentException("Invalid task number: " + taskId);
        return List.of("prompt", component, "--task", n);
    }

    /** CLI returns this when the component is too small to split into separate tasks. */
    static boolean needsFullCardFallback(int exitCode, String output) {
        if (exitCode == 0 || output == null) return false;
        String s = output.toLowerCase(java.util.Locale.ROOT);
        return s.contains("is one task: its card is small enough")
            || s.contains("has tasks t1..t");
    }
}
