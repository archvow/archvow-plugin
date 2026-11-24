package org.archvow.idea;

import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Archvow runs only in a trusted project (0.26.1). Running archvow runs a Python the project may hold (its .venv) and,
 * before -P, any folder named archvow at the project's root: in a project opened in safe mode ("Preview in Safe
 * Mode"), the plugin starts no process - it says why, and the next refresh after "Trust project" runs as before. As
 * VS Code's extension, which runs archvow only in a trusted workspace (capabilities.untrustedWorkspaces).
 * The platform's check, by name (0.26.1): com.intellij.ide.trustedProjects.TrustedProjects.isProjectTrusted(Project) from
 * IntelliJ 2025.1, else com.intellij.ide.impl.TrustedProjects#isTrusted (2024.3: the documented Java check,
 * deprecated and experimental since). Called by name so the plugin names neither in its code: the Plugin Verifier
 * reported the old one on every IDE, and the new one does not exist in 2024.3. A platform with neither is treated as
 * untrusted: archvow then runs nothing, and says why.
 */
final class ArchvowTrust {
    private ArchvowTrust() {}

    /** Why nothing ran: a project opened in safe mode. */
    static final class Untrusted extends IOException {
        Untrusted(String message) { super(message); }
    }

    /** The platform's check, newest first: {class, static boolean method(Project)}. */
    static final String[][] CHECKS = {
        {"com.intellij.ide.trustedProjects.TrustedProjects", "isProjectTrusted"},
        {"com.intellij.ide.impl.TrustedProjects", "isTrusted"},
    };
    private static final Method CHECK = find(CHECKS, ArchvowTrust.class.getClassLoader());

    /** The first {class, method} that exists as a public static boolean method(Project); null when none does. */
    static Method find(String[][] checks, ClassLoader loader) {
        for (String[] check : checks) {
            try {
                Method method = Class.forName(check[0], false, loader).getMethod(check[1], Project.class);
                if (Modifier.isStatic(method.getModifiers()) && method.getReturnType() == boolean.class) return method;
            } catch (ReflectiveOperationException | LinkageError absent) {
                // not in this IDE: the next one
            }
        }
        return null;
    }

    /** True when archvow may run for this project (no project - the welcome screen - is the person's own). */
    static boolean trusted(Project project) {
        return project == null || project.isDefault() || trusted(CHECK, project);
    }

    static boolean trusted(Method check, Project project) {
        if (check == null) return false;
        try {
            return Boolean.TRUE.equals(check.invoke(null, project));
        } catch (ReflectiveOperationException | RuntimeException failed) {
            return false;
        }
    }

    static String message(boolean french) {
        return french
            ? "Ce projet est ouvert en mode sans échec : Archvow ne lance rien dans un projet qui n'est pas approuvé (il "
                + "exécuterait le Python du projet). Clique sur « Trust project » en haut de l'éditeur, puis Rafraîchir."
            : "This project is open in safe mode: Archvow runs nothing in a project you have not trusted (it would run the "
                + "project's Python). Click \"Trust project\" at the top of the editor, then Refresh.";
    }

    /** Throws Untrusted, with the message in the project's language, unless archvow may run. */
    static void require(Project project) throws Untrusted {
        if (!trusted(project)) throw new Untrusted(message(project != null && ArchvowI18n.isFrench(project)));
    }
}
