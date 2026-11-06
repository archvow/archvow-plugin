package org.archvow.idea;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.testFramework.LightVirtualFile;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The ONE Archvow editor tab of a project: the component, the command form or the result chosen in the
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

    /** The tab's panel, opening the tab when it is closed; the focus stays where it is (the tool window). Must run on the UI thread. */
    static ArchvowPanel panel(Project project) {
        if (project == null || project.isDisposed()) return null;
        ArchvowEditorTab file = file(project);
        for (FileEditor editor : FileEditorManager.getInstance(project).openFile(file, false)) {
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
        ArchvowPanel panel = panel(project);
        if (panel != null) panel.showComponent(id);
    }

    static void showCommand(Project project, String name, Map<String, String> initial) {
        ArchvowPanel panel = panel(project);
        if (panel != null) panel.showCommand(name, initial);
    }

    /** archvow show <name> --json in the editor tab (0.18.0): from the find field and "Find in Archvow Project". */
    static void showSearch(Project project, String name) {
        ArchvowPanel panel = panel(project);
        if (panel != null) panel.showSearch(name);
    }

    static void showBriefs(Project project) {
        ArchvowPanel panel = panel(project);
        if (panel != null) panel.openBriefsDetails();
    }

    static void showChecks(Project project, int index) {
        ArchvowPanel panel = panel(project);
        if (panel != null) panel.openDiagnosticDetails(index);
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
