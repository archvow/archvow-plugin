package org.archvow.idea;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Readable, selectable command results. Raw JSON is deliberately a secondary view:
 * this renders the ACTUAL values supplied by Archvow, never synthesized .vow text.
 * Uses Swing's modest HTML 3.2 subset (tables, bgcolor, font colors) rather than
 * modern CSS that JEditorPane does not reliably support.
 */
final class ArchvowResultFormatter {
    private ArchvowResultFormatter() { }

    private record Palette(String bg, String foreground, String muted, String line,
                           String raised, String good, String warn, String bad, String blue) {
        static Palette of(boolean dark) {
            return dark
                ? new Palette("#282B30", "#E0E6EF", "#AAB6C6", "#505763", "#363B43",
                              "#80DFAC", "#F2C46A", "#FF909D", "#83B9FF")
                : new Palette("#FFFFFF", "#253247", "#596B80", "#D9E1EB", "#F3F6FA",
                              "#13814F", "#A86B15", "#B82646", "#235FA9");
        }
    }

    static String render(String label, int exitCode, String output, boolean cancelled,
                         String arguments, boolean french, boolean dark) {
        String raw = output == null ? "" : output.strip();
        Palette p = Palette.of(dark);
        String key = label == null ? "" : label.toLowerCase(Locale.ROOT).strip();
        JsonElement parsed = parseResult(raw);
        StringBuilder out = begin(p);
        boolean reported = false;
        if (!cancelled && key.equals("prompt") && !raw.isBlank() && exitCode == 0) {
            prompt(out, raw, p, french);
            reported = true;
        }
        if (!cancelled && !reported && parsed != null) {
            if (isCheck(key) && parsed.isJsonObject() && parsed.getAsJsonObject().has("violations")) {
                check(out, parsed.getAsJsonObject(), p, french, key);
                reported = true;
            } else if (key.startsWith("audit") && parsed.isJsonArray()) {
                audit(out, parsed.getAsJsonArray(), p, french);
                reported = true;
            } else if (key.startsWith("doctor") && parsed.isJsonArray()) {
                doctor(out, parsed.getAsJsonArray(), p, french);
                reported = true;
            } else if (key.startsWith("next") && (parsed.isJsonArray() || parsed.isJsonObject())) {
                next(out, parsed, p, french);
                reported = true;
            } else if (key.equals("catalog") && parsed.isJsonObject()) {
                catalog(out, parsed.getAsJsonObject(), p, french);
                reported = true;
            }
        }
        if (!reported && key.equals("init") && parsed == null && exitCode == 0 && !cancelled) {
            init(out, raw, p, french);
            reported = true;
        }
        if (!reported) {
            boolean success = exitCode == 0 && !cancelled;
            String state = success ? tr(french, "Completed", "Terminé")
                : cancelled ? tr(french, "Cancelled or timed out", "Annulée ou délai dépassé")
                : tr(french, "Command failed", "Échec de la commande");
            heading(out, p, state + " · " + (label == null ? "Archvow" : label), success ? p.good : p.bad);
            if (raw.isEmpty()) {
                paragraph(out, tr(french, success ? "No output: the command succeeded without a message."
                    : "No output available. Check Python settings and the IDE log.",
                    success ? "Aucune sortie : la commande a réussi sans message."
                    : "Aucune sortie disponible. Vérifie la configuration Python et le journal de l'IDE."), p.muted);
            } else {
                String tip = advice(raw, french);
                if (!tip.isEmpty()) paragraph(out, tip, p.bad);
                if (parsed != null && (parsed.isJsonArray() || parsed.isJsonObject())) {
                    // Other JSON-returning commands still use a compact tree of human-readable values.
                    generic(out, parsed, p, french);
                } else paragraph(out, cut(raw, 16000), p.foreground);
            }
        }
        out.append("<table width='100%' cellpadding='5' cellspacing='0' style='margin-top:12px;'><tr><td bgcolor='")
            .append(p.raised).append("'><small><font color='").append(p.muted).append("'>")
            .append(tr(french, "Exit code: ", "Code retour : ")).append(exitCode)
            .append(" &nbsp;·&nbsp; ")
            .append(tr(french, "Original output available via 'Raw output'", "Sortie originale accessible via « Sortie brute »"))
            .append("</font></small></td></tr></table>");
        return out.append("</body></html>").toString();
    }

    /** Escaped raw source for an explicit toggle in the inline result card. */
    static String renderRaw(String output, boolean french, boolean dark) {
        Palette p = Palette.of(dark);
        String raw = output == null ? "" : output.strip();
        JsonElement element = parseResult(raw);
        if (element != null) raw = ArchvowGrammar.pretty(element);
        StringBuilder out = begin(p);
        heading(out, p, tr(french, "Raw output", "Sortie brute"), p.blue);
        out.append("<pre style='white-space:pre-wrap;font-family:monospace;color:").append(p.foreground).append("'>")
            .append(esc(cut(raw, 80000))).append("</pre>");
        return out.append("</body></html>").toString();
    }

    private static boolean isCheck(String command) {
        return "check".equals(command) || "full check".equals(command) || command.startsWith("check ");
    }

    private static void prompt(StringBuilder out, String prompt, Palette p, boolean fr) {
        heading(out, p, tr(fr, "AI handoff · task prompt", "Passage à l'IA · instructions de tâche"), p.blue);
        paragraph(out, tr(fr,
            "This is the actual Archvow prompt. Copy it to your AI assistant; no decision is made automatically.",
            "Ce sont les instructions réelles d'Archvow. Copie-les vers ton assistant IA ; aucune décision n'est prise automatiquement."), p.muted);
        // Swing's HTML renderer cannot consistently wrap PRE, so keep the exact line
        // breaks and escape the source while allowing the text to wrap naturally.
        out.append("<table width='100%' cellpadding='10'><tr><td bgcolor='").append(p.raised)
            .append("'><font color='").append(p.foreground).append("' face='monospaced'>");
        int count = 0;
        for (String line : prompt.split("\\R", -1)) {
            if (count++ > 0) out.append("<br>");
            out.append(esc(cut(line, 8000)).replace("  ", "&nbsp;&nbsp;"));
            if (count > 450) { out.append("<br>..."); break; }
        }
        out.append("</font></td></tr></table>");
    }

    private static void check(StringBuilder out, JsonObject report, Palette p, boolean fr, String label) {
        int errors = intValue(report, "errors"), warnings = intValue(report, "warnings");
        boolean ok = bool(report, "ok");
        heading(out, p, ("full check".equals(label) ? tr(fr, "Full check", "Contrôle complet")
            : tr(fr, "Check & Gates", "Vérifications et validations")) + " · "
            + (ok ? tr(fr, "Passed", "Validé") : tr(fr, "Action required", "Action requise")), ok ? p.good : p.bad);
        summary(out, p, List.of(
            chip(errors + tr(fr, " errors", " erreurs"), errors > 0 ? p.bad : p.good),
            chip(warnings + tr(fr, " warnings", " avertissements"), warnings > 0 ? p.warn : p.good),
            chip(intArray(report, "components") + tr(fr, " components", " composants"), p.blue)));
        JsonArray violations = array(report, "violations");
        section(out, p, tr(fr, "Diagnostics", "Diagnostics") + " (" + violations.size() + ")");
        if (violations.size() == 0) empty(out, p, tr(fr, "No violations detected.", "Aucune violation détectée."));
        int count = 0;
        for (JsonElement item : violations) {
            if (!item.isJsonObject()) continue;
            JsonObject v = item.getAsJsonObject();
            String severity = str(v, "severity");
            String code = str(v, "code");
            String file = str(v, "file");
            String status = "error".equalsIgnoreCase(severity) ? tr(fr, "ERROR", "ERREUR") : tr(fr, "WARNING", "AVERTISSEMENT");
            String title = (code.isBlank() ? status : code + " · " + status);
            String content = str(v, "message");
            StringBuilder details = new StringBuilder();
            if (!file.isBlank()) locationField(details, file, intValue(v, "line"), fr);
            card(out, p, title, content, "error".equalsIgnoreCase(severity) ? p.bad : p.warn, details.toString());
            if (++count >= 35) break;
        }
        if (violations.size() > count) more(out, p, violations.size() - count, fr);
        JsonObject safety = obj(report, "safety");
        if (safety != null && safety.has("total")) {
            section(out, p, tr(fr, "Safety", "Sécurité"));
            paragraph(out, intValue(safety, "answered") + " / " + intValue(safety, "total")
                + tr(fr, " questions answered", " questions traitées"), p.foreground);
        }
        JsonObject budget = obj(report, "budget");
        if (budget != null) budget(out, budget, p, fr);
    }

    private static void audit(StringBuilder out, JsonArray entries, Palette p, boolean fr) {
        int flagged = 0;
        for (JsonElement item : entries) if (item.isJsonObject() && array(item.getAsJsonObject(), "flags").size() > 0) flagged++;
        heading(out, p, tr(fr, "Audit · Human decisions & traceability", "Audit · Décisions humaines et traçabilité"),
            flagged == 0 ? p.good : p.warn);
        summary(out, p, List.of(
            chip(entries.size() + tr(fr, " records", " enregistrements"), p.blue),
            chip(flagged + tr(fr, " to review", " à vérifier"), flagged > 0 ? p.warn : p.good)));
        if (entries.size() == 0) {
            empty(out, p, tr(fr, "No recorded decisions, assumptions, hazards or invariants yet.",
                "Aucune décision, hypothèse, hazard ou invariant enregistré."));
            return;
        }
        if (flagged > 0) section(out, p, tr(fr, "Records to review", "Enregistrements à vérifier"));
        // Flagged first: a user should not need to scan many approved decisions to find an anomaly.
        int rendered = 0;
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 1 && flagged > 0 && flagged < entries.size())
                section(out, p, tr(fr, "Other records", "Autres enregistrements"));
            for (JsonElement el : entries) {
                if (!el.isJsonObject()) continue;
                JsonObject row = el.getAsJsonObject();
                boolean needsReview = array(row, "flags").size() > 0;
                if (needsReview != (pass == 0)) continue;
                String reference = str(row, "ref");
                String kind = str(row, "kind");
                String[] questionAnswer = str(row, "text").split("\\s+->\\s+", 2);
                StringBuilder extra = new StringBuilder();
                if (questionAnswer.length == 2) field(extra, p, tr(fr, "Recorded answer", "Réponse enregistrée"), questionAnswer[1]);
                String author = str(row, "by");
                String date = str(row, "on");
                String meta = (author.isBlank() ? tr(fr, "Unknown author", "Auteur inconnu") : author)
                    + (date.length() >= 10 ? " · " + date.substring(0, 10) : "");
                field(extra, p, tr(fr, "Recorded by", "Enregistré par"), meta);
                JsonObject commit = obj(row, "commit");
                if (commit != null) field(extra, p, "Git", str(commit, "commit") + " · " + str(commit, "subject"));
                else field(extra, p, "Git", tr(fr, "Not committed", "Non commité"));
                for (JsonElement f : array(row, "flags"))
                    field(extra, p, tr(fr, "Review", "À vérifier"), asText(f));
                card(out, p, kind + " · " + reference, questionAnswer[0], needsReview ? p.warn : p.good, extra.toString());
                if (++rendered == 50) { more(out, p, entries.size() - rendered, fr); return; }
            }
        }
    }

    private static void doctor(StringBuilder out, JsonArray findings, Palette p, boolean fr) {
        int fails = 0, warns = 0;
        for (JsonElement item : findings) {
            if (!item.isJsonObject()) continue;
            String status = str(item.getAsJsonObject(), "status");
            if ("fail".equalsIgnoreCase(status)) fails++;
            else if ("warn".equalsIgnoreCase(status)) warns++;
        }
        heading(out, p, tr(fr, "Doctor · Environment diagnostics", "Doctor · Diagnostic de l'environnement"),
            fails > 0 ? p.bad : warns > 0 ? p.warn : p.good);
        summary(out, p, List.of(
            chip(fails + tr(fr, " failures", " échecs"), fails > 0 ? p.bad : p.good),
            chip(warns + tr(fr, " warnings", " avertissements"), warns > 0 ? p.warn : p.good),
            chip((findings.size() - fails - warns) + " OK", p.good)));
        if (findings.size() == 0) {
            empty(out, p, tr(fr, "No environment checks returned.", "Aucun contrôle d'environnement renvoyé."));
            return;
        }
        int rendered = 0;
        for (String level : List.of("fail", "warn", "ok")) {
            int n = 0;
            for (JsonElement item : findings) if (item.isJsonObject() && level.equalsIgnoreCase(str(item.getAsJsonObject(), "status"))) n++;
            if (n == 0) continue;
            section(out, p, switch (level) {
                case "fail" -> tr(fr, "To fix", "À corriger");
                case "warn" -> tr(fr, "Warnings", "Avertissements");
                default -> tr(fr, "Working correctly", "Fonctionne correctement");
            });
            for (JsonElement el : findings) {
                if (!el.isJsonObject()) continue;
                JsonObject finding = el.getAsJsonObject();
                if (!level.equalsIgnoreCase(str(finding, "status"))) continue;
                String fix = str(finding, "fix");
                StringBuilder extra = new StringBuilder();
                if (!fix.isBlank()) field(extra, p, tr(fr, "Suggested fix", "Correction proposée"), fix);
                card(out, p, str(finding, "check") + " · " + level.toUpperCase(Locale.ROOT),
                    str(finding, "detail"), "fail".equals(level) ? p.bad : "warn".equals(level) ? p.warn : p.good,
                    extra.toString());
                if (++rendered == 50) { more(out, p, findings.size() - rendered, fr); return; }
            }
        }
    }

    private static void next(StringBuilder out, JsonElement json, Palette p, boolean fr) {
        JsonArray components = json.isJsonArray() ? json.getAsJsonArray() : array(json.getAsJsonObject(), "components");
        JsonObject budget = json.isJsonObject() ? obj(json.getAsJsonObject(), "budget") : null;
        int ready = 0, blocked = 0;
        for (JsonElement el : components) {
            if (!el.isJsonObject()) continue;
            String readiness = str(el.getAsJsonObject(), "readiness").toLowerCase(Locale.ROOT);
            if (readiness.startsWith("ready")) ready++;
            if (readiness.startsWith("blocked")) blocked++;
        }
        heading(out, p, tr(fr, "Next · What to work on", "Next · Prochaines actions"), blocked > 0 ? p.warn : p.blue);
        summary(out, p, List.of(
            chip(components.size() + tr(fr, " components", " composants"), p.blue),
            chip(ready + tr(fr, " ready", " prêts"), p.good),
            chip(blocked + tr(fr, " blocked", " bloqués"), blocked > 0 ? p.warn : p.good)));
        if (components.size() == 0) empty(out, p,
            tr(fr, "Nothing planned. Add a component using 'component add'.",
                "Aucun composant planifié. Utilise « component add » pour en ajouter un."));
        int rendered = 0;
        for (JsonElement el : components) {
            if (!el.isJsonObject()) continue;
            JsonObject component = el.getAsJsonObject();
            String readiness = str(component, "readiness");
            String level = readiness.toLowerCase(Locale.ROOT);
            String color = level.startsWith("ready") ? p.good : level.startsWith("blocked") ? p.warn : p.blue;
            StringBuilder extra = new StringBuilder();
            if (component.has("started")) field(extra, p, tr(fr, "Started", "Démarré"), str(component, "started"));
            for (JsonElement note : array(component, "notes")) field(extra, p,
                tr(fr, "Attention", "Attention"), asText(note));
            if (component.has("tokens")) field(extra, p, tr(fr, "Spec size", "Taille de la spec"), str(component, "tokens") + " tokens");
            if (level.startsWith("blocked")) field(extra, p, tr(fr, "Suggested command", "Commande suggérée"),
                "archvow specify " + str(component, "component"));
            card(out, p, str(component, "component") + " · " + readiness,
                str(component, "type"), color, extra.toString());
            if (++rendered >= 45) break;
        }
        if (components.size() > rendered) more(out, p, components.size() - rendered, fr);
        if (budget != null) budget(out, budget, p, fr);
    }

    private static void budget(StringBuilder out, JsonObject budget, Palette p, boolean fr) {
        section(out, p, tr(fr, "Change budget", "Budget de changement"));
        String base = str(budget, "base");
        if (!base.isBlank()) field(out, p, tr(fr, "Compared with", "Comparé à"), base);
        if (budget.has("files")) field(out, p, tr(fr, "Files", "Fichiers"),
            str(budget, "files") + " / " + str(budget, "max_files"));
        if (budget.has("added_lines")) field(out, p, tr(fr, "Lines added", "Lignes ajoutées"),
            str(budget, "added_lines") + " / " + str(budget, "max_added_lines"));
        if (bool(budget, "full")) paragraph(out,
            tr(fr, "Change budget reached: commit/review before continuing.",
                "Budget atteint : valider et revoir les modifications avant de continuer."), p.warn);
    }

    private static void catalog(StringBuilder out, JsonObject catalog, Palette p, boolean fr) {
        JsonArray templates = array(catalog, "templates");
        heading(out, p, tr(fr, "Template catalog", "Catalogue de modèles"), p.blue);
        summary(out, p, List.of(chip(templates.size() + tr(fr, " templates", " modèles"), p.blue)));
        if (templates.size() == 0) {
            empty(out, p, tr(fr, "No templates found. Configure ARCHVOW_TEMPLATES if using company packs.",
                "Aucun modèle trouvé. Configure ARCHVOW_TEMPLATES pour les modèles d'entreprise."));
            return;
        }
        int shown = 0;
        for (JsonElement el : templates) {
            if (!el.isJsonObject()) continue;
            JsonObject template = el.getAsJsonObject();
            String name = str(template, "name");
            StringBuilder details = new StringBuilder();
            field(details, p, tr(fr, "Version", "Version"), str(template,"version"));
            field(details, p, tr(fr, "Language / Type", "Langue / Type"),
                str(template,"language") + " · " + str(template,"kind"));
            field(details, p, tr(fr, "Architecture", "Architecture"), str(template,"style"));
            JsonObject kinds = obj(template,"component_types");
            if (kinds != null) field(details, p, tr(fr,"Component types", "Types de composants"),
                String.join(", ", kinds.keySet()));
            JsonArray questions = array(template,"questions");
            field(details, p, tr(fr,"Initialization questions", "Questions d'initialisation"),
                Integer.toString(questions.size()));
            card(out, p, name, str(template,"description"), p.blue, details.toString());
            if (++shown >= 25) { more(out, p, templates.size() - shown, fr); break; }
        }
        paragraph(out, tr(fr, "To answer a template's questions, open Commands → init.",
            "Pour répondre aux questions d'un modèle, ouvre Commandes → init."), p.muted);
    }

    private static void init(StringBuilder out, String output, Palette p, boolean fr) {
        heading(out, p, tr(fr, "Project initialized", "Projet initialisé"), p.good);
        if (output.isBlank()) {
            empty(out, p, tr(fr, "No output returned.", "Aucune sortie renvoyée."));
            return;
        }
        section(out, p, tr(fr,"Archvow response", "Réponse d'Archvow"));
        int lines = 0;
        for (String line : output.split("\\R")) {
            if (line.isBlank()) continue;
            if (lines++ > 65) { paragraph(out, tr(fr,"More in Raw output", "Suite dans Sortie brute"), p.muted); break; }
            String color = line.contains("Next") || line.contains("Étape") ? p.blue
                : line.contains("warn") || line.contains("Review") ? p.warn : p.foreground;
            paragraph(out, line.trim(), color);
        }
    }

    /** No heading of its own: the card around it already says "Command result" (it said it twice, 0.12.1). */
    private static void generic(StringBuilder out, JsonElement value, Palette p, boolean fr) {
        if (value.isJsonObject()) {
            JsonObject root = value.getAsJsonObject();
            // A machine-readable response may contain several collections. Keep their
            // headings and nested fields instead of serializing whole JSON arrays.
            fieldsCard(out, p, root, tr(fr,"Details", "Détails"), fr);
        } else if (value.isJsonArray()) {
            JsonArray rows = value.getAsJsonArray();
            summary(out, p, List.of(chip(rows.size() + tr(fr," items", " éléments"), p.blue)));
            int index = 0;
            for (JsonElement el : rows) {
                if (++index > 45) { more(out, p, rows.size() - 45, fr); break; }
                if (el.isJsonObject()) fieldsCard(out, p, el.getAsJsonObject(),
                    tr(fr,"Item", "Élément") + " " + index, fr);
                else card(out, p, Integer.toString(index), asText(el), p.blue, "");
            }
        } else paragraph(out, asText(value), p.foreground);
    }

    private static void fieldsCard(StringBuilder out, Palette p, JsonObject object, String title, boolean fr) {
        fieldsCard(out, p, object, title, fr, 0);
    }

    private static void fieldsCard(StringBuilder out, Palette p, JsonObject object, String title,
                                   boolean fr, int depth) {
        if (depth > 3) {
            paragraph(out, tr(fr, "Additional details in Raw output", "Informations supplémentaires dans Sortie brute"), p.muted);
            return;
        }
        StringBuilder extra = new StringBuilder();
        List<java.util.Map.Entry<String, JsonElement>> nested = new ArrayList<>();
        int count = 0;
        for (var entry : object.entrySet()) {
            if (++count > 45) { field(extra, p, "…", tr(fr,"More in Raw output", "Suite dans Sortie brute")); break; }
            JsonElement item = entry.getValue();
            if (item.isJsonArray() || item.isJsonObject()) nested.add(entry);
            else field(extra, p, entry.getKey(), cut(asText(item), 600));
        }
        card(out, p, title, "", p.blue, extra.toString());
        for (var entry : nested) {
            JsonElement child = entry.getValue();
            String name = entry.getKey();
            if (child.isJsonObject()) {
                section(out, p, name);
                fieldsCard(out, p, child.getAsJsonObject(), name, fr, depth + 1);
            } else {
                JsonArray values = child.getAsJsonArray();
                section(out, p, name + " (" + values.size() + ")");
                if (values.size() == 0) { empty(out, p, tr(fr,"No entries", "Aucune entrée")); continue; }
                int index = 0;
                for (JsonElement v : values) {
                    if (++index > 22) { more(out, p, values.size() - 22, fr); break; }
                    if (v.isJsonObject()) {
                        JsonObject row = v.getAsJsonObject();
                        String heading = str(row,"name");
                        if (heading.isBlank()) heading = str(row,"id");
                        if (heading.isBlank()) heading = str(row,"code");
                        if (heading.isBlank()) heading = name + " " + index;
                        fieldsCard(out, p, row, heading, fr, depth + 1);
                    } else card(out, p, name + " " + index, cut(asText(v), 600), p.blue, "");
                }
            }
        }
    }

    private static JsonElement parseResult(String raw) {
        if (raw.isBlank()) return null;
        try { return JsonParser.parseString(raw); }
        catch (RuntimeException ignored) {
            int start = raw.indexOf('['), end = raw.lastIndexOf(']');
            if (start >= 0 && end > start) {
                try { return JsonParser.parseString(raw.substring(start, end + 1)); }
                catch (RuntimeException ignoredAgain) { /* prefixes are not necessarily JSON */ }
            }
            JsonObject obj = ArchvowGrammar.parseObject(raw);
            return obj;
        }
    }

    private static StringBuilder begin(Palette p) {
        // sizes from the IDE's font, in pt (ArchvowHtml): "12px" and h3/h4 drew text far larger than the IDE's
        return new StringBuilder(ArchvowHtml.body(p.foreground, p.bg));
    }
    /** Shared visual hierarchy with the VS Code result cards, using Swing-safe HTML tables. */
    private static void heading(StringBuilder out, Palette p, String title, String accent) {
        out.append("<table width='100%' cellspacing='0' cellpadding='0' border='0' style='margin-bottom:10px;'><tr>")
            .append("<td width='5' bgcolor='").append(accent).append("'>&nbsp;</td>")
            .append("<td width='46' bgcolor='").append(p.raised).append("' align='center'><font color='")
            .append(accent).append("' size='+2'><b>&#9670;</b></font></td>")
            .append("<td bgcolor='").append(p.raised).append("' style='padding:8px;'>")
            .append("<font color='").append(p.muted).append("'><small><b>ARCHVOW &#183; RESULT</b></small></font>")
            .append("<div style='margin-top:3px;'>")
            .append(ArchvowHtml.heading(esc(title), 1.1, "color:" + accent + "; margin:3px 0;"))
            .append("</div></td></tr></table>");
    }
    private static void section(StringBuilder out, Palette p, String label) {
        out.append("<div style='margin-top:12px; border-bottom:1px solid:").append(p.line).append(";'>")
            .append(ArchvowHtml.heading(esc(label), 1.0, "color:" + p.foreground + "; margin:4px 0 6px 0;"))
            .append("</div>");
    }
    private static void summary(StringBuilder out, Palette p, List<String> chips) {
        out.append("<table cellspacing='7' cellpadding='0' style='margin:8px 0 12px;'><tr>");
        for (String item : chips) {
            out.append("<td bgcolor='").append(p.raised)
                .append("' style='padding:9px 11px;'><font color='").append(p.muted)
                .append("'><b>&#9679;</b></font>&nbsp;&nbsp;").append(item).append("</td>");
        }
        out.append("</tr></table>");
    }
    private static String chip(String text, String color) {
        return "<b><font color='" + color + "'>" + esc(text) + "</font></b>";
    }
    private static void card(StringBuilder out, Palette p, String title, String body, String accent, String extraHtml) {
        // Swing JEditorPane supports HTML 3.2 tables but not modern CSS grid/flex.
        // Keep the status strip, raised background and distinct heading/detail rows
        // consistent with the VS Code cards; don't alter or synthesize result data.
        out.append("<table width='100%' cellspacing='0' cellpadding='0' border='0' style='margin-top:9px;'>")
            .append("<tr><td width='5' bgcolor='").append(accent).append("'>&nbsp;</td>")
            .append("<td bgcolor='").append(p.raised).append("'>")
            .append("<table width='100%' cellspacing='0' cellpadding='9' border='0'><tr><td>")
            .append("<font color='").append(accent).append("'><b>&#9679;</b></font>&nbsp;&nbsp;")
            .append("<b><font color='").append(p.foreground).append("'>")
            .append(esc(title)).append("</font></b>");
        if (body != null && !body.isBlank()) out.append("<div style='margin-top:6px;'>")
            .append(esc(cut(body, 4500))).append("</div>");
        if (extraHtml != null && !extraHtml.isEmpty()) out.append("<div style='margin-top:7px;'>")
            .append(extraHtml).append("</div>");
        out.append("</td></tr></table></td></tr></table><p style='margin:2px;'>&nbsp;</p>");
    }
    private static void field(StringBuilder out, Palette p, String key, String value) {
        if (value == null || value.isBlank()) return;
        out.append("<div style='margin-top:4px;'><font color='").append(p.muted).append("'><b>")
            .append(esc(key)).append(":</b></font> ").append(esc(cut(value, 2500))).append("</div>");
    }

    private static void locationField(StringBuilder out, String path, int line, boolean fr) {
        if (path == null || path.isBlank()) return;
        String normalized = path.replace('\\', '/');
        // ComponentView paints ArchvowFilePlanView itself: pixel-identical to the form's Files tree.
        out.append("<div style='margin-top:6px;'><img src='")
            .append(ArchvowHtmlFileIcons.imageSource(normalized, line, fr))
            .append("'></div>");
    }
    private static void paragraph(StringBuilder out, String text, String color) {
        out.append("<p style='color:").append(color).append(";'>").append(esc(text)).append("</p>");
    }
    private static void empty(StringBuilder out, Palette p, String text) { paragraph(out, text, p.muted); }
    private static void more(StringBuilder out, Palette p, int count, boolean fr) {
        if (count > 0) paragraph(out, "+" + count + tr(fr, " more entries (see Raw output)", " autres éléments (voir Sortie brute)"), p.muted);
    }
    private static JsonArray array(JsonObject o, String key) {
        JsonElement el = o == null ? null : o.get(key);
        return el != null && el.isJsonArray() ? el.getAsJsonArray() : new JsonArray();
    }
    private static JsonObject obj(JsonObject o, String key) {
        JsonElement el = o == null ? null : o.get(key);
        return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
    }
    private static int intArray(JsonObject o, String key) { return array(o, key).size(); }
    private static int intValue(JsonObject obj, String name) {
        try { return obj.get(name).getAsInt(); }
        catch (RuntimeException ex) { return 0; }
    }
    private static boolean bool(JsonObject obj, String name) {
        try { return obj.get(name).getAsBoolean(); }
        catch (RuntimeException ex) { return false; }
    }
    private static String str(JsonObject obj, String name) {
        JsonElement value = obj == null ? null : obj.get(name);
        if (value == null || value.isJsonNull()) return "";
        return asText(value);
    }
    private static String asText(JsonElement value) {
        if (value == null || value.isJsonNull()) return "";
        String text = value.isJsonPrimitive() ? value.getAsString() : value.toString();
        return cut(text, 4500);
    }
    private static String cut(String text, int max) { return text.length() > max ? text.substring(0, max) + "…" : text; }
    private static String tr(boolean french, String en, String fr) { return french ? fr : en; }
    static String advice(String output, boolean french) {
        String lower = output.toLowerCase(Locale.ROOT);
        if (lower.contains("no module named archvow"))
            return french ? "Archvow n'est pas installé dans ce Python. Installe-le dans cet environnement."
                          : "Archvow is not installed in this Python environment.";
        if (lower.contains("cannot run program") || lower.contains("no such file or directory"))
            return french ? "L'exécutable est introuvable. Vérifie le chemin Python dans les paramètres Archvow."
                          : "Executable not found. Check the Python path in Archvow settings.";
        if (lower.contains("permission denied") || lower.contains("access denied"))
            return french ? "Accès refusé : vérifie les permissions du dossier ou de l'exécutable."
                          : "Access denied: check directory and executable permissions.";
        return "";
    }
    private static String esc(String input) {
        return input == null ? "" : input.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
