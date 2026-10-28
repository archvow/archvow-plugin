# 11. Problems and checks

> 🇫🇷 [Version française](/11-problems-and-checks) · English version

`archvow check --json` gives, for each violation, its code (ARCH002...), its severity, its message, its
file, its line and its column. The plugins use it in two places: **Check & Gates** in Overview (page
[Overview](/en/07-overview)) and, in VS Code, the editor's **Problems view**.

## VS Code: the Problems view

| When | Command | What changes |
|---|---|---|
| every reread of the project, every Check | `check --json` | the whole `archvow` diagnostic collection is replaced (`showProblems`) |
| a Python file of the project saved | `check --file <file> --json` | that file's diagnostics only (`checkFile`) |

`problemsOf()` in `vscode/src/model.ts` turns the JSON into problems (file relative to the project, line
and column from 0, error or warning). Each diagnostic carries the source `archvow` and its code. The
check on save is turned off with `archvow.checkOnSave`.

The reread runs in the queue of background commands: what a person asks goes first (page
[Running archvow](/en/05-running-archvow)).

## IntelliJ: Check & Gates, not yet in the editor

In IntelliJ, the diagnostics are in Check & Gates (`ArchvowChecksView`): choosing a diagnostic shows its
detail and its rule's explanation; **Open file** goes to the line (`openDiagnosticCode`,
`openViolationFile`). They are **not yet** underlined in the Python editor: that is item 1 of
`docs/analyse-intellij.md` (an `ExternalAnnotator` that would run
`archvow check --file <f> --stdin --json` with the editor's text).

## A `.vow`'s errors

Not to be confused: the errors of a `.vow` file (a tab, an unclosed quote) come from the plugins' `.vow`
reader, without running archvow, on every keystroke (page [The .vow language](/en/10-vow-language)).
What a keyword means (a decision without a question, a hazard without an invariant), `archvow check`
says.

## What a diagnostic can offer

When a diagnostic's message holds a fix documented by archvow (for example the
`component edit ... --signature` command to run), *Other / Project* offers to open it in the command's
form, prefilled and confirmed like any command. `ArchvowIssueAction` and `issueFix()` recognise only
known forms; **they never run a message's text**.

ARCH039 (archvow 1.17, briefs) gives two of them: a brief changed after an item was drawn from it ->
**Accept (reviewed)**, the `archvow brief accept <file#section>` form (a person's command), with
**Review the brief** next to it (opens it at its heading); an item drawn from a folder no longer declared
-> **Add this folder (architect)**, the `init --force` form with `briefs=<current>,<folder>`.
