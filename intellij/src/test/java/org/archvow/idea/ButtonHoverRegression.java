package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/** UI smoke test: all action buttons have observable hover, no duplicate reload icon. */
public final class ButtonHoverRegression {
    private static void require(boolean value, String msg) {
        if (!value) throw new AssertionError(msg);
    }
    private static void enter(JButton button) {
        MouseEvent e = new MouseEvent(button, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(),
            0, 8, 8, 0, false);
        for (MouseListener l : button.getMouseListeners()) l.mouseEntered(e);
    }
    private static void exit(JButton button) {
        MouseEvent e = new MouseEvent(button, MouseEvent.MOUSE_EXITED, System.currentTimeMillis(),
            0, 100, 100, 0, false);
        for (MouseListener l : button.getMouseListeners()) l.mouseExited(e);
    }
    private static void hoverCheck(boolean dark) throws Exception {
        UIManager.put("Panel.background", dark ? new Color(31, 32, 37) : new Color(248, 249, 252));
        JButton[] controls = new JButton[3];
        Color[] original = new Color[3];
        javax.swing.border.Border[] borders = new javax.swing.border.Border[3];
        SwingUtilities.invokeAndWait(() -> {
            JPanel root = new JPanel();
            controls[0] = new JButton("Check");
            controls[1] = new JButton("0 pending");
            controls[1].setBackground(dark ? new Color(74, 55, 37) : new Color(255, 240, 225));
            controls[2] = new JButton("Add question");
            for (int i=0; i<3; i++) {
                root.add(controls[i]);
                original[i] = controls[i].getBackground();
                borders[i] = controls[i].getBorder();
            }
            ArchvowUiIcons.decorateTree(root);
            ArchvowUiIcons.decorateTree(root);
            for (JButton button : controls) {
                int installed = 0;
                for (MouseListener l : button.getMouseListeners()) {
                    if (l.getClass().getName().contains("ArchvowMotion")) installed++;
                }
                require(installed == 1, "hover must install exactly once for: " + button.getText());
                enter(button);
            }
        });
        Thread.sleep(240);
        SwingUtilities.invokeAndWait(() -> {
            for (int i=0; i<controls.length; i++) {
                require(!original[i].equals(controls[i].getBackground()),
                    "button background must change on hover: " + controls[i].getText());
                require(controls[i].getBorder() != borders[i],
                    "button must have a temporary hover outline: " + controls[i].getText());
                exit(controls[i]);
            }
        });
        Thread.sleep(240);
        SwingUtilities.invokeAndWait(() -> {
            for (int i=0; i<controls.length; i++) {
                require(original[i].equals(controls[i].getBackground()),
                    "idle background must be restored: " + controls[i].getText());
                require(borders[i] == controls[i].getBorder(),
                    "native border must be restored: " + controls[i].getText());
            }
            JButton refresh = new JButton("");
            ArchvowUiIcons.explicit(refresh, "refresh");
            require(refresh.getIcon() != null && refresh.getText().isEmpty(),
                "header reload must use one icon, with no second glyph");
        });
    }
    public static void main(String[] args) throws Exception {
        hoverCheck(false);
        hoverCheck(true);
        System.out.println("OK: hover for action, status and command buttons in both themes");
    }
}
