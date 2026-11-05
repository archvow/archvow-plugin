package org.archvow.idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;
import javax.swing.SwingUtilities;

/** Shows the Archvow tool window when an archvow project opens; nothing for other projects. */
public final class ArchvowOpenOnStartup implements StartupActivity.DumbAware {
    @Override public void runActivity(@NotNull Project project) {
        if (project.isDisposed()) return;
        String path = project.getBasePath();
        if (path == null) return;
        boolean fromWelcome = ArchvowInitFromWelcomeAction.consumePending();
        if (!fromWelcome && !ArchvowProjectReadiness.inspect(path).ready()) return;
        SwingUtilities.invokeLater(() -> {
            if (project.isDisposed()) return;
            ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Archvow");
            if (toolWindow != null) toolWindow.show(null);
            if (fromWelcome && ArchvowProjectReadiness.inspect(path).canInitialize())
                ArchvowEditorTab.showCommand(project, "init", java.util.Map.of());
        });
    }
}
