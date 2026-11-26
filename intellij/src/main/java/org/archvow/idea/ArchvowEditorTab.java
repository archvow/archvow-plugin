package org.archvow.idea;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * The ONE Archvow editor tab of a project: the feature, the component, the command form or the result chosen in the
 * Archvow tool window. A click on another component shows it in this same tab; closing the tab and
 * clicking again opens it again. Its title follows what it shows ("archvow: clean_orders").
 */
final class ArchvowEditorTab extends LightVirtualFile {
    private static final Map<Project, ArchvowEditorTab> FILES = new WeakHashMap<>();
    private volatile String title = "archvow";

    private ArchvowEditorTab() {
        super("archvow", PlainTextFileType.INSTANCE, "");
    }

    String title() { return title; }

    private static ArchvowEditorTab file(Project project) {
        synchronized (FILES) {
            return FILES.computeIfAbsent(project, unused -> new ArchvowEditorTab());
        }
    }

    /**
     * Gives the tab's panel to {@code action}, opening the tab when it is closed; the focus stays where it is (the tool
     * window). Must run on the UI thread. False only when there is no project to open it in.
     * The tab opens through OpenFileDescriptor.navigate (0.26.1): FileEditorManager.openFile is deprecated from
     * IntelliJ 2026.3, whose replacements open later and do not exist before it. Up to 2026.2 the tab is open when
     * navigate returns and the action runs at once; when the IDE opens it later, the action runs when it is open.
     */
    static boolean withPanel(Project project, Consumer<ArchvowPanel> action) {
        if (project == null || project.isDisposed()) return false;
        ArchvowEditorTab file = file(project);
        new OpenFileDescriptor(project, file).navigate(false);
        ArchvowPanel now = panelOf(FileEditorManager.getInstance(project), file);
        if (now != null) {
            action.accept(now);
            return true;
        }
        MessageBusConnection connection = project.getMessageBus().connect();
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile opened) {
                if (!opened.equals(file)) return;
                connection.disconnect();
                ArchvowPanel later = panelOf(source, file);
                if (later != null) action.accept(later);
            }
        });
        return true;
    }

    private static ArchvowPanel panelOf(FileEditorManager manager, ArchvowEditorTab file) {
        for (FileEditor editor : manager.getEditors(file)) {
            if (editor instanceof ArchvowMainFileEditor archvow) return archvow.panel();
        }
        return null;
    }

    /** The tab's panel when the tab is open, else null (never opens it). */
    static ArchvowPanel openPanel(Project project) {
        if (project == null || project.isDisposed()) return null;
        ArchvowEditorTab file;
        synchronized (FILES) { file = FILES.get(project); }
        if (file == null) return null;
        FileEditorManager manager = FileEditorManager.getInstance(project);
        if (!manager.isFileOpen(file)) return null;
        for (FileEditor editor : manager.getEditors(file)) {
            if (editor instanceof ArchvowMainFileEditor archvow) return archvow.panel();
        }
        return null;
    }

    static void showComponent(Project project, String id) {
        withPanel(project, panel -> panel.showComponent(id));
    }

    static void showCommand(Project project, String name, Map<String, String> initial) {
        withPanel(project, panel -> panel.showCommand(name, initial));
    }

    /** archvow show <name> --json in the editor tab (0.18.0): from the find field and "Find in Archvow Project". */
    static void showSearch(Project project, String name) {
        withPanel(project, panel -> panel.showSearch(name));
    }

    /** A feature's page in the editor tab (0.24.0, contract 2.0). */
    static void showFeature(Project project, String name) {
        withPanel(project, panel -> panel.showFeature(name));
    }

    static void showBriefs(Project project) {
        withPanel(project, panel -> panel.openBriefsDetails());
    }

    static void showChecks(Project project, int index) {
        withPanel(project, panel -> panel.openDiagnosticDetails(index));
    }

    /** Open an AI skill in the shared editor, rather than a separate blocking OS dialog. */
    static void showSkill(Project project, String skill, Map<String, String> initial) {
        withPanel(project, panel -> panel.openSkill(skill, initial));
    }

    /** The tab's title (shown by {@link ArchvowEditorTabPresentation}). */
    static void setTitle(Project project, String title) {
        if (project == null || project.isDisposed()) return;
        ArchvowEditorTab file;
        synchronized (FILES) { file = FILES.get(project); }
        if (file == null || title == null || title.equals(file.title)) return;
        file.title = title;
        FileEditorManager manager = FileEditorManager.getInstance(project);
        if (manager instanceof FileEditorManagerEx ex && manager.isFileOpen(file)) ex.updateFilePresentation(file);
    }

    /** The Archvow tool window, shown and focused. */
    static void showNavigation(Project project) {
        if (project == null || project.isDisposed()) return;
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Archvow");
        if (toolWindow != null) toolWindow.activate(null);
    }
}
