package org.archvow.idea;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.swing.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.io.ByteArrayInputStream;
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
        // read as XML, not as text: the build rewrites plugin.xml (patchPluginXml: version, since-build) and
        // writes it back in its own layout (`<a b="c" />`), so the text differs from the source file
        Document plugin = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        must(declared(plugin, "toolWindow", "id", "Archvow", "anchor", "left"), "the tool window is on the left");
        must(declared(plugin, "editorTabTitleProvider", "implementation", "org.archvow.idea.ArchvowEditorTabPresentation"), "the tab's title");
        must(declared(plugin, "fileIconProvider", "implementation", "org.archvow.idea.ArchvowEditorTabPresentation"), "the tab's icon");
        must(declared(plugin, "fileEditorProvider", "implementation", "org.archvow.idea.ArchvowEditorProvider"), "the one editor tab");
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

    /** Whether plugin.xml has a {@code <tag>} with these attribute values (name, value, name, value...). */
    private static boolean declared(Document plugin, String tag, String... attributes) {
        NodeList found = plugin.getElementsByTagName(tag);
        for (int i = 0; i < found.getLength(); i++) {
            Element element = (Element) found.item(i);
            boolean all = true;
            for (int a = 0; a < attributes.length; a += 2) all &= attributes[a + 1].equals(element.getAttribute(attributes[a]));
            if (all) return true;
        }
        return false;
    }

    private static void must(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
