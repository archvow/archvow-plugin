# The contract

What the two plugins and the archvow CLI agree on. Both plugins test against these files, so a change
here is a change the plugins must follow.

| Folder | What | Written by | Read by |
|---|---|---|---|
| `vows/` | the vows of archvow's three examples | `tools/regenerate.py` (from archvow) | both `.vow` readers (`coreTest`, `npm test`) |
| `cli/<example>/` | what `show --json`, `show <component> --json`, `next --json` and `check --json` print on each example; `cli/version.txt` the archvow version | `tools/regenerate.py` | the VS Code extension's tests; the IntelliJ workspace reads the same commands |
| `archvow/` | the archvow the plugins are tested with: its wheel (the version of `cli/version.txt`), and from its source the three examples with their code and `docs/rules*.md` | `tools/take_archvow.sh` | `regenerate.py` (CI job `contract`), the live tests (`vscode/src/test/live.test.ts`) |
| `vow-reader/expected.json` | what the Java `.vow` reader gives (tokens, problems, header, links) on every sample and on 400 vows broken on purpose | `./gradlew contractDump` (intellij/) | the VS Code extension's reader must give exactly the same |

After an archvow release:

```bash
contract/tools/take_archvow.sh ../archvow v1.18.0 ../archvow/dist/archvow-1.18.0-py3-none-any.whl
python3 -m venv /tmp/av && /tmp/av/bin/pip install contract/archvow/archvow-*.whl
python3 contract/tools/regenerate.py contract/archvow --python /tmp/av/bin/python
(cd intellij && ./gradlew contractDump check)
(cd vscode && npm test)
git diff contract/                                         # review: this is what changed for the plugins
```

Recorded from archvow 1.17.0.
