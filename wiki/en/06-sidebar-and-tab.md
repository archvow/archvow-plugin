# 6. The sidebar and the tab

> 🇫🇷 [Version française](/06-sidebar-and-tab) · English version

Both plugins have the same layout: **the sidebar chooses, one single editor tab shows**. Overview,
Components (the tree) and Commands (the list) are on the left; what is chosen there (a component, a
command's form, the result of an Overview command) opens in the tab *archvow: clean_orders*, reused for
the next choice. Never one tab per component.

## IntelliJ: one class, two roles

`ArchvowPanel` builds the whole workspace and shows what belongs to its role (`ArchvowPanel.Role`):

| Role | Where | What it shows | Created by |
|---|---|---|---|
| `NAVIGATION` | the *Archvow* tool window, on the left | Overview, the component tree, the command list | `ArchvowToolWindowFactory` (and stored in the project under the `PANEL` key) |
| `DETAIL` | the *archvow* tab | the result (card 0), the component (card 1), the command form (card 2); the tab bar hidden | `ArchvowMainFileEditor`, when the tab opens |

**The single tab**: `ArchvowEditorTab` is a virtual file (`LightVirtualFile`), one per project.
`ArchvowEditorProvider` accepts it and hides the default text editor (`HIDE_DEFAULT_EDITOR`); it opens
an `ArchvowMainFileEditor`, which holds an `ArchvowPanel` in the `DETAIL` role.
`ArchvowEditorTab.panel(project)` opens the tab if it is closed (without taking the focus away from the
tool window); `openPanel(project)` never opens it. The title (*archvow: clean_orders*) and the icon
come from `ArchvowEditorTabPresentation`, kept up to date by `ArchvowEditorTab.setTitle`.

**Who reads the project**: the tool window. `reload` in the tab delegates to the tool window; it reads
(`show`, `check`, `next`, each component) then passes what it read to the tab (`pushToDetail` ->
`adoptFrom`). `adoptFrom` redraws the component shown only if its JSON changed (someone may be typing
an answer). On its own, without a tool window, the tab reads by itself.

**Who follows whom**: a click in the tool window is routed to the tab (`ArchvowEditorTab.showComponent`,
`showCommand`, `runFromOverview`). When the tab changes on its own (a *Uses* link, Clarify, Back),
`afterSelectionChange` updates its title and calls `follow` on the tool window, which selects the same
component without sending a click back (the `syncing` flag).

## VS Code: one page, two roles

`vscode/media/workspace.js` is one page, loaded twice with `window.ARCHVOW_ROLE`:

| Role | Where | Created by |
|---|---|---|
| `nav` | the *archvow.workspace* view of the activity bar | `registerWebviewViewProvider` in `vscode/src/extension.ts`, then `ArchvowProject.attachView` |
| `editor` | the *webview panel* `archvow.detail` | `ArchvowProject.editor()`, on demand; `retainContextWhenHidden` |

The page runs nothing: it sends messages (`postMessage`) and the extension (`workspace.ts`, method
`receive`) decides. The extension talks to the page through:

| Method | To whom |
|---|---|
| `toEditor` | the tab; opens it if it is closed; queues the messages until the page has said `ready` |
| `toOpenEditor` | the tab only if it is open (a result it is waiting for) |
| `toNav` | the sidebar |
| `reply` | whoever asked |
| `publish` | both: `{type: 'state'}`, everything archvow said (`State`) |

On load, each page sends `ready`; the extension answers `init` (the grammar, the catalog, the rules, the
component types) then `state`. The tab's page sends `title` when what it shows changes: that is the
tab's title. Security: a strict Content-Security-Policy (`html()`), a *nonce* per script, nothing
loaded from outside.

## The Components tab (0.15.0)

The tree draws what each node is (`contract/forms/icons.json`): the system, each layer, each component's type
(schema, reader, writer, transformation, job, entry point...) in its status colour, and the Commands tab each
group and each command by its verb. **Add component**, beside All / Pending / Resolved, opens the
`component add` form; on a layer, **+** (VS Code) or a right-click (IntelliJ, `installLayerMenu()`) lists the
types of that layer (`addableByLayer()`, `ArchvowFilePlan`) and opens the form with the type chosen. Under All,
the template's empty layers show too ("No component yet"): the first job or entry point has a place to go. A
button in the tree rather than one more command: adding happens where the architecture is read, at its level.

## Back

A command opened **from a component** (Edit spec, + Question, + Rule, Accept...) shows
**← clean_orders** in the tab's header; it goes back to the component, on the view you were on
(Safety...). A choice made in the sidebar clears it: there is no longer a component you came from.

- IntelliJ: `openCommand` keeps `backComponent` and `backView` when the tab shows a component;
  `showCommand` (called by the tool window) sets `fromToolWindow`, which clears it; `goBack`.
- VS Code: `post()` arms `ui.backArmed` when the page asks for a command from a component; the
  `openCommand` message coming back keeps it (`ui.back`), `select` clears it; the `back` action sends
  `show` again.

## When a vow changes on disk

Whether it changes through a command in a terminal, an AI agent or a `git pull`: the plugin reads again.
`ArchvowVowWatcher` listens to the platform's file changes and groups them (one reread `QUIET_MS` =
800 ms after the last one, and again later if the plugin was busy). VS Code: a `FileSystemWatcher` on
`.archvow/**/*.vow`, then `refreshSoon` (same delay).

## At start-up

IntelliJ: `ArchvowOpenOnStartup` shows the tool window when the project has a `.archvow/spec.vow`,
nothing for other projects. VS Code: the extension activates on `workspaceContains:.archvow/spec.vow`
or when a `.vow` is opened; in an untrusted folder, only the `.vow` language works.
