package org.archvow.idea;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * The IDE's own file chooser (0.25.1): the native dialog on macOS when the IDE uses it, otherwise IntelliJ's, and
 * either way in the IDE's light or dark theme. A Swing JFileChooser drew its macOS look over a dark IDE: headers
 * drawn twice, files greyed out.
 */
final class ArchvowChooser {
    private ArchvowChooser() { }

    enum Kind { FOLDER, FILE, FILE_OR_FOLDER }

    /** The path chosen, or null when the person cancels. {@code current}: what the field holds (selected first). */
    static String choose(Project project, Kind kind, String title, String current) {
        FileChooserDescriptor descriptor = switch (kind) {
            case FOLDER -> FileChooserDescriptorFactory.createSingleFolderDescriptor();
            case FILE -> FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
            case FILE_OR_FOLDER -> FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor();
        };
        descriptor.setTitle(title);
        String start = current == null || current.isBlank() ? System.getProperty("user.home", "") : current.trim();
        VirtualFile toSelect = LocalFileSystem.getInstance().findFileByPath(start.replace('\\', '/'));
        VirtualFile chosen = FileChooser.chooseFile(descriptor, project, toSelect);
        return chosen == null ? null : chosen.getPresentableUrl();
    }
}
