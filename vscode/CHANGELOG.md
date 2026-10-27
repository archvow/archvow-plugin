# Changelog

## 0.11.4 - 2026-10-27

- **A wiki for the plugins' maintainers** (`wiki/` at the repository's root, French, English in
  `wiki/en/`), on the model of archvow's: how both plugins are built, every file, every flow and why.
- **`wiki.test.ts`**: `npm test` fails when the wiki no longer follows the code (a page without its
  English twin, a dead link, a file, an `Archvow*` / `Vow*` class or a `function()` named in a page that
  no longer exists). 55 tests pass.
- Nothing changes in the extension itself; the IntelliJ plugin's text sizes and branch advice are fixed
  in this version (same number for both plugins).

## 0.11.3 - 2026-10-27

- **Back** in the editor tab: a command opened from a component (Edit spec, + Question, + Rule,
  + Component, Accept / Reject an assumption, Add invariant...) shows **← <component>** in the tab's
  header; it returns to that component, on the view it was on. A component or a command chosen in the
  sidebar, or an Overview command, has no Back.
- **Edit spec opens with every field the component has**: from `show <component>` (description, uses)
  and from its entry in the spec (ticket, owners, group, brief; signature when there is one), as the
  IntelliJ plugin. A specific fix (from a diagnostic) still fills only its own field.
- **The cards show one line** of their question and one of their answer, cut with "…" (whole in the
  tooltip and in the answer panel on the right), as in IntelliJ.
- **Reload** is a drawn icon (a circle turning clockwise, the same as in IntelliJ) instead of the "↻"
  character; the header never cuts its buttons (the name gives way).
- Tests: 50. Checked in Chromium: one-line cards, Edit spec -> "← clean_orders" -> back on Safety.

## 0.11.2 - 2026-10-27

- **archvow is looked for in the project's Python interpreter first**: the one the Python extension uses
  for the project (setting `archvow.useProjectInterpreter`, on by default), then `archvow.python`, then
  `archvow.executable`. The first that answers `archvow --version` runs the commands, remembered while
  the settings stay the same. When none answers, the error lists what each one said.
- **archvow in WSL, VS Code on Windows**: `archvow.python` (or the interpreter) may be a WSL path,
  `\\wsl.localhost\Ubuntu\home\you\.venv\bin\python3`, `\\wsl$\...`, `wsl://<distro>/...` or a
  Linux path `/home/...` (the default distribution). It runs through `wsl.exe -d <distro> --cd <project
  as a Linux path> --exec <python> -m archvow ...` (no shell); a Windows path among the arguments
  (`C:\work\out.xml`) becomes `/mnt/c/work/out.xml`; `WSLENV` hands NO_COLOR, PYTHONIOENCODING and
  ARCHVOW_TEMPLATES to WSL. A project opened through `\\wsl.localhost\...` works the same way.
- Tests: 48 (the WSL command lines and paths, the search order, the first that answers and the report
  when none does). Not run on Windows here.

## 0.11.1 - 2026-10-27

- **The architecture table in the sidebar** is now the same table as IntelliJ's: Component, Type, Layer,
  Container, **State**, Pending (State was cut off at the end of the one-line rows). Component, State and
  Pending always show: as the sidebar narrows, Container goes first, then Layer, then Type (the row's
  tooltip still says them all), then Component's names are cut with "…"; only under about 250 px does
  the table scroll sideways.
- **A view with no item** (Questions of a component with none...) shows *Nothing to display.* alone: no
  answer panel ("Select a question.") next to it.
- Tests: 46. Checked in Chromium at sidebar widths 600, 500, 420, 330, 300 (French), 250 and 220.

## 0.11.0 - 2026-10-27

- **A component in two panels** (the 0.7.0 mockup v2): its head over the whole width (name and open
  items, *type · layer · module · ticket · state*, description, Uses · Used by, examples), the actions
  (*Clarify*, Open spec, Open code, Edit spec, AI prompt, + Question, + Rule, + Component) and the view
  chips; under them, for Decisions, Questions, Safety and Assumptions, **the items as cards on the left**
  (*3 · question*, then the recorded answer in green with who and when, or *● Open* in amber) and **the
  selected one's answer panel on the right**: *ANSWER DECISION 3*, the question, the AI proposal or
  suggestion, the field (monospaced), the other actions on the left and **Cancel / Save answer** on the
  right, the confirmation under them. Under 760 px the answer panel goes under the cards.
- **A component opens on its first open item**, in each of those views: the answer panel is ready. Back
  on the same component (after an answer, a reload) a view keeps its card; Clarify still jumps to the
  first open decision, question or safety question.
- Cancel puts back the recorded text and closes a confirmation left open. Propose, Accept proposal and
  Use suggestion show only when they apply; a safety question already answered shows no field.
- The *Actions* and *Vows* headings are gone; the editor tab's header no longer repeats the component's
  name. Other / Project shows no component head.
- Tests: 45 (`layout.test.ts`: the first open item, the cards, the split and its narrow layout). Checked
  in Chromium: the sidebar and the tab side by side on archvow's recorded outputs (orders_reader with its
  decision 3 open): opens on D3, another component on its own first open item, a card clicked and kept,
  Cancel, Save answer -> confirmation -> `decision make`, Safety, Questions, Assumptions, 1000 px stacked,
  English and French.

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
