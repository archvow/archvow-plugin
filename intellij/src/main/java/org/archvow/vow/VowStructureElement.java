package org.archvow.vow;

import org.archvow.vow.core.VowDocument;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** One node of the Structure tool window: the file, or one line of the vow with the lines under it. */
final class VowStructureElement implements StructureViewTreeElement {
    private static final int LABEL = 70;

    private final PsiFile file;
    private final VowDocument document;
    private final VowDocument.Line line;  // null for the file

    private VowStructureElement(PsiFile file, VowDocument document, VowDocument.Line line) {
        this.file = file;
        this.document = document;
        this.line = line;
    }

    static VowStructureElement root(PsiFile file) {
        return new VowStructureElement(file, VowDocument.parse(file.getViewProvider().getContents()), null);
    }

    boolean isLeaf() {
        return line != null && line.children().isEmpty();
    }

    @Override
    public Object getValue() {
        if (line == null) {
            return file;
        }
        PsiElement at = file.findElementAt(line.keyStart);
        VowLinePsi psi = PsiTreeUtil.getParentOfType(at, VowLinePsi.class, false);
        return psi != null ? psi : file;
    }

    @Override
    public @NotNull ItemPresentation getPresentation() {
        if (line == null) {
            String title = "vow " + (document.kind() == null ? "?" : document.kind())
                    + (document.name() == null ? "" : " " + document.name());
            return new PresentationData(title, null, VowIcons.FILE, null);
        }
        String value = line.value == null ? null : shorten(line.value);
        return new PresentationData(line.key, value, null, null);
    }

    private static String shorten(String text) {
        return text.length() <= LABEL ? text : text.substring(0, LABEL - 3) + "...";
    }

    @Override
    public StructureViewTreeElement @NotNull [] getChildren() {
        List<VowDocument.Line> lines = line == null ? document.roots() : line.children();
        StructureViewTreeElement[] children = new StructureViewTreeElement[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            children[i] = new VowStructureElement(file, document, lines.get(i));
        }
        return children;
    }

    @Override
    public void navigate(boolean requestFocus) {
        if (getValue() instanceof Navigatable navigatable) {
            navigatable.navigate(requestFocus);
        }
    }

    @Override
    public boolean canNavigate() {
        return getValue() instanceof Navigatable navigatable && navigatable.canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
        return getValue() instanceof Navigatable navigatable && navigatable.canNavigateToSource();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VowStructureElement e && e.file.equals(file)
                && (line == null ? e.line == null : e.line != null && e.line.start == line.start);
    }

    @Override
    public int hashCode() {
        return 31 * file.hashCode() + (line == null ? 0 : line.start);
    }

    @Override
    public @Nullable String toString() {
        return line == null ? "vow" : line.key;
    }
}
