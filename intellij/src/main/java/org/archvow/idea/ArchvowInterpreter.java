package org.archvow.idea;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * The Python interpreter the IDE uses for the project (PyCharm: Settings > Project > Python Interpreter;
 * IntelliJ IDEA with the Python plugin: the project's or a module's Python SDK). archvow is looked for
 * there first ({@link ArchvowProcessCommand#candidates}). A WSL interpreter comes back as a path
 * {@link ArchvowWsl} understands: {@code \\wsl.localhost\Ubuntu\...} as PyCharm stores it, or
 * {@code wsl://<distro>/<path>} for a target-based one (its distribution read from the SDK's data).
 * Nothing here needs the Python plugin: the SDK is found by its type's name.
 */
final class ArchvowInterpreter {
    private ArchvowInterpreter() { }

    /** The project's Python interpreter, or "" when the IDE has none. Never throws. */
    static String projectPython(Project project) {
        if (project == null || project.isDisposed()) return "";
        try {
            return ApplicationManager.getApplication().runReadAction((com.intellij.openapi.util.Computable<String>) () -> {
                Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
                if (python(sdk)) return home(sdk);
                for (Module module : ModuleManager.getInstance(project).getModules()) {
                    Sdk moduleSdk = ModuleRootManager.getInstance(module).getSdk();
                    if (python(moduleSdk)) return home(moduleSdk);
                }
                return "";
            });
        } catch (Throwable unavailable) {
            return "";
        }
    }

    static boolean python(Sdk sdk) {
        return sdk != null && sdk.getSdkType() != null
            && sdk.getSdkType().getName().toLowerCase(Locale.ROOT).contains("python")
            && sdk.getHomePath() != null && !sdk.getHomePath().isBlank();
    }

    private static String home(Sdk sdk) {
        String home = sdk.getHomePath().trim();
        if (ArchvowWsl.parse(home) != null || !home.startsWith("/") || !ArchvowWsl.windows()) return home;
        // a target-based WSL interpreter: a Linux path, its distribution in the SDK's data
        String distro = wslDistribution(sdk.getSdkAdditionalData());
        return distro.isBlank() ? home : "wsl://" + distro + home;
    }

    /** PyTargetAwareAdditionalSdkData -> WslTargetEnvironmentConfiguration -> WSLDistribution: its id, read by reflection. */
    static String wslDistribution(Object data) {
        Object target = call(data, "getTargetEnvironmentConfiguration");
        Object distribution = call(target, "getDistribution");
        for (String getter : new String[] {"getMsId", "getId"}) {
            Object id = call(distribution, getter);
            if (id instanceof String s && !s.isBlank()) return s;
        }
        return "";
    }

    private static Object call(Object on, String name) {
        if (on == null) return null;
        try {
            Method method = on.getClass().getMethod(name);
            method.setAccessible(true);
            return method.invoke(on);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
