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
 * Compact tool-window tabs wrap to a second row at narrow widths, like VS Code.
 * Other tab strips keep horizontal scrolling for longer labels.
 */
final class ArchvowNavigationTabs extends JPanel {
    private final CardLayout layout = new CardLayout();
    private final JPanel cards = new JPanel(layout);
    private final JPanel strip = new ResponsiveStrip();
    private final JPanel stripHost = new JPanel(new BorderLayout());
    private final JScrollPane stripScroll;
    private final List<ArchvowMotion.EditorTabButton> buttons = new ArrayList<>();
    private final List<ChangeListener> listeners = new ArrayList<>();
    private int selected = -1;
    /** The tool window is narrow: tighter buttons, no icons, so the three main tabs fit. */
    private boolean compact;

    ArchvowNavigationTabs() {
        super(new BorderLayout(0, 2));
        strip.setOpaque(false);
        stripHost.setOpaque(false);
        stripScroll = new JScrollPane(strip, JScrollPane.VERTICAL_SCROLLBAR_NEVER,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        stripScroll.setBorder(BorderFactory.createEmptyBorder());
        stripScroll.setOpaque(false);
        stripScroll.getViewport().setOpaque(false);
        stripScroll.setPreferredSize(new Dimension(120, 49));
        stripScroll.getHorizontalScrollBar().setUnitIncrement(32);
        stripHost.add(stripScroll, BorderLayout.CENTER);
        add(stripHost, BorderLayout.NORTH);
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

    /** Tighter buttons without icons. In compact mode all tabs wrap, never hide behind a scrollbar. */
    void setCompact(boolean value) {
        if (compact != value) {
            // One strip, two layouts: directly in the host for responsive wrapping in the
            // tool window, inside the horizontal scroller elsewhere. Nothing is rebuilt.
            if (value) {
                stripScroll.setViewportView(null);
                stripHost.remove(stripScroll);
                stripHost.add(strip, BorderLayout.CENTER);
            } else {
                stripHost.remove(strip);
                stripScroll.setViewportView(strip);
                stripHost.add(stripScroll, BorderLayout.CENTER);
            }
        }
        compact = value;
        ((FlowLayout) strip.getLayout()).setHgap(value ? 1 : 4);
        for (ArchvowMotion.EditorTabButton button : buttons) {
            if (value) compact(button); else ArchvowUiIcons.decorate(button);
        }
        strip.revalidate();
        stripHost.revalidate();
        revalidate();
        repaint();
    }

    private static void compact(AbstractButton button) {
        button.setIcon(null);
        button.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
    }

    /** The editor tab hides the strip: the tool window chooses what it shows. */
    void setStripVisible(boolean visible) {
        stripHost.setVisible(visible);
        revalidate();
    }

    void setTitleAt(int index, String title) {
        if (index < 0 || index >= buttons.size()) return;
        buttons.get(index).setText(title);
        ArchvowUiIcons.decorate(buttons.get(index));
        if (compact) compact(buttons.get(index));
        buttons.get(index).setToolTipText(title);
        strip.revalidate();
        stripHost.revalidate();
        revalidate();
        strip.repaint();
    }

    void addChangeListener(ChangeListener listener) { listeners.add(listener); }

    // Source compatibility: the compact strip wraps; the full strip scrolls.
    void setTabLayoutPolicy(int ignored) { }

    // Exposed for regression tests and keyboard accessibility checks.
    AbstractButton getNavigationButton(int index) { return buttons.get(index); }

    /** Preferred height follows the actual tool-window width, not an assumed one-line strip. */
    private final class ResponsiveStrip extends JPanel {
        ResponsiveStrip() { super(new FlowLayout(FlowLayout.LEFT, 4, 3)); }

        @Override public Dimension getPreferredSize() {
            Dimension singleLine = super.getPreferredSize();
            if (!compact) return singleLine;
            Insets insets = getInsets();
            FlowLayout flow = (FlowLayout) getLayout();
            int hgap = flow.getHgap(), vgap = flow.getVgap();
            // The outer container knows its width before BorderLayout lays out this strip.
            int width = ArchvowNavigationTabs.this.getWidth();
            if (width <= 0) return singleLine;
            int available = Math.max(1, width - insets.left - insets.right - 2 * hgap);
            int used = 0, rowHeight = 0, height = insets.top + insets.bottom + 2 * vgap;
            for (Component item : getComponents()) {
                if (!item.isVisible()) continue;
                Dimension pref = item.getPreferredSize();
                if (used > 0 && used + hgap + pref.width > available) {
                    height += rowHeight + vgap;
                    used = 0;
                    rowHeight = 0;
                }
                used += (used == 0 ? 0 : hgap) + pref.width;
                rowHeight = Math.max(rowHeight, pref.height);
            }
            return new Dimension(Math.min(singleLine.width, width), height + rowHeight);
        }
    }
}
