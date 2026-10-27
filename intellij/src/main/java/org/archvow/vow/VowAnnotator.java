package org.archvow.vow;

import org.archvow.vow.core.VowDocument;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Reads the whole vow once ({@link VowDocument}) and shows what keeps archvow from reading it (errors) and
 * what archvow refuses for most keywords (warnings). The keyword colours come from the highlighter, one per depth.
 */
public final class VowAnnotator implements Annotator, DumbAware {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof VowFile file)) {
            return;  // one pass for the whole file
        }
        CharSequence text = file.getViewProvider().getContents();
        VowDocument vow = VowDocument.parse(text);
        int length = text.length();
        for (VowDocument.Problem problem : vow.problems()) {
            int start = Math.min(problem.start(), length);
            int end = Math.min(Math.max(problem.end(), start), length);
            if (start == end && start > 0 && end == length) {
                start--;  // a problem at the very end: show it on the last character
            }
            HighlightSeverity severity = problem.warning() ? HighlightSeverity.WARNING : HighlightSeverity.ERROR;
            holder.newAnnotation(severity, problem.message()).range(new TextRange(start, end)).create();
        }
    }
}
