package org.archvow.vow;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public final class VowParserDefinition implements ParserDefinition {
    private static final TokenSet WHITE_SPACE = TokenSet.create(TokenType.WHITE_SPACE);
    private static final TokenSet COMMENTS = TokenSet.create(VowTokenTypes.COMMENT);
    private static final TokenSet STRINGS = TokenSet.create(VowTokenTypes.STRING);

    @Override
    public @NotNull Lexer createLexer(Project project) {
        return new VowLexer();
    }

    @Override
    public @NotNull PsiParser createParser(Project project) {
        return new VowParser();
    }

    @Override
    public @NotNull IFileElementType getFileNodeType() {
        return VowTokenTypes.FILE;
    }

    @Override
    public @NotNull TokenSet getWhitespaceTokens() {
        return WHITE_SPACE;  // indentation and line ends are not: the parser reads lines
    }

    @Override
    public @NotNull TokenSet getCommentTokens() {
        return COMMENTS;
    }

    @Override
    public @NotNull TokenSet getStringLiteralElements() {
        return STRINGS;
    }

    @Override
    public @NotNull PsiElement createElement(ASTNode node) {
        return new VowLinePsi(node);
    }

    @Override
    public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new VowFile(viewProvider);
    }
}
