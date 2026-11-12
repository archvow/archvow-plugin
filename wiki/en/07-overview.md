# 7. Overview

> 🇫🇷 [Version française](/07-overview) · English version

Overview is the first tab of the sidebar: where the project stands, its architecture, its checks, and
the commands that apply to the whole project.

## Before running anything: is the project ready?

`ArchvowProjectReadiness` (VS Code: `readiness()` in `vscode/src/projectState.ts`) looks at the disk
only, without starting Python for a project that is not an archvow project:

| State | What the plugin shows |
|---|---|
| `NO_PROJECT` | "Open a folder" |
| `ROOT_MISSING` | the project folder cannot be found |
| `NOT_INITIALIZED` | no `.archvow/project.vow`: **Initialize Archvow** (the `init` wizard, page [Commands](/en/09-commands)), how to adopt code already there, and (0.25.0) **Not sure yet? See it catch a mistake**: `archvow try <example>` on a finished example in a scratch folder, its result under the card, then *Set up archvow in this repository* or *Read the example's spec* |
| `SPEC_INACCESSIBLE` | `project.vow` empty or unreadable |
| `READY` | the workspace; if the CLI fails all the same, its message, with Configure Python and Retry |

## The top of Overview (0.25.0)

From the top, the same in both IDEs (VS Code: `overviewTab()` in `vscode/media/workspace.js`; IntelliJ:
`overviewPanel()`, the cards of `ArchvowOverviewCards`): the branch advice, **First steps** (for a new
project), the commands bar, **Architecture**, **Checks**, **Packs**. The "Project status" strip (pending,
resolved) is gone in 0.25.0: its counters are the Architecture card's links.

On `main`, `master`, `develop` or `development`, an advice: create a feature branch (`ArchvowBranchAdvice`,
`needsFeatureBranch()`). It is advice, nothing more: the branch policy belongs to the repository and the CI.

## First steps (0.25.0, archvow 2.1)

Five steps archvow reads from the spec, the check and Git (`workspace --json` "first_steps", contract 2.1;
`views/first_steps.py` in archvow): answer the foundations, add a feature, add its first component and answer
its decisions, build it then Check, commit. The plugin computes nothing: it shows "N of 5 done", the first step
not done with its button (Specify, Add feature, Add component, Check; none for Commit) and the link "Try archvow
on an example first" (`archvow try`). The card goes once the five are done: the examples, finished, never show
it. Reading: `firstSteps()` in `vscode/src/overview.ts`, `ArchvowOverview.firstSteps`; both read the examples
the same (`contract/overview/expected.json`, `OverviewRegression`).

## The commands bar

| Button | Command | Where the result goes |
|---|---|---|
| Next | `next --json` | the tab |
| Check | `check --complete --json` | the tab; and Check & Gates is updated |
| More ▾ › Doctor | `doctor --json` | the tab |
| More ▾ › Audit | `audit --json` | the tab |
| More ▾ › Briefs | the briefs browser | the tab |
| More ▾ › Budget / Next | `next --base <Base> --json` | the tab |
| More ▾ › Diff, AI review | the `diff` / `review` form, `--base` already filled | the tab |

Next and Check are named; **More ▾** opens the six others, each with its icon (`projectToolbar()`;
`ArchvowOverviewCards.toolbar`). ↻ (read the project again) is in the header. The **project's vow** has no
button (0.24.3): it is read through the commands and the pages. The **Base** is used by Budget, Diff and
Review: the best branch that exists here (`bestBase`: `develop` on a Git flow feature branch, otherwise what
`origin/HEAD` names, then `origin/main`, `main`, `master`...), until a person chooses one in the list of the
repository's branches and tags (`git for-each-ref`, `refNames`). A base that does not exist is refused before
anything runs, with the branches that do (`missingRef`, `refProblem`).

## The Architecture and Checks cards (0.25.0)

**Architecture** counts its own items - decisions, questions, safety questions, assumptions, columns, tasks:
"Items done 45 of 70", a bar, "25 open: decisions, questions and tasks. Check findings count in Checks." (except
while First steps is there), then **Open items**, **Done items** and, when there are some, **Project items**:
the first component with such work, on the right view and item (`openQuickList`), or the *Other / Project*
view. The items come from `buildWork()` (`vscode/src/workItems.ts`) and `rebuildQuickModels` (IntelliJ);
`check` findings do not count there (`overview()` in `vscode/src/overview.ts`, `ownItem` in `ArchvowPanel`).

**Checks** gives the errors and warnings, then the **first finding**: its rule in words (the `title` of `check
--json`), `ARCH005 · compute_vat.py:16`, and the button that opens the file, named by what it is (Open source,
Open vow, Show configuration folder: `checkFileLabel`, `ArchvowCheckActions.fileLabel`). Without a finding, one
line: the totals and "Open Checks →".

## Results

A result is not raw JSON: `ArchvowResultFormatter` (VS Code: `renderResult()` in
`vscode/src/formatter.ts`) shapes it by command: `check` (totals, then each diagnostic), `audit`,
`doctor` (to fix, warnings, what is fine), `next`, `budget`, `catalog`, and a general form for the
rest. It shows only the values archvow gave, never a rebuilt `.vow` text. **Raw output** shows what the
CLI wrote, **Copy** copies it.

For a known error ("No module named archvow", an executable not found, access denied), `advice()` adds
what to do at the top.

## The architecture

One row per component: Component, Type, Layer, Container, **State**, **Pending**.
`ArchvowArchitectureView` (VS Code: `architectureView()` and the `table.arch` table). The state comes
from `check --json` when it gives one, otherwise from the component's `show`; it is translated
(`localizedState`).

In a narrow sidebar, **Component, State and Pending stay visible**: Container goes first, then Layer,
then Type (the row's tooltip gives them all); then component names are cut with "…"; only below that
does the table scroll sideways. IntelliJ: `ArchvowArchitectureView.columnWidths` computes the widths
from each column's text; VS Code: `@media` rules in `vscode/media/workspace.css`.

A double click (IntelliJ) or a click (VS Code) opens the component in the tab.

## Check & Gates

`ArchvowChecksView` (VS Code: `checksView()`): PASSED or FAILED, the errors and warnings, the count per
rule when there are several (`counts` of `check --json`, "By rule: ARCH012 1 · ARCH053 1"), then the
list of diagnostics, each row led by its rule in words (archvow 2.1's `title`), then its code and severity, then the
message (0.25.0). The chosen diagnostic shows its message, its file, and **its rule's explanation**,
taken from `contract/cli/rules.md` (`ArchvowRuleDocs`, `ruleExplanation()`). View rule opens the checks
reference; Open file goes to the line.

## Briefs (0.16.0)

A third sub-tab beside Architecture and Check & Gates, from archvow 1.20's `brief show --json` (read with the
architecture; an older archvow has no such command: the tab says so). Each brief file, section by section: ✓
used (the items drawn from it), ○ not used yet with **Draft with AI…** (the draft-spec launch form, the section
filled in), ! changed since with **Review…** (opens the section) and **Accept change…** (the `brief accept`
form). Its count is the sections not used plus the ones changed; More ▾ › Briefs
opens it. **Add a briefs folder (architect)…** opens the init form prefilled. VS Code: `briefsView()`,
`briefsDue()`; IntelliJ: `ArchvowBriefs.statusHtml`, `ArchvowBriefs.due`, links `DRAFT_LINK`, `ACCEPT_LINK`.

## Packs (0.22.0)

A third row under Architecture and Checks, from `packs` of archvow 1.26's `workspace --json` (read with the rest,
no extra call): "2 packs · 1 changed by hand". **Open Packs →** lists each pack of `.archvow/packs/`: its
version, what it brings (templates, agents, rules, privacy regimes), its source, and whether it is as added or changed by hand
(ARCH060, with what differs). Each has **Update**, **Remove** and **Check** (the forms of `pack update`, `pack
remove`, `pack check`, its name filled in); a changed pack also has **See the change** (`diff`). At the top,
**Add a pack** and **New pack** (`pack add`, `pack new`). Both plugins do the same: VS Code `packsSummary()` /
`packsView()` in `vscode/media/workspace.js`; IntelliJ `ArchvowPacks` (the row, the list, what each
`archvow-pack:` link opens), tested by `PacksRegression`.

## The language

At the bottom of Overview: the workspace's language (the IDE's, English or French). It is there, and
not in the header, so that the header keeps room for the project's name in a narrow window.
