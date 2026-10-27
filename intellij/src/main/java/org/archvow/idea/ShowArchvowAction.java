package org.archvow.idea;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/** Tools → Show Archvow: the Archvow tool window, shown and focused. */
public final class ShowArchvowAction extends AnAction {
    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project != null && !project.isDisposed()) ArchvowEditorTab.showNavigation(project);
    }
}
