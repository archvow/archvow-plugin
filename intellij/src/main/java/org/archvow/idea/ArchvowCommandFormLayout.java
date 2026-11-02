package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Layout shared by every command form (init, decision make, question remove, ...).
 * A collapsed GridBag row must not influence the label column's width: when it is
 * expanded again the labels keep their width and the form grows vertically.
 * Plain Swing so that the collapse/expand cycle can be tested without an IDE.
 */
final class ArchvowCommandFormLayout {
    private ArchvowCommandFormLayout() { }

    static final int LABEL_WIDTH = 210;
    static final int FORM_MIN_WIDTH = 470;

    /** A predictable, vertically stacked label instead of an HTML JLabel sized by GridBag. */
    static JComponent fieldLabel(String text, String flag, boolean required, Color mutedColor) {
        JPanel label = new JPanel();
        label.setOpaque(false);
        label.setLayout(new BoxLayout(label, BoxLayout.Y_AXIS));

        ArchvowWrapLabel title = new ArchvowWrapLabel();
        title.setOpaque(false);
        title.setText(text);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        int titleWidth = LABEL_WIDTH - (required ? 18 : 0);
        title.setSize(titleWidth, Short.MAX_VALUE);
        int titleHeight = title.getPreferredSize().height;
        JPanel titleLine = new JPanel(new BorderLayout(2, 0));
        titleLine.setOpaque(false);
        titleLine.add(title, BorderLayout.CENTER);
        if (required) {
            JLabel star = new JLabel("*");
            star.setForeground(new Color(0xD9534F));
            star.setVerticalAlignment(SwingConstants.TOP);
            titleLine.add(star, BorderLayout.EAST);
        }
        titleLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLine.setPreferredSize(new Dimension(LABEL_WIDTH, titleHeight));
        titleLine.setMaximumSize(new Dimension(LABEL_WIDTH, titleHeight));
        label.add(titleLine);
        int height = titleHeight;
        if (!flag.isBlank()) {
            JLabel option = new JLabel(flag);
            option.setFont(option.getFont().deriveFont(Math.max(9f, option.getFont().getSize2D() - 2f)));
            option.setForeground(mutedColor);
            option.setAlignmentX(Component.LEFT_ALIGNMENT);
            label.add(option);
            height += option.getPreferredSize().height;
        }
        label.setPreferredSize(new Dimension(LABEL_WIDTH, Math.max(24, height)));
        label.setMinimumSize(label.getPreferredSize());
        label.setToolTipText(text + (flag.isBlank() ? "" : "  " + flag));
        return label;
    }

    /** Right-hand help occupies approximately a third of the editor like in VS Code. */
    static int helpWidth(int editorWidth) {
        if (editorWidth <= 0) return 440;
        return Math.min(600, Math.max(240, Math.round(editorWidth * 0.34f)));
    }

    /**
     * Update the viewport as well as the grid; revalidating only the grid leaves
     * lower panels clipped after a collapsed form is expanded again.
     */
    static void setSectionExpanded(JPanel form, JScrollPane scroller, JToggleButton toggle, List<JComponent> parts) {
        boolean expanded = toggle.isSelected();
        for (JComponent part : parts) part.setVisible(expanded);
        form.revalidate();
        if (scroller != null) {
            scroller.getViewport().revalidate();
            scroller.revalidate();
        }
        form.repaint();
        SwingUtilities.invokeLater(() -> {
            // Layout is complete before requesting scrolling to the newly opened fields.
            if (scroller != null) scroller.validate();
            if (toggle.getParent() != form) return; // the command was replaced meanwhile
            Rectangle heading = SwingUtilities.convertRectangle(toggle.getParent(), toggle.getBounds(), form);
            Rectangle area = new Rectangle(0, heading.y, Math.max(1, form.getWidth()), heading.height);
            if (expanded && !parts.isEmpty()) {
                JComponent first = parts.get(0);
                if (first.getParent() == form) {
                    Rectangle field = SwingUtilities.convertRectangle(first.getParent(), first.getBounds(), form);
                    // Keep the heading and first field in view; further fields remain reachable by scrolling.
                    area.height = Math.max(heading.height, Math.min(heading.height + 110, field.y + field.height - heading.y));
                }
            }
            form.scrollRectToVisible(area);
        });
    }

    /** Never squeeze a complex form into a column narrower than its fields and labels. */
    static final class WidthTrackingPanel extends JPanel implements Scrollable {
        WidthTrackingPanel(LayoutManager layout) { super(layout); }
        @Override public Dimension getMinimumSize() {
            Dimension original = super.getMinimumSize();
            return new Dimension(Math.max(FORM_MIN_WIDTH, original.width), original.height);
        }
        @Override public Dimension getPreferredSize() {
            Dimension original = super.getPreferredSize();
            return new Dimension(Math.max(FORM_MIN_WIDTH, original.width), original.height);
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 18; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return Math.max(18, visible.height - 32); }
        @Override public boolean getScrollableTracksViewportWidth() {
            return !(getParent() instanceof JViewport viewport) || viewport.getWidth() >= FORM_MIN_WIDTH;
        }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
