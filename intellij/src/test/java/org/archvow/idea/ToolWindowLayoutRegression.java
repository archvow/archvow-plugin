package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 0.7.0: the Archvow tool window (left) chooses, ONE editor tab shows. Checks the declarations and the
 * tab strip of both roles without the IntelliJ SDK (the live run is the harness in the CHANGELOG notes).
 */
public final class ToolWindowLayoutRegression {
    public static void main(String[] args) throws Exception {
        String xml;
        try (InputStream in = ToolWindowLayoutRegression.class.getResourceAsStream("/META-INF/plugin.xml")) {
            if (in == null) throw new AssertionError("plugin.xml not on the classpath");
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        must(xml.contains("<toolWindow id=\"Archvow\" anchor=\"left\""), "the tool window is on the left");
        must(xml.contains("<editorTabTitleProvider implementation=\"org.archvow.idea.ArchvowEditorTabPresentation\"/>"), "the tab's title");
        must(xml.contains("<fileIconProvider implementation=\"org.archvow.idea.ArchvowEditorTabPresentation\"/>"), "the tab's icon");
        must(xml.contains("<fileEditorProvider implementation=\"org.archvow.idea.ArchvowEditorProvider\"/>"), "the one editor tab");
        must(!xml.contains("ToolWindowVisibilityListener") && !xml.contains("OpenArchvowEditorAction"), "no sidebar/editor switch left");
        must(xml.contains("org.archvow.idea.ShowArchvowAction"), "Tools > Show Archvow");

        SwingUtilities.invokeAndWait(() -> {
            // the tool window: three compact main tabs that fit a narrow window, even in French
            ArchvowNavigationTabs navigation = new ArchvowNavigationTabs();
            navigation.addTab("Vue d'ensemble", new JPanel());
            navigation.addTab("Composants", new JPanel());
            navigation.addTab("Commandes", new JPanel());
            navigation.setCompact(true);
            int wanted = 0;
            for (int i = 0; i < 3; i++) {
                AbstractButton b = navigation.getNavigationButton(i);
                must(b.getIcon() == null, "no icon on a compact main tab");
                wanted += b.getPreferredSize().width + 1;
            }
            must(wanted <= 340, "the three main tabs fit 340 px: " + wanted);
            navigation.setTitleAt(0, "Overview");
            must(navigation.getNavigationButton(0).getIcon() == null, "still compact after a language change");
            // the editor tab: no strip, its cards still switch
            ArchvowNavigationTabs detail = new ArchvowNavigationTabs();
            JLabel result = new JLabel("result"), component = new JLabel("component"), command = new JLabel("command");
            detail.addTab("Result", result);
            detail.addTab("Components", component);
            detail.addTab("Commands", command);
            detail.setStripVisible(false);
            detail.setSize(800, 600);
            detail.doLayout();
            for (Component child : detail.getComponents()) {
                if (child instanceof JScrollPane strip) must(!strip.isVisible(), "the editor tab has no strip");
            }
            detail.setSelectedIndex(2);
            must(command.isVisible() && !component.isVisible(), "the command card is shown");
            detail.setSelectedIndex(1);
            must(component.isVisible() && !command.isVisible(), "the component card is shown");
        });
        System.out.println("OK: tool window on the left, one editor tab (title, icon), compact strip");
    }

    private static void must(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
