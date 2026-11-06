package org.archvow.idea;

import javax.swing.*;
import javax.swing.text.*;
import javax.swing.text.html.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * The result HTML embeds the real native ArchvowFilePlanView instead of
 * approximating its folder/document icons with Unicode or HTML styling.
 * A safe, encoded virtual image address carries only the file's display data;
 * no external URL or file is read.
 */
final class ArchvowHtmlFileIcons {
    private static final String PREFIX = "archvow-files:";
    private ArchvowHtmlFileIcons() {}

    static void install(JEditorPane pane) {
        pane.setEditorKit(new HTMLEditorKit() {
            private final ViewFactory factory = new HTMLFactory() {
                @Override public View create(Element element) {
                    AttributeSet attrs = element.getAttributes();
                    if (attrs.getAttribute(StyleConstants.NameAttribute) == HTML.Tag.IMG) {
                        Object src = attrs.getAttribute(HTML.Attribute.SRC);
                        if (src instanceof String value && value.startsWith(PREFIX))
                            return new InlineFilePlan(element, value.substring(PREFIX.length()));
                    }
                    return super.create(element);
                }
            };
            @Override public ViewFactory getViewFactory() { return factory; }
        });
    }

    static String imageSource(String file, int line, boolean fr) {
        String path = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(file.getBytes(StandardCharsets.UTF_8));
        return PREFIX + path + ":" + Math.max(0, line) + ":" + (fr ? "1" : "0");
    }

    private static final class InlineFilePlan extends ComponentView {
        private final String encoded;
        InlineFilePlan(Element element, String encoded) { super(element); this.encoded = encoded; }

        @Override protected Component createComponent() {
            ArchvowFilePlanView files = new ArchvowFilePlanView();
            String[] parts = encoded.split(":", 3);
            try {
                if (parts.length == 3) {
                    String path = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
                    int line = Integer.parseInt(parts[1]);
                    files.showLocation(path, line, "1".equals(parts[2]));
                }
            } catch (IllegalArgumentException ignored) {
                // An invalid address does not trigger any I/O or show guessed data.
            }
            return files;
        }
    }
}
