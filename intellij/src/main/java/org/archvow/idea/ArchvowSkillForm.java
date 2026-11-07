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
 * The launch form of an AI skill (0.16.0, design A, the same as the VS Code page's {@code skillForm()}): on the
 * left what the AI reads - the component, or the brief section - at most how many questions and where the
 * request goes; on the right what will happen, what the AI may add, what the person still decides and the exact
 * request. Nothing runs here: the request goes to the assistant (here: the clipboard, IntelliJ has no way to
 * type into an AI chat), which runs archvow; what it adds comes back marked as the AI's. Plain Swing and JSON,
 * tested headless ({@code SkillFormRegression}).
 */
final class ArchvowSkillForm extends JPanel implements Scrollable {
    /** What the person chose: the request's text and the values it was made of. */
    interface Send { void send(String request, Map<String, String> values); }

    final Map<String, String> values = new LinkedHashMap<>();
    final JTextArea request = new JTextArea(3, 30);
    private final JPanel skillReview = new JPanel(new ArchvowWrapLayout(FlowLayout.LEFT, 12, 12));
    final JButton copy;
    final JButton copySkill;
    final JButton cancel;
    private final JsonObject skills;
    private final String skill;
    private final boolean french;
    private final JLabel blocked = new JLabel();
    private final Function<String, String[]> blockedFor;

    /**
     * @param components the components of the project (the first one or {@code values.component} chosen)
     * @param briefs     the brief files and sections, for draft-spec
     * @param tools      the project's AI tools (init answer ai_tools)
     * @param blockedFor component -> why the skill cannot run on it yet ([en, fr]), or null
     */
    ArchvowSkillForm(JsonObject skills, String skill, Map<String, String> initial, List<String> components,
                     List<String> briefs, List<String> tools, Function<String, String[]> blockedFor, boolean french,
                     Send onSend, Runnable onCancel) {
        super(new BorderLayout(0, 10));
        this.skills = skills;
        this.skill = skill;
        this.french = french;
        this.blockedFor = blockedFor == null ? c -> null : blockedFor;
        values.putAll(initial);
        values.putIfAbsent("max", "5");
        List<String> toolList = tools.isEmpty() ? List.of("copilot") : tools;
        if (!toolList.contains(values.getOrDefault("tool", ""))) values.put("tool", toolList.get(0));
        JsonObject def = def(skills, skill);
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        JPanel head = new JPanel(new BorderLayout(0, 3));
        JLabel title = new JLabel(pair(def, "label") + "   skill: archvow-" + skill);
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 4f));
        head.add(title, BorderLayout.NORTH);
        head.add(wrap(pair(def, "does")), BorderLayout.CENTER);
        blocked.setForeground(new Color(176, 118, 26));
        head.add(blocked, BorderLayout.SOUTH);
        add(head, BorderLayout.NORTH);

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        if ("brief".equals(str(def, "target"))) {
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
        int handoffStart = left.getComponentCount();
        ArchvowSegmented tool = new ArchvowSegmented(toolList, values.get("tool"), true);
        tool.onChange(() -> { values.put("tool", tool.value()); update(); });
        field(left, french ? "Assistant IA (optionnel)" : "AI assistant (optional)", tool);
        List<Component> targetParts = new java.util.ArrayList<>();
        List<Component> handoffParts = new java.util.ArrayList<>();
        for (int i = 0; i < left.getComponentCount(); i++) {
            (i < handoffStart ? targetParts : handoffParts).add(left.getComponent(i));
        }
        skillReview.setName("archvow.skill.review");
        skillReview.setOpaque(false);
        skillReview.setAlignmentX(LEFT_ALIGNMENT);
        JPanel reviewPage = new JPanel(new BorderLayout());
        reviewPage.setOpaque(false);
        reviewPage.add(skillReview, BorderLayout.NORTH);
        reviewPage.setAlignmentX(LEFT_ALIGNMENT);
        left.add(reviewPage);
        JPanel navigator = new JPanel(new ArchvowWrapLayout(FlowLayout.LEFT, 5, 2));
        navigator.setName("archvow.skill.steps");
        navigator.setOpaque(false);
        navigator.setAlignmentX(LEFT_ALIGNMENT);
        int[] currentStep = {0};
        java.util.List<JToggleButton> stepButtons = new java.util.ArrayList<>();
        Runnable[] showStep = new Runnable[1];
        showStep[0] = () -> {
            for (Component component : targetParts) component.setVisible(currentStep[0] == 0);
            for (Component component : handoffParts) component.setVisible(currentStep[0] == 1);
            reviewPage.setVisible(currentStep[0] == 2);
            for (int i = 0; i < stepButtons.size(); i++) stepButtons.get(i).setSelected(i == currentStep[0]);
            left.revalidate();
            left.repaint();
        };
        String[] stepNames = french ? new String[] {"Cible", "Transmission IA", "Récapitulatif"}
            : new String[] {"Target", "AI handoff", "Review"};
        for (int i = 0; i < stepNames.length; i++) {
            final int target = i;
            JToggleButton step = new JToggleButton((i + 1) + "  " + stepNames[i]);
            step.addActionListener(e -> { currentStep[0] = target; showStep[0].run(); });
            stepButtons.add(step);
            navigator.add(step);
        }
        left.add(navigator, 0);
        showStep[0].run();

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
        explanation.add(wrap(french ? "1. Copie les consignes complètes et colle-les dans ton assistant IA (ou utilise un skill installé)."
            : "1. Copy the complete instructions and paste them into your AI assistant (or use an installed skill)."));
        explanation.add(wrap("2. " + pair(def, "reads")));
        explanation.add(wrap("3. " + (french ? "L'IA peut : " : "The AI can: ") + pair(def, "may_add")));
        explanation.add(wrap("4. " + (french ? "Toi, tu : " : "You still: ") + pair(def, "you")));
        explanation.add(wrap(french ? "Ce qu'elle ajoute apparaît dans les onglets du composant, marqué IA et À relire."
            : "What it adds appears in the component's tabs, marked AI and To review."));
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
        JPanel body = new JPanel(new GridLayout(1, 2, 12, 0));
        body.setName("archvow.skill.body");
        body.add(leftHost);
        body.add(rightHost);
        left.setAlignmentX(Component.LEFT_ALIGNMENT);
        right.setAlignmentX(Component.LEFT_ALIGNMENT);
        // A narrow editor stacks the controls above the explanation rather than
        // shrinking both columns until labels and controls are cut off.
        body.addComponentListener(new ComponentAdapter() {
            private boolean stacked;
            @Override public void componentResized(ComponentEvent e) {
                boolean narrow = body.getWidth() < 680;
                if (narrow == stacked) return;
                stacked = narrow;
                if (narrow) {
                    // BoxLayout uses each row's natural height. GridLayout(2,1)
                    // would force the short controls to be as tall as the explanation.
                    left.setMaximumSize(new Dimension(Integer.MAX_VALUE, left.getPreferredSize().height));
                    right.setMaximumSize(new Dimension(Integer.MAX_VALUE, right.getPreferredSize().height));
                    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
                } else body.setLayout(new GridLayout(1, 2, 12, 0));
                body.revalidate();
            }
        });
        add(body, BorderLayout.CENTER);

        cancel = new JButton(french ? "Annuler" : "Cancel");
        cancel.addActionListener(e -> onCancel.run());
        copy = ArchvowSplitView.primary(french ? "Copier les consignes" : "Copy instructions");
        copy.addActionListener(e -> onSend.send(request.getText(), Map.copyOf(values)));
        copySkill = new JButton(french ? "Copier la commande skill" : "Copy skill command");
        copySkill.setToolTipText(french ? "Seulement si l'assistant possède le skill Archvow installé"
            : "Only for an assistant with the Archvow skill installed");
        copySkill.addActionListener(e -> onSend.send(ArchvowAiSkills.skillCommand(skills, skill,
            values.getOrDefault("tool", ""), values), Map.copyOf(values)));
        JPanel buttons = new JPanel(new WrapActionLayout());
        buttons.setName("archvow.skill.actions");
        buttons.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { buttons.revalidate(); }
        });
        buttons.add(cancel);
        buttons.add(copySkill);
        buttons.add(copy);
        add(buttons, BorderLayout.SOUTH);
        update();
    }

    /** FlowLayout normally reports one row even when it has wrapped. Reserve the true height. */
    private static final class WrapActionLayout extends FlowLayout {
        WrapActionLayout() { super(FlowLayout.RIGHT, 8, 4); }
        @Override public Dimension preferredLayoutSize(Container target) {
            Dimension single = super.preferredLayoutSize(target);
            int available = target.getWidth() - target.getInsets().left - target.getInsets().right;
            if (available <= 0) return single;
            int x = 0, rows = 1, rowHeight = 0, height = 0;
            for (Component component : target.getComponents()) {
                if (!component.isVisible()) continue;
                Dimension d = component.getPreferredSize();
                int extra = x == 0 ? 0 : getHgap();
                if (x > 0 && x + extra + d.width > available) {
                    height += rowHeight + getVgap();
                    rows++;
                    x = 0;
                    rowHeight = 0;
                    extra = 0;
                }
                x += extra + d.width;
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

    /** The request, the button and the "not yet" line, after each change. */
    private void update() {
        request.setText(ArchvowAiSkills.request(skills, skill, values.getOrDefault("tool", ""), values, french));
        JsonObject def = def(skills, skill);
        boolean brief = "brief".equals(str(def, "target"));
        skillReview.removeAll();
        skillReview.add(reviewItem(brief ? (french ? "Brief" : "Brief") : (french ? "Composant" : "Component"),
            values.getOrDefault(brief ? "brief" : "component", "—")));
        if (def != null && def.has("max") && def.get("max").getAsBoolean())
            skillReview.add(reviewItem(french ? "Au plus" : "At most", values.getOrDefault("max", "5")));
        skillReview.add(reviewItem(french ? "Assistant IA" : "AI assistant", values.getOrDefault("tool", "—")));
        skillReview.revalidate();
        skillReview.repaint();
        boolean ready = brief ? !values.getOrDefault("brief", "").isBlank() : !values.getOrDefault("component", "").isBlank();
        String[] reason = brief ? null : blockedFor.apply(values.getOrDefault("component", ""));
        blocked.setText(reason == null ? "" : reason[french ? 1 : 0]);
        blocked.setVisible(reason != null);
        copy.setEnabled(ready);
        String shorthand = ArchvowAiSkills.skillCommand(skills, skill, values.getOrDefault("tool", ""), values);
        copySkill.setVisible(!shorthand.isBlank());
        copySkill.setEnabled(ready && !shorthand.isBlank());
    }

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

    private static JPanel reviewItem(String title, String value) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ArchvowMotion.blend(UIManager.getColor("Panel.background"),
                UIManager.getColor("Label.foreground"), .18), 1, true),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        JLabel head = new JLabel(title);
        head.setFont(head.getFont().deriveFont(Font.BOLD));
        JLabel body = new JLabel("<html>" + escape(value == null || value.isBlank() ? "—" : value) + "</html>");
        Color muted = UIManager.getColor("Label.disabledForeground");
        if (muted != null) body.setForeground(muted);
        head.setAlignmentX(LEFT_ALIGNMENT);
        body.setAlignmentX(LEFT_ALIGNMENT);
        card.add(head);
        card.add(Box.createVerticalStrut(6));
        card.add(body);
        return card;
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
