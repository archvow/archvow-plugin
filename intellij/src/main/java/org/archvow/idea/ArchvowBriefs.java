package org.archvow.idea;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The briefs folders of archvow 1.17 (0.12.0): the init answer {@code briefs} lists the folders the spec is
 * drawn from with --from (default {@code briefs}; a Spec Kit {@code specs} folder, say). The plugin offers
 * their files and headings in the --from field, opens "Drawn from" at its heading, and turns archvow's
 * refusal of a file outside them into the architect's init --force form, prefilled (shown, then
 * confirmed). archvow decides; the plugin only reads. The same rules as archvow's briefs.py and VS Code's
 * {@code src/briefs.ts}. No IntelliJ class here: tested headless ({@code BriefsRegression}).
 */
final class ArchvowBriefs {
    static final String BRIEF_LINK = "brief:";
    static final String INIT_LINK = "archvow-init:";
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*\\s*$");
    private static final Pattern ADD_FOLDER = Pattern.compile("archvow init (\\S+) --force -a briefs=(\\S+)");
    private static final Pattern ACCEPT = Pattern.compile("archvow\\s+brief\\s+accept\\s+(\\S+)");

    record Section(String slug, String title, int line) {}
    record Brief(String file, List<Section> sections) {}
    record AddFolder(String pkg, String answer) {
        /** The init form's values: the package, --force, the answer (the form shows them, a person confirms). */
        Map<String, String> values() {
            Map<String, String> out = new LinkedHashMap<>();
            out.put("package", pkg);
            out.put("force", "true");
            out.put("answer", answer);
            return out;
        }
        String command() { return "archvow init " + pkg + " --force -a " + answer; }
    }

    private ArchvowBriefs() { }

    /** The project's briefs folders (show --json init_answers.briefs; {@code briefs} when unset). */
    static List<String> folders(JsonObject spec) {
        JsonObject answers = spec != null && spec.has("init_answers") && spec.get("init_answers").isJsonObject()
            ? spec.getAsJsonObject("init_answers") : new JsonObject();
        JsonElement value = answers.get("briefs");
        if (value == null || value.isJsonNull()) return List.of("briefs");
        List<String> items = new ArrayList<>();
        if (value.isJsonArray()) {
            for (JsonElement e : value.getAsJsonArray()) items.add(e.getAsString());
        } else {
            items.addAll(List.of(value.getAsString().split(",", -1)));
        }
        Set<String> found = new LinkedHashSet<>();
        for (String item : items) {
            String f = item.trim().replaceAll("^/+|/+$", "");
            if (!f.isEmpty()) found.add(f);
        }
        return List.copyOf(found);
    }

    /** A heading as its anchor, as archvow: lower case, no accents, words joined by '-'. */
    static String slug(String heading) {
        String ascii = Normalizer.normalize(heading, Normalizer.Form.NFKD).replaceAll("[^\\x00-\\x7F]", "");
        return ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    /** The headings of a Markdown text as anchors, repeated ones as -2, -3... (as GitLab and GitHub). */
    static List<Section> sections(String text) {
        List<Section> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String[] lines = text.split("\\r?\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher m = HEADING.matcher(lines[i]);
            if (!m.matches()) continue;
            String key = slug(m.group(2));
            if (key.isEmpty()) key = "section";
            String unique = key;
            for (int k = 2; seen.contains(unique); k++) unique = key + "-" + k;
            seen.add(unique);
            out.add(new Section(unique, m.group(2), i + 1));
        }
        return out;
    }

    /** The declared folder a file is in, or "". */
    static String folderOf(String file, List<String> declared) {
        String f = file.replace('\\', '/');
        for (String d : declared) if (f.equals(d) || f.startsWith(d + "/")) return d;
        return "";
    }

    /** Every Markdown or text file of the declared folders, with its headings (Markdown only). */
    static List<Brief> files(String root, List<String> declared) {
        List<Brief> out = new ArrayList<>();
        if (root == null) return out;
        Path base = Path.of(root);
        for (String folder : declared) {
            Path dir = base.resolve(folder);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> walk = Files.walk(dir)) {
                for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile).sorted()::iterator) {
                    String name = p.getFileName().toString();
                    if (!name.endsWith(".md") && !name.endsWith(".txt")) continue;
                    if (out.size() >= 500) break;
                    String text = "";
                    if (name.endsWith(".md")) {
                        try { text = Files.readString(p, StandardCharsets.UTF_8); } catch (IOException | RuntimeException e) { text = ""; }
                    }
                    out.add(new Brief(base.relativize(p).toString().replace('\\', '/'), sections(text)));
                }
            } catch (IOException e) {
                // an unreadable folder offers no file
            }
        }
        out.sort((a, b) -> a.file().compareTo(b.file()));
        return out;
    }

    /** What the --from field offers: each file, then each of its headings as file#section. */
    static List<String> choices(List<Brief> briefs) {
        List<String> out = new ArrayList<>();
        for (Brief b : briefs) {
            out.add(b.file());
            for (Section s : b.sections()) out.add(b.file() + "#" + s.slug());
        }
        return out;
    }

    /** "specs/x.md#section" -> the 1-based line of that heading in {@code briefs} (1 without). */
    static int line(List<Brief> briefs, String ref) {
        String[] parts = ref.split("#", 2);
        for (Brief b : briefs) {
            if (!b.file().equals(parts[0]) || parts.length < 2) continue;
            for (Section s : b.sections()) if (s.slug().equals(parts[1])) return s.line();
        }
        return 1;
    }

    /** The {@code source} of a show --json view ({file, section}) as "file#section", "file", or "". */
    static String drawnFrom(JsonObject view) {
        if (view == null || !view.has("source") || !view.get("source").isJsonObject()) return "";
        JsonObject source = view.getAsJsonObject("source");
        String file = text(source, "file");
        String section = text(source, "section");
        return file.isEmpty() ? "" : section.isEmpty() ? file : file + "#" + section;
    }

    /** "Drawn from: <link>" for the component's head; "" when it is drawn from no brief. */
    static String drawnFromHtml(JsonObject view, boolean french) {
        String ref = drawnFrom(view);
        if (ref.isEmpty()) return "";
        return "<p style='color:#6B7280'>" + (french ? "Tiré de" : "Drawn from") + ": <a href='" + BRIEF_LINK
            + URLEncoder.encode(ref, StandardCharsets.UTF_8) + "'>" + escape(ref) + "</a></p>";
    }

    /** The brief a {@code brief:} link opens, or "". */
    static String briefOf(String link) {
        return link != null && link.startsWith(BRIEF_LINK)
            ? URLDecoder.decode(link.substring(BRIEF_LINK.length()), StandardCharsets.UTF_8) : "";
    }

    /** archvow's way to add a briefs folder, in its refusal of --from or in ARCH039; null without. */
    static AddFolder addFolder(String message) {
        Matcher m = ADD_FOLDER.matcher(message == null ? "" : message);
        return m.find() ? new AddFolder(m.group(1), "briefs=" + m.group(2)) : null;
    }

    /** The brief ARCH039 asks to accept once reviewed ("archvow brief accept <ref>"), or "". */
    static String acceptRef(String message) {
        Matcher m = ACCEPT.matcher(message == null ? "" : message);
        return m.find() ? m.group(1) : "";
    }

    /** Under a result that says so: the link that opens the architect's init --force form, prefilled. */
    static String addFolderHtml(String output, boolean french) {
        AddFolder add = addFolder(output);
        if (add == null) return "";
        return "<p><a href='" + INIT_LINK + URLEncoder.encode(add.pkg(), StandardCharsets.UTF_8) + "|"
            + URLEncoder.encode(add.answer(), StandardCharsets.UTF_8) + "'>➕ " + (french ? "Ajouter ce dossier (architecte)" : "Add this folder (architect)")
            + "</a> <span style='color:#6B7280'>" + escape(add.command()) + "</span></p>";
    }

    /** The init form an {@code archvow-init:} link opens, or null. */
    static AddFolder initOf(String link) {
        if (link == null || !link.startsWith(INIT_LINK)) return null;
        String[] parts = link.substring(INIT_LINK.length()).split("\\|", 2);
        if (parts.length < 2) return null;
        return new AddFolder(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
    }

    /** The result's HTML with the add-folder link before its end, when archvow named one. */
    static String withAddFolder(String html, String output, boolean french) {
        String link = addFolderHtml(output, french);
        if (link.isEmpty() || html == null) return html;
        int end = html.lastIndexOf("</body>");
        return end < 0 ? html + link : html.substring(0, end) + link + html.substring(end);
    }

    /** "Brief folders (--from): briefs/, specs/ · 3 files" for the Overview. */
    static String foldersLine(List<String> declared, int files, boolean french) {
        List<String> names = new ArrayList<>();
        for (String d : declared) names.add(d + "/");
        String where = names.isEmpty() ? (french ? "aucun déclaré" : "none declared") : String.join(", ", names);
        return (french ? "Dossiers de briefs (--from) : " : "Brief folders (--from): ") + where + " · " + files + " "
            + (files == 1 ? (french ? "fichier" : "file") : (french ? "fichiers" : "files"));
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;");
    }
}
