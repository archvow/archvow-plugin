package org.archvow.idea;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.beans.PropertyChangeListener;

/** The Archvow editor tab: its own panel in the DETAIL role (the component, command or result chosen in the tool window). */
final class ArchvowMainFileEditor extends UserDataHolderBase implements FileEditor {
    private final VirtualFile file;
    private final ArchvowPanel panel;
    private boolean disposed;

    ArchvowMainFileEditor(Project project, VirtualFile file) {
        this.file = file;
        this.panel = new ArchvowPanel(project, ArchvowPanel.Role.DETAIL);
        panel.openedAsTab();
    }

    ArchvowPanel panel() { return panel; }

    void refresh() { if (!disposed) panel.reload(); }

    @Override public @NotNull JComponent getComponent() { return panel; }
    @Override public @Nullable JComponent getPreferredFocusedComponent() { return panel; }
    @Override public @NotNull String getName() { return "Archvow"; }
    @Override public @NotNull FileEditorState getState(@NotNull FileEditorStateLevel level) {
        return FileEditorState.INSTANCE;
    }
    @Override public void setState(@NotNull FileEditorState state) { }
    @Override public boolean isModified() { return false; }
    @Override public boolean isValid() { return !disposed && file.isValid(); }
    @Override public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) { }
    @Override public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) { }
    @Override public @NotNull VirtualFile getFile() { return file; }
    @Override public void dispose() { disposed = true; panel.dispose(); }
}
