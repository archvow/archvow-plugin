package org.archvow.idea;

import javax.swing.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 0.17.0, a calmer component tab: the filter is a line of words under the views (To review only when the AI
 * added something, no Open in Rules), a done view says where the work is, a save moves to the next open item
 * (Clarify is no button any more), the secondary actions are links, and the action row has four quiet buttons.
 */
public final class ListFilterRegression {
    public static void main(String[] args) throws Exception {
        // the words of the line
        check(ArchvowListFilter.words(0, 2, 1).stream().map(w -> w[0]).toList(), List.of("all", "open", "review"), "decisions with AI items");
        check(ArchvowListFilter.words(0, 2, 0).stream().map(w -> w[0]).toList(), List.of("all", "open"), "no AI item: no To review");
        check(ArchvowListFilter.words(4, 0, 1).stream().map(w -> w[0]).toList(), List.of("all", "review"), "rules: nothing to answer, no Open");
        check(ArchvowListFilter.words(2, 3, 2).get(1)[3], "3", "the open count");
        check(ArchvowListFilter.words(2, 3, 2).get(2)[2], "À relire", "French");
        if (ArchvowListFilter.applies(1) || ArchvowListFilter.applies(3) || !ArchvowListFilter.applies(4))
            throw new AssertionError("the line belongs to Decisions, Safety and Rules");
        // a filter that no longer makes sense goes back to All
        check(ArchvowListFilter.normalized("review", 0, 2, 0), "all", "To review with nothing to review");
        check(ArchvowListFilter.normalized("open", 0, 0, 1), "all", "Open with nothing open");
        check(ArchvowListFilter.normalized("open", 4, 3, 1), "all", "Open in Rules");
        check(ArchvowListFilter.normalized("review", 2, 0, 1), "review", "To review kept");
        // a done view says where the work is
        int[] pending = {0, 0, 2, 1, 0, 0};
        int[] total = {4, 0, 3, 1, 2, 0};
        check(ArchvowListFilter.doneThenGoTo(0, pending, total, 0), 2, "Decisions done -> Safety");
        check(ArchvowListFilter.doneThenGoTo(2, pending, total, 0), -2, "Safety still open: nothing said");
        check(ArchvowListFilter.doneThenGoTo(0, new int[6], total, 3), 7, "only Other left");
        check(ArchvowListFilter.doneThenGoTo(0, new int[6], total, 0), -1, "everything done");
        check(ArchvowListFilter.doneThenGoTo(1, pending, total, 0), -2, "an empty view says nothing");
        check(ArchvowListFilter.doneThenGoTo(4, pending, total, 0), -2, "Rules have nothing to finish");
        // after a save, the next open card, going round
        List<ArchvowItemCards.Card> cards = List.of(card("1", false), card("2", true), card("3", false), card("4", true));
        check(ArchvowItemCards.nextOpenAfter(cards, 1), 3, "next open after 2");
        check(ArchvowItemCards.nextOpenAfter(cards, 3), 1, "goes round");
        check(ArchvowItemCards.nextOpenAfter(List.of(card("1", false), card("2", false)), 0), -1, "none open");
        // a link is a word: no border, no filled area
        JButton link = ArchvowSplitView.link("Utiliser la suggestion");
        if (link.isBorderPainted() || link.isContentAreaFilled()) throw new AssertionError("a link has no border nor fill");
        // 0.17.1: the menus are rows - a heading, a line between the groups, hover on the enabled rows only
        Runnable none = () -> {};
        JPopupMenu menu = ArchvowMenu.create("HEAD", List.of(
            List.of(new ArchvowMenu.Item("Find risks", "what it does", "analyze", none),
                new ArchvowMenu.Item("Implement", "Not yet: 2 decisions to make", "implement", false, false, "", none)),
            List.of(new ArchvowMenu.Item("Copy the card", "for another AI", "prompt", none))));
        check(menu.getComponentCount(), 5, "heading + 2 rows + a line + 1 row");
        JComponent enabled = (JComponent) menu.getComponent(1), disabled = (JComponent) menu.getComponent(2);
        if (enabled.getMouseListeners().length == 0) throw new AssertionError("an enabled row reacts to the mouse (hover, click)");
        if (disabled.getMouseListeners().length != 0) throw new AssertionError("a row that cannot run does nothing");
        if (((JComponent) menu.getComponent(3)).getPreferredSize().height > 12) throw new AssertionError("the line between groups is thin");
        // the action row: four quiet buttons, the AI menu ends with the card for another AI
        String panel = Files.readString(Path.of("src/main/java/org/archvow/idea/ArchvowPanel.java"), StandardCharsets.UTF_8);
        for (String gone : List.of("\"Clarify\", \"Clarifier\"", "\"+ Component\", \"+ Composant\"", "\"AI skills ▾\"", "originFilterButtons", "saveMore",
            "headAndSuggestion.add(suggestStrip"))
            if (panel.contains(gone)) throw new AssertionError("still in the panel: " + gone);
        for (String there : List.of("\"+ Add ▾\", \"+ Ajouter ▾\"", "\"✦ AI spec ▾\"", "Copier la fiche pour une autre IA",
            "Enregistrer comme proposition", "nextOpenAfter", "ArchvowMenu.create", "answerButtons(List.of(proposeInline)"))
            if (!panel.contains(there)) throw new AssertionError("missing in the panel: " + there);
        // 0.17.2: the user's order - AI (the filled one), Add, Edit spec, Open code
        int ai = panel.indexOf("actions.addAction(aiSkills)"), add = panel.indexOf("actions.addAction(add)"),
            edit = panel.indexOf("\"Edit spec\", \"Modifier la spec\"),\n"), open = panel.indexOf("\"Open code\", \"Ouvrir le code\"), this::openCode)");
        if (!(ai > 0 && ai < add && add < edit && edit < open)) throw new AssertionError("the actions are not AI, Add, Edit spec, Open code: " + ai + " " + add + " " + edit + " " + open);
        if (!panel.contains("JButton aiSkills = ArchvowSplitView.primary(")) throw new AssertionError("AI ▾ is the filled button");
        // 0.17.3: + Add ▾ shows its own "+" only (no ⊕ pictogram beside it); the AI button says "AI spec"
        JButton plain = new JButton("+ Ajouter ▾");
        ArchvowUiIcons.decorate(plain);
        if (plain.getIcon() == null) throw new AssertionError("a button saying Ajouter gets its pictogram by default");
        JButton addButton = new JButton("+ Ajouter ▾");
        addButton.putClientProperty(ArchvowUiIcons.NO_ICON, Boolean.TRUE);
        ArchvowUiIcons.decorate(addButton);
        if (addButton.getIcon() != null) throw new AssertionError("+ Add ▾ has an icon beside its +");
        if (!panel.contains("add.putClientProperty(ArchvowUiIcons.NO_ICON, Boolean.TRUE)")) throw new AssertionError("+ Add ▾ is not marked NO_ICON");
        if (panel.contains("\"✦ AI ▾\"") || panel.contains("\"✦ IA ▾\"")) throw new AssertionError("the AI button still says AI ▾");
        // 0.17.3: the command form's head as VS Code - the title, then [archvow <command>] - what it does; no
        // scrolled monospaced box (it clipped the lines and ran under the title in IntelliJ)
        if (panel.contains("scroll(commandHelp)") || panel.contains("commandHelp.setText(\"archvow \" + name + \"\\n\""))
            throw new AssertionError("the command's line is still a scrolled text box");
        for (String head : List.of("commandChip.setText(\"archvow \" + name)", "commandHelp.setText(\"- \" + command.description",
            "commandNote.setVisible(!formHelpExtra.isBlank())", "head.add(commandLine, BorderLayout.CENTER)"))
            if (!panel.contains(head)) throw new AssertionError("the command form's head lacks: " + head);
        System.out.println("OK: the list line, a done view, the next open item, links, four quiet actions, the command head");
    }

    private static ArchvowItemCards.Card card(String n, boolean open) {
        return new ArchvowItemCards.Card(n, "q" + n, open, open ? "Open" : "", open ? "" : "a", "", "");
    }

    private static void check(Object actual, Object expected, String what) {
        if (!expected.equals(actual)) throw new AssertionError(what + ": " + actual + " instead of " + expected);
    }
}
