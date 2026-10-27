# archvow-plugin

archvow's IDE plugins, in one repository, apart from archvow itself (archvow never knows an editor).

| Folder | What | Built with |
|---|---|---|
| `intellij/` | the plugin for IntelliJ IDEA, PyCharm and the other JetBrains IDEs: the archvow workspace and the `.vow` language, in one plugin | Gradle, JDK 21 |
| `vscode/` | the extension for VS Code: the same workspace: Overview, Components and Commands in the sidebar, what is chosen in one editor tab, `archvow check` in the Problems view, the `.vow` language | npm, Node 20+ |
| `contract/` | what both plugins and the archvow CLI agree on: the sample vows, the CLI's JSON, the `.vow` reader's tokens and problems | `contract/tools/regenerate.py`, `./gradlew contractDump` |

```bash
./build.sh            # both, after their tests -> dist/archvow-intellij-0.11.2.zip, dist/archvow-0.11.2.vsix
./build.sh intellij   # or one of them
./build.sh vscode
```

## The rules both plugins follow

- **The same layout in both editors.** The sidebar (IntelliJ: the Archvow tool window, left; VS Code: the
  activity bar) chooses: Overview, Components (the tree), Commands (the list). ONE editor tab shows what
  was chosen (a component, a command's form, a result) and is reused for the next choice.
  A component shows its head over the whole width, then, for Decisions, Questions, Safety and
  Assumptions, the items as cards on the left and the selected one's answer panel on the right (under
  the cards in a narrow tab); it opens on its first open item.
- **The same workspace in both editors.** Overview (architecture, Check & Gates, pending and resolved
  work, Check / Next / Full check / Doctor / Audit / Budget / Diff / Review), Components (Decisions,
  Questions, Safety, Assumptions, Rules, Tasks, Full vow, Other / Project, with their actions) and
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
- **No outside source at run time.** Neither plugin downloads anything; for a team, the `.zip` and the
  `.vsix` go to an internal plugin repository (JetBrains `updatePlugins.xml`, a self-hosted Open VSX)
  or a share.

## Versions

Both plugins have the same version (0.11.2), their own, not archvow's. They read `.vow` format 1 and run
the archvow CLI 1.16 (`contract/cli/version.txt`, the version the contract was recorded from); each says
when the installed archvow is another major.minor (archvow keeps no backward compatibility).
