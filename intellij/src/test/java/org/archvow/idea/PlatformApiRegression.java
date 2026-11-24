package org.archvow.idea;

import com.intellij.openapi.project.Project;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * The Plugin Verifier's warnings of 0.26.1 (IntelliJ 2024.3 to 2026.3 EAP), gone without changing what the plugin does:
 * FileEditorManager.openFile(VirtualFile, boolean) and openTextEditor (deprecated from 2026.3) open through
 * OpenFileDescriptor.navigate; TrustedProjects.isTrusted(Project) (deprecated and experimental) is called by name,
 * after its 2025.1 replacement, which 2024.3 does not have.
 */
public final class PlatformApiRegression {
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    public static final class Newer {
        public static boolean isProjectTrusted(Project project) { return true; }
    }

    public static final class Older {
        public static boolean isTrusted(Project project) { return false; }
    }

    public static final class Broken {
        public static boolean isTrusted(Project project) { throw new IllegalStateException("no application"); }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader loader = PlatformApiRegression.class.getClassLoader();
        String newer = Newer.class.getName(), older = Older.class.getName();
        Method first = ArchvowTrust.find(new String[][] {{"com.example.Absent", "isProjectTrusted"}, {newer, "isProjectTrusted"},
            {older, "isTrusted"}}, loader);
        check(first != null && first.getDeclaringClass() == Newer.class, "the newest check that exists is used");
        Method fallback = ArchvowTrust.find(new String[][] {{newer, "isTrusted"}, {older, "isTrusted"}}, loader);
        check(fallback != null && fallback.getDeclaringClass() == Older.class, "else the older one");
        check(ArchvowTrust.find(new String[][] {{"com.example.Absent", "isTrusted"}}, loader) == null, "none: null");

        Project project = (Project) Proxy.newProxyInstance(loader, new Class<?>[] {Project.class},
            (proxy, method, arguments) -> method.getReturnType() == boolean.class ? false : null);
        check(ArchvowTrust.trusted(first, project), "the check says trusted");
        check(!ArchvowTrust.trusted(fallback, project), "the check says not trusted");
        check(!ArchvowTrust.trusted(null, project), "no check in this IDE: not trusted, nothing runs");
        check(!ArchvowTrust.trusted(Broken.class.getMethod("isTrusted", Project.class), project), "a check that fails: not trusted");
        check(ArchvowTrust.trusted((Project) null), "no project (the welcome screen) is the person's own");
        check(ArchvowTrust.CHECKS[0][0].equals("com.intellij.ide.trustedProjects.TrustedProjects")
            && ArchvowTrust.CHECKS[1][0].equals("com.intellij.ide.impl.TrustedProjects"), "the platform's two checks, newest first");

        // no code of the plugin calls what the verifier reported
        Pattern reported = Pattern.compile("\\.openFile\\([^)]*,\\s*(true|false)\\)|\\.openTextEditor\\(|TrustedProjects\\.isTrusted\\(|import com\\.intellij\\.ide\\.impl\\.TrustedProjects");
        try (var files = Files.list(Path.of("src/main/java/org/archvow/idea"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                var m = reported.matcher(Files.readString(file));
                check(!m.find(), file.getFileName() + ": " + (m.hitEnd() ? "" : m.group()));
            }
        }
        String tab = Files.readString(Path.of("src/main/java/org/archvow/idea/ArchvowEditorTab.java"));
        check(tab.contains("new OpenFileDescriptor(project, file).navigate(false)") && tab.contains("FileEditorManagerListener"),
            "the editor tab opens through navigate, and waits for an IDE that opens it later");
        System.out.println("PlatformApiRegression OK");
    }
}
