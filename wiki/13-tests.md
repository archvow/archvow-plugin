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
| `workspaceTest` | `WorkspaceTests` (`org.archvow.idea`), en `java.awt.headless=true` | les 37 classes de régression : tout ce qui ne demande pas un IntelliJ qui tourne |
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
| `ChoicesRegression` | les formulaires de commande proposent ce qui existe (`ArchvowChoices`) : sur les trois exemples, en anglais et en français, exactement ce que propose VS Code (`contract/choices/expected.json`), et la même base de comparaison |
| `GuidanceRegression` | chaque diagnostic dit quoi faire (`ArchvowGuidance`) : chaque code documenté, et sur chaque message réel exactement le guidage de VS Code (`contract/guidance/expected.json`) |
| `InstallRegression` | Installer archvow : le Python choisi, la ligne pip par source, WSL, pip seulement depuis une confirmation |
| `ProgressRegression` | la barre d'avancement sur le `clean_orders` enregistré (9/9, 1/5, 0/2) |
| `BriefsRegression` | les dossiers de briefs, les ancres d'archvow, les choix de `--from`, Tiré de, Ajouter ce dossier, ARCH039 |
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
puis lance `node --test` sur chaque `out/test/*.test.js`, listé par `vscode/scripts/test.js` (Node 20 ne développe pas un glob donné à `node --test` ; `node scripts/test.js out/test/live.test.js` lance un seul fichier). Pas de VS Code : `vscode` n'est pas importé par les
modules testés, la page est lue comme du texte.

| Fichier | Ce qu'il vérifie |
|---|---|
| `cli.test.ts` | la ligne de commande, stdout et stderr séparés, stdin pour un fichier non enregistré, une commande à la fois, le délai, archvow absent, WSL ; avec un faux `archvow` écrit pour le test |
| `vowReader.test.ts` | le lecteur TypeScript donne **exactement** `contract/vow-reader/expected.json` |
| `grammar.test.ts` | une couleur par profondeur dans la grammaire TextMate ; le pré-remplissage de Modifier la spec |
| `model.test.ts` | le JSON enregistré des trois exemples et du scénario briefs se lit |
| `workspace.test.ts` | formulaire -> ligne de commande, pré-remplissage, actions sur place, corrections documentées, mise en forme des résultats |
| `layout.test.ts` | la page : un seul onglet, le logo, les cartes, le tableau d'architecture, Retour, la barre d'avancement |
| `install.test.ts` | Installer archvow : la source, le Python, la ligne pip, WSL, rien sans Confirmer et exécuter |
| `choices.test.ts` | ce que propose chaque champ de formulaire (`choices.ts`) : les composants (Edit spec rempli avec ce que l'un a), les questions avec leur numéro, seulement les questions ajoutées par une personne pour `question remove`, les types du modèle du projet et les sortes d'un type, les dangers, invariants, règles, les branches ; la base qui existe (`bestBase`) et pourquoi une n'existe pas (`missingRef`) ; Edit spec n'envoie que ce qui a changé ; écrit `contract/choices/expected.json` avec `UPDATE_CHOICES=1` |
| `guidance.test.ts` | chaque diagnostic dit ce qu'il veut dire, quoi faire, qui le fait, s'il bloque et la suite (`guidance.ts`) : tous les codes de `rules.md` et `rules.fr.md`, tous les messages réels de `contract/cli/messages.json` ; la commande qu'un message nomme devient un formulaire qui tourne tel quel ou ne laisse vide que la part de la personne ; écrit `contract/guidance/expected.json` avec `UPDATE_GUIDANCE=1` |
| `briefs.test.ts` | les dossiers de briefs, les ancres d'archvow, les choix de `--from`, Tiré de, le refus -> formulaire init, ARCH039 (sur `contract/cli/briefs`) |
| `wiki.test.ts` | le wiki suit le code (plus bas) |
| `release.test.ts` | avant une version, sans rien construire : une seule version (`package.json`, `gradle.properties`, README, les deux changelogs), un seul archvow (`contract/cli/version.txt`, l'exigence pip, le README), les deux builds embarquent les mêmes fichiers du contrat, les listes de commandes (`DESTRUCTIVE`, `READ_ONLY`, `LONG_TEXT`) identiques dans `grammar.ts` et `ArchvowPanel` et faites de commandes d'archvow, toute commande qui demande confirmation reçoit `--yes`, `package.json` et `plugin.xml` ne citent que des commandes, réglages, classes et fichiers qui existent |
| `live.test.ts` | un vrai archvow, lancé par `ArchvowCli` : sa version est celle du contrat, `catalog --json` est celui enregistré, sur les trois exemples d'archvow avec leur code chaque sortie est **exactement** le contrat, les commandes en lecture seule ne changent aucun vow, `question add` puis `question remove --yes` font l'aller-retour, une erreur est une phrase. Sauté sans `ARCHVOW_PYTHON` (ou `ARCHVOW_EXECUTABLE`) et `ARCHVOW_SOURCE` ; `ARCHVOW_LIVE=required` fait du saut un échec |
| `package.test.ts` | les paquets de `dist/`, ouverts comme les IDE les ouvrent (`zip.ts` les lit) : le `.vsix` (manifeste, version, chaque module, le contrat embarqué, ni source, ni test, ni map, chaque `require` embarqué ou natif) et le `.zip` IntelliJ (le `plugin.xml` du jar avec la version et le `since-build`, chaque classe qu'il cite, le contrat embarqué, aucune classe de test). Sauté sans paquets ; `PACKAGES_REQUIRED=1` fait du saut un échec |

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

## GitHub Actions : `.github/workflows/`

Les mêmes contrôles que la CI d'archvow, à chaque push et pull request (`ci.yml`) :

| Job | Ce qu'il lance |
|---|---|
| `vscode` | `npm ci`, `npm test`, `npm run package` -> le `.vsix` |
| `intellij` | `./gradlew check buildPlugin contractDump` -> le `.zip` ; le lecteur Java doit encore écrire le `contract/vow-reader/expected.json` commité (le fichier sur lequel le lecteur VS Code est testé) |
| `contract` | le wheel de `contract/archvow/` installé, puis `contract/tools/regenerate.py contract/archvow` : `contract/` doit être exactement ce qui est commité |
| `live` | `live.test.ts` contre ce wheel, installé dans un venv, sur les exemples de `contract/archvow/` (`ARCHVOW_LIVE=required`) |
| `packages` | `package.test.ts` sur les deux paquets du run (`PACKAGES_REQUIRED=1`) ; ils sont gardés comme artefact `archvow-plugins` |

`archvow-canary.yml` tourne chaque lundi (ou à la main) : le contrat régénéré depuis la branche par
défaut d'archvow ; il échoue, avec le diff, quand le prochain archvow change ce que lisent les plugins.

Rien n'est récupéré du dépôt d'archvow : `contract/archvow/` contient le wheel de la version d'où vient le
contrat, et les exemples et règles qu'il ne contient pas (`contract/tools/take_archvow.sh` les remplace après une
version d'archvow). Seul le canari, optionnel, clone archvow : il tourne quand la variable de dépôt
`ARCHVOW_GIT_URL` est renseignée (avec le secret `ARCHVOW_GIT_TOKEN`, en lecture seule, pour un dépôt privé).

## Avant chaque commit

```bash
(cd intellij && ./gradlew check)
(cd vscode && npm test)
python3 -m venv .venv && .venv/bin/pip install contract/archvow/archvow-*.whl
ARCHVOW_PYTHON=$PWD/.venv/bin/python npm test --prefix vscode   # avec le vrai archvow, depuis votre propre terminal
```

Et avant une version, la liste de la page [Publier une version](/14-release).
