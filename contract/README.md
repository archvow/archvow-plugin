# The contract

What the two plugins and the archvow CLI agree on. Both plugins test against these files, so a change
here is a change the plugins must follow.

| Folder | What | Written by | Read by |
|---|---|---|---|
| `vows/` | the vows of archvow's three examples (`.archvow/project.vow`, `.archvow/features/<feature>/...`, archvow 2.0) | `tools/regenerate.py` (from archvow) | both `.vow` readers (`coreTest`, `npm test`) |
| `cli/<example>/` | what `show --json`, `show <component> --json`, `next --json` and `check --json` print on each example (pyspark-pipeline without its code and its next story started - `spec-phase.json`, archvow 2.0.1 having finished it), and `workspace.json`: `workspace --json` (archvow 1.25, contract 1.1; its `packs` since archvow 1.26, contract 1.3; its `features`, each as `show <feature> --json`, since archvow 2.0, contract 2.0; its `first_steps` since archvow 2.1, contract 2.1), all of them in one answer - what both plugins load (its digest as `<digest>`); `cli/version.txt` the archvow version; `cli/contract.txt` its contract number (MAJOR.MINOR, `archvow version --json`) | `tools/regenerate.py` | the VS Code extension's tests; the IntelliJ workspace reads the same commands |
| `cli/errors/` | three commands that fail, as the plugins get them (archvow 1.23, `ARCHVOW_LOG=info`): the error envelope on stdout, the message and archvow's log lines on stderr; `usage` is an argparse refusal, with no envelope | `tools/regenerate.py` | the output readers of both plugins (`cli.test.ts`, `OutputRegression`) |
| `archvow/` | the archvow the plugins are tested with: its wheel (the version of `cli/version.txt`), and from its source the three examples with their code and `docs/rules*.md` | `tools/take_archvow.sh` | `regenerate.py` (CI job `contract`), the live tests (`vscode/src/test/live.test.ts`) |
| `cli/messages.json` | every check message archvow's own tests make it print (587, 81 codes) | `tools/harvest_messages.py <archvow source> --python <venv>` | the guidance tests of both plugins |
| `guidance/expected.json` | what a diagnostic's guidance says on each of those messages (en, fr) | `UPDATE_GUIDANCE=1 npm test` (vscode/) | `GuidanceRegression`: IntelliJ must say the same |
| `overview/expected.json` | what the Overview's cards read of each example's `workspace.json` (0.25.0): the first steps, the first finding, each row's suggested answers | `UPDATE_OVERVIEW=1 npm test` (vscode/) | `OverviewRegression`: IntelliJ must read the same |
| `vow-reader/expected.json` | what the Java `.vow` reader gives (tokens, problems, header, links) on every sample and on 400 vows broken on purpose | `./gradlew contractDump` (intellij/) | the VS Code extension's reader must give exactly the same |
| `forms/forms.json` | how each command's form reads (design A, 0.14.0): plain labels, the essential fields first, the button's verb, what archvow will do | by hand (the plugins' own file) | both plugins (`ArchvowForms`, the page's `commandForm()`); `FormsRegression` and `grammar.test.ts` check every name against `cli/commands.json` |

After an archvow release:

```bash
contract/tools/take_archvow.sh ../archvow v2.0.1 ../archvow/dist/archvow-2.0.1-py3-none-any.whl
python3 -m venv /tmp/av && /tmp/av/bin/pip install contract/archvow/archvow-*.whl
python3 contract/tools/regenerate.py contract/archvow --python /tmp/av/bin/python
python3 contract/tools/harvest_messages.py ../archvow --python ../archvow/.venv/bin/python   # its venv with archvow[dev]
(cd vscode && UPDATE_GUIDANCE=1 UPDATE_OVERVIEW=1 npm test)   # review git diff contract/guidance, contract/overview
(cd intellij && ./gradlew contractDump check)
(cd vscode && npm test)
git diff contract/                                         # review: this is what changed for the plugins
```

Recorded from archvow 2.1.0.
