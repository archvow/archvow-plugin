package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Tests non-scrolling compact navigation, selection and light/dark themes. */
public final class CompactViewsRegression {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (Color surface : List.of(new Color(248, 249, 251), new Color(36, 39, 44))) {
                UIManager.put("Panel.background", surface);
                List<Integer> clicked = new ArrayList<>();
                ArchvowCompactViews nav = new ArchvowCompactViews(clicked::add);
                for (int i = 0; i < 7; i++) {
                    final int index = i;
                    JToggleButton view = new ArchvowMotion.EditorTabButton("View " + i);
                    view.addActionListener(e -> { clicked.add(index); nav.setActive(index); });
                    nav.addView(view);
                }
                for (int cap : new int[]{1, 2, 3, 4}) {
                    nav.forcePrimaryLimitForTest(cap);
                    if (nav.visiblePrimaryCount() != cap) throw new AssertionError("wrong limit " + cap);
                    if (nav.hasHorizontalScroller()) throw new AssertionError("must not scroll");
                    for (int i = 0; i < 7; i++) {
                        nav.setActive(i);
                        if (nav.activeIndex() != i) throw new AssertionError("lost view " + i);
                        if (nav.getComponentCount() != cap+1) throw new AssertionError("wrong row width");
                    }
                    // Verify popup entries dispatch actual menu clicks too.
                    try {
                        var buildMenu = ArchvowCompactViews.class.getDeclaredMethod("populateMenu");
                        buildMenu.setAccessible(true);
                        buildMenu.invoke(nav);
                        var menuField = ArchvowCompactViews.class.getDeclaredField("menu");
                        menuField.setAccessible(true);
                        JPopupMenu popup = (JPopupMenu) menuField.get(nav);
                        if (popup.getComponentCount() != 7 - cap)
                            throw new AssertionError("wrong number of popup views");
                        for (Component component : popup.getComponents()) {
                            JMenuItem item = (JMenuItem) component;
                            int before = clicked.size();
                            item.doClick();
                            if (clicked.size() != before + 1)
                                throw new AssertionError("menu entry did not navigate");
                        }
                    } catch (ReflectiveOperationException err) {
                        throw new RuntimeException(err);
                    }
                    // The primary view must still accept actual mouse actions.
                    JToggleButton first = (JToggleButton) nav.getComponent(0);
                    first.doClick();
                    if (clicked.isEmpty() || clicked.get(clicked.size()-1) != 0)
                        throw new AssertionError("click did not dispatch");
                }
            }
        });
        System.out.println("OK: 7 views, compact navigation and dark/light themes");
    }
}
