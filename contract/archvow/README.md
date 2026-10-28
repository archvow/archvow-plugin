# The archvow the plugins are tested with

What CI installs and runs instead of fetching archvow: the wheel of the archvow version the contract was
recorded from (`../cli/version.txt`), and from archvow's source at that version, what the wheel does not
hold - the three examples with their code (`examples/`) and what each check code means (`docs/rules*.md`).

| Used by | How |
|---|---|
| GitHub Actions job `contract` | `pip install` the wheel, then `contract/tools/regenerate.py contract/archvow`: the contract must not change |
| GitHub Actions job `live`, `vscode/src/test/live.test.ts` | the wheel in a venv; the examples run with their code and must print exactly the contract |

After an archvow release, replace it (then regenerate, see `../README.md`):

```bash
contract/tools/take_archvow.sh ../archvow v1.18.0 ../archvow/dist/archvow-1.18.0-py3-none-any.whl
```
