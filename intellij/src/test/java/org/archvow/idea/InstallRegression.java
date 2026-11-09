package org.archvow.idea;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Installing archvow, guided (0.12.0): which Python, from where, the command shown before it runs, and
 * nothing run without "Confirm and run". The same rules as VS Code's install.test.ts.
 */
public final class InstallRegression {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        // the source
        check(ArchvowInstall.source("").kind() == ArchvowInstall.Kind.PIP, "empty is not pip's configuration");
        check(ArchvowInstall.source(null).kind() == ArchvowInstall.Kind.PIP, "null is not pip's configuration");
        check(ArchvowInstall.source(" https://nexus/simple ").equals(new ArchvowInstall.Source(ArchvowInstall.Kind.INDEX, "https://nexus/simple")), "index");
        check(ArchvowInstall.source("/lab/pypi").kind() == ArchvowInstall.Kind.WHEEL, "folder of wheels");
        check(ArchvowInstall.requirement("1.17.0").equals("archvow==1.17.0"), "requirement");
        check(ArchvowInstall.pipArgs("1.17.0", ArchvowInstall.source("")).equals(List.of("-m", "pip", "install", "archvow==1.17.0")), "pip args");
        check(ArchvowInstall.pipArgs("1.17.0", ArchvowInstall.source("https://nexus/simple"))
            .equals(List.of("-m", "pip", "install", "--index-url", "https://nexus/simple", "archvow==1.17.0")), "index args");
        check(ArchvowInstall.pipArgs("1.17.0", ArchvowInstall.source("/w/archvow-1.17.0-py3-none-any.whl"))
            .equals(List.of("-m", "pip", "install", "/w/archvow-1.17.0-py3-none-any.whl")), "wheel args");
        check(ArchvowInstall.pipArgs("1.17.0", ArchvowInstall.source("/lab/pypi"))
            .equals(List.of("-m", "pip", "install", "--no-index", "--find-links", "/lab/pypi", "archvow==1.17.0")), "folder args");

        // the Python: venv, IDE interpreter, setting; never a bare name
        check(ArchvowInstall.venvPythons("/p", false).equals(List.of("/p/.venv/bin/python", "/p/venv/bin/python")), "venv order");
        check(ArchvowInstall.venvPythons("C:\\p", true).get(0).equals("C:\\p\\.venv\\Scripts\\python.exe"), "windows venv");
        Set<String> one = Set.of("/p/venv/bin/python");
        check("/p/venv/bin/python".equals(ArchvowInstall.python("/p", "/usr/bin/python3.12", "/x/python", one::contains, false)), "venv first");
        check("/opt/py/bin/python3".equals(ArchvowInstall.python("/p", "/opt/py/bin/python3", "/x/python", f -> false, false)), "IDE second");
        check("/x/python".equals(ArchvowInstall.python("/p", "python", "/x/python -m archvow", f -> false, false)), "setting third, legacy form read");
        check(ArchvowInstall.python("/p", "python3", "python", f -> false, false) == null, "a bare python chosen");
        check(ArchvowInstall.python("/p", "", "", f -> false, false) == null, "a Python invented");

        // a real venv on disk
        Path root = Files.createTempDirectory("archvow-install");
        Files.createDirectories(root.resolve(".venv/bin"));
        Files.writeString(root.resolve(".venv/bin/python"), "");
        check(root.resolve(".venv/bin/python").toString().equals(
            ArchvowInstall.python(root.toString(), "", "", f -> Files.isRegularFile(Path.of(f)), false)), "the project's .venv not found");

        // the command: no shell; WSL through wsl.exe with Linux paths
        List<String> plain = ArchvowInstall.command("/p/.venv/bin/python", "/p", "1.17.0", ArchvowInstall.source(""), false);
        check(plain.equals(List.of("/p/.venv/bin/python", "-m", "pip", "install", "archvow==1.17.0")), "plain command " + plain);
        check(ArchvowInstall.display(plain).equals("/p/.venv/bin/python -m pip install archvow==1.17.0"), "display");
        List<String> wsl = ArchvowInstall.command("\\\\wsl.localhost\\Ubuntu\\home\\m\\.venv\\bin\\python3", "C:\\work\\shop", "1.17.0",
            ArchvowInstall.source("C:\\wheels\\archvow-1.17.0-py3-none-any.whl"), true);
        check(wsl.equals(List.of("wsl.exe", "-d", "Ubuntu", "--cd", "/mnt/c/work/shop", "--exec", "/home/m/.venv/bin/python3",
            "-m", "pip", "install", "/mnt/c/wheels/archvow-1.17.0-py3-none-any.whl")), "wsl command " + wsl);
        check(ArchvowInstall.workingFolder(wsl, "C:\\work\\shop").equals(System.getProperty("user.home")), "wsl runs from home");
        check(ArchvowInstall.workingFolder(plain, "/p").equals("/p"), "pip runs in the project");

        // why the card shows
        check(ArchvowInstall.reason(new ArchvowVersion.Said("1.99.0", ArchvowVersion.CONTRACT)).isEmpty(), "same contract offered");
        check(ArchvowInstall.reason(new ArchvowVersion.Said("1.22.0", "")).equals("version"), "an older archvow not offered");
        check(ArchvowInstall.reason(new ArchvowVersion.Said("", "")).equals("missing"), "missing not offered");

        // the offer, the card's words
        ArchvowInstall.Offer shown = ArchvowInstall.offer("missing", "1.17.0", "/p/.venv/bin/python", "/p", ArchvowInstall.source(""), false, false);
        check(shown.display().equals("/p/.venv/bin/python -m pip install archvow==1.17.0"), "offer command");
        check(shown.source().equals("pip's configuration"), "offer source");
        check(ArchvowInstall.explain(shown, false).contains("Install archvow 1.17.0 into /p/.venv/bin/python from pip's configuration"), "explain EN");
        ArchvowInstall.Offer none = ArchvowInstall.offer("version", "1.17.0", null, "/p", ArchvowInstall.source(""), true, false);
        check(none.python().isEmpty() && none.command().isEmpty(), "an offer without Python has a command");
        check(ArchvowInstall.explain(none, true).contains("Configurer Python"), "explain FR without Python");
        ArchvowInstall.Offer far = ArchvowInstall.offer("missing", "1.17.0", "\\\\wsl.localhost\\Ubuntu\\usr\\bin\\python3", "\\\\server\\share\\p",
            ArchvowInstall.source(""), false, true);
        check(far.python().isEmpty(), "a WSL Python for a folder WSL cannot reach");

        // nothing runs without "Confirm and run": the panel asks; pip runs from the confirmation only
        Path sources = Path.of(System.getProperty("archvow.sources", "src/main/java/org/archvow/idea"));
        if (!Files.isRegularFile(sources.resolve("ArchvowPanel.java"))) {
            System.out.println("OK (the panel's source not found from " + Path.of("").toAbsolutePath() + ": its confirmation not read)");
            return;
        }
        String panel = Files.readString(sources.resolve("ArchvowPanel.java"));
        int ask = panel.indexOf("private void askInstall(");
        int run = panel.indexOf("private void runInstall(");
        check(ask > 0 && run > 0, "askInstall / runInstall missing");
        String asking = panel.substring(ask, run);
        check(asking.contains("welcomeConfirmation.showConfirmation(") && asking.contains("() -> runInstall("), "install without confirmation");
        check(panel.split("runInstall\\(", -1).length - 1 == 2, "runInstall called outside the confirmation");
        check(panel.contains("new ProcessBuilder(job)"), "pip not run without a shell");
        System.out.println("OK: install source, Python, command, WSL, card and confirmation");
    }
}
