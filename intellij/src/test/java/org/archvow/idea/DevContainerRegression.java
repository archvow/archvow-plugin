package org.archvow.idea;

import java.util.List;

/**
 * archvow in a dev container IntelliJ opened natively (1.4.4): the /$devcontainer.ij/<container>@<daemon>/<path> paths
 * IntelliJ gives, the project folder as the container sees it (its own path or a mount), the command through
 * docker exec with the container's paths, and what the plugin does around it (no folder check, the user's home).
 */
public final class DevContainerRegression {
    private static void equal(Object expected, Object actual, String name) {
        if (!expected.equals(actual)) throw new AssertionError(name + ": expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) {
        String python = "/$devcontainer.ij/5483173dc8bf@u~var~run~docker.sock/usr/local/bin/python3.12";
        String root = "/$devcontainer.ij/5483173dc8bf@u~var~run~docker.sock/lab/clones/alice/shop/";
        ArchvowDevContainer.Target target = ArchvowDevContainer.parse(python);
        equal(new ArchvowDevContainer.Target("5483173dc8bf", "unix:///var/run/docker.sock", "/usr/local/bin/python3.12"), target, "the IDE's interpreter");
        equal(new ArchvowDevContainer.Target("5483173dc8bf", "", "/work"), ArchvowDevContainer.parse("/$devcontainer.ij/5483173dc8bf/work"), "no daemon");
        equal(new ArchvowDevContainer.Target("abc", "unix:///Users/marc/.colima/default/docker.sock", "/"),
            ArchvowDevContainer.parse("/$devcontainer.ij/abc@u~Users~marc~.colima~default~docker.sock"), "colima, the container's root");
        equal(true, ArchvowDevContainer.parse("/usr/local/bin/python3.12") == null && ArchvowDevContainer.parse(null) == null, "not dev container paths");
        equal("/lab/clones/alice/shop/src/a.py", ArchvowDevContainer.argument(root + "src/a.py"), "an argument in the container");
        equal("Fail: keep /usr text", ArchvowDevContainer.argument("Fail: keep /usr text"), "text stays");

        // the project folder: its path in the same container, or a mount of the computer's folder
        List<ArchvowDevContainer.Mount> mounts = List.of(new ArchvowDevContainer.Mount("/Users/marc", "/home/marc"),
            new ArchvowDevContainer.Mount("/Users/marc/work/shop", "/workspaces/shop"));
        equal("/lab/clones/alice/shop", ArchvowDevContainer.folder(target, root, t -> List.of()), "a folder in the container");
        equal("/workspaces/shop/api", ArchvowDevContainer.folder(target, "/Users/marc/work/shop/api", t -> mounts), "the longest mount");
        equal("/home/marc/notes", ArchvowDevContainer.folder(target, "/Users/marc/notes/", t -> mounts), "a mount");
        equal(true, ArchvowDevContainer.folder(target, "/Users/marcel", t -> mounts) == null, "not a mount: /Users/marcel is not under /Users/marc");
        equal(true, ArchvowDevContainer.folder(target, "/$devcontainer.ij/other@u~var~run~docker.sock/p", t -> mounts) == null, "another container");

        // the command
        equal(List.of("docker", "-H", "unix:///var/run/docker.sock", "exec", "-i", "-w", "/lab/clones/alice/shop",
                "-e", "NO_COLOR", "-e", "PYTHONIOENCODING", "-e", "ARCHVOW_LOG", "5483173dc8bf", "/usr/local/bin/python3.12", "--version"),
            ArchvowDevContainer.command("docker", target, "/lab/clones/alice/shop", List.of("/usr/local/bin/python3.12", "--version")), "docker exec");
        List<String> argv = ArchvowProcessCommand.build("", python, root, List.of("check", "--report", root + "r.json"), false);
        equal(true, ArchvowDevContainer.isDocker(argv), "built through docker");
        int at = argv.indexOf("5483173dc8bf");
        equal(List.of("/usr/local/bin/python3.12", "-P", "-m", "archvow", "--root", "/lab/clones/alice/shop", "check", "--report",
            "/lab/clones/alice/shop/r.json"), argv.subList(at + 1, argv.size()), "archvow in the container, the container's paths");
        equal(false, ArchvowDevContainer.isDocker(ArchvowProcessCommand.build("", "/usr/bin/python3", "/tmp", List.of("check"), false)), "a local Python");
        try {
            ArchvowProcessCommand.build("", python, "/Users/nobody/p", List.of("check"), false);
            throw new AssertionError("a folder the container cannot see must be refused");
        } catch (IllegalArgumentException expected) {
            equal(true, expected.getMessage().contains("cannot see the project folder"), "the refusal says why");
        }

        // around the command: no folder check on the computer, docker starts from the user's home
        equal(true, ArchvowDevContainer.isDirectory(root) && !ArchvowDevContainer.isDirectory("/no/such/folder"), "folder checks");
        ProcessBuilder builder = new ProcessBuilder(argv);
        ArchvowCli.prepare(builder, argv, root);
        equal(new java.io.File(System.getProperty("user.home", ".")), builder.directory(), "docker starts from home");
        equal("1", builder.environment().get("NO_COLOR"), "the environment docker exec passes on");
        equal(System.getProperty("user.home", "x"), ArchvowInstall.workingFolder(argv, root), "pip through docker starts from home");
        // 1.4.5: a recreated container - say what to do, not docker's error
        String said = ArchvowDevContainer.gone(argv, "Error response from daemon: No such container: 5483173dc8bf\n", false);
        equal(true, said.startsWith("The dev container 5483173dc8bf no longer exists") && said.contains("Python Interpreter"), "gone, said");
        equal("", ArchvowDevContainer.gone(argv, "ModuleNotFoundError: No module named archvow", false), "another error stays");
        equal("", ArchvowDevContainer.gone(List.of("/usr/bin/python3", "-m", "archvow"), "No such container", false), "not docker");
        System.out.println("DevContainerRegression OK");
    }
}
