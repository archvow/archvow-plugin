package org.archvow.idea;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Read-only, selectable, syntax-highlighted view of a component's real vow. */
final class ArchvowVowViewer {
    private static final int MAX_BYTES = 1_000_000;
    private static final Set<String> KEYWORDS = Set.of(
        "vow", "name", "type", "layer", "module", "ticket", "description", "uses",
        "symbol", "kind", "signature", "decision", "question", "answer", "by", "on",
        "hazard", "severity", "invariant", "prevents", "rule", "assumptions",
        "status", "safety", "columns", "personal", "tasks", "missing", "operations", "example", "fingerprint");
    private static final Pattern QUOTED = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
    private static final Pattern FIRST_WORD = Pattern.compile("(?s)^(\\s*)([a-z][a-z_]*)(.*)$");

    private ArchvowVowViewer() {}

    static String read(String projectRoot, String componentId) {
        if (projectRoot == null || projectRoot.isBlank() || componentId == null
            || !componentId.matches("[A-Za-z_][A-Za-z_0-9-]*")) {
            return "No component spec selected / Aucune spécification sélectionnée.";
        }
        try {
            Path root = Path.of(projectRoot).toRealPath();
            Path spec = ArchvowLayout.componentVow(root, componentId);
            if (spec == null) return "vow not found / vow introuvable : " + componentId;
            if (!spec.toRealPath().startsWith(root)) return "File outside the project / Fichier hors du projet.";
            if (Files.size(spec) > MAX_BYTES) return "vow too large to preview / Fichier trop volumineux pour l'aperçu.";
            return Files.readString(spec, StandardCharsets.UTF_8);
        } catch (IOException | SecurityException e) {
            return "Cannot read the vow / Impossible de lire le vow : " + e.getMessage();
        }
    }

    static JTextPane create() {
        JTextPane editor = new JTextPane();
        editor.setEditable(false);
        editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        editor.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        return editor;
    }

    static void display(JTextPane editor, String source) {
        StyledDocument doc = editor.getStyledDocument();
        try {
            doc.remove(0, doc.getLength());
            boolean dark = isDark();
            SimpleAttributeSet plain = style(dark ? new Color(218, 226, 233) : new Color(41, 48, 57), false, false);
            SimpleAttributeSet keyword = style(dark ? new Color(96, 202, 222) : new Color(24, 103, 145), true, false);
            SimpleAttributeSet comment = style(dark ? new Color(142, 152, 165) : new Color(119, 128, 138), false, true);
            SimpleAttributeSet quoted = style(dark ? new Color(146, 216, 157) : new Color(26, 134, 77), false, false);
            SimpleAttributeSet declaration = style(dark ? new Color(215, 171, 242) : new Color(128, 65, 174), true, false);
            for (String line : source.split("(?<=\\n)", -1)) {
                if (line.stripLeading().startsWith("#")) {
                    doc.insertString(doc.getLength(), line, comment);
                    continue;
                }
                Matcher first = FIRST_WORD.matcher(line);
                if (!first.matches()) {
                    doc.insertString(doc.getLength(), line, plain);
                    continue;
                }
                doc.insertString(doc.getLength(), first.group(1), plain);
                String word = first.group(2);
                doc.insertString(doc.getLength(), word, KEYWORDS.contains(word) ? keyword : plain);
                String tail = first.group(3);
                Matcher match = QUOTED.matcher(tail);
                int pos = 0;
                while (match.find()) {
                    doc.insertString(doc.getLength(), tail.substring(pos, match.start()),
                        ("decision".equals(word) || "hazard".equals(word) || "invariant".equals(word)) ? declaration : plain);
                    doc.insertString(doc.getLength(), match.group(), quoted);
                    pos = match.end();
                }
                doc.insertString(doc.getLength(), tail.substring(pos),
                    ("decision".equals(word) || "hazard".equals(word) || "invariant".equals(word)) ? declaration : plain);
            }
            editor.setCaretPosition(0);
        } catch (BadLocationException ex) {
            editor.setText(source);
        }
    }

    private static SimpleAttributeSet style(Color color, boolean bold, boolean italic) {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setForeground(attrs, color);
        StyleConstants.setBold(attrs, bold);
        StyleConstants.setItalic(attrs, italic);
        return attrs;
    }

    private static boolean isDark() {
        Color c = UIManager.getColor("Panel.background");
        return c != null && (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) < 128;
    }
}
