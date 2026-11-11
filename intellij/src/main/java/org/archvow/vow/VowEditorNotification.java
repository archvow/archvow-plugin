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
        String stem = name.endsWith(".vow") ? name.substring(0, name.length() - 4) : name;
        if (folder != null && folder.getName().equals("components")) {
            return "Written by archvow commands - change it with: archvow specify " + stem
                    + " (all of it: archvow show " + stem + "; another feature: archvow component move " + stem + " <feature>)";
        }
        if (folder != null && folder.getName().equals(stem) && folder.getParent() != null && folder.getParent().getName().equals("features")) {
            return "Written by archvow commands - what the product does: archvow show " + stem + ", archvow feature rename "
                    + stem + " <name>, its page: archvow doc " + stem;
        }
        return "Written by archvow commands (archvow init <package> --force -a <answer>=..., archvow rule add...)"
                + " - only rules, allowed_paths and dependencies_by_path are edited by hand, by an architect";
    }
}
