package org.archvow.idea;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;
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
final class ArchvowSkillForm extends JPanel {
    /** What the person chose: the request's text and the values it was made of. */
    interface Send { void send(String request, Map<String, String> values); }

    final Map<String, String> values = new LinkedHashMap<>();
    final JTextArea request = new JTextArea(3, 30);
    final JButton copy;
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
        ArchvowSegmented tool = new ArchvowSegmented(toolList, values.get("tool"), true);
        tool.onChange(() -> { values.put("tool", tool.value()); update(); });
        field(left, french ? "Envoyer à" : "Send to", tool);

        JPanel right = new JPanel(new BorderLayout(0, 6));
        right.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(128, 128, 128, 90)),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        JLabel what = new JLabel(french ? "CE QUI VA SE PASSER" : "WHAT WILL HAPPEN");
        what.setFont(what.getFont().deriveFont(Font.BOLD, 11f));
        right.add(what, BorderLayout.NORTH);
        JPanel steps = new JPanel();
        steps.setLayout(new BoxLayout(steps, BoxLayout.Y_AXIS));
        steps.add(wrap((french ? "1. La demande est copiée : colle-la dans ton assistant IA (" : "1. The request is copied: paste it in your AI assistant (")
            + values.get("tool") + ")."));
        steps.add(wrap("2. " + pair(def, "reads")));
        steps.add(wrap("3. " + (french ? "L'IA peut : " : "The AI can: ") + pair(def, "may_add")));
        steps.add(wrap("4. " + (french ? "Toi, tu : " : "You still: ") + pair(def, "you")));
        steps.add(wrap(french ? "Ce qu'elle ajoute apparaît dans les onglets du composant, marqué IA et À relire."
            : "What it adds appears in the component's tabs, marked AI and To review."));
        JPanel bottom = new JPanel(new BorderLayout(0, 3));
        bottom.add(new JLabel(french ? "Envoyé à l'IA" : "Sent to the AI"), BorderLayout.NORTH);
        request.setEditable(false);
        request.setLineWrap(true);
        request.setWrapStyleWord(true);
        request.setFont(new Font(Font.MONOSPACED, Font.PLAIN, request.getFont().getSize()));
        bottom.add(request, BorderLayout.CENTER);
        right.add(steps, BorderLayout.CENTER);
        right.add(bottom, BorderLayout.SOUTH);

        JPanel body = new JPanel(new GridLayout(1, 2, 16, 0));
        body.add(left);
        body.add(right);
        add(body, BorderLayout.CENTER);

        cancel = new JButton(french ? "Annuler" : "Cancel");
        cancel.addActionListener(e -> onCancel.run());
        copy = ArchvowSplitView.primary(french ? "Copier la demande" : "Copy request");
        copy.addActionListener(e -> onSend.send(request.getText(), Map.copyOf(values)));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancel);
        buttons.add(copy);
        add(buttons, BorderLayout.SOUTH);
        update();
    }

    /** The request, the button and the "not yet" line, after each change. */
    private void update() {
        request.setText(ArchvowAiSkills.request(skills, skill, values.getOrDefault("tool", ""), values, french));
        JsonObject def = def(skills, skill);
        boolean brief = "brief".equals(str(def, "target"));
        boolean ready = brief ? !values.getOrDefault("brief", "").isBlank() : !values.getOrDefault("component", "").isBlank();
        String[] reason = brief ? null : blockedFor.apply(values.getOrDefault("component", ""));
        blocked.setText(reason == null ? "" : reason[french ? 1 : 0]);
        blocked.setVisible(reason != null);
        copy.setEnabled(ready);
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
