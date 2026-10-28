package org.archvow.idea;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Installing archvow, guided (0.12.0): never on its own, never at startup. When archvow cannot start, or is
 * another major.minor than the contract's, the welcome card offers "Install archvow &lt;version&gt;"; the pip
 * command is shown and runs only after "Confirm and run", with no shell, in the project folder. Where from:
 * the setting "Install archvow from" - empty = pip's own configuration (pip.conf, PIP_INDEX_URL), an
 * http(s) URL = an index, anything else = a wheel file or a folder of wheels. Into which Python: the
 * project's venv first, then the IDE's interpreter, then the "Python executable" setting; never a bare
 * "python" chosen for the person. Plain Java: tested headless ({@code InstallRegression}); the VS Code
 * extension does the same ({@code src/install.ts}).
 */
final class ArchvowInstall {
    enum Kind { PIP, INDEX, WHEEL }

    /** Where pip takes archvow from. */
    record Source(Kind kind, String value) {
        /** In words, for the card. */
        String describe(boolean french) {
            return kind == Kind.PIP ? (french ? "la configuration de pip" : "pip's configuration") : value;
        }
    }

    /** What the card shows: the Python ("" = none found, the person chooses one) and the command ("" without one). */
    record Offer(String reason, String version, String python, List<String> command, String source) {
        String display() { return command.isEmpty() ? "" : ArchvowInstall.display(command); }
    }

    private ArchvowInstall() { }

    /** The setting "Install archvow from". */
    static Source source(String setting) {
        String value = setting == null ? "" : setting.trim();
        if (value.isEmpty()) return new Source(Kind.PIP, "");
        if (value.toLowerCase(Locale.ROOT).matches("^https?://.*")) return new Source(Kind.INDEX, value);
        return new Source(Kind.WHEEL, value);
    }

    /** The Pythons of the project's own virtual environments, in the order they are tried (as VS Code's venvPythons). */
    static List<String> venvPythons(String root, boolean windows) {
        String sep = windows ? "\\" : "/";
        String base = root.endsWith(sep) ? root.substring(0, root.length() - 1) : root;
        List<String> out = new ArrayList<>();
        if (windows) for (String name : List.of(".venv", "venv")) out.add(base + sep + name + sep + "Scripts" + sep + "python.exe");
        for (String name : List.of(".venv", "venv")) out.add(base + sep + name + sep + "bin" + sep + "python");
        return out;
    }

    /** "python", "python3", "py" alone: a name, not an interpreter of the project. */
    static boolean bareName(String python) {
        return python.trim().matches("(?i)^(python3?|py)(\\.exe)?$");
    }

    /** The legacy setting "python3 -m archvow" names its Python too. */
    static String pythonOf(String setting) {
        if (setting == null) return "";
        return setting.trim().replaceFirst("\\s+-m\\s+archvow.*$", "").trim();
    }

    /** The Python archvow goes into; null means ask the person (Configure Python). */
    static String python(String root, String interpreter, String pythonSetting, Predicate<String> exists, boolean windows) {
        for (String venv : venvPythons(root, windows)) if (exists.test(venv)) return venv;
        for (String candidate : List.of(interpreter == null ? "" : interpreter.trim(), pythonOf(pythonSetting))) {
            if (!candidate.isEmpty() && !bareName(candidate)) return candidate;
        }
        return null;
    }

    /** "1.17" -> archvow==1.17.* : archvow keeps no backward compatibility, the plugin needs its major.minor. */
    static String requirement(String version) {
        return "archvow==" + version + ".*";
    }

    /** pip's arguments for one source. */
    static List<String> pipArgs(String version, Source source) {
        String wanted = requirement(version);
        return switch (source.kind()) {
            case INDEX -> List.of("-m", "pip", "install", "--index-url", source.value(), wanted);
            case WHEEL -> source.value().toLowerCase(Locale.ROOT).endsWith(".whl")
                ? List.of("-m", "pip", "install", source.value())
                : List.of("-m", "pip", "install", "--no-index", "--find-links", source.value(), wanted);
            case PIP -> List.of("-m", "pip", "install", wanted);
        };
    }

    /** The command line that installs archvow; a Python in WSL runs through wsl.exe, as archvow does. */
    static List<String> command(String python, String root, String version, Source source, boolean windows) {
        List<String> args = pipArgs(version, source);
        ArchvowWsl.Target wsl = ArchvowWsl.program(python, windows);
        List<String> argv = new ArrayList<>();
        if (wsl != null) {
            String folder = ArchvowWsl.linuxPath(root);
            if (folder == null) throw new IllegalArgumentException("WSL cannot reach the project folder: " + root);
            argv.add(wsl.path());
            for (String a : args) argv.add(ArchvowWsl.argument(a));
            return ArchvowWsl.command(wsl.distro(), folder, argv);
        }
        argv.add(python);
        argv.addAll(args);
        return argv;
    }

    /** The command as a person reads it in the confirmation. */
    static String display(List<String> argv) {
        StringBuilder out = new StringBuilder();
        for (String a : argv) {
            if (out.length() > 0) out.append(' ');
            out.append(a.matches("[A-Za-z0-9_@%+=:,./\\\\*-]+") ? a : "\"" + a.replace("\"", "\\\"") + "\"");
        }
        return out.toString();
    }

    /** Why the card shows: "missing" (archvow did not start or say its version), "version" (another major.minor), "" (none). */
    static String reason(String versionOutput, String known) {
        int[] v = ArchvowVersion.parse(versionOutput);
        if (v == null) return "missing";
        return (v[0] + "." + v[1]).equals(known) ? "" : "version";
    }

    static Offer offer(String reason, String version, String python, String root, Source source, boolean french, boolean windows) {
        List<String> command = List.of();
        if (python != null) {
            try {
                command = command(python, root, version, source, windows);
            } catch (IllegalArgumentException unreachable) {
                python = null;
            }
        }
        return new Offer(reason, version, python == null ? "" : python, command, source.describe(french));
    }

    /** The folder pip runs in: the project, or the home folder when it runs in WSL (wsl.exe --cd gives the project). */
    static String workingFolder(List<String> argv, String root) {
        return ArchvowWsl.isWsl(argv) ? System.getProperty("user.home", root) : root;
    }

    /** The card's text: what is wrong, then what the button will do. */
    static String explain(Offer offer, boolean french) {
        String why = "version".equals(offer.reason())
            ? (french ? "Ce plugin est fait pour archvow " + offer.version() + "." : "This plugin is made for archvow " + offer.version() + ".")
            : (french ? "archvow ne démarre pas dans ce projet." : "archvow does not start in this project.");
        if (offer.python().isEmpty()) {
            return why + " " + (french
                ? "Aucun Python trouvé pour l'installer (ni .venv, ni interpréteur de l'IDE, ni réglage) : choisis-en un avec Configurer Python."
                : "No Python found to install it into (no .venv, no IDE interpreter, no setting): choose one with Configure Python.");
        }
        return why + " " + (french
            ? "Installer archvow " + offer.version() + " dans " + offer.python() + " depuis " + offer.source() + " :"
            : "Install archvow " + offer.version() + " into " + offer.python() + " from " + offer.source() + ":");
    }
}
