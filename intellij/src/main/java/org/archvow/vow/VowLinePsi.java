package org.archvow.vow;

import org.archvow.vow.core.VowDocument;
import org.archvow.vow.core.VowLinks;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiReference;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** One line of a vow. Its links (`uses`, `components`, `prevents`, `module`) are references: Ctrl+click follows them. */
public final class VowLinePsi extends ASTWrapperPsiElement {
    public VowLinePsi(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public PsiReference @NotNull [] getReferences() {
        VowDocument.Line line = VowDocument.line(getText());  // offsets inside this line
        List<VowLinks.Link> links = VowLinks.of(line);
        PsiReference[] references = new PsiReference[links.size()];
        for (int i = 0; i < links.size(); i++) {
            VowLinks.Link link = links.get(i);
            references[i] = new VowReference(this, new TextRange(link.start(), link.end()), link);
        }
        return references;
    }
}
