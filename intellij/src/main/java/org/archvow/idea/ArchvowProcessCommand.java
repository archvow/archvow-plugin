package org.archvow.idea;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts a user-provided executable setting into a safe ProcessBuilder argv.
 *
 * Accepts both the documented Python executable (python3, a venv Python path)
 * and the common legacy input "python3 -m archvow". The latter must be split
 * into three arguments, otherwise ProcessBuilder searches for a literal file
 * named "python3 -m archvow".
 *
 * No shell is involved; paths containing spaces remain a single executable.
 */
final class ArchvowProcessCommand {
    private static final Pattern PYTHON_MODULE =
        Pattern.compile("(?s)^(.*?)\\s+-m\\s+archvow\\s*$");

    private ArchvowProcessCommand() { }

    static List<String> build(String executableSetting, String pythonSetting,
                              String root, List<String> arguments) {
        if (root == null || root.isBlank())
            throw new IllegalArgumentException("Aucun dossier racine pour ce projet IntelliJ.");
        return buildInternal(executableSetting, pythonSetting, root, arguments);
    }

    /** For commands such as catalog that Archvow supports without a project. */
    static List<String> buildWithoutProject(String executableSetting, String pythonSetting,
                                            List<String> arguments) {
        return buildInternal(executableSetting, pythonSetting, null, arguments);
    }

    private static List<String> buildInternal(String executableSetting, String pythonSetting,
                                               String root, List<String> arguments) {
        String python = clean(pythonSetting);
        String executable = clean(executableSetting);
        boolean pythonMode = !python.isEmpty();
        String provided = pythonMode ? python : executable;
        if (provided.isEmpty())
            throw new IllegalArgumentException("Configure un executable Archvow ou un interpreteur Python.");

        Matcher module = PYTHON_MODULE.matcher(provided);
        if (module.matches()) {
            // Backward compatibility: both settings may contain 'python3 -m archvow'.
            provided = clean(module.group(1));
            pythonMode = true;
        } else if (provided.matches("(?s).*\\s+-m\\s+.*")) {
            throw new IllegalArgumentException(
                "Utilise seulement le chemin de Python, ou 'python3 -m archvow' (sans autres arguments).");
        }

        String program = stripWrappingQuotes(provided);
        if (program.isBlank())
            throw new IllegalArgumentException("Le chemin de l'executable Python est vide.");

        ArrayList<String> argv = new ArrayList<>();
        argv.add(program);
        if (pythonMode) {
            argv.add("-m");
            argv.add("archvow");
        }
        if (root != null) {
            argv.add("--root");
            argv.add(root);
        }
        argv.addAll(arguments);
        return List.copyOf(argv);
    }

    private static String clean(String s) { return s == null ? "" : s.trim(); }

    private static String stripWrappingQuotes(String text) {
        if (text.length() >= 2 && ((text.startsWith("\"") && text.endsWith("\""))
                || (text.startsWith("'") && text.endsWith("'")))) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }
}
