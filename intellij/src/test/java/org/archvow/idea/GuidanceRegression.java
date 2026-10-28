package org.archvow.idea;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every diagnostic says what to do (ArchvowGuidance), on every case, and exactly as in VS Code: the
 * guidance of each real message archvow prints (../contract/cli/messages.json, from archvow's own tests)
 * must equal ../contract/guidance/expected.json, which the VS Code extension's guidance.ts writes and
 * checks (vscode/src/test/guidance.test.ts). Every code archvow documents gets its meaning and what to do.
 */
public final class GuidanceRegression {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static String resource(String name) throws Exception {
        try (InputStream in = GuidanceRegression.class.getResourceAsStream(name)) {
            if (in == null) throw new AssertionError(name + " not on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static JsonObject json(ArchvowGuidance.Guidance g) {
        JsonObject o = new JsonObject();
        o.addProperty("code", g.code());
        o.addProperty("meaning", g.meaning());
        o.addProperty("todo", g.todo());
        o.addProperty("who", g.who());
        o.addProperty("blocking", g.blocking());
        o.addProperty("then", g.then());
        if (g.command() == null) o.add("command", JsonNull.INSTANCE);
        else {
            JsonObject c = new JsonObject();
            c.addProperty("name", g.command().name());
            JsonObject values = new JsonObject();
            for (Map.Entry<String, String> e : g.command().values().entrySet()) values.addProperty(e.getKey(), e.getValue());
            c.add("values", values);
            c.addProperty("text", g.command().text());
            JsonArray missing = new JsonArray();
            g.command().missing().forEach(missing::add);
            c.add("missing", missing);
            c.addProperty("readOnly", g.command().readOnly());
            o.add("command", c);
        }
        if (g.file() == null) o.add("file", JsonNull.INSTANCE);
        else {
            JsonObject f = new JsonObject();
            f.addProperty("path", g.file().path());
            f.addProperty("line", g.file().line());
            o.add("file", f);
        }
        return o;
    }

    public static void main(String[] args) throws Exception {
        Path contract = Path.of(System.getProperty("archvow.contract.root", "../contract"));
        String en = resource("/archvow/rules.md"), fr = resource("/archvow/rules.fr.md");
        var commands = ArchvowGrammar.COMMANDS;

        // every documented code: its meaning and what to do, in both languages
        Matcher codes = Pattern.compile("(?m)^\\| ([A-Z]{2,12}\\d{3}) \\|").matcher(en);
        int documented = 0;
        while (codes.find()) {
            documented++;
            for (boolean french : new boolean[] {false, true}) {
                var g = ArchvowGuidance.guide(codes.group(1), "error", "", "", 0, french ? fr : en, commands, french);
                check(!g.meaning().isBlank() && !g.meaning().startsWith("archvow found this") && !g.meaning().startsWith("archvow a trouvé"),
                    codes.group(1) + ": no meaning");
                check(g.todo().length() > 10, codes.group(1) + ": no \"what to do\"");
            }
        }
        check(documented >= 80, documented + " documented codes");

        // the two cases of the screenshot
        var upgrade = ArchvowGuidance.guide("ARCH012", "warning", "the vow says pyspark-pipeline@1.11.0, installed is "
            + "pyspark-pipeline@1.17.0: to adopt it, archvow init acoss_pipeline2 --force (then review and commit)", ".archvow/spec.vow", 0, en, commands, false);
        check(upgrade.command() != null && upgrade.command().name().equals("init")
            && upgrade.command().values().equals(Map.of("package", "acoss_pipeline2", "force", "true")), "ARCH012 prepares init --force: " + upgrade.command());
        check(upgrade.who().startsWith("An architect"), upgrade.who());
        var unseen = ArchvowGuidance.guide("ARCH053", "warning", "written by copilot, which archvow cannot see in JetBrains IDEs "
            + "(the Copilot plugin sets no variable archvow knows): nobody checked how it ran (sandbox, YOLO mode)", "x.py", 0, en, commands, false);
        check(unseen.todo().startsWith("Review what copilot wrote, then commit it.") && unseen.command() == null, unseen.todo());

        // exactly VS Code's guidance, on every real message, in English and in French
        JsonArray messages = JsonParser.parseString(Files.readString(contract.resolve("cli/messages.json"))).getAsJsonObject().getAsJsonArray("messages");
        JsonArray expected = JsonParser.parseString(Files.readString(contract.resolve("guidance/expected.json"))).getAsJsonArray();
        check(expected.size() == messages.size() * 2, "expected.json is not of these messages: UPDATE_GUIDANCE=1 npm test");
        for (int i = 0; i < messages.size(); i++) {
            JsonObject m = messages.get(i).getAsJsonObject();
            for (int k = 0; k < 2; k++) {
                boolean french = k == 1;
                JsonObject want = expected.get(2 * i + k).getAsJsonObject();
                check(want.get("message").getAsString().equals(m.get("message").getAsString()) && want.get("fr").getAsBoolean() == french,
                    "expected.json is not in the order of messages.json at " + i);
                var g = ArchvowGuidance.guide(m.get("code").getAsString(), m.get("severity").getAsString(), m.get("message").getAsString(),
                    m.get("file").getAsString(), m.get("line").getAsInt(), french ? fr : en, commands, french);
                JsonElement got = json(g);
                if (!got.equals(want.get("guide")))
                    throw new AssertionError("not VS Code's guidance (" + (french ? "fr" : "en") + "): " + m.get("message").getAsString()
                        + "\n  IntelliJ: " + got + "\n  VS Code:  " + want.get("guide"));
            }
        }
        System.out.println("OK: guidance on " + messages.size() + " real messages identical to VS Code's (en, fr), "
            + documented + " documented codes explained");
    }
}
