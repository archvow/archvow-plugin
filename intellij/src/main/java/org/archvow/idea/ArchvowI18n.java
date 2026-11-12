package org.archvow.idea;

import com.intellij.openapi.project.Project;

import java.util.Locale;

final class ArchvowI18n {
    private ArchvowI18n() {}

    static String text(Project project, String english, String french) {
        return isFrench(project) ? french : english;
    }

    static boolean isFrench(Project project) {
        String mode = "EN";  // English by default (0.25.1), as archvow
        if (project != null) {
            ArchvowSettings.Values values = ArchvowSettings.get(project).getState();
            if (values != null && values.languageMode != null && !values.languageMode.isBlank()) mode = values.languageMode;
        }
        if ("FR".equalsIgnoreCase(mode)) return true;
        if ("EN".equalsIgnoreCase(mode)) return false;
        return Locale.getDefault().getLanguage().toLowerCase(Locale.ROOT).startsWith("fr");
    }

    static String[] modes() {
        return new String[] {"IDE", "EN", "FR"};
    }

    static String modeLabel(Project project, String mode) {
        return switch (mode) {
            case "EN" -> "English";
            case "FR" -> "Français";
            default -> text(project, "Follow IDE", "Suivre l'IDE");
        };
    }
}
