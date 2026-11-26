package org.archvow.idea;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The launch form of an AI skill (1.3.0, the same as the VS Code page's {@code skillForm()}): on the left the target -
 * the component, or the brief section - at most how many questions, the buttons and the run; on the right what will
 * happen and archvow's instructions ({@code archvow skill --prompt}: the same text in both IDEs). Draft from a brief,
 * Find missing questions and Find risks run in the background with the AI picked in ▾ ({@code archvow skill});
 * Implement is handed to your own agent; Review runs {@code archvow review} with one of the project's reviewers. Plain
 * Swing and JSON, tested headless ({@code SkillFormRegression}): archvow is reached through {@link Host}.
 */
final class ArchvowSkillForm extends JPanel implements Scrollable {
    /** What the person chose: the request's text and the values it was made of. */
    interface Send { void send(String request, Map<String, String> values); }

    /** One of the project's AI assistants (or reviewers) that archvow can run here: workspace --json. */
    record Tool(String name, String title, String program, boolean installed) { }

    /** How a run ended: done or failed, archvow's sentence, whether it added something, whether there is a log. */
    record Ended(String status, String message, boolean added, boolean log) { }

    /** What the form asks of archvow and of the IDE (the panel; a test gives its own). */
    interface Host {
        /** archvow skill <skill> <target> --prompt: the instructions, given back on the UI thread. */
        void prompt(String skill, String target, String max, java.util.function.Consumer<String> text);
        /** Runs archvow (skill, or review) in the background; `ended` on the UI thread; returns how to stop it. */
        Runnable run(List<String> args, java.util.function.Consumer<Ended> ended);
        void showLog(boolean review);
        void openReport();
        /** Review them ›: the component's To review (Find risks: Safety), or the features (Draft from a brief). */
        void reviewAdded(String skill, String component);
        String keptTool();
        void keepTool(String tool);
    }

    final Map<String, String> values = new LinkedHashMap<>();
    final JTextArea request = new JTextArea(3, 30);
    final JButton copy;
    /** 1.2.0: copies the instructions, then opens the IDE's AI chat (JetBrains lets no plugin send to it): Implement. */
    final JButton openChat;
    private Runnable onOpenChat = () -> { };
    final JButton cancel;
    /** 1.3.0: ▶ Run with <AI> (▶ Review with <reviewer>), and ▾ for another one. */
    final JButton run = ArchvowSplitView.primary("");
    final JButton runMore = new JButton("▾");
    /** Under the buttons: the run going on, or how the last one ended. */
    final JPanel runState = new JPanel(new BorderLayout(8, 4));
    private final JsonObject skills;
    private final String skill;
    private final boolean french;
    private final JLabel blocked = new JLabel();
    /** 1.4.0: why archvow starts no run here, in words under the buttons (not only in their hover). */
    final ArchvowWrapLabel runWhy = new ArchvowWrapLabel();
    private final Function<String, String[]> blockedFor;
    private final List<Tool> tools;
    private final String whyNot;
    private final String base;
    private final Host host;
    private String tool = "";
    private Runnable stop;
    private Timer ticking;

    /** 1.4.2: the running clock stops with the form (an editor tab closed during a run kept it ticking). */
    @Override public void removeNotify() {
        super.removeNotify();
        if (ticking != null) ticking.stop();
    }

    @Override public void addNotify() {
        super.addNotify();
        if (ticking != null && stop != null) ticking.start();  // shown again while the run goes on
    }
    private String shownKey = "";

    /**
     * @param components the components of the project (the first one or {@code values.component} chosen)
     * @param briefs     the brief files and sections, for draft-spec
     * @param tools      who can run it here: the project's AI assistants (workspace ai_skills), for review its reviewers
     * @param whyNot     why archvow runs no skill here (archvow's own words: not in ai_runs, a sandbox needed), or null
     * @param base       the branch this one merges into (review)
     * @param blockedFor component -> why the skill cannot run on it yet ([en, fr]), or null
     */
    ArchvowSkillForm(JsonObject skills, String skill, Map<String, String> initial, List<String> components,
                     List<String> briefs, List<Tool> tools, String whyNot, String base, Function<String, String[]> blockedFor,
                     boolean french, Host host, Send onSend, Runnable onCancel) {
        super(new BorderLayout(0, 10));
        this.skills = skills;
        this.skill = skill;
        this.french = french;
        this.tools = tools == null ? List.of() : tools;
        this.whyNot = whyNot == null || whyNot.isBlank() ? null : whyNot;
        this.base = base == null ? "" : base;
        this.host = host;
        this.blockedFor = blockedFor == null ? c -> null : blockedFor;
        values.putAll(initial);
        values.putIfAbsent("max", "5");
        JsonObject def = def(skills, skill);
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        JPanel head = new JPanel(new BorderLayout(0, 3));
        // 1.2.1: the skill's name beside the title goes under it when the editor is narrow (one label was cut)
        JLabel title = new JLabel(pair(def, "label"));
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 4f));
        JLabel skillName = new JLabel("skill: archvow-" + skill);
        skillName.setFont(new Font(Font.MONOSPACED, Font.PLAIN, Math.round(title.getFont().getSize2D() - 4f)));
        skillName.setForeground(UIManager.getColor("Label.disabledForeground"));
        JPanel titleRow = new JPanel(new ArchvowWrapLayout(FlowLayout.LEFT, 0, 0));
        titleRow.setOpaque(false);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        titleRow.add(title);
        titleRow.add(skillName);
        head.add(titleRow, BorderLayout.NORTH);
        head.add(wrap(pair(def, "does")), BorderLayout.CENTER);
        blocked.setForeground(new Color(176, 118, 26));
        head.add(blocked, BorderLayout.SOUTH);
        add(head, BorderLayout.NORTH);

        // stacked, the controls are as tall as they need at the width they have now
        JPanel left = new JPanel() {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setName("archvow.skill.target");
        if ("review".equals(skill)) {
            JComponent what = wrap(this.base.isBlank()
                ? (french ? "Pas de branche de base : une relecture compare cette branche à celle où elle sera fusionnée."
                    : "No base branch found: a review compares this branch with the one it will merge into.")
                : (french ? "Relit les changements de cette branche depuis " + this.base + ". Le rapport va dans reviews/last-change.md ; il ne change jamais le code."
                    : "Reviews the changes of this branch since " + this.base + ". The report goes to reviews/last-change.md; it never changes code."));
            left.add(what);
            left.add(Box.createVerticalStrut(12));
        } else if ("brief".equals(str(def, "target"))) {
            JComboBox<String> brief = new JComboBox<>(briefs.toArray(String[]::new));
            brief.setEditable(true);
            brief.setSelectedItem(values.getOrDefault("brief", ""));
            brief.addActionListener(e -> { values.put("brief", String.valueOf(brief.getSelectedItem()).trim()); update(); });
            field(left, french ? "Le brief, ou une de ses sections" : "The brief, or one section of it", brief);
        } else {
            JComboBox<String> component = new JComboBox<>(components.toArray(String[]::new));
            if (!components.contains(values.getOrDefault("component", "")) && !components.isEmpty()) values.put("component", components.get(0));
            component.setSelectedItem(values.getOrDefault("component", ""));
            component.addActionListener(e -> { values.put("component", String.valueOf(component.getSelectedItem())); update(); });
            field(left, french ? "Composant" : "Component", component);
        }
        if (def != null && def.has("max") && def.get("max").getAsBoolean()) {
            ArchvowSegmented max = new ArchvowSegmented(List.of("3", "5", "10"), values.get("max"), true);
            max.onChange(() -> { values.put("max", max.value()); update(); });
            field(left, french ? "Au plus" : "At most", max);
        }

        JPanel right = new JPanel(new BorderLayout(0, 10));
        right.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(128, 128, 128, 90)),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        JLabel what = new JLabel(french ? "CE QUI VA SE PASSER" : "WHAT WILL HAPPEN");
        what.setFont(what.getFont().deriveFont(Font.BOLD, 11f));
        what.setAlignmentX(Component.LEFT_ALIGNMENT);

        // A BoxLayout in BorderLayout.CENTER stretches every wrapped JTextArea
        // vertically, creating huge gaps between steps at normal editor heights.
        // Keep the explanation as one preferred-height stack anchored to the top.
        JPanel explanation = new CompactExplanation();
        explanation.setName("archvow.skill.explanation");
        explanation.add(what);
        explanation.add(Box.createVerticalStrut(8));
        List<String> steps = steps(def);
        for (int i = 0; i < steps.size(); i++) explanation.add(wrap((i + 1) + ". " + steps.get(i)));
        explanation.add(Box.createVerticalStrut(10));
        JPanel highlights = new JPanel(new GridLayout(1, 2, 10, 0));
        highlights.setOpaque(false);
        highlights.setAlignmentX(Component.LEFT_ALIGNMENT);
        highlights.add(infoBox(french ? "L'IA peut" : "The AI can", pair(def, "may_add"), new Color(68, 115, 217)));
        highlights.add(infoBox(french ? "Toi, tu" : "You still", pair(def, "you"), new Color(40, 138, 84)));
        explanation.add(highlights);
        explanation.add(Box.createVerticalStrut(10));

        JPanel bottom = new JPanel(new BorderLayout(0, 3));
        bottom.setOpaque(false);
        bottom.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottom.add(new JLabel(french ? "Consignes à copier (pour toute IA)" : "Instructions to copy (for any AI)"), BorderLayout.NORTH);
        request.setEditable(false);
        request.setLineWrap(true);
        request.setWrapStyleWord(true);
        request.setFont(new Font(Font.MONOSPACED, Font.PLAIN, request.getFont().getSize()));
        request.setRows(7);
        request.setColumns(22);
        JScrollPane requestScroll = new JScrollPane(request);
        requestScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        requestScroll.setPreferredSize(new Dimension(220, 132));
        bottom.add(requestScroll, BorderLayout.CENTER);
        explanation.add(bottom);
        right.add(explanation, BorderLayout.CENTER);

        cancel = new JButton(french ? "Annuler" : "Cancel");
        cancel.addActionListener(e -> onCancel.run());
        copy = new JButton(french ? "Copier les consignes" : "Copy instructions");
        copy.addActionListener(e -> onSend.send(request.getText(), Map.copyOf(values)));
        openChat = ArchvowSplitView.primary(french ? "Copier et ouvrir le chat IA" : "Copy and open AI chat");
        openChat.setToolTipText(french ? "Copie, puis ouvre ta fenêtre de chat IA : colle (⌘V) et envoie. Ton agent écrit le code."
            : "Copies, then opens your AI chat window: paste (⌘V) and send. Your agent writes the code.");
        openChat.addActionListener(e -> { onSend.send(request.getText(), Map.copyOf(values)); onOpenChat.run(); });
        run.setName("archvow.skill.run");
        run.addActionListener(e -> startRun());
        runMore.setName("archvow.skill.runMore");
        runMore.setMargin(new Insets(2, 6, 2, 6));
        runMore.addActionListener(e -> runMenu().show(runMore, 0, runMore.getHeight()));
        String kept = host == null ? "" : host.keptTool();
        tool = this.tools.stream().filter(t -> t.name().equals(kept) && t.installed()).map(Tool::name).findFirst()
            .orElse(this.tools.stream().filter(Tool::installed).map(Tool::name).findFirst().orElse(""));
        JPanel buttons = new JPanel(new WrapActionLayout());
        buttons.setName("archvow.skill.actions");
        buttons.setOpaque(false);
        buttons.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { buttons.revalidate(); }
        });
        cancel.setBorderPainted(false);
        cancel.setContentAreaFilled(false);
        buttons.add(cancel);
        buttons.add(copy);
        if ("implement".equals(skill)) buttons.add(openChat);
        else {
            JPanel split = new JPanel(new FlowLayout(FlowLayout.LEFT, 1, 0));
            split.setOpaque(false);
            split.add(run);
            split.add(runMore);
            buttons.add(split);
        }
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        runState.setName("archvow.skill.runState");
        runState.setOpaque(false);
        runState.setVisible(false);
        runState.setAlignmentX(LEFT_ALIGNMENT);
        runWhy.setName("archvow.skill.why");
        runWhy.setOpaque(false);
        runWhy.setForeground(UIManager.getColor("Label.disabledForeground"));
        runWhy.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        runWhy.setAlignmentX(LEFT_ALIGNMENT);
        runWhy.setVisible(false);
        left.add(buttons);
        left.add(runWhy);
        left.add(runState);

        JPanel leftHost = new JPanel(new BorderLayout());
        leftHost.setOpaque(false);
        leftHost.add(left, BorderLayout.NORTH);
        JPanel rightHost = new JPanel(new BorderLayout()) {
            @Override public void doLayout() {
                // The explanation's natural height depends on the actual editor width.
                // Measure it at the new width before BorderLayout asks for its height,
                // otherwise narrowing the editor can clip the instructions.
                Insets in = right.getInsets();
                explanation.setSize(Math.max(1, getWidth() - in.left - in.right), explanation.getHeight());
                super.doLayout();
            }
        };
        rightHost.setOpaque(false);
        rightHost.add(right, BorderLayout.NORTH);
        // 1.2.1: side by side or stacked is decided when the body is laid out, at its width - a resize event
        // came after the first layout (and never headless): the explanation stayed a narrow column of words
        final boolean[] stacked = {false};
        JPanel body = new JPanel(new GridLayout(1, 2, 12, 0)) {
            @Override public void doLayout() {
                boolean narrow = getWidth() > 0 && getWidth() < STACK_BELOW;
                if (narrow != stacked[0]) {
                    stacked[0] = narrow;
                    if (narrow) {
                        // BoxLayout uses each row's natural height. GridLayout(2,1)
                        // would force the short controls to be as tall as the explanation.
                        right.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
                        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
                    } else setLayout(new GridLayout(1, 2, 12, 0));
                }
                super.doLayout();
            }
        };
        body.setName("archvow.skill.body");
        body.add(leftHost);
        body.add(rightHost);
        left.setAlignmentX(Component.LEFT_ALIGNMENT);
        right.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(body, BorderLayout.CENTER);
        update();
    }

    /** What will happen, in the order it happens: a run here, a handoff (Implement), a review. */
    private List<String> steps(JsonObject def) {
        if ("implement".equals(skill)) return List.of(
            french ? "Copie les consignes et donne-les à ton propre agent : il écrit le code et ses tests marqués."
                : "Copy the instructions and give them to your own agent: it writes the code and its marked tests.",
            pair(def, "reads"),
            french ? "La vérification dit ensuite ce qui suit la spec, et ce qui ne la suit pas." : "Check then says what follows the spec, and what does not.");
        if ("review".equals(skill)) return List.of(
            french ? "Lance-la ici avec un des relecteurs du projet, ou copie les consignes à un relecteur."
                : "Run it here with one of the project's reviewers, or copy the instructions to a reviewer.",
            pair(def, "reads"),
            french ? "Le rapport est dans reviews/last-change.md : tu corriges ou acceptes chaque constat."
                : "The report is in reviews/last-change.md: you fix or acknowledge each finding.");
        return List.of(
            french ? "Lance-le ici avec un des assistants IA du projet, ou copie les consignes à n'importe quelle IA."
                : "Run it here with one of the project's AI assistants, or copy the instructions to any AI.",
            pair(def, "reads"), pair(def, "may_add"),
            french ? "Ce qu'elle ajoute apparaît dans les onglets du composant, marqué IA et À relire, pour que tu le gardes, le changes ou le retires."
                : "What it adds appears in the component's tabs, marked AI and To review, for you to keep, change or remove.");
    }

    /** What it works on: the component, the brief, or the branch's change (review). */
    String target() {
        if ("review".equals(skill)) return base;
        return values.getOrDefault("brief".equals(str(def(skills, skill), "target")) ? "brief" : "component", "").trim();
    }

    private String key() {
        JsonObject def = def(skills, skill);
        boolean max = def != null && def.has("max") && def.get("max").getAsBoolean();
        return skill + "|" + target() + "|" + (max ? values.getOrDefault("max", "5") : "");
    }

    private Tool chosen() {
        return tools.stream().filter(t -> t.name().equals(tool)).findFirst().orElse(null);
    }

    /** ▾: the project's AI assistants (its reviewers), one not installed here greyed with its command. */
    JPopupMenu runMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.setName("archvow.skill.runMenu");
        JLabel head = new JLabel("review".equals(skill) ? (french ? "LES RELECTEURS DE CE PROJET" : "THIS PROJECT'S REVIEWERS")
            : (french ? "LES ASSISTANTS IA DE CE PROJET" : "THIS PROJECT'S AI ASSISTANTS"));
        head.setFont(head.getFont().deriveFont(Font.BOLD, 10.5f));
        head.setForeground(UIManager.getColor("Label.disabledForeground"));
        head.setBorder(BorderFactory.createEmptyBorder(4, 10, 2, 10));
        menu.add(head);
        for (Tool t : tools) {
            JMenuItem item = new JMenuItem((t.name().equals(tool) ? "✓  " : "     ") + t.title() + "      "
                + (t.installed() ? t.program() : (french ? "pas de commande " : "no ") + t.program() + (french ? "" : " command")));
            item.setEnabled(t.installed());
            if (!t.installed()) item.setToolTipText(french
                ? "Il passe par la commande " + t.program() + ", absente de cet ordinateur. Installe-la, connecte-toi une fois, puis rouvre le projet."
                : "It runs the " + t.program() + " command, which is not on this computer. Install it, log in once, then reopen the project.");
            item.addActionListener(e -> { tool = t.name(); if (host != null) host.keepTool(tool); update(); });
            menu.add(item);
        }
        return menu;
    }

    /** ▶: archvow skill (or review) in the background; the strip while it runs, then how it ended. */
    private void startRun() {
        Tool t = chosen();
        if (t == null || host == null || stop != null) return;
        boolean review = "review".equals(skill);
        List<String> args = new java.util.ArrayList<>();
        if (review) args.addAll(List.of("review", "--base", base, "--with", t.name(), "--json"));
        else {
            args.addAll(List.of("skill", skill, target()));
            JsonObject def = def(skills, skill);
            if (def != null && def.has("max") && def.get("max").getAsBoolean()) args.addAll(List.of("--max", values.getOrDefault("max", "5")));
            args.addAll(List.of("--with", t.name(), "--json"));
        }
        String doing = review ? (french ? "relit les changements depuis " + base : "is reviewing the changes since " + base)
            : (french ? "fait « " + pair(def(skills, skill), "label") + " » sur " + target() : "is doing \"" + pair(def(skills, skill), "label") + "\" on " + target());
        String component = values.getOrDefault("component", "");
        boolean[] over = {false};
        stop = () -> { };
        showRunning(t.title(), doing);
        Runnable halt = host.run(args, ended -> { over[0] = true; stop = null; showEnded(ended, review, component); });
        if (!over[0]) { stop = halt; update(); }
    }

    private void showRunning(String who, String doing) {
        runState.removeAll();
        runState.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0),
            BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(53, 116, 240, 140), 1, true),
                BorderFactory.createEmptyBorder(8, 10, 8, 10))));
        JLabel line = new JLabel("<html><b>" + escape(who) + "</b> " + escape(doing) + "</html>");
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setPreferredSize(new Dimension(80, 6));
        JLabel elapsed = new JLabel("0 s");
        elapsed.setName("archvow.skill.elapsed");
        elapsed.setForeground(UIManager.getColor("Label.disabledForeground"));
        long started = System.currentTimeMillis();
        if (ticking != null) ticking.stop();
        ticking = new Timer(1000, e -> elapsed.setText(elapsed(System.currentTimeMillis() - started)));
        ticking.start();
        JButton log = ArchvowSplitView.link(french ? "Voir le journal" : "Show log");
        log.addActionListener(e -> { if (host != null) host.showLog("review".equals(skill)); });
        JButton halt = new JButton(french ? "■ Arrêter" : "■ Stop");
        halt.setName("archvow.skill.stop");
        halt.addActionListener(e -> { if (stop != null) stop.run(); });
        JPanel tail = new JPanel(new ArchvowWrapLayout(FlowLayout.LEFT, 8, 0));
        tail.setOpaque(false);
        tail.add(bar);
        tail.add(elapsed);
        tail.add(log);
        tail.add(halt);
        runState.add(line, BorderLayout.NORTH);
        runState.add(tail, BorderLayout.CENTER);
        runState.setVisible(true);
        update();
    }

    /** Done: what was added and Review them › (the report ›); failed: the tool's reason, and Copy instructions instead. */
    void showEnded(Ended ended, boolean review, String component) {
        if (ticking != null) ticking.stop();
        stop = null;
        boolean failed = !"done".equals(ended.status());
        Color tone = failed ? new Color(240, 113, 120) : new Color(95, 184, 101);
        runState.removeAll();
        runState.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0),
            BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, tone), BorderFactory.createEmptyBorder(6, 10, 6, 10))));
        ArchvowWrapLabel said = new ArchvowWrapLabel();
        said.setName("archvow.skill.ended");
        said.setOpaque(false);
        said.setText((failed ? "✗ " : "✓ ") + ended.message()
            + (failed && !review ? (french ? "\nCopier les consignes donne la même tâche à une IA que tu lances toi-même."
                : "\nCopy instructions gives the same task to an AI you start yourself.") : ""));
        JPanel links = new JPanel(new ArchvowWrapLayout(FlowLayout.LEFT, 8, 0));
        links.setOpaque(false);
        if (!failed && review) {
            JButton open = ArchvowSplitView.link(french ? "Ouvrir le rapport ›" : "Open the report ›");
            open.addActionListener(e -> { if (host != null) host.openReport(); });
            links.add(open);
        } else if (!failed && ended.added()) {
            JButton them = ArchvowSplitView.link(french ? "Les relire ›" : "Review them ›");
            them.setName("archvow.skill.reviewAdded");
            them.addActionListener(e -> { if (host != null) host.reviewAdded(skill, component); });
            links.add(them);
        }
        if (ended.log()) {
            JButton log = ArchvowSplitView.link(french ? "Voir le journal" : "Show log");
            log.addActionListener(e -> { if (host != null) host.showLog(review); });
            links.add(log);
        }
        runState.add(said, BorderLayout.NORTH);
        runState.add(links, BorderLayout.CENTER);
        runState.setVisible(true);
        update();
    }

    private static String elapsed(long millis) {
        long s = Math.max(0, millis / 1000);
        return s < 60 ? s + " s" : (s / 60) + " min " + (s % 60) + " s";
    }

    /** What "Copy and open AI chat" opens after copying (the panel: the IDE's AI chat window). */
    void onOpenChat(Runnable action) { onOpenChat = action; }

    /** Under this width the controls go above the explanation, instead of two columns of a few words each. */
    static final int STACK_BELOW = 680;

    /** FlowLayout normally reports one row even when it has wrapped. Reserve the true height. */
    private static final class WrapActionLayout extends FlowLayout {
        WrapActionLayout() { super(FlowLayout.RIGHT, 8, 4); }
        @Override public Dimension preferredLayoutSize(Container target) {
            Dimension single = super.preferredLayoutSize(target);
            // the same rule as FlowLayout.layoutContainer, so the rows counted are the rows drawn
            int available = target.getWidth() - target.getInsets().left - target.getInsets().right - 2 * getHgap();
            if (available <= 0) return single;
            int x = 0, rowHeight = 0, height = 0;
            for (Component component : target.getComponents()) {
                if (!component.isVisible()) continue;
                Dimension d = component.getPreferredSize();
                if (x > 0 && x + d.width > available) {
                    height += rowHeight + getVgap();
                    x = 0;
                    rowHeight = 0;
                }
                x += (x > 0 ? getHgap() : 0) + d.width;
                rowHeight = Math.max(rowHeight, d.height);
            }
            height += rowHeight + 2 * getVgap();
            return new Dimension(single.width, Math.max(single.height, height + target.getInsets().top + target.getInsets().bottom));
        }
    }

    /** The viewport controls width, so the editor never needs horizontal scrolling. */
    @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(740, 520); }
    @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 16; }
    @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return 100; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }

    /**
     * Layout that measures wrapped instructions at the actual editor width. Unlike
     * BoxLayout, extra height is left below the instructions rather than divided
     * between them. A shorter editor wraps lines without clipping their bottoms.
     */
    private static final class CompactExplanation extends JPanel {
        CompactExplanation() {
            super(null);
            setOpaque(false);
        }

        @Override public Dimension getPreferredSize() {
            int width = getWidth() > 0 ? getWidth() : 440;
            return new Dimension(width, place(width, false));
        }

        @Override public void doLayout() { place(getWidth(), true); }

        private int place(int width, boolean layOut) {
            int y = 0;
            int available = Math.max(40, width);
            for (Component child : getComponents()) {
                int h;
                if (child instanceof JTextArea area) {
                    // JTextArea calculates its wrapped preferred height from width.
                    // Use a tall measurement bound, then set the exact measured height.
                    area.setSize(available, Short.MAX_VALUE);
                    h = area.getPreferredSize().height;
                } else {
                    h = child.getPreferredSize().height;
                }
                if (layOut) child.setBounds(0, y, available, h);
                y += h;
            }
            return y;
        }
    }

    /** The instructions (archvow's), the buttons and the "not yet" line, after each change. */
    private void update() {
        JsonObject def = def(skills, skill);
        boolean review = "review".equals(skill);
        String target = target();
        boolean ready = !target.isBlank();
        String key = key();
        if (!key.equals(shownKey)) {
            shownKey = key;
            if (!ready) request.setText(french ? "Choisis sur quoi il travaille." : "Choose what it works on.");
            else if (host != null) {
                request.setText(french ? "Chargement…" : "Loading…");
                host.prompt(skill, target, def != null && def.has("max") && def.get("max").getAsBoolean() ? values.getOrDefault("max", "5") : "",
                    text -> { if (key.equals(shownKey)) { request.setText(text); request.setCaretPosition(0); } });
            }
        }
        String[] reason = review || "brief".equals(str(def, "target")) ? null : blockedFor.apply(values.getOrDefault("component", ""));
        blocked.setText(reason == null ? "" : reason[french ? 1 : 0]);
        blocked.setVisible(reason != null);
        boolean go = ready && reason == null;
        copy.setEnabled(go);
        openChat.setEnabled(go);
        Tool t = chosen();
        boolean running = stop != null;
        String why = review ? (tools.isEmpty() ? (french ? "Aucun relecteur approuvé : un architecte en approuve un à l'init (archvow init <package> --force -a reviewers=codex)."
            : "No reviewer approved: an architect approves one at init (archvow init <package> --force -a reviewers=codex).") : null) : whyNot;
        String name = t == null ? "" : t.title();
        boolean says = why != null && !"implement".equals(skill);
        if (!(says ? "ⓘ " + why : "").equals(runWhy.getText())) runWhy.setText(says ? "ⓘ " + why : "");
        runWhy.setVisible(says);
        run.setText(review ? (name.isEmpty() ? (french ? "▶ Relire" : "▶ Review") : (french ? "▶ Relire avec " : "▶ Review with ") + name)
            : (name.isEmpty() ? (french ? "▶ Lancer" : "▶ Run") : (french ? "▶ Lancer avec " : "▶ Run with ") + name));
        run.setEnabled(why == null && go && t != null && !running);
        runMore.setEnabled(!tools.isEmpty() && (review || why == null));
        String tip = why != null ? why : running ? (french ? "Un lancement est en cours : attends-le, ou arrête-le." : "A run is going on: wait for it, or stop it.")
            : t == null ? (french ? "Aucun de ces outils n'est sur cet ordinateur. ▾ les montre." : "None of them is on this computer. ▾ shows them.")
            : review ? (french ? name + " relit le changement en arrière-plan et écrit le rapport. Il ne change jamais le code."
                : name + " reviews the change in the background and writes the report. It never changes code.")
            : (french ? name + " le fait en arrière-plan, pendant que tu continues. Ce qu'il ajoute est de l'IA, À relire."
                : name + " does it in the background, while you go on. What it adds is the AI's, To review.");
        run.setToolTipText(tip);
        runMore.setToolTipText(why != null ? why : (french ? "Choisir l'IA" : "Choose the AI"));
        // Copy is the main button when nothing can run here
        copy.setFont(copy.getFont().deriveFont(run.isEnabled() || "implement".equals(skill) ? Font.PLAIN : Font.BOLD));
        revalidate();
        repaint();
    }

    /** Why ▶ cannot run (for a test, and the tooltip). */
    String runTip() { return run.getToolTipText(); }

    static JsonObject def(JsonObject skills, String skill) {
        JsonObject all = skills == null ? null : skills.getAsJsonObject("skills");
        return all != null && all.has(skill) ? all.getAsJsonObject(skill) : null;
    }

    /** A [en, fr] pair of the skill's definition, in the person's language. */
    String pair(JsonObject def, String key) {
        return pair(def, key, french);
    }

    static String pair(JsonObject def, String key, boolean french) {
        JsonElement v = def == null ? null : def.get(key);
        if (v == null || !v.isJsonArray()) return "";
        JsonArray a = v.getAsJsonArray();
        return a.size() == 0 ? "" : a.get(french && a.size() > 1 ? 1 : 0).getAsString();
    }

    private static String str(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    private static JPanel infoBox(String title, String text, Color accent) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);
        box.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ArchvowMotion.blend(UIManager.getColor("Panel.background"), accent, .45), 1, true),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        JLabel head = new JLabel(title);
        head.setFont(head.getFont().deriveFont(Font.BOLD));
        head.setForeground(accent);
        head.setAlignmentX(LEFT_ALIGNMENT);
        JComponent body = wrap(text);
        body.setAlignmentX(LEFT_ALIGNMENT);
        box.add(head);
        box.add(Box.createVerticalStrut(4));
        box.add(body);
        return box;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void field(JPanel panel, String label, JComponent control) {
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        l.setAlignmentX(LEFT_ALIGNMENT);
        control.setAlignmentX(LEFT_ALIGNMENT);
        if (control instanceof JComboBox<?>) control.setMaximumSize(new Dimension(340, control.getPreferredSize().height));
        panel.add(l);
        panel.add(Box.createVerticalStrut(4));
        panel.add(control);
        panel.add(Box.createVerticalStrut(12));
    }

    private static JComponent wrap(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        area.setFont(UIManager.getFont("Label.font"));
        area.setAlignmentX(LEFT_ALIGNMENT);
        return area;
    }
}
