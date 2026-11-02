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
        super(new FlowLayout(FlowLayout.LEFT, 0, 0));
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
}
