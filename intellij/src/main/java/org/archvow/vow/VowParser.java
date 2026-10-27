package org.archvow.vow;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Makes one {@link VowTokenTypes#LINE} element per line. The tree of blocks (a line and the lines under
 * it) is read from the text by {@link org.archvow.vow.core.VowDocument}, which folding, the structure view
 * and the checks share, so the parser stays this simple and never fails.
 */
public final class VowParser implements PsiParser {
    @Override
    public @NotNull ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
        PsiBuilder.Marker file = builder.mark();
        while (!builder.eof()) {
            IElementType type = builder.getTokenType();
            if (type == VowTokenTypes.NEWLINE) {
                builder.advanceLexer();
                continue;
            }
            IElementType next = builder.lookAhead(1);
            if (type == VowTokenTypes.INDENT && (next == null || next == VowTokenTypes.NEWLINE)) {
                builder.advanceLexer();  // spaces before a comment or on a blank line
                continue;
            }
            PsiBuilder.Marker line = builder.mark();
            while (!builder.eof() && builder.getTokenType() != VowTokenTypes.NEWLINE) {
                builder.advanceLexer();
            }
            line.done(VowTokenTypes.LINE);
        }
        file.done(root);
        return builder.getTreeBuilt();
    }
}
