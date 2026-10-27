package org.archvow.vow;

import org.archvow.vow.core.VowLinks;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A word of a vow that names something else: a component (its spec.vow), a hazard (its line), a Python
 * module (its file). Found by path from the vow itself, so it works without any index; a name that
 * leads nowhere is simply not a link (soft reference, no error).
 */
final class VowReference extends PsiReferenceBase<VowLinePsi> {
    private final VowLinks.Link link;

    VowReference(@NotNull VowLinePsi element, @NotNull TextRange range, @NotNull VowLinks.Link link) {
        super(element, range, true);
        this.link = link;
    }

    @Override
    public @Nullable PsiElement resolve() {
        PsiFile file = getElement().getContainingFile().getOriginalFile();
        VirtualFile vow = file.getVirtualFile();
        if (vow == null) {
            return null;
        }
        return switch (link.kind()) {
            case COMPONENT -> componentVow(vow, link.target());
            case HAZARD -> hazard(file, vow);
            case MODULE -> module(vow);
        };
    }

    private @Nullable PsiFile componentVow(VirtualFile vow, String component) {
        VirtualFile components = componentsFolder(vow);
        VirtualFile target = components == null ? null : components.findFileByRelativePath(component + "/spec.vow");
        return target == null ? null : PsiManager.getInstance(getElement().getProject()).findFile(target);
    }

    private @Nullable PsiElement hazard(PsiFile file, VirtualFile vow) {
        String component = VowLinks.hazardComponent(link.target());
        PsiFile target = component == null ? file : componentVow(vow, component);
        if (target == null) {
            return null;
        }
        String wanted = "hazard " + VowLinks.hazardShortId(link.target());
        for (VowLinePsi line : PsiTreeUtil.getChildrenOfTypeAsList(target, VowLinePsi.class)) {
            if (line.getText().trim().equals(wanted)) {
                return line;
            }
        }
        return target;
    }

    private @Nullable PsiFile module(VirtualFile vow) {
        VirtualFile root = projectRoot(vow);
        if (root == null) {
            return null;
        }
        for (String path : VowLinks.moduleFiles(link.target())) {
            VirtualFile target = root.findFileByRelativePath(path);
            if (target != null) {
                return PsiManager.getInstance(getElement().getProject()).findFile(target);
            }
        }
        return null;
    }

    /** `.archvow/components/` seen from a component's vow or from the project's. */
    private static @Nullable VirtualFile componentsFolder(VirtualFile vow) {
        VirtualFile folder = vow.getParent();
        if (folder == null) {
            return null;
        }
        VirtualFile parent = folder.getParent();
        if (parent != null && parent.getName().equals("components")) {
            return parent;
        }
        return folder.findChild("components");
    }

    /** The folder holding `.archvow/`. */
    private static @Nullable VirtualFile projectRoot(VirtualFile vow) {
        for (VirtualFile dir = vow.getParent(); dir != null; dir = dir.getParent()) {
            if (dir.getName().equals(".archvow")) {
                return dir.getParent();
            }
        }
        return null;
    }

    @Override
    public Object @NotNull [] getVariants() {
        return new Object[0];
    }
}
