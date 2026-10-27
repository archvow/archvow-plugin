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
        return build(executableSetting, pythonSetting, root, arguments, ArchvowWsl.windows());
    }

    /** As {@link #build(String, String, String, List)}; {@code windows}: a WSL program runs through wsl.exe. */
    static List<String> build(String executableSetting, String pythonSetting,
                              String root, List<String> arguments, boolean windows) {
        if (root == null || root.isBlank())
            throw new IllegalArgumentException("No project folder / Aucun dossier racine pour ce projet.");
        return buildInternal(executableSetting, pythonSetting, root, arguments, windows);
    }

    /** For commands such as catalog that Archvow supports without a project. */
    static List<String> buildWithoutProject(String executableSetting, String pythonSetting,
                                            List<String> arguments) {
        return buildInternal(executableSetting, pythonSetting, null, arguments, ArchvowWsl.windows());
    }

    /**
     * Where archvow is looked for, in this order (0.11.2): the IDE's Python interpreter for the project
     * (Settings > Project > Python Interpreter) when {@code useIde}, then the Python of Settings > Tools >
     * Archvow, then its archvow executable ("archvow" on the PATH). The first that answers
     * {@code --version} runs the commands ({@link ArchvowCli}).
     */
    record Candidate(String source, String executable, String python) {
        String program() { return python.isBlank() ? executable : python; }
    }

    static List<Candidate> candidates(boolean useIde, String idePython, String executable, String python) {
        List<Candidate> out = new ArrayList<>();
        if (useIde && idePython != null && !idePython.isBlank()) out.add(new Candidate("ide", "", idePython.trim()));
        if (python != null && !python.isBlank() && out.stream().noneMatch(c -> c.python().equals(python.trim())))
            out.add(new Candidate("python", "", python.trim()));
        String exe = executable == null || executable.isBlank() ? "archvow" : executable.trim();
        out.add(new Candidate("executable", exe, ""));
        return out;
    }

    private static List<String> buildInternal(String executableSetting, String pythonSetting,
                                               String root, List<String> arguments, boolean windows) {
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

        ArchvowWsl.Target wsl = ArchvowWsl.program(program, windows);
        if (wsl != null) {
            // archvow in WSL: wsl.exe runs the Linux program in the project folder, as Linux paths
            String linuxRoot = root == null ? null : ArchvowWsl.linuxPath(root);
            if (root != null && linuxRoot == null)
                throw new IllegalArgumentException("WSL cannot reach the project folder / WSL n'atteint pas le dossier du projet : " + root);
            ArrayList<String> inner = new ArrayList<>();
            inner.add(wsl.path());
            if (pythonMode) { inner.add("-m"); inner.add("archvow"); }
            if (linuxRoot != null) { inner.add("--root"); inner.add(linuxRoot); }
            for (String argument : arguments) inner.add(ArchvowWsl.argument(argument));
            return List.copyOf(ArchvowWsl.command(wsl.distro(), linuxRoot, inner));
        }

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
