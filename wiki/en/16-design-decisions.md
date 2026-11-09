# 16. Design decisions and limits

> 🇫🇷 [Version française](/16-design-decisions) · English version

Why the code is the way it is. Read before "simplifying" anything.

## Decisions

| Decision | Why | Consequence in the code |
|---|---|---|
| **The CLI is the only writer** | one source of truth, validated and stamped (who, when) by archvow; a plugin that wrote a vow would have its own rules | the plugins read `show`, `next`, `check --json` and run a command for every change; `VowEditorNotification` and the VS Code banner say which command changes an open vow |
| **A confirmation that shows the exact command** | the person sees what will change the spec, and can redo it in a terminal | `isMutating()`, `preview()`, `ArchvowConfirmPanel`; `--yes` is added only after the confirmation, for a removal |
| **No shell, one command at a time, a time-out** | an answer may hold `;`, `$()`, quotes; two writes at once would trip over each other; a stuck command must not block the IDE | `ArchvowProcessCommand`, `ArchvowCli`; `vscode/src/cli.ts` (`spawnOnce`, the queue); only stdout is read as JSON |
| **The same layout in both IDEs** | a team has both; a person who switches editors must not relearn anything | a sidebar that chooses, **one** tab that shows; the same views, the same words |
| **Same functions, not same code** | Swing and a web page have nothing in common; a bridge (a web page inside IntelliJ) would add a layer to maintain in both | `ArchvowPanel` in Swing; `media/workspace.js` in plain JavaScript; the TypeScript files name the Java class they follow |
| **Forms drawn from archvow's parser** | forty commands written by hand would be wrong at the next version | `contract/cli/commands.json`, `ArchvowGrammar`, `commandForm()`; a new option is there with no code |
| **A contract recorded from the real archvow** | test without archvow installed, and see in a diff what a version changes | `contract/`, `regenerate.py`; the tests read the recorded JSON |
| **A `.vow` reader that reads the format, not a keyword list** | a new archvow or company keyword must not require a plugin | `VowScanner`, `VowDocument`; colours by depth; the meaning stays with `archvow check` |
| **The Java is the reader's reference, the TypeScript follows it** | two readers written apart would differ on edge cases | `DumpContract` -> `contract/vow-reader/expected.json` -> `vowReader.test.ts`, to the character |
| **The plugin does not install archvow** | archvow is a Python tool of the project (its environment, its version, the company's index); a plugin that ran `pip` would choose for the team | archvow not found: a message that says what was tried and what to set (`ArchvowCli.NotFound`); a guided, confirmed installation is planned for 0.12.0 |
| **The IDE's interpreter first** | archvow is installed in the project's environment, which the IDE already knows; looking in the `PATH` often found another one | `ArchvowInterpreter`, `readInterpreter()`; then the settings' Python, then the executable; the first that answers `--version` wins |
| **WSL through `wsl.exe`, not through the `\\wsl.localhost` path** | Windows cannot start a Linux Python by its network path | `ArchvowWsl` and the WSL functions of `vscode/src/cli.ts`: `wsl.exe -d <distribution> --cd <folder> --exec ...`, paths translated |
| **Nothing downloaded at run time** | supply chain: critical projects have no outside access | no CDN in the page (CSP with a nonce, `default-src 'none'`), the init catalog bundled (`ArchvowInitCatalog`), icons drawn in code |
| **Install archvow guided, never automatic** (0.12.0) | the wrong Python or venv, an internal index or a wheel instead of PyPI, a version that must match (no backward compatibility): a person decides | the pip command shown, then run only after Confirm and run; the project's venv first, never a bare `python`; pip's own configuration unless *Install archvow from* says otherwise (`ArchvowInstall`, `vscode/src/install.ts`) |
| **A diagnostic's text is never run** | a message is data; a project file could slip a command into it | `ArchvowIssueAction`, `issueFix()` recognise only documented forms and open a form |
| **Plain Swing, sizes relative to the IDE** | a plugin must look like the IDE, in every theme and font size | no UI library; `ArchvowHtml` in points; a single coloured button per panel |
| **The logic moves out of the panel** | the panel needs a running IntelliJ; a small class is tested in one line | `ArchvowSplitView`, `ArchvowItemCards`, `ArchvowCommandPrefill`, `ArchvowWsl`... and a regression each |
| **Tests without a framework** | they run anywhere, even without the JetBrains repositories or JUnit | one `main()` per regression, `WorkspaceTests`; `node:test` on the VS Code side |
| **A version number of their own, one archvow contract** (0.20.0) | archvow keeps no compatibility; reading two JSON formats would double the code; but one plugin release per archvow release is waste when the JSON did not change | `contract/cli/contract.txt`, `contract/cli/version.txt`, `ArchvowVersion`, `contractWarning()`; the plugin accepts the same MAJOR and a MINOR at least its own, and installs the tested archvow |
| **The wiki lives with the code** | internal documentation that is not kept up to date misleads | `wiki/` in the repository, same commit as the code, `wiki.test.ts` |

## What the plugins do not do, on purpose

| Not done | Why |
|---|---|
| editing a vow directly (a form that writes the file) | the CLI is the only writer |
| running a command without confirmation, even a "safe" one | a single simple rule: what changes the spec is confirmed |
| guessing a value to prefill a field | a prefilled field reads as an answer already given; only what is known is filled |
| running `specify` | it asks its questions in a terminal; the tab opens the first open item instead |
| keeping a copy of archvow's rules | they change with each version; a code's explanation comes from the contract's `rules.md` |

## Known limits

| Limit | Where it stands |
|---|---|
| IntelliJ does not underline `check` violations in the Python editor | item 1 of `docs/analyse-intellij.md` (an `ExternalAnnotator`); VS Code does it |
| one archvow rule is still read in the plugins (a high or critical hazard without an invariant, ARCH030) | item 3 of `docs/analyse-intellij.md`: put it in archvow's JSON |
| `ArchvowPanel` is about 4,000 lines | item 5 of `docs/analyse-intellij.md`: move the state out, then one class per view; meanwhile, each new piece of logic goes into a small class |
| the IntelliJ panel and the VS Code page are tested by hand only | item 6 of `docs/analyse-intellij.md`; `runIde` and F5 before each release |
| archvow must be installed separately | guided installation planned for 0.12.0 |
| one archvow minor version at a time | intended (see above): one plugin version per archvow version |
