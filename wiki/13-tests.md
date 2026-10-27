# 13. Les tests

> 🇬🇧 [English version](/en/13-tests) · Version française

Les tests des deux plugins tournent **sans IDE** et **sans archvow installé** : ils lisent le contrat
(`contract/`), et un faux `archvow` quand il faut lancer quelque chose. `./build.sh` ne construit un
plugin qu'après ses tests.

## IntelliJ : `./gradlew check`

Pas de framework de test : chaque test est un `main()` qui lève une exception au premier échec, et
Gradle les lance avec `JavaExec` (`intellij/build.gradle.kts`). La tâche `test` de Gradle est coupée.

| Tâche | Classe | Ce qu'elle vérifie |
|---|---|---|
| `coreTest` | `CoreTests` (`org.archvow.vow.core`) | le lecteur `.vow` sur chaque vow de `contract/vows` : le scanner couvre tout le texte, redémarre à n'importe quel jeton, une couleur par profondeur ; des vows cassés exprès donnent les bons problèmes |
| `workspaceTest` | `WorkspaceTests` (`org.archvow.idea`), en `java.awt.headless=true` | les 34 classes de régression : tout ce qui ne demande pas un IntelliJ qui tourne |
| `contractDump` (pas dans `check`) | `DumpContract` | écrit `contract/vow-reader/expected.json`, la référence du lecteur TypeScript |

Le panneau lui-même (`ArchvowPanel`) a besoin d'un IntelliJ qui tourne ; c'est pourquoi **la logique
est sortie dans de petites classes sans état de la plateforme** (`ArchvowSplitView`, `ArchvowItemCards`,
`ArchvowCommandPrefill`, `ArchvowWsl`, `ArchvowHtml`...), testées une par une. Une régression porte le
nom de ce qu'elle protège :

| Régression | Protège |
|---|---|
| `SplitViewRegression`, `AnswerEditorRegression` | les cartes et le panneau de réponse, le premier élément ouvert, « Rien à afficher. » |
| `ArchitectureViewRegression` | Composant, État, En attente toujours visibles à toute largeur |
| `CommandPrefillRegression`, `InlineCommandRegression`, `LaunchCommandRegression` | ce qu'un formulaire reçoit, la ligne de commande exacte |
| `WslRegression` | `wsl.exe -d ... --cd ... --exec`, les chemins `\\wsl.localhost\...` |
| `TextDialogRegression` | la taille d'un dialogue, Copier la commande |
| `TextSizeRegression` | aucun `px` ni `<h1>`-`<h6>` dans le HTML affiché, le conseil de branche sur plusieurs lignes |
| `ToolWindowLayoutRegression`, `ResponsiveOverflowRegression`, `CompactViewsRegression` | les écrans étroits |
| `VersionRegression` | « archvow 1.17 n'est pas la version connue » |

Une nouvelle régression s'ajoute dans `WorkspaceTests.main` (une ligne `run(...)`) : une classe non
inscrite ne tourne pas.

### Sans accès aux dépôts JetBrains

`./gradlew` télécharge la plateforme IntelliJ. Sur une machine qui ne peut pas les joindre, on peut
encore compiler `org.archvow.idea` et `org.archvow.vow.core` avec `javac` contre des **doublures** :
de petites classes Java aux noms de la plateforme (`com.intellij.openapi.project.Project`,
`com.intellij.ui.components.JBList`...) qui ne font rien, plus Gson. Les tests headless tournent alors
tels quels, et le panneau peut s'afficher dans une simple `JFrame` pour une capture. Ces doublures ne
sont **pas** dans le dépôt (elles suivraient mal la plateforme) : elles servent à vérifier un changement,
jamais à livrer. Avant une version, `./gradlew check buildPlugin` et un essai dans `runIde` restent
obligatoires.

`intellij/tools/verify_built_plugin.py` contrôle le `.zip` construit : l'identifiant du plugin et le
`since-build` réécrits par Gradle dans `META-INF/plugin.xml`.

## VS Code : `npm test`

`npm test` compile (`scripts/sync-contract.js` copie d'abord `contract/cli` dans `resources/archvow`),
puis lance `node --test` sur `out/test/*.test.js`. Pas de VS Code : `vscode` n'est pas importé par les
modules testés, la page est lue comme du texte.

| Fichier | Ce qu'il vérifie |
|---|---|
| `cli.test.ts` | la ligne de commande, stdout et stderr séparés, stdin pour un fichier non enregistré, une commande à la fois, le délai, archvow absent, WSL ; avec un faux `archvow` écrit pour le test |
| `vowReader.test.ts` | le lecteur TypeScript donne **exactement** `contract/vow-reader/expected.json` |
| `grammar.test.ts` | une couleur par profondeur dans la grammaire TextMate ; le pré-remplissage de Modifier la spec |
| `model.test.ts` | le JSON enregistré des trois exemples se lit |
| `workspace.test.ts` | formulaire -> ligne de commande, pré-remplissage, actions sur place, corrections documentées, mise en forme des résultats |
| `layout.test.ts` | la page : un seul onglet, le logo, les cartes, le tableau d'architecture, Retour |
| `wiki.test.ts` | le wiki suit le code (plus bas) |

Le comportement de la page (un clic, une largeur) se vérifie à la main avec F5, ou dans un navigateur
headless qui charge `media/workspace.js` avec un faux `acquireVsCodeApi` et des messages tirés du
contrat ; comme les doublures IntelliJ, ce banc n'est pas dans le dépôt.

## Le test du wiki

`vscode/src/test/wiki.test.ts` fait échouer `npm test` quand le wiki ne suit plus le code :

- chaque page française a sa jumelle anglaise dans `wiki/en/`, et l'inverse ;
- chaque lien `/page` (ou `/en/page`) mène à une page qui existe ;
- chaque chemin entre accents graves qui contient un `/` et commence par un dossier du dépôt
  (`intellij/`, `vscode/`, `contract/`, `docs/`, `wiki/`) existe ;
- chaque classe nommée `Archvow...` ou `Vow...` existe en Java (un fichier `.java`) ou dans le code
  VS Code ;
- chaque fonction citée avec ses parenthèses (comme `planForm()`) apparaît dans le code de l'un des
  deux plugins.

Un renommage oublié dans le wiki casse donc le test, comme dans archvow
(`test_the_wiki_follows_the_code`).

## Avant chaque commit

```bash
(cd intellij && ./gradlew check)
(cd vscode && npm test)
```

Et avant une version, la liste de la page [Publier une version](/14-release).
