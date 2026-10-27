package org.archvow.idea;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Reloads the workspace when a vow changes on disk, whoever changed it: an archvow command in a
 * terminal, an AI agent, a git checkout or pull. Changes are grouped: one reload after
 * {@link #QUIET_MS} without a new change, and again later if the workspace was busy.
 */
final class ArchvowVowWatcher {
    static final int QUIET_MS = 800;

    private ArchvowVowWatcher() { }

    /**
     * Calls {@code reload} on the Swing thread after the vows of {@code project} changed.
     * {@code reload} returns false when the workspace is busy: it is tried again later.
     */
    static void watch(Project project, Disposable lifetime, BooleanSupplier reload) {
        Timer timer = new Timer(QUIET_MS, null);
        timer.setRepeats(false);
        timer.addActionListener(e -> {
            if (!project.isDisposed() && !reload.getAsBoolean()) timer.restart();
        });
        project.getMessageBus().connect(lifetime).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override public void after(List<? extends VFileEvent> events) {
                String base = project.getBasePath();
                for (VFileEvent event : events) {
                    if (concerns(base, event.getPath())) {
                        SwingUtilities.invokeLater(timer::restart);
                        return;
                    }
                }
            }
        });
    }

    /** Whether a changed file is one of the project's vows ({@code .archvow/**}{@code /*.vow}). */
    static boolean concerns(String projectBase, String changedPath) {
        if (projectBase == null || changedPath == null) return false;
        String base = projectBase.replace('\\', '/');
        String path = changedPath.replace('\\', '/');
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return path.startsWith(base + "/.archvow/") && path.endsWith(".vow");
    }
}
