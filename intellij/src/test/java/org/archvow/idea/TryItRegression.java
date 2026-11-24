package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * "Try it" shows its result (0.26.1). Before: the welcome page was a GridBagLayout panel that did not scroll. Once
 * Try it's 260-pixel result area joined the cards, the page no longer fit a tool window, GridBagLayout gave each part
 * its minimum size, and the result area - a scroll pane - shrank to its scroll bar: nothing seemed to happen.
 * Laid out here as ArchvowPanel.buildWelcomeView() builds it, in a short tool window.
 */
public final class TryItRegression {
    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    private static JComponent block(int height) {
        JPanel part = new JPanel();
        part.setPreferredSize(new Dimension(380, height));
        part.setAlignmentX(0f);
        return part;
    }

    /** The welcome page's parts, with the result area as the panel sets it, laid out in a tool window. */
    private static int resultHeight(boolean scrolls, int width, int height) {
        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        for (int part : new int[] {24, 230, 130}) column.add(block(part));  // title, status card, Try card
        JEditorPane result = new JEditorPane("text/html", "<html><body>" + "<p>a finding</p>".repeat(30) + "</body></html>");
        JScrollPane area = new JScrollPane(result);
        area.setPreferredSize(new Dimension(420, 260));
        area.setAlignmentX(0f);
        if (scrolls) area.setMinimumSize(new Dimension(0, 200));
        column.add(area);
        column.add(block(36));  // the foot line
        JPanel container;
        if (scrolls) {
            container = new ArchvowPanel.WelcomePage();
            container.add(column, BorderLayout.NORTH);
        } else {  // 0.26.0's welcome page
            container = new JPanel(new GridBagLayout());
            GridBagConstraints gc = new GridBagConstraints();
            gc.weightx = 1;
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.insets = new Insets(15, 15, 15, 15);
            container.add(column, gc);
        }
        JComponent window = scrolls ? new JScrollPane(container, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER) : container;
        window.setSize(width, height);
        layOut(window);
        return area.getHeight();
    }

    private static void layOut(Component component) {
        component.doLayout();
        if (component instanceof Container container) for (Component child : container.getComponents()) layOut(child);
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        int[][] windows = {{360, 900}, {420, 520}, {700, 520}};  // a narrow sidebar, a short one, a wide editor
        int before = resultHeight(false, 360, 900);
        check(before < 60, "the layout of 0.26.0 no longer collapses: the test no longer shows the bug (" + before + " px)");
        int now = Integer.MAX_VALUE;
        for (int[] window : windows) {
            int height = resultHeight(true, window[0], window[1]);
            check(height == 260, "Try it's result area is " + height + " px high in a " + window[0] + " x " + window[1]
                + " tool window, not the 260 px it asks for");
            now = Math.min(now, height);
        }
        Path sources = Path.of(System.getProperty("archvow.sources", "src/main/java/org/archvow/idea"));
        if (Files.isDirectory(sources)) {
            String panel = Files.readString(sources.resolve("ArchvowPanel.java"));
            check(panel.contains("JPanel container = new WelcomePage();"), "the welcome page is not the scrollable WelcomePage");
            check(panel.contains("welcomeResultScroll.setMinimumSize("), "the result area has no minimum height");
            int run = panel.indexOf("private void runTry(");
            check(run > 0 && panel.indexOf("revealWelcomeResult();", run) > run, "Try it's result is not brought into view");
        }
        System.out.println("OK: Try it's result area " + now + " px in a narrow, a short and a wide tool window (0.26.0: " + before + " px)");
    }
}
