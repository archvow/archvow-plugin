# 3. Organisation du dépôt

> 🇬🇧 [English version](/en/03-repository-layout) · Version française

```
archvow-plugin/
├── README.md               les règles communes, comment construire les deux
├── build.sh                les deux plugins, chacun après ses tests -> dist/
├── Makefile                make bundle | zip | release : le dépôt en .bundle / .zip
├── apply_patch.sh          applique un patch reçu (format git format-patch) avec git apply
├── docs/analyse-intellij.md  l'analyse du plugin IntelliJ et ce qui reste à faire, par priorité
├── wiki/                   ce wiki (français ; anglais dans wiki/en/)
├── contract/               ce que les plugins et la CLI ont en commun (page 4)
│   ├── cli/                le JSON enregistré d'archvow 1.16, la grammaire, le catalogue, les règles
│   ├── vows/               les vows des trois exemples d'archvow
│   ├── vow-reader/         expected.json : ce que donne le lecteur Java
│   └── tools/              regenerate.py, export_grammar.py
├── intellij/               le plugin JetBrains (Gradle, JDK 21)
│   ├── build.gradle.kts    plateforme 2024.3.5, tâches coreTest, workspaceTest, contractDump
│   ├── gradle.properties   pluginVersion, platformVersion, sinceBuild
│   ├── src/main/java/org/archvow/idea/      l'espace de travail
│   ├── src/main/java/org/archvow/vow/       le langage .vow branché sur la plateforme
│   ├── src/main/java/org/archvow/vow/core/  le lecteur .vow en Java simple
│   ├── src/main/resources/  plugin.xml, icônes, logo, schémas de couleurs
│   ├── src/test/java/       les tests (des main(), sans framework)
│   └── tools/verify_built_plugin.py  vérifie le plugin.xml du .zip construit
└── vscode/                 l'extension VS Code (npm, Node 20+)
    ├── package.json        contributions : vue, commandes, menus, réglages, langage
    ├── src/                l'extension en TypeScript, et src/test/
    ├── media/              la page (workspace.js, workspace.css), les icônes
    ├── syntaxes/           la grammaire TextMate du .vow et son injection Markdown
    └── scripts/sync-contract.js  copie contract/cli dans resources/archvow à chaque compilation
```

## Les fichiers à la racine

| Fichier | Rôle |
|---|---|
| `README.md` | ce qu'est le dépôt, les règles communes, la version des plugins et de la CLI qu'ils lancent |
| `build.sh` | `./build.sh`, `./build.sh intellij`, `./build.sh vscode` : tests puis paquet dans `dist/` |
| `Makefile` | `make bundle` (`git bundle --all`), `make zip` (`git archive`), `make release` |
| `apply_patch.sh` | prend un fichier `.patch`, garde le sujet et le corps, applique avec `git apply` (sans commit) |

## Les fichiers générés, jamais commités

`intellij/build/` (Gradle), `vscode/out/` (TypeScript compilé), `vscode/resources/archvow/` (la copie
de `contract/cli`, faite par `scripts/sync-contract.js`), `dist/` (les paquets), `node_modules/`. La
liste est dans `.gitignore`.

## Les deux plugins ont la même version

`intellij/gradle.properties` (`pluginVersion`) et `vscode/package.json` (`version`) portent le même
numéro, celui des plugins, pas celui d'archvow. La page [Publier une version](/14-release) liste tous
les endroits qui le citent.
