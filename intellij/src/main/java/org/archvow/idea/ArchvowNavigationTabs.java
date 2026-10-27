package org.archvow.idea;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Small, reliable editor-style navigation. Buttons explicitly select CardLayout views.
 * Unlike a custom BasicTabbedPaneUI, no paint or tab hit-testing logic handles clicks.
 * The tab strip scrolls horizontally instead of cutting text at narrow widths.
 */
final class ArchvowNavigationTabs extends JPanel {
    private final CardLayout layout = new CardLayout();
    private final JPanel cards = new JPanel(layout);
    private final JPanel strip = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 3));
    private final JScrollPane stripScroll;
    private final List<ArchvowMotion.EditorTabButton> buttons = new ArrayList<>();
    private final List<ChangeListener> listeners = new ArrayList<>();
    private int selected = -1;
    /** The tool window is narrow: tighter buttons, no icons, so the three main tabs fit. */
    private boolean compact;

    ArchvowNavigationTabs() {
        super(new BorderLayout(0, 2));
        strip.setOpaque(false);
        stripScroll = new JScrollPane(strip, JScrollPane.VERTICAL_SCROLLBAR_NEVER,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        stripScroll.setBorder(BorderFactory.createEmptyBorder());
        stripScroll.setOpaque(false);
        stripScroll.getViewport().setOpaque(false);
        stripScroll.setPreferredSize(new Dimension(120, 49));
        stripScroll.getHorizontalScrollBar().setUnitIncrement(32);
        add(stripScroll, BorderLayout.NORTH);
        add(cards, BorderLayout.CENTER);
    }

    void addTab(String title, Component view) {
        int index = buttons.size();
        ArchvowMotion.EditorTabButton button = new ArchvowMotion.EditorTabButton(title);
        ArchvowUiIcons.decorate(button);
        if (compact) compact(button);
        button.addActionListener(e -> setSelectedIndex(index));
        button.setToolTipText(title);
        buttons.add(button);
        strip.add(button);
        cards.add(view, Integer.toString(index));
        if (selected < 0) setSelectedIndex(0);
        revalidate();
        repaint();
    }

    void setSelectedIndex(int index) {
        if (index < 0 || index >= buttons.size()) return;
        boolean changed = selected != index;
        selected = index;
        layout.show(cards, Integer.toString(index));
        for (int i = 0; i < buttons.size(); i++) {
            ArchvowMotion.EditorTabButton button = buttons.get(i);
            button.setSelected(i == index);
            ArchvowMotion.updateViewButton(button);
        }
        if (changed) {
            ChangeEvent event = new ChangeEvent(this);
            for (ChangeListener listener : new ArrayList<>(listeners)) listener.stateChanged(event);
        }
        // Keep the active tab visible even after a programmatic jump from a pending badge.
        if (buttons.get(index).isShowing()) {
            AbstractButton active = buttons.get(index);
            active.scrollRectToVisible(new Rectangle(0, 0, active.getWidth(), active.getHeight()));
        }
        cards.revalidate();
        cards.repaint();
    }

    int getSelectedIndex() { return selected; }

    /** Tighter buttons without icons (the narrow tool window). */
    void setCompact(boolean value) {
        compact = value;
        ((FlowLayout) strip.getLayout()).setHgap(value ? 1 : 4);
        for (ArchvowMotion.EditorTabButton button : buttons) {
            if (value) compact(button); else ArchvowUiIcons.decorate(button);
        }
        strip.revalidate();
    }

    private static void compact(AbstractButton button) {
        button.setIcon(null);
        button.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
    }

    /** The editor tab hides the strip: the tool window chooses what it shows. */
    void setStripVisible(boolean visible) {
        stripScroll.setVisible(visible);
        revalidate();
    }

    void setTitleAt(int index, String title) {
        if (index < 0 || index >= buttons.size()) return;
        buttons.get(index).setText(title);
        ArchvowUiIcons.decorate(buttons.get(index));
        if (compact) compact(buttons.get(index));
        buttons.get(index).setToolTipText(title);
        strip.revalidate();
        strip.repaint();
    }

    void addChangeListener(ChangeListener listener) { listeners.add(listener); }

    // Source compatibility: the new strip always scrolls; no policy is needed.
    void setTabLayoutPolicy(int ignored) { }

    // Exposed for regression tests and keyboard accessibility checks.
    AbstractButton getNavigationButton(int index) { return buttons.get(index); }
}
