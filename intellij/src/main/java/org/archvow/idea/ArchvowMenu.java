package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * 0.17.1: the plugin's menus (✦ AI ▾, + Add ▾, Save ▾) as rows, the same in every theme - a heading, a line
 * between the groups, each row's background on hover, a row that cannot run greyed with why (never hidden), the
 * suggested one tinted and tagged. JMenuItem with HTML did none of that (no hover on two-line items, no line
 * between, a clipped selection arc on the left in the new UI).
 */
final class ArchvowMenu {
    private ArchvowMenu() {}

    /** One row: its name, a line under it (what it does, or why not yet), a small tag on the right. */
    record Item(String title, String note, String tag, boolean enabled, boolean suggested, String suggestedLabel, Runnable action) {
        Item(String title, String note, String tag, Runnable action) { this(title, note, tag, true, false, "", action); }
    }

    static final int TEXT_WIDTH = 380;

    /** The menu: an optional heading, then the groups of rows, a line between groups. */
    static JPopupMenu create(String heading, List<List<Item>> groups) {
        JPopupMenu menu = new JPopupMenu();
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
        menu.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(border(), 1), BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        if (heading != null && !heading.isBlank()) {
            JLabel head = new JLabel(heading);
            head.setForeground(muted());
            head.setFont(head.getFont().deriveFont(Font.BOLD, 10.5f));
            head.setBorder(BorderFactory.createEmptyBorder(5, 10, 4, 10));
            head.setAlignmentX(Component.LEFT_ALIGNMENT);
            menu.add(head);
        }
        boolean first = true;
        for (List<Item> group : groups) {
            if (group.isEmpty()) continue;
            if (!first) menu.add(separator());
            first = false;
            for (Item item : group) menu.add(row(menu, item));
        }
        return menu;
    }

    /** A thin line between two groups. */
    static JComponent separator() {
        JPanel line = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(border());
                g.fillRect(6, getHeight() / 2, getWidth() - 12, 1);
            }
        };
        line.setOpaque(false);
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        line.setPreferredSize(new Dimension(10, 9));
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 9));
        return line;
    }

    /** One row: hover background, click runs it and closes the menu; a disabled one is greyed and does nothing. */
    static JPanel row(JPopupMenu menu, Item item) {
        JPanel row = new JPanel(new BorderLayout(14, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        Color normal = item.suggested() && item.enabled() ? ArchvowMotion.blend(background(), ArchvowMotion.accent(), .16) : background();
        row.setBackground(normal);
        row.setOpaque(true);
        String titleColor = hex(item.enabled() ? foreground() : muted());
        String noteColor = hex(item.enabled() ? muted() : warn());
        String html = "<html><body style='width:" + TEXT_WIDTH + "px'><b style='color:" + titleColor + "'>" + esc(item.title()) + "</b>"
            + (item.suggested() && item.enabled() ? " &nbsp;<b style='color:" + hex(ArchvowMotion.accent()) + ";font-size:90%'>" + esc(item.suggestedLabel()) + "</b>" : "")
            + (item.note() == null || item.note().isBlank() ? "" : "<br><span style='color:" + noteColor + "'>" + esc(item.note()) + "</span>")
            + "</body></html>";
        JLabel text = new JLabel(html);
        row.add(text, BorderLayout.CENTER);
        if (item.tag() != null && !item.tag().isBlank()) {
            JLabel tag = new JLabel(item.tag());
            tag.setForeground(muted());
            tag.setFont(tag.getFont().deriveFont(11f));
            tag.setVerticalAlignment(SwingConstants.TOP);
            row.add(tag, BorderLayout.EAST);
        }
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        if (item.enabled()) {
            row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            row.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { row.setBackground(hover()); }
                @Override public void mouseExited(MouseEvent e) {
                    if (!row.contains(e.getPoint())) row.setBackground(normal);
                }
                @Override public void mouseReleased(MouseEvent e) {
                    if (!row.contains(e.getPoint())) return;
                    if (menu != null) menu.setVisible(false);
                    if (item.action() != null) item.action().run();
                }
            });
        }
        row.putClientProperty("archvow.menu.enabled", item.enabled());
        return row;
    }

    // ------------------------------------------------------------------ the theme's colours, with fallbacks

    static Color background() { return color("PopupMenu.background", color("Panel.background", Color.WHITE)); }
    static Color foreground() { return color("MenuItem.foreground", color("Label.foreground", Color.BLACK)); }
    static Color muted() { return color("Label.disabledForeground", ArchvowMotion.blend(background(), foreground(), .55)); }
    static Color border() { return color("Separator.foreground", ArchvowMotion.blend(background(), foreground(), .2)); }
    static Color warn() { return dark() ? new Color(0xE0, 0xA4, 0x58) : new Color(0x9C, 0x59, 0x12); }

    /** The row under the mouse: the theme's list hover, else a light tint of the text colour. */
    static Color hover() {
        Color c = UIManager.getColor("List.hoverBackground");
        if (c == null) c = UIManager.getColor("Tree.hoverBackground");
        return c != null ? c : ArchvowMotion.blend(background(), foreground(), .12);
    }

    static boolean dark() {
        Color b = background();
        return (b.getRed() * 299 + b.getGreen() * 587 + b.getBlue() * 114) / 1000 < 128;
    }

    private static Color color(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
