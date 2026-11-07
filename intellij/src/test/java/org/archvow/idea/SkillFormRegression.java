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

    private static void layoutTree(java.awt.Container parent) {
        parent.doLayout();
        for (java.awt.Component child : parent.getComponents())
            if (child instanceof java.awt.Container nested) layoutTree(nested);
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

    private static void compactSkillExplanation(ArchvowSkillForm form) {
        for (int width : new int[] {1200, 760, 550}) {
            form.setSize(width, 820);
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
            if (width >= 1000) check(previousBottom < explanation.getHeight() - 40,
                "the scrollable request remains grouped with the instructions at the top");
        }
    }

    public static void main(String[] args) throws Exception {
        Path contract = Path.of(System.getProperty("archvow.contract.root", "../contract"));
        JsonObject skills = JsonParser.parseString(Files.readString(contract.resolve("forms/forms.json")))
            .getAsJsonObject().getAsJsonObject("ai_skills");
        List<String> sent = new ArrayList<>();
        ArchvowSkillForm clarify = new ArchvowSkillForm(skills, "clarify", Map.of("component", "clean_orders"),
            List.of("orders_reader", "clean_orders"), List.of(), List.of("claude-code", "copilot"),
            c -> null, false, (text, values) -> sent.add(text), () -> sent.add("cancel"));
        check(clarify.request.getText().contains("Component: clean_orders")
            && clarify.request.getText().contains("Maximum questions: 5")
            && clarify.request.getText().contains("Human approval:"), clarify.request.getText());
        check(clarify.copy.isEnabled(), "a component is chosen: the request can go");
        clarify.copy.doClick();
        check(sent.size() == 1 && sent.get(0).equals(clarify.request.getText()), "the displayed full instructions are copied: " + sent);
        check(clarify.copySkill.isVisible(), "Claude Code has a native shortcut button");
        clarify.copySkill.doClick();
        check(sent.get(1).equals("/archvow-clarify clean_orders (at most 5 questions)"), "native shortcut only on request");
        // implement on a component with decisions to make: the form says why, in French
        ArchvowSkillForm implement = new ArchvowSkillForm(skills, "implement", Map.of(), List.of("clean_orders"), List.of(),
            List.of("copilot"), c -> new String[] {"Not yet: 2 decisions still to make", "Pas encore : 2 décisions encore à prendre"},
            true, (text, values) -> { }, () -> { });
        check(implement.request.getText().contains("Validation humaine :")
            && implement.request.getText().contains("Composant : clean_orders"), implement.request.getText());
        check(!implement.copySkill.isVisible(), "Copilot has no Claude-specific shortcut");
        // draft-spec needs a brief: nothing to send before one is given
        ArchvowSkillForm draft = new ArchvowSkillForm(skills, "draft-spec", Map.of(), List.of(), List.of("briefs/orders.md#pricing"),
            List.of(), c -> null, false, (text, values) -> { }, () -> { });
        check(!draft.copy.isEnabled(), "no brief yet: nothing to send");
        check(draft.values.get("tool").equals("copilot"), "no AI tool declared: copilot");
        ArchvowSkillForm drafted = new ArchvowSkillForm(skills, "draft-spec", Map.of("brief", "briefs/orders.md#pricing"), List.of(),
            List.of(), List.of("cursor"), c -> null, false, (text, values) -> { }, () -> { });
        check(drafted.copy.isEnabled() && drafted.request.getText().contains("Brief / section: briefs/orders.md#pricing")
            && drafted.request.getText().contains("If you cannot access the project files"), drafted.request.getText());
        // The editor can be tall or narrow: the numbered instructions must not
        // spread across its whole height or clip when their text wraps.
        SwingUtilities.invokeAndWait(() -> compactSkillExplanation(drafted));

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
                    check(button.getY() + button.getHeight() <= buttons.getHeight(), "button footer height too short at " + width);
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
