package org.archvow.vow.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The words of a vow that name something else, so an editor can jump to it: a component named in
 * {@code uses} or {@code components}, a hazard named in {@code prevents}, the Python module of
 * {@code module}.
 */
public final class VowLinks {
    private VowLinks() {
    }

    public enum Kind {
        /** Another component: its {@code components/<name>/spec.vow}. */
        COMPONENT,
        /** A hazard: {@code H1} in the same vow, {@code clean_orders.H1} in that component's vow. */
        HAZARD,
        /** A Python module: {@code acme.p1_domain.clean_orders} -> {@code src/acme/p1_domain/clean_orders.py}. */
        MODULE
    }

    /** One link: what it names, and its offsets in the file. */
    public record Link(Kind kind, String target, int start, int end) {
    }

    /** The links of one line of a vow. */
    public static List<Link> of(VowDocument.Line line) {
        List<Link> links = new ArrayList<>();
        if (line.key == null || line.value == null) {
            return links;
        }
        Kind kind = switch (line.key) {
            case "uses", "components" -> Kind.COMPONENT;
            case "prevents" -> Kind.HAZARD;
            case "module" -> Kind.MODULE;
            default -> null;
        };
        if (kind == null) {
            return links;
        }
        if (kind == Kind.MODULE) {
            String word = line.word();
            if (word != null) {
                links.add(new Link(kind, word, line.valueStart, line.valueStart + word.length()));
            }
            return links;
        }
        for (VowValues.Item item : VowValues.items(line.value)) {
            String text = VowValues.unquote(item.text());
            links.add(new Link(kind, text, line.valueStart + item.start(), line.valueStart + item.end()));
        }
        return links;
    }

    /** The file a module lives in, relative to the project root (the folder holding .archvow/). */
    public static List<String> moduleFiles(String module) {
        String path = module.replace('.', '/');
        return List.of("src/" + path + ".py", path + ".py", "src/" + path + "/__init__.py", path + "/__init__.py");
    }

    /** For {@code clean_orders.H1}: {@code clean_orders}; for {@code H1}: null (the same vow). */
    public static String hazardComponent(String id) {
        int dot = id.lastIndexOf('.');
        return dot < 0 ? null : id.substring(0, dot);
    }

    /** For {@code clean_orders.H1} or {@code H1}: {@code H1}. */
    public static String hazardShortId(String id) {
        return id.substring(id.lastIndexOf('.') + 1);
    }
}
