package org.archvow.idea;

import java.util.List;

/**
 * The texts of Settings > Tools > Archvow, in one language (0.26.1: they were in both, side by side). Plain Java:
 * {@code SettingsTextRegression} checks that each is in one language only.
 */
final class ArchvowSettingsText {
    private ArchvowSettingsText() { }

    private static String t(boolean fr, String en, String french) { return fr ? french : en; }

    static String runsTitle(boolean fr) { return t(fr, "Which archvow the IDE runs", "Quel archvow l'IDE lance"); }
    static String runsLabel(boolean fr) { return t(fr, "Runs", "Lance"); }
    static String otherTitle(boolean fr) { return t(fr, "Other settings", "Autres réglages"); }
    static String test(boolean fr) { return t(fr, "Test", "Tester"); }
    static String browse(boolean fr) { return t(fr, "Browse…", "Parcourir…"); }
    static String findAgain(boolean fr) { return t(fr, "Look again", "Chercher à nouveau"); }
    static String automatic(boolean fr) { return t(fr, "Automatic (recommended)", "Automatique (recommandé)"); }
    static String chosenByHand(boolean fr) { return t(fr, "chosen by hand", "choisi à la main"); }
    static String looking(boolean fr) { return t(fr, "Looking for archvow…", "Recherche d'archvow…"); }

    static String notFound(boolean fr) {
        return t(fr, "No archvow found. Choose a Python that has it below, or use Install archvow in the Archvow window.",
            "Aucun archvow trouvé. Choisis ci-dessous un Python qui l'a, ou utilise « Installer archvow » dans la fenêtre Archvow.");
    }

    /** "archvow 2.6.2 with /usr/local/bin/python3.12 — the project's Python interpreter". */
    static String runs(boolean fr, String program, String source, String version) {
        String what = version.isBlank() ? "archvow" : "archvow " + version;
        String where = switch (source) {
            case "python" -> t(fr, "the Python chosen here", "le Python choisi ici");
            case "ide" -> t(fr, "the project's Python interpreter", "l'interpréteur Python du projet");
            case "venv" -> t(fr, "the project's .venv", "le .venv du projet");
            case "sdk" -> t(fr, "another Python interpreter of the IDE", "un autre interpréteur Python de l'IDE");
            case "found" -> t(fr, "where pip or pipx installed it", "là où pip ou pipx l'a installé");
            default -> t(fr, "the archvow executable", "l'exécutable archvow");
        };
        return t(fr, what + " with " + program + " — " + where, what + " avec " + program + " — " + where);
    }

    static String pythonHint(boolean fr) {
        return t(fr, "The Pythons of this computer, with their version and the archvow each has. Choose one to always run archvow "
                + "with it, or keep Automatic.",
            "Les Python de cet ordinateur, avec leur version et l'archvow que chacun a. Choisis-en un pour toujours lancer archvow "
                + "avec lui, ou garde Automatique.");
    }

    static String useIde(boolean fr) {
        return t(fr, "Automatic: look in the project's Python interpreter first",
            "Automatique : chercher d'abord dans l'interpréteur Python du projet");
    }

    /** How Automatic finds archvow, in order; {@code projectPython}: the project's interpreter ("" when none). */
    static String automaticOrder(boolean fr, String projectPython) {
        String ide = projectPython == null || projectPython.isBlank()
            ? t(fr, "none set yet", "aucun pour l'instant") : projectPython;
        return t(fr, "Automatic runs the first Python that has archvow, in this order: 1. the project's Python interpreter "
                + "(Settings > Project > Python Interpreter: " + ide + "), 2. the project's .venv, 3. archvow on the PATH, "
                + "4. the IDE's other interpreters, then where pip or pipx install archvow.",
            "Automatique lance le premier Python qui a archvow, dans cet ordre : 1. l'interpréteur Python du projet "
                + "(Settings > Project > Python Interpreter : " + ide + "), 2. le .venv du projet, 3. archvow dans le PATH, "
                + "4. les autres interpréteurs de l'IDE, puis là où pip ou pipx installent archvow.");
    }

    static String executable(boolean fr) { return t(fr, "archvow executable", "Exécutable archvow"); }
    static String executableHint(boolean fr) {
        return t(fr, "Optional: a standalone archvow (a name on the PATH or a full path), used when no Python has archvow.",
            "Facultatif : un archvow autonome (un nom dans le PATH ou un chemin complet), utilisé quand aucun Python n'a archvow.");
    }
    static String language(boolean fr) { return t(fr, "Language", "Langue"); }
    static String languageHint(boolean fr) {
        return t(fr, "The language of the Archvow window and of this page. IDE: the language of IntelliJ or PyCharm.",
            "La langue de la fenêtre Archvow et de cette page. IDE : la langue d'IntelliJ ou de PyCharm.");
    }
    static String timeout(boolean fr) { return t(fr, "Timeout (seconds)", "Délai (secondes)"); }
    static String timeoutHint(boolean fr) { return t(fr, "From 5 to 3600 seconds.", "De 5 à 3600 secondes."); }
    static String timeoutNotNumber(boolean fr) { return t(fr, "The timeout is a number of seconds.", "Le délai est un nombre de secondes."); }
    static String installFrom(boolean fr) { return t(fr, "Install archvow from", "Installer archvow depuis"); }
    static String installFromHint(boolean fr) {
        return t(fr, "Used only by Install archvow, after you confirm. Empty: pip's own configuration (pip.conf, PIP_INDEX_URL). "
                + "Or your company's index URL, a .whl file, or a folder of wheels.",
            "Utilisé seulement par « Installer archvow », après ta confirmation. Vide : la configuration de pip (pip.conf, "
                + "PIP_INDEX_URL). Ou l'URL de l'index de l'entreprise, un fichier .whl, ou un dossier de wheels.");
    }
    static String noFolder(boolean fr, String root) {
        return t(fr, "The project folder was not found: ", "Dossier du projet introuvable : ") + root;
    }
    static String timedOut(boolean fr) { return t(fr, "No answer within 10 seconds.", "Pas de réponse en 10 secondes."); }
    static String commandLine(boolean fr) { return t(fr, "Command:", "Commande :"); }

    /** Every text of the page, for the tests. */
    static List<String> all(boolean fr) {
        return List.of(runsTitle(fr), runsLabel(fr), otherTitle(fr), test(fr), browse(fr), findAgain(fr), automatic(fr),
            chosenByHand(fr), looking(fr), notFound(fr), runs(fr, "/usr/bin/python3", "ide", "2.6.2"), pythonHint(fr), useIde(fr),
            automaticOrder(fr, ""), executable(fr), executableHint(fr), language(fr), languageHint(fr), timeout(fr),
            timeoutHint(fr), timeoutNotNumber(fr), installFrom(fr), installFromHint(fr), noFolder(fr, "/p"), timedOut(fr),
            commandLine(fr));
    }
}
