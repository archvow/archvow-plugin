package org.archvow.idea;

import java.util.function.Consumer;

/**
 * Switch from the Archvow tool window to the Project tree before selecting a diagnostic folder.
 * Both tool-window operations are asynchronous, so the selection must run only after activation.
 */
final class ArchvowProjectFolderReveal {
    private ArchvowProjectFolderReveal() {}

    static void handOff(Consumer<Runnable> hideArchvow, Consumer<Runnable> activateProject,
                        Runnable selectDirectory) {
        hideArchvow.accept(() -> activateProject.accept(selectDirectory));
    }
}
