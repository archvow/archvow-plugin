package org.archvow.idea;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.InvalidPathException;

/** Disk-only preflight. Never starts Python for an unrelated IDE project. */
final class ArchvowProjectReadiness {
    enum State { NO_PROJECT, ROOT_MISSING, NOT_INITIALIZED, SPEC_INACCESSIBLE, READY }
    record Status(State state, String root) {
        boolean ready() { return state == State.READY; }
        boolean canInitialize() { return state == State.NOT_INITIALIZED; }
    }
    private ArchvowProjectReadiness() { }

    static Status inspect(String basePath) {
        if (basePath == null || basePath.isBlank()) return new Status(State.NO_PROJECT, "");
        try {
            Path root = Path.of(basePath);
            if (!Files.isDirectory(root)) return new Status(State.ROOT_MISSING, basePath);
            Path spec = root.resolve(".archvow").resolve("spec.vow");
            if (!Files.exists(spec)) return new Status(State.NOT_INITIALIZED, basePath);
            if (!Files.isRegularFile(spec) || !Files.isReadable(spec) || Files.size(spec) == 0)
                return new Status(State.SPEC_INACCESSIBLE, basePath);
            return new Status(State.READY, basePath);
        } catch (java.io.IOException ex) {
            return new Status(State.SPEC_INACCESSIBLE, basePath);
        } catch (InvalidPathException | SecurityException ex) {
            return new Status(State.ROOT_MISSING, basePath);
        }
    }
}
