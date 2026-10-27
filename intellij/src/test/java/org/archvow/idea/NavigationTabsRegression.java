package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Regressions for the formerly non-clickable main, overview and pending tabs. */
public final class NavigationTabsRegression {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
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
                    tabs.setSize(240, 420); // narrow sidebar must scroll instead of destroying buttons
                    tabs.doLayout();
                    tabs.getNavigationButton(2).doClick();
                    if (tabs.getSelectedIndex() != 2) throw new AssertionError("Narrow window navigation failed");
                }
            }
        });
        System.out.println("OK: three navigation levels are clickable, even in narrow/dark mode");
    }
}
