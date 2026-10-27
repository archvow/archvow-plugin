package org.archvow.idea;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/** Displays the full Archvow dashboard in the central editor area. */
public final class ArchvowEditorProvider implements FileEditorProvider, DumbAware {
    @Override public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
        return file instanceof ArchvowEditorTab;
    }

    @Override public @NotNull FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
        return new ArchvowMainFileEditor(project, file);
    }

    @Override public @NotNull String getEditorTypeId() {
        return "archvow.dashboard.editor";
    }

    @Override public @NotNull FileEditorPolicy getPolicy() {
        return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
    }
}
