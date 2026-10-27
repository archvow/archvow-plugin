package org.archvow.idea;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@State(name = "ArchvowSettings", storages = @Storage("archvow-settings.xml"))
@Service(Service.Level.PROJECT)
public final class ArchvowSettings implements PersistentStateComponent<ArchvowSettings.Values> {
    public static final class Values {
        public String executable = "archvow";
        public String python = "";
        public int timeoutSeconds = 180;
        public String languageMode = "IDE";
        /** archvow is looked for in the IDE's Python interpreter for the project first (0.11.2). */
        public boolean useProjectInterpreter = true;
    }
    private Values values = new Values();
    public static ArchvowSettings get(Project project) { return project.getService(ArchvowSettings.class); }
    @Override public @NotNull Values getState() { return values; }
    @Override public void loadState(@NotNull Values value) { values = value; }
}
