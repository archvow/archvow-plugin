# 17. Glossary

> 🇫🇷 [Version française](/17-glossary) · English version

archvow's words (vow, component, decision, hazard, invariant...) are explained in the glossary of
archvow's wiki; here are the plugins' own, with where to find them in the code.

| Term | Meaning | Code |
|---|---|---|
| **sidebar** (navigation) | what chooses: Overview, Components, Commands. IntelliJ: the Archvow tool window on the left; VS Code: the activity bar | `ArchvowPanel` in the `NAVIGATION` role, `ArchvowNavigationTabs`; the page in the `nav` role |
| **the tab** (detail) | **one single** editor tab that shows what is chosen, reused for the next choice | `ArchvowEditorTab`, `ArchvowEditorProvider`, `ArchvowPanel` in the `DETAIL` role; the *webview panel*, the page in the `editor` role |
| **Overview** | the project's screen: its state, the architecture, Check & Gates, pending and resolved work, the project commands | page [Overview](/en/07-overview) |
| **view** (of a component) | Decisions, Questions, Safety, Assumptions, Rules, Tasks, Spec, Other / Project | `addComponentView`, the page's `VIEWS` constant |
| **card** | an item of a view (a decision, a question...) on one line, on the left | `ArchvowItemCards`, `cards()` |
| **answer panel** | the chosen card, on the right (or below): the whole question, the field, the actions | `ArchvowSplitView`, `split()` |
| **first open item** | the card chosen when a component opens: the first item without an answer, otherwise the first | `ArchvowItemCards.firstOpen`, `current()` |
| **pending** | what needs a person: a decision without an answer, an unresolved safety question, a proposed assumption... | `ArchvowSafetyState`, `viewCounts()` |
| **in-place action** (inline) | a view's button that runs a command for the chosen card (Save answer, Declare hazard) | `ArchvowInlineCommands`, the `inline` object, `inlineAction` |
| **confirmation** | the exact command, shown under the button, before it goes | `ArchvowConfirmPanel`, `requestConfirmation`; `confirm` in the extension |
| **read command** | a command that changes nothing and goes without confirmation (`show`, `next`, `check`...) | `READ_ONLY` |
| **destructive command** | a removal: confirmed, then run with `--yes` | `DESTRUCTIVE` |
| **grammar** | all of archvow's commands and their parameters, drawn from its parser | `contract/cli/commands.json`, `ArchvowGrammar`, `findCommand()` |
| **prefill** | a form's fields filled with what is known, never guessed | `ArchvowCommandPrefill`, `prefill()` |
| **Back** | in the tab, going back to the component a command was opened from, on its view | `goBack`, `backComponent`; `back` in the page |
| **contract** | what the plugins and the CLI share: sample vows, recorded JSON, grammar, catalog, meaning of the codes, version | `contract/`, page [The contract](/en/04-contract) |
| **known version** | the archvow the plugins are tested with and install (`contract/cli/version.txt`) | `ArchvowVersion.KNOWN`, `knownVersion()` |
| **contract** (0.20.0) | archvow's number for what it prints for tools (MAJOR.MINOR, `archvow version --json`); the plugins accept the same MAJOR and a MINOR at least theirs | `ArchvowVersion.CONTRACT`, `contractWarning()` |
| **error envelope** (0.20.0) | archvow's error as an object on stdout with `--json`: `{"error": {type, message, hint, exit}}` | `ArchvowOutput.error`, `errorOf()` |
| **candidate** | a way to run archvow: the IDE's interpreter, the settings' Python, the executable; the first that answers `--version` is kept | `ArchvowProcessCommand.Candidate`, `candidates()` |
| **WSL** | archvow installed in Linux under Windows, run through `wsl.exe` with Linux paths | `ArchvowWsl` |
| **snapshot** | a component's last view read (`show <component> --json`, one of `workspace --json`'s `components` since 0.21.0), shown at once while it is read again | `componentSnapshots`; `snapshots` in the extension |
| **`.vow` reader** | the code that cuts a vow into tokens, reads its lines and its problems, without archvow | `org.archvow.vow.core`, `vscode/src/vow` |
| **depth** | a keyword's indentation level (0, 1, 2, 3 and more), which gives its colour | `VowTokenKind` (`KEY_0`...) |
| **stand-in** | an empty class with the name of an IntelliJ platform class, to compile without it; outside the repository | page [Tests](/en/13-tests) |
| **regression** | an IntelliJ test: a class with a `main()`, listed in `WorkspaceTests` | `intellij/src/test/java/org/archvow/idea` |
