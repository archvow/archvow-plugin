package org.archvow.idea;

import javax.swing.*;
import java.awt.*;

/** Single status palette for vow and task lists; selection remains unchanged. */
final class ArchvowStatusPalette {
    private ArchvowStatusPalette() {}

    static Color statusColor(boolean pending) {
        return pending ? (ArchvowMotion.dark() ? new Color(235, 181, 90) : new Color(190, 126, 27))
            : (ArchvowMotion.dark() ? new Color(96, 207, 143) : new Color(29, 149, 83));
    }

    static Color rowBackground(Color background, Color status, boolean selected, float hover) {
        double intensity = ArchvowMotion.dark() ? .26 : .18;
        if (!selected) intensity = (ArchvowMotion.dark() ? .14 : .12) * (1 - Math.min(1, Math.max(0, hover)));
        return ArchvowMotion.blend(background, status, intensity);
    }
}
