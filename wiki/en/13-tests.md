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
| `workspaceTest` | `WorkspaceTests` (`org.archvow.idea`), with `java.awt.headless=true` | the 37 regression classes: everything that does not need a running IntelliJ |
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
| `ChoicesRegression` | the command forms offer what exists (`ArchvowChoices`): on the three examples, in English and French, exactly what VS Code offers (`contract/choices/expected.json`), and the same comparison base |
| `FilePlanRegression` | the files a form announces (`ArchvowFilePlan`): exactly VS Code's on `contract/forms/file-plans.expected.json`; the layers to add to, Save to a file, the first commit, the icons (`ArchvowTreeIcons`), the answer tables (a bin per row, the parameters) |
| `AiSkillsRegression` | the AI view of a component (`ArchvowAiSkills`): exactly VS Code's on `contract/forms/ai-skills.expected.json` (origins, To review counts, suggestion, blocked skills); the requests each tool gets |
| `SkillFormRegression` | the launch form of a skill (`ArchvowSkillForm`): the request, Copy disabled without a brief, "Not yet"; Overview › Briefs (`ArchvowBriefs.statusHtml`); the cards' origin tags and the All / Open / To review filter |
| `GuidanceRegression` | every diagnostic says what to do (`ArchvowGuidance`): each documented code, and on each real message exactly the guidance of VS Code (`contract/guidance/expected.json`) |
| `InstallRegression` | Install archvow: the Python chosen, the pip line per source, WSL, pip only from a confirmation |
| `ProgressRegression` | the progress bar on the recorded `clean_orders` (9/9, 1/5, 0/2) |
| `BriefsRegression` | the brief folders, archvow's slugs, the `--from` choices, Drawn from, Add this folder, ARCH039 |

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
then runs `node --test` on every `out/test/*.test.js`, listed by `vscode/scripts/test.js` (Node 20 does not expand a glob given to `node --test`; `node scripts/test.js out/test/live.test.js` runs one file). No VS Code: `vscode` is not imported by the modules
tested, the page is read as text.

| File | What it checks |
|---|---|
| `cli.test.ts` | the command line, stdout and stderr apart, stdin for an unsaved file, one command at a time, the time-out, archvow missing, WSL; with a fake `archvow` written for the test |
| `vowReader.test.ts` | the TypeScript reader gives **exactly** `contract/vow-reader/expected.json` |
| `grammar.test.ts` | one colour per depth in the TextMate grammar; the prefill of Edit spec |
| `model.test.ts` | the recorded JSON of the three examples and of the briefs scenario can be read |
| `workspace.test.ts` | form -> command line, prefill, in-place actions, documented fixes, result formatting |
| `layout.test.ts` | the page: one tab, the logo, the cards, the architecture table, Back, the progress bar |
| `install.test.ts` | Install archvow: the source, the Python, the pip line, WSL, nothing without Confirm and run |
| `choices.test.ts` | what each form field offers (`choices.ts`): the components (Edit spec filled with what one has), the questions with their number, only the questions a person added for `question remove`, the types of the project's template and the kinds of a type, the hazards, invariants, rules, the branches; the base that exists (`bestBase`) and why one does not (`missingRef`); Edit spec sends only what changed; writes `contract/choices/expected.json` with `UPDATE_CHOICES=1` |
| `guidance.test.ts` | every diagnostic says what it means, what to do, who does it, whether it blocks and what then (`guidance.ts`): all the codes of `rules.md` and `rules.fr.md`, all the real messages of `contract/cli/messages.json`; the command a message names becomes a form that runs as it is or leaves only the person's part empty; writes `contract/guidance/expected.json` with `UPDATE_GUIDANCE=1` |
| `briefs.test.ts` | the brief folders, archvow's slugs, the `--from` choices, Drawn from, the refusal -> init form, ARCH039 (on `contract/cli/briefs`) |
| `filePlan.test.ts` | the Files block (`filePlan()`): component add with a group, a signature, an existing file, an unknown type; removals, rules and the cards they reach; Save to a file (`withPluginParameters()`, `previewWithOutput()`); the types per layer; an icon for every type, group and command; writes `contract/forms/file-plans.expected.json` with `UPDATE_FILE_PLANS=1` |
| `wiki.test.ts` | the wiki follows the code (below) |
| `release.test.ts` | before a release, nothing built: one version (`package.json`, `gradle.properties`, README, both changelogs), one archvow (`contract/cli/version.txt`, the pip requirement, the README), both builds package the same contract files, the command lists (`DESTRUCTIVE`, `READ_ONLY`, `LONG_TEXT`) identical in `grammar.ts` and `ArchvowPanel` and made of archvow's commands, every command that asks for confirmation gets `--yes`, `package.json` and `plugin.xml` name only commands, settings, classes and files that exist |
| `live.test.ts` | a real archvow, run through `ArchvowCli`: its version is the contract's, `catalog --json` is the recorded one, on archvow's three examples with their code every output is **exactly** the contract, read-only commands change no vow, `question add` then `question remove --yes` round-trip, an error is a sentence. Skipped without `ARCHVOW_PYTHON` (or `ARCHVOW_EXECUTABLE`) and `ARCHVOW_SOURCE`; `ARCHVOW_LIVE=required` makes the skip a failure |
| `package.test.ts` | the packages in `dist/`, opened as the IDEs open them (`zip.ts` reads them): the `.vsix` (manifest, version, every module, the contract inside, no source, test or map, every `require` packaged or built in) and the IntelliJ `.zip` (the jar's `plugin.xml` with the version and `since-build`, every class it names, the contract inside, no test class). Skipped without packages; `PACKAGES_REQUIRED=1` makes the skip a failure |

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

## GitHub Actions: `.github/workflows/`

The same checks as archvow's own CI, on every push and pull request (`ci.yml`):

| Job | What it runs |
|---|---|
| `vscode` | `npm ci`, `npm test`, `npm run package` -> the `.vsix` |
| `intellij` | `./gradlew check buildPlugin contractDump` -> the `.zip`; the Java reader must still write the committed `contract/vow-reader/expected.json` (the file the VS Code reader is tested against) |
| `contract` | the wheel of `contract/archvow/` installed, then `contract/tools/regenerate.py contract/archvow`: `contract/` must be exactly what is committed |
| `live` | `live.test.ts` against that wheel, installed in a venv, on the examples of `contract/archvow/` (`ARCHVOW_LIVE=required`) |
| `packages` | `package.test.ts` on both packages of the run (`PACKAGES_REQUIRED=1`); they are kept as the artifact `archvow-plugins` |

`archvow-canary.yml` runs every Monday (or by hand): the contract regenerated from archvow's default
branch; it fails, with the diff, when the next archvow changes what the plugins read.

Nothing is fetched from archvow's repository: `contract/archvow/` holds the wheel of the version the contract
was recorded from, and the examples and rules it does not hold (`contract/tools/take_archvow.sh` replaces them
after an archvow release). Only the optional canary clones archvow: it runs when the repository variable
`ARCHVOW_GIT_URL` is set (with the secret `ARCHVOW_GIT_TOKEN`, read-only, for a private repository).

## Before every commit

```bash
(cd intellij && ./gradlew check)
(cd vscode && npm test)
python3 -m venv .venv && .venv/bin/pip install contract/archvow/archvow-*.whl
ARCHVOW_PYTHON=$PWD/.venv/bin/python npm test --prefix vscode   # with the real archvow, from your own terminal
```

And before a release, the list on the page [Releasing](/en/14-release).
