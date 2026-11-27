package org.archvow.idea;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * archvow in a dev container that IntelliJ opened natively (1.4.4): the IDE stays on the computer and names the
 * container's files {@code /$devcontainer.ij/<container>@<daemon>/<path>}, for instance
 * {@code /$devcontainer.ij/5483173dc8bf@u~var~run~docker.sock/usr/local/bin/python3.12}. Such a path is not a program
 * the computer can start: archvow runs in the container through {@code docker exec}, with the container's paths:
 * {@code docker -H unix:///var/run/docker.sock exec -i -w /lab/p -e NO_COLOR ... 5483173dc8bf /usr/local/bin/python3.12
 * -P -m archvow --root /lab/p ...}. A project folder on the computer is found in the container through the
 * container's mounts. No shell. Plain Java: tested headless ({@code DevContainerRegression}), as {@link ArchvowWsl}.
 */
final class ArchvowDevContainer {
    /** A file in a container: the container, the daemon's address ("" = docker's default) and the path inside. */
    record Target(String container, String daemon, String path) {}

    /** A folder of the computer seen at another place in the container. */
    record Mount(String source, String destination) {}

    /** What docker exec passes on from the IDE's environment (ArchvowCli.prepare sets them). */
    static final List<String> ENVIRONMENT = List.of("NO_COLOR", "PYTHONIOENCODING", "ARCHVOW_LOG");

    private static final Pattern PATH = Pattern.compile("^/\\$devcontainer\\.ij/([^/@]+)(?:@([^/]*))?(/.*)?$");

    /** The mounts of a container, asked once of docker (replaced in the tests). */
    static Function<Target, List<Mount>> mounts = ArchvowDevContainer::inspect;
    private static final Map<String, List<Mount>> MOUNTS = new ConcurrentHashMap<>();

    private ArchvowDevContainer() { }

    /** The container and path of {@code path}, or null when it is not a dev container path. */
    static Target parse(String path) {
        if (path == null) return null;
        Matcher m = PATH.matcher(path.trim().replace('\\', '/'));
        if (!m.matches()) return null;
        String path0 = m.group(3) == null ? "/" : m.group(3);
        while (path0.length() > 1 && path0.endsWith("/")) path0 = path0.substring(0, path0.length() - 1);
        return new Target(m.group(1), daemon(m.group(2)), path0);
    }

    static boolean is(String path) {
        return parse(path) != null;
    }

    /** IntelliJ's name of the daemon as docker's -H: {@code u~var~run~docker.sock} -> {@code unix:///var/run/docker.sock}. */
    static String daemon(String encoded) {
        if (encoded == null || encoded.isBlank()) return "";
        if (encoded.startsWith("u~")) return "unix://" + encoded.substring(1).replace('~', '/');
        return "";  // another kind of daemon: docker's current context
    }

    /** The project folder as the container sees it: its own path, or a mounted folder of the computer; else null. */
    static String folder(Target program, String root, Function<Target, List<Mount>> mounts) {
        if (root == null) return null;
        Target inside = parse(root);
        if (inside != null) return inside.container().equals(program.container()) ? inside.path() : null;
        String r = root.replace('\\', '/');
        while (r.length() > 1 && r.endsWith("/")) r = r.substring(0, r.length() - 1);
        Mount best = null;
        for (Mount m : mounts.apply(program)) {
            String s = m.source();
            if ((r.equals(s) || r.startsWith(s.endsWith("/") ? s : s + "/"))
                && (best == null || s.length() > best.source().length())) best = m;
        }
        return best == null ? null : best.destination() + r.substring(best.source().length());
    }

    /** An argument for archvow in the container: a dev container path becomes the container's path, anything else stays. */
    static String argument(String argument) {
        Target t = parse(argument);
        return t == null ? argument : t.path();
    }

    /** docker [-H daemon] exec -i [-w folder] -e NO_COLOR -e PYTHONIOENCODING -e ARCHVOW_LOG container program args... */
    static List<String> command(String docker, Target program, String folder, List<String> inner) {
        List<String> argv = new ArrayList<>();
        argv.add(docker);
        if (!program.daemon().isEmpty()) { argv.add("-H"); argv.add(program.daemon()); }
        argv.add("exec");
        argv.add("-i");
        if (folder != null && !folder.isBlank()) { argv.add("-w"); argv.add(folder); }
        for (String name : ENVIRONMENT) { argv.add("-e"); argv.add(name); }
        argv.add(program.container());
        argv.addAll(inner);
        return argv;
    }

    /** Whether a command line runs through docker exec. */
    static boolean isDocker(List<String> argv) {
        if (argv == null || argv.size() < 2) return false;
        String name = new File(argv.get(0)).getName();
        return (name.equals("docker") || name.equals("docker.exe")) && argv.contains("exec");
    }

    /** The docker command: on the PATH, or where Docker Desktop, Homebrew or Rancher put it (an IDE started from the Dock has a short PATH). */
    static String docker() {
        List<String> places = new ArrayList<>();
        String pathVariable = System.getenv("PATH");
        if (pathVariable != null) for (String dir : pathVariable.split(File.pathSeparator)) if (!dir.isBlank()) places.add(dir);
        String home = System.getProperty("user.home", "");
        places.addAll(List.of("/usr/local/bin", "/opt/homebrew/bin", "/usr/bin",
            "/Applications/Docker.app/Contents/Resources/bin", home + "/.docker/bin", home + "/.rd/bin"));
        boolean windows = ArchvowWsl.windows();
        for (String dir : places) {
            File f = new File(dir, windows ? "docker.exe" : "docker");
            if (f.isFile() && f.canExecute()) return f.getPath();
        }
        return "docker";
    }

    /**
     * What to do when docker says the container is gone (1.4.5): a dev container that was rebuilt (make up, Rebuild)
     * has a new id, and the paths IntelliJ gave before still name the old one. "" when the output is not that.
     */
    static String gone(List<String> argv, String output, boolean french) {
        if (!isDocker(argv) || output == null || !output.contains("No such container")) return "";
        int at = argv.indexOf("exec");
        String id = "";
        for (int i = at + 1; i < argv.size(); i++) {
            String a = argv.get(i);
            if (a.equals("-w") || a.equals("-e")) { i++; continue; }
            if (!a.startsWith("-")) { id = a; break; }
        }
        return french
            ? "Le dev container " + id + " n'existe plus : il a été recréé et a un autre identifiant. Rouvre le dev container, "
                + "puis choisis à nouveau son Python (Settings > Project > Python Interpreter) ; vide le Python de Settings > Tools > "
                + "Archvow s'il nomme encore " + id + "."
            : "The dev container " + id + " no longer exists: it was recreated and has another id. Open the dev container again, "
                + "then choose its Python again (Settings > Project > Python Interpreter); empty the Python of Settings > Tools > "
                + "Archvow if it still names " + id + ".";
    }

    /** Whether a folder is there: a dev container folder counts as there (the computer cannot look; docker exec says if it is not). */
    static boolean isDirectory(String path) {
        return path != null && (is(path) || new File(path).isDirectory());
    }

    /** docker inspect, once per container: its bind mounts (source on the computer, destination inside). */
    private static List<Mount> inspect(Target program) {
        return MOUNTS.computeIfAbsent(program.container() + "@" + program.daemon(), key -> {
            List<String> argv = new ArrayList<>();
            argv.add(docker());
            if (!program.daemon().isEmpty()) { argv.add("-H"); argv.add(program.daemon()); }
            argv.addAll(List.of("inspect", "--format", "{{range .Mounts}}{{.Source}}\t{{.Destination}}\n{{end}}", program.container()));
            try {
                Process process = new ProcessBuilder(argv).redirectErrorStream(false).start();
                process.getOutputStream().close();
                byte[] out = process.getInputStream().readAllBytes();
                if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) { process.destroyForcibly(); return List.of(); }
                return parseMounts(new String(out, StandardCharsets.UTF_8));
            } catch (Exception e) {
                return List.of();
            }
        });
    }

    /** The lines of docker inspect: source TAB destination. */
    static List<Mount> parseMounts(String text) {
        List<Mount> out = new ArrayList<>();
        for (String line : text.split("\n")) {
            String[] parts = line.strip().split("\t");
            if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) out.add(new Mount(parts[0], parts[1]));
        }
        return out;
    }
}
