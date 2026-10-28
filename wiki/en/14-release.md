# 14. Releasing

> 🇫🇷 [Version française](/14-release) · English version

Both plugins have **the same number**, their own (0.12.0), not archvow's. A release is a commit
`v<version>: <what changes, in one line>` on `main`.

## The numbers to change

| File | What |
|---|---|
| `intellij/gradle.properties` | `pluginVersion` |
| `vscode/package.json`, `vscode/package-lock.json` | `"version"` (the lock twice: the root and `packages[""]`) |
| `README.md` | the `./build.sh` line (the names of the built files) and the Versions section |
| `intellij/README.md` | the built `.zip`, the installation checklist, the version section |
| `vscode/README.md` | the built `.vsix` |
| `intellij/CHANGELOG.md`, `vscode/CHANGELOG.md` | a `## <version> - <date>` section at the top |
| `wiki/home.md`, `wiki/en/home.md` | "Version described" and the line and test counts |

A CHANGELOG says what a person sees change, in bold first, then the class or function; the last line
names the tests added and what was checked by hand. The IntelliJ CHANGELOG and the VS Code one say the
same thing when the change touches both.

## The list before releasing

```bash
(cd intellij && ./gradlew check buildPlugin)     # coreTest, workspaceTest, then the .zip
python3 intellij/tools/verify_built_plugin.py    # the id and since-build of the built .zip
(cd intellij && ./gradlew runIde)                # a try: open an example, a component, a command
(cd vscode && npm ci && npm test && npm run package)
./build.sh                                       # or both at once -> dist/
```

Then, in `runIde` and in VS Code (F5 or the installed `.vsix`), on an archvow example: open a component
(the first open item is chosen), answer a decision (the confirmation shows the command), narrow the
sidebar (Component, State, Pending stay), run Doctor (text at the IDE's size).

## Shipping

`make release VERSION=0.12.0` writes a Git *bundle* of all branches and a `.zip` of the repository. A
release is also shipped as **patches**: `git format-patch` from the previous commit, applied with
`git am`, or with `./apply_patch.sh <file.patch>` (which runs `git apply` on the content, without making
the commit). A checked delivery applies the patch onto the previous version and compares the resulting
tree with the commit's.

For a team, the `.zip` and the `.vsix` go to an internal plugin repository (a JetBrains
`updatePlugins.xml`, a self-hosted Open VSX) or a share: the plugins never download anything themselves.

## A new archvow version

archvow keeps no backward compatibility: each minor version may change its commands or its JSON. The
plugins follow **one** version, that of `contract/cli/version.txt` (`ArchvowVersion.KNOWN`), and say when
the installed one is another (page [Running archvow](/en/05-running-archvow)).

1. Put the new archvow in `contract/archvow/` (`contract/tools/take_archvow.sh`: its wheel, its examples and
   rules), then regenerate the contract from it (page [The contract](/en/04-contract)):
   `python3 contract/tools/regenerate.py contract/archvow`.
2. Read `git diff contract/`: it is exactly what changed for the plugins (an option, a JSON field, a
   rule code, a template question).
3. `(cd intellij && ./gradlew contractDump check)` and `(cd vscode && npm test)`: the forms follow
   `commands.json` with no code; a renamed JSON field breaks a test.
4. Adapt what the diff asks for, on both sides, with a CHANGELOG line each.
5. Update the "archvow 1.17" mentions: `README.md`, `intellij/README.md`, `contract/README.md`,
   `intellij/gradle.properties` (the comment), `wiki/home.md` and its twin.

## The wiki in the same release

A release that changes a screen, a class or a setting changes the wiki pages concerned, **in French and
in English, in the same commit**. `wiki.test.ts` refuses a dead link, a page without a twin, a file or
class that no longer exists (page [Tests](/en/13-tests)).
