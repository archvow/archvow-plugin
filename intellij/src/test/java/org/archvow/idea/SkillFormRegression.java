package org.archvow.idea;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.SwingUtilities;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 0.16.0, headless: the launch form of an AI skill (what it reads, at most how many, where the request goes, the
 * exact request, "Not yet" when it cannot run), Overview › Briefs drawn from {@code brief show --json}, and the
 * cards' origin tags and To review filter.
 */
public final class SkillFormRegression {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    /** Headless, nothing is ever validated, so a child's revalidate() does not reach its parents' layout caches: as on screen, start over. */
    private static void layoutTree(java.awt.Container parent) {
        invalidateTree(parent);
        doLayoutTree(parent);
    }

    private static void invalidateTree(java.awt.Container parent) {
        parent.invalidate();
        for (java.awt.Component child : parent.getComponents())
            if (child instanceof java.awt.Container nested) invalidateTree(nested);
    }

    private static void doLayoutTree(java.awt.Container parent) {
        parent.doLayout();
        for (java.awt.Component child : parent.getComponents())
            if (child instanceof java.awt.Container nested) doLayoutTree(nested);
    }

    private static java.awt.Container named(java.awt.Container parent, String name) {
        if (name.equals(parent.getName())) return parent;
        for (java.awt.Component child : parent.getComponents()) {
            if (child instanceof java.awt.Container nested) {
                java.awt.Container match = named(nested, name);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static <T extends java.awt.Component> T firstVisible(java.awt.Container parent, Class<T> type) {
        for (java.awt.Component child : parent.getComponents()) {
            if (type.isInstance(child) && child.isVisible()) return type.cast(child);
            if (child instanceof java.awt.Container nested) {
                T match = firstVisible(nested, type);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static void skillFormStaysTopAligned(ArchvowSkillForm form) {
        form.setSize(1240, 760);
        layoutTree(form);
        java.awt.Container target = named(form, "archvow.skill.target");
        java.awt.Container actions = named(form, "archvow.skill.actions");
        check(target != null && actions != null, "the field and the buttons are present");
        javax.swing.JComboBox<?> combo = firstVisible(form, javax.swing.JComboBox.class);
        check(combo != null, "the visible target field is present");
        int fieldTop = SwingUtilities.convertPoint(combo, 0, 0, form).y;
        int actionsTop = SwingUtilities.convertPoint(actions, 0, 0, form).y;
        check(actionsTop > fieldTop && actionsTop - fieldTop < 160,
            "the buttons stay just below the field, in the left column, like VS Code");
    }

    /** What the panel does for the form: archvow --prompt, the background run, the links. */
    static final class TestHost implements ArchvowSkillForm.Host {
        final List<String> calls = new ArrayList<>();
        List<String> args;
        java.util.function.Consumer<ArchvowSkillForm.Ended> ended;
        String kept = "";
        boolean stopped;
        @Override public void prompt(String skill, String target, String max, java.util.function.Consumer<String> text) {
            text.accept("archvow says: " + skill + " on " + target + (max.isEmpty() ? "" : " at most " + max));
        }
        @Override public Runnable run(List<String> a, java.util.function.Consumer<ArchvowSkillForm.Ended> e) {
            args = a; ended = e;
            return () -> stopped = true;
        }
        @Override public void showLog(boolean review) { calls.add("log " + review); }
        @Override public void openReport() { calls.add("report"); }
        @Override public void reviewAdded(String skill, String component) { calls.add("review " + skill + " " + component); }
        @Override public String keptTool() { return kept; }
        @Override public void keepTool(String tool) { kept = tool; calls.add("keep " + tool); }
    }

    private static javax.swing.JButton button(java.awt.Container parent, String name) {
        java.awt.Container found = named(parent, name);
        return found instanceof javax.swing.JButton b ? b : null;
    }

    private static void compactSkillExplanation(ArchvowSkillForm form) {
        for (int width : new int[] {1200, 760, 550, 410}) {
            form.setSize(width, 820);
            layoutTree(form);
            java.awt.Container body = named(form, "archvow.skill.body");
            check(body != null, "skill columns are present");
            body.dispatchEvent(new java.awt.event.ComponentEvent(body, java.awt.event.ComponentEvent.COMPONENT_RESIZED));
            layoutTree(form);
            java.awt.Container explanation = named(form, "archvow.skill.explanation");
            check(explanation != null, "the compact instructions are visible");
            int previousBottom = -1;
            for (java.awt.Component step : explanation.getComponents()) {
                if (previousBottom >= 0) check(step.getY() - previousBottom <= 12,
                    "instruction gaps must stay compact at " + width);
                check(step.getHeight() > 0, "instructions have nonzero height at " + width);
                previousBottom = step.getY() + step.getHeight();
            }
            // The explanation is now hosted at its natural height (BorderLayout.NORTH),
            // rather than stretched to fill the editor. Its final request block should
            // end at the bottom of that natural-height panel, without being clipped.
            check(previousBottom <= explanation.getHeight() + 2,
                "instructions or the request are clipped at " + width);
        }
    }

    public static void main(String[] args) throws Exception {
        Path contract = Path.of(System.getProperty("archvow.contract.root", "../contract"));
        JsonObject skills = JsonParser.parseString(Files.readString(contract.resolve("forms/forms.json")))
            .getAsJsonObject().getAsJsonObject("ai_skills");
        List<String> sent = new ArrayList<>();
        List<ArchvowSkillForm.Tool> assistants = List.of(
            new ArchvowSkillForm.Tool("claude-code", "Claude Code", "claude", true),
            new ArchvowSkillForm.Tool("copilot", "GitHub Copilot CLI", "copilot", false));
        TestHost host = new TestHost();
        ArchvowSkillForm clarify = new ArchvowSkillForm(skills, "clarify", Map.of("component", "clean_orders"),
            List.of("orders_reader", "clean_orders"), List.of(), assistants, null, "main",
            c -> null, false, host, (text, values) -> sent.add(text), () -> sent.add("cancel"));
        // the instructions are archvow's (archvow skill clarify clean_orders --max 5 --prompt)
        check(clarify.request.getText().equals("archvow says: clarify on clean_orders at most 5"), clarify.request.getText());
        check(clarify.copy.isEnabled(), "a component is chosen: the instructions can go");
        clarify.copy.doClick();
        check(sent.size() == 1 && sent.get(0).equals(clarify.request.getText()), "the displayed instructions are copied: " + sent);
        // ▶ Run with <the AI>: the name follows the project's ai_tools, not a fixed one
        check(clarify.run.getText().equals("▶ Run with Claude Code") && clarify.run.isEnabled(), clarify.run.getText());
        javax.swing.JPopupMenu menu = clarify.runMenu();
        boolean greyed = false;
        for (java.awt.Component item : menu.getComponents())
            if (item instanceof javax.swing.JMenuItem mi && mi.getText().contains("GitHub Copilot CLI"))
                greyed = !mi.isEnabled() && mi.getText().contains("no copilot command");
        check(greyed, "an AI that is not on this computer is greyed, with its command");
        clarify.run.doClick();
        check(host.args.equals(List.of("skill", "clarify", "clean_orders", "--max", "5", "--with", "claude-code", "--json")),
            "archvow runs it: " + host.args);
        check(clarify.runState.isVisible() && button(clarify, "archvow.skill.stop") != null && !clarify.run.isEnabled(),
            "running: the strip with Stop, and no second run");
        button(clarify, "archvow.skill.stop").doClick();
        check(host.stopped, "Stop stops archvow");
        host.ended.accept(new ArchvowSkillForm.Ended("done", "Added 3 questions to clean_orders.", true, true));
        java.awt.Container said = named(clarify, "archvow.skill.ended");
        check(said instanceof ArchvowWrapLabel l && l.getText().startsWith("✓ Added 3 questions"), "done: what was added");
        button(clarify, "archvow.skill.reviewAdded").doClick();
        check(host.calls.contains("review clarify clean_orders") && clarify.run.isEnabled(), "Review them › opens what was added");
        host.ended.accept(new ArchvowSkillForm.Ended("failed", "Claude Code stopped: not logged in.", false, true));
        said = named(clarify, "archvow.skill.ended");
        check(said instanceof ArchvowWrapLabel l && l.getText().contains("✗ Claude Code stopped: not logged in.")
            && l.getText().contains("Copy instructions gives the same task"), "failed: the tool's reason, and the way round");
        // the kept AI comes back, and a pick is kept
        TestHost picked = new TestHost();
        picked.kept = "copilot";
        ArchvowSkillForm other = new ArchvowSkillForm(skills, "analyze", Map.of("component", "clean_orders"), List.of("clean_orders"),
            List.of(), List.of(new ArchvowSkillForm.Tool("claude-code", "Claude Code", "claude", true),
                new ArchvowSkillForm.Tool("copilot", "GitHub Copilot CLI", "copilot", true)), null, "main",
            c -> null, false, picked, (text, values) -> { }, () -> { });
        check(other.run.getText().equals("▶ Run with GitHub Copilot CLI"), "the AI picked last time: " + other.run.getText());
        for (java.awt.Component item : other.runMenu().getComponents())
            if (item instanceof javax.swing.JMenuItem mi && mi.getText().contains("Claude Code")) mi.doClick();
        check(picked.kept.equals("claude-code") && other.run.getText().equals("▶ Run with Claude Code"), "a pick is kept");
        // off (strict, or a sandbox): the reason is on the button, Copy is still there
        String off = "archvow does not start AI skill runs in this project (ai_runs [] in project.vow)";
        ArchvowSkillForm strict = new ArchvowSkillForm(skills, "clarify", Map.of("component", "clean_orders"), List.of("clean_orders"),
            List.of(), assistants, off, "main", c -> null, false, new TestHost(), (text, values) -> { }, () -> { });
        check(!strict.run.isEnabled() && strict.runTip().equals(off) && strict.copy.isEnabled(), "off: said on ▶, Copy still works");
        check(strict.runWhy.isVisible() && strict.runWhy.getText().equals("ⓘ " + off), "1.4.0: and under the buttons, in words");
        check(!clarify.runWhy.isVisible(), "on: nothing to say");
        // implement: a handoff only, never run by archvow; the form says why it waits, in French
        ArchvowSkillForm implement = new ArchvowSkillForm(skills, "implement", Map.of(), List.of("clean_orders"), List.of(),
            assistants, null, "main", c -> new String[] {"Not yet: 2 decisions still to make", "Pas encore : 2 décisions encore à prendre"},
            true, new TestHost(), (text, values) -> { }, () -> { });
        check(!new ArchvowSkillForm(skills, "implement", Map.of(), List.of("clean_orders"), List.of(), assistants, off, "main",
            c -> null, false, new TestHost(), (text, values) -> { }, () -> { }).runWhy.isVisible(), "Implement: archvow runs nothing, no reason");
        check(named(implement, "archvow.skill.run") == null && named(implement, "archvow.skill.actions") != null
            && !implement.copy.isEnabled() && !implement.openChat.isEnabled(), "implement: no run, and it waits");
        List<String> opened = new ArrayList<>();
        ArchvowSkillForm ready = new ArchvowSkillForm(skills, "implement", Map.of(), List.of("clean_orders"), List.of(),
            assistants, null, "main", c -> null, false, new TestHost(), (text, values) -> sent.add(text), () -> { });
        ready.onOpenChat(() -> opened.add("chat"));
        ready.openChat.doClick();
        check(opened.equals(List.of("chat")) && sent.get(sent.size() - 1).equals("archvow says: implement on clean_orders"),
            "copied, then the chat opened: " + sent + " " + opened);
        // review: the project's reviewers, the report at the end
        TestHost reviewing = new TestHost();
        ArchvowSkillForm review = new ArchvowSkillForm(skills, "review", Map.of(), List.of(), List.of(),
            List.of(new ArchvowSkillForm.Tool("codex", "Codex", "codex", true)), null, "main",
            c -> null, false, reviewing, (text, values) -> { }, () -> { });
        check(review.run.getText().equals("▶ Review with Codex"), review.run.getText());
        review.run.doClick();
        check(reviewing.args.equals(List.of("review", "--base", "main", "--with", "codex", "--json")), "archvow review: " + reviewing.args);
        reviewing.ended.accept(new ArchvowSkillForm.Ended("done", "3 findings.", false, true));
        check(named(review, "archvow.skill.ended") != null, "the review ended");
        ArchvowSkillForm noReviewer = new ArchvowSkillForm(skills, "review", Map.of(), List.of(), List.of(), List.of(), null, "main",
            c -> null, false, new TestHost(), (text, values) -> { }, () -> { });
        check(!noReviewer.run.isEnabled() && noReviewer.runTip().contains("No reviewer approved"), noReviewer.runTip());
        // draft-spec needs a brief: nothing to send or run before one is given
        ArchvowSkillForm draft = new ArchvowSkillForm(skills, "draft-spec", Map.of(), List.of(), List.of("briefs/orders.md#pricing"),
            assistants, null, "main", c -> null, false, new TestHost(), (text, values) -> { }, () -> { });
        check(!draft.copy.isEnabled() && !draft.run.isEnabled(), "no brief yet: nothing to send");
        ArchvowSkillForm drafted = new ArchvowSkillForm(skills, "draft-spec", Map.of("brief", "briefs/orders.md#pricing"), List.of(),
            List.of(), assistants, null, "main", c -> null, false, new TestHost(), (text, values) -> { }, () -> { });
        check(drafted.copy.isEnabled() && drafted.request.getText().equals("archvow says: draft-spec on briefs/orders.md#pricing"),
            drafted.request.getText());
        // The editor can be tall or narrow: the numbered instructions must not
        // spread across its whole height or clip when their text wraps.
        SwingUtilities.invokeAndWait(() -> { compactSkillExplanation(drafted); skillFormStaysTopAligned(draft); });

        // Responsive action footer: two or three buttons fit without being cut
        // off, even after a narrow resize wraps them into an additional row.
        SwingUtilities.invokeAndWait(() -> {
            check(clarify.getScrollableTracksViewportWidth(), "form follows editor width");
            for (int width : new int[] {1200, 850, 550, 410}) {
                clarify.setSize(width, 720);
                layoutTree(clarify);
                java.awt.Container body = named(clarify, "archvow.skill.body");
                java.awt.Container buttons = named(clarify, "archvow.skill.actions");
                check(body != null && buttons != null, "responsive body and footer exist");
                body.dispatchEvent(new java.awt.event.ComponentEvent(body, java.awt.event.ComponentEvent.COMPONENT_RESIZED));
                layoutTree(clarify);
                buttons.dispatchEvent(new java.awt.event.ComponentEvent(buttons, java.awt.event.ComponentEvent.COMPONENT_RESIZED));
                layoutTree(clarify);
                for (java.awt.Component button : buttons.getComponents()) if (button.isVisible()) {
                    check(button.getX() + button.getWidth() <= buttons.getWidth(), "button cut off at " + width);
                    check(button.getY() + button.getHeight() <= buttons.getHeight(), "button footer height too short at " + width + ": " + button.getBounds() + " in " + buttons.getSize() + " pref " + buttons.getPreferredSize() + " parent " + buttons.getParent().getSize());
                }
            }
        });

        // Overview › Briefs: used, not used yet (Draft with AI…), changed since (Review…, Accept change…)
        JsonObject status = JsonParser.parseString("""
            {"folders": ["briefs"], "to_draft": 1, "changed": 1, "files": [{"file": "briefs/orders.md", "sections": [
              {"section": "cleaning", "items": ["clean_orders", "R1"], "changed": false},
              {"section": "pricing", "items": [], "changed": false},
              {"section": "late-files", "items": ["daily_job"], "changed": true}]}]}""").getAsJsonObject();
        check(ArchvowBriefs.due(status) == 2 && ArchvowBriefs.due(null) == 0, "what to look at: unused + changed");
        String html = ArchvowBriefs.statusHtml(status, "sales", false);
        check(html.contains("2 of 3 sections used") && html.contains(ArchvowBriefs.DRAFT_LINK + "briefs%2Forders.md%23pricing"), html);
        check(html.contains(ArchvowBriefs.ACCEPT_LINK + "briefs%2Forders.md%23late-files") && html.contains("changed since: daily_job"), html);
        check(ArchvowBriefs.linkRef(ArchvowBriefs.DRAFT_LINK + "briefs%2Forders.md%23pricing", ArchvowBriefs.DRAFT_LINK)
            .equals("briefs/orders.md#pricing"), "a link gives its brief section back");
        check(html.contains(ArchvowBriefs.INIT_LINK + "sales|briefs%3Dbriefs%2C"), "add a folder: the architect's init form");
        check(ArchvowBriefs.statusHtml(null, "sales", true).contains("archvow 1.20"), "an older archvow: said so");

        // the cards: one tag each, To review outlined, the filter keeps what it must
        ArchvowAiSkills.Origin ai = new ArchvowAiSkills.Origin("ai", true, "claude-code", "why", "", false);
        ArchvowItemCards.Card card = new ArchvowItemCards.Card("5", "A cancelled order?", true, "Open", "", "", "").withOrigin(ai);
        check(ArchvowItemCards.tag(ai, false).equals("AI · claude-code · To review"), ArchvowItemCards.tag(ai, false));
        check(ArchvowItemCards.tag(new ArchvowAiSkills.Origin("brief", false, "", "", "cleaning", false), true).equals("Brief #cleaning"), "brief");
        ArchvowItemCards.Card done = new ArchvowItemCards.Card("1", "Rows kept?", false, "", "all", "", "");
        check(ArchvowItemCards.shown(card, "review") && !ArchvowItemCards.shown(done, "review") && ArchvowItemCards.shown(done, "all")
            && !ArchvowItemCards.shown(done, "open") && ArchvowItemCards.shown(card, "open"), "the filter");
        System.out.println("SkillFormRegression: OK");
    }
}
