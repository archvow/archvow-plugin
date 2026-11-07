package org.archvow.idea;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.Arrays;

/** A shared, theme-aware terminal skin for command text. The command itself is unchanged. */
final class ArchvowTerminal {
    private ArchvowTerminal() { }

    static JPanel wrap(JTextArea commandText) {
        Color panel = color("Panel.background", Color.WHITE);
        Color ink = color("Label.foreground", Color.BLACK);
        Color muted = color("Label.disabledForeground", blend(panel, ink, .6));
        Color surface = blend(panel, ink, .045);
        Color header = blend(panel, ink, .11);

        JPanel shell = new JPanel(new BorderLayout(0, 0));
        shell.setName("archvow.terminal.command");
        shell.setAlignmentX(Component.LEFT_ALIGNMENT);
        shell.setBorder(BorderFactory.createLineBorder(blend(panel, ink, .23), 1, true));

        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBackground(header);
        bar.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 5));
        JPanel lights = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        lights.setOpaque(false);
        for (Color shade : new Color[] { new Color(237, 106, 95), new Color(244, 191, 80), new Color(97, 196, 84) }) {
            JComponent dot = new JComponent() {
                @Override public Dimension getPreferredSize() { return new Dimension(9, 9); }
                @Override protected void paintComponent(Graphics graphics) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    try {
                        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g.setColor(shade);
                        g.fillOval(0, 0, 9, 9);
                    } finally { g.dispose(); }
                }
            };
            lights.add(dot);
        }
        JLabel title = new JLabel("Terminal — archvow");
        title.setForeground(muted);
        title.setFont(title.getFont().deriveFont(Math.max(10f, title.getFont().getSize2D() - 1f)));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        left.setOpaque(false);
        left.add(lights);
        left.add(title);
        bar.add(left, BorderLayout.WEST);
        JButton copy = new JButton();
        ArchvowUiIcons.explicit(copy, "copy");
        copy.setToolTipText("Copy command / Copier la commande");
        copy.getAccessibleContext().setAccessibleName("Copy command");
        copy.setMargin(new Insets(1, 4, 1, 4));
        copy.setOpaque(false);
        copy.setContentAreaFilled(false);
        copy.setPreferredSize(new Dimension(25, 23));
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new StringSelection(commandText.getText()), null));
        bar.add(copy, BorderLayout.EAST);
        shell.add(bar, BorderLayout.NORTH);

        commandText.setEditable(false);
        commandText.setLineWrap(true);
        commandText.setWrapStyleWord(true);
        commandText.setFont(terminalFont(commandText));
        commandText.setForeground(ink);
        commandText.setBackground(surface);
        commandText.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 8));
        JLabel prompt = new JLabel("$");
        prompt.setFont(commandText.getFont().deriveFont(Font.BOLD));
        prompt.setForeground(new Color(26, 155, 101));
        prompt.setBorder(BorderFactory.createEmptyBorder(8, 9, 0, 8));
        // BorderLayout.WEST stretches to the scroll pane's full height. A bare
        // JLabel is vertically centered there, placing the prompt *below* the
        // command's first line. Anchor the prompt in a NORTH slot instead.
        JPanel promptColumn = new JPanel(new BorderLayout());
        promptColumn.setOpaque(false);
        promptColumn.add(prompt, BorderLayout.NORTH);
        // Measure the wrapped text at the *actual* available width. A fixed-height
        // JScrollPane used to hide long commands behind a second, inner scrollbar.
        // The surrounding preview owns scrolling; the terminal must show every line.
        JPanel body = new JPanel(new BorderLayout()) {
            @Override public Dimension getPreferredSize() {
                int width = getWidth() > 0 ? getWidth()
                    : getParent() != null && getParent().getWidth() > 0 ? getParent().getWidth() : 240;
                int available = Math.max(48, width - promptColumn.getPreferredSize().width);
                commandText.setSize(available, Short.MAX_VALUE);
                int textHeight = commandText.getPreferredSize().height;
                int promptHeight = prompt.getPreferredSize().height;
                return new Dimension(width, Math.max(textHeight, promptHeight));
            }
        };
        body.setBackground(surface);
        body.add(promptColumn, BorderLayout.WEST);
        body.add(commandText, BorderLayout.CENTER);
        shell.add(body, BorderLayout.CENTER);
        commandText.getDocument().addDocumentListener(new DocumentListener() {
            private void resize() { shell.revalidate(); shell.repaint(); }
            @Override public void insertUpdate(DocumentEvent event) { resize(); }
            @Override public void removeUpdate(DocumentEvent event) { resize(); }
            @Override public void changedUpdate(DocumentEvent event) { resize(); }
        });
        shell.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent event) {
                // Recompute wrapped height when the editor/help column is resized.
                shell.revalidate();
            }
        });
        return shell;
    }

    /** Prefer a locally available JetBrains Mono; never install or bundle fonts. */
    private static Font terminalFont(JTextArea text) {
        int size = text.getFont() == null ? 12 : text.getFont().getSize();
        boolean jetBrainsMono = Arrays.stream(GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getAvailableFontFamilyNames()).anyMatch(name -> name.equalsIgnoreCase("JetBrains Mono"));
        return new Font(jetBrainsMono ? "JetBrains Mono" : Font.MONOSPACED, Font.PLAIN, size);
    }

    /** Same terminal motif for read-only HTML result/reference cards (Swing's limited HTML engine). */
    static String html(String command) {
        Color background = color("Panel.background", Color.WHITE);
        Color foreground = color("Label.foreground", Color.BLACK);
        Color bar = blend(background, foreground, .11);
        Color surface = blend(background, foreground, .045);
        Color line = blend(background, foreground, .25);
        String text = command == null ? "" : command;
        return "<table width='100%' cellpadding='0' cellspacing='0' border='1' style='border-color:" + hex(line) + ";margin:5px 0'>"
            + "<tr><td bgcolor='" + hex(bar) + "' style='padding:4px'>"
            + "<font color='#ed6a5f'>●</font> <font color='#e0a62f'>●</font> <font color='#61c454'>●</font> "
            + "<font color='" + hex(foreground) + "'><small>Terminal — archvow</small></font>"
            + "</td></tr><tr><td bgcolor='" + hex(surface) + "' style='padding:8px'>"
            + "<font color='#1a9b65' face='monospaced'><b>$ </b></font>"
            + "<font color='" + hex(foreground) + "' face='monospaced'>"
            + esc(text).replace("\n", "<br>").replace("  ", "&nbsp;&nbsp;")
            + "</font></td></tr></table>";
    }

    private static Color color(String name, Color fallback) {
        Color value = UIManager.getColor(name);
        return value == null ? fallback : value;
    }
    private static Color blend(Color background, Color foreground, double fraction) {
        double a = 1 - fraction;
        return new Color((int) (background.getRed() * a + foreground.getRed() * fraction),
            (int) (background.getGreen() * a + foreground.getGreen() * fraction),
            (int) (background.getBlue() * a + foreground.getBlue() * fraction));
    }
    private static String hex(Color color) { return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()); }
    private static String esc(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
