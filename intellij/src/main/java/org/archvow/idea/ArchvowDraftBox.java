package org.archvow.idea;

import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Draft with AI (1.2.0), around an answer field: an icon in its top-right corner. ✦ asks one of the project's AI
 * tools for a draft (archvow draft, text only) and puts it in the field, marked "AI draft · Claude Code"; ▾ chooses
 * another of the project's AI assistants, or copies the prompt instead. Nothing is saved: the person reads the draft,
 * changes it or not, and saves - then the save carries --drafted-with (and --unchanged when the text is the
 * draft's). Off for the project (ai_drafts in project.vow): the icon greyed, its hover says why. The VS Code page
 * does the same (media/workspace.js, draftCorner). Plain Swing, tested headless (DraftBoxRegression).
 */
final class ArchvowDraftBox extends JPanel {
    /** What the box needs from the page. */
    interface Host {
        /** {@code workspace --json} ai_drafts: {on, why_not, tools: [{name, title, program, installed}]}; null before archvow 3.2. */
        JsonObject aiDrafts();
        /** The component of the question shown, and the question as archvow draft names it (2, P1, S3); null: none. */
        String component();
        String ref();
        /** Runs archvow with these arguments (as the person), then gives its result on the UI thread. */
        void run(List<String> arguments, Consumer<ArchvowCli.Result> done);
        void copy(String text, String said);
        boolean french();
    }

    /** A draft in the field: the tool, its title, the draft's text, the field's text before it. */
    record Draft(String component, String ref, String tool, String title, String text, String before) { }

    private static String chosenTool = kept();

    private final Host host;
    private final javax.swing.text.JTextComponent field;
    private final JPanel corner = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
    private final JLabel tag = new JLabel();
    private final ArchvowWrapLabel note = new ArchvowWrapLabel();
    private final JLabel problem = new JLabel();
    private final JComponent fieldScroll;
    private Draft draft;
    private int asked;        // the draft asked for (a newer ask, or Stop, makes an older answer unwanted)
    private boolean drafting;
    private double angle;
    private final Timer spin = new Timer(60, e -> { angle += Math.PI / 12; corner.repaint(); });

    ArchvowDraftBox(Host host, javax.swing.text.JTextComponent field, JComponent fieldScroll) {
        super(new BorderLayout(0, 4));
        this.host = host;
        this.field = field;
        this.fieldScroll = fieldScroll;
        setOpaque(false);
        setName("archvow.draft.box");
        tag.setName("archvow.draft.tag");
        tag.setOpaque(true);
        tag.setFont(tag.getFont().deriveFont(Font.BOLD, 11f));
        tag.setForeground(new Color(0xE5, 0xA5, 0x4B));
        tag.setBackground(new Color(0xE5, 0xA5, 0x4B, 46));
        tag.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
        tag.setVisible(false);
        note.setForeground(new Color(0xE5, 0xC0, 0x7B));
        note.setVisible(false);
        problem.setForeground(new Color(0xE0, 0x6C, 0x75));
        problem.setVisible(false);
        corner.setOpaque(false);
        corner.setName("archvow.draft.corner");
        JLayeredPane layers = new JLayeredPane() {
            @Override public void doLayout() {
                fieldScroll.setBounds(0, 0, getWidth(), getHeight());
                Dimension size = corner.getPreferredSize();
                corner.setBounds(getWidth() - size.width - 8, 6, size.width, size.height);
            }
            @Override public Dimension getPreferredSize() { return fieldScroll.getPreferredSize(); }
            @Override public Dimension getMinimumSize() { return fieldScroll.getMinimumSize(); }
        };
        layers.add(fieldScroll, JLayeredPane.DEFAULT_LAYER);
        layers.add(corner, JLayeredPane.PALETTE_LAYER);
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        top.setOpaque(false);
        top.add(tag);
        JPanel under = new JPanel(new BorderLayout());
        under.setOpaque(false);
        under.add(note, BorderLayout.NORTH);
        under.add(problem, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        add(layers, BorderLayout.CENTER);
        add(under, BorderLayout.SOUTH);
        field.setMargin(new Insets(4, 6, 4, 64));
        refresh();
    }

    private static String kept() {
        try {
            String value = com.intellij.ide.util.PropertiesComponent.getInstance().getValue("archvow.ai.tool");
            return value == null ? "" : value;
        } catch (RuntimeException | LinkageError e) {
            return "";  // no IDE (the headless tests)
        }
    }

    private static void keep(String tool) {
        chosenTool = tool;
        try {
            com.intellij.ide.util.PropertiesComponent.getInstance().setValue("archvow.ai.tool", tool);
        } catch (RuntimeException | LinkageError e) {
            // no IDE: not kept
        }
    }

    private String t(String english, String french) { return host.french() ? french : english; }

    /** The project's AI tools that can draft, in its order. */
    List<JsonObject> tools() {
        JsonObject drafts = host.aiDrafts();
        return drafts == null ? List.of() : ArchvowGrammar.objects(drafts, "tools");
    }

    /** The AI that drafts: the one chosen last, else the first of the project's AI assistants installed here. */
    JsonObject tool() {
        JsonObject first = null;
        for (JsonObject t : tools()) {
            if (!ArchvowGrammar.flag(t, "installed")) continue;
            if (ArchvowGrammar.str(t, "name").equals(chosenTool)) return t;
            if (first == null) first = t;
        }
        return first;
    }

    private boolean on() {
        return why().isEmpty();
    }

    /** 1.4.0: archvow's reason it starts no draft here (not in ai_runs, a sandbox needed), or "". */
    String why() {
        JsonObject drafts = host.aiDrafts();
        if (drafts == null) return "";
        String said = drafts.has("why_not") && drafts.get("why_not").isJsonPrimitive() ? drafts.get("why_not").getAsString() : "";
        if (!said.isBlank()) return said;
        boolean off = drafts.has("on") && !drafts.get("on").getAsBoolean();
        return off ? t("archvow does not start AI drafts in this project (ai_runs in project.vow).",
            "archvow ne lance pas de brouillons IA dans ce projet (ai_runs dans project.vow).") : "";
    }

    /** The page shows another question (or the same after a reload): a draft of another question goes. */
    void follow() {
        if (draft != null && (!draft.component().equals(host.component()) || !draft.ref().equals(host.ref()))) {
            draft = null;
        }
        if (drafting && host.ref() == null) stopDrafting();
        problem.setVisible(false);
        refresh();
    }

    /** What a save sends: --drafted-with <tool> [--unchanged] when the answer started as an AI draft, else nothing. */
    List<String> savedWith(String answer) {
        if (draft == null || !draft.component().equals(host.component()) || !draft.ref().equals(host.ref())) return List.of();
        List<String> out = new ArrayList<>(List.of("--drafted-with", draft.tool()));
        if (answer.trim().equals(draft.text().trim())) out.add("--unchanged");
        return out;
    }

    /** After a save: the answer is the person's now; the draft's marks go. */
    void saved() {
        draft = null;
        refresh();
    }

    Draft draft() { return draft; }
    boolean drafting() { return drafting; }

    void ask(String toolName) {
        String component = host.component();
        String ref = host.ref();
        if (component == null || ref == null || toolName == null || toolName.isBlank()) return;
        int token = ++asked;
        drafting = true;
        problem.setVisible(false);
        spin.start();
        refresh();
        host.run(List.of("draft", component, ref, "--with", toolName, "--json"), result -> {
            if (token != asked || !drafting) return;  // stopped, or asked again meanwhile
            stopDrafting();
            JsonObject answer = ArchvowGrammar.parseObject(result.stdout);
            if (answer == null || !result.ok() || ArchvowGrammar.str(answer, "draft").isBlank()) {
                JsonObject error = answer != null && answer.has("error") && answer.get("error").isJsonObject() ? answer.getAsJsonObject("error") : null;
                String said = error != null ? ArchvowGrammar.str(error, "message") : result.output;
                problem.setText("<html>" + ArchvowExamples.esc(said == null || said.isBlank() ? t("No draft came back.", "Aucun brouillon n'est revenu.") : said) + "</html>");
                problem.setVisible(true);
                refresh();
                return;
            }
            String before = draft != null ? draft.before() : field.getText();
            draft = new Draft(component, ref, ArchvowGrammar.str(answer, "tool"), ArchvowGrammar.str(answer, "title"),
                ArchvowGrammar.str(answer, "draft"), before);
            field.setText(draft.text());
            field.setCaretPosition(0);
            refresh();
        });
    }

    private void stopDrafting() {
        drafting = false;
        spin.stop();
        angle = 0;
    }

    void stop() {
        asked++;
        stopDrafting();
        refresh();
    }

    void drop() {
        if (draft == null) return;
        field.setText(draft.before());
        draft = null;
        refresh();
    }

    private void copyPrompt() {
        String component = host.component();
        String ref = host.ref();
        if (component == null || ref == null) return;
        host.run(List.of("draft", component, ref, "--prompt", "--json"), result -> {
            JsonObject answer = ArchvowGrammar.parseObject(result.stdout);
            if (answer == null || ArchvowGrammar.str(answer, "prompt").isBlank()) {
                problem.setText(result.output);
                problem.setVisible(true);
                return;
            }
            host.copy(ArchvowGrammar.str(answer, "prompt"), t("The prompt is copied: paste it in your AI chat.", "Le prompt est copié : colle-le dans ton chat IA."));
        });
    }

    /** The hover of the icon: which AI drafts, or why it cannot. */
    String tip() {
        JsonObject drafts = host.aiDrafts();
        if (drafts == null) return "";  // archvow before 3.2: no Draft with AI
        if (!on()) return why();
        JsonObject tool = tool();
        if (tool != null) return t("Draft an answer with " + ArchvowGrammar.str(tool, "title") + " - you read it and save it. ▾ another AI",
            "Rédiger un brouillon avec " + ArchvowGrammar.str(tool, "title") + " - tu le relis et l'enregistres. ▾ une autre IA");
        List<String> programs = new ArrayList<>();
        for (JsonObject t : tools()) programs.add(ArchvowGrammar.str(t, "program"));
        String list = programs.isEmpty() ? "-" : String.join(", ", programs);
        return t("None of this project's AI assistants is on this machine (" + list + "). ▾ Copy the prompt instead.",
            "Aucun assistant IA de ce projet n'est sur cette machine (" + list + "). ▾ Copier le prompt à la place.");
    }

    /** The corner as it is now: ✦▾, or the spinner and ■, or ↻ and ×; the tag and the note with a draft. */
    void refresh() {
        corner.removeAll();
        boolean known = host.aiDrafts() != null && host.ref() != null;
        corner.setVisible(known);
        if (drafting) {
            corner.add(new IconButton(Kind.SPIN, t("Writing a draft…", "Écriture du brouillon…"), null));
            corner.add(new IconButton(Kind.STOP, t("Stop waiting for the draft", "Ne plus attendre le brouillon"), this::stop));
        } else if (draft != null) {
            corner.add(new IconButton(Kind.AGAIN, t("Another draft", "Un autre brouillon"), () -> ask(draft.tool())));
            corner.add(new IconButton(Kind.DROP, t("Drop the draft: the box as it was", "Abandonner le brouillon : la case comme avant"), this::drop));
        } else if (known) {
            boolean on = on() && field.isEnabled();
            JsonObject tool = tool();
            IconButton caret = new IconButton(Kind.CARET, tip(), null);
            // 1.4.0: off here, ✦ and ▾ open archvow's reason in words (not only in the hover), with Copy the prompt
            Runnable open = () -> menu().show(caret, caret.getWidth() - 260, caret.getHeight() + 2);
            IconButton spark = new IconButton(Kind.SPARK, tip(), !on() ? open : tool == null ? null : () -> ask(ArchvowGrammar.str(tool, "name")));
            spark.setName("archvow.draft.run");
            spark.setEnabled(field.isEnabled() && (!on() || tool != null));
            spark.dimmed = !on();
            caret.setName("archvow.draft.menu");
            caret.setEnabled(field.isEnabled());
            caret.dimmed = !on();
            caret.action = open;
            corner.add(spark);
            corner.add(caret);
        }
        tag.setText(draft == null ? "" : t("AI draft · ", "Brouillon IA · ") + draft.title());
        tag.setVisible(draft != null);
        note.setText(t("Read it and change what is wrong: when you save, it is your answer.",
            "Relis-le et corrige ce qui ne va pas : quand tu enregistres, c'est ta réponse."));
        note.setVisible(draft != null);
        fieldScroll.setBorder(draft != null || drafting
            ? BorderFactory.createMatteBorder(0, 3, 0, 0, new Color(0xE5, 0xA5, 0x4B)) : UIManager.getBorder("ScrollPane.border"));
        corner.revalidate();
        corner.repaint();
        revalidate();
        repaint();
    }

    /** ▾: the project's AI assistants (those not installed greyed), then Copy the prompt instead. */
    JPopupMenu menu() {
        JPopupMenu menu = new JPopupMenu();
        menu.setName("archvow.draft.popup");
        if (!on()) {
            JLabel why = new JLabel("<html><div style='width:280px'>" + why().replace("&", "&amp;").replace("<", "&lt;")
                + "</div></html>");
            why.setName("archvow.draft.why");
            why.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            menu.add(why);
            menu.addSeparator();
            JMenuItem prompt = new JMenuItem(t("Copy the prompt instead", "Copier le prompt à la place"));
            prompt.addActionListener(e -> copyPrompt());
            menu.add(prompt);
            return menu;
        }
        JLabel head = new JLabel(t("THIS PROJECT'S AI ASSISTANTS", "LES ASSISTANTS IA DE CE PROJET"));
        head.setFont(head.getFont().deriveFont(Font.BOLD, 10.5f));
        head.setForeground(UIManager.getColor("Label.disabledForeground"));
        head.setBorder(BorderFactory.createEmptyBorder(4, 10, 2, 10));
        menu.add(head);
        JsonObject current = tool();
        for (JsonObject t : tools()) {
            boolean installed = ArchvowGrammar.flag(t, "installed");
            boolean chosen = current != null && ArchvowGrammar.str(t, "name").equals(ArchvowGrammar.str(current, "name"));
            String program = ArchvowGrammar.str(t, "program");
            JMenuItem item = new JMenuItem((chosen ? "✓  " : "     ") + ArchvowGrammar.str(t, "title")
                + "      " + (installed ? program : t("no " + program + " command", "pas de commande " + program)));
            item.setEnabled(installed);
            if (!installed) item.setToolTipText(missing(program));
            String name = ArchvowGrammar.str(t, "name");
            item.addActionListener(e -> { keep(name); ask(name); });
            menu.add(item);
        }
        menu.addSeparator();
        JMenuItem prompt = new JMenuItem("     " + t("Copy the prompt instead", "Copier le prompt à la place"));
        prompt.addActionListener(e -> copyPrompt());
        menu.add(prompt);
        return menu;
    }

    /** Why an assistant is greyed: drafts use its command, which the IDE's own plugin does not give. */
    String missing(String program) {
        return t("Drafts use the " + program + " command, which is not on this computer (the IDE's plugin is not "
                + "enough). Install it, log in once, then reopen the project.",
            "Les brouillons passent par la commande " + program + ", absente de cet ordinateur (le plugin de l'IDE ne "
                + "suffit pas). Installe-la, connecte-toi une fois, puis rouvre le projet.");
    }

    enum Kind { SPARK, CARET, SPIN, STOP, AGAIN, DROP }

    /** A small flat icon button: drawn, with a hover background. */
    final class IconButton extends JComponent {
        final Kind kind;
        Runnable action;
        boolean dimmed;  // 1.4.0: clickable, but archvow starts no draft here (the click says why)
        private boolean over;

        IconButton(Kind kind, String tip, Runnable action) {
            this.kind = kind;
            this.action = action;
            setToolTipText(tip);
            if (getAccessibleContext() != null) getAccessibleContext().setAccessibleName(tip);
            setCursor(Cursor.getPredefinedCursor(action == null && kind != Kind.CARET ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(java.awt.event.MouseEvent e) { over = true; repaint(); }
                @Override public void mouseExited(java.awt.event.MouseEvent e) { over = false; repaint(); }
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (isEnabled() && IconButton.this.action != null) IconButton.this.action.run();
                }
            });
        }

        void click() { if (isEnabled() && action != null) action.run(); }

        @Override public Dimension getPreferredSize() { return new Dimension(kind == Kind.CARET ? 14 : 26, 26); }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                if (over && isEnabled() && kind != Kind.SPIN) {
                    g.setColor(ArchvowMotion.dark() ? new Color(0x3A, 0x3D, 0x42) : new Color(0xDF, 0xE1, 0xE5));
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                }
                Color muted = UIManager.getColor("Label.disabledForeground") != null ? UIManager.getColor("Label.disabledForeground") : Color.GRAY;
                Color violet = new Color(0xC9, 0xA0, 0xFF);
                if (!isEnabled() || dimmed) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .45f));
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                switch (kind) {
                    case SPARK -> { g.setColor(violet); g.fill(spark(cx - 8, cy - 8)); }
                    case SPIN -> {
                        g.setColor(new Color(0xE5, 0xA5, 0x4B));
                        g.fill(AffineTransform.getRotateInstance(angle, cx, cy).createTransformedShape(spark(cx - 8, cy - 8)));
                    }
                    case CARET -> {
                        g.setColor(muted);
                        Path2D p = new Path2D.Double();
                        p.moveTo(cx - 3, cy - 1.5); p.lineTo(cx + 3, cy - 1.5); p.lineTo(cx, cy + 2); p.closePath();
                        g.fill(p);
                    }
                    case STOP -> { g.setColor(muted); g.fillRect(cx - 4, cy - 4, 9, 9); }
                    case DROP -> {
                        g.setColor(muted);
                        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g.drawLine(cx - 4, cy - 4, cx + 4, cy + 4);
                        g.drawLine(cx + 4, cy - 4, cx - 4, cy + 4);
                    }
                    case AGAIN -> {
                        g.setColor(muted);
                        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g.drawArc(cx - 5, cy - 5, 10, 10, 60, 290);
                        g.drawLine(cx + 3, cy - 6, cx + 4, cy - 3);
                        g.drawLine(cx + 4, cy - 3, cx + 1, cy - 3);
                    }
                }
            } finally {
                g.dispose();
            }
        }
    }

    /** The ✦ of Draft with AI: a four-pointed star with a small one at its top right, in a 16 px box at (x, y). */
    static Shape spark(double x, double y) {
        Path2D p = new Path2D.Double();
        double[][] big = {{8, 1.5}, {9.4, 6.6}, {14.5, 8}, {9.4, 9.4}, {8, 14.5}, {6.6, 9.4}, {1.5, 8}, {6.6, 6.6}};
        double[][] small = {{13, 1.5}, {13.5, 3}, {15, 3.5}, {13.5, 4}, {13, 5.5}, {12.5, 4}, {11, 3.5}, {12.5, 3}};
        for (double[][] star : new double[][][] {big, small}) {
            p.moveTo(x + star[0][0], y + star[0][1]);
            for (int i = 1; i < star.length; i++) p.lineTo(x + star[i][0], y + star[i][1]);
            p.closePath();
        }
        return p;
    }
}
