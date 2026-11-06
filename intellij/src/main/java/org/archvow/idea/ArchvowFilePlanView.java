package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Native Swing equivalent of VS Code's filesBlock: a folder icon on each directory,
 * a document/folder icon on each item, its change status and optional note.
 * The drawings come from the shared icons.json (via ArchvowTreeIcons), so their
 * stroke and colour follow IntelliJ's active light or dark theme.
 */
final class ArchvowFilePlanView extends JPanel {
    private static final Map<String, String> EN = Map.of(
        "new", "new", "updated", "updated", "removed", "removed", "kept", "kept", "planned", "planned");
    private static final Map<String, String> FR = Map.of(
        "new", "nouveau", "updated", "modifié", "removed", "retiré", "kept", "gardé", "planned", "prévu");

    ArchvowFilePlanView() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setAlignmentX(LEFT_ALIGNMENT);
        setVisible(false);
    }

    /** A diagnostic's file, using the very same Files tree as a command form. */
    void showLocation(String path, int line, boolean fr) {
        String atLine = line > 0 ? (fr ? "ligne " : "line ") + line : "";
        showRows(List.of(new ArchvowFilePlan.Row("location", path, atLine)), fr);
    }

    void showRows(List<ArchvowFilePlan.Row> rows, boolean fr) {
        removeAll();
        for (boolean planned : new boolean[]{false, true}) {
            List<ArchvowFilePlan.Row> part = new ArrayList<>();
            for (ArchvowFilePlan.Row row : rows)
                if ("planned".equals(row.change()) == planned) part.add(row);
            if (part.isEmpty()) continue;
            String heading = planned
                ? (fr ? "Prévus - écrits plus tard par l'IA ou un développeur (archvow n'écrit jamais de code)"
                      : "Planned - written later by the AI or a developer (archvow never writes code)")
                : (fr ? "Fichiers" : "Files");
            ArchvowWrapLabel head = wrapped(heading, muted(), false);
            head.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
            add(head);
            String folder = null;
            for (ArchvowFilePlan.Row row : part) {
                String path = row.path();
                boolean isDir = path.endsWith("/");
                int cut = isDir ? path.substring(0, path.length() - 1).lastIndexOf('/') : path.lastIndexOf('/');
                String dir = cut >= 0 ? path.substring(0, cut + 1) : "";
                String leaf = cut >= 0 ? path.substring(cut + 1) : path;
                if (!dir.equals(folder)) {
                    add(folderLine(dir.isEmpty() ? "./" : dir));
                    folder = dir;
                }
                JPanel file = new JPanel();
                file.setLayout(new BoxLayout(file, BoxLayout.X_AXIS));
                file.setOpaque(false);
                file.setAlignmentX(LEFT_ALIGNMENT);
                file.setBorder(BorderFactory.createEmptyBorder(1, 14, 0, 0));
                // VS Code renders file names as code chips, distinct from the muted description below.
                // Keep the icon OUTSIDE the chip, as in its filesBlock tree.
                JLabel icon = iconLabel(isDir ? "folder" : "document", "", false);
                file.add(icon);
                file.add(Box.createHorizontalStrut(5));
                JLabel name = fileName(leaf);
                file.add(name);
                file.add(Box.createHorizontalStrut(5));
                String statusText = "location".equals(row.change()) ? row.note()
                    : (fr ? FR : EN).getOrDefault(row.change(), row.change());
                if (!statusText.isBlank()) {
                    JLabel status = new JLabel(statusText);
                    status.putClientProperty("html.disable", Boolean.TRUE);
                    status.setForeground(changeColor(row.change()));
                    file.add(status);
                }
                file.add(Box.createHorizontalGlue());
                add(file);
                if (!"location".equals(row.change()) && !row.note().isBlank()) {
                    ArchvowWrapLabel note = wrapped(row.note(), muted(), true);
                    note.setBorder(BorderFactory.createEmptyBorder(1, 38, 2, 0));
                    add(note);
                }
            }
        }
        setVisible(!rows.isEmpty());
        revalidate();
        repaint();
    }

    private static JPanel folderLine(String path) {
        JPanel line = new JPanel(new BorderLayout());
        line.setOpaque(false);
        line.setAlignmentX(LEFT_ALIGNMENT);
        line.setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 0));
        JLabel label = iconLabel("folder", path, false);
        label.setForeground(muted());
        line.add(label, BorderLayout.WEST);
        return line;
    }

    private static JLabel iconLabel(String kind, String text, boolean code) {
        JLabel label = new JLabel(text, ArchvowTreeIcons.icon(kind, null), SwingConstants.LEFT);
        // Paths are plain text, including when a user-supplied name starts with <html>.
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setIconTextGap(6);
        if (code) label.setFont(new Font(Font.MONOSPACED, Font.PLAIN, label.getFont().getSize()));
        return label;
    }

    /** A compact code-style filename badge that adapts its contrast to IntelliJ's theme. */
    private static JLabel fileName(String filename) {
        JLabel label = new JLabel(filename) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(ArchvowMotion.dark() ? new Color(62, 61, 66) : new Color(237, 237, 237));
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), 5, 5);
                } finally {
                    g.dispose();
                }
                setForeground(ArchvowMotion.dark() ? new Color(255, 142, 132) : new Color(166, 35, 37));
                super.paintComponent(graphics);
            }
        };
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setOpaque(false);
        label.setFont(new Font(Font.MONOSPACED, Font.PLAIN, label.getFont().getSize()));
        label.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        return label;
    }

    private static ArchvowWrapLabel wrapped(String text, Color ink, boolean compact) {
        ArchvowWrapLabel label = new ArchvowWrapLabel();
        label.setText(text);
        label.setOpaque(false);
        label.setForeground(ink);
        if (compact) label.setFont(label.getFont().deriveFont(10.5f));
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static Color muted() {
        return ArchvowMotion.dark() ? new Color(162, 172, 186) : new Color(85, 95, 110);
    }

    private static Color changeColor(String change) {
        boolean dark = ArchvowMotion.dark();
        return switch (change) {
            case "new" -> dark ? new Color(96, 207, 143) : new Color(29, 149, 83);
            case "updated" -> dark ? new Color(99, 172, 255) : new Color(48, 116, 220);
            case "removed" -> dark ? new Color(255, 118, 109) : new Color(190, 50, 45);
            default -> muted();
        };
    }
}
