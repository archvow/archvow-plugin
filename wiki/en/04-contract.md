# 4. The contract

> 🇫🇷 [Version française](/04-contract) · English version

`contract/` is what both plugins and the archvow CLI have in common. Both plugins are built from these
files and tested against them: **a change here is a change the plugins must follow**. It is recorded
from a real archvow CLI, never written by hand.

## What it holds

| Folder or file | What | Written by | Read by |
|---|---|---|---|
| `contract/cli/version.txt` | `archvow --version` (today `archvow 1.17.0`) | `contract/tools/regenerate.py` | `ArchvowVersion.KNOWN`, `knownVersion()`: the version the plugin knows |
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
| `contract/cli/messages.json` | every check message archvow's own test suite makes archvow 1.22 print: 587 messages, 72 codes | `contract/tools/harvest_messages.py` (runs archvow's tests with a hook) | `vscode/src/test/guidance.test.ts`, `GuidanceRegression` |
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
tree), runs `show`, `next`, `check` and `show <component>` there, and writes the indented JSON. A command
that exits with an error (a code other than 0 or 1) or prints nothing stops everything: a half
regenerated contract would be worse than the old one.

What to look for in the diff:

| Change | What to do |
|---|---|
| a new command or option in `commands.json` | nothing: the forms are built from the grammar |
| a new field in `show --json` | show it where it makes sense (component header, card, panel) in both plugins |
| a renamed or removed field | fix both readers (`ArchvowPanel`, `workspace.js`); the tests point to it |
| `version.txt` | both plugins will say "another version" for the old CLI: change the READMEs' text (the CLI version run) |
| `expected.json` | regenerated by `contractDump`: if `npm test` fails, the TypeScript reader no longer follows the Java one |

## The archvow version

`ArchvowVersion.warning` and `versionWarning()` compare the installed CLI's `--version` with
`version.txt`: same major.minor, nothing; another one, a warning that gives both versions. archvow keeps
no backward compatibility: another minor may print what the plugin misreads, and saying so is better
than showing it wrong.
