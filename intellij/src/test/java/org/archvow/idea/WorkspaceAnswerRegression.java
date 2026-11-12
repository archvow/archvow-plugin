package org.archvow.idea;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 0.21.0: the panel loads a project with `archvow workspace --json` (archvow 1.25, contract 1.1; packs since 1.26, contract 1.2). On the contract's
 * real answers (../contract/cli/<example>/workspace.json), WorkspaceAnswer gives exactly what show, show <id>, next,
 * check, brief show and catalog print on their own; `unchanged` and a failure are told apart from an answer. VS Code
 * checks the same (model.test.ts).
 */
public final class WorkspaceAnswerRegression {
    private static final Path CLI = Path.of("..", "contract", "cli");

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static String text(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** What a command printed alone, without its "contract" key (the answer has one, at its top). */
    private static JsonObject alone(Path path) throws Exception {
        JsonObject object = JsonParser.parseString(text(path)).getAsJsonObject();
        object.remove("contract");
        return object;
    }

    public static void main(String[] args) throws Exception {
        int read = 0;
        for (String example : List.of("python-app", "fastapi-service", "pyspark-pipeline")) {
            Path folder = CLI.resolve(example);
            WorkspaceAnswer answer = WorkspaceAnswer.read(text(folder.resolve("workspace.json")));
            check(answer != null && !answer.unchanged, example + ": not read");
            check("<digest>".equals(answer.digest), example + ": digest");
            check(answer.spec.equals(alone(folder.resolve("show.json"))), example + ": the spec is not show --json");
            check(answer.check.equals(alone(folder.resolve("check.json"))), example + ": the findings are not check --json");
            JsonElement next = JsonParser.parseString(text(folder.resolve("next.json")));
            check(JsonParser.parseString(answer.next).equals(next), example + ": the plan is not next --json");
            Set<String> shown = new TreeSet<>();
            try (var files = Files.list(folder.resolve("show"))) {
                for (Path file : files.toList()) {
                    String id = file.getFileName().toString().replaceFirst("\\.json$", "");
                    shown.add(id);
                    check(alone(file).equals(answer.snapshots.get(id)), example + ": " + id + " is not show " + id + " --json");
                }
            }
            check(shown.equals(new TreeSet<>(answer.snapshots.keySet())), example + ": components " + answer.snapshots.keySet());
            check(answer.briefs != null && answer.briefs.has("files"), example + ": briefs");
            check(ArchvowInitCatalog.hasTemplates(answer.catalog), example + ": catalog");
            check(answer.packs != null && answer.packs.equals(JsonParser.parseString(text(folder.resolve("workspace.json")))
                .getAsJsonObject().get("packs")), example + ": packs");
            check(answer.firstSteps.equals(JsonParser.parseString(text(folder.resolve("workspace.json")))
                .getAsJsonObject().get("first_steps")) && answer.firstSteps.size() == 5, example + ": first steps (contract 2.1)");
            read++;
        }
        WorkspaceAnswer same = WorkspaceAnswer.read("{\"contract\": \"1.2\", \"unchanged\": true, \"digest\": \"4f2a\"}");
        check(same != null && same.unchanged && "4f2a".equals(same.digest), "unchanged");
        String failure = JsonParser.parseString(text(CLI.resolve("errors").resolve("no-spec.json"))).getAsJsonObject().get("stdout").getAsString();
        check(WorkspaceAnswer.read(failure) == null, "the error envelope read as an answer");
        check(WorkspaceAnswer.read("{\"digest\": \"4f2a\", \"project\": {}}") == null, "a part missing");
        check(WorkspaceAnswer.read("") == null && WorkspaceAnswer.read(null) == null, "nothing");
        System.out.println("OK: workspace --json read on " + read + " examples");
    }
}
