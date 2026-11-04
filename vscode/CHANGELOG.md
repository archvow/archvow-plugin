# Changelog

## 0.16.0 - 2026-11-04

Built on archvow 1.20 (the contract regenerated from it). The AI skills become easy to reach and their work easy to
review, without a new tab: what the AI adds sits in the views it belongs to, marked. Both IDEs, the same functions.

- **One origin tag per card**, in Decisions, Safety and Rules: **Template** (came with the component type),
  **Project** (answered once for every component of the type), **Brief #section** (drawn from a brief), **AI ·
  agent** (added by an AI agent) - nothing for a person's own. What the AI added and no person kept yet is outlined
  in blue and says **To review**; its answer panel shows **Why the AI asked**.
- **Keep or remove what the AI added, where it is**: an AI question - **Save answer** keeps it and decides it in
  one step, **Keep, answer later** (`question accept`), **Remove question**; an AI hazard - **Keep**
  (`hazard accept`, with the invariants the AI added to prevent it); an AI rule - **Keep** / **Remove**.
- **All / Open / To review** beside the views, a blue dot and a count on each view that has something from the
  AI, and "N from AI to review" next to the component's name.
- **Suggested next with AI**: one suggestion at a time under the description, with why - what the AI added waits
  first, then Find missing questions, Find risks, Implement, Review changes, from the component's state.
- **AI skills ▾** on every component: the five skills in plain words, in the order of the work, the skill's own
  name small beside it, and why one cannot run yet ("Not yet: 2 decisions still to make").
- **The launch form of a skill** (design A): what the AI reads, at most how many questions, where the request
  goes (the project's `ai_tools`); on the right what will happen, what the AI may add, what you still decide and the
  exact request. VS Code opens Copilot Chat with it typed (you press Enter), or copies it for the other tools;
  IntelliJ copies it.
- **Commands: an "AI skills" group** after archvow's commands.
- **Overview › Briefs**, beside Architecture and Check & Gates (`archvow brief show`): every brief section by
  section - used (and by what), not used yet (**Draft with AI…**), changed since (**Review…**, **Accept change…**);
  the "Brief folders" line links to it.
- The skills, the origins and the requests live once in `contract/forms/forms.json` (`ai_skills`); both IDEs
  compute the same view of a component (`ai-skills.scenarios.json` / `ai-skills.expected.json`).

## 0.15.0 - 2026-11-04

Built on archvow 1.19 (works with 1.19.0 and 1.19.1: the parameters questions of 1.19.1 say "no default =
required" instead of showing `(required)` in their example). From the user's ten screenshots, both IDEs.

- **Components tab: Add component, and "+" on a layer.** A button **Add component** beside All / Pending /
  Resolved opens the component add form; on a layer, **+** (VS Code) or a right-click (IntelliJ) lists the types
  of that layer (p2_io: reader, writer) and opens the form with the type chosen. Under All, the template's empty
  layers show too ("No component yet"), so the first job or entry point has a place to go.
- **Meaningful icons** in the Components and Commands trees: the system, each layer, each component's type
  (schema, reader, writer, transformation, job, entry point, model, router, repository...) in its status colour,
  each command group and each command by its verb (add, edit, remove, accept...). One drawing each, in
  `contract/forms/icons.json`, painted by both IDEs.
- **What a command writes, before it runs**: under the command in the form, a **Files** block - the files new,
  updated, removed or kept (`.archvow/components/orders_reader/spec.vow`, its card, the project's page) - and for
  `component add` the code and test files it plans, with the function it declares (written later by the AI or
  a developer: archvow never writes code). The command preview stays. From `files` in forms.json.
- **Save to a file** on `changes` and `diff`: `archvow changes --from <first commit> --to v1.0.0 --markdown >
  CHANGES.md` in one form - the report is written to the project's file (replaced) and opened. The repository's
  first commit is offered first for `--from` ("everything the project contains").
- **Answer with a table** (the image-4 design): columns with Never null / Nullable / Unknown and Yes / No /
  Unknown as buttons, notes, and a bin on every row - IntelliJ had no way to remove a column; only what the
  question asks about is shown (a personal-data question has no Nullable column). A **parameters table** for the
  `name: type = default` questions (config, settings): parameter, type, default (empty = required), notes.

## 0.14.0 - 2026-11-02

Built on archvow 1.19 (the contract regenerated from it): **Install archvow 1.19** when another major.minor is
installed. The forms redesigned (design A, chosen by the user) and what archvow 1.19 adds, in both IDEs.

- **Every command's form, design A**: the fields in plain words ("What it must do", "Always true",
  "Prevents"), archvow's option small beside each; the essential fields first, the others under **More
  options** (opened when one holds a value); a few fixed values (severity, criticality, verify...) as one
  button each; on the right, **what archvow will do** and **the command as it is typed** (the live preview:
  the same grammar as Run, VS Code's `planForm`); the button says what it does ("Add invariant", "Declare
  hazard"), next to Cancel. Labels, order, verbs and the "what it does" line come from one file both
  plugins read, `contract/forms/forms.json`.
- **archvow's refusal in place**: when archvow says no (nothing written), its message shows in red under
  the form; when it writes with a doubt (a rule it would contradict, a test too weak for the severity), in
  amber.
- **One Spec view** instead of "Open spec" and "Full vow": the card the AI implements (spec.md) and the vow
  it is written from (spec.vow), a switch between them, both read only, each openable in the editor.
- **A safety question a decision already answers**: "Covered by decision N" with its answer and **Confirm**
  (the hazard, and an invariant that *is* the decision - archvow 1.19's `from_decision`); a decision that
  says there is nothing to protect: **Confirm not applicable**, no reason to type (the decision is the reason).
- **Change a hazard keeps its invariants**: `hazard edit` (archvow 1.19), one command, offered for every
  recorded hazard; Remove says the invariants that protected only it go too.
- **Questions with no safe default** offer archvow's choices as buttons ("No default (it depends on this
  project) - to start from: ..."): one fills the answer, to complete.
- **A component the AI created** (modes guided and free) shows **AI: to review** in the tree and its head,
  with Accept… and Remove…; the forms `hazard edit`, `invariant edit` (choosing one fills its words) and
  `invariant add --from-decision` (the component's decisions made) offer what exists.

## 0.13.1 - 2026-11-02

Built on archvow 1.18 (unchanged). Fixes from a user's screenshots of the IntelliJ plugin, in both IDEs.

- **Language**: changing it redraws everything open - IntelliJ's editor tab no longer needs closing and
  reopening, an open form keeps what is typed; VS Code's Pending lists follow too.
- **hazard stays a keyword in French**: "Déclarer un hazard", "Supprimer un hazard…", "Hazard proposé" (it
  said "danger").
- **A recorded hazard can be removed and changed**: Remove and Change next to it. IntelliJ: the buttons
  under the field wrap (the row did not: "Remove hazard…" and "Remove invariant…" were out of sight).
  Change puts its text and severity in the field; saving runs `hazard remove` then `hazard add`, confirmed
  once - offered while no invariant protects it (archvow 1.18 has no `hazard edit`).
- **The invariant a hazard needs is written in place**: right after a high or critical hazard is declared,
  the Safety view shows the invariant field, filled with the template's suggestion; "Add required
  invariant" (Pending lists) opens it there instead of the Commands form.
- **`invariant add`**: `--verify`, `--evidence` and `--rule` (options archvow hides from `--help`) are
  under "Expert checks", closed; their help no longer shows `==SUPPRESS==` (`export_grammar.py` marks
  them `hidden`).

## 0.13.0 - 2026-10-30

Built on archvow 1.18 (the contract regenerated from it): **Install archvow 1.18** when another
major.minor is installed. From a user's screenshots of the IntelliJ plugin.

- **Fields offer what exists** (`choices.ts`, `ArchvowChoices`, the same rules in both IDEs): a list of the
  project's components in every form that takes one; the component's questions with their number
  (`decision make`, `question remove` - only those a person added, the template's stay); `question add`
  says which number archvow will give; the assumptions, examples, safety questions, hazards, invariants,
  rules, dependencies and tasks by their id; `component add` offers the types of the project's template
  only, then the **kinds of the chosen type** (the default first); `template new --extends` the templates.
  Choosing a component in **Edit spec** fills the fields with what it has; a rule in `rule edit` its text;
  a question in `decision make` its answer.
- **Edit spec sends only what changed**, even when opened from a diagnostic; nothing changed is said
  ("Nothing changed"), not run. With archvow 1.18, what was recorded before a description change is to
  review (`archvow component accept`).
- **`template new`**: choosing the base fills "Settings of <base>" with its values: the defaults of the
  init answers (its choices only, its formats checked) and the folder of each layer. What the base imposes
  - what it extends, its version, its layers - is shown, not editable. Only the differences are sent.
- **`init`**: the template is chosen once, in "Template and its questions" right under the package's
  name (or a company template's folder); in a project, its own template first, with the `--force` note.
- **Undo changes** replaces the answer panels' "Cancel": enabled only when the field differs from the
  recorded text, it only puts it back.
- **Readable results**: **Expand** to the window's height, or drag the bar under a result; "Command
  result" no longer said twice. IntelliJ: the lines under form fields wrap at the form's width (they ran
  past the window's edge) and cannot be focused.
- **Comparison base**: the best branch that exists here (develop on a Git flow feature branch, else
  origin's HEAD, main, master...) instead of a fixed `origin/main`; chosen in the list of the repository's
  branches and tags; a base that does not exist is refused before running, with the branches that do.
- The form's header no longer says "JSON is added automatically..." and "Description et options fournies
  par le moteur Archvow.".
- Contract: recorded from archvow 1.18.0; `contract/cli/kinds.json` (kinds for an archvow 1.17),
  `contract/choices/expected.json` (`ChoicesRegression` = `choices.test.ts`).

## 0.12.0 - 2026-10-28

Built on archvow 1.17 (the contract regenerated from it).

- **Install archvow, guided**: when archvow does not start (the welcome page) or is another major.minor
  than 1.17 (Overview, and the version warning's **Install archvow 1.17**), the workspace shows the pip
  command and runs it only after **Confirm and run** - never at startup, never on its own. Into the
  project's `.venv` (or `venv`) first, then the project's interpreter, then `archvow.python`; a bare
  `python` is never chosen for you (no Python found: **Configure Python**). `archvow==1.17.*`, as archvow
  keeps no backward compatibility. From pip's own configuration (pip.conf, `PIP_INDEX_URL`) or the new
  setting **`archvow.installFrom`**: your company's index URL (`--index-url`), a `.whl` file, or a folder
  of wheels (`--no-index --find-links`, the lab's `pypi/` for instance). No shell, in the project folder,
  10 minutes at most; a Python in WSL through `wsl.exe` as archvow itself. On success `archvow.python` is
  set to that Python and the workspace reloads; on failure pip's output is shown, nothing is saved.
- **A progress bar under a component's name**: decisions made, safety answered, tasks done, examples
  tested (each only when there is something to count), green when complete, amber otherwise, the counts
  in the tooltip. Read from `show <component> --json`, as IntelliJ's.
- **The brief folders of archvow 1.17** (init answer `briefs`, e.g. `briefs,specs` for Spec Kit's
  `specs/`): the `--from` field of component add / edit, question add, decision add and rule add offers
  the files of those folders and their headings as `file#section` (archvow's own anchors: lower case, no
  accents, repeats `-2`, `-3`); free text stays allowed. When archvow refuses a file outside them, the
  result shows **Add this folder (architect)**: the `init --force` form opens prefilled with
  `briefs=<current>,<new>`, to be checked and confirmed. A component drawn from a brief shows **Drawn
  from: specs/001-orders/plan.md#paying-an-invoice**, a link that opens the brief at that heading.
  ARCH039 in Other / Project: **Accept (reviewed)** (`archvow brief accept`, confirmed) and **Review the
  brief**; an item drawn from a folder no longer declared gets **Add this folder (architect)**. Overview
  lists the folders.
- **Contract**: recorded from archvow 1.17.0, with a `briefs` scenario (`contract/cli/briefs/`: python-app
  with `specs/` declared, `mark_paid` drawn from it, ARCH039 after a change, the refusal before).
- Tests: 68 (`install.test.ts`, `briefs.test.ts`, the progress bar in `layout.test.ts`). Checked in
  Chromium on the briefs recording: the progress bar, *Drawn from* opening the brief, the refusal ->
  **Add this folder** -> the init form prefilled, the install card on the welcome page.

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
