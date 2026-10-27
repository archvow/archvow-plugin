# 3. Repository layout

> 🇫🇷 [Version française](/03-repository-layout) · English version

```
archvow-plugin/
├── README.md               the shared rules, how to build both
├── build.sh                both plugins, each after its tests -> dist/
├── Makefile                make bundle | zip | release: the repository as .bundle / .zip
├── apply_patch.sh          applies a received patch (git format-patch format) with git apply
├── docs/analyse-intellij.md  the review of the IntelliJ plugin and what is left to do, by priority
├── wiki/                   this wiki (French; English in wiki/en/)
├── contract/               what the plugins and the CLI share (page 4)
│   ├── cli/                archvow 1.16's recorded JSON, the grammar, the catalog, the rules
│   ├── vows/               the vows of archvow's three examples
│   ├── vow-reader/         expected.json: what the Java reader gives
│   └── tools/              regenerate.py, export_grammar.py
├── intellij/               the JetBrains plugin (Gradle, JDK 21)
│   ├── build.gradle.kts    platform 2024.3.5, tasks coreTest, workspaceTest, contractDump
│   ├── gradle.properties   pluginVersion, platformVersion, sinceBuild
│   ├── src/main/java/org/archvow/idea/      the workspace
│   ├── src/main/java/org/archvow/vow/       the .vow language plugged into the platform
│   ├── src/main/java/org/archvow/vow/core/  the .vow reader in plain Java
│   ├── src/main/resources/  plugin.xml, icons, logo, colour schemes
│   ├── src/test/java/       the tests (main()s, no framework)
│   └── tools/verify_built_plugin.py  checks the plugin.xml of the built .zip
└── vscode/                 the VS Code extension (npm, Node 20+)
    ├── package.json        contributions: view, commands, menus, settings, language
    ├── src/                the extension in TypeScript, and src/test/
    ├── media/              the page (workspace.js, workspace.css), the icons
    ├── syntaxes/           the .vow TextMate grammar and its Markdown injection
    └── scripts/sync-contract.js  copies contract/cli into resources/archvow on every compile
```

## The files at the root

| File | Role |
|---|---|
| `README.md` | what the repository is, the shared rules, the plugins' version and the CLI version they run |
| `build.sh` | `./build.sh`, `./build.sh intellij`, `./build.sh vscode`: tests, then package into `dist/` |
| `Makefile` | `make bundle` (`git bundle --all`), `make zip` (`git archive`), `make release` |
| `apply_patch.sh` | takes a `.patch` file, keeps the subject and body, applies with `git apply` (no commit) |

## Generated files, never committed

`intellij/build/` (Gradle), `vscode/out/` (compiled TypeScript), `vscode/resources/archvow/` (the copy
of `contract/cli`, made by `scripts/sync-contract.js`), `dist/` (the packages), `node_modules/`. The
list is in `.gitignore`.

## Both plugins have the same version

`intellij/gradle.properties` (`pluginVersion`) and `vscode/package.json` (`version`) carry the same
number, the plugins' own, not archvow's. The page [Releasing](/en/14-release) lists every place that
quotes it.
