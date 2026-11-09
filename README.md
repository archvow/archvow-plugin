# archvow-plugin

archvow's IDE plugins, in one repository, apart from archvow itself (archvow never knows an editor).

| Folder | What | Built with |
|---|---|---|
| `intellij/` | the plugin for IntelliJ IDEA, PyCharm and the other JetBrains IDEs: the archvow workspace and the `.vow` language, in one plugin | Gradle, JDK 21 |
| `vscode/` | the extension for VS Code: the same workspace: Overview, Components, Commands and AI skills in the sidebar, what is chosen in one editor tab, `archvow check` in the Problems view, the `.vow` language | npm, Node 20+ |
| `contract/` | what both plugins and the archvow CLI agree on: the sample vows, the CLI's JSON, the `.vow` reader's tokens and problems | `contract/tools/regenerate.py`, `./gradlew contractDump` |
| `wiki/` | how the plugins are built, for whoever maintains them: every file, every flow, and why (French; English in `wiki/en/`), checked against the code by `npm test` | - |

```bash
./build.sh            # both, after their tests -> dist/archvow-intellij-0.19.2.zip, dist/archvow-0.19.2.vsix
./build.sh intellij   # or one of them
./build.sh vscode
```

GitHub Actions (`.github/workflows/ci.yml`) runs both plugins' tests, regenerates the contract with the
archvow kept in `contract/archvow/` (the 1.19.0 wheel, its examples and rules) and requires it unchanged,
runs the VS Code tests against that archvow, then opens the built `.vsix` and `.zip` as the IDEs do. The
page [Tests](wiki/en/13-tests.md) says what each job checks.

After a successful `main` build, CI publishes stable plugins to both Marketplaces. Pushes to
an optional `beta` branch publish VS Code pre-releases and JetBrains `beta` channel builds.
Publisher setup, GitHub secrets, channel-specific versioning, and retry behavior are documented in
[Marketplace CI/CD](docs/marketplace-publishing.md).

## The rules both plugins follow

- **The same layout in both editors.** The sidebar (IntelliJ: the Archvow tool window, left; VS Code: the
  activity bar) chooses: Overview (a project summary), Components (the tree), Commands (the list), AI skills. ONE editor tab shows what
  was chosen (a component, a command's form, a result) and is reused for the next choice.
  A component shows its head over the whole width, then, for Decisions, Questions, Safety and
  Assumptions, the items as cards on the left and the selected one's answer panel on the right (under
  the cards in a narrow tab); it opens on its first open item.
- **The same workspace in both editors.** Overview (a compact architecture summary, pending and resolved pills, Next / Check / Doctor / Audit / Project vow, and a More menu for Budget / Diff / Review), Components (Decisions,
  Questions, Safety, Assumptions, Rules, Tasks, Spec, Other / Project, with their actions) and
  Commands (every archvow command as a form built from archvow's own parser, the init wizard built from
  `archvow catalog --json`). VS Code reproduces what the IntelliJ workspace does, rule by rule
  (`vscode/src/workItems.ts`, `grammar.ts`, `formatter.ts` name the Java class each one follows).
- **The CLI is the only writer.** The state is the vows (`.archvow/**/*.vow`). A plugin reads what the
  CLI prints (`show`, `next`, `check --json`) and runs an archvow command, after a confirmation that
  shows it, for every change. One reading of an archvow rule is still in both workspaces (a high or
  critical hazard without an invariant shows as "NEEDS INVARIANT", ARCH030): moving it into archvow's
  JSON is item 3 of `docs/analyse-intellij.md`.
- **The CLI runs with no shell**, in the project folder, one command at a time, its JSON read from stdout
  only, stopped after a time-out.
- **Same functions, not same code.** IntelliJ is Swing (one panel class in two roles: navigation and
  editor tab); VS Code is one web page in two places, the sidebar and the editor tab (the page asks, the extension checks the command against the grammar and runs it). They share the
  vocabulary (English and French), archvow's icon (#1A876B), one keyword colour per depth in vows, and
  the `contract/`. VS Code adds what a web page cannot give: `archvow check` in the Problems view, a
  Python file checked on save.
- **One `.vow` reader, written twice.** The Java reader (`intellij/.../org/archvow/vow/core`) is the
  reference; the TypeScript one (`vscode/src/vow`) must give the same tokens, problems and links on
  every case of `contract/vow-reader/expected.json`.
- **A plugin says when the installed archvow is outside the versions it knows**, rather than
  misreading it.
- **No outside source at run time.** Neither plugin downloads anything on its own; for a team, the `.zip`
  and the `.vsix` go to an internal plugin repository (JetBrains `updatePlugins.xml`, a self-hosted Open
  VSX) or a share. When archvow is missing or another major.minor, **Install archvow 1.19** shows the pip
  command (into the project's venv, from pip's own configuration, your company's index or a wheel: the
  setting *Install archvow from*) and runs it only after **Confirm and run** - never at startup.

## Versions

Both plugins have the same version (0.19.2), their own, not archvow's. They read `.vow` format 1 and run
the archvow CLI 1.22 (`contract/cli/version.txt`, the version the contract was recorded from); each says
when the installed archvow is another major.minor (archvow keeps no backward compatibility).
