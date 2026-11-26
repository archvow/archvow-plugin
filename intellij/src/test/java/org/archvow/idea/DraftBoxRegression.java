package org.archvow.idea;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 1.2.0: Draft with AI around an answer field (ArchvowDraftBox), the walk strip's ×, try's language and the Output
 * window's short lines - headless, as the VS Code page's tests check the same (layout.test.ts, generic-ui).
 */
public final class DraftBoxRegression {
    private DraftBoxRegression() { }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    /** A page whose archvow answers what each test sets. */
    static final class Page implements ArchvowDraftBox.Host {
        JsonObject drafts;
        String ref = "2";
        final List<List<String>> ran = new ArrayList<>();
        String answer = "{\"component\": \"orders_schema\", \"question\": \"2\", \"tool\": \"claude-code\", \"title\": \"Claude Code\", "
            + "\"draft\": \"amount: not personal\\norder_id: not personal\"}";
        String copied = "";

        @Override public JsonObject aiDrafts() { return drafts; }
        @Override public String component() { return "orders_schema"; }
        @Override public String ref() { return ref; }
        @Override public void run(List<String> arguments, Consumer<ArchvowCli.Result> done) {
            ran.add(arguments);
            done.accept(new ArchvowCli.Result(arguments, 0, answer, "", false));
        }
        @Override public void copy(String text, String said) { copied = text; }
        @Override public boolean french() { return false; }
    }

    /** Pictures of the new parts, for a person to compare with the mockups (ARCHVOW_SHOTS=<folder>). */
    private static void shots(String folder) {
        try {
            Page page = new Page();
            page.drafts = json("{\"on\": true, \"tools\": [{\"name\": \"claude-code\", \"title\": \"Claude Code\", \"program\": \"claude\", \"installed\": true}]}");
            JTextArea field = new JTextArea(4, 40);
            ArchvowDraftBox idle = new ArchvowDraftBox(page, field, new JScrollPane(field));
            save(idle, 460, 140, folder + "/ij-draft-idle.png");
            JTextArea f2 = new JTextArea(4, 40);
            ArchvowDraftBox drafted = new ArchvowDraftBox(page, f2, new JScrollPane(f2));
            drafted.ask("claude-code");
            save(drafted, 460, 170, folder + "/ij-draft-done.png");
            ArchvowWalkStrip strip = new ArchvowWalkStrip();
            strip.onLeave("Leave", () -> { });
            strip.show(ArchvowWalkStrip.Tone.INFO, "Building orders · 2 of 2", 1, 2, "orders_reader", "1 · The code ✓ copied   2 · Check   3 · Next",
                List.of(new ArchvowWalkStrip.Action("Copy AI prompt", false, () -> { }), new ArchvowWalkStrip.Action("Check", true, () -> { }),
                    new ArchvowWalkStrip.Action("Build next ▸", false, false, () -> { })));
            save(strip, 760, 130, folder + "/ij-walk.png");
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    private static void save(JComponent c, int w, int h, String file) throws java.io.IOException {
        JFrame frame = null;
        c.setSize(w, h);
        layoutAll(c);
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setColor(new java.awt.Color(0x1e, 0x1f, 0x22));
        g.fillRect(0, 0, w, h);
        c.printAll(g);
        g.dispose();
        javax.imageio.ImageIO.write(image, "png", new java.io.File(file));
    }

    private static void layoutAll(java.awt.Container c) {
        c.doLayout();
        for (java.awt.Component child : c.getComponents()) if (child instanceof java.awt.Container inner) layoutAll(inner);
    }

    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }

    public static void main(String[] args) {
        Page page = new Page();
        JTextArea field = new JTextArea("my own words");
        ArchvowDraftBox box = new ArchvowDraftBox(page, field, new JScrollPane(field));
        // before archvow 3.2 (no ai_drafts): no icon at all
        check(box.tools().isEmpty() && box.tip().isEmpty(), "no ai_drafts, no Draft with AI");
        // off for the project: the icon says why
        page.drafts = json("{\"on\": false, \"why_not\": null, \"tools\": [{\"name\": \"claude-code\", \"title\": \"Claude Code\", \"program\": \"claude\", \"installed\": true}]}");
        box.refresh();
        check(box.tip().startsWith("archvow does not start AI drafts in this project (ai_runs"), "off: " + box.tip());
        // 1.4.0: on for the project, but a sandbox is needed and archvow is not in one: archvow's reason, said on a click
        String sandboxed = "this project runs AI agents only in a sandbox (sandbox yes), and archvow is not in one here - copy the instructions to the agent you run there";
        page.drafts = json("{\"on\": true, \"why_not\": \"" + sandboxed + "\", \"tools\": [{\"name\": \"claude-code\", \"title\": \"Claude Code\", \"program\": \"claude\", \"installed\": true}]}");
        box.refresh();
        check(box.tip().equals(sandboxed), "archvow's reason: " + box.tip());
        javax.swing.JPopupMenu why = box.menu();
        boolean said = false, prompt = false, runs = false;
        for (java.awt.Component c : why.getComponents()) {
            if (c instanceof javax.swing.JLabel l && "archvow.draft.why".equals(l.getName())) said = l.getText().contains("only in a sandbox (sandbox yes)");
            if (c instanceof javax.swing.JMenuItem item && item.getText().contains("Copy the prompt instead")) prompt = true;
            if (c instanceof javax.swing.JMenuItem item && item.getText().contains("Claude Code")) runs = true;
        }
        check(said && prompt && !runs, "a click says why, in words, with the prompt instead and no AI to pick");
        // on: the first of the project's AI tools installed here drafts; the hover names it
        page.drafts = json("{\"on\": true, \"tools\": [{\"name\": \"codex\", \"title\": \"OpenAI Codex\", \"program\": \"codex\", \"installed\": false},"
            + " {\"name\": \"claude-code\", \"title\": \"Claude Code\", \"program\": \"claude\", \"installed\": true}]}");
        box.refresh();
        check("claude-code".equals(ArchvowGrammar.str(box.tool(), "name")), "the first installed tool: " + box.tool());
        check(box.tip().startsWith("Draft an answer with Claude Code"), "the hover: " + box.tip());
        // the draft lands in the field, marked; nothing saved
        box.ask("claude-code");
        check(page.ran.get(0).equals(List.of("draft", "orders_schema", "2", "--with", "claude-code", "--json")), "archvow draft: " + page.ran);
        check(field.getText().startsWith("amount: not personal") && box.draft() != null && !box.drafting(), "the draft in the field");
        // saved as it is: --drafted-with and --unchanged; changed: --drafted-with alone
        check(box.savedWith(field.getText()).equals(List.of("--drafted-with", "claude-code", "--unchanged")), "unchanged: " + box.savedWith(field.getText()));
        check(box.savedWith("amount: not personal (checked)").equals(List.of("--drafted-with", "claude-code")), "edited");
        // dropped: the field as it was
        box.drop();
        check("my own words".equals(field.getText()) && box.draft() == null && box.savedWith("x").isEmpty(), "dropped: " + field.getText());
        // another question shown: an older draft goes
        box.ask("claude-code");
        page.ref = "3";
        box.follow();
        check(box.draft() == null, "another question, no draft");
        // a failure says why, in the box
        page.ref = "2";
        page.answer = "{\"error\": {\"message\": \"Claude Code gave no draft (exit 1)\"}}";
        box.ask("claude-code");
        check(box.draft() == null, "no draft on a failure");
        // Copy the prompt instead
        page.answer = "{\"prompt\": \"Draft an answer to one question\"}";
        box.menu();
        check(box.menu().getComponentCount() >= 4, "the menu: head, two tools, a separator and Copy the prompt");
        javax.swing.JMenuItem codex = null;
        for (java.awt.Component c : box.menu().getComponents())
            if (c instanceof javax.swing.JMenuItem item && item.getText().contains("OpenAI Codex")) codex = item;
        check(codex != null && !codex.isEnabled() && codex.getText().contains("no codex command"),
            "a tool not installed: greyed, naming its command: " + (codex == null ? null : codex.getText()));
        check(codex != null && codex.getToolTipText() != null && codex.getToolTipText().contains("the IDE's plugin is not enough"),
            "its hover says why");
        java.util.Map<String, String> environment = new java.util.HashMap<>(java.util.Map.of("NO_COLOR", "1"));
        ArchvowCli.shellEnvironment(environment);  // outside the IDE: the shell's or nothing, never an exception
        check("1".equals(environment.get("NO_COLOR")) || environment.containsKey("PATH"), "the environment kept");

        // the walk strip: no Stop button, a × that leaves, steps drawn between the words and the count
        ArchvowWalkStrip strip = new ArchvowWalkStrip();
        List<String> left = new ArrayList<>();
        strip.onLeave("Leave the walk (Esc)", () -> left.add("left"));
        strip.show(ArchvowWalkStrip.Tone.INFO, "Building orders · 2 of 2", 1, 2, "orders_reader", "1 · The code", List.of(
            new ArchvowWalkStrip.Action("Copy AI prompt", false, () -> { })));
        check(!strip.buttonLabels().contains("Stop") && strip.stepsDone() == 1 && strip.stepsTotal() == 2, "no Stop; 1 of 2 drawn");
        check(strip.progressText().equals("Building orders · 2 of 2"), "the progress: " + strip.progressText());
        strip.leaveButton().doClick();
        check(left.equals(List.of("left")), "× leaves the walk");

        // try in the page's language; the Output window's lines as a person types the command
        check(ArchvowCli.withLanguage(List.of("try", "python-app", "--json"), true).equals(List.of("try", "python-app", "--json", "--language", "fr")), "try --language fr");
        check(ArchvowCli.withLanguage(List.of("check", "--json"), true).equals(List.of("check", "--json")), "only try");
        check(ArchvowRunLog.display(List.of("/usr/bin/python3.12", "-P", "-m", "archvow", "--root", "/Users/me/lab", "check", "--json")).equals("check"),
            "short line: " + ArchvowRunLog.display(List.of("/usr/bin/python3.12", "-P", "-m", "archvow", "--root", "/Users/me/lab", "check", "--json")));
        check(ArchvowRunLog.display(List.of("decision", "make", "orders_reader", "2", "not null")).equals("decision make orders_reader 2 \"not null\""),
            "archvow's own arguments as typed");
        if (System.getenv("ARCHVOW_SHOTS") != null) shots(System.getenv("ARCHVOW_SHOTS"));
        System.out.println("OK: Draft with AI box, walk ×, try --language, short Output lines");
    }
}
