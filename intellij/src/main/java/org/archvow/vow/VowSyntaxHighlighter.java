package org.archvow.vow;

import org.archvow.vow.core.VowTokenKind;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

/** Colours of the .vow tokens. Each one can be changed in Settings > Editor > Color Scheme > Vow. */
public final class VowSyntaxHighlighter extends SyntaxHighlighterBase {
    public static final TextAttributesKey HEADER =
            createTextAttributesKey("VOW_HEADER", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey NAME =
            createTextAttributesKey("VOW_NAME", DefaultLanguageHighlighterColors.CLASS_NAME);
    /**
     * The keywords, one colour per depth (indentation): `name`, `decision`; `question`, `by`; deeper.
     * Their colours are the plugin's own in every colour scheme (light or dark), not borrowed from another
     * language element: a scheme colours "instance field" or "label" as it likes (violet in many), which
     * hid the rule. A scheme can still set them: Settings > Editor > Color Scheme > Vow > Keyword.
     * The keys have no fallback: their colours (light / dark) are in colorSchemes/VowDefault.xml and
     * VowDarcula.xml, registered as additionalTextAttributes in plugin.xml and inherited by every scheme based
     * on Default or Darcula. 0: teal bold / yellow bold; 1: burnt orange / soft green; 2: steel blue / blue;
     * 3+: olive / pink. (Default colours set in code need the deprecated createTextAttributesKey(String,
     * TextAttributes).)
     */
    public static final TextAttributesKey KEY_0 = createTextAttributesKey("VOW_KEY_0");
    public static final TextAttributesKey KEY_1 = createTextAttributesKey("VOW_KEY_1");
    public static final TextAttributesKey KEY_2 = createTextAttributesKey("VOW_KEY_2");
    public static final TextAttributesKey KEY_3 = createTextAttributesKey("VOW_KEY_3");
    public static final TextAttributesKey STRING =
            createTextAttributesKey("VOW_STRING", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey NUMBER =
            createTextAttributesKey("VOW_NUMBER", DefaultLanguageHighlighterColors.NUMBER);
    public static final TextAttributesKey LITERAL =
            createTextAttributesKey("VOW_LITERAL", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey ID =
            createTextAttributesKey("VOW_ID", DefaultLanguageHighlighterColors.CONSTANT);
    public static final TextAttributesKey PERSON =
            createTextAttributesKey("VOW_PERSON", DefaultLanguageHighlighterColors.METADATA);
    public static final TextAttributesKey DATE =
            createTextAttributesKey("VOW_DATE", DefaultLanguageHighlighterColors.NUMBER);
    public static final TextAttributesKey WORD =
            createTextAttributesKey("VOW_WORD", DefaultLanguageHighlighterColors.IDENTIFIER);
    public static final TextAttributesKey BRACKETS =
            createTextAttributesKey("VOW_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS);
    public static final TextAttributesKey COMMA =
            createTextAttributesKey("VOW_COMMA", DefaultLanguageHighlighterColors.COMMA);
    public static final TextAttributesKey JSON =
            createTextAttributesKey("VOW_JSON", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey COMMENT =
            createTextAttributesKey("VOW_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
    public static final TextAttributesKey BAD =
            createTextAttributesKey("VOW_BAD", HighlighterColors.BAD_CHARACTER);

    private static final Map<VowTokenKind, TextAttributesKey> BY_KIND = new EnumMap<>(VowTokenKind.class);
    private static final Map<IElementType, TextAttributesKey[]> BY_TYPE = new HashMap<>();

    static {
        BY_KIND.put(VowTokenKind.HEADER_KEYWORD, HEADER);
        BY_KIND.put(VowTokenKind.HEADER_VERSION, HEADER);
        BY_KIND.put(VowTokenKind.HEADER_KIND, HEADER);
        BY_KIND.put(VowTokenKind.HEADER_NAME, NAME);
        BY_KIND.put(VowTokenKind.KEY_0, KEY_0);
        BY_KIND.put(VowTokenKind.KEY_1, KEY_1);
        BY_KIND.put(VowTokenKind.KEY_2, KEY_2);
        BY_KIND.put(VowTokenKind.KEY_3, KEY_3);
        BY_KIND.put(VowTokenKind.STRING, STRING);
        BY_KIND.put(VowTokenKind.NUMBER, NUMBER);
        BY_KIND.put(VowTokenKind.LITERAL, LITERAL);
        BY_KIND.put(VowTokenKind.ID, ID);
        BY_KIND.put(VowTokenKind.PERSON, PERSON);
        BY_KIND.put(VowTokenKind.DATE, DATE);
        BY_KIND.put(VowTokenKind.WORD, WORD);
        BY_KIND.put(VowTokenKind.LBRACKET, BRACKETS);
        BY_KIND.put(VowTokenKind.RBRACKET, BRACKETS);
        BY_KIND.put(VowTokenKind.EMPTY_GROUP, BRACKETS);
        BY_KIND.put(VowTokenKind.COMMA, COMMA);
        BY_KIND.put(VowTokenKind.JSON_MARK, JSON);
        BY_KIND.put(VowTokenKind.JSON, JSON);
        BY_KIND.put(VowTokenKind.COMMENT, COMMENT);
        BY_KIND.put(VowTokenKind.BAD, BAD);
        BY_KIND.forEach((kind, key) -> BY_TYPE.put(VowTokenTypes.of(kind), pack(key)));
    }

    @Override
    public @NotNull Lexer getHighlightingLexer() {
        return new VowLexer();
    }

    @Override
    public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        return BY_TYPE.getOrDefault(tokenType, TextAttributesKey.EMPTY_ARRAY);
    }
}
