# Changelog

The plugin joins two former plugins in 0.5.0; their own histories follow.

## 0.25.0 - 2026-11-12

Built on archvow 2.1.0 (contract 2.1: `next` rows say their `suggested` answers, `workspace --json` its
`first_steps`, and the new command `try`). The plugin needs archvow 2.1 or later.

The Overview as designed with the onboarding proposals, the same in VS Code and IntelliJ.
Nothing was removed: every command and list is where it was, or one click away in More.

- **Next and Check named on the toolbar; More holds the others** - Doctor, Audit, Briefs, Budget / Next, Diff and
  AI review, each with its icon. The eight icon buttons and their breakpoints are gone.
- **First steps**, for a new project: answer the foundations, add a feature, add its first component and answer
  its decisions, build it then Check, commit. archvow reads each from the spec, the check and Git (`first_steps`);
  the plugin only shows them, so both IDEs show the same card. The current step has its button (Specify, Add
  feature, Add component, Check); "Try archvow on an example first" runs `archvow try`. The card goes once the
  five are done - the examples, finished, never show it.
- **Architecture counts its own items**: "Items done 45 of 70", a bar, "25 open: decisions, questions and tasks.
  Check findings count in Checks.", then Open items, Done items and, when there are some, Project items. The
  Project status strip is gone: its counters are these links.
- **Checks shows the first finding**: its rule in words, `ARCH005 · compute_vat.py:16`, and Open file.
- **Every finding leads with its rule's title** - in the Checks list and in Check's result.
- **Next's result counts the suggested answers**: "Suggested answers: 4 of the 9 decisions" (`specify` offers them;
  a person confirms each).
- **The welcome screen** keeps Initialize, Configure Python and Retry, says how to adopt code already there, and
  offers "Not sure yet? See it catch a mistake": `archvow try <example>` copies a finished example to a scratch
  folder, makes the change an AI often makes and shows what Check says. Its result reads as the design shows
  (what was copied, the change, what caught it); "Read the example's spec" opens the copy's vow. `try` is
  read-only for the project: no confirmation.
- `contract/overview/expected.json`: what both IDEs read of the examples (first steps, first finding, suggested
  answers) - `UPDATE_OVERVIEW=1 npm test` writes it, IntelliJ's `OverviewRegression` must read the same.
- The contract recorded from archvow 2.1.0 (its wheel, examples, messages and the `.vow` readers' dump).
- Tests: VS Code `npm test` exits 0 - 164 passed with archvow 2.1.0 live and the .vsix built, 2 skipped (no IntelliJ
  .zip). IntelliJ: the 63 plain regressions pass (`OverviewRegression` new); the panels need `./gradlew check` (the
  IntelliJ platform).

## 0.24.3 - 2026-11-11

Built on archvow 2.0.1 (contract 2.0, unchanged).

- **No Project vow button on the Overview.** The toolbar keeps the commands (Next, Check, Doctor, Audit, Briefs,
  Budget / Next, Diff, AI review): the project's vow is read through them and the pages, and changed with
  `archvow` commands, not opened to edit by hand. It stays one click away where it is read - a component's
  spec (IntelliJ) and the Next items that point at it. The VS Code toolbar's breakpoints follow (eight buttons).

## 0.24.2 - 2026-11-11

Built on archvow 2.0.1 (contract 2.0, unchanged: 0.24.2 works with archvow 2.0.0 too).

- **The Packs card reads like the others.** On the Overview, "Open Packs →" sits on the right of the Packs card, on
  the same line as its title and count, as "Open Components for details →" and "Open Checks →" do; it was under
  the title, on its own line (VS Code; IntelliJ already drew it on the right).
- **The contract recorded from archvow 2.0.1**, whose examples are finished (code and tests, every safety question
  answered). The PySpark example is recorded without its code and with its next story started (`order_totals`, by
  `component add`; `contract/cli/pyspark-pipeline/spec-phase.json` says how), so the contract still has planned
  components, `next` rows, open decisions and safety questions; the live test makes that state again. The `.vow`
  readers' contract was dumped again from the new samples.
- Tests: VS Code `npm test` exits 0 - 160 passed with archvow 2.0.1 live and the .vsix built, 2 skipped (no IntelliJ
  .zip). IntelliJ: the 62 plain regressions pass; the panels need `./gradlew check` (the IntelliJ platform).

## 0.24.1 - 2026-11-11

Built on archvow 2.0.0 (contract 2.0, unchanged).

- **Files as a tree.** The Files preview of every form draws the files as a tree: each folder once, its children
  under it with their connectors, the folders a path only passes through drawn too (`.archvow/` › `features/` ›
  `orders/` › `components/` › `create_order.vow`), the root files (`AGENTS.md`, `README.md`...) beside `.archvow/`,
  never under a `./`. The connectors run on beside a note on several lines. A check's location stays compact
  (`src/sales/p2_io/` › `orders_reader.py`). VS Code and IntelliJ draw the same lines.
- **Where it is.** A feature's page shows its vow, its components' vows, their code and tests as one tree; a
  component's Spec view shows its vow, code and test.
- **Richer previews**: `init` shows `shared/` with its vow and its components; `feature rename` the vow and each
  component under the new name (six at most, "and N more"); `feature remove` the feature's vow; `doc` the project
  page and each feature's, or one feature's, and the status; the first `component add` of a project the feature it
  makes. The files table of forms.json gains `{member}`, `{each_feature}` and conditions (`with:`, `without:`,
  `new-feature`).
- Tests: the 62 plain regressions pass with javac and Gson (`FilePlanRegression`: the same tree lines as VS Code,
  the line painted beside a note; `FeatureViewRegression`: a feature's files); `ArchvowPanel` (the Spec view's
  "Where it is") needs the IntelliJ Platform: run `./gradlew test` before publishing.

## 0.24.0 - 2026-11-11

Built on archvow 2.0.0 (contract regenerated from the 2.0.0 wheel; contract 2.0, a new MAJOR).

- **Features.** archvow 2.0 organises the spec by what the product does: `.archvow/features/<feature>/` holds the
  feature's vow, one vow per component and its own proposals. The sidebar's **Features** tab (was Components)
  groups the components **by feature** - each feature folds, its "+" adds a component to it, its name opens its
  page, `shared` last - or **by layer** (the tree of 0.15-0.23), with **Add feature** above it.
- **A feature's page** in the editor tab: what the product does in it, its open items, its tickets, how many of
  its components are verified, the features it uses and that use it (with the uses that make each link), its
  components by layer and what is open in it. **Add component** (`component add -f`), **Open feature doc**
  (`archvow doc <feature>`, then its page in an editor), **Copy AI prompt** (`archvow prompt --feature`: the
  feature in a few lines, then the task of its next component), **Next steps** (`next --feature`), **Rename…**.
- **A component** names its feature (a link to its page) and has **Move…** (`component move`). Its **Spec**
  view shows the task the AI implements from `archvow prompt` (nothing generated is committed in archvow 2.0)
  next to its vow, found in its feature's folder.
- **Forms** for `feature add / rename / remove`, `component move` and `doc`; every `feature` field offers the
  project's features (`shared` is never renamed nor removed). The Files block shows each vow in its feature
  (`{home}`, `{feature}`) and no generated `spec.md` any more; `doc` runs without a confirmation (it writes only
  the ignored `.archvow/doc/`). The checks reference opens the project doc (`archvow doc`).
- **Paths**: the project is the folder holding `.archvow/project.vow` (activation, readiness, watcher); a vow's
  banner and Ctrl+click find a component's vow in any feature. An archvow 1.x project (`.archvow/spec.vow`) shows
  "not initialized", with how to convert it (archvow 2.0's `tools/convert_to_2.py`).
- Needs archvow 2.0 (contract 2.0); **Install archvow 2.0.0** pins `archvow==2.0.0`. Contract: the 2.0.0 grammar,
  the examples' new layout, `workspace.json` with `features`, 587 harvested messages (81 codes); icons for
  `feature` and `move`.
- Tests: the plain regressions (62, the new `FeatureViewRegression` on the recorded workspace among them) compiled and
  passed with `javac` and Gson; `ArchvowPanel`, `ArchvowEditorTab` and the `.vow` language classes need the
  IntelliJ Platform and were not compiled where this release was built (Gradle could not download it): run
  `./gradlew test` before publishing.

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

Built on archvow 1.22 (contract regenerated from the 1.22.0 wheel). The same as VS Code 0.19.0.

- **The session log tells what happened**, in the bottom *Archvow Output* tool window (☷; the lines in
  `ArchvowRunLog`, IDE-free and tested, the window in `ArchvowRunLogView`, refreshed as calls end): your actions (`ArchvowCli.executeAction`) `▶`
  with the whole command, exit, time and archvow's answer; a refresh in one line, each read in idea.log at
  debug level; failures `✖` with what archvow said; calls over a second marked slow; a header per session.
  The same lines go to idea.log (`ArchvowRunLog.Sink`).
- **Copy for support** in the *Archvow Output* window, your answers and rationales hidden by default ("hide my
  answers").
- **After an action, only what it touched is read again** (`refreshTouched`, `ArchvowGrammar.touchedComponent`);
  the vow watcher ignores the change the action just made.
- Icons for the `langgraph-agent` template's component types.

## 0.18.0 - 2026-11-05

Built on archvow 1.21 (contract regenerated from the 1.21.0 wheel). The same as VS Code 0.18.0.

- **The Examples view** (`ArchvowExamplesView`, `ArchvowExamples`), always shown, after Rules and before Tasks
  (`ArchvowCompactViews.addView(button, position)`): the examples as cards with their test's verdict, the
  selected one's state, rule, brief, scenario and output files expected/ | actual/ side by side; Open test,
  Copy command; the head says how many fail; the progress bar counts the passing ones. The states are VS Code's
  (`contract/forms/examples.expected.json`, `ExamplesAndSearchRegression`).
- **Find in the project**: a field under the tool window's header, and **Find in Archvow Project** in the editor
  menu (`FindNameArchvowAction`): `archvow show <name> --json` in the editor tab (`ArchvowSearchView`,
  `ArchvowSearch`) - groups, places, the lines around the selected one, Open at line N, Show <component>, Rule;
  close names as buttons.
- **Initialize Archvow…** on the Welcome screen (`ArchvowInitFromWelcomeAction`): open any folder, then the `init`
  form starts there; the form's Files list covers `init` too.
- The Git base choices say local branch, remote branch (fetched) or tag (`ArchvowChoices`).
- The AI button says **✦ Spec IA ▾** in French (user decision of 0.17.5, not in the 0.17.4 bundle).
- Fixed for archvow 1.21: `show <name> --json` on a name that is no longer a component gives a search result; it
  is no longer stored as that component's view (`ArchvowGrammar.componentView`), and the tab says the component is gone.

## 0.17.4 - 2026-11-05

Built on archvow 1.20 (contract unchanged). IntelliJ only (VS Code unchanged but for the version).

- **The Components tree as VS Code's**: a component's name in the text colour and its type muted; a thin bar in
  the status colour on its left (orange: something to do, green: done); its count in a grey pill or a green ✓
  at the right edge; a ⊕ at the right of each layer (a click opens its Add menu, as the right-click does);
  "No component yet" in italics. Before, the whole row was orange or green with "(2)" or "✓" after the type
  (`ArchvowComponentTree`, `ComponentTreeRegression`).

## 0.17.3 - 2026-11-05

Built on archvow 1.20 (contract unchanged). IntelliJ only (VS Code unchanged but for the version).

- **A command form's head as in VS Code**: the title, then one muted line - the command as a chip
  (`archvow component add`) and what it does - then, only when fields were prefilled, the note saying so. The
  scrolled monospaced box of before clipped its lines and ran under the title.
- **✦ AI spec ▾**: the component's AI button says what it works on (it was ✦ AI ▾ / ✦ IA ▾).
- **+ Add ▾ without the ⊕ pictogram**: its own "+" says it (`ArchvowUiIcons.NO_ICON` keeps a button bare).

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
major.minor is installed. The same functions as the VS Code extension's 0.13.0. From a user's screenshots of the IntelliJ plugin.

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

Built on archvow 1.17 (the contract regenerated from it). The same functions as the VS Code extension's
0.12.0.

- **Install archvow, guided** (`ArchvowInstall`): when archvow does not start, or is another major.minor
  than 1.17, the welcome card shows **Install archvow 1.17** with the pip command, and the version
  notification has the same action; pip runs only after **Confirm and run** - never at startup. Into the
  project's `.venv` (or `venv`) first, then the IDE's interpreter, then the Python of the settings (the
  former `python3 -m archvow` form read too); a bare `python` is never chosen for you. `archvow==1.17.*`,
  from pip's own configuration or the new setting **Install archvow from** (Settings > Tools > Archvow:
  an index URL, a `.whl` file or a folder of wheels, with Browse...). No shell, in the project folder, 10
  minutes at most, a Python in WSL through `wsl.exe` (`ArchvowWsl`). On success the settings keep that
  Python and the workspace reloads; pip's output in a notification either way.
- **A progress bar under a component's name** (`ArchvowProgress`): decisions made, safety answered, tasks
  done, examples tested; green when complete, amber otherwise.
- **The brief folders of archvow 1.17** (`ArchvowBriefs`): the `--from` field is a list of the brief
  files and their headings (editable: free text stays allowed); **Add this folder (architect)** under a
  result where archvow refuses a file outside them opens `init --force` prefilled (the *Other answers*
  field of the init form now takes a prefilled value); **Drawn from** in the component's head opens the
  brief at its heading; ARCH039: **Accept (reviewed)** and **Review the brief**, or **Add this folder**;
  Overview lists the folders.
- Tests: `InstallRegression`, `ProgressRegression`, `BriefsRegression` (registered in `WorkspaceTests`);
  35 headless regressions pass here (WslRegression and VowWatcherRegression run in `./gradlew check`). The panel's own changes (welcome card, notification action, links) need
  `./gradlew check buildPlugin` and a look in `runIde`.

## 0.11.4 - 2026-10-27

- **The branch advice is shown whole in a narrow tool window** ("You are on main: create a feature
  branch..." was cut at the panel's edge): it wraps over as many lines as it needs (`ArchvowWrapLabel`,
  a read-only text that wraps to the panel's width).
- **Text at the IDE's size everywhere** (the result of Doctor, Check, Audit, Next... and a component's
  head were far too large): Swing reads a CSS `px` as 1.3 point, and `<h2>`/`<h3>` added more. Every HTML
  the plugin shows now starts from `ArchvowHtml.body` (the IDE's font, in points), titles from
  `ArchvowHtml.heading` (slightly larger, no `<h1>`-`<h6>`), relative sizes from `ArchvowHtml.size`.
- **A wiki for the plugins' maintainers** (`wiki/`, French, English in `wiki/en/`), on the model of
  archvow's: reading guide, big picture, layout, contract, running archvow, sidebar and tab, Overview, a
  component, commands, the `.vow` language, problems, languages and look, tests, releasing, recipes,
  design decisions, glossary. `npm test` checks it follows the code (`wiki.test.ts`).
- Tests: `TextSizeRegression` (the IDE's size, no `px` or heading tags in Doctor, Check and an empty
  result, the advice wrapped at 260 px); 34 headless regressions pass; seen in a Swing frame: Doctor at
  the IDE's size, the advice on four lines at 300 px.

## 0.11.3 - 2026-10-27

- **The tool window's header no longer cuts the project's name** (the language box drew over it): the
  language is chosen at the bottom of Overview, next to Open settings; the name takes what is left of
  the width, never drawn under the buttons. **Reload** is a new drawn icon (a circle turning clockwise,
  open at the top right; the former one read as a "C"), the same as VS Code's.
- **Back** in the editor tab: a command opened from a component (Edit spec, + Question, + Rule, Accept /
  Reject, Add invariant...) shows **← <component>** left of the logo; it returns to that component, on
  the view it was on. Nothing to go back to after a choice in the tool window.
- **Edit spec opens with every field the component has** (`ArchvowCommandPrefill`): description and uses
  from `show <component>`, the ticket (--spec), owners, group and brief (--from) from its entry in the
  spec (they were left empty), the signature when there is one. A diagnostic's specific fix stays specific.
- **Test Archvow and long messages** (`ArchvowTextDialog`): a dialog sized to its text (it was one line as
  wide as the screen), the text selectable, **Copy command** (the exact command line, quoted for a
  terminal), Copy, Close. Also for the Python help and for what was tried when archvow is not found.
- Tests: `TextDialogRegression`, `CommandPrefillRegression` (7 cases); 33 headless regressions pass; run in
  a Swing frame: Edit spec from Safety -> "← orders_reader" -> back on Safety; the header at 330 px.

## 0.11.2 - 2026-10-27

- **archvow is looked for in the project's Python interpreter first** (PyCharm: Settings > Project >
  Python Interpreter; IntelliJ IDEA: the project's or a module's Python SDK, `ArchvowInterpreter`), then
  the Python of Settings > Tools > Archvow, then its executable. The first that answers `archvow
  --version` runs the commands (`ArchvowCli.choose`), remembered while the settings and the interpreter
  stay the same; when none answers, the error lists what each one said (English or French). Settings >
  Tools > Archvow: "Use the project's Python interpreter first" (on by default), the interpreter found,
  and Test follows the same order and says which one answered.
- **archvow in WSL, PyCharm on Windows** (`ArchvowWsl`): a WSL interpreter or Python path -
  `\\wsl.localhost\Ubuntu\home\you\.venv\bin\python3`, `\\wsl$\...`, `//wsl.localhost/...`,
  `wsl://<distro>/...`, a Linux path `/home/...` (PyCharm's target-based WSL interpreter: its distribution
  is read from the SDK) - runs through `wsl.exe -d <distro> --cd <project as a Linux path> --exec <python>
  -m archvow --root <project as a Linux path> ...` (no shell); a Windows path among the arguments
  becomes its Linux path (`C:\work\out.xml` -> `/mnt/c/work/out.xml`); `WSLENV` hands NO_COLOR,
  PYTHONIOENCODING and ARCHVOW_TEMPLATES to WSL. A project opened from `\\wsl.localhost\...` works too.
- "Archvow could not start" is in the project's language (it was French only).
- Tests: `WslRegression` (the paths, the commands, the order, the first that answers, a stand-in wsl.exe
  run); 32 headless regressions pass. Not run on Windows here: try it in PyCharm (Settings > Tools >
  Archvow > Test).

## 0.11.1 - 2026-10-27

- **The Overview architecture table keeps Component, State and Pending in view** in a narrow tool
  window (everything was cut to "fo...", "p..."): the columns get their width from their text
  (`ArchvowArchitectureView.columnWidths`); Container goes first, then Layer, then Type (each row's
  tooltip says them all), then Component's names are cut with "…"; only then does the table scroll
  sideways. The same table as VS Code's.
- *Project status* and its two counters no longer overlap in a narrow tool window: the counters go under it.
- **A view with no item** shows *Nothing to display.*, as in VS Code, and no answer panel (it showed
  "Select a question." with an empty field and Save answer): `ArchvowSplitView.cards` and `create` follow
  the list.
- Tests: `ArchitectureViewRegression` (the widths at 800, 500, 420, 300, 270, 240, 200 px),
  `SplitViewRegression` (the empty view and its way back). 31 headless regressions pass; run in a Swing
  frame with the tool window at 300 and 470 px.

## 0.11.0 - 2026-10-27

- **A component in two panels**, as in VS Code: the head over the whole width (name and open items,
  *type · layer · module · ticket · state*, description, Uses · Used by, examples - the details pane,
  built since 0.9.0 but not shown in the editor tab since 0.7.0, is that head now), the actions (Clarify
  first, painted as the main button; + Question, + Rule, + Component) and the view buttons; under them, for
  Decisions, Questions, Safety and Assumptions, the items as cards on the left (`ArchvowItemCards`) and
  the selected one's answer panel on the right (`ArchvowSplitView`): *ANSWER DECISION 3*, the question,
  who recorded the answer and when, the field (monospaced), the other actions on the left and **Cancel /
  Save answer** on the right (on a row under them when the panel is too narrow), the inline confirmation
  under them. Under 680 px of view the answer panel goes under the cards.
- **A component opens on its first open item**, in each of those views (`renderComponent`): the answer
  panel is ready. The same component drawn again (after an answer, a reload) keeps each view's card.
- Cancel puts back the recorded text and closes a confirmation left open. Propose, Accept proposal, Use
  suggestion, Accept / Reject show only when they apply; a safety question already answered shows no field.
- The *Actions* and *Vows* headings are gone; the header no longer repeats the component's name.
- Tests: `SplitViewRegression` (side by side and stacked, the buttons' rows, the main button's colour,
  the answer panel's height, the cards' text, the first open item); `DetailAndLogRegression` follows the
  new heading. 31 headless regressions and `CoreTests` pass, compiled against stand-ins of the platform
  classes (no JetBrains repository here); the panels were also run in a Swing frame (tool window + editor
  tab) on archvow's recorded outputs. `./gradlew check buildPlugin` and `runIde` on your machine.

## 0.10.0 - 2026-10-27

- On archvow 1.16: the contract is recorded from archvow 1.16.0 (`example add|remove` in the command
  forms, `examples` in `show --json`); `expected.json` rewritten by `DumpContract`.
- A component's details list its business examples (`ArchvowLinks.examplesHtml`): ✓ tested and
  unchanged, or ✖ with the reason (ARCH056, ARCH055). The vow viewer colours `example` and `fingerprint`.
- Checked as for 0.9.0: `CoreTests`, the 29 headless regressions (`UsesAndFileRegression` covers the
  examples); `./gradlew check buildPlugin` and `runIde` on your machine.

## 0.9.0 - 2026-10-27

- On archvow 1.15: the contract is recorded from archvow 1.15.0 (`show --json` gains `uses`, `used_by`,
  `evidence`; `show <file>[:<line>]` is new). `contract/vow-reader/expected.json` rewritten by
  `DumpContract` (the samples' template version changed).
- A component's details start with **Uses ... · Used by ...**, each name a link that opens that
  component (`ArchvowLinks`, the same line as VS Code).
- **Show Archvow for This File** (`ShowFileArchvowAction`), on the editor's and the editor tab's context
  menus: `archvow show <file>:<line>` (the caret's line), then the component in the Archvow editor tab;
  a file no component owns gets archvow's hint.
- Checked here without Gradle (JetBrains hosts are not reachable): `CoreTests` (182 584 checks on 29
  vows), the 29 workspace regressions that need no IntelliJ classes (new: `UsesAndFileRegression`),
  compiled with javac and run headless, and `ShowFileArchvowAction` compiled against stubs of the
  IntelliJ API it uses. The panel's two additions are not compiled here: `./gradlew check buildPlugin`
  and `runIde` on your machine.

## 0.8.0 - 2026-10-26

- On archvow 1.14: the contract is recorded from archvow 1.14.0. The command forms follow it with no
  code change: `init` has **--adopt** (existing code stays where it is: archvow proposes `layer_paths`),
  `component add` has **--file** (an existing module declared where it is). `contract/vow-reader/
  expected.json` rewritten by `DumpContract` (the project vow's header line changed).
- Check & Gates shows the count per rule archvow gives (`check --json` `counts`, most first), under the
  totals: *By rule: ARCH001 146 · SPK008 415* (*Par règle : ...* in French). Not shown for a single rule.
- Checked here without Gradle (JetBrains hosts are not reachable): `CoreTests` (182 584 checks on 29
  vows) and the 27 workspace regressions that need no IntelliJ classes, compiled with javac and run
  headless. `./gradlew check buildPlugin` still to run on your machine.

## 0.7.0 - 2026-10-26

- **The tool window chooses, one editor tab shows.** The Archvow tool window is on the left and holds
  Overview, Components (the tree) and Commands (the list). A click on a component opens it in ONE editor
  tab, titled *archvow: clean_orders* with the archvow logo; a click on another component shows it in the
  same tab. A command opens its form there too, and Overview's Check, Next, Doctor... their result.
  Closing the tab is fine: the next click opens it again. Open spec / Open code open ordinary editor tabs.
- The tool window reads the project and hands it to the tab (no second reading); a check run in the tab
  updates Check & Gates in the tool window; the tool window selects what the tab shows.
- Removed: the workspace as a whole in an editor tab, the "Open in Editor / Open in Sidebar" switch and
  **Tools > Open Archvow in Editor** (now **Tools > Show Archvow**). At startup, an archvow project shows
  the tool window instead of opening an editor tab. An installed earlier version may keep the tool window
  on the right: drag its icon to the left once.
- The main tabs of the tool window are compact (no icons) so Overview, Components, Commands fit a narrow
  window, in French too (ToolWindowLayoutRegression; 29 test classes).

## 0.6.0 - 2026-10-26

- Save answer: the decision, question and safety editors no longer have a fixed height. When the inline
  confirmation opened, the answer field was squeezed to nothing and "Your answer or proposal" was drawn
  over the Save answer button (AnswerEditorRegression).
- archvow 1.13.0: the command grammar, the template catalog, the rule docs and the version are taken
  from `../contract/cli` at build time (no frozen 1.11 copies); the workspace says once when the
  installed archvow is another major.minor (VersionRegression).
- The VS Code extension 0.6.0 has the same workspace.

## 0.5.0 - 2026-10-25

- One plugin: the archvow workspace (former archvow-intellij 0.4.5.16.11) and the `.vow` language
  (former vow-plugin-intellij 0.4.2). Id `org.archvow.intellij`; uninstall the former vow plugin
  (`com.archvow.intellij`) first. Packages `org.archvow.idea` and `org.archvow.vow`.
- Built with the IntelliJ Platform Gradle Plugin 2.19.0 against IntelliJ IDEA 2024.3.5 (installs in
  2024.3 and later), JDK 21; the Gradle wrapper is complete (no Python bootstrap).
- `./gradlew check` runs the tests: `coreTest` (the .vow reader, on `../contract/vows`) and
  `workspaceTest` (26 test classes of the workspace, headless).
- The workspace reloads by itself when a vow changes on disk (an archvow command in a terminal, an
  AI agent, a git checkout): one reload 0.8 s after the last change, later if it is busy.
- The CLI's stdout and stderr are read apart: the JSON is read from stdout only, so a warning on
  stderr no longer breaks it; messages still show both. A result over 20 MB says it is incomplete
  (was cut silently at 2 MB).

## vow-plugin-intellij (the .vow language)

### 0.4.2 - 2026-10-21

- The keyword colours by depth are the plugin's own in every colour scheme, light or dark (Monokai,
  Dracula, One Dark... included), not only in Default and Darcula: before, a scheme the plugin did
  not know coloured them as its "function declaration" and "instance field" (violet in many). Dark:
  yellow bold, soft green, blue, pink; light: teal bold, burnt orange, steel blue, olive. A scheme
  can still set them (Settings > Editor > Color Scheme > Vow > Keyword).

### 0.4.1 - 2026-10-21

- New default colour for the keywords indented once (`question`, `answer`, `by`, `on`, `kind`...):
  soft green in the dark schemes, burnt orange in the light one (was purple). Light depth 2 is now
  steel blue. Settings > Editor > Color Scheme > Vow > Keyword still changes each depth.

### 0.4.0 - 2026-10-21

- Keywords: one colour per depth, the general rule for every vow. Depth 0 (not indented: `name`,
  `type`, `uses`, `decision`, `hazard`), depth 1 (`question`, `answer`, `by`, `on`, `kind`, a
  column such as `commentaire`), depth 2, depth 3 and deeper; 2 spaces a level, quoted keywords
  included. Settings > Editor > Color Scheme > Vow > Keyword. The Default (light) and Darcula
  schemes get set colours (light: teal bold, purple, brown, olive; dark: yellow bold, purple, blue,
  pink), so no depth is left uncoloured. The separate colour of a block's keyword is gone: a block
  keyword is a keyword of its depth.
- An e-mail (`by alice@acme.com`) is coloured as a person, like `@alice`.

### 0.3.0 - 2026-10-21

- Two keyword colours: a top-level keyword (`name`, `type`, `layer`, `module`, `description`,
  `uses`: a line that is not indented) has its own colour; a nested keyword (`kind`, `question`,
  `answer`, `by`, `on`, `in`: an indented line) keeps the keyword colour. Both are set in
  Settings > Editor > Color Scheme > Vow ("Top-level keyword", "Nested keyword"). The keyword of a
  block (`hazard H1`, `decision 3`) keeps its own colour.
- `coreTest` checks on every sample vow that each keyword's kind follows its indentation; the
  samples are archvow 1.9.1's examples.

### 0.2.0 - 2026-10-21

- archvow's icon for `.vow` files and the plugin (green; a lighter green in dark themes).
- `coreTest` reads the vows of archvow 1.9.0's three examples (`python-app` added).

### 0.1.0 - 2026-10-21

- `.vow` files and ```` ```vow ```` blocks in Markdown: highlighting, errors with their line,
  folding, structure view, Ctrl+click from a component, a hazard or a module to what it names,
  a banner saying which archvow command changes the file.

## archvow-intellij (the workspace)

### 0.4.5.16.11 — Consistent button hover and single reload icon (Java 17)

- Added subtle theme-aware hover background and border on all ordinary action buttons, status counters and overflow buttons. Native borders and original background are restored after hover; selected tabs keep their own selection styling.
- Applied hover to buttons created dynamically in command forms and component actions, with idempotent registration and no permanent animations.
- Removed duplicate reload symbols by using one 14 px refresh icon in the header; the accessible label and tooltip remain.
- Added Swing regression coverage for light/dark hover, restoring native appearance, repeated decoration and the single reload icon.
- No CLI, feature, permissions or navigation changes.

### 0.4.5.16.10 — Native UI polish (Java 17)

- Localized architecture states `tested` → `Testé`, `drift` → `Écart détecté` in French; preserved technical state names in English. Drift has an explanatory tooltip.
- Added small 14 px theme-aware vector icons to recognized actions and navigation controls without replacing text or forcing button backgrounds.
- Distinguished command result cards from other panels using a calm, theme-aware tinted surface and blue leading border.
- Added Java 17 regression tests for icon rendering, translations, and result styling.
- No CLI command, behavior, workflow, or architectural permission changes.

### 0.4.5.16.9 — Status clarity, inline approvals and light theme

- Pending vow rows are amber, resolved vow rows are green, even without hovering.
- Hover neutralizes the background; selection retains the existing stronger appearance.
- Tasks and Other / Project follow the same status language.
- Inline human confirmation appears below Save / Answer / Safety actions, not above Vows.
- Switching view or component cancels an obsolete confirmation.
- Overview buttons keep the native IntelliJ painter, without focus rings or forced opacity.
- No change to the Archvow CLI or to the 40-command grammar.

### 0.4.5.16.8 — Workflow, forms and branch guidance

- Inline human confirmation (no oversized modal) for command changes and Vows changes, including cancellation.
- Init: browse for --dir destination using a directory chooser.
- Component add: known types from the built-in Archvow 1.11 catalog with editable extension for company templates, visible naming hint, and clear --spec example.
- Vows: prefer existing answer or proposed/suggested value when present, always editable; apply hazard suggestion as an editable input.
- Rename Data & tasks view to Tasks; retain column classification tasks inside.
- Non-blocking warning on main/master/develop/development Git branches to encourage feature branches.
- Simplify Getting Started notices and avoid mixing languages.

## Changelog

### 0.4.5.16.8-java17-multiide — Init on Welcome Screen

- Independent project-free catalog runner: no collision with show/check/next.
- Bundled built-in Archvow 1.11 catalog generated from the real CLI (offline reference), so templates and their questions always appear, even when the Welcome Screen has no configured Python.
- Prefer live installed CLI catalog when available; company templates require the installed CLI.
- Preserve typed init answers when the live catalog replaces the bundled reference.
- Reload fetches a fresh CLI catalog; no project root or .archvow directory required.

### 0.4.5.16.8-java17-multiide — Project-free init catalog and safety details

- `init` loads `archvow catalog --json` from the installed Archvow CLI without
  requiring `.archvow/spec.vow` and without reading project-local templates.
- The catalog includes built-in packs and company packs configured with
  `ARCHVOW_TEMPLATES`; when a valid custom pack folder is entered, its parent
  catalog is added for that request only.
- Project commands still execute within the current project and retain `--root`.
- Safety details use the same readable identity / question / recorded answer
  hierarchy as Decisions, with explicit high/critical invariant warnings.
- Added independent CLI and global-catalog regressions.

### 0.4.5.16.5 — Init wizard, readable outputs, editor by default, Vow detail panels

- Catalog-based init wizard powered by actual `archvow catalog --json` template questions, defaults and choice lists.
- Single-choice questions use selectors; multi-choice questions use compact checkbox chips. Additional KEY=VALUE overrides remain available.
- Human-readable Catalog and init results; recursive structured output for JSON-returning commands, with raw output retained as an optional view.
- For initialized Archvow projects, the workspace opens in the central editor when the IDE project starts; uninitialized projects are not auto-opened.
- Compact one-line summaries for decisions, questions, safety and assumptions; full selected details remain below.
- Replaced fragile CSS-based safety HTML with explicit Swing-compatible tables and visible invariant requirements.

## Changelog

### 0.4.5.16.4 — Readable results and reliable AI task prompts

- Check / Full check: clearer error and warning cards, with original JSON behind Raw output.
- Audit: structured decision traceability; Doctor: controls and suggested fixes; Next: grouped component readiness.
- Corrected `archvow prompt <component> --task N`: Archvow uses 1-based integer task numbers, not labels such as `T1`.
- Fall back to the full component prompt when the engine says the card is not split into tasks.
- AI prompt actions started from Components now show results under the component, not invisibly in Overview.
- Added Copy output, preserving the complete original AI prompt for pasting into an assistant.
- Data & tasks shows selected item descriptions, including completed tasks, and a clear message if the spec has no further description.
- Added an Edit component description shortcut to `component edit`, and a read-only preview of its current description.
- No roles or CLI changes; keep the full set of 40 commands.

### 0.4.5.16.3 — Contextual details, safety operations and native checks

- Unified **Details / Détails** sections in Questions, Safety, Assumptions, and Other / Project.
- Split the former **Rules & data** into **Rules** (architecture rules and edit/remove/add actions) and **Data & tasks** (columns, classifications and task prompts). Pending counts remain based on actual records.
- Added confirmed hazard and invariant removal entry points (CLI 1.11 has `add` and `remove`, but no dedicated `edit`). Additional invariants can be added for existing hazards.
- Kept high/critical hazards pending when no invariant protects them; Archvow itself enforces last-protection safety constraints.
- Replaced fragile Swing HTML Check & Gates dashboard with a native list of violations, live error/warning counts, selectable details and clickable rule references. Manual Check updates it too.
- No role-based restrictions. The CLI remains the authority for command validation.

### 0.4.5.16.2 — Native architecture inventory

- Replaced fragile HTML component table with a native Swing JTable.
- Inventory uses the successful `show --json` manifest as source of truth, independently from `check`.
- Shows component, type, layer, container, state and per-component pending count.
- Explicit empty, loading, error and non-evaluated states; double-click opens component.
- Added regression tests for missing/partial check statuses and French translation.
- No changes to CLI commands, roles, or user specifications.

### 0.4.5.16.2-java17-multiide — Java compile fix

- Removed the duplicate `openProjectVow()` method in `ArchvowPanel.java`.
- Retained the first implementation, which opens `.archvow/spec.vow` and shows a helpful message if missing.
- Added a regression test that asserts this method has exactly one definition.
- No CLI behavior, UI navigation, profiles, or command semantics changed.

### 0.4.5.16-java17-multiide — UI clarity and diagnostics

- Corrected mixed interface labels in EN/FR.
- Renamed component section to Vows; check diagnostics remain available.
- Added toolbar log viewer and real project vow entry point.
- Styled decision details and simplified readable reports.
- Improved handling of prefixed JSON from the CLI.

### 0.4.5.15 — Overview, diagnostics and rules documentation

- Fixed empty/stale Check & Gates panel after manual Check.
- Replaced raw JSON check summary with semantic errors/warnings and links to real rule definitions.
- Linked ARCH, SPK, GDPR and all recognized project rule codes to `.archvow/spec.md`, with official offline FR/EN references.
- Localized overview and check headings; kept raw CLI diagnostic text intact.
- Grounded Architecture table in actual spec components and explicit per-component pending counts.

### 0.4.5.14 — Natural-width overflow and Archvow terminology

- Show every button that actually fits, hiding More when nothing overflows.
- Apply measured-width overflow to Overview commands, component actions and component navigation.
- Replace generic Views header with Vow & checks / Vow et contrôles, because the sections include both component vows and diagnostics.
- Keep the 40 CLI commands and safe human confirmations unchanged.

### 0.4.5.13-java17-multiide — Compact views and grounded command context

- Replaced horizontally scrolling Components → Views row with a compact navigation bar and **More views** menu. All 7 views remain available, with status counts and selection retained.
- Added responsive reduction of primary view buttons based on available width.
- Prefill CLI forms from exact diagnostic values, the selected component, and existing component specifications when opening generic `component edit`.
- Prefill `component add` container from the architecture manifest when selecting an existing component.
- Preserve diagnostic-specific fields without injecting unrelated optional modifications.
- Protect `rule add` from running with only the prefilled component identifier; the rule text must be supplied by a human.
- Added interactive Java Swing and grounded JSON prefill regression tests.


### 0.4.5.12 — Actionable diagnostics, data actions, and verified safety status

- Other/Project diagnostics now offer grounded prefilled command forms for ARCH019, ARCH004, and ARCH030.
- Removed duplicate component buttons; fallback diagnostics without a fix remain read-only.
- Rules & Data now displays selectable pending columns and tasks, with classification and task-prompt actions.
- Safety now shows template-proposed hazard and invariant, and references actual `prevented_by` invariants.
- High/critical hazards with no invariant remain pending even when the Archvow worksheet says answered.
- Safety quick treatment links directly to an invariant-add form prefilled with `--prevents`.
- No role restrictions or behavioral changes to the underlying Archvow CLI.


### 0.4.5.11 — Consistent counts and visible Rules & data items

- Shared data-item model for column classifications and tasks: one count, one rendered list, one source for global pending/resolved statuses.
- Selectable, word-wrapped, native Swing text replaces the brittle HTML rules/data pane.
- Pending columns and tasks appear at the top rather than behind empty applicable-rules space.
- Component-level pending explanation reconciles the tree badge with the current category and diagnostics.

### 0.4.5.10-java17-multiide — Status in context, project-wide issues

- Removed the redundant Components → Status view and preserved individual component tabs.
- Marked decision, question, safety and assumption rows with pending/treated status colors.
- Made assumptions selectable with actions applying to the selected record.
- Added pending counters per component view and a compact Other / Project view for uncategorized or global issues.
- Kept check violations and root-level project records visible without inventing component ownership.
- Routed global Overview counters to the relevant Components view (or Other / Project for global-only issues).
- Corrected deduplication to preserve distinct records sharing the same command/title and keep pending and resolved apart.
- Removed redundant Questions from Rules & data; retained columns, tasks and applicable rules there.
- Preserved all CLI commands, project preflight, inline decisions and editor/sidebar switching; no profiles.


### 0.4.5.9-java17-multiide — Component-centric status

- Overview simplified: project status, clickable counters, Check / Next / More, Architecture and Check & Gates.
- Removed the Quick treatment panel and its automatic modal prompt from Overview.
- Moved existing treatment actions into Components → Status, preserving human confirmation.
- Counters now jump to Components → Status and filter the architecture tree by unresolved or fully resolved components.
- Added colored status markers next to component IDs (amber for pending items; green for resolved items).
- Added All / Pending / Resolved filters directly above the component tree.
- Retained every CLI command and the existing editor/sidebar switching and project preflight.

### v0.4.5.8

- Replaced fragile custom-painted tab interaction with explicit, fully clickable tab controls at all navigation levels.
- Allowed horizontal scrolling for narrow view selector bars, avoiding cut-off labels.
- Made the Editor/Sidebar location switch reciprocal and mutually exclusive.
- Added a project-level sidebar visibility listener to prevent duplicate Archvow workspaces when using the tool-window stripe.
- Kept core Archvow behavior and 40 CLI commands unchanged.

### 0.4.5.7 — Editor-like unified navigation

- Unified top-level, overview and quick-treatment tabs.
- Matched the six component Views to the same compact editor-like style.
- Removed overdrawn double underlines and oversized action-button borders.
- Returned action buttons to IntelliJ's Look-and-Feel.
- Added headless light/dark interaction and rendering regression checks.

### 0.4.5.7-java17-multiide — Reliable Views + main editor tab

- Replaced fragile component JTabbedPane custom painting with explicit segmented buttons and a CardLayout; each click changes a view reliably.
- Improved navigation contrast for selected vs hovered states in light and dark IDE themes.
- Simplified the component actions toolbar with an accessible More actions menu.
- Added a central editor-tab implementation using a project-scoped LightVirtualFile and FileEditorProvider.
- Added entry points from the panel header and Tools menu; sidebar remains available.
- Preserved all Archvow CLI commands, inline decisions, questions and Full vow.

### 0.4.5.5-java17-multiide — Subtle navigation motion

- Gentle hover tint on primary, overview, Quick treatment and component tabs.
- Selected tabs use a stronger accent plus a short underline transition.
- Soft hover/selection colors in the component tree, commands, pending/resolved items, decisions, questions and safety lists.
- Subtle hover feedback on action buttons without replacing the IDE's native button rendering.
- Smooth transitions on the Swing event thread, with timers stopping as soon as transitions finish.
- Theme-aware colors (light/dark), including scrolled component tabs.
- No new dependencies, backend changes or profile permissions; all 40 commands preserved.

### 0.4.5.3 — Compact component reading & Full vow

- Use compact, selectable decision/question details; remove giant empty split areas.
- Display actual component `spec.vow` syntax-highlighted instead of JSON.
- Make Pending / Resolved summary counts navigate to corresponding Quick treatment lists.
- Explain the scope of applicable rules.
- Remove redundant component Details tab and extra Review button.
- Preserve 40 CLI commands and v0.4.5.1 project-onboarding behavior.


### 0.4.5.2 — Inline questions and decisions

- In-place answer/proposal/acceptance for numbered decisions.
- In-place answers for keyed questions and safety hazard decisions.
- Wrapped clickable question lists; no mandatory navigation to Commands.
- Full question context preview in Commands forms.
- CLI-confirmed edits, inline results, and updated quick-treatment counts.


### 0.4.5.1-java17-multiide — Project preflight and inline results

- Based directly on v0.4.5. Does **not** include role/profile filtering.
- Welcome/onboarding screen for no project, missing directory, missing .archvow/spec.vow or inaccessible spec.
- CLI not launched on uninitialized IDE projects; clearly identifiable Python/CLI errors on initialized projects.
- Quick treatment details hidden until an item is selected.
- Removed global Results tab; commands display colored output immediately below where they were executed.
- Added Java 17 runtime regressions for project readiness and output formatting.

## Changelog

### 0.4.5.7 — Editor-like unified navigation

- Unified top-level, overview and quick-treatment tabs.
- Matched the six component Views to the same compact editor-like style.
- Removed overdrawn double underlines and oversized action-button borders.
- Returned action buttons to IntelliJ's Look-and-Feel.
- Added headless light/dark interaction and rendering regression checks.

### 0.4.5.1-java17-multiide

- Plugin Manager SVG logos for light and dark modes.
- Theme-aware component details with color-coded decisions, safety statuses and hazards.
- Human-readable CLI results: command, exit status and output, including explicit empty output.
- Project name, path and Git current branch visible; Git comparison base restored.
- Better responsive layouts without reparenting.

## Changelog

### 0.4.5.7 — Editor-like unified navigation

- Unified top-level, overview and quick-treatment tabs.
- Matched the six component Views to the same compact editor-like style.
- Removed overdrawn double underlines and oversized action-button borders.
- Returned action buttons to IntelliJ's Look-and-Feel.
- Added headless light/dark interaction and rendering regression checks.

### 0.4.5.1-java17-multiide

- Adaptive tool window: compact tabs on narrow IDE windows, wider split layout when space allows.
- Compact toolbar with More menu; full checks, doctor, audit and review stay accessible.
- Quick treatment is populated from the engine's explicit states, not hazard counts or speculative NEXT text.
- Distinct actions for unanswered decisions, AI proposals, unanswered questions, open safety items, assumptions, unclassified columns, tasks, and active check diagnostics.
- Warnings and errors are differentiated. Resolved records are displayed separately.
- Secondary actions allow editing a proposed decision, dismissing an inapplicable hazard, or rejecting an assumption.
- Rules/Data presents readable text, answers, classification and statuses; raw JSON is preserved in the JSON tab.
- Tool window icon remains the green Archvow mark.
- No automatic spec mutations: all writes open existing command forms with human confirmation.
- Compatibility range remains IDE builds 241–262 and Java 17 bytecode. Full build against JetBrains SDK still requires local validation.

## Changelog

### 0.4.5.7 — Editor-like unified navigation

- Unified top-level, overview and quick-treatment tabs.
- Matched the six component Views to the same compact editor-like style.
- Removed overdrawn double underlines and oversized action-button borders.
- Returned action buttons to IntelliJ's Look-and-Feel.
- Added headless light/dark interaction and rendering regression checks.

### 0.4.5.1-java17-multiide

- Added Archvow tool-window stripe icon using the logo.
- Improved overview responsiveness and compact Python guidance.
- Reworked quick-treatment pending/resolved lists to use detailed component snapshots.
- Added styled color rendering for Rules / Data with clearer task states.
- Kept EN/FR support and Java 17 compatibility.

## Changelog

### 0.4.5.7 — Editor-like unified navigation

- Unified top-level, overview and quick-treatment tabs.
- Matched the six component Views to the same compact editor-like style.
- Removed overdrawn double underlines and oversized action-button borders.
- Returned action buttons to IntelliJ's Look-and-Feel.
- Added headless light/dark interaction and rendering regression checks.

### 0.4.5.1-java17-multiide

- Fixed a compilation blocker in ArchvowPanel.scroll(): parameter now uses `java.awt.Component` rather than the Archvow architecture record `NodeComponent`.
- Added compile regression test for Swing Component type and updated version declarations.

### 0.4.5.1-java17-multiide

- Added Archvow branding header with integrated logo.
- Added a quick-treatment overview with pending and resolved items.
- Added automatic popup prompt when the Archvow tool window opens and pending work exists.
- Added colored status chips and richer HTML rendering for overview and checks.
- Added clear distinction between treated and untreated items.
- Added bilingual English/French UI support with default language following the IDE.
- Improved Python configuration help, browse button, examples and backward-compatible handling of `python3 -m archvow`.
- Added overview actions to open settings and test Archvow with `--version`.

### 0.3.2-java17-multiide

- Fixed Python launcher handling so `python3 -m archvow` is split correctly.
- Added a settings test button.
