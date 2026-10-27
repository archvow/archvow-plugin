package org.archvow.vow;

import org.archvow.vow.core.VowDocument;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Folds every block - a line and the lines under it - at any depth: `hazard H1 ...`, `columns ...`. */
public final class VowFoldingBuilder extends FoldingBuilderEx implements DumbAware {
    @Override
    public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        VowDocument vow = VowDocument.parse(document.getCharsSequence());
        List<FoldingDescriptor> regions = new ArrayList<>();
        ASTNode node = root.getNode();
        if (node == null) {
            return FoldingDescriptor.EMPTY_ARRAY;
        }
        add(vow.roots(), node, regions);
        return regions.toArray(FoldingDescriptor.EMPTY_ARRAY);
    }

    private static void add(List<VowDocument.Line> lines, ASTNode node, List<FoldingDescriptor> regions) {
        for (VowDocument.Line line : lines) {
            if (!line.children().isEmpty() && line.blockEnd() > line.end) {
                int count = count(line);
                regions.add(new FoldingDescriptor(node, new TextRange(line.end, line.blockEnd()), null,
                        " ... (" + count + (count == 1 ? " line)" : " lines)")));
                add(line.children(), node, regions);
            }
        }
    }

    private static int count(VowDocument.Line line) {
        int n = 0;
        for (VowDocument.Line child : line.children()) {
            n += 1 + count(child);
        }
        return n;
    }

    @Override
    public @Nullable String getPlaceholderText(@NotNull ASTNode node) {
        return " ...";
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return false;
    }
}
