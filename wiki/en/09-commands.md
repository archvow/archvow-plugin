# 9. Commands

> 🇫🇷 [Version française](/09-commands) · English version

The Commands tab of the sidebar lists **every archvow command**; a chosen command opens its form in the
tab. A component's actions (Edit spec, + Question, Accept assumption...) open the same forms,
prefilled.

## Forms drawn from archvow's parser

The forms are not written by hand: they are built from `contract/cli/commands.json`, which
`contract/tools/export_grammar.py` draws from archvow's argparse parser (page
[The contract](/en/04-contract)). A new option in archvow is in the form as soon as the contract is
regenerated, without changing the code.

Each parameter has a name, its options (`-d`, `--description`), required or not, a check box
(boolean), a list of choices, several values (one per line), and its help (shown under the field).
IntelliJ: `ArchvowGrammar.COMMANDS`, `renderForm`; VS Code: `commandForm()` in the page,
`findCommand()`. Long text fields (`description`, `answer`, `statement`, `words`...) span several lines
(`LONG_TEXT`). The options archvow hides from `--help` (`argparse.SUPPRESS`: `--verify`, `--evidence`,
`--rule` of `invariant add`) are flagged `hidden` by `contract/tools/export_grammar.py` and put under
"Expert checks", closed (0.13.1: their help showed `==SUPPRESS==`).

## How a form is laid out (design A, 0.14.0)

The grammar says what a command takes; `contract/forms/forms.json` (the plugins' own file, read by both)
says how its form reads:

| In forms.json | In the form | Code |
|---|---|---|
| `labels` (by parameter), `commands.<c>.labels` | the field in plain words ("What it must do", "Always true"), archvow's option small beside it | `fieldLabel()`, `ArchvowForms.label` |
| `commands.<c>.essential` | those fields first, in this order (and every required one); the others under **More options**, opened when one holds a value; the expert checks inside it | `isEssential()`, `ArchvowForms.essential` / `ordered` |
| `commands.<c>.title`, `verb`, `does` | the form's title; the button ("Add invariant", next to Cancel); "What archvow will do" on the right | `commandForm()`, `buildForm` |
| `commands.<c>.hints` | the line under a field, instead of archvow's help | |

A command or a parameter missing there falls back to archvow's words. A test in each IDE checks that every
name in forms.json is one of `commands.json`, and VS Code's that every required field is essential.
A parameter with at most four fixed values (severity, criticality, verify...) is one button per value
(`segmented()`, `ArchvowSegmented`).

On the right, **the command as it is typed**: VS Code's page asks the extension (`formPreview`, debounced),
which plans it with `planForm` - the grammar Run uses - and writes it in place (no redraw: the cursor stays);
IntelliJ computes `ArchvowForms.commandLine` at each change. A required field still empty is named instead.

**archvow's refusal in place**: after Run, exit code 2 (archvow refused, nothing written) shows its words
in red under the form; the warnings of what it wrote anyway (the `warnings` of a `--json` entry, or the
`!` lines) in amber (`alerts()` in `grammar.ts`, `ArchvowForms.refusal` / `warnings`, `showFormAlerts`).

## What a field offers

Nobody has to know an id by heart (0.13.0). `choiceBook()` in `vscode/src/choices.ts` (IntelliJ:
`ArchvowChoices.book`, the same rules: `ChoicesRegression` compares both on `contract/choices/expected.json`)
says, for each command and parameter, what the form offers, from what archvow printed (`show --json`,
`show <component> --json`, `catalog --json` run in the project, the repository's refs):

| Field | Offers |
|---|---|
| `component` (every command but `component add`) | the project's components, "clean_orders — transformation"; for **Edit spec**, choosing one fills the fields with what it has |
| `decision make` question | the component's questions, the open ones first, then P1, O1...; choosing one fills the answer recorded, proposed or suggested |
| `decision propose` / `decision accept` | the open questions / the AI's proposals waiting |
| `question remove` number | the questions a person added (archvow 1.18 marks the template's: `template: true`; they stay) |
| `hazard edit` / `invariant edit` id | the project's hazards / invariants; choosing one fills its words (and severity); an invariant linked to a decision has no words to fill |
| `invariant add --from-decision` | the component's decisions that have an answer: the invariant is what that decision answers |
| `question add` / `decision add` | no number to give: the line under the field says which one archvow will give |
| `assumption accept/reject`, `example remove`, `prompt --task` | the component's assumptions waiting, examples, tasks |
| `hazard add/dismiss` guide word, `invariant add --prevents` | the safety questions (open first) / the component's hazards |
| `hazard remove`, `invariant remove`, `rule edit/remove`, `dependency remove` | the project's ids; `rule edit` fills the rule's text |
| `component add` type and `--kind` | the types of the project's template only; the kinds of the chosen type, the default first (archvow 1.18's catalog, else `contract/cli/kinds.json` for 1.17) |
| `template new --extends` | the templates of the catalog |
| `--base`, `changes` start/end | the branches and tags of the repository |

A `select` field takes only what it lists; a list that depends on the component or the type waits for it
("Choose the component first"); an empty list says why ("No hazard declared in this project."). A
`suggest` field (a branch, a template) also takes what is typed. In IntelliJ the list shows each value's
label at the width of its field, its open list wider (`readableChoices`), and the lines under the fields
wrap at the form's width (`WidthTrackingPanel`, `ArchvowWrapLabel`): they ran past the window's edge.

The `--from` field (component add / edit, question add, decision add, rule add) offers the `.md` and
`.txt` files of the project's brief folders and each Markdown heading as `file#section`, with archvow's
own anchors: lower case, no accents, words joined by `-`, a repeated heading `-2`, `-3`
(`ArchvowBriefs.slug`, `slug()` and `sections()` in `vscode/src/briefs.ts`). Free text stays allowed:
archvow decides. When archvow refuses a file outside the folders, its message names the
`archvow init <package> --force -a briefs=...` that adds it; the result shows **Add this folder
(architect)**, which opens the init form prefilled (`addFolderButton()`, `ArchvowBriefs.withAddFolder`) -
the architect checks it and confirms; nothing runs on its own.

## From the form to the command line

`planForm()` (IntelliJ: `submitForm`) turns the fields into arguments, in the grammar's order: an empty
required field is refused; a ticked box gives its option; several values give the option before each;
`--json` is added when the command knows it. A few cases are handled by hand:

| Case | Why |
|---|---|
| `specify` is not run: the tab opens the component's first open item | `specify` asks its questions in a terminal |
| `rule add` refuses a component alone, without the rule's text | a rule's text is a person's, never guessed |
| `prompt --task T2` becomes `--task 2` | the CLI expects a number (`ArchvowTaskPrompt`, `taskNumber()`) |
| `init` gets `--no-input` and the wizard's answers (`--answer KEY=VALUE`) | no terminal to ask the questions |

### What a command writes, and Save to a file (0.15.0)

Under the command, in "What archvow will do", the **Files** block (`filePlan()`, `filesBlock()`; IntelliJ
`ArchvowFilePlan`): the files new, updated, removed or kept, from `files` in forms.json and what the project
already says; for `component add`, also the **planned** code and test (the path archvow gives, its function and
signature), which the AI or a developer will write. `changes` and `diff` have a **Save to a file** field
(`output`, a field of the plugins, never on archvow's line): the report is asked as Markdown, written to that
file of the project, then opened (`writeReport()`) - the preview shows `archvow changes --from ... --markdown >
CHANGES.md`. The repository's first commit (`git rev-list --max-parents=0 HEAD`) is offered first for `--from`.

## Confirming before changing

A command that changes nothing (first word in `READ_ONLY`: `show`, `catalog`, `next`, `check`,
`audit`, `doctor`, `changes`, `diff`, `prompt`) goes at once. **Every other command asks for a
confirmation** that shows the exact command (`preview()`) under the button that asked for it; nothing
goes before "Confirm and run" (`isMutating()`). A removal (`DESTRUCTIVE`: `component remove`,
`question remove`, `rule remove`, `hazard remove`, `invariant remove`) also gets `--yes`, after the
person's confirmation. After a successful command that changes something, the plugin reads the project
again.

## Prefill

`ArchvowCommandPrefill.prepare` (VS Code: `prefill()` in `vscode/src/grammar.ts`) fills what is known,
never what would be guessed:

- the command's component, when it takes one and a component is chosen (`usesComponent`);
- **Edit spec** from a component: every field the component already has: the description and `uses`
  (from `show <component>`), the ticket (`--spec`), the owners, the group, the brief (`--from`) and the
  signature (from its entry in the spec). You see what is there and change what you want; only the fields
  changed are sent (`formBaseline`: what the component has, even when opened from a diagnostic;
  `planForm(..., { baseline })`; IntelliJ: `whatTheComponentHas`, `submitForm`), and nothing changed is
  said, not run ("Nothing changed"): so archvow 1.18 marks to review only what really changed;
- a fix proposed by a diagnostic stays **specific**: it fills only its own field, so as not to change
  something else by mistake;
- `component add` gets the chosen component's container, never an invented name.

For `decision make`, `decision propose`, `decision accept` and `question remove`, the form shows the
question and its state ("Load question") before you answer.

## The init wizard

In a folder without `.archvow/spec.vow`, **Initialize Archvow** opens the `init` form with a wizard:
the template, then its questions (the essential ones; the others under "advanced settings"). The
questions come from `archvow catalog --json` run outside the project (the installed templates,
company ones included, `ARCHVOW_TEMPLATES`), otherwise from the bundled copy of the catalog
(`ArchvowInitCatalog`, the catalog of `contract/cli/catalog.json`). `ArchvowInitQuestions` (VS Code:
`initWizard()`) emits only documented answers, `--answer KEY=VALUE`, and only those shown or changed.
The template is chosen **once**, in "Template and its questions", right under the package's name
(0.13.0: the form's `--template` field made it a second place): a template of the catalog, or the
folder of a company template; in a project, its own template first, with a line saying `init` needs
`--force` there.

## Making a company template

`template new`: choosing the base template (`--extends`, the catalog's list) fills "Settings of <base>"
under it with the base's values (`ArchvowTemplateSettings`; VS Code: `templateWizard()`). What the company
may set - the defaults of the init answers, the folder of each layer - can be changed, within what the base
allows: its choices only (a list, nothing to type), its formats (the field turns red, running is refused
with the expected format). What the base imposes - what it extends, its version, its layers, its choices -
is shown, not editable. Only what differs from the base becomes `-a KEY=VALUE` and `--layer OLD=NEW`.

## Readable results

A command's result opens at 225 px: **Expand** makes it as tall as the window (the tab in VS Code), and
the bar under it drags it taller or shorter (`resultGrip`, `expandedResultHeight`; VS Code:
`expandResult`, `data-grip`). Wherever a result shows. Its title "Command result" is no longer said twice
(`generic` in both formatters).

## Showing archvow for a file

Right click in an editor or on its tab: **Show Archvow for This File** runs
`archvow show <file>:<line>` (the caret's line) and opens the component that owns the file; a file no
component owns gets archvow's hint (the `component add ... --file` to run). IntelliJ:
`ShowFileArchvowAction`, `ArchvowPanel.showFile`; VS Code: the `archvow.showFile` command,
`ArchvowProject.showFile`.
