package org.archvow.idea;

import javax.swing.*;
import javax.swing.text.View;
import java.awt.*;

/** The check result really uses the Files view's native folder and document drawings. */
public final class HtmlFileIconsRegression {
    private HtmlFileIconsRegression() {}

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JEditorPane pane = new JEditorPane("text/html", "");
            ArchvowHtmlFileIcons.install(pane);
            pane.setText("<html><body><img src='"
                + ArchvowHtmlFileIcons.imageSource(".archvow/spec.vow", 12, false)
                + "'></body></html>");
            pane.setSize(240, 180);
            pane.getPreferredSize();
            pane.doLayout();
            int count = icons(pane.getUI().getRootView(pane));
            if (count != 1) throw new AssertionError("Expected a native Files component, got " + count);
            if (!containsNativeFiles(pane))
                throw new AssertionError("The HTML result did not embed ArchvowFilePlanView");
        });
        System.out.println("OK: check result embeds ArchvowFilePlanView itself");
    }

    private static int icons(View view) {
        int found = view.getClass().getName().contains("ArchvowHtmlFileIcons$InlineFilePlan") ? 1 : 0;
        for (int i = 0; i < view.getViewCount(); i++) found += icons(view.getView(i));
        return found;
    }

    private static boolean containsNativeFiles(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof ArchvowFilePlanView) return true;
            if (child instanceof Container container && containsNativeFiles(container)) return true;
        }
        return false;
    }
}
