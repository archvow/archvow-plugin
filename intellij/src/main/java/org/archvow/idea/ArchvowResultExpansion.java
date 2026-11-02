package org.archvow.idea;

import javax.swing.*;
import java.awt.*;

/**
 * Temporarily displays an inline result across the entire editor workspace.
 *
 * Increasing a scroll pane's preferred height inside a BorderLayout.SOUTH pushes
 * the command toolbar above the visible viewport. Inside a split view it can
 * never become wider than the answer column. Instead move the SAME result card
 * into a full-workspace card, then restore its exact original position on Reduce.
 * The result's editor, scroll position and buttons are never reconstructed.
 */
final class ArchvowResultExpansion {
    private static final String EXPANDED = "expanded-result";
    private final JPanel workspaceCards;
    private final CardLayout cards;
    private final String normalCard;
    private final JPanel expanded = new JPanel(new BorderLayout());

    private JComponent result;
    private Container originalParent;
    private Component placeholder;
    private Object constraint;
    private int index;

    ArchvowResultExpansion(JPanel workspaceCards, CardLayout cards, String normalCard) {
        this.workspaceCards = workspaceCards;
        this.cards = cards;
        this.normalCard = normalCard;
        expanded.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        workspaceCards.add(expanded, EXPANDED);
    }

    boolean isExpanded() { return result != null; }

    /** Shows the card at full available width and height, without creating another result. */
    boolean expand(JComponent card) {
        if (card == null || card.getParent() == null) return false;
        if (result == card) return true;
        if (result != null) collapse();

        originalParent = card.getParent();
        index = originalParent.getComponentZOrder(card);
        constraint = originalParent.getLayout() instanceof BorderLayout border ? border.getConstraints(card) : null;
        placeholder = new JPanel();
        ((JComponent) placeholder).setOpaque(false);
        placeholder.setVisible(false);
        originalParent.remove(card);
        if (constraint == null) originalParent.add(placeholder, index);
        else originalParent.add(placeholder, constraint, index);

        result = card;
        expanded.add(card, BorderLayout.CENTER);
        card.setVisible(true);
        originalParent.revalidate();
        cards.show(workspaceCards, EXPANDED);
        workspaceCards.revalidate();
        workspaceCards.repaint();
        return true;
    }

    /** Restores the card to its original split/command panel (including its original layout slot). */
    JComponent collapse() {
        if (result == null) return null;
        JComponent restored = result;
        expanded.remove(restored);
        originalParent.remove(placeholder);
        if (constraint == null) originalParent.add(restored, index);
        else originalParent.add(restored, constraint, index);
        restored.setVisible(true);
        originalParent.revalidate();
        originalParent.repaint();
        result = null;
        originalParent = null;
        placeholder = null;
        constraint = null;
        cards.show(workspaceCards, normalCard);
        workspaceCards.revalidate();
        workspaceCards.repaint();
        return restored;
    }
}
