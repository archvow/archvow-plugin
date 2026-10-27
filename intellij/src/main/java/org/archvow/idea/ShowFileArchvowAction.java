package org.archvow.idea;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * "Show Archvow for This File", on an editor tab and in the editor: archvow show <file>[:<line>], and the
 * component that owns the file in the Archvow editor tab - where a bug points, its component, what uses it,
 * the test behind each decision. A file no component owns gets archvow's own hint.
 */
public final class ShowFileArchvowAction extends AnAction {
    @Override public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        boolean inProject = project != null && file != null && file.isInLocalFileSystem()
            && !ArchvowLinks.relative(project.getBasePath(), file.getPath()).isBlank();
        event.getPresentation().setEnabledAndVisible(inProject);
    }

    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (project == null || project.isDisposed() || file == null) return;
        String relative = ArchvowLinks.relative(project.getBasePath(), file.getPath());
        if (relative.isBlank()) return;
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        boolean same = editor != null && file.equals(FileDocumentManager.getInstance().getFile(editor.getDocument()));
        int line = same ? editor.getCaretModel().getLogicalPosition().line + 1 : 0;
        ArchvowPanel panel = ArchvowEditorTab.panel(project);
        if (panel != null) panel.showFile(relative, line);
    }
}
