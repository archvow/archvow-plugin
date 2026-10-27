package org.archvow.vow.core;

import java.util.ArrayList;
import java.util.List;

/** The values of a .vow line: what a value may be, and the items of a list. */
public final class VowValues {
    private VowValues() {
    }

    /** One item of a list value, with its offsets inside the value. */
    public record Item(String text, int start, int end) {
    }

    /**
     * What is wrong with a value, or null when archvow reads it: a word, "text", a number,
     * true/false/null, [], {}, [a, "b c", 1] or {@code = <JSON>} (the JSON is not checked here).
     */
    public static String check(String value) {
        if (value.startsWith("= ") || value.equals("=")) {
            if (value.length() <= 2) {
                return "`=` needs JSON after it";
            }
            String error = VowJson.check(value.substring(2));
            return error == null ? null : "`= ...` is not valid JSON (" + error + ")";
        }
        if (value.equals("[]") || value.equals("{}")) {
            return null;
        }
        if (value.startsWith("[")) {
            if (!value.endsWith("]")) {
                return "a list starts with [ and ends with ]";
            }
            String inside = value.substring(1, value.length() - 1);
            List<Item> items = new ArrayList<>();
            String error = split(inside, 1, items);
            if (error != null) {
                return error;
            }
            for (Item item : items) {
                String e = scalar(item.text());
                if (e != null) {
                    return e;
                }
            }
            return null;
        }
        return scalar(value);
    }

    /** The items of a list value ({@code [a, b]}), with offsets in the value; empty for any other value. */
    public static List<Item> items(String value) {
        List<Item> items = new ArrayList<>();
        if (value != null && value.startsWith("[") && value.endsWith("]") && value.length() > 2) {
            split(value.substring(1, value.length() - 1), 1, items);
        }
        return items;
    }

    private static String split(String text, int offset, List<Item> out) {
        int i = 0;
        int n = text.length();
        while (i < n) {
            while (i < n && text.charAt(i) == ' ') {
                i++;
            }
            if (i >= n) {
                break;
            }
            int start = i;
            if (text.charAt(i) == '"') {
                int close = closingQuote(text, i);
                if (close < 0) {
                    return "text in quotes with no closing quote";
                }
                i = close + 1;
            } else {
                while (i < n && text.charAt(i) != ',' && text.charAt(i) != ' ') {
                    i++;
                }
            }
            out.add(new Item(text.substring(start, i), start + offset, i + offset));
            while (i < n && text.charAt(i) == ' ') {
                i++;
            }
            if (i < n) {
                if (text.charAt(i) != ',') {
                    return "list values are separated by commas";
                }
                i++;
                if (text.substring(i).isBlank()) {
                    return "a comma with no value after it";
                }
            }
        }
        return null;
    }

    private static String scalar(String text) {
        if (text.startsWith("\"")) {
            int close = closingQuote(text, 0);
            if (close < 0 || close != text.length() - 1) {
                return "text in quotes must be the whole value (one closing quote, at the end)";
            }
            return null;
        }
        VowTokenKind kind = VowScanner.classify(text);
        return kind == VowTokenKind.BAD ? "`" + text + "`: put text with spaces or signs in double quotes" : null;
    }

    private static int closingQuote(String text, int open) {
        int i = open + 1;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '"') {
                return i;
            }
            i++;
        }
        return -1;
    }

    /** The text of a quoted value or key, without its quotes and escapes ({@code \"}, {@code \\}, {@code \n}...). */
    public static String unquote(String quoted) {
        if (quoted.length() < 2 || !quoted.startsWith("\"") || !quoted.endsWith("\"")) {
            return quoted;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 1; i < quoted.length() - 1; i++) {
            char c = quoted.charAt(i);
            if (c == '\\' && i + 1 < quoted.length() - 1) {
                char e = quoted.charAt(++i);
                switch (e) {
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'r' -> out.append('\r');
                    case 'u' -> {
                        if (i + 4 < quoted.length()) {
                            out.append((char) Integer.parseInt(quoted.substring(i + 1, i + 5), 16));
                            i += 4;
                        }
                    }
                    default -> out.append(e);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
