package org.archvow.vow.core;

/** The kinds of token a .vow file is cut into. Every character of a file belongs to exactly one token. */
public enum VowTokenKind {
    /** Spaces (or tabs) at the start of a line. */
    INDENT,
    /** Spaces inside a line. */
    WHITE_SPACE,
    /** The end of a line. */
    NEWLINE,
    /** A line starting with {@code #}. */
    COMMENT,
    /** {@code vow} on the first line. */
    HEADER_KEYWORD,
    /** The format version on the first line: {@code 1}. */
    HEADER_VERSION,
    /** What the file is: {@code project}, {@code component}, {@code proposals}. */
    HEADER_KIND,
    /** The name on the first line: the component's id. */
    HEADER_NAME,
    /**
     * The keyword starting a line, one kind per depth (the indentation, 2 spaces a level), plain or in
     * double quotes: depth 0 {@code name}, {@code type}, {@code decision}; depth 1 {@code question},
     * {@code answer}, {@code by}, a column; depth 2 what a depth-1 line holds; depth 3 and deeper.
     */
    KEY_0,
    KEY_1,
    KEY_2,
    KEY_3,
    /** Text in double quotes. */
    STRING,
    /** An integer or decimal number. */
    NUMBER,
    /** {@code true}, {@code false}, {@code null}. */
    LITERAL,
    /** An id: {@code H1}, {@code INV2}, {@code R1}, {@code clean_orders.H1}. */
    ID,
    /** A person or group: {@code @alice}, {@code @acme/architects}, or an e-mail: {@code alice@acme.com}. */
    PERSON,
    /** A date or a date-time: {@code 2026-10-21}, {@code 2026-10-21T10:00:00+00:00}. */
    DATE,
    /** Any other word. */
    WORD,
    LBRACKET,
    RBRACKET,
    COMMA,
    /** {@code {}}: an empty group. */
    EMPTY_GROUP,
    /** The {@code =} of {@code key = <JSON>}. */
    JSON_MARK,
    /** The JSON after {@code key = }. */
    JSON,
    /** Characters that cannot be read. */
    BAD
}
