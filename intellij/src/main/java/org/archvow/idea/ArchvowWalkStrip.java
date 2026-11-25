package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * The strip of a walk (1.1.0), at the top of a component's page: where Specify or Build is - "✓ config specified ·
 * Next: logging_setup (1 decision) [Specify next ▸]", "Building orders ▬▬ 1 of 2" with its three steps - in
 * place of the modal "All decision and safety questions are answered" of 1.0. The progress over the words, the
 * buttons under them. 1.2.0: no Stop button - a small × in the corner, and Esc, leave the walk. Plain Swing,
 * tested headless (WalksRegression).
 */
final class ArchvowWalkStrip extends JPanel {
    /** done (green), info (blue), warn (orange), bad (red). */
    enum Tone { DONE, INFO, WARN, BAD }

    /** A button of the strip; {@code primary}: the filled one (the next step). */
    record Action(String label, boolean primary, boolean enabled, Runnable run) {
        Action(String label, boolean primary, Runnable run) { this(label, primary, true, run); }
    }

    private final JLabel progress = new JLabel();
    private final JLabel count = new JLabel();
    private final Steps steps = new Steps();
    private final JButton leave = new JButton("×");
    private Runnable onLeave = () -> { };
    private final JLabel title = new JLabel();
    private final ArchvowWrapLabel detail = new ArchvowWrapLabel();
    private final JPanel buttons = new JPanel(new ArchvowWrapLayout(FlowLayout.LEFT, 6, 2));
    private Tone tone = Tone.DONE;

    ArchvowWalkStrip() {
        super(new BorderLayout(10, 4));
        setName("archvow.walk");
        setOpaque(false);
        progress.setFont(progress.getFont().deriveFont(11.5f));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13.5f));
        detail.setOpaque(false);
        buttons.setOpaque(false);
        JPanel words = new JPanel(new BorderLayout(0, 2));
        words.setOpaque(false);
        words.add(title, BorderLayout.NORTH);
        words.add(detail, BorderLayout.CENTER);
        count.setFont(progress.getFont());
        JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        line.setOpaque(false);
        line.add(progress);
        line.add(steps);
        line.add(count);
        leave.setName("archvow.walk.leave");
        leave.setFont(leave.getFont().deriveFont(15f));
        leave.setBorderPainted(false);
        leave.setContentAreaFilled(false);
        leave.setFocusPainted(false);
        leave.setMargin(new Insets(0, 4, 0, 4));
        leave.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        leave.addActionListener(e -> onLeave.run());
        leave.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { leave.setContentAreaFilled(true); }
            @Override public void mouseExited(java.awt.event.MouseEvent e) { leave.setContentAreaFilled(false); }
        });
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(line, BorderLayout.CENTER);
        top.add(leave, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);
        add(words, BorderLayout.CENTER);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "archvow.walk.leave");
        getActionMap().put("archvow.walk.leave", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { if (isShowing()) onLeave.run(); }
        });
        add(buttons, BorderLayout.SOUTH);
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        setVisible(false);
    }

    /** What × and Esc do (leave the walk), and the ×'s hover text. */
    void onLeave(String tip, Runnable action) {
        leave.setToolTipText(tip);
        onLeave = action;
    }

    /** Shows the strip: its tone, a progress line ("" for none), a title, a detail and its buttons. */
    void show(Tone tone, String progressText, String titleText, String detailText, List<Action> actions) {
        show(tone, progressText, 0, 0, titleText, detailText, actions);
    }

    /**
     * The same, with the walk's steps drawn between the progress's words and its count ("Building orders ▬▬ 2 of
     * 2"): {@code done} of {@code total} (the current one is the next); 0 total: the progress line as it is.
     */
    void show(Tone tone, String progressText, int done, int total, String titleText, String detailText, List<Action> actions) {
        this.tone = tone;
        String text = progressText == null ? "" : progressText;
        int cut = text.lastIndexOf(" · ");
        boolean drawn = total > 0 && cut > 0;
        progress.setText(drawn ? text.substring(0, cut) : text);
        count.setText(drawn ? text.substring(cut + 3) : "");
        count.setVisible(drawn);
        steps.set(drawn ? done : 0, drawn ? total : 0);
        steps.setVisible(drawn);
        progress.setVisible(!text.isBlank());
        title.setText(titleText);
        detail.setText(detailText == null ? "" : detailText);
        detail.setVisible(detailText != null && !detailText.isBlank());
        buttons.removeAll();
        for (Action a : actions) {
            JButton b = a.primary() ? ArchvowSplitView.primary(a.label()) : new JButton(a.label());
            b.setEnabled(a.enabled());
            b.addActionListener(e -> a.run().run());
            buttons.add(b);
        }
        Color[] c = colors(tone);
        title.setForeground(c[2]);
        progress.setForeground(c[3]);
        count.setForeground(c[3]);
        leave.setForeground(c[3]);
        detail.setForeground(c[3]);
        setVisible(true);
        revalidate();
        repaint();
    }

    void close() { setVisible(false); }

    /** For the tests: the title, the detail, the progress and the buttons' labels. */
    String titleText() { return title.getText(); }
    String detailText() { return detail.getText(); }
    String progressText() { return count.isVisible() ? progress.getText() + " · " + count.getText() : progress.getText(); }
    int stepsDone() { return steps.done; }
    int stepsTotal() { return steps.total; }
    JButton leaveButton() { return leave; }
    List<String> buttonLabels() {
        return java.util.Arrays.stream(buttons.getComponents()).filter(JButton.class::isInstance)
            .map(b -> ((JButton) b).getText()).toList();
    }
    JButton button(String label) {
        for (Component b : buttons.getComponents()) if (b instanceof JButton button && label.equals(button.getText())) return button;
        return null;
    }

    /** background, border, title, text - for the dark and the light themes. */
    private static Color[] colors(Tone tone) {
        boolean dark = ArchvowMotion.dark();
        return switch (tone) {
            case DONE -> dark ? new Color[] {new Color(0x26, 0x3A, 0x2A), new Color(0x3F, 0x6B, 0x46), new Color(0xA6, 0xE0, 0xA6), new Color(0xC9, 0xCC, 0xD2)}
                : new Color[] {new Color(0xE8, 0xF5, 0xE9), new Color(0x8B, 0xC3, 0x8F), new Color(0x1E, 0x6B, 0x2A), new Color(0x33, 0x3A, 0x33)};
            case INFO -> dark ? new Color[] {new Color(0x22, 0x30, 0x4A), new Color(0x2D, 0x44, 0x70), new Color(0xC8, 0xD8, 0xFF), new Color(0xC9, 0xCC, 0xD2)}
                : new Color[] {new Color(0xE8, 0xEF, 0xFC), new Color(0x8F, 0xAE, 0xE8), new Color(0x1D, 0x4E, 0xB0), new Color(0x33, 0x36, 0x3D)};
            case WARN -> dark ? new Color[] {new Color(0x3B, 0x31, 0x22), new Color(0x5C, 0x4A, 0x2A), new Color(0xF0, 0xC7, 0x8A), new Color(0xD6, 0xB8, 0x8A)}
                : new Color[] {new Color(0xFD, 0xF3, 0xE3), new Color(0xE0, 0xB0, 0x60), new Color(0x8A, 0x55, 0x00), new Color(0x5A, 0x45, 0x20)};
            case BAD -> dark ? new Color[] {new Color(0x3A, 0x26, 0x28), new Color(0x6B, 0x3A, 0x3F), new Color(0xF0, 0x95, 0x9C), new Color(0xD9, 0xC3, 0xC5)}
                : new Color[] {new Color(0xFC, 0xEB, 0xEC), new Color(0xE0, 0x90, 0x96), new Color(0xA0, 0x20, 0x2A), new Color(0x5A, 0x30, 0x33)};
        };
    }

    /** The walk's steps: one short bar each - green done, blue the current one, grey the rest. */
    private static final class Steps extends JComponent {
        int done;
        int total;

        void set(int done, int total) {
            this.done = done;
            this.total = total;
            revalidate();
            repaint();
        }

        @Override public Dimension getPreferredSize() { return new Dimension(Math.max(0, total * 25 - 3), 12); }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean dark = ArchvowMotion.dark();
                for (int i = 0; i < total; i++) {
                    g.setColor(i < done ? new Color(0x5F, 0xB8, 0x65) : i == done ? new Color(0x35, 0x74, 0xF0)
                        : dark ? new Color(0x4E, 0x51, 0x57) : new Color(0xC9, 0xCC, 0xD2));
                    g.fillRoundRect(i * 25, getHeight() / 2 - 3, 22, 5, 5, 5);
                }
            } finally {
                g.dispose();
            }
        }
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color[] c = colors(tone);
            g.setColor(c[0]);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            g.setColor(c[1]);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
        } finally {
            g.dispose();
        }
        super.paintComponent(graphics);
    }
}
