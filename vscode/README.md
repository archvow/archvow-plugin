# archvow for VS Code

The archvow workspace in VS Code: the same functions as the IntelliJ plugin. The **archvow** sidebar
(activity bar) holds Overview, Components and Commands; what you choose there opens in **one** editor tab. The
extension reads everything from the archvow CLI and changes nothing itself: every change is an archvow
command, shown to you before it runs.

## What it does

| Where | What |
|---|---|
| **archvow** sidebar (activity bar; **archvow: Show the workspace**) | **Overview**: project status (pending and resolved counters that lead to the work), the architecture table, Check & Gates (every diagnostic, its rule, its file), Check, Next, Full check, Doctor, Audit, Project vow, Budget / Next, Diff, Review with a Git base, a feature-branch warning on main/develop; a command's result opens in the editor tab. **Components**: the components by container and layer (All, Pending, Resolved). **Commands**: every archvow command |
| **archvow** editor tab (one tab, titled *archvow: clean_orders*, reused for the next choice) | the chosen component, its head over the whole width (open items, type · layer · module · ticket, one progress bar - decisions made, safety answered, tasks done, examples tested, green when complete -, description, *Drawn from* its brief (opens it at that heading), Uses · Used by, examples): Clarify, Open code, Edit spec, AI prompt, + Question, + Rule, + Component, and its views - Decisions, Questions, Safety and Assumptions as cards on the left with the selected one's answer panel on the right, opened on the first open item: Decisions (Save answer, Propose, Accept proposal, Use suggestion), Questions, Safety (Declare hazard, Not applicable, proposed hazard + invariant, add or remove hazards and invariants), Assumptions, Rules, Tasks (classify a column, a task's AI prompt), Spec (the card the AI implements and the vow it is written from), Other / Project; Open code opens the file as an ordinary tab next to it. The chosen command: its form built from archvow's own parser (plain labels, the essential fields first and More options, what archvow will do and the command as it is typed, archvow's refusal in red), with the init wizard of `archvow catalog --json`; the `--from` field offers the files of the project's brief folders and their headings (`specs/001-orders/plan.md#data-model`), and when archvow refuses a file outside them, **Add this folder (architect)** opens `init --force` prefilled. A result of Overview's Check, Next, Doctor... |
| The AI skills (0.16.0, 0.17.x) | **✦ AI ▾**, the first action of a component and an **AI skills** group in Commands: Draft from a brief, Find missing questions, Find risks, Implement, Review changes, then Copy the card for another AI - a launch form says what the AI reads, may add and what you still decide, and sends the request to Copilot Chat (typed, you press Enter) or copies it. Its menu marks the suggested step. **+ Add ▾**: a question, a rule, an assumption. What the AI adds sits in Decisions, Safety and Rules with an origin tag (Template, Project, Brief, AI) and **To review** until you keep it (**Keep**, **Keep, answer later**, **Remove**); a small line under the views shows **All · Open · To review**. Save answer moves to the next open item. Overview › **Briefs**: each brief section used, not used yet (**Draft with AI…**) or changed since |
| An editor (right-click), or its tab (right-click on the title) | **archvow: Show archvow for this file**: `archvow show <file>:<line>` - the component that owns the file opens in the editor tab, with what it uses and what uses it (each one a link); a file no component owns gets archvow's hint |
| Problems view | `archvow check` on the whole project, and on a Python file each time you save it |
| `.vow` files | one keyword colour per depth, ids, people, dates; what keeps archvow from reading the file, as you type; Ctrl+click on a component in `uses [...]`, a hazard in `prevents [...]`, the file of `module`; Outline; folding; `#` comments; a line on top naming the command that changes the vow; ```` ```vow ```` blocks in Markdown |

Every change asks first, in the page: the command line it will run is shown with **Confirm and run**.
The workspace refreshes by itself when a vow changes on disk (an archvow command in a terminal, an AI
agent, a `git checkout`). English or French: **Language** at the bottom of Overview, or the `archvow.language` setting.

## Set up

1. archvow installed: `archvow --version` in a terminal. The extension knows archvow 1.17 and says so
   when the installed one is another version. When archvow is missing or another major.minor, the
   workspace offers **Install archvow 1.17**: it shows `<python> -m pip install archvow==1.17.*` - the
   project's `.venv` (or `venv`) first, then the project's interpreter, then `archvow.python`, never a
   bare `python` - and runs it only after **Confirm and run**, no shell, in the project folder; on
   success `archvow.python` is set to that Python. pip takes archvow where your pip is configured to
   (pip.conf, `PIP_INDEX_URL`), or from **archvow.installFrom**.
2. archvow is looked for in this order, the first that answers `archvow --version` runs the commands:
   the Python interpreter VS Code uses for the project (the Python extension's, when
   **archvow.useProjectInterpreter** is on), **archvow.python** (`/path/venv/bin/python3`: the extension
   runs `<python> -m archvow`), **archvow.executable** (`archvow` on the PATH, or its full path). When
   none answers, the error lists what each one said.
   On Windows with archvow in WSL, give the WSL Python: `\\wsl.localhost\Ubuntu\home\you\.venv\bin\python3`
   (or `wsl://Ubuntu/home/you/.venv/bin/python3`, or `/home/you/.venv/bin/python3` for the default
   distribution): it runs through `wsl.exe --exec`, the project folder given as a Linux path.
3. Open the project folder. A folder without `.archvow/spec.vow` shows **Initialize Archvow** (the init
   wizard). In an untrusted folder only the `.vow` language works.

The brief folders (archvow 1.17): `briefs/` by default; an architect adds others in the init answer
`briefs` (*Show advanced settings* in the init wizard), e.g. `briefs,specs` for Spec Kit's `specs/`.
Overview lists them. An item drawn from a brief whose text changed shows ARCH039 with **Accept
(reviewed)** (`archvow brief accept`, confirmed) and **Review the brief**.

| Setting | Default | |
|---|---|---|
| `archvow.executable` | `archvow` | the command, or its full path |
| `archvow.useProjectInterpreter` | true | look first in the project's Python interpreter (Python extension) |
| `archvow.python` | (empty) | a Python with archvow installed (a WSL path on Windows: through `wsl.exe`); before the executable |
| `archvow.installFrom` | (empty) | where **Install archvow** takes it from: empty = pip's own configuration, your company's index URL (`--index-url`), a `.whl` file, or a folder of wheels (`--no-index --find-links`) |
| `archvow.timeoutSeconds` | 180 | how long one command may run |
| `archvow.checkOnSave` | true | `archvow check --file` on each saved Python file |
| `archvow.language` | `auto` | the workspace's language: VS Code's, `en` or `fr` |

archvow 1.17 runs its person-only commands (decisions, hazards, rules...) only for a person: the
extension's commands come from VS Code, not from an AI agent, so they pass. If archvow stops one
(ARCH051, ARCH052, ARCH054), the workspace shows archvow's message as it is.

With Remote - SSH or WSL the extension runs on the remote side, so archvow runs where the project is.

## How it works

`src/workspace.ts` runs archvow (no shell, in the project folder, one command at a time, what a person
asked before the background reads), keeps what it said and sends it to the page (`media/workspace.js`).
The page asks; the extension checks every command against archvow's grammar (`contract/cli/commands.json`)
and runs a change only after **Confirm and run**. `src/workItems.ts`, `src/grammar.ts` and
`src/formatter.ts` follow the IntelliJ workspace rule by rule; the `.vow` reader (`src/vow`) is the
IntelliJ plugin's, tested against it (`../contract/`).

```bash
npm ci
npm test            # the .vow reader against the Java one, the grammar, the CLI runner, the workspace's rules
npm run package     # -> ../dist/archvow-0.12.0.vsix
```

Install: Extensions view > `...` > **Install from VSIX...**. For a team, a private registry (a
self-hosted Open VSX) or the `.vsix` on an internal share, so nobody downloads from outside.
