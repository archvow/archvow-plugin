package org.archvow.idea;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

/**
 * Where {@code archvow init} puts the project, and what the IDE then opens (0.26.1). Plain Java
 * ({@code InitTargetRegression}): initializing another folder (Folder, {@code --dir}) opens that folder as the IDE's
 * project; and PyCharm's welcome project ("PyCharm Home", the folder PyCharmMiscProject) is never initialized itself:
 * a new project gets its own folder.
 */
final class ArchvowInitTarget {
    private ArchvowInitTarget() { }

    /** The folder PyCharm's non-modal welcome screen keeps as a project ("PyCharm Home"). */
    static final String WELCOME_FOLDER = "PyCharmMiscProject";

    /** The folder {@code init} initialized when it is not the project's own; null otherwise (nothing to open). */
    static String folderToOpen(List<String> args, String projectRoot) {
        if (args == null || args.isEmpty() || !"init".equals(args.get(0))) return null;
        int at = args.indexOf("--dir");
        if (at < 0 || at + 1 >= args.size() || args.get(at + 1).isBlank()) return null;
        try {
            Path root = projectRoot == null || projectRoot.isBlank() ? null : Path.of(projectRoot).toAbsolutePath().normalize();
            Path dir = Path.of(args.get(at + 1).trim());
            if (!dir.isAbsolute() && root != null) dir = root.resolve(dir);
            dir = dir.toAbsolutePath().normalize();
            return dir.equals(root) ? null : dir.toString();
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /** Whether this folder is PyCharm's welcome project, by its name (when the IDE cannot say). */
    static boolean welcomeFolder(String basePath) {
        if (basePath == null || basePath.isBlank()) return false;
        try {
            Path name = Path.of(basePath).getFileName();
            return name != null && name.toString().equals(WELCOME_FOLDER);
        } catch (InvalidPathException e) {
            return false;
        }
    }

    /** The folder a new project goes to from the welcome project: the IDE's projects folder, then its package's name. */
    static String suggestedFolder(String projectsFolder, String pkg) {
        String name = pkg == null ? "" : pkg.trim();
        if (projectsFolder == null || projectsFolder.isBlank() || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) return "";
        return Path.of(projectsFolder).resolve(name).toString();
    }
}
