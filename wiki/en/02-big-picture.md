# 2. Big picture

> 🇫🇷 [Version française](/02-big-picture) · English version

## The idea

archvow keeps a project's spec (`.archvow/**/*.vow`) and fails the build when the code does not follow
it. The plugins put that spec **inside the IDE**: you see a component, its open decisions and
questions, you answer them, you run a check, without leaving the editor. They know nothing archvow does
not tell them: they read what the CLI prints and run the CLI for every change.

## The rules both plugins follow

| Rule | Why | In the code |
|---|---|---|
| **The CLI is the only writer** | one source of truth, validated on every write, with who and when | no plugin writes a `.vow`; every change is an archvow command, after "Confirm and run" |
| **The same layout in both IDEs** | a team has both; what you learn in one holds in the other | the sidebar chooses (Overview, Components, Commands), **one** editor tab shows and is reused for the next choice |
| **Same functions, not same code** | Swing and a web page have nothing in common | VS Code follows the IntelliJ workspace rule by rule; `vscode/src/workItems.ts`, `vscode/src/grammar.ts`, `vscode/src/formatter.ts` name the Java class they follow |
| **One `.vow` reader, written twice** | both editors must show the same errors in the same place | the Java reader (`org.archvow.vow.core`) is the reference; the TypeScript one (`vscode/src/vow`) must give the same tokens on `contract/vow-reader/expected.json` |
| **No shell, one command at a time** | no injection, no two writes at once | `ArchvowCli` and `vscode/src/cli.ts`: an argument list, the project folder, a time-out |
| **The plugin says when it does not know the installed archvow** | archvow keeps no backward compatibility | `ArchvowVersion`, `versionWarning()` compare with `contract/cli/version.txt` |
| **No outside source at run time** | critical projects, supply chain | no plugin downloads anything; the `.zip` and the `.vsix` go through an internal repository |

## The map of the files

**IntelliJ** (`intellij/src/main/java/org/archvow/`):

| Role | Classes |
|---|---|
| Plugging into the IDE | `plugin.xml`, `ArchvowToolWindowFactory`, `ArchvowEditorProvider`, `ArchvowMainFileEditor`, `ArchvowEditorTab`, `ArchvowEditorTabPresentation`, `ArchvowOpenOnStartup`, `ShowArchvowAction`, `ShowFileArchvowAction`, `RefreshArchvowAction` |
| The workspace (both roles) | `ArchvowPanel` |
| Running the CLI | `ArchvowCli`, `ArchvowProcessCommand`, `ArchvowInterpreter`, `ArchvowWsl`, `ArchvowVersion`, `ArchvowRunLog`, `ArchvowSettings`, `ArchvowSettingsConfigurable` |
| Overview | `ArchvowArchitectureView`, `ArchvowChecksView`, `ArchvowResultFormatter`, `ArchvowRuleDocs`, `ArchvowBranchAdvice`, `ArchvowProjectReadiness` |
| A component | `ArchvowSplitView`, `ArchvowItemCards`, `ArchvowDecisionView`, `ArchvowSafetyView`, `ArchvowSafetyState`, `ArchvowDataView`, `ArchvowAnswerEditor`, `ArchvowConfirmPanel`, `ArchvowInlineCommands`, `ArchvowLinks`, `ArchvowVowViewer`, `ArchvowTaskPrompt` |
| Commands | `ArchvowGrammar`, `ArchvowCommandPrefill`, `ArchvowInitCatalog`, `ArchvowInitQuestions`, `ArchvowComponentTypes`, `ArchvowIssueAction` |
| Rereading when a vow changes | `ArchvowVowWatcher` |
| Look | `ArchvowMotion`, `ArchvowNavigationTabs`, `ArchvowCompactViews`, `ArchvowOverflowBar`, `ArchvowUiIcons`, `ArchvowStatusPalette`, `ArchvowHtml`, `ArchvowTextDialog`, `ArchvowWrapLabel`, `ArchvowI18n` |
| The `.vow` language | `org.archvow.vow` (plugged into the platform) and `org.archvow.vow.core` (plain Java, tested on its own) |

**VS Code** (`vscode/`):

| Role | Files |
|---|---|
| Plugging in the extension | `vscode/package.json` (contributions), `vscode/src/extension.ts` |
| The extension: state, CLI, messages | `vscode/src/workspace.ts`, `vscode/src/cli.ts` |
| The workspace's rules (without `vscode`) | `vscode/src/workItems.ts`, `vscode/src/projectState.ts`, `vscode/src/grammar.ts`, `vscode/src/formatter.ts`, `vscode/src/model.ts`, `vscode/src/i18n.ts` |
| The page (sidebar and tab) | `vscode/media/workspace.js`, `vscode/media/workspace.css` |
| The `.vow` language | `vscode/src/vowLanguage.ts`, `vscode/src/vow/scanner.ts`, `vscode/src/vow/document.ts`, `vscode/src/vow/values.ts`, `vscode/src/vow/links.ts`, `vscode/syntaxes/vow.tmLanguage.json` |

The files "without `vscode`" do not depend on the VS Code API: the tests load them in Node as they
are. On the IntelliJ side, the same idea: everything that can be a plain Java class is one
(`ArchvowSplitView`, `ArchvowItemCards`, `ArchvowWsl`...), to be tested without an IDE.

## What each plugin reads, and when

| When | Commands run | Where |
|---|---|---|
| Opening, Reload, a vow changing on disk | `show --json`, `check --json`, `next --json`, then `show <c> --json` for each component | `ArchvowPanel.reload`, `ArchvowProject.refresh` |
| A component chosen | `show <c> --json` | `loadComponent`, `reloadComponent` |
| A Python file saved (VS Code) | `check --file <f> --json`, the text on stdin | `checkFile` |
| An action | the action's archvow command, after a confirmation for a change | page [Commands](/en/09-commands) |
| The first command | `--version`, once per candidate until one answers | page [Running archvow](/en/05-running-archvow) |
