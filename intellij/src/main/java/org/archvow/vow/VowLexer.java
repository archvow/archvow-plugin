package org.archvow.vow;

import org.archvow.vow.core.VowScanner;
import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The IntelliJ lexer of .vow files: {@link VowScanner} (plain Java, tested on its own) behind the Lexer API. */
public final class VowLexer extends LexerBase {
    private final VowScanner scanner = new VowScanner();
    private CharSequence buffer = "";
    private int endOffset;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.endOffset = endOffset;
        scanner.start(buffer, startOffset, endOffset, initialState);
    }

    @Override
    public int getState() {
        return scanner.state();
    }

    @Override
    public @Nullable IElementType getTokenType() {
        return scanner.kind() == null ? null : VowTokenTypes.of(scanner.kind());
    }

    @Override
    public int getTokenStart() {
        return scanner.tokenStart();
    }

    @Override
    public int getTokenEnd() {
        return scanner.tokenEnd();
    }

    @Override
    public void advance() {
        scanner.advance();
    }

    @Override
    public @NotNull CharSequence getBufferSequence() {
        return buffer;
    }

    @Override
    public int getBufferEnd() {
        return endOffset;
    }
}
