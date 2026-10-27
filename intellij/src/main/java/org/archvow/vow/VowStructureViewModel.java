package org.archvow.vow;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class VowStructureViewModel extends StructureViewModelBase implements StructureViewModel.ElementInfoProvider {
    VowStructureViewModel(@NotNull PsiFile file, @Nullable Editor editor) {
        super(file, editor, VowStructureElement.root(file));
    }

    @Override
    public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
        return false;
    }

    @Override
    public boolean isAlwaysLeaf(StructureViewTreeElement element) {
        return element instanceof VowStructureElement e && e.isLeaf();
    }
}
