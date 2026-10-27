# Changelog

The plugin joins two former plugins in 0.5.0; their own histories follow.

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
