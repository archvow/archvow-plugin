package org.archvow.vow;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

public final class VowFile extends PsiFileBase {
    public VowFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, VowLanguage.INSTANCE);
    }

    @Override
    public @NotNull FileType getFileType() {
        return VowFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "Vow file";
    }
}
