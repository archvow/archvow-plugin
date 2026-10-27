package org.archvow.vow;

import org.archvow.vow.core.VowTokenKind;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import java.util.EnumMap;
import java.util.Map;

/** The IntelliJ element types of the .vow tokens (one per {@link VowTokenKind}), the line and the file. */
public final class VowTokenTypes {
    public static final IFileElementType FILE = new IFileElementType(VowLanguage.INSTANCE);
    /** One line of a vow: its keyword and its value. */
    public static final IElementType LINE = new IElementType("LINE", VowLanguage.INSTANCE);

    private static final Map<VowTokenKind, IElementType> TYPES = new EnumMap<>(VowTokenKind.class);

    static {
        for (VowTokenKind kind : VowTokenKind.values()) {
            TYPES.put(kind, switch (kind) {
                case WHITE_SPACE -> TokenType.WHITE_SPACE;
                case BAD -> TokenType.BAD_CHARACTER;
                default -> new IElementType(kind.name(), VowLanguage.INSTANCE);
            });
        }
    }

    public static final IElementType INDENT = of(VowTokenKind.INDENT);
    public static final IElementType NEWLINE = of(VowTokenKind.NEWLINE);
    public static final IElementType COMMENT = of(VowTokenKind.COMMENT);
    public static final IElementType STRING = of(VowTokenKind.STRING);

    private VowTokenTypes() {
    }

    public static IElementType of(VowTokenKind kind) {
        return TYPES.get(kind);
    }
}
