package org.archvow.vow.core;

import java.util.regex.Pattern;

/**
 * Cuts the text of a .vow file into tokens, one at a time, for syntax highlighting.
 *
 * <p>The scanner never fails: every character belongs to a token, characters it cannot read are
 * {@link VowTokenKind#BAD}. It knows no keyword list - any keyword is a key of its depth,
 * {@link VowTokenKind#KEY_0} to {@link VowTokenKind#KEY_3} - so a new keyword never needs a new version
 * of the editor support. Its state is a small integer, so an
 * editor can restart it at any token (see {@link #state()}).
 */
public final class VowScanner {
    // modes: where the scanner is on its line
    private static final int LINE_START = 0;
    private static final int VALUE = 1;
    private static final int HEADER = 2;
    private static final int JSON_REST = 3;
    private static final int MODE_MASK = 3;
    private static final int HEADER_SEEN = 4;       // the first line of the vow was read
    private static final int HEADER_WORD_SHIFT = 3;  // which word of the first line comes next (0..3)

    static final Pattern KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_./\\-]*");
    static final Pattern WORD = Pattern.compile("[A-Za-z0-9_@][A-Za-z0-9_@./:+\\-]*");
    static final Pattern NUMBER = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
    static final Pattern DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}(?:T[0-9:.]+(?:Z|[+-][0-9]{2}:[0-9]{2})?)?");
    static final Pattern ID = Pattern.compile("(?:[a-z][a-z0-9_]*\\.)?[A-Z]+[0-9]+");
    static final Pattern PERSON = Pattern.compile("@[A-Za-z0-9_.\\-]+(?:/[A-Za-z0-9_.\\-]+)*"
            + "|[A-Za-z0-9_.+\\-]+@[A-Za-z0-9\\-]+(?:\\.[A-Za-z0-9\\-]+)+");  // @alice, @acme/architects, alice@acme.com

    private CharSequence buffer = "";
    private int end;
    private int tokenStart;
    private int tokenEnd;
    private VowTokenKind kind;
    private int state;       // the state at tokenStart
    private int nextState;   // the state at tokenEnd

    /** Starts scanning {@code buffer} from {@code start} (exclusive {@code end}) in {@code state}. */
    public void start(CharSequence buffer, int start, int end, int state) {
        this.buffer = buffer;
        this.end = end;
        this.tokenEnd = start;
        this.nextState = state;
        advance();
    }

    /** The current token, or null at the end. */
    public VowTokenKind kind() {
        return kind;
    }

    public int tokenStart() {
        return tokenStart;
    }

    public int tokenEnd() {
        return tokenEnd;
    }

    /** The state at the start of the current token: {@link #start} accepts it to resume there. */
    public int state() {
        return state;
    }

    public int end() {
        return end;
    }

    /** Moves to the next token. */
    public void advance() {
        tokenStart = tokenEnd;
        state = nextState;
        if (tokenStart >= end) {
            kind = null;
            return;
        }
        int mode = state & MODE_MASK;
        switch (mode) {
            case LINE_START -> lineStart();
            case HEADER -> header();
            case JSON_REST -> jsonRest();
            default -> value();
        }
    }

    private char at(int i) {
        return buffer.charAt(i);
    }

    private int lineEnd(int from) {
        int i = from;
        while (i < end && at(i) != '\n') {
            i++;
        }
        return i;
    }

    private void emit(VowTokenKind k, int to, int mode) {
        emit(k, to, mode, state);
    }

    private void emit(VowTokenKind k, int to, int mode, int base) {
        kind = k;
        tokenEnd = to;
        nextState = (base & ~MODE_MASK) | mode;
    }

    private void lineStart() {
        int i = tokenStart;
        char c = at(i);
        if (c == '\n') {
            emit(VowTokenKind.NEWLINE, i + 1, LINE_START);
            return;
        }
        if (c == ' ' || c == '\t' || c == '\r') {
            int j = i;
            while (j < end && (at(j) == ' ' || at(j) == '\t' || at(j) == '\r')) {
                j++;
            }
            emit(VowTokenKind.INDENT, j, LINE_START);
            return;
        }
        if (c == '#') {
            emit(VowTokenKind.COMMENT, lineEnd(i), LINE_START);
            return;
        }
        if ((state & HEADER_SEEN) == 0) {  // the first line of the vow
            int seen = state | HEADER_SEEN;
            int j = wordEnd(i);
            if (j - i == 3 && "vow".contentEquals(buffer.subSequence(i, j))) {
                kind = VowTokenKind.HEADER_KEYWORD;
                tokenEnd = j;
                nextState = (seen & ~MODE_MASK & ~(3 << HEADER_WORD_SHIFT)) | HEADER | (1 << HEADER_WORD_SHIFT);
                return;
            }
            key(i, seen);
            return;
        }
        key(i, state);
    }

    private void key(int i, int base) {
        VowTokenKind keyKind = keyOfDepth(i);
        if (at(i) == '"') {
            int close = closingQuote(i);
            emit(close < 0 ? VowTokenKind.BAD : keyKind, close < 0 ? lineEnd(i) : close + 1, VALUE, base);
            return;
        }
        int j = i;
        while (j < end && !isSpace(at(j)) && at(j) != '\n') {
            j++;
        }
        boolean ok = KEY.matcher(buffer.subSequence(i, j)).matches();
        emit(ok ? keyKind : VowTokenKind.BAD, j, VALUE, base);
    }

    /** The key kind of a keyword starting at {@code i}: its depth is its column / 2 (2 spaces a level). */
    private VowTokenKind keyOfDepth(int i) {
        int column = 0;
        while (i - column - 1 >= 0 && at(i - column - 1) != '\n') {
            column++;
        }
        return depthKind(column / 2);
    }

    /** {@link VowTokenKind#KEY_0} for depth 0 ... {@link VowTokenKind#KEY_3} for depth 3 and deeper. */
    public static VowTokenKind depthKind(int depth) {
        return switch (Math.min(depth, 3)) {
            case 0 -> VowTokenKind.KEY_0;
            case 1 -> VowTokenKind.KEY_1;
            case 2 -> VowTokenKind.KEY_2;
            default -> VowTokenKind.KEY_3;
        };
    }

    private void header() {
        int i = tokenStart;
        char c = at(i);
        if (c == '\n') {
            emit(VowTokenKind.NEWLINE, i + 1, LINE_START);
            return;
        }
        if (isSpace(c)) {
            emit(VowTokenKind.WHITE_SPACE, spaceEnd(i), HEADER);
            return;
        }
        int word = (state >> HEADER_WORD_SHIFT) & 3;
        int j = wordEnd(i);
        VowTokenKind k = switch (word) {
            case 1 -> VowTokenKind.HEADER_VERSION;
            case 2 -> VowTokenKind.HEADER_KIND;
            case 3 -> VowTokenKind.HEADER_NAME;
            default -> VowTokenKind.BAD;  // more words than a first line has
        };
        kind = k;
        tokenEnd = j;
        int next = word == 0 ? 0 : Math.min(word + 1, 4) & 3;  // after the name: 0 (BAD from then on)
        nextState = (state & ~MODE_MASK & ~(3 << HEADER_WORD_SHIFT)) | HEADER | (next << HEADER_WORD_SHIFT);
    }

    private void jsonRest() {
        int i = tokenStart;
        if (at(i) == '\n') {
            emit(VowTokenKind.NEWLINE, i + 1, LINE_START);
            return;
        }
        emit(VowTokenKind.JSON, lineEnd(i), JSON_REST);
    }

    private void value() {
        int i = tokenStart;
        char c = at(i);
        if (c == '\n') {
            emit(VowTokenKind.NEWLINE, i + 1, LINE_START);
        } else if (isSpace(c)) {
            emit(VowTokenKind.WHITE_SPACE, spaceEnd(i), VALUE);
        } else if (c == '"') {
            int close = closingQuote(i);
            emit(VowTokenKind.STRING, close < 0 ? lineEnd(i) : close + 1, VALUE);
        } else if (c == '[') {
            emit(VowTokenKind.LBRACKET, i + 1, VALUE);
        } else if (c == ']') {
            emit(VowTokenKind.RBRACKET, i + 1, VALUE);
        } else if (c == ',') {
            emit(VowTokenKind.COMMA, i + 1, VALUE);
        } else if (c == '{') {
            boolean pair = i + 1 < end && at(i + 1) == '}';
            emit(pair ? VowTokenKind.EMPTY_GROUP : VowTokenKind.BAD, i + (pair ? 2 : 1), VALUE);
        } else if (c == '=' && (i + 1 >= end || at(i + 1) == ' ' || at(i + 1) == '\n')) {
            emit(VowTokenKind.JSON_MARK, i + 1, JSON_REST);
        } else {
            int j = i;
            while (j < end && !isSpace(at(j)) && at(j) != '\n' && at(j) != ',' && at(j) != ']' && at(j) != '"') {
                j++;
            }
            emit(classify(buffer.subSequence(i, j).toString()), Math.max(j, i + 1), VALUE);
        }
    }

    /** What kind of value a bare word is. */
    public static VowTokenKind classify(String word) {
        if (word.equals("true") || word.equals("false") || word.equals("null")) {
            return VowTokenKind.LITERAL;
        }
        if (NUMBER.matcher(word).matches()) {
            return VowTokenKind.NUMBER;
        }
        if (DATE.matcher(word).matches()) {
            return VowTokenKind.DATE;
        }
        if (PERSON.matcher(word).matches()) {
            return VowTokenKind.PERSON;
        }
        if (ID.matcher(word).matches()) {
            return VowTokenKind.ID;
        }
        return WORD.matcher(word).matches() ? VowTokenKind.WORD : VowTokenKind.BAD;
    }

    private int closingQuote(int open) {
        int i = open + 1;
        while (i < end && at(i) != '\n') {
            if (at(i) == '\\') {
                i += 2;
                continue;
            }
            if (at(i) == '"') {
                return i;
            }
            i++;
        }
        return -1;
    }

    private int spaceEnd(int i) {
        int j = i;
        while (j < end && isSpace(at(j))) {
            j++;
        }
        return j;
    }

    private int wordEnd(int i) {
        int j = i;
        while (j < end && !isSpace(at(j)) && at(j) != '\n') {
            j++;
        }
        return j;
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\r';
    }
}
