package org.archvow.vow;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

/** The .vow language of archvow. Its id, `vow`, is also what a Markdown fence names: ```vow. */
public final class VowLanguage extends Language {
    public static final VowLanguage INSTANCE = new VowLanguage();

    private VowLanguage() {
        super("vow");
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Vow";
    }
}
