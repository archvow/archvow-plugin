package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Regressions for the formerly non-clickable main, overview and pending tabs. */
public final class NavigationTabsRegression {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            // Overview titles may refresh while the navigation strip has 0 or 1 tabs.
            // setTitleAt, not JTabbedPane.getTabCount(), owns the range check.
            ArchvowNavigationTabs incomplete = new ArchvowNavigationTabs();
            incomplete.setTitleAt(0, "Architecture");
            incomplete.setTitleAt(1, "Check & Gates");
            incomplete.addTab("Architecture", new JPanel());
            incomplete.setTitleAt(0, "Architecture · 4");
            incomplete.setTitleAt(1, "Check & Gates · 3");
            if (!"Architecture · 4".equals(incomplete.getNavigationButton(0).getText()))
                throw new AssertionError("Early overview title refresh changed the existing tab");
            for (Color theme : List.of(new Color(247, 248, 252), new Color(32, 34, 39))) {
                UIManager.put("Panel.background", theme);
                UIManager.put("Label.foreground", theme.getRed() > 120 ? Color.BLACK : Color.WHITE);
                for (int layer = 0; layer < 3; layer++) {
                    ArchvowNavigationTabs tabs = new ArchvowNavigationTabs();
                    AtomicInteger changes = new AtomicInteger();
                    tabs.addChangeListener(e -> changes.incrementAndGet());
                    JLabel[] views = new JLabel[3];
                    String[] titles = {"Overview", "Components", "Commands"};
                    for (int i = 0; i < 3; i++) {
                        views[i] = new JLabel("View " + i);
                        tabs.addTab(titles[i], views[i]);
                    }
                    for (int target : new int[] {1, 2, 0, 2, 1}) {
                        tabs.getNavigationButton(target).doClick(); // actual user action, not programmatic selection
                        if (tabs.getSelectedIndex() != target) throw new AssertionError("Not selected: " + target);
                        for (int i = 0; i < 3; i++) {
                            if (views[i].isVisible() != (target == i))
                                throw new AssertionError("Wrong visible view in layer=" + layer + ", view=" + i);
                            if (tabs.getNavigationButton(i).isSelected() != (target == i))
                                throw new AssertionError("Wrong button selection: " + i);
                        }
                    }
                    if (changes.get() != 6) throw new AssertionError("Unexpected change event: " + changes.get());
                    tabs.setTitleAt(2, "Quick treatment (123)");
                    if (!tabs.getNavigationButton(2).getText().equals("Quick treatment (123)"))
                        throw new AssertionError("Title update missing");
                    tabs.setSize(240, 420);
                    tabs.doLayout();
                    tabs.getNavigationButton(2).doClick();
                    if (tabs.getSelectedIndex() != 2) throw new AssertionError("Narrow window navigation failed");
                    assertVisibleAndWrapped(tabs, 240);
                    // The sidebar instead wraps all three buttons (French included), without a
                    // horizontal scrollbar covering the tabs at 300px like in the screenshot.
                    ArchvowNavigationTabs sidebar = new ArchvowNavigationTabs();
                    sidebar.addTab("Vue d'ensemble", new JPanel());
                    sidebar.addTab("Composants", new JPanel());
                    sidebar.addTab("Commandes", new JPanel());
                    sidebar.setCompact(true);
                    sidebar.setSize(275, 500);
                    sidebar.doLayout();
                    Container header = (Container) sidebar.getComponent(0);
                    if (!(header.getComponent(0) instanceof JPanel tabStrip))
                        throw new AssertionError("Main tabs must wrap without a horizontal scroller");
                    assertVisibleAndWrapped(sidebar, 275);
                    sidebar.getNavigationButton(2).doClick();
                    if (sidebar.getSelectedIndex() != 2)
                        throw new AssertionError("Wrapped Commands tab is not clickable");
                    sidebar.setSize(750, 500);
                    sidebar.doLayout();
                    header.doLayout();
                    tabStrip.doLayout();
                    if (sidebar.getNavigationButton(0).getY() != sidebar.getNavigationButton(2).getY())
                        throw new AssertionError("Tabs did not return to one row after widening");
                    sidebar.setCompact(false);
                    assertVisibleAndWrapped(sidebar, 275);
                    sidebar.setCompact(true);
                    assertVisibleAndWrapped(sidebar, 275);

                    // The former Architecture / Check & Gates / Briefs strip had a
                    // horizontal scrollbar: the last button was cut off in the sidebar.
                    ArchvowNavigationTabs overview = new ArchvowNavigationTabs();
                    overview.addTab("Architecture · 0", new JPanel());
                    overview.addTab("Check & Gates · 1", new JPanel());
                    overview.addTab("Briefs · 2", new JPanel());
                    for (int width : new int[] {240, 275, 340, 420, 750}) {
                        assertVisibleAndWrapped(overview, width);
                        overview.getNavigationButton(2).doClick();
                        if (overview.getSelectedIndex() != 2)
                            throw new AssertionError("Wrapped Briefs button stopped responding");
                    }
                }
            }
        });
        System.out.println("OK: all navigation rows wrap and remain clickable, even in narrow/dark mode");
    }

    private static void assertVisibleAndWrapped(ArchvowNavigationTabs tabs, int width) {
        tabs.setSize(width, 500);
        tabs.doLayout();
        Container header = (Container) tabs.getComponent(0);
        header.doLayout();
        if (!(header.getComponent(0) instanceof JPanel strip))
            throw new AssertionError("Unexpected scrolling tab header at " + width + " px");
        strip.doLayout();
        int maximumBottom = 0;
        int minimumTop = Integer.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            AbstractButton button = tabs.getNavigationButton(i);
            Rectangle r = SwingUtilities.convertRectangle(button.getParent(), button.getBounds(), tabs);
            if (r.width < button.getPreferredSize().width || r.height <= 0 || r.x < 0 || r.y < 0
                || r.x + r.width > tabs.getWidth())
                throw new AssertionError("Tab hidden or clipped at " + width + " px: " + i + " " + r);
            maximumBottom = Math.max(maximumBottom, button.getY() + button.getHeight());
            minimumTop = Math.min(minimumTop, button.getY());
        }
        if (header.getHeight() < maximumBottom)
            throw new AssertionError("Tab header cuts off wrapped row at " + width + " px");
        if (width <= 275 && maximumBottom - minimumTop <= tabs.getNavigationButton(0).getHeight())
            throw new AssertionError("Narrow tab strip failed to wrap at " + width + " px");
    }
}
