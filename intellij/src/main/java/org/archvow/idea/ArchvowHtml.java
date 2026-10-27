package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;

/**
 * Text sizes of the HTML the plugin shows (results, the component's head, the answer panels...), taken
 * from the IDE's own font: the body is the size of a label, a heading a little larger. Swing reads a CSS
 * size in "px" as 1.3 point, so "12px" drew 15.6 pt text and an h3 even larger (the doctor's result, 0.11.4):
 * every size here is in "pt", which Swing reads as the Java font size. No h1-h6 (their own sizes are large
 * in Swing): {@link #heading}. Plain Java: tested headless ({@code UiPolishRegression}).
 */
final class ArchvowHtml {
    private ArchvowHtml() { }

    /** The IDE's label size (13 when the IDE gives none). */
    static int base() {
        Font font = UIManager.getFont("Label.font");
        return font == null ? 13 : Math.max(9, Math.round(font.getSize2D()));
    }

    /** The label's family, for the HTML body (a CSS name Swing knows when there is none). */
    static String family() {
        Font font = UIManager.getFont("Label.font");
        return font == null ? "sans-serif" : font.getFamily().replace("'", "");
    }

    /** "font-size:Npt", N = the label's size times {@code factor}. */
    static String size(double factor) {
        return "font-size:" + Math.max(8, Math.round(base() * factor)) + "pt";
    }

    /** The start of a document: the label's family and size, these colours (null: none). */
    static String body(String foreground, String background) {
        return "<html><body style='font-family:" + family() + "; " + size(1) + ";"
            + (foreground == null ? "" : " color:" + foreground + ";")
            + (background == null ? "" : " background-color:" + background + ";") + "'>";
    }

    /** A heading {@code factor} times the body's size, bold, with these extra styles (margins, colour). */
    static String heading(String text, double factor, String style) {
        return "<div style='" + size(factor) + "; font-weight:bold;" + (style == null ? "" : " " + style) + "'>" + text + "</div>";
    }

    /** Whether a document has no size Swing would read too large: no px size, no h1-h6. */
    static boolean wellSized(String html) {
        String lower = html == null ? "" : html.toLowerCase(Locale.ROOT);
        return !lower.matches("(?s).*font-size:\\s*[0-9.]+px.*") && !lower.matches("(?s).*<h[1-6][ >].*");
    }
}
