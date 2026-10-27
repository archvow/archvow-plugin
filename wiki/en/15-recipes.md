# 15. Recipes

> 🇫🇷 [Version française](/15-recipes) · English version

Step by step for the most frequent changes. One rule for all: **both plugins change together** (same
function, same word in English and in French), and **the wiki pages concerned change in French and in
English in the same commit**.

## Adding an action to a component's view

A button that runs an archvow command (for example "Reject proposal").

1. **The command**: a function that returns the arguments, without running anything.
   IntelliJ: a static method of `ArchvowInlineCommands` (like `decide`, `dismiss`);
   VS Code: the same in the `inline` object of `vscode/src/grammar.ts`. An empty required argument
   throws an error with a sentence for the person.
2. **The button**: IntelliJ, in `ArchvowPanel` with the view's other buttons, passed to
   `ArchvowSplitView.actions` (the secondary actions on the left, the main button on the right);
   VS Code, in the page's view (`media/workspace.js`), which sends
   `post({ type: 'inline', action: '...', component, ... })`.
3. **The confirmation**: IntelliJ, `requestConfirmation` with the command and a summary; VS Code, one
   more `case` in `inlineAction` (`vscode/src/workspace.ts`) that builds `args` and `summary`.
   Nothing goes without "Confirm and run"; afterwards the component is read again (`reloadComponent`).
4. **The tests**: the command line in `InlineCommandRegression` and `workspace.test.ts`
   ("in-place actions").

An action that changes nothing (reading, copying an instruction) asks for no confirmation, but still
goes through a function that returns the arguments.

## Following a new field of archvow's JSON

1. Regenerate the contract (page [The contract](/en/04-contract)): the field appears in
   `contract/cli/<example>/*.json`.
2. VS Code: read it in `vscode/src/workItems.ts` or `vscode/src/model.ts` (with `str()`, `objects()`,
   never assuming a missing field is there), show it in the page.
3. IntelliJ: read it in the view's class (Gson, `JsonObject`), show it.
4. A test on the recorded JSON, in both (an example of the contract that has the field).
5. A missing field (an older archvow) must break nothing: nothing shown, no error.

## Adding a setting

| | IntelliJ | VS Code |
|---|---|---|
| declare | a public field of `ArchvowSettings.Values`, with its default value | `contributes.configuration.properties` in `vscode/package.json` (`archvow.<name>`, type, default, description) |
| show | `ArchvowSettingsConfigurable`: the field in `createComponent`, then `isModified`, `apply`, `reset` | nothing: VS Code shows it |
| read | `ArchvowSettings.get(project).getState().<name>` | `vscode.workspace.getConfiguration('archvow').get(...)`; for the CLI, in `cliSettings()` |

The interpreter choice is remembered for a list of candidates (`ArchvowCli.choose`, `choose()` on the
VS Code side): a setting that changes how archvow is run must change that list
(`ArchvowProcessCommand.candidates`, `candidates()` in `vscode/src/cli.ts`), otherwise the former choice
stays.

## Adding or changing a text

- IntelliJ: `ArchvowI18n.text(project, "English", "Français")`; in a class without a project, an `fr`
  boolean passed by the caller (like `ArchvowResultFormatter`).
- VS Code: `t('English', 'Français')` in the page, `tr(fr, 'English', 'Français')` in the extension.
- The same word in both plugins (Safety / Sécurité, Assumptions / Hypothèses, "Nothing to display."):
  look for the English text in the other plugin before inventing one.
- archvow's words are not translated: `.vow` keywords, rule codes, command names.

## Showing HTML in IntelliJ

Always start from `ArchvowHtml.body` (the IDE's font and size); a title with `ArchvowHtml.heading`, a
relative size with `ArchvowHtml.size`. Never `px` nor `<h1>`-`<h6>`: Swing draws them too large (page
[Languages and look](/en/12-languages-and-look)). Escape every text coming from archvow. Add the output
to `TextSizeRegression` if it is a new screen.

## Fixing a screen that is too narrow

1. Find the width where it breaks: IntelliJ, in `runIde` by dragging the tool window; VS Code, the
   sidebar.
2. Choose what stays and what goes: the name first, then the state, then the rest. An advice text
   wraps (`ArchvowWrapLabel`); buttons go into More ▾ (`ArchvowOverflowBar`); a column hides
   (`ArchvowArchitectureView.columnWidths`, an `@media` rule in `vscode/media/workspace.css`).
3. A regression at the width found (`ResponsiveOverflowRegression`, `ArchitectureViewRegression`,
   `layout.test.ts`).

## Changing the `.vow` reader

1. **The Java first** (`org.archvow.vow.core`, the reference), with a case in `CoreTests`.
2. `(cd intellij && ./gradlew contractDump coreTest)` rewrites `contract/vow-reader/expected.json`.
3. The same change in `vscode/src/vow`, until `vowReader.test.ts` passes: the TypeScript must give
   exactly what the Java gives.
4. Review `git diff contract/vow-reader/`: every changed token must be intended.

## Adding an IntelliJ regression

A class `intellij/src/test/java/org/archvow/idea/<Topic>Regression.java` with a `main()` that throws an
`AssertionError` on the first failure and prints an `OK: ...` line at the end; list it in
`WorkspaceTests.main`. No framework, no IntelliJ: if the test needs one, the logic must move out of the
panel into a small class.

## Adding a VS Code test

A `test('what is true, in one sentence', ...)` in the topic's file (`vscode/src/test/*.test.ts`), with
`node:test` and `node:assert/strict`. It reads the contract (`contract/cli`), never an installed
archvow; to run something, a fake `archvow` written by the test (`cli.test.ts`).
