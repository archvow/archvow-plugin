package org.archvow.vow.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A .vow file read as a tree of lines, with the problems that keep archvow from reading it.
 *
 * <p>The rules are the format's, not a list of keywords: the first line {@code vow <version> <kind>
 * [<name>]}; two spaces per level, never tabs; {@code keyword value} or a keyword with lines under it;
 * values are words, "text", numbers, true/false/null, [lists], {} or {@code = <JSON>}. Any keyword is
 * accepted at any depth, so the editor support follows new keywords without a new version. archvow
 * itself also checks what each keyword means ({@code archvow check}).
 */
public final class VowDocument {
    /** The version of the format this reader knows. */
    public static final int FORMAT_VERSION = 1;

    /** One line of the file. */
    public static final class Line {
        public final int number;       // 1-based
        public final int start;        // offset of the line's first character
        public final int end;          // offset of its end (before the newline)
        public final int indent;       // spaces before the keyword
        public final boolean blank;
        public final boolean comment;
        public final boolean header;   // the `vow 1 ...` line
        public String key;             // the keyword (unquoted), null for blank, comment and header lines
        public int keyStart;
        public int keyEnd;
        public String value;           // the text after the keyword, null when there is none
        public int valueStart;
        final List<Line> children = new ArrayList<>();
        Line parent;

        Line(int number, int start, int end, int indent, boolean blank, boolean comment, boolean header) {
            this.number = number;
            this.start = start;
            this.end = end;
            this.indent = indent;
            this.blank = blank;
            this.comment = comment;
            this.header = header;
        }

        /** The lines indented under this one. */
        public List<Line> children() {
            return children;
        }

        public Line parent() {
            return parent;
        }

        /** The end offset of this line and everything under it. */
        public int blockEnd() {
            return children.isEmpty() ? end : children.get(children.size() - 1).blockEnd();
        }

        /** The value as one word (an item's id, a name), or null. */
        public String word() {
            if (value == null) {
                return null;
            }
            String v = value.trim();
            return VowScanner.WORD.matcher(v).matches() ? v : null;
        }
    }

    /**
     * Something wrong in the file. An error keeps archvow from reading it; a warning is what archvow
     * refuses for most keywords but accepts for some (an empty item, a repeated item), so the editor
     * does not need a keyword list to say it.
     */
    public record Problem(int line, int start, int end, String message, boolean warning) {
        Problem(int line, int start, int end, String message) {
            this(line, start, end, message, false);
        }
    }

    private final CharSequence text;
    private final List<Line> lines = new ArrayList<>();
    private final List<Line> roots = new ArrayList<>();
    private final List<Problem> problems = new ArrayList<>();
    private String kind;
    private String name;
    private String version;

    private VowDocument(CharSequence text) {
        this.text = text;
    }

    /** Reads one line on its own (not the first line of a file): its keyword and value, offsets in it. */
    public static Line line(CharSequence lineText) {
        VowDocument doc = new VowDocument(lineText);
        int end = lineText.length();
        int i = 0;
        while (i < end && (lineText.charAt(i) == ' ' || lineText.charAt(i) == '\t')) {
            i++;
        }
        boolean blank = i == end;
        boolean comment = !blank && lineText.charAt(i) == '#';
        Line line = new Line(1, 0, end, i, blank, comment, false);
        if (!blank && !comment) {
            doc.readKeyAndValue(line, i);
        }
        return line;
    }

    /** Reads a whole file. Never throws: what cannot be read becomes a problem. */
    public static VowDocument parse(CharSequence text) {
        VowDocument doc = new VowDocument(text);
        doc.readLines();
        doc.buildTree();
        return doc;
    }

    public List<Line> lines() {
        return lines;
    }

    /** The lines at the top level (not indented), in order. */
    public List<Line> roots() {
        return roots;
    }

    public List<Problem> problems() {
        return problems;
    }

    /** {@code project}, {@code component}, {@code proposals}... (null when the first line is missing). */
    public String kind() {
        return kind;
    }

    /** The name on the first line (a component's id), or null. */
    public String name() {
        return name;
    }

    public String version() {
        return version;
    }

    // ------------------------------------------------------------------ lines

    private void readLines() {
        int length = text.length();
        int start = 0;
        int number = 1;
        boolean headerSeen = false;
        while (start <= length) {
            int end = start;
            while (end < length && text.charAt(end) != '\n') {
                end++;
            }
            int lineEnd = end > start && text.charAt(end - 1) == '\r' ? end - 1 : end;
            int i = start;
            while (i < lineEnd && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
                i++;
            }
            boolean blank = i == lineEnd;
            boolean comment = !blank && text.charAt(i) == '#';
            boolean header = !blank && !comment && !headerSeen;
            Line line = new Line(number, start, lineEnd, i - start, blank, comment, header);
            if (!blank && !comment) {
                if (!header && text.subSequence(start, i).toString().contains("\t")) {
                    problems.add(new Problem(number, start, i, "a tab in the indentation - two spaces per level"));
                }
                if (header) {
                    headerSeen = true;
                    readHeader(line, i);
                } else {
                    readKeyAndValue(line, i);
                }
            }
            lines.add(line);
            if (end >= length) {
                break;
            }
            start = end + 1;
            number++;
        }
        if (!headerSeen) {
            problems.add(new Problem(1, 0, Math.min(length, 1),
                    "empty - a vow starts with `vow " + FORMAT_VERSION + " project|component <id>|proposals`"));
        }
    }

    private void readHeader(Line line, int from) {
        String[] words = text.subSequence(from, line.end).toString().trim().split("\\s+");
        if (words.length < 3 || words.length > 4 || !words[0].equals("vow")) {
            problems.add(new Problem(line.number, from, line.end, "not a vow - the first line is `vow "
                    + FORMAT_VERSION + " <kind> [<name>]`, e.g. `vow 1 component clean_orders`"));
            return;
        }
        version = words[1];
        kind = words[2];
        name = words.length == 4 ? words[3] : null;
        if (!version.equals(String.valueOf(FORMAT_VERSION))) {
            problems.add(new Problem(line.number, from, line.end, "format version " + version
                    + " - this editor support reads version " + FORMAT_VERSION + " (update the plugin)"));
        }
    }

    private void readKeyAndValue(Line line, int i) {
        if (line.indent % 2 != 0) {
            problems.add(new Problem(line.number, line.start, i, "indented by " + line.indent
                    + " spaces - two spaces per level"));
        }
        int keyEnd;
        if (text.charAt(i) == '"') {
            int close = closingQuote(i, line.end);
            if (close < 0) {
                problems.add(new Problem(line.number, i, line.end, "a key in quotes with no closing quote"));
                return;
            }
            line.key = VowValues.unquote(text.subSequence(i, close + 1).toString());
            keyEnd = close + 1;
        } else {
            keyEnd = i;
            while (keyEnd < line.end && text.charAt(keyEnd) != ' ' && text.charAt(keyEnd) != '\t') {
                keyEnd++;
            }
            String key = text.subSequence(i, keyEnd).toString();
            if (!VowScanner.KEY.matcher(key).matches()) {
                problems.add(new Problem(line.number, i, keyEnd, "`" + key + "` is not a keyword"));
                return;
            }
            line.key = key;
        }
        line.keyStart = i;
        line.keyEnd = keyEnd;
        if (keyEnd < line.end && text.charAt(keyEnd) != ' ') {
            problems.add(new Problem(line.number, keyEnd, line.end, "a space is needed after `" + line.key + "`"));
            return;
        }
        int v = keyEnd;
        while (v < line.end && text.charAt(v) == ' ') {
            v++;
        }
        if (v < line.end) {
            line.value = text.subSequence(v, line.end).toString().stripTrailing();
            line.valueStart = v;
            String error = VowValues.check(line.value);
            if (error != null) {
                problems.add(new Problem(line.number, v, line.end, error));
            }
        }
    }

    private int closingQuote(int open, int limit) {
        int i = open + 1;
        while (i < limit) {
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

    // ------------------------------------------------------------------ tree

    private void buildTree() {
        List<Line> stack = new ArrayList<>();  // the open blocks, outermost first
        for (Line line : lines) {
            if (line.key == null) {
                continue;  // blank, comment, header or unreadable
            }
            while (!stack.isEmpty() && stack.get(stack.size() - 1).indent >= line.indent) {
                stack.remove(stack.size() - 1);
            }
            Line parent = stack.isEmpty() ? null : stack.get(stack.size() - 1);
            int expected = parent == null ? 0 : parent.indent + 2;
            if (line.indent > expected) {
                problems.add(new Problem(line.number, line.start, line.keyStart,
                        "indented more than the line above allows (" + expected + " spaces here)"));
            }
            line.parent = parent;
            (parent == null ? roots : parent.children).add(line);
            stack.add(line);
        }
        checkBlocks(roots);
    }

    private void checkBlocks(List<Line> siblings) {
        Map<String, Line> seen = new HashMap<>();
        for (Line line : siblings) {
            boolean block = !line.children.isEmpty();
            if (line.value == null && !block) {
                problems.add(new Problem(line.number, line.keyStart, line.keyEnd, "`" + line.key
                        + "` needs a value (or lines under it, indented by two spaces)", true));
            }
            if (line.value != null && block && line.word() == null) {
                Line first = line.children.get(0);
                problems.add(new Problem(first.number, first.start, first.end, "`" + line.key
                        + "` has a value: nothing may be indented under it (only an item's id or name may"
                        + " stand on its line, e.g. `hazard H1`)"));
            }
            if (!block) {  // items (`decision 1`, `hazard H1`...) repeat; a plain key appears once
                Line before = seen.putIfAbsent(line.key, line);
                if (before != null) {
                    problems.add(new Problem(line.number, line.keyStart, line.keyEnd, "`" + line.key
                            + "` appears twice (line " + before.number + ")", true));
                }
            }
            checkBlocks(line.children);
        }
    }
}
