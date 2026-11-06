package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Tests natural-fit navigation: no fixed breakpoints and no redundant More menu. */
public final class ResponsiveOverflowRegression {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (Color theme : List.of(new Color(246, 247, 250), new Color(34, 37, 43))) {
                UIManager.put("Panel.background", theme);
                List<String> invoked = new ArrayList<>();
                ArchvowOverflowBar actions = new ArchvowOverflowBar("More ▾");
                for (int i = 0; i < 8; i++) {
                    final int number = i;
                    JButton button = new JButton("Action " + i);
                    button.addActionListener(e -> invoked.add("action-" + number));
                    actions.addAction(button);
                }
                actions.setSize(1500, 40);
                actions.refresh();
                if (actions.hasOverflow() || actions.visibleActionCount() != 8 || actions.getComponentCount() != 8)
                    throw new AssertionError("Large action bar must show every action and no More button");
                actions.setSize(240, 40);
                actions.refresh();
                int shown = actions.visibleActionCount();
                if (shown >= 8 || shown < 1 || !actions.hasOverflow())
                    throw new AssertionError("Narrow action bar must show natural-fit actions + More");
                JPopupMenu hidden = actions.menuForTest();
                if (hidden.getComponentCount() != 8 - shown)
                    throw new AssertionError("Only overflowing actions belong in More");
                ((JMenuItem) hidden.getComponent(0)).doClick();
                if (!invoked.contains("action-" + shown))
                    throw new AssertionError("Overflow menu did not execute original action");
                actions.setSize(1500, 40);
                actions.refresh();
                if (actions.hasOverflow()) throw new AssertionError("More menu must disappear when wide again");

                // The real Overview contains nine equal-size icon commands: the More
                // control must appear at exactly the first pixel where the icons stop fitting.
                ArchvowOverflowBar project = new ArchvowOverflowBar("More");
                project.setSeparated(true);
                project.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
                for (int i = 0; i < 9; i++) {
                    JButton icon = new JButton("");
                    icon.setPreferredSize(new Dimension(37, 31));
                    project.addAction(icon);
                }
                project.setSize(439, 38); // 8 px inset + 431 px actual FlowLayout content
                project.refresh();
                if (project.hasOverflow() || project.visibleActionCount() != 9)
                    throw new AssertionError("All nine icons fit exactly: no redundant More");
                project.setSize(438, 38);
                project.refresh();
                if (!project.hasOverflow() || project.visibleActionCount() != 8)
                    throw new AssertionError("More must appear only after the last icon stops fitting");
                project.setSize(600, 38);
                project.refresh();
                if (project.hasOverflow() || project.getComponentCount() != 17)
                    throw new AssertionError("Width restored: nine icon buttons and eight dividers, no More");

                // Overview uses a permanently available compact More control. It contains both
                // actions displaced by the available width and the advanced project actions.
                ArchvowOverflowBar overview = new ArchvowOverflowBar("More");
                overview.setAlwaysShowMore(true);
                List<String> commands = new ArrayList<>();
                for (int i = 0; i < 6; i++) {
                    final int index = i;
                    JButton button = new JButton("");
                    button.getAccessibleContext().setAccessibleName("Project " + i);
                    button.setPreferredSize(new Dimension(37, 31));
                    button.addActionListener(e -> commands.add("action-" + index));
                    overview.addAction(button);
                }
                overview.addMenuAction("Budget / Next", () -> commands.add("budget"));
                overview.setSize(900, 36);
                overview.refresh();
                if (overview.visibleActionCount() != 6 || overview.menuForTest().getComponentCount() != 1)
                    throw new AssertionError("Wide Overview: all icons and only advanced More actions");
                ((JMenuItem) overview.menuForTest().getComponent(0)).doClick();
                if (!commands.contains("budget")) throw new AssertionError("Advanced action must execute");
                overview.setSize(160, 36);
                overview.refresh();
                int kept = overview.visibleActionCount();
                if (kept < 1 || kept >= 6) throw new AssertionError("Narrow Overview must overflow");
                JPopupMenu projectMore = overview.menuForTest();
                if (projectMore.getComponentCount() != 6 - kept + 2)
                    throw new AssertionError("More must contain hidden actions, a separator and advanced commands");
                ((JMenuItem) projectMore.getComponent(0)).doClick();
                if (!commands.contains("action-" + kept))
                    throw new AssertionError("Hidden Overview action must execute");

                List<Integer> selected = new ArrayList<>();
                ArchvowCompactViews views = new ArchvowCompactViews(selected::add);
                for (int i = 0; i < 7; i++) {
                    final int idx = i;
                    JToggleButton button = new ArchvowMotion.EditorTabButton("Section " + i);
                    button.addActionListener(e -> { selected.add(idx); views.setActive(idx); });
                    views.addView(button);
                }
                views.setSize(1800, 36);
                views.refresh();
                if (views.visiblePrimaryCount() != 7 || views.hasOverflow() || views.getComponentCount() != 7)
                    throw new AssertionError("Large view navigation must show every section without More");
                views.setSize(250, 36);
                views.refresh();
                if (!views.hasOverflow() || views.visiblePrimaryCount() >= 7)
                    throw new AssertionError("Narrow view navigation should show More");
                views.setActive(6);
                if (views.activeIndex() != 6) throw new AssertionError("Selection lost when view is hidden in More");
                views.setSize(1800, 36);
                views.refresh();
                if (views.hasOverflow() || views.visiblePrimaryCount() != 7)
                    throw new AssertionError("View overflow must disappear when resized wide");
            }
        });
        System.out.println("OK: measured-width overflow for actions and vows in light/dark themes");
    }
}
