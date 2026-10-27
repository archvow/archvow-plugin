package org.archvow.idea;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** One operation at a time; no shell, project-local execution, bounded output, cancelable. */
final class ArchvowCli {
    static final class Result {
        final List<String> arguments;
        final int exitCode;
        /** What the CLI printed on stdout: the JSON of a --json command, nothing else. */
        final String stdout;
        /** What it printed on stderr: warnings, errors, Python tracebacks. */
        final String stderr;
        /** Both, for people: stdout then stderr. */
        final String output;
        final boolean interrupted;
        Result(List<String> arguments, int exitCode, String stdout, String stderr, boolean interrupted) {
            this.arguments = List.copyOf(arguments); this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
            this.output = this.stderr.isBlank() ? this.stdout
                : this.stdout.isBlank() ? this.stderr : this.stdout.stripTrailing() + "\n" + this.stderr;
            this.interrupted = interrupted;
        }
        /** A message only (the command did not run). */
        Result(List<String> arguments, int exitCode, String message) {
            this(arguments, exitCode, "", message, false);
        }
        boolean ok() { return exitCode == 0 && !interrupted; }
    }

    private final Project project;
    private final AtomicBoolean running = new AtomicBoolean(false);
    ArchvowCli(Project project) { this.project = project; }
    boolean isRunning() { return running.get(); }

    void execute(String label, List<String> arguments, String stdin, Consumer<Result> finished) {
        executeInternal(label, arguments, stdin, false, null, finished);
    }

    /**
     * Read the installed Archvow catalog, NOT the IDE project's .archvow directory.
     * Built-ins and ARCHVOW_TEMPLATES are discovered by the actual CLI. A custom
     * --template /path/to/pack may additionally expose its parent catalog.
     */
    void executeGlobalCatalog(String templateDirectory, Consumer<Result> finished) {
        executeInternal("Catalog", List.of("catalog", "--json"), null, true, templateDirectory, finished);
    }

    private void executeInternal(String label, List<String> arguments, String stdin,
                                 boolean globalCatalog, String templateDirectory,
                                 Consumer<Result> finished) {
        if (!running.compareAndSet(false, true)) {
            finished.accept(new Result(arguments, -1, "Une autre commande Archvow est déjà en cours."));
            return;
        }
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Archvow: " + label, true) {
            @Override public void run(@NotNull ProgressIndicator indicator) {
                long started = System.nanoTime();
                Result result;
                try { result = invoke(arguments, stdin, indicator, globalCatalog, templateDirectory); }
                catch (Exception ex) {
                    result = new Result(arguments, -1, describeFailure(ex));
                } finally { running.set(false); }
                ArchvowRunLog.record(project, label, result.exitCode, result.interrupted,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                Result returned = result;
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!project.isDisposed()) finished.accept(returned);
                });
            }
        });
    }

    private static String describeFailure(Exception ex) {
        if (ex instanceof IOException) {
            return "Impossible de démarrer Archvow : " + ex.getMessage()
                + "\nDans Settings > Tools > Archvow, indique uniquement l'exécutable Python "
                + "(par exemple /chemin/venv/bin/python3), ou 'python3 -m archvow'."
                + "\nSur macOS, utilise de préférence le chemin absolu obtenu avec : "
                + "python3 -c \"import sys; print(sys.executable)\"";
        }
        return ex.getClass().getSimpleName() + ": " + ex.getMessage()
            + "\nVérifie Settings > Tools > Archvow et ton environnement Python.";
    }

    private Result invoke(List<String> arguments, String stdin, ProgressIndicator indicator,
                          boolean globalCatalog, String templateDirectory) throws Exception {
        ArchvowSettings.Values opts = ArchvowSettings.get(project).getState();
        File workingDirectory;
        List<String> argv;
        if (globalCatalog) {
            // Archvow's cmd_catalog supports running outside a project. Using a neutral
            // directory avoids discovering any .archvow/templates/ from the IDE workspace.
            // No --root argument is emitted, even when Project.getBasePath() is null.
            workingDirectory = new File(System.getProperty("java.io.tmpdir", "."));
            if (!workingDirectory.isDirectory())
                workingDirectory = new File(System.getProperty("user.home", "."));
            argv = ArchvowProcessCommand.buildWithoutProject(opts.executable, opts.python, arguments);
        } else {
            String root = project.getBasePath();
            if (root == null) throw new IllegalStateException("Aucun dossier racine pour ce projet IntelliJ");
            workingDirectory = new File(root);
            if (!workingDirectory.isDirectory())
                throw new IllegalStateException("Le dossier du projet n'existe pas : " + root);
            argv = ArchvowProcessCommand.build(opts.executable, opts.python, root, arguments);
        }
        // stdout and stderr apart: a warning on stderr must never be read as part of the JSON on stdout.
        ProcessBuilder builder = new ProcessBuilder(argv).directory(workingDirectory);
        builder.environment().put("NO_COLOR", "1");
        builder.environment().put("PYTHONIOENCODING", "utf-8");
        if (globalCatalog && templateDirectory != null && !templateDirectory.isBlank()) {
            try {
                Path pack = Path.of(templateDirectory).toAbsolutePath().normalize();
                if (Files.isDirectory(pack) && Files.isRegularFile(pack.resolve("template.yaml"))) {
                    String catalogRoot = pack.getParent().toString();
                    String previous = builder.environment().getOrDefault("ARCHVOW_TEMPLATES", "");
                    if (!List.of(previous.split(java.util.regex.Pattern.quote(File.pathSeparator)))
                        .contains(catalogRoot)) {
                        builder.environment().put("ARCHVOW_TEMPLATES", previous.isBlank()
                            ? catalogRoot : previous + File.pathSeparator + catalogRoot);
                    }
                }
            } catch (java.nio.file.InvalidPathException ignored) {
                // The catalog still loads built-in and configured company templates.
            }
        }
        Process process = builder.start();
        StringBuilder buffer = new StringBuilder();
        StringBuilder errors = new StringBuilder();
        Thread reader = collect(process.getInputStream(), buffer, "archvow-cli-stdout");
        Thread errorReader = collect(process.getErrorStream(), errors, "archvow-cli-stderr");
        try (var out = process.getOutputStream()) {
            if (stdin != null) out.write(stdin.getBytes(StandardCharsets.UTF_8));
        }
        boolean cancelled = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(opts.timeoutSeconds);
        try {
            while (!process.waitFor(200, TimeUnit.MILLISECONDS)) {
                if (indicator.isCanceled() || System.nanoTime() >= deadline) {
                    cancelled = true;
                    process.destroyForcibly();
                    break;
                }
            }
            if (!process.waitFor(3, TimeUnit.SECONDS)) { cancelled = true; process.destroyForcibly(); }
        } catch (InterruptedException e) {
            cancelled = true;
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
        reader.join(1500);
        errorReader.join(1500);
        String out;
        String err;
        synchronized (buffer) { out = buffer.toString(); }
        synchronized (errors) { err = errors.toString(); }
        if (out.length() >= MAX_OUTPUT)
            err += "\nSortie tronquée à " + MAX_OUTPUT / 1_000_000 + " Mo : le résultat est incomplet.";
        if (cancelled) err += "\nCommande annulée ou délai dépassé.";
        int exit = process.isAlive() ? -1 : process.exitValue();
        return new Result(argv, cancelled ? -1 : exit, out, err, cancelled);
    }

    /** Characters kept from one stream: a project's whole `show --json` is far below it. */
    private static final int MAX_OUTPUT = 20_000_000;

    private static Thread collect(java.io.InputStream stream, StringBuilder buffer, String name) {
        Thread reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) synchronized (buffer) {
                    if (buffer.length() < MAX_OUTPUT) buffer.append(line).append('\n');
                }
            } catch (Exception ignored) { /* the read stops when the process is cancelled */ }
        }, name);
        reader.setDaemon(true);
        reader.start();
        return reader;
    }
}
