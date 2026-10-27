# 13. Tests

> 🇫🇷 [Version française](/13-tests) · English version

Both plugins' tests run **without an IDE** and **without archvow installed**: they read the contract
(`contract/`), and a fake `archvow` when something must be run. `./build.sh` builds a plugin only after
its tests.

## IntelliJ: `./gradlew check`

No test framework: each test is a `main()` that throws an exception on the first failure, and Gradle
runs them with `JavaExec` (`intellij/build.gradle.kts`). Gradle's `test` task is turned off.

| Task | Class | What it checks |
|---|---|---|
| `coreTest` | `CoreTests` (`org.archvow.vow.core`) | the `.vow` reader on every vow of `contract/vows`: the scanner covers all the text, restarts at any token, one colour per depth; vows broken on purpose give the right problems |
| `workspaceTest` | `WorkspaceTests` (`org.archvow.idea`), with `java.awt.headless=true` | the 34 regression classes: everything that does not need a running IntelliJ |
| `contractDump` (not in `check`) | `DumpContract` | writes `contract/vow-reader/expected.json`, the TypeScript reader's reference |

The panel itself (`ArchvowPanel`) needs a running IntelliJ; that is why **the logic is taken out into
small classes that hold no platform state** (`ArchvowSplitView`, `ArchvowItemCards`,
`ArchvowCommandPrefill`, `ArchvowWsl`, `ArchvowHtml`...), tested one by one. A regression is named after
what it protects:

| Regression | Protects |
|---|---|
| `SplitViewRegression`, `AnswerEditorRegression` | the cards and the answer panel, the first open item, "Nothing to display." |
| `ArchitectureViewRegression` | Component, State, Pending always visible at any width |
| `CommandPrefillRegression`, `InlineCommandRegression`, `LaunchCommandRegression` | what a form receives, the exact command line |
| `WslRegression` | `wsl.exe -d ... --cd ... --exec`, the `\\wsl.localhost\...` paths |
| `TextDialogRegression` | a dialog's size, Copy command |
| `TextSizeRegression` | no `px` and no `<h1>`-`<h6>` in the HTML shown, the branch advice over several lines |
| `ToolWindowLayoutRegression`, `ResponsiveOverflowRegression`, `CompactViewsRegression` | narrow screens |
| `VersionRegression` | "archvow 1.17 is not the known version" |

A new regression is added in `WorkspaceTests.main` (one `run(...)` line): a class not listed there does
not run.

### Without access to the JetBrains repositories

`./gradlew` downloads the IntelliJ platform. On a machine that cannot reach them, `org.archvow.idea` and
`org.archvow.vow.core` can still be compiled with `javac` against **stand-ins**: small Java classes with
the platform's names (`com.intellij.openapi.project.Project`, `com.intellij.ui.components.JBList`...)
that do nothing, plus Gson. The headless tests then run as they are, and the panel can be shown in a
plain `JFrame` for a screenshot. These stand-ins are **not** in the repository (they would follow the
platform badly): they serve to check a change, never to ship. Before a release, `./gradlew check
buildPlugin` and a try in `runIde` remain mandatory.

`intellij/tools/verify_built_plugin.py` checks the built `.zip`: the plugin's id and the `since-build`
that Gradle rewrites in `META-INF/plugin.xml`.

## VS Code: `npm test`

`npm test` compiles (`scripts/sync-contract.js` first copies `contract/cli` into `resources/archvow`),
then runs `node --test` on `out/test/*.test.js`. No VS Code: `vscode` is not imported by the modules
tested, the page is read as text.

| File | What it checks |
|---|---|
| `cli.test.ts` | the command line, stdout and stderr apart, stdin for an unsaved file, one command at a time, the time-out, archvow missing, WSL; with a fake `archvow` written for the test |
| `vowReader.test.ts` | the TypeScript reader gives **exactly** `contract/vow-reader/expected.json` |
| `grammar.test.ts` | one colour per depth in the TextMate grammar; the prefill of Edit spec |
| `model.test.ts` | the recorded JSON of the three examples can be read |
| `workspace.test.ts` | form -> command line, prefill, in-place actions, documented fixes, result formatting |
| `layout.test.ts` | the page: one tab, the logo, the cards, the architecture table, Back |
| `wiki.test.ts` | the wiki follows the code (below) |

The page's behaviour (a click, a width) is checked by hand with F5, or in a headless browser that loads
`media/workspace.js` with a fake `acquireVsCodeApi` and messages drawn from the contract; like the
IntelliJ stand-ins, this bench is not in the repository.

## The wiki test

`vscode/src/test/wiki.test.ts` fails `npm test` when the wiki no longer follows the code:

- every French page has its English twin in `wiki/en/`, and the reverse;
- every `/page` (or `/en/page`) link leads to a page that exists;
- every path between backticks that holds a `/` and starts with a folder of the repository
  (`intellij/`, `vscode/`, `contract/`, `docs/`, `wiki/`) exists;
- every class named `Archvow...` or `Vow...` exists in Java (a `.java` file) or in the VS Code code;
- every function quoted with its parentheses (like `planForm()`) appears in the code of one of the two
  plugins.

A rename forgotten in the wiki therefore breaks the test, as in archvow
(`test_the_wiki_follows_the_code`).

## Before every commit

```bash
(cd intellij && ./gradlew check)
(cd vscode && npm test)
```

And before a release, the list on the page [Releasing](/en/14-release).
