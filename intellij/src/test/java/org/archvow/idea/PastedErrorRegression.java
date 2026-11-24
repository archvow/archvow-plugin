package org.archvow.idea;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A pasted error in the find field (archvow 2.7.0, contract 2.5): archvow show "<the error>" --json is shown as an
 * error - what went wrong, the template's known errors it matches with their fix and why, where it broke - not as a
 * search found nowhere, nor as the component alone (the known errors would be lost).
 */
public final class PastedErrorRegression {
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    private static <T extends java.awt.Component> List<T> all(java.awt.Container root, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (java.awt.Component c : root.getComponents()) {
            if (type.isInstance(c)) out.add(type.cast(c));
            if (c instanceof java.awt.Container inner) out.addAll(all(inner, type));
        }
        return out;
    }

    public static void main(String[] args) throws Exception {
        // the real output of archvow 2.7.0 (contract/cli/python-app/pasted-error.json): no known error, a file
        JsonObject recorded = JsonParser.parseString(Files.readString(Path.of("../contract/cli/python-app/pasted-error.json")))
            .getAsJsonObject();
        JsonObject real = JsonParser.parseString(recorded.has("stdout") ? recorded.get("stdout").getAsString()
            : recorded.toString()).getAsJsonObject();
        check(ArchvowSearch.isError(real), "a pasted error is an error");
        ArchvowSearch.Hit place = ArchvowSearch.errorPlace(real);
        check(place != null && place.where().equals("src/invoicing/p3_services/mark_paid.py:28") && place.component().equals("mark_paid"),
            "where it broke: " + (place == null ? null : place.where()));
        List<String[]> around = new ArrayList<>();
        around.add(new String[] {"28", "        raise ValueError(...)"});
        String html = ArchvowSearch.errorHtml(real, around, false);
        check(html.contains("ValueError: invoice F-1 is a draft"), "what went wrong");
        check(html.contains("Not a known error of this project&#39;s template") || html.contains("Not a known error of this project's template"), "no known error: said");
        check(html.contains("Where it broke") && html.contains("code of mark_paid") && html.contains("<b>  28  "), "where it broke, the line");
        check(html.contains("Fixed? Record the lesson"), "where the lesson goes");
        check(!ArchvowSearch.isError(JsonParser.parseString("{\"name\":\"x\",\"total\":0,\"groups\":[]}").getAsJsonObject()),
            "a search is not an error");

        // a known error of the template: its code, message, fix and why
        JsonObject known = JsonParser.parseString("""
            {"error": {"error": "KeyError: 'run_date'", "frames": [], "known": [{"code": "ACME012", "error": "KeyError: 'run_date'",
              "message": "The run date comes from datetime.now()", "fix": "read it from config", "why": "INC-12", "severity": "error"}]}}
            """).getAsJsonObject();
        String fr = ArchvowSearch.errorHtml(known, List.of(), true);
        check(fr.contains("ACME012") && fr.contains("Correction : read it from config") && fr.contains("Pourquoi : INC-12"), "known error, in French");
        check(fr.contains("Aucun fichier de ce projet dedans"), "no file: said");
        check(ArchvowSearch.errorPlace(known) == null, "no place");

        // the search view shows it, with buttons to the place
        List<String> opened = new ArrayList<>();
        ArchvowSearchView view = new ArchvowSearchView(new ArchvowSearchView.Actions() {
            @Override public void openFile(String relative, int line) { opened.add("open " + relative + ":" + line); }
            @Override public void showComponent(String id) { opened.add("show " + id); }
            @Override public void openRule(String code) { }
            @Override public void search(String name) { }
        });
        view.show(real, "Traceback (most recent call last): ...", null, false);
        JButton open = null, show = null;
        for (JButton b : all(view, JButton.class)) {
            if (b.getText().equals("Open at line 28")) open = b;
            if (b.getText().equals("Show mark_paid")) show = b;
        }
        check(open != null && open.isVisible() && show != null && show.isVisible(), "buttons to where it broke");
        open.doClick();
        show.doClick();
        check(opened.equals(List.of("open src/invoicing/p3_services/mark_paid.py:28", "show mark_paid")), "they go there: " + opened);
        boolean named = false;
        for (JLabel l : all(view, JLabel.class)) named |= "Error".equals(l.getText());
        check(named, "titled Error, not the whole pasted text");
        System.out.println("PastedErrorRegression OK");
    }
}
