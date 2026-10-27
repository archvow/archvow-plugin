package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Test Archvow and long messages (0.11.3): the text in a box sized to it (never a line as wide as the
 * screen), selectable, Copy command / Copy / Close; the command line quoted as a terminal takes it.
 */
public final class TextDialogRegression {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static JButton find(Container c, String name) {
        for (Component child : c.getComponents()) {
            if (child instanceof JButton b && name.equals(b.getName())) return b;
            if (child instanceof Container inner) { JButton b = find(inner, name); if (b != null) return b; }
        }
        return null;
    }

    public static void main(String[] args) {
        List<String> argv = List.of("/Library/Frameworks/Python.framework/Versions/3.12/bin/python3", "-m", "archvow",
            "--root", "/Users/marc/PycharmProjects/acoss pipeline2", "--version");
        String command = ArchvowTextDialog.commandLine(argv);
        check(command.equals("/Library/Frameworks/Python.framework/Versions/3.12/bin/python3 -m archvow --root "
            + "\"/Users/marc/PycharmProjects/acoss pipeline2\" --version"), command);
        check(ArchvowTextDialog.commandLine(List.of("wsl.exe", "--exec", "C:\\x y")).equals("wsl.exe --exec \"C:\\\\x y\""),
            ArchvowTextDialog.commandLine(List.of("wsl.exe", "--exec", "C:\\x y")));
        String text = "Found in / Trouvé dans : python (/Library/Frameworks/Python.framework/Versions/3.12/bin/python3)\n"
            + "Command / Commande: " + command + "\nExit code / Code: 0\n\narchvow 1.16.0";
        boolean[] closed = {false};
        JPanel content = ArchvowTextDialog.content(text, command, false, () -> closed[0] = true);
        JScrollPane box = (JScrollPane) content.getComponent(0);
        Dimension size = box.getPreferredSize();
        check(size.width == ArchvowTextDialog.WIDTH && size.height <= ArchvowTextDialog.HEIGHT && size.height >= 60,
            "a box of the text's size, not the screen's: " + size);
        JTextArea area = (JTextArea) box.getViewport().getView();
        check(!area.isEditable() && area.getLineWrap() && area.getText().contains("archvow 1.16.0"), "selectable, wrapped text");
        JButton copyCommand = find(content, "archvow.copyCommand");
        check(copyCommand != null && command.equals(copyCommand.getToolTipText()), "Copy command");
        check(find(content, "archvow.copy") != null, "Copy");
        find(content, "archvow.close").doClick();
        check(closed[0], "Close closes");
        check(find(ArchvowTextDialog.content("help", null, true, () -> {}), "archvow.copyCommand") == null, "no command: no Copy command");
        String tall = "line\n".repeat(200);
        check(ArchvowTextDialog.textBox(tall).getPreferredSize().height == ArchvowTextDialog.HEIGHT, "a long text scrolls");
        System.out.println("OK: the text dialog's size, its copies and the quoted command");
    }
}
