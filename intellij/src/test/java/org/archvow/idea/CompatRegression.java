package org.archvow.idea;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 1.0.0: what the plugin says of the installed archvow - its range (../contract/archvow-range.txt), the contract it
 * speaks, the archvow it is tested with, the project's pin - is VS Code's on every case of
 * ../contract/compat/expected.json (written by UPDATE_COMPAT=1 npm test): the state, the sentence (EN and FR) and the
 * version Install puts (none over a pin outside the range).
 */
public final class CompatRegression {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    public static void main(String[] args) throws Exception {
        Path contract = Path.of(System.getProperty("archvow.contract.root", "../contract"));
        ArchvowCompat.Range range = ArchvowCompat.parseRange(Files.readString(contract.resolve("archvow-range.txt")));
        check(range != null && range.text().equals(ArchvowCompat.RANGE_OF_PLUGIN.text()), "the packaged range is contract/archvow-range.txt");
        JsonObject file = JsonParser.parseString(Files.readString(contract.resolve("compat/expected.json"))).getAsJsonObject();
        int count = 0;
        for (JsonElement element : file.getAsJsonArray("cases")) {
            JsonObject c = element.getAsJsonObject();
            ArchvowVersion.Said installed = c.get("installed").isJsonNull() ? null
                : new ArchvowVersion.Said(str(c.getAsJsonObject("installed"), "version"), str(c.getAsJsonObject("installed"), "contract"));
            ArchvowCompat.Pin pin = c.get("pin").isJsonNull() ? null
                : new ArchvowCompat.Pin(str(c.getAsJsonObject("pin"), "version"), str(c.getAsJsonObject("pin"), "file"));
            ArchvowCompat.Compatibility got = ArchvowCompat.of(installed, range, str(c, "wanted"), str(c, "tested"), pin, c.get("fr").getAsBoolean());
            JsonObject want = c.getAsJsonObject("expected");
            String name = str(c, "name");
            check(got.state().equals(str(want, "state")), name + ": state " + got.state());
            check(got.text().equals(str(want, "text")), name + ": text\n  " + got.text() + "\n  " + str(want, "text"));
            check(got.install().equals(str(want, "install")), name + ": install " + got.install());
            count++;
        }
        check(count >= 10, "cases");
        check(ArchvowCompat.pin(List.<String[]>of(new String[] {"requirements.txt", "pytest\narchvow==2.8.0\n"})).equals(new ArchvowCompat.Pin("2.8.0", "requirements.txt")), "pin");
        check(ArchvowCompat.pin(List.<String[]>of(new String[] {"pyproject.toml", "dev = [\"archvow==3.0.1\", \"ruff\"]"})).equals(new ArchvowCompat.Pin("3.0.1", "pyproject.toml")), "pin, pyproject");
        check(ArchvowCompat.pin(List.<String[]>of(new String[] {"requirements.txt", "archvow>=3"})) == null, "no pin");
        System.out.println("OK: " + count + " cases as VS Code says them, range " + range.text());
    }
}
