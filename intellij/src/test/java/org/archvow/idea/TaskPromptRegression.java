package org.archvow.idea;

import java.util.List;

public final class TaskPromptRegression {
    public static void main(String[] args) {
        if (!ArchvowTaskPrompt.command("sales_reader", "T1").equals(
                List.of("prompt", "sales_reader", "--task", "1")))
            throw new AssertionError("T1 should map to --task 1");
        if (!ArchvowTaskPrompt.command("config", "t02").equals(
                List.of("prompt", "config", "--task", "2")))
            throw new AssertionError("Leading zeros should normalize");
        if (!ArchvowTaskPrompt.command("config", "").equals(List.of("prompt", "config")))
            throw new AssertionError("Empty task must become full card");
        if (!ArchvowTaskPrompt.needsFullCardFallback(2, "'config' is one task: its card is small enough (archvow prompt config)"))
            throw new AssertionError("Missing single-task fallback");
        if (ArchvowTaskPrompt.needsFullCardFallback(0, "has tasks T1..T3"))
            throw new AssertionError("Do not retry successful commands");
        if (!ArchvowTaskPrompt.taskNumber("X2").isEmpty())
            throw new AssertionError("Reject non-task keys");
        System.out.println("OK: 6 prompt cases");
    }
}
