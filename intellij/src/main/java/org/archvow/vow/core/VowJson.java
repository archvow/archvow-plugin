package org.archvow.vow.core;

/** Checks that a text is one JSON value (the rare `key = <JSON>` lines of a vow). */
final class VowJson {
    private final String s;
    private int i;

    private VowJson(String s) {
        this.s = s;
    }

    /** What is wrong with the JSON, or null when it is one valid JSON value. */
    static String check(String text) {
        VowJson json = new VowJson(text);
        try {
            json.space();
            json.value();
            json.space();
            if (json.i != text.length()) {
                return "extra data at column " + (json.i + 1);
            }
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    private IllegalArgumentException error(String what) {
        return new IllegalArgumentException(what + " at column " + (i + 1));
    }

    private void space() {
        while (i < s.length() && " \t\r\n".indexOf(s.charAt(i)) >= 0) {
            i++;
        }
    }

    private void value() {
        if (i >= s.length()) {
            throw error("expecting a value");
        }
        char c = s.charAt(i);
        switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> word("true");
            case 'f' -> word("false");
            case 'n' -> word("null");
            case 'N' -> word("NaN");
            case 'I' -> word("Infinity");
            default -> number();
        }
    }

    private void object() {
        i++;
        space();
        if (i < s.length() && s.charAt(i) == '}') {
            i++;
            return;
        }
        while (true) {
            space();
            if (i >= s.length() || s.charAt(i) != '"') {
                throw error("expecting a property name in double quotes");
            }
            string();
            space();
            expect(':');
            space();
            value();
            space();
            if (i < s.length() && s.charAt(i) == ',') {
                i++;
                continue;
            }
            expect('}');
            return;
        }
    }

    private void array() {
        i++;
        space();
        if (i < s.length() && s.charAt(i) == ']') {
            i++;
            return;
        }
        while (true) {
            space();
            value();
            space();
            if (i < s.length() && s.charAt(i) == ',') {
                i++;
                continue;
            }
            expect(']');
            return;
        }
    }

    private void string() {
        i++;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '"') {
                i++;
                return;
            }
            if (c < 0x20) {
                throw error("a control character in a string");
            }
            i++;
        }
        throw error("a string with no closing quote");
    }

    private void word(String w) {
        if (!s.startsWith(w, i)) {
            throw error("expecting a value");
        }
        i += w.length();
    }

    private void number() {
        int start = i;
        if (i < s.length() && s.charAt(i) == '-') {
            i++;
            if (s.startsWith("Infinity", i)) {
                i += 8;
                return;
            }
        }
        while (i < s.length() && "0123456789.eE+-".indexOf(s.charAt(i)) >= 0) {
            i++;
        }
        if (!VowScanner.NUMBER.matcher(s.substring(start, i)).matches()) {
            i = start;
            throw error("expecting a value");
        }
    }

    private void expect(char c) {
        if (i >= s.length() || s.charAt(i) != c) {
            throw error("expecting '" + c + "'");
        }
        i++;
    }
}
