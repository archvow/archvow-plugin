package org.archvow.idea;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The command forms offer what exists (ArchvowChoices), exactly as in VS Code: on archvow's three
 * examples (../contract/cli), in English and in French, the book of every command equals
 * ../contract/choices/expected.json, which the VS Code extension's choices.ts writes and checks
 * (vscode/src/test/choices.test.ts); so do the comparison base, the refused base and the ref names.
 */
public final class ChoicesRegression {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static JsonObject read(Path file) throws Exception {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static List<String> strings(JsonElement array) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : array.getAsJsonArray()) out.add(e.getAsString());
        return out;
    }

    public static void main(String[] args) throws Exception {
        Path contract = Path.of(System.getProperty("archvow.contract.root", "../contract"));
        Path cli = contract.resolve("cli");
        JsonObject expected = read(contract.resolve("choices/expected.json"));
        List<String> refs = strings(expected.get("refs"));
        String base = ArchvowChoices.bestBase(refs, expected.get("current").getAsString(), expected.get("gitflow").getAsBoolean(),
            expected.get("remoteHead").getAsString());
        JsonObject catalog = read(cli.resolve("catalog.json")), kinds = read(cli.resolve("kinds.json"));
        int entries = 0;
        for (JsonElement e : expected.getAsJsonArray("entries")) {
            JsonObject want = e.getAsJsonObject();
            String example = want.get("example").getAsString();
            boolean fr = want.get("fr").getAsBoolean();
            Map<String, JsonObject> snapshots = new LinkedHashMap<>();
            try (Stream<Path> files = Files.list(cli.resolve(example).resolve("show"))) {
                for (Path f : files.sorted().toList())
                    if (f.toString().endsWith(".json")) snapshots.put(f.getFileName().toString().replaceAll("\\.json$", ""), read(f));
            }
            var book = ArchvowChoices.book(ArchvowGrammar.COMMANDS, new ArchvowChoices.Input(read(cli.resolve(example).resolve("show.json")),
                snapshots, catalog, kinds, refs, base, fr));
            JsonObject got = ArchvowChoices.json(book);
            JsonObject book0 = want.getAsJsonObject("book");
            for (String command : book0.keySet()) {
                check(got.has(command), example + (fr ? " fr" : "") + ": no form for " + command);
                for (String field : book0.getAsJsonObject(command).keySet()) {
                    JsonElement w = book0.getAsJsonObject(command).get(field), g = got.getAsJsonObject(command).get(field);
                    if (!w.equals(g)) throw new AssertionError("not VS Code's choices (" + example + (fr ? ", fr" : "") + ", " + command + " "
                        + field + ")\n  IntelliJ: " + g + "\n  VS Code:  " + w);
                }
            }
            check(got.keySet().equals(book0.keySet()), example + ": other forms than VS Code's: " + got.keySet() + " vs " + book0.keySet());
            entries++;
        }
        check(entries == 6, entries + " entries");

        for (JsonElement e : expected.getAsJsonArray("bases")) {
            JsonObject b = e.getAsJsonObject();
            String got = ArchvowChoices.bestBase(strings(b.get("refs")), b.get("current").getAsString(), b.get("gitflow").getAsBoolean(),
                b.get("head").getAsString());
            check(got.equals(b.get("base").getAsString()), "base " + b + ": " + got);
        }
        for (JsonElement e : expected.getAsJsonArray("missing")) {
            JsonObject m = e.getAsJsonObject();
            String got = ArchvowChoices.missingRef(m.get("ref").getAsString(), strings(m.get("refs")), m.get("fr").getAsBoolean());
            check(got.equals(m.get("message").getAsString()), "missing " + m + ":\n  " + got);
        }
        List<String> names = ArchvowChoices.refNames("refs/heads/main\nrefs/heads/feature/a\nrefs/remotes/origin/HEAD\nrefs/remotes/origin/main\nrefs/tags/v1\n");
        check(names.equals(strings(expected.get("names"))), "ref names " + names);
        check(ArchvowChoices.choicesNow(null, Map.of()).isEmpty(), "no field, no choice");
        System.out.println("OK: the command forms offer what VS Code's offer, on " + entries + " examples (en, fr); bases and refused bases identical");
    }
}
