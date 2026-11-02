package org.archvow.idea;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Headless regression: the Project tree takes over only after Archvow is hidden. */
public final class ProjectFolderRevealRegression {
    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }

    public static void main(String[] args) {
        List<String> calls = new ArrayList<>();
        AtomicReference<Runnable> hidden = new AtomicReference<>();
        AtomicReference<Runnable> active = new AtomicReference<>();
        ArchvowProjectFolderReveal.handOff(callback -> {
            calls.add("hide Archvow");
            hidden.set(callback);
        }, callback -> {
            calls.add("activate Project");
            active.set(callback);
        }, () -> calls.add("select .claude"));
        check(calls.equals(List.of("hide Archvow")), "selection ran before hiding the sidebar");
        hidden.get().run();
        check(calls.equals(List.of("hide Archvow", "activate Project")), "selection ran before Project activation");
        active.get().run();
        check(calls.equals(List.of("hide Archvow", "activate Project", "select .claude")),
            "folder was not selected after Project activation");

        calls.clear();
        ArchvowProjectFolderReveal.handOff(Runnable::run,
            callback -> { calls.add("activate Project"); callback.run(); },
            () -> calls.add("select .claude"));
        check(calls.equals(List.of("activate Project", "select .claude")),
            "already-hidden Archvow should not block the Project view");
        System.out.println("OK: Project explorer activation and directory selection hand-off");
    }
}
