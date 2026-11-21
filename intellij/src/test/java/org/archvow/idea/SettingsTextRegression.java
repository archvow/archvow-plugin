package org.archvow.idea;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One language at a time (0.26.1): Settings > Tools > Archvow said everything twice, "English / Français". Each text
 * of the page is in one language, and no text of the plugin puts both side by side.
 */
public final class SettingsTextRegression {
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    /** A French word or letter: a text with one is French. */
    private static final Pattern FRENCH = Pattern.compile("[éèêàçù]|\\b(le|la|les|des|du|une|dans|avec|pour|Choisis|Dossier|Langue|Délai)\\b");
    /** A string literal with " / " in it: "English / Français" when one side is French and the other is not. */
    private static final Pattern SLASHED = Pattern.compile("\"([^\"\\n]*?) / ([^\"\\n]*?)\"");

    public static void main(String[] args) throws Exception {
        List<String> en = ArchvowSettingsConfigurable.texts(false);
        List<String> fr = ArchvowSettingsConfigurable.texts(true);
        for (String text : en) check(!FRENCH.matcher(text).find(), "French in an English text: " + text);
        for (int i = 0; i < en.size(); i++) check(!en.get(i).contains(" / "), "two languages: " + en.get(i));
        for (int i = 0; i < fr.size(); i++) check(!fr.get(i).contains(" / "), "two languages: " + fr.get(i));
        check(!en.equals(fr), "French is French");

        Path sources = Path.of("src/main/java/org/archvow/idea");
        try (var files = Files.list(sources)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file);
                Matcher m = SLASHED.matcher(text);
                while (m.find()) {
                    boolean left = FRENCH.matcher(m.group(1)).find(), right = FRENCH.matcher(m.group(2)).find();
                    check(left == right, file.getFileName() + ": two languages side by side: " + m.group());
                }
            }
        }
        System.out.println("SettingsTextRegression OK");
    }
}
