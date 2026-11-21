package org.archvow.idea;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The AI assistants of a template question that names agents (ai_tools, reviewers: the catalog's {@code suggestions},
 * 0.26.1): the chosen ones as chips, a list of the assistants archvow knows, and a box for any other name - archvow
 * takes any agent name (an unknown one gets the standard AGENTS.md and .agents/skills), so the list is never closed.
 * The rules ({@link #with}, {@link #validName}) are plain Java ({@code AgentPickerRegression}); the VS Code form has
 * the same picker (media/workspace.js, agentPicker).
 */
final class ArchvowAgentPicker extends JPanel {
    /** An agent's name, as archvow takes it (agents.NAME_RE): lowercase letters and digits, words joined by - or _. */
    static final Pattern NAME = Pattern.compile("^[a-z][a-z0-9]*([_-][a-z0-9]+)*$");

    private final List<String> known;
    private final boolean fr;
    private final List<String> picked = new ArrayList<>();
    private final JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
    private final JComboBox<String> add = new JComboBox<>();
    private final JTextField other = new JTextField(14);
    private final JLabel problem = new JLabel();
    private Runnable onChange = () -> { };
    private boolean filling;

    /** Whether a template question names agents: several answers, and the agents archvow knows as suggestions. */
    static boolean names(JsonObject question) {
        return question != null && question.has("multiple") && question.get("multiple").getAsBoolean()
            && question.has("suggestions") && question.get("suggestions").isJsonArray() && !question.getAsJsonArray("suggestions").isEmpty();
    }

    static List<String> suggestions(JsonObject question) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : question.getAsJsonArray("suggestions")) if (e.isJsonPrimitive()) out.add(e.getAsString());
        return out;
    }

    /** The list after adding one: {@code none} alone, or the assistants without {@code none}; each once. */
    static List<String> with(List<String> current, String name) {
        if ("none".equals(name)) return List.of("none");
        List<String> out = new ArrayList<>();
        for (String c : current) if (!c.equals("none") && !c.equals(name)) out.add(c);
        out.add(name);
        return out;
    }

    /** A name typed by a person, as archvow takes it: lowercased; null when archvow would refuse it. */
    static String validName(String typed) {
        String name = typed == null ? "" : typed.trim().toLowerCase(java.util.Locale.ROOT);
        return NAME.matcher(name).matches() ? name : null;
    }

    static String label(String name, boolean fr) {
        return "none".equals(name) ? (fr ? "aucun (pas d'assistant IA)" : "none (no AI assistant)") : name;
    }

    static String note(String name, boolean known, boolean fr) {
        if (known) return fr ? "Connu d'archvow : il écrit les fichiers propres à cet assistant." : "Known to archvow: it writes this assistant's own files.";
        return fr ? "Inconnu d'archvow : " + name + " reçoit les fichiers standard (AGENTS.md, .agents/skills) ; archvow le reconnaît quand il tourne avec AI_AGENT=" + name + "."
            : "Not known to archvow: " + name + " gets the standard files (AGENTS.md, .agents/skills); archvow recognises it when it runs with AI_AGENT=" + name + ".";
    }

    static String help(boolean fr) {
        return fr ? "La liste contient les assistants qu'archvow connaît : il écrit leurs fichiers propres. Tout autre nom marche aussi : "
                + "il reçoit les fichiers standard (AGENTS.md, .agents/skills), et archvow le reconnaît quand il tourne avec AI_AGENT=<nom>."
            : "The list holds the assistants archvow knows: it writes their own files. Any other name works too: it gets the standard "
                + "files (AGENTS.md, .agents/skills), and archvow recognises it when it runs with AI_AGENT=<name>.";
    }

    static String badName(boolean fr) {
        return fr ? "Un nom : lettres minuscules et chiffres, mots reliés par - ou _ (mon-agent)."
            : "A name is lowercase letters and digits, words joined by - or _ (my-agent).";
    }

    ArchvowAgentPicker(List<String> known, String value, boolean fr) {
        super(new BorderLayout(0, 3));
        this.known = List.copyOf(known);
        this.fr = fr;
        setOpaque(false);
        for (String v : value == null ? new String[0] : value.split(",")) if (!v.isBlank() && !picked.contains(v.trim())) picked.add(v.trim());
        chips.setOpaque(false);
        add.getAccessibleContext().setAccessibleName(fr ? "Ajouter un assistant connu d'archvow" : "Add an assistant archvow knows");
        add.addActionListener(e -> {
            if (filling || add.getSelectedIndex() <= 0) return;
            choose(known.get(knownIndex(add.getSelectedIndex())));
        });
        JButton addOther = new JButton(fr ? "Ajouter" : "Add");
        addOther.addActionListener(e -> addTyped());
        other.addActionListener(e -> addTyped());
        other.putClientProperty("JTextField.placeholderText", fr ? "ou un autre, ex. mon-agent" : "or another one, e.g. my-agent");
        other.setToolTipText(fr ? "Un assistant qu'archvow ne connaît pas : son nom" : "An assistant archvow does not know: its name");
        problem.setForeground(UIManager.getColor("Component.errorFocusColor") != null ? UIManager.getColor("Component.errorFocusColor") : new Color(0xE0, 0x5A, 0x5A));
        problem.setVisible(false);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setOpaque(false);
        row.add(add);
        row.add(other);
        row.add(addOther);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(row, BorderLayout.NORTH);
        south.add(problem, BorderLayout.CENTER);
        ArchvowWrapLabel hint = new ArchvowWrapLabel();
        hint.setText(help(fr));
        hint.setOpaque(false);
        hint.setFont(hint.getFont().deriveFont(11f));
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        south.add(hint, BorderLayout.SOUTH);
        add(chips, BorderLayout.NORTH);
        add(south, BorderLayout.CENTER);
        refresh();
    }

    /** The answer: the chosen assistants, comma-separated. */
    String value() { return String.join(",", picked); }

    void onChange(Runnable run) { onChange = run == null ? () -> { } : run; }

    /** The list's entries, but the first ("Add an assistant…"), are the known ones not chosen yet. */
    private int knownIndex(int entry) {
        int seen = 0;
        for (int i = 0; i < known.size(); i++) {
            if (picked.contains(known.get(i))) continue;
            if (++seen == entry) return i;
        }
        return 0;
    }

    private void choose(String name) {
        List<String> next = with(picked, name);
        picked.clear();
        picked.addAll(next);
        refresh();
        onChange.run();
    }

    private void addTyped() {
        String name = validName(other.getText());
        if (name == null) {
            problem.setText(badName(fr));
            problem.setVisible(true);
            revalidate();
            return;
        }
        problem.setVisible(false);
        other.setText("");
        choose(name);
    }

    private void refresh() {
        filling = true;
        try {
            chips.removeAll();
            if (picked.isEmpty()) {
                JLabel nothing = new JLabel(fr ? "Aucun choisi pour l'instant" : "None chosen yet");
                nothing.setForeground(UIManager.getColor("Label.disabledForeground"));
                chips.add(nothing);
            }
            for (String name : picked) {
                boolean own = known.contains(name);
                JPanel chip = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
                chip.setOpaque(false);
                Color edge = own ? UIManager.getColor("Component.focusColor") : new Color(0xB0, 0x7E, 0x24);
                chip.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(edge != null ? edge : Color.GRAY, 1, true), BorderFactory.createEmptyBorder(0, 4, 0, 0)));
                JLabel text = new JLabel(label(name, fr));
                chip.setToolTipText(note(name, own, fr));
                text.setToolTipText(note(name, own, fr));
                JButton remove = new JButton("×");
                remove.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
                remove.setContentAreaFilled(false);
                remove.setToolTipText((fr ? "Retirer " : "Remove ") + name);
                remove.getAccessibleContext().setAccessibleName((fr ? "Retirer " : "Remove ") + name);
                remove.addActionListener(e -> { picked.remove(name); refresh(); onChange.run(); });
                chip.add(text);
                chip.add(remove);
                chips.add(chip);
            }
            add.removeAllItems();
            add.addItem(fr ? "Ajouter un assistant…" : "Add an assistant…");
            for (String name : known) if (!picked.contains(name)) add.addItem(label(name, fr));
            add.setSelectedIndex(0);
        } finally {
            filling = false;
        }
        revalidate();
        repaint();
    }
}
