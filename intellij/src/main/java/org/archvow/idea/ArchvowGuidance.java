package org.archvow.idea;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What to do about a check diagnostic, in plain words: what it means, what to do, who does it, whether it
 * blocks, and the archvow command its message names as a prefilled form (never run without a confirmation).
 * The same rules, word for word, as the VS Code extension's guidance.ts: GuidanceRegression requires the
 * output of both on every real message (../contract/guidance/expected.json).
 */
final class ArchvowGuidance {
    private ArchvowGuidance() { }

    record GuideCommand(String name, Map<String, String> values, String text, List<String> missing, boolean readOnly) { }

    record FileRef(String path, int line) { }

    record Guidance(String code, String meaning, String todo, String who, String blocking, String then,
                    GuideCommand command, FileRef file) { }

    private static String tr(boolean fr, String english, String french) { return fr ? french : english; }

    private static final Map<Boolean, String> RULES = new java.util.concurrent.ConcurrentHashMap<>();

    /** archvow's rules.md (rules.fr.md in French), packaged from ../contract/cli; "" when absent. */
    static String rules(boolean fr) {
        return RULES.computeIfAbsent(fr, french -> {
            try (var in = ArchvowGuidance.class.getResourceAsStream(french ? "/archvow/rules.fr.md" : "/archvow/rules.md")) {
                return in == null ? "" : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                return "";
            }
        });
    }

    // ------------------------------------------------------------------ archvow's rules table

    /** The cells of a check code's row in archvow's rules.md (Code | Fails when | Fix): {when, fix}. */
    static String[] ruleRow(String markdown, String code) {
        if (markdown != null) {
            Pattern row = Pattern.compile("^\\s*\\|\\s*" + Pattern.quote(code) + "\\s*\\|(.*)\\|\\s*$");
            for (String line : markdown.split("\n", -1)) {
                Matcher m = row.matcher(line);
                if (m.find()) {
                    String[] cells = m.group(1).split("(?<!\\\\)\\|", -1);
                    List<String> rest = new ArrayList<>();
                    for (int i = 1; i < cells.length; i++) rest.add(clean(cells[i].replace("\\|", "|")));
                    return new String[] {cells.length > 0 ? clean(cells[0].replace("\\|", "|")) : "", String.join(" ", rest).trim()};
                }
            }
        }
        return new String[] {"", ""};
    }

    private static String clean(String cell) {
        return cell.replaceAll("\\[([^\\]]+)\\]\\([^)]*\\)", "$1")
            .replaceAll("(?i)^\\s*\\((?:a )?warning[^)]*\\)\\s*", "")
            .replaceAll("(?i)^\\s*\\((?:un )?avertissement[^)]*\\)\\s*", "")
            .replaceAll("(?i)^\\s*\\(always on\\)\\s*", "")
            .replaceAll("(?i)^\\s*\\(toujours actif\\)\\s*", "").trim();
    }

    // ------------------------------------------------------------------ the commands a message names

    private static final Set<String> STOP_WORDS = Set.of("or", "and", "then", "to", "after", "before", "if", "when",
        "with", "ou", "et", "puis", "pour", "après", "avant", "si", "quand", "avec");
    private static final Set<String> STOP_TOKENS = Set.of("->", "→", "|", ";", "—", "-", "–");
    private static final Pattern PLACEHOLDER = Pattern.compile("<[^>]*>|\\{[^}]*\\}|^(\\.\\.\\.|…)$");

    record Token(String text, boolean quoted) { }

    /** The words of a command written in a sentence, up to where the sentence goes on. */
    static List<Token> commandTokens(String text, boolean wholeText) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
            if (i >= text.length()) break;
            if (text.charAt(i) == '"') {
                int j = i + 1;
                StringBuilder value = new StringBuilder();
                while (j < text.length() && text.charAt(j) != '"') {
                    if (text.charAt(j) == '\\' && j + 1 < text.length()) j++;
                    value.append(text.charAt(j++));
                }
                tokens.add(new Token(value.toString(), true));
                i = j + 1;
                if (!wholeText && i < text.length() && ",;:)".indexOf(text.charAt(i)) >= 0) break;
                if (!wholeText && i < text.length() && text.charAt(i) == '.'
                    && (i + 1 >= text.length() || Character.isWhitespace(text.charAt(i + 1)))) break;
                continue;
            }
            int j = i;
            while (j < text.length() && !Character.isWhitespace(text.charAt(j))) j++;
            String raw = text.substring(i, j);
            i = j;
            if (!wholeText) {
                if (raw.startsWith("(") || STOP_TOKENS.contains(raw) || STOP_WORDS.contains(raw.toLowerCase(Locale.ROOT))) break;
                boolean end = false;
                if (!raw.isEmpty() && ",;:)".indexOf(raw.charAt(raw.length() - 1)) >= 0) { raw = raw.substring(0, raw.length() - 1); end = true; }
                else if (raw.endsWith(".") && !raw.endsWith("...") && raw.length() > 1) { raw = raw.substring(0, raw.length() - 1); end = true; }
                if (!raw.isEmpty()) tokens.add(new Token(raw, false));
                if (end) break;
            } else {
                tokens.add(new Token(raw, false));
            }
        }
        return tokens;
    }

    private static final Pattern ARCHVOW = Pattern.compile("(`?)\\barchvow\\s+");

    /** The archvow commands a message names, in order, as prefilled forms. */
    static List<GuideCommand> commandsIn(String message, List<ArchvowGrammar.Command> commands) {
        List<GuideCommand> found = new ArrayList<>();
        String text = message == null ? "" : message;
        Matcher m = ARCHVOW.matcher(text);
        while (m.find()) {
            String after = text.substring(m.end());
            boolean fenced = "`".equals(m.group(1));
            int close = fenced ? after.indexOf('`') : -1;
            List<Token> tokens = fenced && close >= 0 ? commandTokens(after.substring(0, close), true) : commandTokens(after, false);
            String first = tokens.size() > 0 ? tokens.get(0).text() : null;
            String second = tokens.size() > 1 ? tokens.get(1).text() : null;
            ArchvowGrammar.Command command = null;
            for (ArchvowGrammar.Command c : commands)
                if (c.path.size() == 2 && c.path.get(0).equals(first) && c.path.get(1).equals(second)) { command = c; break; }
            if (command == null)
                for (ArchvowGrammar.Command c : commands)
                    if (c.path.size() == 1 && c.path.get(0).equals(first)) { command = c; break; }
            if (command == null) continue;
            boolean quotedPath = false;
            for (int k = 0; k < command.path.size() && k < tokens.size(); k++) quotedPath |= tokens.get(k).quoted();
            if (quotedPath) continue;
            found.add(formOf(command, tokens.subList(command.path.size(), tokens.size())));
        }
        return found;
    }

    private static GuideCommand formOf(ArchvowGrammar.Command command, List<Token> args) {
        Map<String, String> values = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        List<ArchvowGrammar.Param> positional = new ArrayList<>();
        for (ArchvowGrammar.Param p : command.parameters) if (p.flags.isEmpty()) positional.add(p);
        int next = 0;
        for (int k = 0; k < args.size(); k++) {
            Token token = args.get(k);
            ArchvowGrammar.Param flagged = null;
            if (!token.quoted() && token.text().startsWith("-")) {
                String flag = token.text().contains("=") ? token.text().substring(0, token.text().indexOf('=')) : token.text();
                for (ArchvowGrammar.Param p : command.parameters) if (p.flags.contains(flag)) { flagged = p; break; }
            }
            if (flagged != null) {
                if (flagged.bool) { values.put(flagged.name, "true"); continue; }
                Token value = token.text().contains("=")
                    ? new Token(token.text().substring(token.text().indexOf('=') + 1), false)
                    : (k + 1 < args.size() ? args.get(++k) : null);
                if (value != null) set(values, missing, flagged, value);
                continue;
            }
            if (!token.quoted() && token.text().startsWith("-")) continue; // an option this archvow does not have
            if (next >= positional.size()) continue;
            ArchvowGrammar.Param param = positional.get(next);
            set(values, missing, param, token);
            if (!param.multiToken) next++;
        }
        for (ArchvowGrammar.Param p : command.parameters)
            if (p.required && !values.containsKey(p.name) && !missing.contains(p.name)) missing.add(p.name);
        List<String> words = new ArrayList<>(List.of("archvow"));
        words.addAll(command.path);
        for (Token t : args) words.add(t.quoted() || t.text().matches(".*\\s.*") ? "\"" + t.text() + "\"" : t.text());
        boolean readOnly = READ_ONLY.contains(command.path.get(0));
        return new GuideCommand(command.label, values, String.join(" ", words), missing, readOnly);
    }

    private static void set(Map<String, String> values, List<String> missing, ArchvowGrammar.Param param, Token token) {
        boolean choice = (!token.quoted() && token.text().contains("|")) || (!param.choices.isEmpty() && !param.choices.contains(token.text()));
        if (PLACEHOLDER.matcher(token.text()).find() || choice) { if (!missing.contains(param.name)) missing.add(param.name); return; }
        boolean append = param.append || param.multiToken;
        values.put(param.name, append && values.containsKey(param.name) ? values.get(param.name) + "\n" + token.text() : token.text());
    }

    /** The command to offer first: one that changes something (the fix), else the first. */
    static GuideCommand mainCommand(List<GuideCommand> found) {
        for (GuideCommand c : found) if (!c.readOnly()) return c;
        return found.isEmpty() ? null : found.get(0);
    }

    /** First words of the commands that change nothing (the panel's READ_ONLY, VS Code's grammar.ts). */
    static final Set<String> READ_ONLY = Set.of("show", "catalog", "next", "check", "audit", "doctor", "changes", "diff", "prompt", "doc", "try");

    // ------------------------------------------------------------------ who does it

    private static final List<String> PERSON_ONLY = List.of("component", "decision make", "decision add", "decision accept",
        "question remove", "assumption accept", "assumption reject", "hazard", "invariant", "rule", "column", "dependency",
        "brief", "example", "specify", "freeze");
    private static final Set<String> ARCHITECT_CODES = Set.of("ARCH012", "ARCH020", "ARCH023", "ARCH024", "ARCH040", "ARCH041");
    private static final Set<String> PERSON_CODES = Set.of("ARCH009", "ARCH014", "ARCH036", "ARCH039", "ARCH043", "ARCH051",
        "ARCH052", "ARCH054");

    private static String whoOf(String code, GuideCommand command) {
        if (ARCHITECT_CODES.contains(code)) return "architect";
        if (command != null) {
            if (command.name().equals("init")) return "architect";
            if (command.readOnly()) return PERSON_CODES.contains(code) ? "person" : "anyone";
            for (String p : PERSON_ONLY) if (command.name().equals(p) || command.name().startsWith(p + " ")) return "person";
            return "you";
        }
        return PERSON_CODES.contains(code) ? "person" : "developer";
    }

    private static String whoText(String who, boolean fr) {
        return switch (who) {
            case "architect" -> tr(fr, "An architect: it changes the guards of the whole project.", "Un architecte : cela change les garde-fous de tout le projet.");
            case "person" -> tr(fr, "A person, here or in their own terminal - not an AI agent: it decides or changes the spec.",
                "Une personne, ici ou dans son propre terminal - pas un agent IA : cela décide ou change la spec.");
            case "anyone" -> tr(fr, "Anyone: the command changes nothing.", "Tout le monde : la commande ne change rien.");
            case "you" -> tr(fr, "You (or the AI, with your review).", "Vous (ou l'IA, sous votre relecture).");
            default -> tr(fr, "Whoever writes the code: you, or the AI with your review.", "Celui qui écrit le code : vous, ou l'IA sous votre relecture.");
        };
    }

    // ------------------------------------------------------------------ the guidance

    /** What to do about one diagnostic. {@code rules} is archvow's rules.md (rules.fr.md in French). */
    static Guidance guide(String code0, String severity, String message0, String file, int line, String rules,
                          List<ArchvowGrammar.Command> commands, boolean fr) {
        String code = code0 == null ? "" : code0.toUpperCase(Locale.ROOT);
        String message = message0 == null ? "" : message0;
        boolean warning = "warning".equalsIgnoreCase(severity);
        String[] row = ruleRow(rules, code);
        GuideCommand command = personal(code, mainCommand(commandsIn(message, commands)));
        FileRef where = fileOf(code, file == null ? "" : file, line);
        String meaning = !row[0].isEmpty() ? row[0] : tr(fr, "archvow found this while checking the project (see the message above).",
            "archvow a trouvé ceci en vérifiant le projet (voir le message ci-dessus).");
        String todo = !row[1].isEmpty() ? row[1] : command != null ? tr(fr, "Run the command the message gives.", "Lancez la commande que donne le message.")
            : where != null ? tr(fr, "Open the file and fix what the message says.", "Ouvrez le fichier et corrigez ce que dit le message.")
            : tr(fr, "Do what the message says.", "Faites ce que dit le message.");
        String who = whoText(whoOf(code, command), fr);
        String blocking = warning
            ? tr(fr, "Does not block: a warning - the commit and CI pass. Some warnings become errors in mode strict or with check --complete.",
                "Non bloquant : un avertissement - le commit et la CI passent. Certains avertissements deviennent des erreurs en mode strict ou avec check --complete.")
            : tr(fr, "Blocks: archvow check fails until it is fixed - the Git hook refuses the commit and CI fails.",
                "Bloquant : archvow check échoue tant que ce n'est pas corrigé - le hook Git refuse le commit et la CI échoue.");
        String then = tr(fr, "Then click Check (↻): the item disappears once it is fixed.", "Ensuite cliquez sur Vérifier (↻) : l'élément disparaît une fois corrigé.");
        Map<String, String> s = specific(code, message, fr, command);
        return new Guidance(code, s.getOrDefault("meaning", meaning), s.getOrDefault("todo", todo), s.getOrDefault("who", who),
            s.getOrDefault("blocking", blocking), s.getOrDefault("then", then), command, where);
    }

    /** Codes about what a vow says: their file is worth opening. Other vows are archvow's, not to edit. */
    private static final Set<String> VOW_CODES = Set.of("ARCH009", "ARCH014", "ARCH036", "ARCH050");

    /** The file a diagnostic is about, to open: "a.py (and 3 more)" is a.py; a vow only for a vow's own codes. */
    static FileRef fileOf(String code, String file, int line) {
        String path = file.replaceAll("\\s+\\(and \\d+ more\\)\\s*$", "").trim();
        if (path.isEmpty() || (path.startsWith(".archvow/") && path.endsWith(".vow") && !VOW_CODES.contains(code))) return null;
        return new FileRef(path, Math.max(0, line));
    }

    private static String group(Pattern pattern, String text, int group, String otherwise) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(group) : otherwise;
    }

    /** In the IDE a person treats it: the decision the AI would propose (ARCH033), the person makes. */
    private static GuideCommand personal(String code, GuideCommand command) {
        if (!code.equals("ARCH033") || command == null || !command.name().equals("decision propose")) return command;
        return new GuideCommand("decision make", command.values(), command.text().replace("archvow decision propose", "archvow decision make"),
            command.missing(), command.readOnly());
    }

    /** Where archvow's rules table is not enough: the codes that confused people, by message variant. */
    private static Map<String, String> specific(String code, String message, boolean fr, GuideCommand command) {
        Map<String, String> s = new LinkedHashMap<>();
        if (code.equals("ARCH012")) {
            Matcher m = Pattern.compile("says ([\\w.-]+)@([\\d.]+), installed is [\\w.-]+@([\\d.]+)").matcher(message);
            boolean found = m.find();
            String template = found ? m.group(1) : "the template", old = found ? m.group(2) : "an older version", now = found ? m.group(3) : "a newer one";
            String prepared = command != null ? " (" + command.text() + ")" : "";
            s.put("meaning", tr(fr, "The project was set up with " + template + " " + old + "; the archvow installed here brings " + now + ". archvow's own files in the project (CI job, Git hooks, AGENTS.md, ruff and test settings) are still those of " + old + ".",
                "Le projet a été créé avec " + template + " " + old + " ; l'archvow installé ici apporte " + now + ". Les fichiers d'archvow dans le projet (job CI, hooks Git, AGENTS.md, réglages ruff et tests) sont encore ceux de " + old + "."));
            s.put("todo", tr(fr, "Upgrade the project to " + now + ": run the prepared command" + prepared + ". It rewrites archvow's files with the new template and keeps your spec and your answers. Then review git diff - keep your own lines if you had edited a generated file such as the README - and commit.",
                "Mettez le projet en " + now + " : lancez la commande préparée" + prepared + ". Elle réécrit les fichiers d'archvow avec le nouveau template et garde votre spec et vos réponses. Relisez ensuite git diff - gardez vos propres lignes si vous aviez modifié un fichier généré comme le README - et commitez."));
            s.put("then", tr(fr, "Then commit: the warning disappears.", "Ensuite commitez : l'avertissement disparaît."));
            return s;
        }
        if (code.equals("ARCH053")) {
            String tool = group(Pattern.compile("written by ([\\w.-]+)"), message, 1,
                group(Pattern.compile("écrit par ([\\w.-]+)"), message, 1, tr(fr, "the AI tool", "l'outil IA")));
            String base = tr(fr, tool + " wrote these files (their header says @assisted " + tool + "). Before an agent's code is committed, archvow checks that the agent ran protected: in its sandbox, without an option that lets it act without asking (YOLO).",
                tool + " a écrit ces fichiers (leur en-tête dit @assisted " + tool + "). Avant qu'un code d'agent soit commité, archvow vérifie que l'agent a tourné protégé : dans son bac à sable, sans option qui le laisse agir sans demander (YOLO).");
            if (Pattern.compile("cannot see|ne peut pas voir").matcher(message).find()) {
                String where = group(Pattern.compile("cannot see (in [^:]+?)(?: \\(|:)"), message, 1, "");
                s.put("meaning", base + " " + tr(fr, "archvow cannot see " + tool + (where.isEmpty() ? "" : " " + where) + ", so nobody checked how it ran.",
                    "archvow ne peut pas voir " + tool + (where.isEmpty() ? "" : " " + where) + " : personne n'a vérifié comment il a tourné."));
                s.put("todo", tr(fr, "Review what " + tool + " wrote, then commit it. This warning only concerns files not committed yet: it disappears with the commit, and it never blocks. "
                        + (!where.isEmpty() ? "For archvow to check the agent next time, use " + tool + " where archvow sees it (archvow doctor says where)." : "archvow cannot check " + tool + " anywhere: review its code with care before each commit."),
                    "Relisez ce que " + tool + " a écrit, puis commitez. Cet avertissement ne concerne que les fichiers pas encore commités : il disparaît avec le commit et ne bloque jamais. "
                        + (!where.isEmpty() ? "Pour qu'archvow vérifie l'agent la prochaine fois, utilisez " + tool + " là où archvow le voit (archvow doctor dit où)." : "archvow ne peut vérifier " + tool + " nulle part : relisez son code avec soin avant chaque commit.")));
                s.put("who", tr(fr, "You, who commit the code.", "Vous, qui commitez le code."));
                s.put("then", tr(fr, "Then commit: the warning disappears.", "Ensuite commitez : l'avertissement disparaît."));
                return s;
            }
            if (Pattern.compile("never ran archvow|n'a jamais lancé archvow").matcher(message).find()) {
                s.put("meaning", base + " " + tr(fr, tool + " never ran archvow in this project, so nobody checked how it ran.", tool + " n'a jamais lancé archvow dans ce projet : personne n'a vérifié comment il a tourné."));
                s.put("todo", tr(fr, "Ask " + tool + " to run archvow check here, in its sandbox; then commit again. The commit message then records how it ran (an Archvow-Agent line).",
                    "Demandez à " + tool + " de lancer archvow check ici, dans son bac à sable ; puis commitez à nouveau. Le message de commit note alors comment il a tourné (une ligne Archvow-Agent)."));
                s.put("who", tr(fr, "You, then " + tool + ".", "Vous, puis " + tool + "."));
                s.put("then", tr(fr, "Then commit again.", "Ensuite commitez à nouveau."));
                return s;
            }
            if (Pattern.compile("refused to run inside it|a refusé de tourner").matcher(message).find()) {
                s.put("meaning", base + " " + tr(fr, "The last time " + tool + " ran archvow here, it was not protected, and archvow stopped it.", "La dernière fois que " + tool + " a lancé archvow ici, il n'était pas protégé, et archvow l'a arrêté."));
                s.put("todo", tr(fr, "Start " + tool + " protected - its sandbox on, without the option that lets it act without asking - let it run archvow check, then commit again. archvow doctor says what archvow sees.",
                    "Démarrez " + tool + " protégé - bac à sable actif, sans l'option qui le laisse agir sans demander - laissez-le lancer archvow check, puis commitez à nouveau. archvow doctor dit ce qu'archvow voit."));
                s.put("who", tr(fr, "You, who start " + tool + ".", "Vous, qui démarrez " + tool + "."));
                s.put("then", tr(fr, "Then commit again.", "Ensuite commitez à nouveau."));
                return s;
            }
            if (Pattern.compile("no Archvow-Agent line|aucune ligne Archvow-Agent|pas de ligne Archvow-Agent").matcher(message).find()) {
                s.put("meaning", tr(fr, "A commit of this branch changes code " + tool + " wrote, and its message has no Archvow-Agent line: it was made without the Git hooks (git commit --no-verify?), so nobody checked how " + tool + " ran.",
                    "Un commit de cette branche modifie du code écrit par " + tool + ", et son message n'a pas de ligne Archvow-Agent : il a été fait sans les hooks Git (git commit --no-verify ?), personne n'a vérifié comment " + tool + " a tourné."));
                s.put("todo", tr(fr, "Redo the commit with the hooks on: git reset --soft HEAD~1, then git commit. If the hooks never run on your machine: git config core.hooksPath .githooks.",
                    "Refaites le commit avec les hooks : git reset --soft HEAD~1, puis git commit. Si les hooks ne tournent jamais sur votre poste : git config core.hooksPath .githooks."));
                s.put("who", tr(fr, "You, who made the commit.", "Vous, qui avez fait le commit."));
                s.put("then", tr(fr, "Then push again: CI checks the branch again.", "Ensuite poussez à nouveau : la CI revérifie la branche."));
                return s;
            }
            if (Pattern.compile("YOLO|outside a sandbox|hors bac à sable").matcher(message).find()) {
                s.put("meaning", tr(fr, "A commit of this branch changes code " + tool + " wrote while it was not protected (outside its sandbox, or in YOLO mode).",
                    "Un commit de cette branche modifie du code que " + tool + " a écrit sans être protégé (hors de son bac à sable, ou en mode YOLO)."));
                s.put("todo", tr(fr, "Have that code reviewed closely, or redo it with " + tool + " protected; the architects decide whether the branch can be merged.",
                    "Faites relire ce code de près, ou refaites-le avec " + tool + " protégé ; les architectes décident si la branche peut être fusionnée."));
                s.put("who", tr(fr, "You, then the architects.", "Vous, puis les architectes."));
                return s;
            }
        }
        if (code.equals("ARCH014")) {
            s.put("todo", tr(fr, "This entry of a vow was edited by hand, and archvow writes it. Undo the hand edit (git checkout -- the file, or Edit back), then make the change with the commands: Edit spec, + Question, + Rule...",
                "Cette entrée d'un vow a été modifiée à la main, et c'est archvow qui l'écrit. Annulez la modification (git checkout -- le fichier), puis faites le changement avec les commandes : Modifier la spec, + Question, + Règle..."));
            return s;
        }
        if ((code.equals("ARCH035") || code.equals("ARCH038")) && Pattern.compile("^\\s*\\d+\\s").matcher(message).find()) {
            s.put("todo", code.equals("ARCH035")
                ? tr(fr, "Open each component's Safety view: for every open question, add the hazard it could cause, or mark it not applicable with a reason. archvow check --all lists them one by one.",
                    "Ouvrez la vue Sécurité de chaque composant : pour chaque question ouverte, ajoutez le hazard qu'elle pourrait causer, ou déclarez-la non applicable avec une raison. archvow check --all les liste une par une.")
                : tr(fr, "Mark the tests: @pytest.mark.decision(<n>) on the test of each decision, @pytest.mark.rule(\"R<n>\") for a rule. archvow check --all lists them one by one.",
                    "Marquez les tests : @pytest.mark.decision(<n>) sur le test de chaque décision, @pytest.mark.rule(\"R<n>\") pour une règle. archvow check --all les liste un par un."));
        }
        return s;
    }
}
