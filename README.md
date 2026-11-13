# archvow-plugin

The IDE plugins of **archvow** - spec-driven development with AI - for **VS Code** and the **JetBrains IDEs**
(IntelliJ IDEA, PyCharm and the others). archvow itself is a command-line tool: it keeps a project's
architecture as specifications (`.vow` files), lets people decide what the AI must not decide, and checks the
code against them. These plugins put that work in the editor - the project's state, its open decisions, its
checks, every command as a form - and never decide anything themselves: **the archvow CLI is the only writer**.

This repository holds both plugins, apart from archvow itself (archvow never knows an editor).

- What a user gets: [the VS Code extension's page](vscode/README.md) and the JetBrains plugin's
  description (`intellij/src/main/resources/META-INF/plugin.xml`), the texts of both Marketplaces.
- How it is built, file by file and flow by flow: [the wiki](wiki/en/home.md) (French: [wiki/](wiki/home.md)).

## What the plugins do

The same workspace in both editors, in the same places:

| Where | What |
|---|---|
| **Overview** | for a new project, **First steps** (five steps archvow reads from the spec and Git, the current one with its button); **Next** and **Check**, with Doctor, Audit, Briefs, Budget / Next, Diff and AI review under **More**; **Architecture** (items done, open items, done items); **Checks** (errors, warnings and the first finding: its rule in words, its file and line); **Packs** |
| **Features** | what the product does, feature by feature (`shared` last) or by layer; a feature's page: how far it is, the features it uses and that use it, its components by layer, what is open, with Add component, Open feature doc, Copy AI prompt, Next steps, Rename |
| **A component** | its decisions (with the template's suggested answers), questions, safety (hazards, invariants), assumptions, rules, columns, tasks and its spec: the items as cards, the answer panel beside them |
| **Commands** | every archvow command as a form built from archvow's own parser, the exact command shown, its result read and laid out; the init wizard built from `archvow catalog --json`, with the files it will write |
| **AI skills** | the request for an AI assistant (draft from a brief, find missing questions, find risks, implement, review changes), copied for the assistant the project approved; what the AI adds waits **To review** until a person keeps it |
| **Before a project** | Initialize (or Adopt existing code), and **Try archvow on an example**: `archvow try` copies a finished example to a scratch folder, makes the change an AI often makes, and shows what Check catches |
| **In the editor** | the `.vow` language (highlighting by depth, errors as you read, folding, outline, links); `archvow check` in VS Code's Problems view and a Python file checked on save; *Show archvow for this file* |

English by default, French as a setting. Every change runs an archvow command, after **Confirm and run**.

## The repository

| Folder | What | Built with |
|---|---|---|
| `intellij/` | the plugin for IntelliJ IDEA, PyCharm and the other JetBrains IDEs (2024.3 and later): the archvow workspace and the `.vow` language, in one plugin | Gradle, JDK 21 |
| `vscode/` | the extension for VS Code (1.90 and later): the same workspace - Overview, Features, Commands and AI skills in the sidebar, what is chosen in one editor tab - `archvow check` in the Problems view, the `.vow` language | npm, Node 20+ (22 to publish) |
| `contract/` | what both plugins and the archvow CLI agree on: the archvow wheel the plugins are tested with, the sample vows, the CLI's JSON, the `.vow` reader's tokens and problems, what both IDEs read of the examples | `contract/tools/regenerate.py`, `./gradlew contractDump` |
| `wiki/` | how the plugins are built, for whoever maintains them: every file, every flow, and why (French; English in `wiki/en/`), checked against the code by `npm test` | - |
| `docs/` | design notes and [Marketplace CI/CD](docs/marketplace-publishing.md) | - |
| `.github/` | CI (`workflows/ci.yml`), the archvow canary, the release-version script | GitHub Actions |

## Build and test

```bash
./build.sh            # both, after their tests -> dist/archvow-intellij-0.25.2.zip, dist/archvow-0.25.2.vsix
./build.sh intellij   # or one of them
./build.sh vscode
```

| | VS Code (`vscode/`) | IntelliJ (`intellij/`) |
|---|---|---|
| Tests | `npm test`: the extension's tests, the webview regressions, the wiki against the code | `./gradlew check`: the `.vow` reader (`coreTest`) and the workspace's plain classes (`workspaceTest`) |
| Against a real archvow | `ARCHVOW_EXECUTABLE=<archvow> ARCHVOW_LIVE=required npm test` | `./gradlew runIde`: a sandbox IDE with the plugin |
| Package | `npm run package` -> `dist/archvow-<version>.vsix` | `./gradlew buildPlugin` -> `build/distributions/archvow-intellij-<version>.zip` |

The IntelliJ build downloads Gradle and the IntelliJ Platform it compiles against (`platformVersion` in
`intellij/gradle.properties`); behind a proxy or with no outside source, see [intellij/README.md](intellij/README.md).
When archvow changes, the contract is recorded again from its wheel: [contract/README.md](contract/README.md).

## Continuous integration and publishing

GitHub Actions (`.github/workflows/ci.yml`) runs both plugins' tests, regenerates the contract with the
archvow kept in `contract/archvow/` (the 2.1.1 wheel, its examples and rules) and requires it unchanged,
runs the VS Code tests against that archvow, then opens the built `.vsix` and `.zip` as the IDEs do. The
page [Tests](wiki/en/13-tests.md) says what each job checks.

After a successful `main` build, CI publishes stable plugins to both Marketplaces. Pushes to
an optional `beta` branch publish VS Code pre-releases and JetBrains `beta` channel builds.
Publisher setup, GitHub secrets, channel-specific versioning, and retry behavior are documented in
[Marketplace CI/CD](docs/marketplace-publishing.md). The Marketplace texts are the VS Code page
([vscode/README.md](vscode/README.md)) and the `<description>` of `intellij/.../META-INF/plugin.xml`: change
both together.

## The rules both plugins follow

- **The same layout in both editors.** The sidebar (IntelliJ: the Archvow tool window, left; VS Code: the
  activity bar) chooses: Overview (a project summary), Features (the tree: by feature - what the product does,
  `shared` last - or by layer), Commands (the list), AI skills. ONE editor tab shows what
  was chosen (a feature, a component, a command's form, a result) and is reused for the next choice.
  A component shows its head over the whole width, then, for Decisions, Questions, Safety and
  Assumptions, the items as cards on the left and the selected one's answer panel on the right (under
  the cards in a narrow tab); it opens on its first open item.
- **The same Overview in both editors** (0.25): First steps for a new project, Next and Check named with
  More for the others, the Architecture card counting its own items (decisions, questions, tasks), the
  Checks card with the first finding, Packs. What both read of archvow's JSON is checked against one file,
  `contract/overview/expected.json`.
- **The CLI is the only writer.** The state is the vows (`.archvow/project.vow`, `.archvow/features/**/*.vow`). A plugin reads what the
  CLI prints (`workspace --json`, `show`, `next`, `check --json`) and runs an archvow command, after a
  confirmation that shows it, for every change. One reading of an archvow rule is still in both workspaces (a high or
  critical hazard without an invariant shows as "NEEDS INVARIANT", ARCH030): moving it into archvow's
  JSON is item 3 of `docs/analyse-intellij.md`.
- **The CLI runs with no shell**, in the project folder, one command at a time, its JSON read from stdout
  only, stopped after a time-out. archvow is looked for in the IDE's Python interpreter, the settings' Python,
  the folder's `.venv`, the settings' executable, then where pip and pipx install it - an IDE started from the
  Dock does not see the shell's PATH ([Running archvow](wiki/en/05-running-archvow.md)).
- **Same functions, not same code.** IntelliJ is Swing (one panel class in two roles: navigation and
  editor tab); VS Code is one web page in two places, the sidebar and the editor tab (the page asks, the extension checks the command against the grammar and runs it). They share the
  vocabulary (English and French), archvow's icon (#1A876B), one keyword colour per depth in vows, and
  the `contract/`. VS Code adds what a web page cannot give: `archvow check` in the Problems view, a
  Python file checked on save.
- **One `.vow` reader, written twice.** The Java reader (`intellij/.../org/archvow/vow/core`) is the
  reference; the TypeScript one (`vscode/src/vow`) must give the same tokens, problems and links on
  every case of `contract/vow-reader/expected.json`.
- **A plugin says when the installed archvow does not speak its contract**, rather than misreading it:
  archvow says its contract number (`archvow version --json`, MAJOR.MINOR); the same MAJOR and a MINOR at
  least the plugin's is accepted, so an archvow release that keeps the contract needs no plugin release.
- **No outside source at run time.** Neither plugin downloads anything on its own, runs no AI model and sends
  nothing anywhere; for a team, the `.zip` and the `.vsix` go to an internal plugin repository (JetBrains
  `updatePlugins.xml`, a self-hosted Open VSX) or a share. When archvow is missing or does not speak the
  contract, **Install archvow 2.1.1** shows the pip command (into the project's venv, from pip's own
  configuration, your company's index or a wheel: the setting *Install archvow from*) and runs it only after
  **Confirm and run** - never at startup.

## Versions

Both plugins have the same version (0.25.2), their own, not archvow's. They read `.vow` format 1, speak
archvow's contract 2.1 (`contract/cli/contract.txt`) and are tested with, and install, the archvow CLI
2.1.1 (`contract/cli/version.txt`, the version the contract was recorded from); each says when the
installed archvow does not speak that contract (archvow keeps no backward compatibility). What changed:
[VS Code](vscode/CHANGELOG.md), [IntelliJ](intellij/CHANGELOG.md).
