package org.archvow.vow;

import com.intellij.openapi.fileTypes.LanguageFileType;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

/** Files ending in .vow: spec.vow, proposals.vow. */
public final class VowFileType extends LanguageFileType {
    public static final VowFileType INSTANCE = new VowFileType();

    private VowFileType() {
        super(VowLanguage.INSTANCE);
    }

    @Override
    public @NotNull String getName() {
        return "vow";
    }

    @Override
    public @NotNull String getDescription() {
        return "archvow vow";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "vow";
    }

    @Override
    public Icon getIcon() {
        return VowIcons.FILE;
    }
}
