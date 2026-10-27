package org.archvow.vow.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Tests of the plain-Java core (no IntelliJ, no test framework): {@code ./gradlew coreTest}, or
 * {@code java CoreTests <folder with .vow files>...}. Exits with 1 on the first failure.
 */
public final class CoreTests {
    private static int checks;

    public static void main(String[] args) throws IOException {
        List<Path> files = new ArrayList<>();
        for (String arg : args.length > 0 ? args : new String[] {"../contract/vows"}) {
            try (Stream<Path> walk = Files.walk(Path.of(arg))) {
                walk.filter(p -> p.toString().endsWith(".vow")).sorted().forEach(files::add);
            }
        }
        check(!files.isEmpty(), "no .vow file found in " + String.join(", ", args));
        for (Path file : files) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            scannerCoversEverything(file.toString(), text);
            scannerRestartsAnywhere(file.toString(), text);
            keywordsFollowTheirIndent(file.toString(), text);
            VowDocument doc = VowDocument.parse(text);
            check(doc.problems().isEmpty(), file + ": " + doc.problems());
            check(doc.kind() != null, file + ": no kind");
        }
        headerAndTokens();
        problems();
        links();
        tree();
        System.out.println("core tests: " + checks + " checks passed on " + files.size() + " vow files");
    }

    /** Every keyword has the kind of its depth: its column / 2, 3 and deeper together. */
    static void keywordsFollowTheirIndent(String label, String text) {
        VowScanner s = new VowScanner();
        s.start(text, 0, text.length(), 0);
        java.util.Set<VowTokenKind> keys = java.util.EnumSet.of(
                VowTokenKind.KEY_0, VowTokenKind.KEY_1, VowTokenKind.KEY_2, VowTokenKind.KEY_3);
        while (s.kind() != null) {
            if (keys.contains(s.kind())) {
                int column = s.tokenStart() - (text.lastIndexOf('\n', s.tokenStart() - 1) + 1);
                check(s.kind() == VowScanner.depthKind(column / 2), label + ": keyword at column " + column
                        + " is " + s.kind());
            }
            s.advance();
        }
    }

    static void scannerCoversEverything(String label, String text) {
        VowScanner s = new VowScanner();
        s.start(text, 0, text.length(), 0);
        int at = 0;
        while (s.kind() != null) {
            check(s.tokenStart() == at, label + ": a gap before offset " + s.tokenStart());
            check(s.tokenEnd() > s.tokenStart(), label + ": an empty token at " + at);
            at = s.tokenEnd();
            s.advance();
        }
        check(at == text.length(), label + ": stopped at " + at + " of " + text.length());
    }

    /** An editor restarts the scanner at any token, with that token's state: the tokens must not change. */
    static void scannerRestartsAnywhere(String label, String text) {
        List<int[]> all = tokens(text, 0, 0);
        for (int i = 0; i < all.size(); i += Math.max(1, all.size() / 50)) {
            int[] t = all.get(i);
            List<int[]> again = tokens(text, t[0], t[3]);
            for (int j = 0; j < again.size(); j++) {
                int[] a = all.get(i + j);
                int[] b = again.get(j);
                check(a[0] == b[0] && a[1] == b[1] && a[2] == b[2],
                        label + ": restart at " + t[0] + " gives another token at " + b[0]);
            }
        }
    }

    private static List<int[]> tokens(String text, int start, int state) {
        VowScanner s = new VowScanner();
        s.start(text, start, text.length(), state);
        List<int[]> out = new ArrayList<>();
        while (s.kind() != null) {
            out.add(new int[] {s.tokenStart(), s.tokenEnd(), s.kind().ordinal(), s.state()});
            s.advance();
        }
        return out;
    }

    static void headerAndTokens() {
        String text = "vow 1 component clean_orders\n# hi\nuses [orders_schema, \"x y\"]\nhazard H1\n"
                + "  never \"A row is lost\"\n  by alice@acme.com\n  on 2026-10-21T10:00:00+00:00\n"
                + "  prevents [clean_orders.H2]\nflags [true, null, 3.5]\nrules {}\nodd = [[1]]\n";
        List<VowTokenKind> kinds = new ArrayList<>();
        VowScanner s = new VowScanner();
        s.start(text, 0, text.length(), 0);
        while (s.kind() != null) {
            if (s.kind() != VowTokenKind.WHITE_SPACE && s.kind() != VowTokenKind.NEWLINE
                    && s.kind() != VowTokenKind.INDENT) {
                kinds.add(s.kind());
            }
            s.advance();
        }
        List<VowTokenKind> expected = List.of(
                VowTokenKind.HEADER_KEYWORD, VowTokenKind.HEADER_VERSION, VowTokenKind.HEADER_KIND,
                VowTokenKind.HEADER_NAME, VowTokenKind.COMMENT,
                VowTokenKind.KEY_0, VowTokenKind.LBRACKET, VowTokenKind.WORD, VowTokenKind.COMMA,
                VowTokenKind.STRING, VowTokenKind.RBRACKET,
                VowTokenKind.KEY_0, VowTokenKind.ID,
                VowTokenKind.KEY_1, VowTokenKind.STRING,
                VowTokenKind.KEY_1, VowTokenKind.PERSON,
                VowTokenKind.KEY_1, VowTokenKind.DATE,
                VowTokenKind.KEY_1, VowTokenKind.LBRACKET, VowTokenKind.ID, VowTokenKind.RBRACKET,
                VowTokenKind.KEY_0, VowTokenKind.LBRACKET, VowTokenKind.LITERAL, VowTokenKind.COMMA,
                VowTokenKind.LITERAL, VowTokenKind.COMMA, VowTokenKind.NUMBER, VowTokenKind.RBRACKET,
                VowTokenKind.KEY_0, VowTokenKind.EMPTY_GROUP,
                VowTokenKind.KEY_0, VowTokenKind.JSON_MARK, VowTokenKind.JSON);
        check(kinds.equals(expected), "tokens: " + kinds);
        VowScanner quoted = new VowScanner();
        String keys = "vow 1 project x\n\"a b\" 1\nrules\n  \"c d\" 2\n  scope\n    e 3\n      f 4\n        g 5\n";
        quoted.start(keys, 0, keys.length(), 0);
        List<VowTokenKind> keyKinds = new ArrayList<>();
        while (quoted.kind() != null) {
            if (quoted.kind().name().startsWith("KEY_")) {
                keyKinds.add(quoted.kind());
            }
            quoted.advance();
        }
        check(keyKinds.equals(List.of(VowTokenKind.KEY_0, VowTokenKind.KEY_0, VowTokenKind.KEY_1, VowTokenKind.KEY_1,
                VowTokenKind.KEY_2, VowTokenKind.KEY_3, VowTokenKind.KEY_3)), "keywords by depth: " + keyKinds);
        check(VowDocument.parse(text).problems().isEmpty(), "valid sample: " + VowDocument.parse(text).problems());
    }

    static void problems() {
        String[][] cases = {
                {"", "empty"},
                {"version: 1\n", "not a vow"},
                {"vow 2 project\n", "format version 2"},
                {"vow 1 project\nrules\n   ARCH022 {}\n", "indented by 3 spaces"},
                {"vow 1 project\nrules\n\tARCH022 {}\n", "a tab in the indentation"},
                {"vow 1 project\nmode strict\nmode free\n", "`mode` appears twice (line 2)"},
                {"vow 1 project\nsystem\n", "`system` needs a value"},
                {"vow 1 project\ndescription two words\n", "`two words`: put text with spaces"},
                {"vow 1 project\nmode \"strict\"\n  x 1\n", "`mode` has a value"},
                {"vow 1 project\nai_tools [a b]\n", "list values are separated by commas"},
                {"vow 1 project\nai_tools [a,]\n", "a comma with no value after it"},
                {"vow 1 project\nai_tools [a\n", "a list starts with [ and ends with ]"},
                {"vow 1 project\ndescription \"open\n", "text in quotes must be the whole value"},
                {"vow 1 project\nsystem\n    name x\n", "indented more than the line above allows"},
                {"vow 1 project\n-bad 1\n", "`-bad` is not a keyword"},
                {"vow 1 project\n\"open 1\n", "a key in quotes with no closing quote"},
                {"vow 1 project\nodd =\n", "`=` needs JSON after it"},
        };
        for (String[] c : cases) {
            List<VowDocument.Problem> found = VowDocument.parse(c[0]).problems();
            check(found.stream().anyMatch(p -> p.message().contains(c[1])),
                    "expected `" + c[1] + "` for " + c[0].replace("\n", "\\n") + ", got " + found);
        }
        String ok = "vow 1 component c\nhazard H1\n  never \"x\"\nhazard H2\n  never \"y\"\nproposal\n  number 1\n"
                + "proposal\n  number 2\n\"decision\" x\n";
        check(VowDocument.parse(ok).problems().isEmpty(), "items repeat: " + VowDocument.parse(ok).problems());
    }

    static void links() {
        String text = "vow 1 component c\nmodule acme.p1_domain.c\nuses [a, b]\ninvariant INV1\n  prevents [H1, d.H2]\n";
        VowDocument doc = VowDocument.parse(text);
        List<VowLinks.Link> links = new ArrayList<>();
        doc.lines().forEach(l -> links.addAll(VowLinks.of(l)));
        check(links.size() == 5, "links: " + links);
        for (VowLinks.Link link : links) {
            check(text.substring(link.start(), link.end()).equals(link.target()), "link offsets: " + link);
        }
        check(VowLinks.moduleFiles("acme.p1_domain.c").get(0).equals("src/acme/p1_domain/c.py"), "module file");
        check("d".equals(VowLinks.hazardComponent("d.H2")) && VowLinks.hazardComponent("H1") == null, "hazard scope");
    }

    static void tree() {
        String text = "vow 1 project\nsystem\n  name x\n  description \"d\"\n\n# --- rules\nrule R1\n  text \"t\"\n"
                + "  scope\n    components [a]\nmode strict\n";
        VowDocument doc = VowDocument.parse(text);
        check(doc.roots().size() == 3, "roots: " + doc.roots().size());
        VowDocument.Line rule = doc.roots().get(1);
        check(rule.key.equals("rule") && "R1".equals(rule.word()) && rule.children().size() == 2, "rule block");
        check(text.substring(rule.start, rule.blockEnd()).endsWith("components [a]"), "block end");
        check(rule.children().get(1).children().get(0).parent() == rule.children().get(1), "parents");
    }

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) {
            System.out.println("FAILED: " + message);
            System.exit(1);
        }
    }
}
