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
| **`ARCHVOW_LOG=info`** (0.20.0) | what archvow did, for the session log; a level the person set is kept | `ArchvowCli.prepare` | `env` of `spawn` |
| **archvow's log and error envelope apart** (0.20.0) | archvow 1.23's log lines are on stderr and, with `--json`, its error is an object on stdout: neither may be read as its answer | `ArchvowOutput.split`, `ArchvowOutput.error`: `Result.log`, `Result.error` (stdout then `''`) | `splitLog()`, `errorOf()`: `CliResult.log`, `CliResult.error` |
| **One failure message** (0.20.0) | the welcome card, a notification, a form's refusal and the log say the same: archvow's message and its hint (an internal error's hint is its crash report's path), else the last lines of stderr | `ArchvowOutput.failure`, `ArchvowForms.refusal` | `failure()`, `alerts()` |

### The session log (0.19.0)

What the plugin asked archvow: in IntelliJ the bottom *Archvow Output* tool window, opened by the ☷ button (`ArchvowRunLog`, shown by `ArchvowRunLogView`, and idea.log), in VS Code the
*archvow* output channel (`runlog.ts`, `archvow.showOutput`), the same lines:

- **a person's action** (what goes first in the queue: `executeAction` in IntelliJ, `run(args, stdin,
  first)` in VS Code): `▶` its whole command, its exit code, its duration and archvow's last text line
  (`✔ Decision 1 of logging_setup made`);
- **a refresh**: one line for all its reads (`Refresh  ·  9 reads  ·  2.4 s`), each at debug level
  (idea.log, or *Developer: Set Log Level* in VS Code);
- **a failure**: `✖` the command and what archvow said (its error and hint, else the last lines of
  stderr), then archvow's own log lines of that call (0.20.0: archvow already hides the values in them, so
  *Copy for support* keeps them); `check` exiting 1 because it finds errors is an answer, not a failure;
  archvow's log lines of the other calls go to debug level;
- a call over a second is marked `slow`; one header line per session: the plugin's version, archvow's and
  its contract (0.20.0), the Python used, the project, the IDE.

"Copy for support" (the *Archvow Output* window's button, with "hide my answers"; *archvow: Copy the archvow log for support* in VS Code) gives the
header and the lines with their time, a person's own words (answers, rationales, statements: `masked`)
hidden on request.

A refresh is one call (0.21.0): `archvow workspace --json` (archvow 1.25 and later) gives the spec, every component's
view, `check`, `next`, the briefs and the catalog together (`ArchvowProject.load`, `ArchvowPanel.reload`).
After an action the plugin makes the same call (`refreshAfter`; before 0.21.0 a partial refresh started
four commands); the vow change that follows is its own: the vow watcher ignores it (`ownChangeUntil`). When
a vow changes on disk, the plugin passes the last answer's digest (`--since`, kept with the settings or the
archvow that gave it): if no file of the project changed, archvow answers `unchanged` and nothing is
redrawn. It never does this after an action or when a person refreshes. Since 0.22.0 (archvow 1.26) the
answer also gives the project's packs (`packs`: the overview's Packs row, page 7) and its catalog is the
project's.

The init wizard's catalog (0.22.0): `archvow catalog --json` run in the project's folder, even before `init`
(archvow 1.26 reads `.archvow/packs/` there once `pack add` copied a pack into it); before 0.22.0 it ran
outside the project, with `ARCHVOW_TEMPLATES`.

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
- `WSLENV` passes `NO_COLOR`, `PYTHONIOENCODING` and `ARCHVOW_LOG` (0.22.0: no longer `ARCHVOW_TEMPLATES`,
  which archvow 1.26 no longer reads: company templates come from the project's packs).
- The first answer may wait for WSL to start: the probe waits 60 s through `wsl.exe` (10 s otherwise).
- With VS Code opened *inside* WSL (Remote - WSL), the extension runs on the Linux side: none of this
  is needed.

## Installing archvow (0.12.0)

When archvow does not start, or does not speak the plugin's contract (0.20.0, page [The contract](/en/04-contract)), the plugins offer
**Install archvow 2.1.0**; nothing is installed at startup or without a click. `ArchvowInstall`
(IntelliJ) and `vscode/src/install.ts` (VS Code) hold the same rules:

| Question | Answer |
|---|---|
| Into which Python? | the project's `.venv`, then `venv` (`venvPythons()`), then the IDE's interpreter, then the Python of the settings; a bare `python` is never chosen for the person (`installPython()`, `ArchvowInstall.python`): no Python found, the card says **Configure Python** |
| Which version? | `archvow==2.1.0` (`requirement()`): exactly the archvow the plugins are tested with (0.20.0); once installed, any archvow of the contract is accepted |
| From where? | the setting *Install archvow from* / `archvow.installFrom` (`installSource()`): empty = pip's own configuration (pip.conf, `PIP_INDEX_URL`), an http(s) URL = `--index-url`, a `.whl` file, or a folder of wheels = `--no-index --find-links` (`pipArgs()`) |
| How? | the command is shown (`displayCommand()`), then run only after **Confirm and run**: no shell, in the project folder, 10 minutes at most, `PIP_DISABLE_PIP_VERSION_CHECK=1`; a Python in WSL through `wsl.exe`, as archvow (`installCommand()`, `ArchvowInstall.command`) |
| Afterwards? | success: the settings keep that Python, the version is probed again, the workspace reloads; failure: pip's output, nothing saved |

VS Code: the card is `installCard()` on the welcome page (and in Overview for another version); the
host keeps the offer (`offerInstall`), asks (`askInstall`) and runs pip on the confirmed token only
(`runInstall`). IntelliJ: the welcome card's **Install archvow 1.17** button and the version
notification's action, `askInstall` / `runInstall` in `ArchvowPanel`, the confirmation an
`ArchvowConfirmPanel`.

## Commands reserved for people

archvow 1.17 refuses its decision commands (decisions, hazards, rules...) when an AI agent is among the
parent processes (ARCH051). The plugins' commands come from the IDE, not from an agent: they go
through. If archvow stops one, the plugin shows its message as it is.

## Testing without an IDE

`LaunchCommandRegression` runs a fake `archvow` module with a real Python; `WslRegression` checks the
paths, the order, the probe and a stand-in `wsl.exe`; `vscode/src/test/cli.test.ts` does the same for
VS Code with a fake `archvow` written in Node. `InstallRegression` and `vscode/src/test/install.test.ts`
check the install: the Python chosen, the pip line for each source, WSL, and that pip runs only from a
confirmation.
