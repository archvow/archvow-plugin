# Changelog

## 0.10.0 - 2026-10-27

- On archvow 1.16: the contract is recorded from archvow 1.16.0. The Commands list has **example add**
  and **example remove** (business examples, built from archvow's parser with no code change);
  `show --json` gains `examples`.
- A component's head lists its business examples: ✓ when its test is marked and its files are the
  recorded ones, ✖ with the reason in the tooltip (no marked test yet, ARCH056; files changed, ARCH055).
- Tests: 43.

## 0.9.0 - 2026-10-27

- On archvow 1.15: the contract is recorded from archvow 1.15.0. `show <component> --json` gains
  `uses`, `used_by`, `evidence` and, on each decision made, `by` / `on`; `show <file>[:<line>]` is new.
- A component's head shows **Uses ... · Used by ...** (`uses`, `used_by`): each name opens that
  component - what a change to it may break. The same line as IntelliJ.
- **archvow: Show archvow for this file**, on an editor's context menu and on its tab's: runs `archvow
  show <file>:<line>` (the line of the cursor) and opens the component that owns the file; a file no
  component owns gets archvow's hint (the `component add ... --file` to run).
- Tests: 42 (layout.test.ts runs the page's `links()` on archvow's recorded `show` of the pyspark example,
  and checks the command and its two menus).

## 0.8.0 - 2026-10-26

- On archvow 1.14: the contract is recorded from archvow 1.14.0 (`contract/tools/regenerate.py`). The
  command forms follow it with no code change: `init` has **--adopt** (existing code stays where it
  is: archvow proposes `layer_paths`), `component add` has **--file** (an existing module declared where
  it is).
- Check & Gates shows the count per rule archvow gives (`check --json` `counts`, most first), under the
  totals: *By rule: ARCH001 146 · SPK008 415*. Not shown for a single rule. The same line as IntelliJ.
- Tests: 40 (layout.test.ts runs the page's `byRule` on archvow's recorded check of the pyspark example).

## 0.7.0 - 2026-10-26

- **The sidebar chooses, one editor tab shows.** The archvow sidebar (activity bar) now holds Overview,
  Components (the tree) and Commands (the list); the former Components tree view is gone. A click on a
  component opens it in ONE editor tab, titled *archvow: clean_orders* with the archvow logo; a click on
  another component shows it in the same tab, without taking the focus from the sidebar. A command opens
  its form there too, and Overview's Check, Next, Doctor... their result.
- Open spec / Open code open ordinary editor tabs in the archvow tab's group (were opened beside it).
- Overview fits the sidebar: the architecture as one line per component, the language at the bottom.
- Tests: 39 (layout.test.ts holds the contributions and the two roles of the page). Checked end to end:
  the extension on a mocked vscode API, its sidebar and its tab in Chromium, archvow replaced by the
  recorded 1.13 outputs of the pyspark example (one tab for three components, a command form, Check,
  Save answer -> confirmation -> `decision make`, closing and reopening the tab; English and French).

## 0.6.0 - 2026-10-26

- **The IntelliJ workspace, in VS Code**: one editor tab with Overview, Components and Commands, the
  same functions as the IntelliJ plugin 0.6.0 (see the README). The panel per component of 0.5.0 is gone.
- **One tab**: clicking a component (sidebar, architecture table, a pending item) shows it in the same
  tab; 0.5.0 opened a new tab per component.
- Every change is confirmed in the page, under the button that asked for it, with the command line.
- The command grammar, the template catalog, the rule docs and the archvow version come from
  `../contract/cli` (archvow 1.13.0); the extension says when the installed archvow is another version.
- Settings: `archvow.language` (VS Code's, English, French).

## 0.5.0 - 2026-10-25

First version, the same version as the IntelliJ plugin of this repository.

- The **Components** view (archvow icon in the activity bar): the components by layer, with archvow's
  status (`check --json`) and what to do next (`next --json`), in archvow's own words.
- `archvow check` in the **Problems** view: the whole project on refresh, a Python file on save
  (`check --file`).
- A **panel per component** (`show <component> --json`): decisions (make one, take the suggestion,
  accept the AI's proposal), personal-data and operations questions, safety (add a hazard, say it does
  not apply), assumptions (accept, reject), rules and tasks; Open Spec, Open Code, Copy the AI
  instruction (`prompt`). Every change runs an archvow command after a confirmation that shows it.
- Refreshes by itself when a vow changes on disk (a terminal, an AI agent, a git checkout).
- Says when the installed archvow is outside the versions it knows (1.9 to 1.11).
- The **.vow language**: one keyword colour per depth, ids, people, dates; the reader's problems as you
  type; Ctrl+click on a component in `uses`, a hazard in `prevents`, the file of `module`; outline;
  folding; `#` comments; ```` ```vow ```` blocks in Markdown; a line over each vow naming the command
  that changes it. The reader is the IntelliJ plugin's, in TypeScript, tested against it.
