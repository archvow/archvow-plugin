package org.archvow.idea;

import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/** The Archvow editor tab's title ("archvow: clean_orders") and icon (the archvow logo). */
public final class ArchvowEditorTabPresentation implements EditorTabTitleProvider, FileIconProvider {
    private static final Icon LOGO = IconLoader.getIcon("/icons/archvowToolWindow.svg", ArchvowEditorTabPresentation.class);

    @Override public @Nullable String getEditorTabTitle(@NotNull Project project, @NotNull VirtualFile file) {
        return file instanceof ArchvowEditorTab tab ? tab.title() : null;
    }

    @Override public @Nullable Icon getIcon(@NotNull VirtualFile file, int flags, @Nullable Project project) {
        return file instanceof ArchvowEditorTab ? LOGO : null;
    }
}
