package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.List;

/**
 * A message with text a person may want to read whole or copy (Test Archvow, what was tried, a
 * command's help): the text selectable, wrapped, in a box at most {@link #WIDTH} x {@link #HEIGHT}
 * that scrolls, and Copy command / Copy / Close (copying keeps the dialog open). A JOptionPane drew
 * such a text on one line as wide as the screen. Plain Swing: its content is tested headless
 * ({@code UiPolishRegression}).
 */
final class ArchvowTextDialog {
    static final int WIDTH = 640;
    static final int HEIGHT = 320;

    private ArchvowTextDialog() { }

    /** A command line as a person can paste it in a terminal: arguments with spaces or quotes quoted. */
    static String commandLine(List<String> argv) {
        StringBuilder out = new StringBuilder();
        for (String a : argv) {
            if (out.length() > 0) out.append(' ');
            out.append(a.matches("[A-Za-z0-9_@%+=:,./\\\\-]+") ? a : "\"" + a.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
        }
        return out.toString();
    }

    /** The text area of the dialog (selectable, wrapped), in its scroll pane. */
    static JScrollPane textBox(String text) {
        JTextArea area = new JTextArea(text == null ? "" : text.strip());
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setCaretPosition(0);
        Font font = UIManager.getFont("Label.font");
        if (font != null) area.setFont(font);
        area.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        area.setBackground(UIManager.getColor("Panel.background"));
        JScrollPane scroll = new JScrollPane(area, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        // as tall as the text at this width, up to HEIGHT; then it scrolls
        area.setSize(WIDTH - 30, Short.MAX_VALUE);
        int height = Math.min(HEIGHT, Math.max(60, area.getPreferredSize().height + 4));
        scroll.setPreferredSize(new Dimension(WIDTH, height));
        return scroll;
    }

    /** The whole content: the text, then [Copy command] [Copy] ... [Close]. */
    static JPanel content(String text, String command, boolean french, Runnable close) {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 10, 12));
        panel.add(textBox(text), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new BorderLayout());
        JPanel copies = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        if (command != null && !command.isBlank()) {
            JButton copyCommand = new JButton(french ? "Copier la commande" : "Copy command");
            copyCommand.setName("archvow.copyCommand");
            copyCommand.setToolTipText(command);
            copyCommand.addActionListener(e -> copy(command, copyCommand, french));
            copies.add(copyCommand);
        }
        JButton copyAll = new JButton(french ? "Copier" : "Copy");
        copyAll.setName("archvow.copy");
        copyAll.addActionListener(e -> copy(text, copyAll, french));
        copies.add(copyAll);
        JButton closeButton = new JButton(french ? "Fermer" : "Close");
        closeButton.setName("archvow.close");
        closeButton.addActionListener(e -> close.run());
        buttons.add(copies, BorderLayout.WEST);
        buttons.add(closeButton, BorderLayout.EAST);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private static void copy(String text, JButton button, boolean french) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text == null ? "" : text), null);
            button.setText(french ? "Copié ✓" : "Copied ✓");
        } catch (HeadlessException | IllegalStateException e) {
            button.setText(french ? "Copie impossible" : "Cannot copy");
        }
    }

    /** Shows the dialog, modal, over {@code parent}. */
    static void show(Component parent, String title, String text, String command, boolean french) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setContentPane(content(text, command, french, dialog::dispose));
        dialog.pack();
        dialog.setResizable(true);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }
}
