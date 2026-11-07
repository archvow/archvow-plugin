package org.archvow.idea;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

public final class ResultFormatterRegression {
    public static void main(String[] args) {
        String empty = ArchvowResultFormatter.render("next",0,"",false,"python -m archvow next",false,false);
        if (!empty.contains("No output") || !empty.contains("Completed"))
            throw new AssertionError("Empty successful result missing friendly summary");
        String failure = ArchvowResultFormatter.render("show",1,"No module named archvow",false,"python -m archvow show",true,true);
        if (!failure.contains("n&#39;est pas installé") || !failure.contains("Échec"))
            throw new AssertionError("Module missing guidance lost");
        String escaped = ArchvowResultFormatter.render("check",2,"<bad> & unsafe",false,"python -m archvow check",false,false);
        if (!escaped.contains("&lt;bad&gt; &amp; unsafe"))
            throw new AssertionError("Unsanitized output");
        String report = "{\"errors\":1,\"warnings\":0,\"ok\":false,"
            + "\"violations\":[{\"code\":\"ARCH008\",\"severity\":\"error\","
            + "\"message\":\"missing\",\"file\":\".archvow/spec.vow\"}]}";
        String location = ArchvowResultFormatter.render("full check",1,report,false,"check --complete --json",false,false);
        if (!location.contains(ArchvowHtmlFileIcons.imageSource(".archvow/spec.vow", 0, false)))
            throw new AssertionError("Check location does not embed the original Files component");
        String next = ArchvowResultFormatter.render("next", 0,
            "[{\"component\":\"config\",\"readiness\":\"blocked: 2 decisions to make\",\"tokens\":659}]",
            false, "archvow next --json", false, true);
        if (!next.contains("config") || !next.contains("&#9679;") || !next.contains("659 tokens")
            || !next.contains("ARCHVOW &#183; RESULT"))
            throw new AssertionError("Next result card lost its status, token details or icon");
        String audit = ArchvowResultFormatter.render("audit", 0,
            "[{\"kind\":\"decision\",\"ref\":\"R1\",\"text\":\"Accept? -> yes\",\"flags\":[\"review\"]}]",
            false, "archvow audit --json", false, false);
        if (!audit.contains("R1") || !audit.contains("Accept?"))
            throw new AssertionError("Audit result was lost after card redesign");
        String raw = ArchvowResultFormatter.renderRaw("message", false, true);
        if (!raw.contains("Raw output") || raw.contains("&#9679;"))
            throw new AssertionError("Raw output must stay unchanged");
        // Every registered CLI result flows through this formatter.  Use a generic
        // sample to verify both palettes and languages, while the representative
        // results above verify the specialized schemas and Files rendering.
        Path contract = Path.of(System.getProperty("archvow.contract.root", "../contract"));
        JsonArray commands;
        try {
            commands = JsonParser.parseString(Files.readString(contract.resolve("cli/commands.json")))
                .getAsJsonObject().getAsJsonArray("commands");
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read the shared command contract", ex);
        }
        int total = 0;
        for (JsonElement item : commands) {
            JsonObject command = item.getAsJsonObject();
            StringBuilder label = new StringBuilder();
            for (JsonElement name : command.getAsJsonArray("path")) {
                if (label.length() > 0) label.append(' ');
                label.append(name.getAsString());
            }
            for (boolean fr : new boolean[] {false, true}) {
                for (boolean dark : new boolean[] {false, true}) {
                    String body = ArchvowResultFormatter.render(label.toString(), 0,
                        "{\"detail\":\"unchanged value\"}", false, label.toString(), fr, dark);
                    if (!body.contains("&#9670;") || !body.contains("ARCHVOW &#183; RESULT")
                        || !body.contains("Exit code: ") && !body.contains("Code retour : "))
                        throw new AssertionError("Missing shared result frame for " + label);
                    if (!label.toString().equals("next") && !label.toString().equals("catalog")
                        && !label.toString().equals("prompt") && !body.contains("unchanged value"))
                        throw new AssertionError("Lost result details for " + label);
                }
            }
            total++;
        }
        if (total < 49) throw new AssertionError("Missing registered commands: " + total);
        System.out.println("OK: result cards for check, next and audit; raw output unchanged; "
            + total + " commands in EN/FR and light/dark");
    }
}
