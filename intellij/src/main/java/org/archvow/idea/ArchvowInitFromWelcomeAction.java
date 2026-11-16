package org.archvow.idea;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import org.jetbrains.annotations.NotNull;
import java.util.concurrent.atomic.AtomicBoolean;

/** Welcome screen: let IntelliJ choose any folder (not necessarily an existing Archvow project). */
public final class ArchvowInitFromWelcomeAction extends AnAction {
    private static final AtomicBoolean PENDING = new AtomicBoolean();

    static boolean consumePending() { return PENDING.getAndSet(false); }

    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        ActionManager actions = ActionManager.getInstance();
        AnAction open = actions.getAction("WelcomeScreen.OpenProject");
        if (open == null) return;
        PENDING.set(true);
        // Run IntelliJ's Open action through the ActionManager: AnAction.actionPerformed is override-only
        // (@ApiStatus.OverrideOnly) and must not be called by a plugin. If the action does not run (disabled
        // after its update), no project opens from here, so the flag is cleared.
        actions.tryToExecute(open, event.getInputEvent(), event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT),
                        event.getPlace(), true)
                .doWhenRejected(() -> PENDING.set(false));
    }
}
