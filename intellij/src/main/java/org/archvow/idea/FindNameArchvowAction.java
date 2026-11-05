package org.archvow.idea;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * "Find in Archvow Project", in the editor (0.18.0): the selection, or the word under the caret, searched where
 * archvow keeps things (archvow show <name> --json) - the spec, the code, the tests, the examples, the briefs,
 * and the problems naming it - in the Archvow editor tab. A component's name opens the component.
 */
public final class FindNameArchvowAction extends AnAction {
    @Override public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        event.getPresentation().setEnabledAndVisible(project != null && editor != null && !word(editor).isBlank());
    }

    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        if (project == null || project.isDisposed() || editor == null) return;
        String name = word(editor);
        if (!name.isBlank()) ArchvowEditorTab.showSearch(project, name);
    }

    /** The selection when it is one word, else the name under the caret (letters, digits, _ . -). */
    static String word(Editor editor) {
        String selected = editor.getSelectionModel().getSelectedText();
        if (selected != null && !selected.isBlank() && !selected.trim().contains(" ")) return selected.trim();
        Document document = editor.getDocument();
        CharSequence text = document.getCharsSequence();
        int offset = Math.min(editor.getCaretModel().getOffset(), text.length());
        return around(text, offset);
    }

    /** The name around {@code offset} in {@code text}: letters, digits, _, . and - ("" when none). */
    static String around(CharSequence text, int offset) {
        int start = offset;
        int end = offset;
        while (start > 0 && part(text.charAt(start - 1))) start--;
        while (end < text.length() && part(text.charAt(end))) end++;
        String found = text.subSequence(start, end).toString().replaceAll("^[.\\-]+|[.\\-]+$", "");
        return found;
    }

    private static boolean part(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '-';
    }
}
