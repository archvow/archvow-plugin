package org.archvow.idea;

import javax.swing.*;
import java.awt.*;

/**
 * 1.2.1: the IDE's own font for text the plugin draws itself. Font.SANS_SERIF is Java's logical font, not the
 * IDE's: its letters looked wider and spaced apart beside the labels around them.
 */
final class ArchvowFonts {
    private ArchvowFonts() { }

    static String family() {
        Font label = UIManager.getFont("Label.font");
        return label == null ? Font.SANS_SERIF : label.getFamily();
    }

    static Font ui(int style, float size) {
        return new Font(family(), style, Math.round(size));
    }
}
