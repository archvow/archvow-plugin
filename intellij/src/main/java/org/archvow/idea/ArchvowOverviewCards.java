package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Overview's cards (0.25.0), drawn as the VS Code page draws them (media/workspace.js: firstStepsCard,
 * architectureView, checksCard; the design in docs): First steps, the project's commands (Next and Check named,
 * More holding the rest), Architecture with its own items, Checks with the first finding. Plain Swing: no
 * IntelliJ class, so the plain regressions draw them (OverviewCardsRegression).
 */
final class ArchvowOverviewCards {
    private ArchvowOverviewCards() {}

    /** What the cards do; the panel gives the real actions. */
    interface Actions {
        void openComponents();
        /** 0: the open items, 1: the done items (the components with such work). */
        void openItems(int list);
        void openProjectItems();
        void openChecks();
        void openFile(String path, int line);
        void step(ArchvowOverview.FirstStep step);
        void tryExample();
    }

    static boolean dark() { return ArchvowMotion.dark(); }
    static Color border() { return dark() ? new Color(75, 75, 80) : new Color(208, 210, 214); }
    static Color muted() { return dark() ? new Color(161, 161, 170) : new Color(100, 106, 115); }
    static Color text() { return dark() ? new Color(228, 228, 231) : new Color(34, 43, 55); }
    static Color yellow() { return dark() ? new Color(233, 196, 106) : new Color(176, 126, 18); }
    static Color link() { return dark() ? new Color(233, 196, 106) : new Color(29, 99, 205); }
    static Color bad() { return dark() ? new Color(244, 135, 113) : new Color(196, 43, 28); }
    static Color warn() { return dark() ? new Color(226, 192, 141) : new Color(156, 89, 18); }
    static Color good() { return dark() ? new Color(137, 209, 133) : new Color(29, 149, 83); }
    static Color well() { return dark() ? new Color(42, 42, 47) : new Color(242, 243, 245); }
    static Color primary() { return new Color(14, 99, 156); }

    static String t(boolean fr, String en, String french) { return fr ? french : en; }

    static JLabel label(String text, Color color, int style, float size) {
        JLabel l = new JLabel(text);
        l.setForeground(color);
        l.setFont(l.getFont().deriveFont(style, size));
        return l;
    }

    /** A wrapped sentence (the step details and the hints): it takes the card's width and as many lines as it needs. */
    static JTextArea wrapped(String text, Color color, float size) {
        JTextArea area = new JTextArea(text);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setBorder(BorderFactory.createEmptyBorder());
        area.setForeground(color);
        area.setFont(UIManager.getFont("Label.font") == null ? area.getFont().deriveFont(size) : UIManager.getFont("Label.font").deriveFont(size));
        area.setAlignmentX(0f);
        return area;
    }

    static JButton link(String text, Runnable run) {
        JButton b = new JButton(text);
        b.setForeground(link());
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setBorder(BorderFactory.createEmptyBorder());
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> run.run());
        return b;
    }

    /** A filled button: `main` the blue one that does the current step. */
    static JButton button(String text, boolean main, Runnable run) {
        JButton b = new JButton(text);
        b.addActionListener(e -> run.run());
        b.setFocusPainted(false);
        if (main) {
            b.setBackground(primary());
            b.setForeground(Color.WHITE);
            b.setFont(b.getFont().deriveFont(Font.BOLD));
            b.putClientProperty("JButton.buttonType", "default");
        }
        return b;
    }

    /** A card: the line border with rounded corners, 14/16 padding, its rows one under the other. */
    static JPanel card(Color line, int thickness, Color fill) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(line, thickness, true),
            BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        card.setOpaque(fill != null);
        if (fill != null) card.setBackground(fill);
        card.setAlignmentX(0f);
        return card;
    }

    /** A row: `west` left, `east` right, as the cards' heads ("Checks" ... "Open Checks →"). */
    static JPanel row(Component west, Component east) {
        JPanel row = new Line(new BorderLayout(12, 0));
        if (west != null) row.add(west, BorderLayout.WEST);
        if (east != null) row.add(east, BorderLayout.EAST);
        return row;
    }

    static Component gap(int h) { return Box.createVerticalStrut(h); }

    /** A row of a card: as wide as the card, as tall as its content at that width (a wrapped sentence grows it). */
    static final class Line extends JPanel {
        Line(LayoutManager layout) { super(layout); setOpaque(false); setAlignmentX(0f); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
    }

    /** The step's mark: its number in a circle (the current one bold and yellow), a tick once done. */
    static final class StepMark extends JComponent {
        private final String text;
        private final boolean now;
        private final boolean done;
        StepMark(String text, boolean now, boolean done) {
            this.text = text; this.now = now; this.done = done;
            setPreferredSize(new Dimension(22, 22));
            setMinimumSize(new Dimension(22, 22));
            setMaximumSize(new Dimension(22, 22));
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                Color ink = done ? good() : now ? yellow() : muted();
                g.setColor(ink);
                g.setStroke(new BasicStroke(now ? 2f : 1f));
                g.drawOval(1, 1, 20, 20);
                g.setFont(getFont().deriveFont(now ? Font.BOLD : Font.PLAIN, 12f));
                FontMetrics m = g.getFontMetrics();
                String s = done ? "✓" : text;
                g.drawString(s, 11 - m.stringWidth(s) / 2f, 11 + (m.getAscent() - m.getDescent()) / 2f);
            } finally { g.dispose(); }
        }
    }

    /** First steps: the five, "N of 5 done", the current one with its button; null steps: no card. */
    static JPanel firstSteps(ArchvowOverview.FirstSteps f, boolean fr, Actions actions) {
        if (f == null) return null;
        JPanel card = card(yellow(), 2, dark() ? new Color(42, 38, 24) : new Color(255, 250, 235));
        card.add(row(label(t(fr, "First steps", "Premiers pas"), text(), Font.BOLD, 14f),
            label(t(fr, f.done() + " of " + f.steps().size() + " done", f.done() + " sur " + f.steps().size() + " faits"), muted(), Font.PLAIN, 12.5f)));
        for (int i = 0; i < f.steps().size(); i++) {
            ArchvowOverview.FirstStep step = f.steps().get(i);
            boolean now = i == f.current();
            card.add(gap(i == 0 ? 12 : 14));
            JPanel line = new Line(new BorderLayout(12, 0));
            JPanel markBox = new JPanel(new BorderLayout());
            markBox.setOpaque(false);
            markBox.add(new StepMark(String.valueOf(i + 1), now, step.done()), BorderLayout.NORTH);
            line.add(markBox, BorderLayout.WEST);
            JPanel words = new JPanel();
            words.setOpaque(false);
            words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
            words.add(label(step.title(), step.done() ? muted() : text(), step.done() ? Font.PLAIN : Font.BOLD, 13f));
            words.add(gap(2));
            JTextArea detail = wrapped(step.detail(), muted(), 12f);
            detail.setToolTipText(step.command());
            words.add(detail);
            line.add(words, BorderLayout.CENTER);
            if (now) {
                String verb = stepVerb(step, fr);
                if (!verb.isBlank()) {
                    JPanel holder = new JPanel(new GridBagLayout());
                    holder.setOpaque(false);
                    holder.add(button(verb, true, () -> actions.step(step)));
                    line.add(holder, BorderLayout.EAST);
                }
            }
            card.add(line);
        }
        card.add(gap(12));
        JSeparator rule = new JSeparator();
        rule.setForeground(border());
        rule.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        rule.setAlignmentX(0f);
        card.add(rule);
        card.add(gap(10));
        card.add(wrapped(t(fr, "Each step is read from the spec and Git, on this machine. The card goes once the five are done.",
            "Chaque étape est lue dans la spec et Git, sur cette machine. La carte part une fois les cinq faites."), muted(), 12f));
        JButton tryIt = link(t(fr, "Try archvow on an example first", "Essayer archvow sur un exemple d'abord"), actions::tryExample);
        tryIt.setAlignmentX(0f);
        card.add(tryIt);
        return card;
    }

    /** The current step's button: what the page's firstStepsCard() shows. Empty: no button (Commit). */
    static String stepVerb(ArchvowOverview.FirstStep step, boolean fr) {
        if (step.command().startsWith("archvow specify ")) return t(fr, "Specify", "Spécifier");
        return switch (step.step()) {
            case "feature" -> t(fr, "Add feature", "Ajouter une feature");
            case "component" -> t(fr, "Add component", "Ajouter un composant");
            case "build" -> "Check";
            default -> "";
        };
    }

    /** A bar of `percent` filled in yellow, 6 pixels high. */
    static final class Bar extends JPanel {  // a JPanel: a bare JComponent has no accessible context
        private final int percent;
        Bar(int percent, String accessible) {
            this.percent = percent;
            setOpaque(false);
            setPreferredSize(new Dimension(100, 6));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
            setAlignmentX(0f);
            getAccessibleContext().setAccessibleName(accessible);
            setToolTipText(accessible);
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(dark() ? new Color(58, 58, 64) : new Color(226, 228, 232));
                g.fillRoundRect(0, 0, getWidth(), 6, 6, 6);
                if (percent > 0) {
                    g.setColor(yellow());
                    g.fillRoundRect(0, 0, Math.max(6, getWidth() * percent / 100), 6, 6, 6);
                }
            } finally { g.dispose(); }
        }
    }

    /** Architecture: the components, its own items done (decisions, questions, tasks) and the links to them. */
    static JPanel architecture(int components, int done, int open, int projectItems, boolean firstStepsShown, boolean fr, Actions actions) {
        JPanel card = card(border(), 1, null);
        card.add(row(label("Architecture", text(), Font.BOLD, 14f),
            link(t(fr, "Open Components for details →", "Voir les composants en détail →"), actions::openComponents)));
        card.add(gap(10));
        card.add(label(components + t(fr, " components", " composants"), dark() ? new Color(196, 196, 204) : text(), Font.PLAIN, 13f));
        card.add(gap(10));
        int total = done + open;
        card.add(row(label(t(fr, "Items done", "Éléments faits"), text(), Font.PLAIN, 13f),
            label(t(fr, done + " of " + total, done + " sur " + total), text(), Font.BOLD, 13f)));
        card.add(gap(10));
        card.add(new Bar(ArchvowOverview.percent(done, total), t(fr, done + " of " + total + " items done", done + " éléments faits sur " + total)));
        if (open > 0 && !firstStepsShown) {
            card.add(gap(10));
            card.add(wrapped(t(fr, open + " open: decisions, questions and tasks. Check findings count in Checks.",
                open + " ouverts : décisions, questions et tâches. Les constats des contrôles comptent dans Contrôles."), muted(), 12f));
        }
        card.add(gap(10));
        JPanel links = new Line(new FlowLayout(FlowLayout.LEFT, 16, 0));
        ((FlowLayout) links.getLayout()).setHgap(0);
        links.add(link(t(fr, "Open items (" + open + ")", "Éléments ouverts (" + open + ")"), () -> actions.openItems(0)));
        links.add(Box.createHorizontalStrut(16));
        links.add(link(t(fr, "Done items (" + done + ")", "Éléments faits (" + done + ")"), () -> actions.openItems(1)));
        if (projectItems > 0) {
            links.add(Box.createHorizontalStrut(16));
            JButton project = link(t(fr, "Project items (" + projectItems + ")", "Éléments projet (" + projectItems + ")"), actions::openProjectItems);
            project.setToolTipText(t(fr, "Project-wide items, not attributed to components", "Éléments du projet non attribués aux composants"));
            links.add(project);
        }
        card.add(links);
        return card;
    }

    /** "10 errors · 20 warnings", red and amber when there are some, green when there are none. */
    static JComponent totals(int errors, int warnings, boolean fr) {
        JPanel line = new Line(new FlowLayout(FlowLayout.LEFT, 0, 0));
        line.add(label(errors + t(fr, " errors", " erreurs"), errors > 0 ? bad() : good(), errors > 0 ? Font.BOLD : Font.PLAIN, 13f));
        line.add(label(" · ", muted(), Font.PLAIN, 13f));
        line.add(label(warnings + t(fr, " warnings", " avertissements"), warnings > 0 ? warn() : errors > 0 ? muted() : good(), Font.PLAIN, 13f));
        return line;
    }

    /** Checks: the totals and, when there is one, the first finding - its rule in words, where, Open file. */
    static JPanel checks(int errors, int warnings, ArchvowOverview.Finding first, boolean fr, Actions actions) {
        JPanel card = card(border(), 1, null);
        JButton open = link(t(fr, "Open Checks →", "Voir les contrôles →"), actions::openChecks);
        if (first == null) {
            JPanel words = new JPanel();
            words.setOpaque(false);
            words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
            words.add(label(t(fr, "Checks", "Contrôles"), text(), Font.BOLD, 14f));
            words.add(gap(4));
            words.add(totals(errors, warnings, fr));
            card.add(row(words, open));
            return card;
        }
        card.add(row(label(t(fr, "Checks", "Contrôles"), text(), Font.BOLD, 14f), open));
        card.add(gap(10));
        card.add(totals(errors, warnings, fr));
        card.add(gap(10));
        JPanel box = new Line(new BorderLayout(12, 0));
        box.setBackground(well());
        box.setOpaque(true);
        box.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JPanel words = new JPanel();
        words.setOpaque(false);
        words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
        words.add(label(t(fr, "First finding", "Premier constat"), muted(), Font.PLAIN, 12f));
        words.add(gap(2));
        words.add(label(first.title(), text(), Font.BOLD, 13f));
        words.add(gap(2));
        JLabel where = label(first.where(), muted(), Font.PLAIN, 12f);
        where.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        where.setToolTipText(first.file() + (first.line() > 0 ? ":" + first.line() : ""));
        words.add(where);
        box.add(words, BorderLayout.CENTER);
        JPanel holder = new JPanel(new GridBagLayout());
        holder.setOpaque(false);
        holder.add(first.file().isBlank() ? button(t(fr, "Open Checks", "Voir les contrôles"), false, actions::openChecks)
            // named by what the file is (Open source, Open vow, Show configuration folder...), as in the Checks list
            : button(ArchvowCheckActions.fileLabel(first.file(), fr), false, () -> actions.openFile(first.file(), first.line())));
        box.add(holder, BorderLayout.EAST);
        card.add(box);
        return card;
    }

    /** One project command of the bar: an icon and its name (Next, Check), or More with its arrow. */
    static JButton command(String icon, String label, Runnable run) {
        JButton b = new JButton(label);
        if (!icon.isBlank()) b.setIcon(ArchvowUiIcons.iconFor(b, icon));
        b.setIconTextGap(8);
        b.setFocusPainted(false);
        b.setMargin(new Insets(4, 12, 4, 12));
        b.getAccessibleContext().setAccessibleName(label);
        b.setToolTipText(label);
        b.addActionListener(e -> run.run());
        return b;
    }

    /** A command of More's menu: what it does and the icon that goes with it. */
    record MenuCommand(String icon, String label, Runnable run) {}

    /** The project's commands: Next and Check named, then More ▾ with the others (Doctor, Audit, Briefs,
     *  Budget / Next, Diff, AI review), each with its icon - as the VS Code page's projectToolbar(). */
    static JPanel toolbar(Runnable next, Runnable check, List<MenuCommand> more, boolean fr, Consumer<JPopupMenu> shown) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        bar.setOpaque(false);
        bar.setAlignmentX(0f);
        bar.add(command("arrow", "Next", next));
        bar.add(Box.createHorizontalStrut(8));
        bar.add(command("check", "Check", check));
        bar.add(Box.createHorizontalStrut(8));
        String moreLabel = t(fr, "More project commands", "Autres commandes du projet");
        JButton moreButton = new JButton(t(fr, "More", "Plus"));
        moreButton.setIcon(ArchvowUiIcons.iconFor(moreButton, "down"));
        moreButton.setHorizontalTextPosition(SwingConstants.LEFT);
        moreButton.setIconTextGap(6);
        moreButton.setFocusPainted(false);
        moreButton.setMargin(new Insets(4, 12, 4, 12));
        moreButton.setToolTipText(moreLabel);
        moreButton.getAccessibleContext().setAccessibleName(moreLabel);
        JPopupMenu menu = new JPopupMenu();
        for (MenuCommand c : more) {
            JMenuItem item = new JMenuItem(c.label());
            item.setIcon(ArchvowUiIcons.iconFor(item, c.icon()));
            item.setIconTextGap(10);
            item.addActionListener(e -> c.run().run());
            menu.add(item);
        }
        moreButton.addActionListener(e -> {
            menu.show(moreButton, 0, moreButton.getHeight() + 4);
            if (shown != null) shown.accept(menu);
        });
        bar.add(moreButton);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, bar.getPreferredSize().height));
        return bar;
    }
}
