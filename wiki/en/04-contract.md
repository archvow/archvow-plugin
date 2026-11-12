# 4. The contract

> 🇫🇷 [Version française](/04-contract) · English version

`contract/` is what both plugins and the archvow CLI have in common. Both plugins are built from these
files and tested against them: **a change here is a change the plugins must follow**. It is recorded
from a real archvow CLI, never written by hand.

## What it holds

| Folder or file | What | Written by | Read by |
|---|---|---|---|
| `contract/cli/version.txt` | `archvow --version` (today `archvow 2.0.1`) | `contract/tools/regenerate.py` | `ArchvowVersion.KNOWN`, `knownVersion()`: the archvow the plugin is tested with and installs |
| `contract/cli/contract.txt` (0.20.0) | the contract number of `archvow version --json` (MAJOR.MINOR, today `1.1`: 1.0 plus `workspace`) | `regenerate.py` | `ArchvowVersion.CONTRACT`, `contractWarning()`: the contract the plugin speaks |
| `contract/cli/errors/` (0.20.0) | three commands that fail, as the plugins get them (`ARCHVOW_LOG=info`): `no-spec`, `refused` (the error envelope on stdout, the message and archvow's log lines on stderr), `usage` (an argparse refusal: no envelope) | `regenerate.py` (`errors()`) | `cli.test.ts`, `OutputRegression` |
| `contract/cli/commands.json` | every command and its parameters, drawn from archvow's argparse parser | `contract/tools/export_grammar.py` | `ArchvowGrammar`, `vscode/src/grammar.ts`: the Commands forms |
| `contract/forms/forms.json` | how each form reads (0.14.0, design A): labels in plain words, essential fields and their order, the button's verb, what archvow will do - the plugins' own file, not archvow's | by hand | `ArchvowForms`, the page's `commandForm()`; `FormsRegression`, `grammar.test.ts` check its names against `commands.json` |
| `contract/forms/forms.json` (0.15.0) | also: `files` (what each command writes, the form's Files block), `extra_parameters` (the plugins' own fields: `output`, Save to a file, on `changes` and `diff`), `smart_answers.parameters` (the `name: type = default` table) | by hand | `filePlan()`, `ArchvowFilePlan`, `withPluginParameters()`, `ArchvowGrammar`, `ArchvowColumnAnswerPanel` |
| `contract/forms/forms.json` (0.16.0) | also: `ai_skills` - the five AI skills (label, what each does, reads, may add, what the person decides, the request each tool gets) and the origin tags' words | by hand | `vscode/src/aiSkills.ts`, the page's `skillForm()` and `aiMenu()`, `ArchvowAiSkills`, `ArchvowSkillForm` |
| `contract/forms/ai-skills.scenarios.json`, `ai-skills.expected.json` | component views (archvow 1.20 `show --json` fields) and the AI view both plugins compute from each: origins, To review counts, suggestion, blocked skills | scenarios by hand; expected: `UPDATE_AI_SKILLS=1 npm test` | `aiSkills.test.ts`, `AiSkillsRegression`: IntelliJ must give exactly the same |
| `contract/forms/icons.json` | the icons of the Components and Commands trees (0.15.0): one drawing per component type, command group and verb, on a 14 x 14 grid | by hand | the page's `icon()` (as SVG), `ArchvowTreeIcons` (Java2D); `filePlan.test.ts`, `FilePlanRegression` check that no type, group or command lacks one |
| `contract/forms/file-plans.expected.json` | what `filePlan()` gives on 21 examples in English and in French, the layers to add a component to, the lines of Save to a file | `UPDATE_FILE_PLANS=1 npm test` | `FilePlanRegression`: `ArchvowFilePlan` must give exactly the same |
| `contract/forms/examples.expected.json` | each example's state (passing, failing, no test) as the page says it, in English and in French | `UPDATE_EXAMPLES=1 npm test` | `ExamplesAndSearchRegression`: `ArchvowExamples.state` must say the same |
| `contract/cli/python-app/examples/` (`passing.json`, `failing.json`, `no-test.json`), `contract/cli/<example>/search/<name>.json` | `show invoices_cli --json` with a passing, a failing and an untested example; `show <name> --json` on names found and one found nowhere (`mark_payd`) | `regenerate.py` (`examples()`, `SEARCHES`) | `examples.test.ts`, `ExamplesAndSearchRegression` |
| `contract/cli/catalog.json` | `archvow catalog --json` outside a project: the templates and their questions | `regenerate.py` | `ArchvowInitCatalog`, the `init` wizard when the CLI does not answer; `componentTypes()` |
| `contract/cli/rules.md`, `rules.fr.md` | the meaning of each check code (archvow's `docs/rules*.md`) | `regenerate.py` (copy) | `ArchvowRuleDocs`, `ruleExplanation()`: the explanation under a diagnostic |
| `contract/cli/<example>/show.json`, `next.json`, `check.json`, `show/<component>.json` | what the CLI prints on archvow's three examples | `regenerate.py` | the VS Code tests (`vscode/src/test/workspace.test.ts`, `layout.test.ts`) and both plugins' test setups |
| `contract/cli/briefs/` (`show.json`, `next.json`, `check.json`, `show/<component>.json`, `refused.txt`) | python-app with Spec Kit's `specs/` declared as a brief folder (`-a briefs=briefs,specs`): `mark_paid` drawn from `specs/001-orders/plan.md#paying-an-invoice` (`source`), ARCH039 once that section changed, and archvow's refusal of `--from` before the folder was declared | `regenerate.py` (`briefs()`) | `vscode/src/test/briefs.test.ts`, `BriefsRegression` |
| `contract/vows/<example>/.archvow/**/*.vow` | the vows archvow wrote on its examples | `regenerate.py` | both `.vow` readers (`CoreTests`, `vscode/src/test/vowReader.test.ts`) |
| `contract/archvow/` | the archvow the plugins are tested with: the wheel of `contract/cli/version.txt`, archvow's three examples with their code, `docs/rules*.md` | `contract/tools/take_archvow.sh` | `regenerate.py` (CI job `contract`), `vscode/src/test/live.test.ts` |
| `contract/cli/messages.json` | every check message archvow's own test suite makes archvow 1.26 print | `contract/tools/harvest_messages.py` (runs archvow's tests with a hook) | `vscode/src/test/guidance.test.ts`, `GuidanceRegression` |
| `contract/overview/expected.json` | what the Overview's cards read of each example's `workspace.json` (0.25.0): first steps, first finding, suggested answers | `UPDATE_OVERVIEW=1 npm test` | `OverviewRegression`: `ArchvowOverview` must read exactly the same |
| `contract/guidance/expected.json` | what `guidance.ts` says on each of those messages, in English and French | `UPDATE_GUIDANCE=1 npm test` | `GuidanceRegression`: `ArchvowGuidance` must say exactly the same |
| `contract/cli/kinds.json` | the kinds of each component type (`component add -k`), for an archvow whose catalog does not list them (1.17; archvow 1.18 gives them in `catalog --json`) | `contract/tools/export_kinds.py` (by `regenerate.py`) | `choices.ts`, `ArchvowChoices`: the `--kind` list |
| `contract/choices/expected.json` | what each command form offers (`choiceBook()`) on the three examples, in English and French, and the comparison base on a few repositories | `UPDATE_CHOICES=1 npm test` | `ChoicesRegression`: `ArchvowChoices` must offer exactly the same |
| `contract/vow-reader/expected.json` | what the Java reader gives (tokens, problems, header, links) on every vow and on 400 vows broken on purpose | `./gradlew contractDump` (`DumpContract`) | the TypeScript reader must give exactly the same |

## How the plugins carry it

The contract is not read from `contract/` at run time: it is copied into each plugin when it is built.

- **IntelliJ**: the `processResources` task of `intellij/build.gradle.kts` copies `commands.json`,
  `catalog.json`, `kinds.json`, `rules.md`, `rules.fr.md` and `version.txt` into the resources, under `archvow/`.
- **VS Code**: `vscode/scripts/sync-contract.js` copies the same files into
  `vscode/resources/archvow/`, on every `npm run compile`, `npm test` and `npm run package`.

## After a new archvow version

```bash
contract/tools/take_archvow.sh ../archvow v1.19.0 ../archvow/dist/archvow-1.19.0-py3-none-any.whl
python3 -m venv /tmp/av && /tmp/av/bin/pip install contract/archvow/archvow-*.whl
python3 contract/tools/regenerate.py contract/archvow --python /tmp/av/bin/python
(cd intellij && ./gradlew contractDump check)
(cd vscode && npm test)
git diff contract/          # review it: this is what changed for the plugins
```

`regenerate.py` copies each archvow example into a temporary folder (nothing is written in the archvow
tree), runs `show`, `next`, `check`, `show <component>` and `workspace` there, and writes the indented JSON
(`workspace.json`'s digest, made of file times, is written `<digest>`). A command
that exits with an error (a code other than 0 or 1) or prints nothing stops everything: a half
regenerated contract would be worse than the old one.

What to look for in the diff:

| Change | What to do |
|---|---|
| a new command or option in `commands.json` | nothing: the forms are built from the grammar |
| a new field in `show --json` | show it where it makes sense (component header, card, panel) in both plugins |
| a renamed or removed field | fix both readers (`ArchvowPanel`, `workspace.js`); the tests point to it |
| `version.txt` | the archvow the plugins are tested with and install: change the READMEs' text (`release.test.ts` says where) |
| `contract.txt` | MINOR up: archvow added something the plugin may use; MAJOR up: something it reads was removed or changed meaning - both readers must follow, and the plugins then refuse the old archvow |
| `expected.json` | regenerated by `contractDump`: if `npm test` fails, the TypeScript reader no longer follows the Java one |

## The archvow version and the contract (0.20.0)

archvow 1.23 says its contract: `archvow version --json` gives `{"contract": "2.1", "archvow": "2.1.0",
...}`, and every `--json` object starts with `"contract"`. `ArchvowVersion.warning` and `contractWarning()`
compare it with `contract.txt`: the same MAJOR and a MINOR at least the plugin's, nothing; an older one, or
an archvow that says no contract (before 1.23: it has no `version` command, its `--version` gives the
number), "older: update archvow"; a later MAJOR, "newer: update the plugin". So an archvow release that
keeps the contract needs no plugin release, while archvow still keeps no backward compatibility: the
contract number is what tells. Install puts the tested archvow exactly (`archvow==2.1.0`).

Contract 1.1 (archvow 1.25, plugin 0.21.0) adds `archvow workspace --json`: what the plugins load, in one
process (`readWorkspace()` in `model.ts`, `WorkspaceAnswer` in IntelliJ, both tested on
`contract/cli/<example>/workspace.json`). Each part has the shape of its own command (`project` is `show
--json`, `components.<id>` is `show <id> --json`, then `next`, `check`, `briefs` and `catalog`); only the
top of the answer says `"contract"`. `--since <digest>` answers `{"unchanged": true}` while no file of the
project changed (paths, sizes and modification times).

Contract 1.2 (archvow 1.26, plugin 0.22.0) adds packs: `workspace --json` has `packs` (each pack of the
project, what it brings, its source, `state` `ok` or `changed`, `why`), `catalog --json` has `packs` and
`agents`, and templates are named `<pack>/<template>` (`archvow/python-app`); the `pack new/template/agent/
rule/add/update/remove/check` commands replace `template new`. The plugins read `packs` (the Packs row, page
7), have one form per `pack` command and no longer pass `ARCHVOW_TEMPLATES`.

Contract 2.0 (archvow 2.0, plugin 0.24.0) organises the spec by feature, a new MAJOR: the vows are
`.archvow/project.vow` and `.archvow/features/<feature>/{<feature>.vow, components/<id>.vow, proposals.vow}` (`layout.ts`,
`ArchvowLayout`), nothing generated is committed (no `spec.md`: the Spec view runs `archvow prompt`, the checks
reference runs `archvow doc`), `show --json` lists the `features` and each component its `feature`, `workspace --json`
adds `features` (each as `show <feature> --json`: its components with their status, file and vow, `uses` and `used_by`
by feature), and the commands `feature add/rename/remove`, `component move`, `doc`, `prompt --feature`, `next --feature`
are new. The file plans name `{home}` (the feature a component is in) and `{feature}` (the one the form names).

Contract 1.3 (archvow 1.27, plugin 0.23.0) adds the privacy regimes: `catalog --json` (and the catalog of
`workspace --json`) has `regimes` (name, prefix, letter, title, law, the codes and the register of each), a pack
says the regimes it holds, `column set` takes `--tag`, and the personal-data codes are PRIV001-006 (GDPR001, 004-007,
009 before) next to each regime's own (GDPR002, USP001, GLBA003...). Nothing in the plugins names a regime: the
questions of each one (P1, U1, G1...) are component questions like the others, the `rules` choices come from the
catalog, and a regime's codes are explained from archvow's rules pages.
