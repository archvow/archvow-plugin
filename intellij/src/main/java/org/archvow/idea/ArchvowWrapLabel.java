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

    /** As tall as the text at the width given (its parent's when it has none yet). */
    @Override public Dimension getPreferredSize() {
        int width = getWidth() > 0 ? getWidth() : getParent() == null ? 0 : getParent().getWidth();
        if (width > 0 && getSize().width != width) setSize(width, Short.MAX_VALUE);
        Dimension size = super.getPreferredSize();
        return width > 0 ? new Dimension(width, size.height) : size;
    }
}
