package org.archvow.idea;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;
import java.util.concurrent.atomic.AtomicBoolean;

/** Welcome screen: let IntelliJ choose any folder (not necessarily an existing Archvow project). */
public final class ArchvowInitFromWelcomeAction extends AnAction {
    private static final AtomicBoolean PENDING = new AtomicBoolean();

    static boolean consumePending() { return PENDING.getAndSet(false); }

    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        AnAction open = ActionManager.getInstance().getAction("WelcomeScreen.OpenProject");
        if (open == null) return;
        PENDING.set(true);
        open.actionPerformed(event);
    }
}
