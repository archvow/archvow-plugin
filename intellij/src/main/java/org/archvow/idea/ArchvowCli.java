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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** One operation at a time; no shell, project-local execution, bounded output, cancelable. */
final class ArchvowCli {
    static final class Result {
        final List<String> arguments;
        final int exitCode;
        /** What the CLI printed on stdout: the JSON of a --json command, nothing else ('' when it was the error envelope). */
        final String stdout;
        /** What it printed on stderr: warnings, errors, Python tracebacks; without its log lines. */
        final String stderr;
        /** Both, for people: stdout then stderr. */
        final String output;
        final boolean interrupted;
        /** archvow's own log lines (ARCHVOW_LOG=info, archvow 1.23): for the session log. */
        final List<String> log;
        /** With --json, the error archvow printed as an object on stdout (archvow 1.23), else null. */
        final ArchvowOutput.Error error;
        Result(List<String> arguments, int exitCode, String stdout, String stderr, boolean interrupted) {
            this.arguments = List.copyOf(arguments); this.exitCode = exitCode;
            // archvow's log lines go to `log`, its error envelope to `error`: stdout and stderr say what they said before
            ArchvowOutput.Split split = ArchvowOutput.split(stderr == null ? "" : stderr);
            this.log = split.log();
            this.error = exitCode != 0 ? ArchvowOutput.error(stdout) : null;
            this.stdout = stdout == null || this.error != null ? "" : stdout;
            this.stderr = split.log().isEmpty() ? (stderr == null ? "" : stderr) : split.said();
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

    /** The program archvow runs with (a Python, or the archvow executable), once found: for the session log. */
    String program() { return program; }
    private volatile String program = "";

    /** A read the workspace does itself (show, check, next...): one line of a refresh in the session log. */
    void execute(String label, List<String> arguments, String stdin, Consumer<Result> finished) {
        executeInternal(label, arguments, stdin, false, null, false, finished);
    }

    /** What a person asked for: a line of its own in the session log, with the whole command (0.19.0). */
    void executeAction(String label, List<String> arguments, String stdin, Consumer<Result> finished) {
        executeInternal(label, arguments, stdin, false, null, true, finished);
    }

    /**
     * The catalog for init (0.22.0): what archvow lists in the project's folder - its own pack and the packs
     * `archvow pack add` copied there (.archvow/packs/), templates named {@code <pack>/<template>}.
     */
    void executeGlobalCatalog(String ignored, Consumer<Result> finished) {
        executeInternal("Catalog", List.of("catalog", "--json"), null, false, null, false, finished);
    }

    private void executeInternal(String label, List<String> arguments, String stdin,
                                 boolean globalCatalog, String templateDirectory, boolean action,
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
                ArchvowRunLog.record(project, new ArchvowRunLog.Call(result.arguments, result.exitCode, result.stdout,
                    result.stderr, result.interrupted, result.log, result.error == null ? "" : result.error.text()), action, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                Result returned = result;
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!project.isDisposed()) finished.accept(returned);
                });
            }
        });
    }

    private String describeFailure(Exception ex) {
        if (ex instanceof NotFound) return ex.getMessage();
        if (ex instanceof IOException) {
            return ArchvowI18n.text(project, "Archvow could not start: " + ex.getMessage()
                    + "\nIn Settings > Tools > Archvow, give only the Python executable (e.g. /path/venv/bin/python3, "
                    + "or \\\\wsl.localhost\\Ubuntu\\home\\you\\.venv\\bin\\python3 for a Python in WSL), or 'python3 -m archvow'.",
                "Impossible de démarrer Archvow : " + ex.getMessage()
                    + "\nDans Settings > Tools > Archvow, indique uniquement l'exécutable Python (par exemple /chemin/venv/bin/python3, "
                    + "ou \\\\wsl.localhost\\Ubuntu\\home\\toi\\.venv\\bin\\python3 pour un Python dans WSL), ou 'python3 -m archvow'.");
        }
        return ex.getClass().getSimpleName() + ": " + ex.getMessage() + ArchvowI18n.text(project,
            "\nCheck Settings > Tools > Archvow and your Python environment.",
            "\nVérifie Settings > Tools > Archvow et ton environnement Python.");
    }

    /** No candidate answered --version: what was tried, and what each said. */
    static final class NotFound extends IOException {
        NotFound(String message) { super(message); }
    }

    /** The candidate that answered --version, per project, while the settings and the IDE interpreter stay the same. */
    private static final java.util.Map<Project, String[]> CHOSEN = new java.util.WeakHashMap<>();

    /**
     * Where archvow runs from (ArchvowProcessCommand.candidates): the first candidate that answers
     * {@code --version}, remembered until the settings or the IDE's interpreter change.
     */
    private ArchvowProcessCommand.Candidate choose(ArchvowSettings.Values opts, String root) throws IOException {
        List<ArchvowProcessCommand.Candidate> candidates = ArchvowProcessCommand.candidates(opts.useProjectInterpreter,
            opts.useProjectInterpreter ? ArchvowInterpreter.projectPython(project) : "", opts.executable, opts.python);
        String signature = candidates.toString();
        synchronized (CHOSEN) {
            String[] known = CHOSEN.get(project);
            if (known != null && known[0].equals(signature))
                for (ArchvowProcessCommand.Candidate c : candidates) if (c.toString().equals(known[1])) { program = c.program(); return c; }
        }
        Probe probe = probe(candidates, root, ArchvowI18n.isFrench(project));
        if (probe.chosen() == null) throw new NotFound(probe.report());
        synchronized (CHOSEN) { CHOSEN.put(project, new String[] {signature, probe.chosen().toString()}); }
        program = probe.chosen().program();
        return probe.chosen();
    }

    /** What trying the candidates gave: the first that answered (or null), and a line per candidate tried. */
    record Probe(ArchvowProcessCommand.Candidate chosen, String report) {}

    /** Runs {@code --version} with each candidate in turn (10 s each, 60 s through wsl.exe) until one answers. Also used by the settings' Test. */
    static Probe probe(List<ArchvowProcessCommand.Candidate> candidates, String root, boolean french) {
        StringBuilder report = new StringBuilder(french ? "Archvow introuvable. Essayé, dans l'ordre :\n"
            : "Archvow not found. Tried, in order:\n");
        for (ArchvowProcessCommand.Candidate c : candidates) {
            String where = switch (c.source()) {
                case "ide" -> french ? "interpréteur Python de l'IDE" : "the IDE's Python interpreter";
                case "python" -> french ? "Python des réglages Archvow" : "the Python of Archvow settings";
                default -> french ? "exécutable des réglages Archvow" : "the executable of Archvow settings";
            };
            String said;
            try {
                List<String> argv = root == null
                    ? ArchvowProcessCommand.buildWithoutProject(c.executable(), c.python(), List.of("--version"))
                    : ArchvowProcessCommand.build(c.executable(), c.python(), root, List.of("--version"));
                ProcessBuilder builder = new ProcessBuilder(argv).redirectErrorStream(true);
                prepare(builder, argv, root);
                Process process = builder.start();
                // WSL may have to start its machine first: a longer wait through wsl.exe
                int seconds = ArchvowWsl.isWsl(argv) ? 60 : 10;
                if (!process.waitFor(seconds, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    said = (french ? "pas de réponse en " : "no answer within ") + seconds + " s";
                } else {
                    String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
                    if (process.exitValue() == 0 && out.toLowerCase(java.util.Locale.ROOT).contains("archvow"))
                        return new Probe(c, report.toString());
                    String last = out.isEmpty() ? "" : out.lines().reduce((a, b) -> b).orElse("");
                    said = "exit " + process.exitValue() + (last.isBlank() ? "" : ": " + last);
                }
            } catch (IOException | IllegalArgumentException e) {
                said = e.getMessage();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                said = "interrupted";
            }
            report.append("- ").append(where).append(" (").append(c.program()).append("): ").append(said).append('\n');
        }
        report.append(french
            ? "Installe archvow dans l'interpréteur Python du projet, ou indique un Python qui l'a dans Settings > Tools > Archvow."
            : "Install archvow in the project's Python interpreter, or give a Python that has it in Settings > Tools > Archvow.");
        return new Probe(null, report.toString());
    }

    /** The folder and environment of an archvow process; through wsl.exe, a Windows folder and WSLENV. */
    static void prepare(ProcessBuilder builder, List<String> argv, String root) {
        builder.environment().put("NO_COLOR", "1");
        builder.environment().put("PYTHONIOENCODING", "utf-8");
        // what archvow did, for the session log (a level the person set is kept)
        if (builder.environment().getOrDefault("ARCHVOW_LOG", "").isBlank()) builder.environment().put("ARCHVOW_LOG", "info");
        if (ArchvowWsl.isWsl(argv)) {
            // the Linux folder is given by --cd; wsl.exe itself starts from the user's home (never a \\wsl$ folder)
            builder.directory(new File(System.getProperty("user.home", ".")));
            builder.environment().put("WSLENV", ArchvowWsl.wslenv(builder.environment().get("WSLENV")));
        } else if (root != null) {
            builder.directory(new File(root));
        }
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
            ArchvowProcessCommand.Candidate chosen = choose(opts, null);
            argv = ArchvowProcessCommand.buildWithoutProject(chosen.executable(), chosen.python(), arguments);
        } else {
            String root = project.getBasePath();
            if (root == null) throw new IllegalStateException("Aucun dossier racine pour ce projet IntelliJ");
            workingDirectory = new File(root);
            if (!workingDirectory.isDirectory())
                throw new IllegalStateException("Le dossier du projet n'existe pas : " + root);
            ArchvowProcessCommand.Candidate chosen = choose(opts, root);
            argv = ArchvowProcessCommand.build(chosen.executable(), chosen.python(), root, arguments);
        }
        // stdout and stderr apart: a warning on stderr must never be read as part of the JSON on stdout.
        ProcessBuilder builder = new ProcessBuilder(argv).directory(workingDirectory);
        prepare(builder, argv, workingDirectory.getPath());
        Process process = builder.start();
           try {
                // WSL may have to start its machine first: a longer wait through wsl.exe
                int seconds = ArchvowWsl.isWsl(argv) ? 60 : 10;
                if (!process.waitFor(seconds, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    said = (french ? "pas de réponse en " : "no answer within ") + seconds + " s";
                } else {
                    String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
                    if (process.exitValue() == 0 && out.toLowerCase(java.util.Locale.ROOT).contains("archvow"))
                        return new Probe(c, report.toString());
                    String last = out.isEmpty() ? "" : out.lines().reduce((a, b) -> b).orElse("");
                    said = "exit " + process.exitValue() + (last.isBlank() ? "" : ": " + last);
                }
            } catch (IOException | IllegalArgumentException e) {
                said = e.getMessage();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                said = "interrupted";
            }
            report.append("- ").append(where).append(" (").append(c.program()).append("): ").append(said).append('\n');
        }
        report.append(french
            ? "Installe archvow dans l'interpréteur Python du projet, ou indique un Python qui l'a dans Settings > Tools > Archvow."
            : "Install archvow in the project's Python interpreter, or give a Python that has it in Settings > Tools > Archvow.");
        return new Probe(null, report.toString());
    }

    /** The folder and environment of an archvow process; through wsl.exe, a Windows folder and WSLENV. */
    static void prepare(ProcessBuilder builder, List<String> argv, String root) {
        builder.environment().put("NO_COLOR", "1");
        builder.environment().put("PYTHONIOENCODING", "utf-8");
        // what archvow did, for the session log (a level the person set is kept)
        if (builder.environment().getOrDefault("ARCHVOW_LOG", "").isBlank()) builder.environment().put("ARCHVOW_LOG", "info");
        if (ArchvowWsl.isWsl(argv)) {
            // the Linux folder is given by --cd; wsl.exe itself starts from the user's home (never a \\wsl$ folder)
            builder.directory(new File(System.getProperty("user.home", ".")));
            builder.environment().put("WSLENV", ArchvowWsl.wslenv(builder.environment().get("WSLENV")));
        } else if (root != null) {
            builder.directory(new File(root));
        }
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
            ArchvowProcessCommand.Candidate chosen = choose(opts, null);
            argv = ArchvowProcessCommand.buildWithoutProject(chosen.executable(), chosen.python(), arguments);
        } else {
            String root = project.getBasePath();
            if (root == null) throw new IllegalStateException("Aucun dossier racine pour ce projet IntelliJ");
            workingDirectory = new File(root);
            if (!workingDirectory.isDirectory())
                throw new IllegalStateException("Le dossier du projet n'existe pas : " + root);
            ArchvowProcessCommand.Candidate chosen = choose(opts, root);
            argv = ArchvowProcessCommand.build(chosen.executable(), chosen.python(), root, arguments);
        }
        // stdout and stderr apart: a warning on stderr must never be read as part of the JSON on stdout.
        ProcessBuilder builder = new ProcessBuilder(argv).directory(workingDirectory);
        prepare(builder, argv, workingDirectory.getPath());
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
