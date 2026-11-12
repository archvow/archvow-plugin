# archvow for IntelliJ IDEA

One plugin for archvow in IntelliJ IDEA and the other JetBrains IDEs (PyCharm included), 2024.3 and
later. It has two parts:

- **the workspace** (`org.archvow.idea`): the components of the project, their decisions, questions,
  safety, rules, tasks and checks, read from the archvow CLI (`show`, `next`, `check --json`). Every
  change runs an archvow command, after a confirmation: the plugin never writes a vow itself.
- **the `.vow` language** (`org.archvow.vow`): editor support for `.archvow/project.vow`,
  `.archvow/features/<feature>/<feature>.vow`, `.archvow/features/<feature>/components/<component>.vow` and
  each feature's `proposals.vow`.

It replaces the two former plugins, `archvow-intellij` (the workspace, id `org.archvow.intellij`) and
`vow-plugin-intellij` (the language, id `com.archvow.intellij`). The id is the workspace's, so an installed
workspace updates in place. **Uninstall the former vow plugin** (Settings > Plugins > archvow, the one
with version 0.4.x) before installing this one: both declare the `.vow` file type.

## The workspace

| Where | What |
|---|---|
| Tool window **Archvow** (left; **Tools > Show Archvow**) | Overview, Features (the tree: by feature - what the product does, `shared` last - or by layer; **Add feature**), Commands (the list), and AI skills (fourth tab): what you choose there opens in **one** editor tab, *archvow: clean_orders*, reused for the next component or command (close it, and the next click opens it again) |
| Overview | compact project status with pending/resolved outline pills, one complete Check, Next, Doctor, Audit and Project vow as icon-only actions with tooltips (Budget / Diff / Review in More), an architecture summary with a link to Components, and a **Packs** row (0.23.0, archvow 1.26: each pack of the project, what it brings, changed by hand or not, with Update / Remove / Check, Add a pack and New pack); no duplicate component table, brief folders, command reference, or base branch; header search, filter, reload and logs are compact |
| A feature (the editor tab, 0.24.0; its files as a tree, 0.24.1) | what the product does in it, how far it is (components verified), the features it uses and that use it - worked out from the components' uses -, its components by layer (each a link) and what is open in it; **Add component**, **Open feature doc** (`archvow doc <feature>`, the Markdown page, ignored by Git), **Copy AI prompt** (`archvow prompt --feature`), **Next steps**, **Rename…** |
| Components (the editor tab) | per component, its head (open items, type · layer · module · ticket, one progress bar - decisions made, safety answered, tasks done, examples tested, green when complete -, description, *Drawn from* its brief - a link that opens it at that heading -, Uses · Used by, examples) and, for decisions, questions, safety and assumptions, the items as cards on the left and the answer panel on the right, opened on the first open item: decisions (answer, propose, accept), questions, safety (hazards, invariants), assumptions, rules, columns and tasks, the task the AI implements (`archvow prompt`) and the full vow; Open Code |
| The AI skills (0.16.0, 0.17.x) | **✦ AI ▾**, the first action of a component and an **AI skills** fourth navigation tab: Draft from a brief, Find missing questions, Find risks, Implement, Review changes, then Copy the task for another AI - a launch form says what the AI reads, may add and what you still decide, and copies the request for your assistant. Its menu marks the suggested step. **+ Add ▾**: a question, a rule, an assumption. What the AI adds sits in Decisions, Safety and Rules with an origin tag (Template, Project, Brief, AI) and **To review** until you keep it (**Keep**, **Keep, answer later**, **Remove**); a small line under the views shows **All · Open · To review**. Save answer moves to the next open item. Brief operations remain accessible in Commands. Previously, Overview › **Briefs**: each brief section used, not used yet (**Draft with AI…**) or changed since |
| Right-click in an editor, or on its tab | **Show Archvow for This File**: `archvow show <file>:<line>` - the component that owns the file opens in the editor tab; its details start with *Uses ... · Used by ...*, each name a link |
| Commands (the editor tab) | a form for each archvow command, built from the CLI's own parser; read-only commands run at once, changes ask for a confirmation. The `--from` field lists the files of the brief folders and their headings (`specs/001-orders/plan.md#data-model`); when archvow refuses a file outside them, **Add this folder (architect)** under the result opens `init --force` prefilled |
| Settings > Tools > Archvow | where archvow is looked for, in this order, the first that answers `--version` running the commands: the project's Python interpreter in the IDE (Settings > Project > Python Interpreter; on by default), the Python that has archvow installed (`python3 -m archvow`), the archvow executable. On Windows a Python in WSL (`\\wsl.localhost\Ubuntu\home\you\.venv\bin\python3`, PyCharm's WSL interpreter, `wsl://<distro>/...`) runs through `wsl.exe --exec` with Linux paths. Test shows which one answered, or what each said. The time-out, the language (English / French / the IDE's). *Install archvow from*: where **Install archvow** takes it from (empty = pip's own configuration, your company's index URL, a `.whl` file or a folder of wheels) |

The archvow CLI must be installed (`archvow --version` in a terminal). The plugin runs it in the project
folder, never through a shell, one command at a time. When archvow does not start, or does not
speak the plugin's contract, the welcome card (or the version notification) offers **Install archvow 2.1.0**: it shows
`<python> -m pip install archvow==2.1.0` - the project's `.venv` (or `venv`) first, then the IDE's
interpreter, then the Python of the settings, never a bare `python` - and runs it only after **Confirm
and run**, no shell; on success the settings keep that Python. Nothing is installed at startup.

The brief folders (archvow 1.17): `briefs/` by default; an architect adds others with the init answer
`briefs` (e.g. `briefs,specs` for Spec Kit's `specs/`). An item drawn from a brief whose text changed
shows ARCH039 with **Accept (reviewed)** (`archvow brief accept`, confirmed) and **Review the brief**.

## The `.vow` language

| What | How |
|---|---|
| File type and icon | every `*.vow` file, with archvow's icon (green; a lighter green in dark themes) |
| Highlighting | the first line; keywords, one colour per depth - the general rule of the format: not indented (`name`, `type`, `uses`, `decision`, `hazard`), indented once (`question`, `answer`, `by`, `on`, `kind`, a column), twice, three times and deeper; ids (`H1`, `INV2`, `R1`), text in quotes, numbers, `true`/`false`/`null`, people (`@alice`, `alice@acme.com`), dates, lists, JSON, comments. Colours: Settings > Editor > Color Scheme > Vow |
| Errors as you read | what keeps archvow from reading the vow (indentation, tabs, quotes, lists, the first line, JSON), with its line; warnings for a key with no value, or a key repeated where only items repeat |
| Folding | every block, at any depth |
| Structure | the Structure tool window (Alt+7 / Cmd+7) shows the blocks and lines as indented |
| Comments | Ctrl+/ (Cmd+/) adds or removes `# ` |
| Ctrl+click (Cmd+click) | a component in `uses [...]` or `components [...]` opens its vow, wherever its feature is; a hazard in `prevents [H1, clean_orders.H2]` jumps to its line; the value of `module` opens the Python file |
| Banner | over each vow: which archvow command changes it |
| Markdown | ```` ```vow ```` blocks in `.md` files are highlighted the same way (the Markdown plugin injects the language named after the fence) |

The language knows the **format**, not a list of keywords: any keyword is accepted at any depth, so a
new keyword in archvow, or a company's own, needs no new plugin. What each keyword means is checked by
`archvow check`.

## Build

You need a JDK 21. IntelliJ's own runtime is one:

```bash
cd intellij
export JAVA_HOME="/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home"   # or IntelliJ IDEA CE.app, PyCharm.app...
./gradlew buildPlugin        # -> build/distributions/archvow-intellij-0.12.0.zip
./gradlew check              # coreTest (the .vow reader) + workspaceTest (the workspace's plain classes)
./gradlew runIde             # optional: a sandbox IntelliJ with the plugin, to try it
```

Or, from the repository root, `./build.sh` builds this plugin and the VS Code extension into `dist/`.

The first build downloads Gradle and the IntelliJ Platform it compiles against (`platformVersion` in
`gradle.properties`, 2024.3.5). Behind a company proxy, set it in `~/.gradle/gradle.properties`
(`systemProp.https.proxyHost=...`). For a build with no outside source, point
`distributionUrl` (gradle/wrapper/gradle-wrapper.properties) and the repositories at the company's
mirror. If that IntelliJ version cannot be downloaded, put your own version there (Help > About).

## Install

Settings > Plugins > gear icon > **Install Plugin from Disk...** > `build/distributions/archvow-intellij-0.12.0.zip`,
then restart. For a team: a company plugin repository (an `updatePlugins.xml` on an internal server,
Settings > Plugins > gear icon > Manage Plugin Repositories), so nobody downloads from outside.

## Check it once installed

- [ ] Settings > Plugins lists **one** archvow plugin, version 0.12.0.
- [ ] The Archvow tool window opens on the left on a project with `.archvow/`, lists its components, and
      Check shows its result in the editor tab and the diagnostics in Check & Gates.
- [ ] A click on a component opens the tab *archvow: <component>*; a click on another one shows it in the
      same tab (no second tab); a command in Commands opens its form in that tab too. If you installed an
      earlier version, the tool window may stay where it was: drag its icon to the left once.
- [ ] A component's vow shows the vow icon, the colours and the banner.
- [ ] `name`, `decision` (not indented), `question`, `by` (indented once) and `components` under a
      rule's `scope` (twice) have three different colours; Settings > Editor > Color Scheme > Vow >
      Keyword changes each depth.
- [ ] Indent a line by 3 spaces: an error with the line; undo: it goes.
- [ ] Fold a `hazard H1` block; the Structure tool window lists the blocks.
- [ ] Cmd+click on a name in `uses [...]`: its vow opens.
- [ ] In a Markdown file, a ```` ```vow ```` block is coloured like a `.vow` file.

## How it is built

`src/main/java/org/archvow/vow/core/` reads the `.vow` text in plain Java, with no IntelliJ class:
`VowScanner` (tokens for highlighting, restartable at any token), `VowDocument` (the tree of blocks and
the problems), `VowValues`, `VowJson`, `VowLinks`. It is tested on its own (`coreTest`): every token of
every sample vow, restarting anywhere, the problems with their messages. It was also compared with
archvow's Python reader on 3 000 random vows. The VS Code extension has the same reader in TypeScript;
`../contract/` holds what both must agree on (see the repository README). The rest of
`src/main/java/org/archvow/vow/` is the thin IntelliJ layer: file type, lexer, parser (one element per
line), highlighter, colour page, annotator, folding, structure view, references, banner.

`src/main/java/org/archvow/idea/` is the workspace: `ArchvowCli` runs the CLI (no shell, project folder,
time-out, cancel), `ArchvowGrammar` reads the command grammar (`src/main/resources/archvow/commands.json`,
exported from archvow's own parser by `tools/export_cli.py`), `ArchvowPanel` is the workspace, the
`Archvow*View` classes are its views. `src/main/resources/META-INF/plugin.xml` registers both parts.

The plugin has its own version (0.25.0); it reads `.vow` format 1, speaks archvow's contract 2.1
(`../contract/cli/contract.txt`) and is tested with the archvow CLI 2.0.1 (`../contract/cli/version.txt`); the
workspace says when the installed archvow does not speak that contract.
