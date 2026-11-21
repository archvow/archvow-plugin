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
    private final JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
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

    /**
     * One chosen assistant (0.26.1): a rounded chip as wide as its name, and a small × that removes it. Not a JButton:
     * IntelliJ's look gives every button a wide minimum size, which left a gap between the name and its ×.
     */
    static final class Chip extends JPanel {
        final Remove remove;
        private final boolean known;

        Chip(String text, boolean known, String removeName, Runnable onRemove) {
            this.known = known;
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 4));
            JLabel name = new JLabel(text);
            remove = new Remove(removeName, onRemove);
            add(name);
            add(Box.createHorizontalStrut(4));
            add(remove);
        }

        @Override public void setToolTipText(String text) {
            super.setToolTipText(text);
            for (Component c : getComponents()) if (c instanceof JLabel l) l.setToolTipText(text);
        }

        @Override public Dimension getMaximumSize() { return getPreferredSize(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color text = UIManager.getColor("Label.foreground") != null ? UIManager.getColor("Label.foreground") : Color.DARK_GRAY;
                Color edge = known ? (UIManager.getColor("Component.focusColor") != null ? UIManager.getColor("Component.focusColor") : new Color(0x3D, 0x7E, 0xD8))
                    : new Color(0xC8, 0x8A, 0x1E);
                int arc = getHeight();
                g2.setColor(new Color(text.getRed(), text.getGreen(), text.getBlue(), 22));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                g2.setColor(edge);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            } finally {
                g2.dispose();
            }
        }
    }

    /** The small × of a chip: drawn, 14 px, reachable with the keyboard (Tab, then Enter, Space or Delete). */
    static final class Remove extends JComponent {
        private final Runnable onRemove;
        private boolean over;

        Remove(String name, Runnable onRemove) {
            this.onRemove = onRemove;
            setFocusable(true);
            setToolTipText(name);
            getAccessibleContext().setAccessibleName(name);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            Dimension size = new Dimension(14, 14);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) { doClick(); }
                @Override public void mouseEntered(java.awt.event.MouseEvent e) { over = true; repaint(); }
                @Override public void mouseExited(java.awt.event.MouseEvent e) { over = false; repaint(); }
            });
            addKeyListener(new java.awt.event.KeyAdapter() {
                @Override public void keyPressed(java.awt.event.KeyEvent e) {
                    int k = e.getKeyCode();
                    if (k == java.awt.event.KeyEvent.VK_ENTER || k == java.awt.event.KeyEvent.VK_SPACE
                        || k == java.awt.event.KeyEvent.VK_DELETE || k == java.awt.event.KeyEvent.VK_BACK_SPACE) doClick();
                }
            });
            addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusGained(java.awt.event.FocusEvent e) { repaint(); }
                @Override public void focusLost(java.awt.event.FocusEvent e) { repaint(); }
            });
        }

        void doClick() { onRemove.run(); }

        /** Screen readers: a button named "Remove <name>". */
        @Override public javax.accessibility.AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
                @Override public javax.accessibility.AccessibleRole getAccessibleRole() { return javax.accessibility.AccessibleRole.PUSH_BUTTON; }
            };
            return accessibleContext;
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color text = UIManager.getColor("Label.foreground") != null ? UIManager.getColor("Label.foreground") : Color.DARK_GRAY;
                if (over || isFocusOwner()) {
                    g2.setColor(new Color(text.getRed(), text.getGreen(), text.getBlue(), 40));
                    g2.fillOval(0, 0, getWidth(), getHeight());
                }
                g2.setColor(over || isFocusOwner() ? text : new Color(text.getRed(), text.getGreen(), text.getBlue(), 170));
                g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int m = 4, w = getWidth(), h = getHeight();
                g2.drawLine(m, m, w - m - 1, h - m - 1);
                g2.drawLine(w - m - 1, m, m, h - m - 1);
            } finally {
                g2.dispose();
            }
        }
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
                Chip chip = new Chip(label(name, fr), known.contains(name), (fr ? "Retirer " : "Remove ") + name,
                    () -> { picked.remove(name); refresh(); onChange.run(); });
                chip.setToolTipText(note(name, known.contains(name), fr));
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
