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
| `NOT_INITIALIZED` | no `.archvow/spec.vow`: **Initialize Archvow** (the `init` wizard, page [Commands](/en/09-commands)) |
| `SPEC_INACCESSIBLE` | `spec.vow` empty or unreadable |
| `READY` | the workspace; if the CLI fails all the same, its message, with Configure Python and Retry |

## The "Project status" strip

Two counters: **pending** and **resolved**. They count the work items the plugins build from what
archvow said: decisions and questions without an answer, safety questions open or without an invariant,
assumptions to review, columns to classify, tasks, and the `check` diagnostics tied to a component.
IntelliJ: `rebuildQuickModels`; VS Code: `buildWork()` in `vscode/src/workItems.ts`, which follows the
same rule (and says so in its comments).

A click on a counter opens, in the tab, the first component that has such work, on the right view and
item (`openQuickList`); if there is only project work, the *Other / Project* view.

Under the strip, on `main`, `master`, `develop` or `development`, an advice: create a feature branch
(`ArchvowBranchAdvice`, `needsFeatureBranch()`). It is advice, nothing more: the branch policy belongs
to the repository and the CI. It wraps over several lines in a narrow window (`ArchvowWrapLabel`).

## The action bar

| Button | Command | Where the result goes |
|---|---|---|
| ↻ | reads the project again | - |
| Check | `check --json` | the tab; and Check & Gates is updated |
| Next | `next --json` | the tab |
| Full check | `check --complete --json` | the tab |
| Doctor | `doctor --json` | the tab |
| Audit | `audit --json` | the tab |
| Project vow | opens `.archvow/spec.vow` | an ordinary editor tab |
| Budget / Next | `next --base <Base> --json` | the tab |
| Diff, Review | the `diff` / `review` form, `--base` already filled | the tab |

In IntelliJ, the buttons that do not fit go into **More ▾** (`ArchvowOverflowBar`: it measures the
buttons' real width, no fixed threshold). The **Base** field (`origin/main` by default) is used by
Budget, Diff and Review.

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
list of diagnostics. The chosen diagnostic shows its message, its file, and **its rule's explanation**,
taken from `contract/cli/rules.md` (`ArchvowRuleDocs`, `ruleExplanation()`). View rule opens the checks
reference; Open file goes to the line.

## The language

At the bottom of Overview: the workspace's language (the IDE's, English or French). It is there, and
not in the header, so that the header keeps room for the project's name in a narrow window.
