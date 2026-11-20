package org.archvow.idea;

import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.project.Project;

import java.io.IOException;

/**
 * Archvow runs only in a trusted project (0.26.1). Running archvow runs a Python the project may hold (its .venv) and,
 * before -P, any folder named archvow at the project's root: in a project opened in safe mode ("Preview in Safe
 * Mode"), the plugin starts no process - it says why, and the next refresh after "Trust project" runs as before. As
 * VS Code's extension, which runs archvow only in a trusted workspace (capabilities.untrustedWorkspaces).
 * TrustedProjects.isTrusted(Project): the platform's documented Java check (plugins.jetbrains.com, Trusted Project).
 */
final class ArchvowTrust {
    private ArchvowTrust() {}

    /** Why nothing ran: a project opened in safe mode. */
    static final class Untrusted extends IOException {
        Untrusted(String message) { super(message); }
    }

    /** True when archvow may run for this project (no project - the welcome screen - is the person's own). */
    static boolean trusted(Project project) {
        return project == null || project.isDefault() || TrustedProjects.isTrusted(project);
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
