package org.archvow.vow;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import java.util.function.Function;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A banner over a vow: archvow commands write it, and which command changes it. */
public final class VowEditorNotification implements EditorNotificationProvider, DumbAware {
    @Override
    public @Nullable Function<? super FileEditor, ? extends JComponent> collectNotificationData(
            @NotNull Project project, @NotNull VirtualFile file) {
        if (!"vow".equals(file.getExtension())) {
            return null;
        }
        String text = message(file);
        return editor -> {
            EditorNotificationPanel panel = new EditorNotificationPanel();
            panel.setText(text);
            return panel;
        };
    }

    static String message(VirtualFile file) {
        VirtualFile folder = file.getParent();
        String name = file.getName();
        if (name.equals("proposals.vow")) {
            return "Decisions the AI proposed - a person reviews each one: archvow decision accept <component> <n>,"
                    + " or archvow decision make <component> <n> \"...\"";
        }
        if (folder != null && folder.getParent() != null && folder.getParent().getName().equals("components")) {
            String component = folder.getName();
            return "Written by archvow commands - change it with: archvow specify " + component
                    + " (all of it: archvow show " + component + ")";
        }
        return "Written by archvow commands (archvow init <package> --force -a <answer>=..., archvow rule add...)"
                + " - only rules, allowed_paths and dependencies_by_path are edited by hand, by an architect";
    }
}
