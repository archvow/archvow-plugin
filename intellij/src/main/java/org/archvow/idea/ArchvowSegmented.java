package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A few fixed values (severity, verify, criticality...): one button each, the chosen one pressed (design A).
 * Pressing the chosen one again empties an optional field. The form reads it with value().
 */
final class ArchvowSegmented extends JPanel {
    private final List<JToggleButton> buttons = new ArrayList<>();
    private final boolean required;
    private String value = "";
    private Runnable onChange = () -> {};

    ArchvowSegmented(List<String> choices, String initial, boolean required) {
        super(new Wrap());  // 0.25.1: buttons that do not fit go to the next line (a FlowLayout hid them)
        this.required = required;
        setOpaque(false);
        for (String choice : choices) {
            JToggleButton button = new JToggleButton(choice);
            button.setFocusable(true);
            // the button's text when pressed (a label may follow the language): pressing the chosen one empties an optional field
            button.addActionListener(e -> choose(button.getText().equals(value) && !this.required ? "" : button.getText()));
            buttons.add(button);
            add(button);
        }
        choose(initial == null ? "" : initial.trim());
    }

    String value() { return value; }

    void onChange(Runnable action) { onChange = action == null ? () -> {} : action; }

    void choose(String choice) {
        value = choice;
        for (JToggleButton b : buttons) b.setSelected(b.getText().equals(choice));
        onChange.run();
    }

    List<JToggleButton> buttons() { return buttons; }

    /** A FlowLayout whose height counts its lines: at the width it is given, the buttons that do not fit go below,
     *  and the field grows to show them. */
    static final class Wrap extends FlowLayout {
        Wrap() { super(FlowLayout.LEFT, 0, 0); }
        @Override public Dimension preferredLayoutSize(Container target) { return size(target, true); }
        @Override public Dimension minimumLayoutSize(Container target) {
            Dimension minimum = size(target, false);
            minimum.width -= getHgap() + 1;
            return minimum;
        }
        private Dimension size(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int width = target.getSize().width;
                Container parent = target;
                while (width == 0 && parent.getParent() != null) { parent = parent.getParent(); width = parent.getSize().width; }
                Insets insets = target.getInsets();
                int max = (width == 0 ? Integer.MAX_VALUE : width) - insets.left - insets.right;
                int lineWidth = 0, lineHeight = 0, totalWidth = 0, totalHeight = 0;
                for (Component c : target.getComponents()) {
                    if (!c.isVisible()) continue;
                    Dimension d = preferred ? c.getPreferredSize() : c.getMinimumSize();
                    if (lineWidth > 0 && lineWidth + d.width > max) {
                        totalWidth = Math.max(totalWidth, lineWidth);
                        totalHeight += lineHeight + getVgap();
                        lineWidth = 0;
                        lineHeight = 0;
                    }
                    lineWidth += d.width + getHgap();
                    lineHeight = Math.max(lineHeight, d.height);
                }
                totalWidth = Math.max(totalWidth, lineWidth);
                totalHeight += lineHeight;
                return new Dimension(totalWidth + insets.left + insets.right, totalHeight + insets.top + insets.bottom);
            }
        }
    }
}
