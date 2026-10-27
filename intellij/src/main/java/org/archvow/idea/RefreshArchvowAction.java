package org.archvow.idea;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/** Reads the project again: the tool window reads it and hands it to the editor tab. */
public final class RefreshArchvowAction extends AnAction {
    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) return;
        ArchvowPanel panel = project.getUserData(ArchvowToolWindowFactory.PANEL);
        if (panel != null) { panel.reload(); return; }
        ArchvowPanel tab = ArchvowEditorTab.openPanel(project);
        if (tab != null) tab.reload();
    }
}
