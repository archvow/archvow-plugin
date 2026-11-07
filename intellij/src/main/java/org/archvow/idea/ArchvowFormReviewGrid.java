package org.archvow.idea;

import java.util.List;
import java.util.Map;

/** The command form's read-only Review step, using the same three-column cards as VS Code. */
final class ArchvowFormReviewGrid {
    private ArchvowFormReviewGrid() { }

    /** Keep a three-column grid when it fits; stack cards in narrow editor splits. */
    static int columnsForWidth(int width) {
        return width > 0 && width < 470 ? 1 : width > 0 && width < 730 ? 2 : 3;
    }

    static String html(List<Map.Entry<String, String>> entries, boolean dark, int columns) {
        columns = Math.max(1, Math.min(3, columns));
        String foreground = dark ? "#EDEBF0" : "#262930";
        String muted = dark ? "#B8B4BE" : "#626B77";
        String card = dark ? "#322F33" : "#F1F3F6";
        StringBuilder html = new StringBuilder("<html><body style='font-family:sans-serif;'>")
            .append("<table width='100%' border='0' cellspacing='7' cellpadding='0'>");
        for (int i = 0; i < entries.size(); i++) {
            if (i % columns == 0) html.append("<tr>");
            Map.Entry<String, String> entry = entries.get(i);
            String label = entry.getKey() == null ? "" : entry.getKey();
            String value = entry.getValue() == null || entry.getValue().isBlank() ? "—" : entry.getValue();
            html.append("<td width='").append(100 / columns).append("%' valign='top' bgcolor='").append(card).append("'>")
                .append("<table width='100%' border='0' cellspacing='0' cellpadding='8'><tr><td>")
                .append("<font color='").append(foreground).append("'><b>").append(escape(label)).append("</b></font>")
                .append("<br><font color='").append(muted).append("'>").append(escape(value)).append("</font>")
                .append("</td></tr></table></td>");
            if (i % columns == columns - 1) html.append("</tr>");
        }
        int remainder = entries.size() % columns;
        if (remainder != 0) {
            for (int i = remainder; i < columns; i++) html.append("<td></td>");
            html.append("</tr>");
        }
        return html.append("</table></body></html>").toString();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
