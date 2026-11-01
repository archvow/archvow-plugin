package org.archvow.idea;

import javax.swing.*;
import java.awt.*;

/**
 * A label whose text goes on as many lines as the width needs (the branch advice in Overview): a JLabel
 * cut it with "…" in a narrow tool window. Not editable, not focusable, the label's font. Plain Swing:
 * tested headless ({@code UiPolishRegression}).
 */
final class ArchvowWrapLabel extends JTextArea {
    ArchvowWrapLabel() {
        setEditable(false);
        setFocusable(false);
        setLineWrap(true);
        setWrapStyleWord(true);
        setHighlighter(null);
        Font font = UIManager.getFont("Label.font");
        if (font != null) setFont(font);
    }

    @Override public void updateUI() {
        super.updateUI();
        Font font = UIManager.getFont("Label.font");
        if (font != null) setFont(font);
    }

    /** Without a width yet (a form being laid out): wrapped at this one, never as wide as its longest line. */
    static final int FIRST_WIDTH = 240;

    /**
     * As tall as the text at the width given (its parent's when it has none yet; FIRST_WIDTH when neither
     * has one: a hint under a field made the whole form as wide as its text, cut at the window's edge).
     */
    @Override public Dimension getPreferredSize() {
        int width = getWidth() > 0 ? getWidth() : getParent() == null || getParent().getWidth() == 0 ? FIRST_WIDTH : getParent().getWidth();
        if (getSize().width != width) setSize(width, Short.MAX_VALUE);
        Dimension size = super.getPreferredSize();
        return new Dimension(width, size.height);
    }

    /** Given another width: its height follows on the next layout. */
    @Override public void setBounds(int x, int y, int width, int height) {
        boolean changed = width > 0 && width != getWidth();
        super.setBounds(x, y, width, height);
        if (changed) SwingUtilities.invokeLater(this::revalidate);
    }
}
