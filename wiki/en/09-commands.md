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
(`LONG_TEXT`). The `type` field of `component add` offers the catalog's types
(`ArchvowComponentTypes`, `componentTypes()`).

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
  signature (from its entry in the spec). You see what is there and change what you want; a field left
  as it is changes nothing;
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
A custom template path stays editable by hand.

## Showing archvow for a file

Right click in an editor or on its tab: **Show Archvow for This File** runs
`archvow show <file>:<line>` (the caret's line) and opens the component that owns the file; a file no
component owns gets archvow's hint (the `component add ... --file` to run). IntelliJ:
`ShowFileArchvowAction`, `ArchvowPanel.showFile`; VS Code: the `archvow.showFile` command,
`ArchvowProject.showFile`.
