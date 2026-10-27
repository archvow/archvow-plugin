package org.archvow.idea;

import javax.swing.*;
import java.awt.*;

/** Status is visible at rest, hover removes tint, selection remains prominent. */
public final class StatusPaletteRegression {
    public static void main(String[] args) {
        for (Color theme : new Color[]{new Color(248,249,252), new Color(34,36,42)}) {
            UIManager.put("Panel.background", theme);
            for (boolean pending : new boolean[]{true, false}) {
                Color status = ArchvowStatusPalette.statusColor(pending);
                Color normal = ArchvowStatusPalette.rowBackground(theme, status, false, 0);
                Color hover = ArchvowStatusPalette.rowBackground(theme, status, false, 1);
                Color selected = ArchvowStatusPalette.rowBackground(theme, status, true, 0);
                if (distance(normal, theme) < 10) throw new AssertionError("status is invisible at rest");
                if (distance(hover, theme) != 0) throw new AssertionError("hover should be neutral");
                if (distance(selected, theme) <= distance(normal, theme))
                    throw new AssertionError("selection should be stronger than status at rest");
            }
        }
        System.out.println("StatusPaletteRegression passed in light and dark modes");
    }
    private static int distance(Color a, Color b) {
        return Math.abs(a.getRed()-b.getRed())+Math.abs(a.getGreen()-b.getGreen())+Math.abs(a.getBlue()-b.getBlue());
    }
}
