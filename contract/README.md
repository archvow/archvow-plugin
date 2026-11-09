# The contract

What the two plugins and the archvow CLI agree on. Both plugins test against these files, so a change
here is a change the plugins must follow.

| Folder | What | Written by | Read by |
|---|---|---|---|
| `vows/` | the vows of archvow's three examples | `tools/regenerate.py` (from archvow) | both `.vow` readers (`coreTest`, `npm test`) |
| `cli/<example>/` | what `show --json`, `show <component> --json`, `next --json` and `check --json` print on each example; `cli/version.txt` the archvow version; `cli/contract.txt` its contract number (MAJOR.MINOR, `archvow version --json`) | `tools/regenerate.py` | the VS Code extension's tests; the IntelliJ workspace reads the same commands |
| `cli/errors/` | three commands that fail, as the plugins get them (archvow 1.23, `ARCHVOW_LOG=info`): the error envelope on stdout, the message and archvow's log lines on stderr; `usage` is an argparse refusal, with no envelope | `tools/regenerate.py` | the output readers of both plugins (`cli.test.ts`, `OutputRegression`) |
| `archvow/` | the archvow the plugins are tested with: its wheel (the version of `cli/version.txt`), and from its source the three examples with their code and `docs/rules*.md` | `tools/take_archvow.sh` | `regenerate.py` (CI job `contract`), the live tests (`vscode/src/test/live.test.ts`) |
| `cli/messages.json` | every check message archvow's own tests make it print (587, 72 codes) | `tools/harvest_messages.py <archvow source> --python <venv>` | the guidance tests of both plugins |
| `guidance/expected.json` | what a diagnostic's guidance says on each of those messages (en, fr) | `UPDATE_GUIDANCE=1 npm test` (vscode/) | `GuidanceRegression`: IntelliJ must say the same |
| `vow-reader/expected.json` | what the Java `.vow` reader gives (tokens, problems, header, links) on every sample and on 400 vows broken on purpose | `./gradlew contractDump` (intellij/) | the VS Code extension's reader must give exactly the same |
| `forms/forms.json` | how each command's form reads (design A, 0.14.0): plain labels, the essential fields first, the button's verb, what archvow will do | by hand (the plugins' own file) | both plugins (`ArchvowForms`, the page's `commandForm()`); `FormsRegression` and `grammar.test.ts` check every name against `cli/commands.json` |

After an archvow release:

```bash
contract/tools/take_archvow.sh ../archvow v1.23.0 ../archvow/dist/archvow-1.23.0-py3-none-any.whl
python3 -m venv /tmp/av && /tmp/av/bin/pip install contract/archvow/archvow-*.whl
python3 contract/tools/regenerate.py contract/archvow --python /tmp/av/bin/python
python3 contract/tools/harvest_messages.py ../archvow --python ../archvow/.venv/bin/python   # its venv with archvow[dev]
(cd vscode && UPDATE_GUIDANCE=1 npm test)                 # review git diff contract/guidance: what the IDEs now say
(cd intellij && ./gradlew contractDump check)
(cd vscode && npm test)
git diff contract/                                         # review: this is what changed for the plugins
```

Recorded from archvow 1.23.0.
