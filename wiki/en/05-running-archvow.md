# 5. Running archvow

> 🇫🇷 [Version française](/05-running-archvow) · English version

Everything the plugins know comes from an archvow command; everything they change goes through an
archvow command. This page explains how a command is run. Files: `ArchvowCli`, `ArchvowProcessCommand`,
`ArchvowInterpreter`, `ArchvowWsl` (IntelliJ); `vscode/src/cli.ts` (VS Code).

## The rules

| Rule | Why | IntelliJ | VS Code |
|---|---|---|---|
| **Never a shell** | the text of an answer must never become a command | `new ProcessBuilder(argv)` | `spawn(program, args, { shell: false })` |
| **In the project folder** | archvow finds `.archvow/` from there | `--root <project>` and the working directory | `cwd: root` |
| **One command at a time** | two writes of the spec at once would trip over each other | a second command is refused while the first runs (`running`) | a queue: what a person asks goes before background reads (`run(args, stdin, first)`) |
| **stdout and stderr apart** | a warning on stderr must never break the JSON read on stdout | `Result.stdout`, `Result.stderr` | `CliResult.stdout`, `CliResult.stderr` |
| **A time-out** | a stuck command must not block the IDE | `timeoutSeconds` (180 s by default), cancellable from the progress bar | `archvow.timeoutSeconds` |
| **Bounded output** | a huge project must not fill the memory | 20 million characters per stream (`MAX_OUTPUT`) | - |
| **`NO_COLOR=1`, `PYTHONIOENCODING=utf-8`** | no ANSI colours in the JSON, no wrongly decoded text | `ArchvowCli.prepare` | `env` of `spawn` |

The session log (button ☷) keeps, per project, each command, its exit code and its duration, never its
content: `ArchvowRunLog`; in VS Code, the *archvow* output channel (`archvow.showOutput`).

## Where archvow is looked for

In this order; **the first that answers `archvow --version`** (code 0 and "archvow" in its answer) runs
the commands. The choice is kept while the settings and the IDE's interpreter stay the same: one probe
per session.

1. **The IDE's Python interpreter for the project**: `<python> -m archvow`.
   - IntelliJ / PyCharm: the project's Python SDK, otherwise a module's (`ArchvowInterpreter.projectPython`,
     through `ProjectRootManager` and `ModuleRootManager`, without depending on the Python plugin: the
     SDK type is recognised by its name).
   - VS Code: the interpreter of the Python extension (`ms-python.python`,
     `environments.getActiveEnvironmentPath`), read again when it changes.
   - Setting *Use the project's Python interpreter first* / `archvow.useProjectInterpreter`, on by
     default.
2. **The Python of the settings** (Settings > Tools > Archvow / `archvow.python`): `<python> -m archvow`.
3. **The executable of the settings** (`archvow` by default, looked for in the IDE's PATH).

`ArchvowProcessCommand.candidates` and `candidates()` make the list; `ArchvowCli.probe` and
`ArchvowCli.choose` (VS Code: the `choose` method of `ArchvowCli` in `cli.ts`) go through it. When none
answers, the message says what each one answered ("No module named archvow", "not found"...) instead
of an error without context.

Why the IDE's interpreter first: on macOS, an IDE started from the Dock does not have the terminal's
PATH, so `archvow` is often not on it; the project's Python is known to the IDE.

## The Python field, and `python3 -m archvow`

`ArchvowProcessCommand.build` accepts the path alone (`/path/venv/bin/python3`, the advised form) and
the former form `python3 -m archvow`, which it splits into three arguments (otherwise the system would
look for a file named "python3 -m archvow"). A path with spaces stays one argument; quotes around it are
removed. Any other option in the field is refused.

## archvow in WSL, the IDE on Windows

A Linux program cannot be started by its Windows path (`\\wsl.localhost\Ubuntu\home\...\python3`):
Windows sees a file there, not a program. `ArchvowWsl` (VS Code: `wslProgram()`, `linuxPath()` in
`cli.ts`) starts it through `wsl.exe`:

```
wsl.exe -d Ubuntu --cd /home/marc/proj --exec /home/marc/.venv/bin/python3 -m archvow --root /home/marc/proj check --json
```

| Form recognised | Distribution |
|---|---|
| `\\wsl.localhost\Ubuntu\...`, `\\wsl$\Ubuntu\...`, `//wsl.localhost/Ubuntu/...` | the path's |
| `wsl://Ubuntu/...` | the path's |
| `wsl:///...` or a Linux path `/home/...` (on Windows only) | the default one |
| PyCharm's WSL interpreter (a "target" SDK) | read from the SDK's data, by reflection (`ArchvowInterpreter.wslDistribution`) |

- The project folder becomes a Linux path (`C:\work\p` -> `/mnt/c/work/p`, `\\wsl.localhost\Ubuntu\home\p` -> `/home/p`),
  as does every argument that is a Windows path (`ArchvowWsl.argument`).
- `--exec`: no shell on the Linux side either.
- `WSLENV` passes `NO_COLOR`, `PYTHONIOENCODING` and `ARCHVOW_TEMPLATES` (a list of folders, translated
  into Linux paths: `/l`).
- The first answer may wait for WSL to start: the probe waits 60 s through `wsl.exe` (10 s otherwise).
- With VS Code opened *inside* WSL (Remote - WSL), the extension runs on the Linux side: none of this
  is needed.

## Commands reserved for people

archvow 1.16 refuses its decision commands (decisions, hazards, rules...) when an AI agent is among the
parent processes (ARCH051). The plugins' commands come from the IDE, not from an agent: they go
through. If archvow stops one, the plugin shows its message as it is.

## Testing without an IDE

`LaunchCommandRegression` runs a fake `archvow` module with a real Python; `WslRegression` checks the
paths, the order, the probe and a stand-in `wsl.exe`; `vscode/src/test/cli.test.ts` does the same for
VS Code with a fake `archvow` written in Node.
