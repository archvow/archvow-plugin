# Changelog

## 0.23.0 - 2026-11-11

Built on archvow 1.27.0 (contract regenerated from the 1.27.0 wheel; contract 1.3).

- **The privacy regimes come from archvow, none is named in the plugins.** archvow 1.27 makes GDPR, the US state
  privacy laws (US-PRIVACY) and GLBA data files, and a company adds its own (HIPAA...) in a pack: their questions
  (P1, U1, G1, H1...) are component questions like the others, the init wizard offers them from the catalog
  (`rules`: ...GDPR, GLBA, US-PRIVACY, TST, DORA), and each code (PRIV001-006, GDPR002, USP001, GLBA003...) is
  explained from archvow's rules pages. The two column forms no longer look for "gdpr" or "rgpd" in a question.
- **Classify columns** (`column set`): a **Tags** field, the ones a privacy regime covers (`financial`, `health`,
  `biometric`, `children`, `precise-location`); its title says personal data, not GDPR, and its file plan
  each regime's register in `.archvow/spec.md`.
- **Packs**: what a pack brings lists its privacy regimes too.
- Needs archvow 1.27 or newer (contract 1.3); **Install archvow 1.27.0** pins `archvow==1.27.0`.
- Contract: the 1.27.0 grammar (`column set --tag`), the catalog's `regimes`, the examples' new codes and
  registers, 618 harvested messages (81 codes). `contract/tools/regenerate.py` records the built-in templates' `source` without the machine's
  path again (archvow 1.26 moved them: `<archvow>/templates/<name>`), in `workspace.json` too;
  `harvest_messages.py` stops when it records nothing instead of writing an empty file.
- Tests: the live tests read the templates' `source` the way `regenerate.py` records it (archvow 1.26's
  `<archvow>/templates/<name>`), in `workspace --json` too. VS Code: 156 passed with archvow 1.27.0 live, 6 skipped
  (no .vsix or .zip built). The IntelliJ tests were not run for this release (Gradle could not be downloaded where
  it was built): run `./gradlew test` before publishing.

## 0.22.0 - 2026-11-10

Built on archvow 1.26.0 (contract regenerated from the 1.26.0 wheel; contract 1.2).

- **Packs in the overview**: a third row under Architecture and Checks, "2 packs · 1 changed by hand", read
  from `packs` of `workspace --json` (no extra call). **Open Packs →** lists each pack of `.archvow/packs/`:
  its version, what it brings (templates, agents, rules), its source, and whether its copy is as added or was
  changed by hand (ARCH060, with what differs). Each pack has **Update**, **Remove** and **Check**, which open
  the form of `pack update`, `pack remove` or `pack check` with its name filled in; a changed pack also has
  **See the change** (`diff`). **Add a pack** and **New pack** open `pack add` and `pack new`.
- **A Packs group in Commands**: one form for each of the eight `archvow pack` commands (`new`, `template`,
  `agent`, `rule`, `add`, `update`, `remove`, `check`), with plain labels, the essential fields first, and a
  file plan that shows what each writes in the pack's folder (`--dir`, or the named pack in `.archvow/packs/`).
  `pack template` replaces `template new`; its `--extends` lists the catalog's `<pack>/<template>` names.
- **The init wizard reads the project's catalog**: `archvow catalog --json` now runs in the project's folder,
  where archvow 1.26 also sees the packs `pack add` copied before `init`. Templates are named
  `<pack>/<template>` (`archvow/python-app`); a short name (`python-app`) still selects the pack's template, as
  `init --template` reads it. The plugins no longer set `ARCHVOW_TEMPLATES` (archvow 1.26 no longer reads it),
  also through WSL.
- The message for an empty catalog points to `archvow pack add`.
- Needs archvow 1.26 or newer (contract 1.2); **Install archvow 1.26.0** pins `archvow==1.26.0`.
- Contract: the 1.26.0 grammar (the `pack` commands, their forms and icons), the examples' `.agents/skills`,
  600 messages for 76 codes, the vow-reader cases on the 1.26 examples.
- Not in this version: the seven-screen New pack wizard of the plan. The `pack` forms and the Packs list cover
  the same commands; the wizard would only order them.

## 0.21.0 - 2026-11-09

Built on archvow 1.25.0 (contract regenerated from the 1.25.0 wheel; contract 1.1).

- **One archvow call loads a project**: `archvow workspace --json` (archvow 1.25) gives the spec, every
  component's view, `check`, `next`, the briefs and the catalog together, each in the shape of its own
  command. The plugin used to start `show`, `brief show`, `catalog`, `check`, `next` and then one `show
  <component>` per component (16 processes on the FastAPI example); now it starts one. `check` runs once for
  every component.
- **After an action**, the whole workspace is read again in that same single call: one process, where the
  partial refresh started four. The vow watcher still ignores the change the action made.
- **Nothing to do when nothing changed**: when a vow changes on disk, the plugin passes the last answer's
  digest (`--since`). If no file of the project changed, archvow answers `unchanged` and the views stay as
  they are. This is never used after an action or when you refresh yourself.
- A failed `workspace` call is reported in archvow's own words, as before. If `check` alone crashes, the
  welcome card now shows that error; before, the plugin showed the spec without findings.
- Needs archvow 1.25 or newer (contract 1.1); **Install archvow 1.25.0** pins `archvow==1.25.0`.
- Contract: `contract/cli/<example>/workspace.json`, the 1.25.0 grammar (`workspace`, its form and icon),
  594 messages for 73 codes. The tools follow archvow 1.25's modules (`archvow.cli.parser`,
  `archvow.checks.engine`).

## 0.20.0 - 2026-11-09

Built on archvow 1.23.0 (contract regenerated from the 1.23.0 wheel; contract 1.0).

- **A contract number instead of one archvow version**: archvow 1.23 says its contract (`archvow version
  --json`, `contract/cli/contract.txt`, today 1.0). The plugin accepts any archvow of the same MAJOR and a
  MINOR at least its own, so an archvow release that keeps the contract needs no plugin release; an older
  archvow (no contract) is said to be older, with **Install archvow 1.23.0**. Install pins the tested
  archvow exactly (`archvow==1.23.0`).
- **archvow's own words when a command fails**: with `--json`, archvow 1.23 prints its error as an object on
  stdout (message, hint, exit). One reader takes it (and leaves stdout as before), one rule builds every
  failure message (the welcome card, notifications, the form's refusal, the session log) - the hint of an
  internal error is the path of archvow's crash report.
- **The session log shows what archvow did**: archvow runs with `ARCHVOW_LOG=info` (a level you set is
  kept); its log lines leave stderr and go to the log at debug level, and under a failed call, so *Copy the
  log for support* has them (archvow already hides the values in them).
- **Publishing fixed**: vsce 4.1.0 (the first with `--oidc`) locked as a devDependency and run with
  `npx --no-install`, CI on Node 22; a stable JetBrains release goes to the `default` channel (an empty list
  published nothing). `release.test.ts` checks both.
- The trees have an icon for the new `version` command; `contract/cli/errors/` holds three real failures
  both plugins test against.

## 0.19.2 - 2026-11-09

Built on archvow 1.22 (unchanged CLI contract).

- Automated Marketplace beta release from the validated beta branch.

## 0.19.1 - 2026-11-09

Built on archvow 1.22 (unchanged CLI contract).

- Automated Marketplace release from the validated main branch.

## 0.19.0 - 2026-11-09

Built on archvow 1.22 (contract regenerated from the 1.22.0 wheel).

- **The session log tells what happened** (the *archvow* output channel, `runlog.ts`): your actions on a line
  of their own, `▶` with the whole command, its exit code, its time and archvow's answer ("✔ Decision 1 of
  logging_setup made"); a refresh in one line ("Refresh · 9 reads · 2.4 s"), each read at debug level;
  failures `✖` with what archvow said; calls over a second marked slow; a header per session (the plugin,
  archvow, the Python used, the project, VS Code).
- **archvow: Copy the archvow log for support**: the header and the lines with their time, your answers and
  rationales hidden if you choose.
- **After an action, only what it touched is read again**: `decision make settings ...` reads the spec,
  `settings`, check and next (4 reads instead of every component); the vow change that follows no longer
  starts a second refresh.
- Icons for the `langgraph-agent` template's component types (state, prompt, chunker, tool, retriever,
  mcp_client, node, agent, graph, api, mcp_server, index_job).

## 0.18.0 - 2026-11-05

Built on archvow 1.21 (contract regenerated from the 1.21.0 wheel).

- **The Examples view**, always shown, after Rules and before Tasks ("Examples 4 · 1 ✖"; "Examples" when there is none): the examples
  as cards grouped by the rule they show (the scenario's `Rule:`), each with its test's own verdict - ✔ passing,
  ✖ failing, ○ no test yet, · not run (`show --json` `result`; the test decides, archvow shows); the selected one
  right: its rule, its brief (a link), its scenario, then each output file expected/ | actual/ side by side by
  kind - a table (CSV, TSV, JSON Lines) row by row with the changed cells marked, a document (JSON, YAML) or a text
  line by line: + only in actual, − only in expected, ~ changed. Open expected/, actual/ or the test; a failing
  example says "Fix the code, not the example" and copies the `archvow example add` a person runs when the
  business team changes its mind. A component with failing examples says so next to its name. The progress bar
  counts the passing examples.
- **Find in the project**: a field at the top of the sidebar (Enter) and "Find in archvow project" on the word
  under the caret (editor menu) run `archvow show <name> --json`; the editor tab shows where the name is - groups
  with counts (a click filters), the places, the selected one's lines - with Open at line N, Show <component>
  and, for a problem, its rule. A name found nowhere gives its close names, a click searches them.
- `example add` takes `--from` (the brief chooser, as the other commands that take a brief).
- **Initialize Archvow in a folder…** (`archvow.initialize`): from the sidebar when no folder is open, it picks a new
  or existing folder, opens it and starts the `init` form there; in an open folder it opens the form. The form says
  which main files `init` writes (updated with `--force`) and that no source folder is created.
- The base field and the `--base` / `changes` choices say what each Git ref is: local branch, remote branch
  (fetched), tag (`refKinds()`, from Git's full ref names).
- Fixed: a view's test file was never found from show --json `evidence` (list() kept objects only).
- Fixed for archvow 1.21: `show <name> --json` on a name that is no longer a component (removed, renamed) gives a
  search result; it is no longer stored as that component's view (`componentView()`). A failing example's head is red.

## 0.17.4 - 2026-11-05

Built on archvow 1.20 (contract unchanged). IntelliJ only (VS Code unchanged but for the version): the Components
tree as VS Code's (bar, count pill, ✓, ⊕ on the layers).

## 0.17.3 - 2026-11-05

Built on archvow 1.20 (contract unchanged). IntelliJ only (VS Code unchanged but for the version): a command
form's head as in VS Code, the AI button says **✦ AI spec ▾**, **+ Add ▾** without the ⊕ pictogram.

## 0.17.2 - 2026-11-05

Built on archvow 1.20 (contract unchanged). Both IDEs.

- **No suggestion strip.** The component's actions are, in this order: **✦ AI ▾** (the one filled button; its
  menu marks the suggested step), **+ Add ▾**, **Edit spec**, **Open code**. What the AI added and no person kept
  yet is still announced next to the name ("N from AI to review").
- **Save as a proposal is a real button** beside Save answer, shown while the decision is open (the ▾ menu of
  0.17.0 is gone).

## 0.17.1 - 2026-11-05

Built on archvow 1.20 (contract unchanged). IntelliJ only (VS Code unchanged but for the version).

- **The suggested step's button keeps its own size**: in a narrow tool window it filled half the strip and its
  height; it now goes under the text when the strip is narrower than 720 px, on its right otherwise.
- **The menus are readable** (✦ AI ▾, + Add ▾, Save ▾ - `ArchvowMenu`): a heading, a line between the groups,
  the row under the mouse highlighted, a step that cannot run yet greyed with why, the suggested one tinted. The
  two-line HTML menu items of 0.17.0 had no hover, no line between groups and a clipped selection mark on the left.
- The "N pending for <component>" line no longer shows under the Show line (twice the same information).

## 0.17.0 - 2026-11-04

Built on archvow 1.20 (contract unchanged). A calmer component tab, and IntelliJ's counts back. Both IDEs, the same
functions.

- **IntelliJ: the counts, the badges and "resolved" are right again.** 0.16.0 started `brief show` beside `check`;
  the plugin runs one archvow command at a time, so `check` was refused and `next`, the catalog and every
  component's `show` never ran: no counts in the tree, every component "resolved", Architecture "0". `brief show`
  now runs inside the load chain, last; `LoadChainRegression` fails if a command is ever started beside another.
- **Four quiet actions** on a component: **Open code**, **Edit spec**, **+ Add ▾** (a question, a rule, an
  assumption - what you add to this component) and **✦ AI ▾** (the five skills in the order of the work, the
  suggested one marked, one that cannot run yet greyed with why, then **Copy the card for another AI**, for an
  assistant that cannot read the project's files). Gone from the row: Clarify, AI prompt, + Question, + Rule,
  + Component (added from the Components tab) and AI skills ▾.
- **One filled button**: the suggested next step, in the strip under the description (always shown, no ×).
- **The filter is a line of small words** under the views, no longer buttons beside them: "Show **All** · Open 2
  · To review 1" - To review only when the AI added something, no Open in Rules.
- **No Clarify button**: the first open item is already selected; after **Save answer**, the next open one of the
  view shows; a view with nothing open left says where the work is ("Decisions: all done · Safety: 2 open ›").
- **One button in the answer panel**: **Save answer**, with **▾ › Save as a proposal**; **Use the suggestion**,
  **Accept the AI's proposal**, **Keep, answer later**, **Remove question** and **Undo** are links.

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
