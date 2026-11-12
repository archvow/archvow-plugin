# Analyse du plugin IntelliJ (2026-10-25)

Ce document analyse `archvow-intellij` 0.4.5.16.11 (le workspace) et `vow-plugin-intellij` 0.4.2 (le
langage `.vow`). Les deux sont fusionnés dans `intellij/` (version 0.5.0). Pour chaque point, il indique
si la 0.5.0 l'a déjà corrigé ou ce qu'il reste à faire, par ordre de priorité.

**En résumé** : le langage `.vow` est propre (le cœur Java est séparé d'IntelliJ et testé). Le workspace
fonctionne, mais il recode des règles d'archvow et embarque des copies figées de la CLI. Il lance un
processus Python par composant à chaque rafraîchissement. Et il ne met pas les erreurs de `check` dans
l'éditeur, alors que c'est ce qui rapproche le plus archvow des développeurs.

## Corrigé dans 0.5.0

| Problème | Correction |
|---|---|
| Deux plugins nommés « archvow » avec deux ids (`org.archvow`, `com.archvow.intellij`), deux Java (17 et 21), deux builds Gradle (plugin 2.0.1 et 2.19.0), deux icônes (celle du workspace n'était pas l'icône officielle #1A876B) | Un seul plugin, id `org.archvow`, un seul build (2.19.0, IntelliJ 2024.3.5, JDK 21), l'icône officielle partout. **Désinstaller l'ancien plugin vow avant d'installer le nouveau** : les deux déclarent le type de fichier `.vow`. |
| `gradle-wrapper.jar` absent : `./gradlew` ne marchait pas. À la place, un script Python téléchargeait Gradle depuis services.gradle.org, contre la règle « pas de sources externes » | Le wrapper complet du plugin vow. Le script Python est supprimé. |
| stderr fusionné dans stdout (`redirectErrorStream(true)`) : un avertissement cassait le JSON. D'où le contournement « prendre du premier `{` au dernier `}` », qui peut lire un JSON faux sans rien dire | stdout et stderr sont lus séparément. Le JSON est lu sur stdout seulement. Les messages affichent les deux. |
| Sortie coupée sans rien dire à 2 Mo | Limite de 20 Mo par flux, et un message quand elle est atteinte. |
| Pas de rafraîchissement automatique : une commande lancée dans le terminal, un agent IA ou un `git checkout` n'apparaissaient qu'après un clic sur Refresh | `ArchvowVowWatcher` écoute `.archvow/**/*.vow`, attend 0,8 s sans changement, puis recharge, ou réessaie si le workspace est occupé. |
| Tests hors Gradle. Une partie était des tests Python qui cherchaient du texte dans le code Java (`assertIn('button.setFocusPainted(false)', text)`) : ils cassent au moindre renommage et laissent passer les vrais bogues. Les fausses classes Gson faites main ne se comportaient pas comme la vraie | `./gradlew check` lance `coreTest` et `workspaceTest` (26 classes, sans écran, avec le vrai Gson). En les branchant, deux tests se sont révélés **périmés** (les règles avaient changé de vue, l'apostrophe était échappée) : ils ne tournaient donc plus. Un troisième demandait des arguments. Les trois sont corrigés. |
| Paramètres affichés sous « Other Settings », alors que les messages d'erreur renvoyaient à Settings > Tools > Archvow | `parentId="tools"`. |
| Racine encombrée : 10 notes de version (`COMPILE_FIX.md`, `SIMPLIFIED_UI.md`…), des scripts de patch ponctuels (`tools/patch_16.py`…), `apply_patch.sh`, `.idea/` dans git, un seul commit « init » | Un README et un CHANGELOG (les deux historiques y sont repris). Les deux historiques git sont conservés dans le nouveau dépôt. |
| Version `0.4.5.16.11-java17-multiide` | `0.5.0`. |

## Corrigé dans 0.6.0

| Problème | Correction |
|---|---|
| « Save answer » masqué par son titre quand la confirmation s'ouvrait (hauteur fixe des éditeurs) | Les éditeurs de décision, question et safety prennent la hauteur de leur contenu (`ArchvowAnswerEditor`, testé). |
| Copies figées de la CLI 1.11 (`commands.json`, `catalog-1.11.json`, `rules.md`) et aucune vérification de version (point 4 ci-dessous) | Les quatre fichiers viennent de `contract/cli` (archvow 1.13.0) au build ; `archvow --version` est comparé une fois, avec un message si la version installée est différente. |

## À faire, par priorité

### 1. Les erreurs de `check` dans l'éditeur (le plus utile pour les développeurs)

`check --json` donne `file`, `line` et `col` pour chaque violation, mais le plugin ne s'en sert pas. Les
diagnostics restent dans une liste du panneau, sans lien vers le code (le `FEATURE_MATRIX.md` d'origine
le notait comme « à développer »).

**À faire** : un `ExternalAnnotator` sur les fichiers Python du projet qui lance
`archvow check --file <f> --stdin --json` avec le contenu de l'éditeur. Il souligne les lignes et ajoute
un lien vers la règle. La liste « Check & Gates » doit aussi ouvrir le fichier à la bonne ligne au
double-clic. L'extension VS Code le fait déjà (vérifié avec la vraie CLI : ARCH002 ligne 34).

### 2. Un processus Python par composant à chaque rafraîchissement

`reload()` lance `show --json`, `check --json`, `next --json`, puis **`show <c> --json` pour chaque
composant, l'un après l'autre**. Cela fait 3 + N processus Python, à environ 0,1 à 0,3 s chacun. Avec
200 composants, il faut une minute. Pendant ce temps, `ArchvowCli` refuse toute autre commande (« Une
autre commande Archvow est déjà en cours »).

Ces appels servent surtout aux badges de l'arbre. Or `check --json` donne déjà, par composant, `status`,
`open_questions`, `pending_assumptions`, `hazards` et `invariants_total/verified`.

**À faire** : construire l'arbre et les compteurs à partir de `check --json` et `next --json`, et ne
lancer `show <c> --json` que pour le composant ouvert. Le badge « Pending » par catégorie (décisions,
safety…) se calcule alors sur le composant ouvert. Si un nombre manque vraiment dans `check --json`, il
vaut mieux l'ajouter côté CLI (quelques lignes) que le recalculer dans le plugin. Par ailleurs, mettre
les commandes **en file d'attente** au lieu de les refuser (c'est ce que fait l'extension VS Code).

### 3. Des règles d'archvow recodées dans le plugin

- `ArchvowSafetyState` réimplémente ARCH030 (un hazard high/critical sans invariant reste « NEEDS
  INVARIANT »).
- `ArchvowPanel` (lignes 2800 à 3050 environ) classe les éléments en « pending/resolved/other ». Il
  dédoublonne « ARCH019 et `missing: schema` sont le même manque ».
- `ArchvowIssueAction` relit le **texte** des messages de `check` avec des expressions régulières
  (`archvow component edit X --signature "..."`) pour préremplir les formulaires.

Si archvow change une règle ou une phrase, le plugin affiche quelque chose de faux sans le savoir.

**À faire** : afficher ce que la CLI dit. ARCH030 sort déjà dans `check`. Pour le préremplissage, la
bonne solution est côté CLI : un champ structuré dans chaque violation (par exemple
`"fix": ["component", "edit", "x", "--signature", "..."]`). Les expressions régulières disparaissent
alors. C'est un petit ajout pour archvow 1.12, à décider par toi.

### 4. Des copies figées de la CLI 1.11 (corrigé en 0.6.0, sauf la console des 40 commandes)

- `commands.json` : la grammaire des 40 commandes, exportée par `tools/export_cli.py`.
- `catalog-1.11.json` : le catalogue des templates.
- `rules.md` / `rules.fr.md` : une copie de la documentation.

Avec ta règle « pas de rétrocompatibilité », elles seront fausses à la prochaine version d'archvow (1.12
est prévue). Et le plugin ne lit jamais `archvow --version`.

**À faire** :
1. Vérifier `archvow --version` au chargement et prévenir quand la version est hors de la plage connue
   (comme l'extension VS Code).
2. Garder `commands.json`, mais le régénérer à chaque version d'archvow (`contract/tools/regenerate.py`
   pourrait le faire).
3. Remplacer la copie des règles par la section « Checks » du `.archvow/spec.md` du projet, que la CLI
   installée génère déjà.
4. Se demander si la console des 40 commandes doit rester. Le terminal le fait mieux. Les actions
   utiles au quotidien sont celles du panneau composant : décider, répondre, hazard, hypothèses, prompt.

### 5. Structure du code

- `ArchvowPanel.java` fait 3 659 lignes, soit la moitié du plugin. Il mélange l'état, l'orchestration
  de la CLI, la classification, le rendu HTML et Swing. **À faire, progressivement** : un service projet
  `ArchvowProjectState` (spec, check, next, avec un écouteur) partagé par la fenêtre latérale et l'onglet
  éditeur (aujourd'hui, chacun a son panneau, son `ArchvowCli` et son état), puis une classe par vue.
- 265 appels `text(en, fr)` dans le code, et des messages en français en dur dans `ArchvowCli` et
  `ArchvowProcessCommand`. **À faire** : un `ArchvowBundle.properties` + `_fr` (le `DynamicBundle`
  standard d'IntelliJ).
- `ArchvowMotion` (539 lignes de minuteries et d'effets de survol) pour des effets que les composants
  natifs donnent déjà. À simplifier.
- `StartupActivity.DumbAware` est déprécié. Le remplacer par `ProjectActivity`.
- `ArchvowProcessCommand` accepte encore `python3 -m archvow` dans le champ « exécutable » (« legacy »).
  C'est contraire à ta règle « pas de rétrocompatibilité » : un champ, un sens.

### 6. Tests à réécrire

Trois tests ont été retirés (`ComponentStatusSmoke`, `IssuePlacementRegression`,
`WorkspaceNavigationSmoke`). Ils pilotaient `ArchvowPanel` par réflexion sur des méthodes privées, avec
un faux `Project`, et ne compilent pas contre la vraie plateforme. Ils deviendront possibles quand
l'étape 5 aura sorti l'état du panneau. On pourra alors tester l'état sans Swing, sur les JSON réels de
`contract/cli/`.

## Le plugin `.vow` (bon état)

Le cœur (`VowScanner`, `VowDocument`…) est sans IntelliJ et testé (174 299 vérifications sur 29 vows).
La couche IntelliJ est mince. Points mineurs :

- Les vows d'exemple dataient d'archvow 1.9.1. Ils sont maintenant dans `contract/vows/` et se
  régénèrent depuis archvow.
- `VowValues.unquote` appelle `Integer.parseInt` sur `\uZZZZ` : une exception est possible sur une clé
  entre guillemets mal formée, alors que le lecteur promet de ne jamais lever d'exception. Aucun des 400
  cas cassés du contrat ne l'a déclenchée, mais il faudrait l'entourer d'un try.

## Non vérifié ici

Je ne peux pas compiler le plugin IntelliJ dans cet environnement : les dépôts JetBrains et Maven sont
bloqués. Voici ce qui a été vérifié :

- le cœur `.vow` et les 26 classes de test du workspace, compilés et lancés avec le vrai Gson ;
- `ArchvowCli`, `ArchvowSettings` et `ArchvowVowWatcher`, compilés contre des signatures minimales de
  l'API IntelliJ.

Le build complet (`./gradlew check buildPlugin`) et l'essai dans l'IDE (`./gradlew runIde`) sont à
faire chez toi.
