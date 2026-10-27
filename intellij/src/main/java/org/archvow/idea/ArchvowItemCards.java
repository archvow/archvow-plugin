package org.archvow.idea;

import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Locale;

/**
 * The items of a component view as cards, on the left of the split view ({@link ArchvowSplitView}):
 * "3 · Header row missing: infer the columns or fail?", then the recorded answer (green) with who
 * recorded it and when, or "● Open" (amber). The VS Code page draws the same cards
 * ({@code media/workspace.js}, {@code cards()}). Plain Swing and JSON: tested headless
 * ({@code SplitViewRegression}).
 */
final class ArchvowItemCards {
    /** The height of a card in the list (three lines and the gap between two cards). */
    static final int HEIGHT = 74;

    private ArchvowItemCards() { }

    /** One item: its number, its question (or text), open or not, and what was recorded. */
    record Card(String number, String title, boolean open, String state, String answer, String by, String note) {
        @Override public String toString() { return number + " · " + title; }
    }

    /** "architect@example.com · 2026-10-08": who recorded the answer, and the day. */
    static String byOn(JsonObject row) {
        String by = ArchvowGrammar.str(row, "by");
        String on = ArchvowGrammar.str(row, "on");
        if (on.length() > 10) on = on.substring(0, 10);
        if (by.isBlank()) return on;
        return on.isBlank() ? by : by + " · " + on;
    }

    static Card decision(JsonObject d, boolean french) {
        String answer = ArchvowGrammar.str(d, "answer");
        String proposed = ArchvowGrammar.str(d, "proposed");
        boolean open = answer.isBlank();
        return new Card(ArchvowGrammar.str(d, "number"), ArchvowGrammar.str(d, "question"), open,
            french ? "Ouvert" : "Open", answer, open ? "" : byOn(d),
            open && !proposed.isBlank() ? (french ? "Proposition IA : " : "AI proposal: ") + proposed : "");
    }

    static Card question(JsonObject q, boolean french) {
        String key = ArchvowGrammar.str(q, "key");
        if (key.isBlank()) key = ArchvowGrammar.str(q, "number");
        String answer = ArchvowGrammar.str(q, "answer");
        boolean open = answer.isBlank();
        return new Card(key, ArchvowGrammar.str(q, "question"), open, french ? "Ouverte" : "Open", answer,
            open ? "" : byOn(q), "");
    }

    static Card safety(JsonObject q, boolean french) {
        boolean open = ArchvowSafetyState.pending(q);
        String recorded = "";
        String dismissed = ArchvowGrammar.str(q, "dismissed");
        List<JsonObject> hazards = ArchvowGrammar.objects(q, "hazards");
        if (!dismissed.isBlank()) recorded = (french ? "Non applicable : " : "Not applicable: ") + dismissed;
        else if (!hazards.isEmpty()) {
            JsonObject h = hazards.get(0);
            recorded = (french ? "Danger " : "Hazard ") + ArchvowGrammar.str(h, "id") + " · "
                + ArchvowGrammar.str(h, "severity") + ": " + ArchvowGrammar.str(h, "description");
        }
        return new Card("S" + ArchvowGrammar.str(q, "number"), ArchvowGrammar.str(q, "question"), open,
            ArchvowSafetyState.status(q), recorded, "", open ? recorded : "");
    }

    static Card assumption(JsonObject a, boolean french) {
        String status = ArchvowGrammar.str(a, "status").toLowerCase(Locale.ROOT);
        boolean accepted = status.equals("accepted");
        boolean open = !accepted && !status.equals("rejected");
        return new Card("A" + ArchvowGrammar.str(a, "number"), ArchvowGrammar.str(a, "text"), open,
            french ? "À examiner" : "To review",
            accepted ? (french ? "Acceptée" : "Accepted") : (french ? "Rejetée" : "Rejected"), open ? "" : byOn(a), "");
    }

    /** The card a view starts on when its component opens: the first open one, else the first; -1 for none. */
    static int firstOpen(List<Card> cards) {
        for (int i = 0; i < cards.size(); i++) if (cards.get(i).open()) return i;
        return cards.isEmpty() ? -1 : 0;
    }

    /** The index of the card numbered {@code number}, else {@code otherwise}. */
    static int indexOf(List<Card> cards, String number, int otherwise) {
        if (number == null || number.isBlank()) return otherwise;
        for (int i = 0; i < cards.size(); i++) {
            String n = cards.get(i).number();
            if (n.equals(number) || n.equals("S" + number) || n.equals("A" + number)) return i;
        }
        return otherwise;
    }

    /** Draws a card: its number and question, then the answer or "● Open", then who and when. */
    static final class Renderer extends JPanel implements ListCellRenderer<Card> {
        private final JLabel title = new JLabel();
        private final JLabel status = new JLabel();
        private final JLabel by = new JLabel();

        Renderer() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            for (JLabel label : List.of(title, status, by)) {
                label.setAlignmentX(LEFT_ALIGNMENT);
                add(label);
                add(Box.createVerticalStrut(2));
            }
            Font font = UIManager.getFont("Label.font");
            if (font != null) {
                title.setFont(font);
                status.setFont(font);
                by.setFont(font.deriveFont(font.getSize2D() - 1.5f));
            }
        }

        @Override public Component getListCellRendererComponent(JList<? extends Card> list, Card card, int index,
                                                                boolean selected, boolean focused) {
            boolean dark = ArchvowMotion.dark();
            Color background = list.getBackground();
            Color amber = dark ? new Color(235, 181, 90) : new Color(176, 118, 26);
            Color green = dark ? new Color(96, 207, 143) : new Color(24, 132, 80);
            Color muted = dark ? new Color(150, 158, 170) : new Color(100, 110, 124);
            int width = Math.max(120, list.getWidth() - 30);
            String number = card == null ? "" : card.number();
            String text = card == null ? "" : card.title();
            title.setText(fit(title, number + " · " + text, width));
            title.setToolTipText(text);
            title.setForeground(selected && !dark ? new Color(23, 51, 93) : list.getForeground());
            if (card != null && card.open()) {
                status.setText(fit(status, "● " + card.state() + (card.note().isBlank() ? "" : " · " + card.note()), width));
                status.setForeground(amber);
            } else {
                status.setText(fit(status, card == null ? "" : card.answer().isBlank() ? card.state() : card.answer(), width));
                status.setForeground(green);
            }
            by.setText(card == null ? "" : card.by());
            by.setForeground(muted);
            float hover = ArchvowMotion.hoverLevel(list, index);
            Color fill = selected ? ArchvowMotion.blend(background, ArchvowMotion.accent(), dark ? .22 : .12)
                : ArchvowMotion.blend(background, ArchvowMotion.accent(), (dark ? .10 : .05) * hover);
            setBackground(list.getBackground());
            Color line = card != null && card.open() ? amber
                : ArchvowMotion.blend(background, list.getForeground(), dark ? .22 : .18);
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(3, 2, 3, 2),
                BorderFactory.createCompoundBorder(new CardBorder(line, fill, selected ? ArchvowMotion.accent() : null),
                    BorderFactory.createEmptyBorder(6, 11, 6, 10))));
            return this;
        }

        /** The text cut with "…" to the width of the card. */
        private static String fit(JLabel label, String text, int width) {
            String full = text == null ? "" : text.replace('\n', ' ').replaceAll("\\s+", " ").trim();
            FontMetrics metrics = label.getFontMetrics(label.getFont());
            if (metrics.stringWidth(full) <= width) return full;
            String cut = full;
            while (cut.length() > 4 && metrics.stringWidth(cut + "…") > width) cut = cut.substring(0, cut.length() - 1);
            return cut.stripTrailing() + "…";
        }
    }

    /** The card's rounded outline and fill; a bar on the left of the selected card. */
    private static final class CardBorder extends javax.swing.border.AbstractBorder {
        private final Color line;
        private final Color fill;
        private final Color selected;

        CardBorder(Color line, Color fill, Color selected) {
            this.line = line;
            this.fill = fill;
            this.selected = selected;
        }

        @Override public void paintBorder(Component c, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                java.awt.geom.RoundRectangle2D shape = new java.awt.geom.RoundRectangle2D.Float(x + .5f, y + .5f, width - 1f, height - 1f, 7, 7);
                // the border is painted after the cell's background and before its labels: the fill goes under the text
                g.setColor(fill);
                g.fill(shape);
                g.setColor(line);
                g.draw(shape);
                if (selected != null) {
                    g.setColor(selected);
                    g.fillRect(x + 1, y + 3, 3, height - 6);
                }
            } finally {
                g.dispose();
            }
        }

        @Override public Insets getBorderInsets(Component c) { return new Insets(1, 4, 1, 1); }
        @Override public boolean isBorderOpaque() { return false; }
    }
}
