# ArchVow for VS Code - Spec-Driven Development with AI

**Spec-driven development (SDD) for AI-assisted coding: architecture specifications, decisions and checks in your editor.**

An AI assistant writes code fast - and decides things nobody asked it to decide. **ArchVow** keeps your project's
architecture as specifications: what each component must do, the decisions a person makes, what must never happen
and the invariants that prevent it. It checks the code against them, locally and in CI, before any review.

This extension brings the **ArchVow CLI** into VS Code: a visual workspace for your features, components, open
decisions and checks, every ArchVow command as a guided form, and structured prompts for your AI assistant. The
CLI stays the single source of truth: the extension reads what it says and runs its commands - only after you
confirm them.

## Highlights

- **See where the project stands** - the Overview shows what is done, what is open and what Check found, with the
  first finding in plain words and a button to its file.
- **Start in minutes** - try ArchVow on a finished example first, then initialize your repository (or adopt the
  code already there) with a guided wizard that shows every file it will write.
- **Decide what matters** - decisions, open questions, hazards and invariants per component, with the template's
  suggested answers ready to confirm.
- **Work with any AI assistant** - copy a structured request (implement, review, find risks...) for the assistant
  your project approved; what the AI proposes waits for a person to keep it.
- **Catch drift early** - `archvow check` results in the Problems view, Python files checked on save.

## Get started

1. **Install the ArchVow CLI 2.1.1** in your project's Python 3.11+ environment, from your package index or a wheel:
   `python -m pip install archvow==2.1.1`. Check it with `archvow --version`.
   The extension can also show and run that command for you - only after **Confirm and run**.
2. **Not sure yet?** Open any folder, open **ArchVow** in the Activity Bar, and use **Try it**: ArchVow copies a
   finished example to a scratch folder (the form shows where), makes a change an AI often makes - a module nobody
   decided on, a shortcut across layers - and shows what Check catches. Nothing in your folder changes.
3. **Initialize ArchVow** in your repository: choose a template (Python app, FastAPI service, PySpark pipeline,
   LangGraph agent...), answer a few questions, review the files it will write, then confirm. Code already there?
   Choose **Adopt**: your modules become components where they are.
4. **Follow the First steps** on the Overview: answer the foundations, add a feature, add its first component and
   answer its decisions, build it and run Check, commit. Each step is read from the spec and Git; the card goes
   once the five are done.

Developers and reviewers who clone a project that already uses ArchVow just open it: the workspace shows its state.

## The workspace

Open **ArchVow** from the Activity Bar. The sidebar chooses; what you choose opens in **one reusable editor tab**, so
your editor stays uncluttered.

### Overview

- **First steps** for a new project, the current step with its button (Specify, Add feature, Add component, Check).
- **Next** - what to build now, each component ready or blocked, with how many of its decisions have a suggested
  answer. **Check** - the full check of the project.
- **More** - Doctor (is the setup right, what to set once in your Git host and CI), Audit, Briefs, Budget / Next
  (is this branch's change small enough to review), Diff (what changed in the spec) and AI review.
- **Architecture** - the components and their items done: decisions, questions and tasks, with links to the open
  and the done ones.
- **Checks** - errors and warnings, and the first finding: its rule in words (*Illegal dependency between layers*),
  the code and the file and line, with a button that opens it.
- **Packs** - the shared sets of templates and rules your project uses, whether their copy was changed by hand,
  with Update, Remove and Check.

### Features and components

- **Features** - what the product does, feature by feature (`shared`, what every feature uses, last), or the
  components by layer. A feature's page: how far it is, the features it uses and that use it, its components by
  layer and what is open, with **Add component**, **Open feature doc**, **Copy AI prompt**, **Next steps** and
  **Rename**.
- **A component** - its head (type, layer, module, progress, what it uses and what uses it, the brief it was drawn
  from), then its **Decisions**, **Questions**, **Safety** (hazards and the invariants that prevent them),
  **Assumptions**, **Rules**, **Tasks** and **Spec**: the items as cards, the selected one's answer panel beside
  them, opened on the first open item. Move a component to another feature, open its code or its vow.

### Commands

Every ArchVow command as a guided form, built from the CLI's own parser: the options in plain words, the exact
command line as you fill it, and its result laid out - not raw terminal output. Commands that only read run at
once; commands that change the project ask you to **Confirm and run** first. The init wizard previews the files
it will write, as a tree.

### AI skills

Prepare a structured request for your AI assistant: **Draft from a brief**, **Find missing questions**, **Find
risks**, **Implement** a component, **Review changes**, or copy the task for another AI. A launch form says what the
AI reads, what it may add and what you still decide. What the AI adds - a decision, a question, a hazard, a rule -
appears marked **To review** until a person keeps it or removes it.

The extension **prepares AI instructions; it does not run an AI model itself**, and sends nothing anywhere.

## In the editor

- **`.vow` files** - syntax highlighting with one colour per depth, errors and warnings as you type, Outline,
  folding, comments, and navigation to the components, hazards and Python modules a vow refers to. ` ```vow `
  blocks in Markdown are highlighted too.
- **Problems view** - `archvow check` findings as diagnostics, with their code and line; a Python file is checked
  when you save it (`archvow.checkOnSave`).
- **Show ArchVow for this file** - right-click a file or its tab: the component that owns it opens, with what it uses
  and what uses it.
- **Always up to date** - the workspace refreshes when `.vow` files change, whether by a command, Git or an edit.

## Changes stay under your control

- The extension **never edits architecture files itself**: every change is an ArchVow CLI command, shown in full,
  that runs after you select **Confirm and run**.
- What the AI suggests is marked for review until a person accepts or removes it; some decisions are a person's
  only (accepting an assumption, accepting a rule the AI added).
- Nothing is downloaded or installed at startup. **Install archvow** shows the exact pip command - into the
  project's virtual environment, from pip's own configuration, your company's index or a wheel - and runs it only
  after your confirmation.

## How the extension finds ArchVow

In this order, the first that answers `--version` runs the commands:

1. the Python interpreter VS Code uses for the project (the Python extension's), when `archvow.useProjectInterpreter`
   is on;
2. `archvow.python`;
3. the folder's `.venv` (or `venv`);
4. `archvow.executable` (`archvow` on the PATH by default);
5. `archvow` where pip and pipx install it (`~/.local/bin`, `/opt/homebrew/bin`, `/usr/local/bin`, pyenv's shims).

When none answers, the message lists what each one said. **Remote - SSH** and **WSL** (a Python in WSL runs through
`wsl.exe`) work, with the CLI installed where the code is. In an untrusted workspace, the
`.vow` language works, but no command runs.

## Settings

| Setting | Default | What |
|---|---|---|
| `archvow.useProjectInterpreter` | on | look for ArchVow in the project's Python interpreter first |
| `archvow.python` | - | a Python that has ArchVow installed (`<python> -m archvow`) |
| `archvow.executable` | `archvow` | the `archvow` command (a name on the PATH or a full path) |
| `archvow.installFrom` | - | where **Install archvow** takes it from: empty for pip's own configuration, an index URL, a `.whl` file or a folder of wheels |
| `archvow.timeoutSeconds` | 180 | how long one command may run before it is stopped |
| `archvow.checkOnSave` | on | check a Python file when it is saved |
| `archvow.language` | `en` | the language of the workspace: English, French, or VS Code's |

## Commands in the Command Palette

All under **archvow**: Show the workspace, Initialize Archvow in a folder…, Check the project, Open a component,
Open Spec, Open Code, Copy the AI instruction, Show archvow for this file, Find in archvow project, Refresh,
Show the archvow log, Copy the archvow log for support.

## Requirements

- VS Code 1.90 or later.
- The ArchVow CLI 2.1.1 (contract 2.1) in a Python 3.11+ environment. The extension says when the installed
  ArchVow does not speak its contract, rather than misreading it.
- Optional: the Python extension, to use the project's interpreter.

**Languages:** English (default) and French. The same workspace exists for IntelliJ IDEA, PyCharm and the other
JetBrains IDEs.
