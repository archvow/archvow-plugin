package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/** Small, non-modal human confirmation anchored to the action that caused it. */
final class ArchvowConfirmPanel extends JPanel {
    private final JLabel heading = new JLabel();
    private final JTextArea summary = new JTextArea(3, 30);
    private final JTextArea terminalText = new JTextArea(3, 30);
    private final JTextArea terminalNote = new JTextArea(2, 30);
    private final CardLayout previewLayout = new CardLayout();
    private final JPanel previewCards = new JPanel(previewLayout);
    private final JButton approve = new JButton();
    private final JButton cancel = new JButton();
    private Runnable action;

    ArchvowConfirmPanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(173, 147, 88)),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        add(heading, BorderLayout.NORTH);
        summary.setEditable(false);
        summary.setLineWrap(true);
        summary.setWrapStyleWord(true);
        summary.setOpaque(false);
        JScrollPane scroll = new JScrollPane(summary,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setPreferredSize(new Dimension(220, 66));
        previewCards.add(scroll, "plain");
        JPanel commandView = new JPanel(new BorderLayout(0, 5));
        commandView.add(ArchvowTerminal.wrap(terminalText), BorderLayout.NORTH);
        terminalNote.setEditable(false);
        terminalNote.setOpaque(false);
        terminalNote.setLineWrap(true);
        terminalNote.setWrapStyleWord(true);
        commandView.add(terminalNote, BorderLayout.CENTER);
        previewCards.add(commandView, "terminal");
        add(previewCards, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(cancel);
        buttons.add(approve);
        add(buttons, BorderLayout.SOUTH);
        cancel.addActionListener(e -> hideConfirmation());
        approve.addActionListener(e -> {
            Runnable chosen = action;
            hideConfirmation();
            if (chosen != null) chosen.run();
        });
        setVisible(false);
    }

    void showConfirmation(String command, String preview, boolean french, Runnable operation) {
        if (operation == null) throw new IllegalArgumentException("operation");
        action = operation;
        heading.setText(french ? "⚠ Confirmer : " + command : "⚠ Confirm: " + command);
        cancel.setText(french ? "Annuler" : "Cancel");
        approve.setText(french ? "Confirmer et exécuter" : "Confirm and run");
        summary.setText(preview);
        summary.setCaretPosition(0);
        // Only a command is presented as a terminal. Human decision summaries remain plain text.
        String firstLine = preview == null ? "" : preview.split("\\R", 2)[0].trim();
        boolean shell = firstLine.startsWith("archvow ") || firstLine.equals("archvow")
            || firstLine.contains(" -m pip ") || firstLine.startsWith("python -m pip ");
        if (shell) {
            terminalText.setText(firstLine);
            terminalText.setCaretPosition(0);
            String rest = preview.substring(Math.min(preview.length(), preview.indexOf(firstLine) + firstLine.length())).strip();
            terminalNote.setText(rest);
            terminalNote.setVisible(!rest.isBlank());
        }
        previewLayout.show(previewCards, shell ? "terminal" : "plain");
        setVisible(true);
        revalidate();
    }

    void hideConfirmation() {
        action = null;
        setVisible(false);
    }

    static String preview(List<String> args) {
        StringBuilder out = new StringBuilder("archvow");
        for (String arg : args) {
            out.append(' ').append(arg.indexOf(' ') >= 0 || arg.contains("\n")
                ? "\"" + arg.replace("\n", "\\n").replace("\"", "\\\"") + "\"" : arg);
        }
        return out.toString();
    }
}
