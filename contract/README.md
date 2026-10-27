# The contract

What the two plugins and the archvow CLI agree on. Both plugins test against these files, so a change
here is a change the plugins must follow.

| Folder | What | Written by | Read by |
|---|---|---|---|
| `vows/` | the vows of archvow's three examples | `tools/regenerate.py` (from archvow) | both `.vow` readers (`coreTest`, `npm test`) |
| `cli/<example>/` | what `show --json`, `show <component> --json`, `next --json` and `check --json` print on each example; `cli/version.txt` the archvow version | `tools/regenerate.py` | the VS Code extension's tests; the IntelliJ workspace reads the same commands |
| `vow-reader/expected.json` | what the Java `.vow` reader gives (tokens, problems, header, links) on every sample and on 400 vows broken on purpose | `./gradlew contractDump` (intellij/) | the VS Code extension's reader must give exactly the same |

After an archvow release:

```bash
python3 contract/tools/regenerate.py ../archvow            # or: ... ../archvow /path/venv/bin/archvow
(cd intellij && ./gradlew contractDump check)
(cd vscode && npm test)
git diff contract/                                         # review: this is what changed for the plugins
```

Recorded from archvow 1.16.0.
