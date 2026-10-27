package org.archvow.idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * The Archvow tool window (left): Overview, Components (the tree) and Commands (the list). What is chosen
 * there opens in the one Archvow editor tab ({@link ArchvowEditorTab}).
 */
public final class ArchvowToolWindowFactory implements ToolWindowFactory {
    static final Key<ArchvowPanel> PANEL = Key.create("archvow.active.panel");
    @Override public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ArchvowPanel panel = new ArchvowPanel(project, ArchvowPanel.Role.NAVIGATION);
        project.putUserData(PANEL, panel);
        toolWindow.getContentManager().addContent(ContentFactory.getInstance().createContent(panel, "", false));
        panel.reload();
    }
}
