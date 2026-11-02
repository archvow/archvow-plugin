package org.archvow.idea;

import javax.swing.*;
import java.awt.*;

/** A result header: keep every action visible, even in a narrow editor tab or translated UI. */
final class ArchvowResultHeader extends JPanel {
    private final JComponent heading;
    private final JPanel buttons;
    private static final int GAP = 5;

    ArchvowResultHeader(JComponent heading, JPanel buttons) {
        super(null);
        this.heading = heading;
        this.buttons = buttons;
        setOpaque(false);
        add(heading);
        add(buttons);
    }

    private int buttonWidth() {
        int width = 10;
        boolean first = true;
        for (Component button : buttons.getComponents()) {
            if (!button.isVisible()) continue;
            if (!first) width += 5;
            width += button.getPreferredSize().width;
            first = false;
        }
        return width;
    }

    private int controlsHeight(int width) {
        buttons.setSize(Math.max(1, width), buttons.getHeight());
        return buttons.getPreferredSize().height;
    }

    @Override public Dimension getPreferredSize() {
        int natural = heading.getPreferredSize().width + GAP + buttonWidth();
        int width = getWidth();
        if (getParent() != null && getParent().getWidth() > 0) {
            Insets in = getParent().getInsets();
            width = getParent().getWidth() - in.left - in.right;
        }
        if (width <= 0) width = natural;
        int titleHeight = heading.getPreferredSize().height;
        boolean single = width >= natural;
        int height = single
            ? Math.max(titleHeight, controlsHeight(buttonWidth()))
            : titleHeight + 3 + controlsHeight(width);
        return new Dimension(width, height);
    }

    @Override public Dimension getMinimumSize() {
        return new Dimension(100, heading.getPreferredSize().height + 3 + controlsHeight(100));
    }

    @Override public void setBounds(int x, int y, int width, int height) {
        boolean resized = width != getWidth();
        super.setBounds(x, y, width, height);
        if (resized && isDisplayable()) SwingUtilities.invokeLater(this::revalidate);
    }

    @Override public void doLayout() {
        int width = Math.max(0, getWidth());
        int titleHeight = heading.getPreferredSize().height;
        int controlsWidth = buttonWidth();
        if (width >= heading.getPreferredSize().width + GAP + controlsWidth) {
            heading.setBounds(0, 0, width - controlsWidth - GAP, Math.max(titleHeight, controlsHeight(controlsWidth)));
            buttons.setBounds(width - controlsWidth, 0, controlsWidth, controlsHeight(controlsWidth));
        } else {
            heading.setBounds(0, 0, width, titleHeight);
            buttons.setBounds(0, titleHeight + 3, width, controlsHeight(width));
        }
    }
}
