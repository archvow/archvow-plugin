package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/** Regression for both screenshots: reopen More options and keep the entire form scrollable. */
public final class CommandFormLayoutRegression {
    private static void check(boolean ok, String description) {
        if (!ok) throw new AssertionError(description);
    }

    private static GridBagConstraints cell(int x, int y, int width, boolean stretch) {
        GridBagConstraints constraint = new GridBagConstraints();
        constraint.gridx = x; constraint.gridy = y; constraint.gridwidth = width;
        constraint.weightx = stretch ? 1 : 0;
        constraint.fill = GridBagConstraints.HORIZONTAL;
        constraint.insets = new Insets(4, 3, 4, 3);
        return constraint;
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component part : container.getComponents()) if (part instanceof Container inner) layout(inner);
    }

    private static void cycle(String command, String[] optionalLabels, int viewportWidth) {
        JPanel form = new ArchvowCommandFormLayout.WidthTrackingPanel(new GridBagLayout());
        form.add(ArchvowCommandFormLayout.fieldLabel("Composant", "--component", true, Color.GRAY), cell(0, 0, 1, false));
        form.add(new JTextField("spark"), cell(1, 0, 1, true));
        JToggleButton toggle = new JToggleButton("Plus d'options");
        form.add(toggle, cell(0, 1, 2, true));
        java.util.List<JComponent> optional = new java.util.ArrayList<>();
        int row = 2;
        for (String text : optionalLabels) {
            JComponent label = ArchvowCommandFormLayout.fieldLabel(text, "--force", false, Color.GRAY);
            JTextField input = new JTextField("value");
            form.add(label, cell(0, row, 1, false));
            form.add(input, cell(1, row++, 1, true));
            label.setVisible(false); input.setVisible(false);
            optional.add(label); optional.add(input);
        }
        JPanel preview = new JPanel(new BorderLayout());
        preview.setBorder(BorderFactory.createTitledBorder(command.equals("decision make") ? "Question et statut" : "Autres réponses"));
        preview.add(new JTextArea(4, 20), BorderLayout.CENTER);
        preview.add(new JButton("Charger la question"), BorderLayout.SOUTH);
        form.add(preview, cell(0, row, 2, true));
        JScrollPane scroll = new JScrollPane(form);
        scroll.setSize(viewportWidth, 240);
        scroll.doLayout();
        Dimension closed = form.getPreferredSize();
        toggle.setSelected(true);
        ArchvowCommandFormLayout.setSectionExpanded(form, scroll, toggle, optional);
        Dimension opened = form.getPreferredSize();
        check(opened.height > closed.height, command + ": opening did not expand form");
        check(form.getPreferredSize().width >= ArchvowCommandFormLayout.FORM_MIN_WIDTH, "form too narrow");
        layout(scroll);
        layout(form);
        for (int index = 0; index < optional.size(); index += 2) {
            JComponent label = optional.get(index);
            check(label.isVisible(), command + ": hidden option");
            check(label.getWidth() >= ArchvowCommandFormLayout.LABEL_WIDTH,
                command + ": label shrank to " + label.getWidth());
            check(label.getPreferredSize().height >= 24, command + ": title/flag clipped");
        }
        check(preview.getY() >= optional.get(optional.size()-1).getY(), command + ": bottom form overlaps opened options");
        check(scroll.getVerticalScrollBar().getMaximum() > scroll.getVerticalScrollBar().getVisibleAmount(),
            command + ": lower panel inaccessible by vertical scrolling");
        toggle.setSelected(false);
        ArchvowCommandFormLayout.setSectionExpanded(form, scroll, toggle, optional);
        Dimension collapsed = form.getPreferredSize();
        check(collapsed.height < opened.height, command + ": form did not collapse again");
        toggle.setSelected(true);
        ArchvowCommandFormLayout.setSectionExpanded(form, scroll, toggle, optional);
        layout(scroll); layout(form);
        check(form.getPreferredSize().height == opened.height, command + ": size changed after reopen");
        for (int index = 0; index < optional.size(); index += 2) {
            JComponent label = optional.get(index);
            check(label.getWidth() >= ArchvowCommandFormLayout.LABEL_WIDTH,
                command + ": label shrunk after reopen: " + label.getWidth());
        }
        check(preview.isVisible(), command + ": lower panel must remain visible");
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (int width : new int[] {1600, 900, 520, 390}) {
                cycle("init", new String[]{"Dossier des sources", "À nouveau", "Sans Git", "Ne rien demander", "Code existant"}, width);
                cycle("decision make", new String[]{"Type", "Effacer la réponse"}, width);
                cycle("question remove", new String[]{"Sans demander"}, width);
            }
            check(ArchvowCommandFormLayout.helpWidth(1750) >= 550, "help panel stays too narrow on wide editor");
            check(ArchvowCommandFormLayout.helpWidth(850) <= 300, "help panel consumes narrow editor");
            check(ArchvowCommandFormLayout.helpWidth(1920) == 600, "help width cap");
        });
        // drain toggle callbacks, as EDT does when the user clicks a toggle
        SwingUtilities.invokeAndWait(() -> { });
        System.out.println("OK: command form labels, collapse/reopen, scrolling and proportional help width");
    }
}
