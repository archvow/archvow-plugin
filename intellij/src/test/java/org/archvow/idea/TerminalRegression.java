package org.archvow.idea;

import javax.swing.*;
import java.awt.*;

/** Command snippets get a terminal skin without changing the command text or human confirmation flow. */
public final class TerminalRegression {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextArea command = new JTextArea("archvow init project --answer mode=strict");
            JPanel shell = ArchvowTerminal.wrap(command);
            check("archvow.terminal.command".equals(shell.getName()), "terminal component missing");
            check(command.getText().equals("archvow init project --answer mode=strict"), "command mutated");
            check(command.getLineWrap(), "long commands should wrap");
            JPanel bar = (JPanel) ((BorderLayout) shell.getLayout()).getLayoutComponent(BorderLayout.NORTH);
            JButton copy = (JButton) ((BorderLayout) bar.getLayout()).getLayoutComponent(BorderLayout.EAST);
            check(copy.isEnabled() && copy.getActionListeners().length > 0, "copy command must be clickable");
            // A terminal displays the entire command; only its containing preview scrolls.
            JPanel body = (JPanel) ((BorderLayout) shell.getLayout()).getLayoutComponent(BorderLayout.CENTER);
            check(((BorderLayout) body.getLayout()).getLayoutComponent(BorderLayout.CENTER) == command,
                "command must not be trapped in an inner scroll pane");
            command.setText("archvow check");
            shell.setSize(300, 150);
            shell.doLayout();
            body.doLayout();
            int shortHeight = shell.getPreferredSize().height;
            command.setText("archvow init myproject --template pyspark-pipeline --no-input"
                + " --answer language=fr --answer platform=gitlab --answer ci=auto"
                + " --answer ai_tools=copilot --answer mode=strict --answer sandbox=no"
                + " --answer rules=ARCH,STYLE,SPARK,GDPR,TST --answer scheduler=none");
            check(shell.getPreferredSize().height > shortHeight,
                "terminal height must grow when the command gains arguments");
            int narrowHeight = 0;
            int wideHeight = 0;
            for (int width : new int[] { 300, 500, 900 }) {
                shell.setSize(width, 150);
                shell.doLayout();
                check(shell.getWidth() == width, "terminal should follow container width");
                body.doLayout();
                if (width == 300) narrowHeight = shell.getPreferredSize().height;
                if (width == 900) wideHeight = shell.getPreferredSize().height;
                JPanel promptColumn = (JPanel) ((BorderLayout) body.getLayout()).getLayoutComponent(BorderLayout.WEST);
                promptColumn.doLayout();
                JLabel prompt = (JLabel) ((BorderLayout) promptColumn.getLayout()).getLayoutComponent(BorderLayout.NORTH);
                check("$".equals(prompt.getText()) && prompt.getY() == 0, "terminal prompt must be anchored on first line");
            }
            check(narrowHeight > wideHeight,
                "a long command must make a narrow terminal taller (" + narrowHeight + " <= " + wideHeight + ")");
            String markup = ArchvowTerminal.html("archvow check --json & <unsafe>");
            check(markup.contains("Terminal — archvow"), "HTML terminal title");
            check(markup.contains("&amp; &lt;unsafe&gt;"), "command must be escaped");
            ArchvowConfirmPanel confirm = new ArchvowConfirmPanel();
            confirm.showConfirmation("check", "archvow check --complete --json", false, () -> {});
            check(confirm.isVisible(), "confirmation hidden");
            confirm.showConfirmation("decision make", "A decision that requires human approval", false, () -> {});
            check(confirm.isVisible(), "human confirmation lost");
        });
        System.out.println("TerminalRegression: OK");
    }
}
