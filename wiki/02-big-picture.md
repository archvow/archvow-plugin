# 2. Vue d'ensemble

> 🇬🇧 [English version](/en/02-big-picture) · Version française

## L'idée

archvow garde la spec d'un projet (`.archvow/**/*.vow`) et fait échouer le build quand le code ne la
suit pas. Les plugins mettent cette spec **dans l'IDE** : on voit un composant, ses décisions et ses
questions ouvertes, on y répond, on lance un contrôle, sans quitter l'éditeur. Ils ne savent rien
qu'archvow ne leur dise : ils lisent ce que la CLI affiche et lancent la CLI pour chaque changement.

## Les règles que suivent les deux plugins

| Règle | Pourquoi | Dans le code |
|---|---|---|
| **La CLI est la seule à écrire** | une seule source de vérité, validée à chaque écriture, avec qui et quand | aucun plugin n'écrit un `.vow` ; chaque changement est une commande archvow, après « Confirmer et exécuter » |
| **La même disposition dans les deux IDE** | une équipe a les deux ; ce qu'on apprend dans l'un vaut dans l'autre | la barre latérale choisit (Overview, Components, Commands), **un** onglet d'éditeur montre et sert pour le choix suivant |
| **Les mêmes fonctions, pas le même code** | Swing et une page web n'ont rien en commun | VS Code reprend l'espace de travail IntelliJ règle par règle ; `vscode/src/workItems.ts`, `vscode/src/grammar.ts`, `vscode/src/formatter.ts` nomment la classe Java qu'ils suivent |
| **Un seul lecteur `.vow`, écrit deux fois** | les deux éditeurs doivent voir les mêmes erreurs au même endroit | le lecteur Java (`org.archvow.vow.core`) est la référence ; le TypeScript (`vscode/src/vow`) doit donner les mêmes jetons sur `contract/vow-reader/expected.json` |
| **Pas de shell, une commande à la fois** | pas d'injection, pas deux écritures en même temps | `ArchvowCli` et `vscode/src/cli.ts` : liste d'arguments, dossier du projet, délai maximal |
| **Le plugin dit quand l'archvow installé ne parle pas son contrat** | archvow ne garde pas de compatibilité ascendante | `ArchvowVersion`, `contractWarning()` comparent `archvow version --json` avec `contract/cli/contract.txt` |
| **Aucune source extérieure à l'exécution** | projets critiques, chaîne d'approvisionnement | aucun plugin ne télécharge ; le `.zip` et le `.vsix` passent par un dépôt interne |

## La carte des fichiers

**IntelliJ** (`intellij/src/main/java/org/archvow/`) :

| Rôle | Classes |
|---|---|
| Brancher le plugin dans l'IDE | `plugin.xml`, `ArchvowToolWindowFactory`, `ArchvowEditorProvider`, `ArchvowMainFileEditor`, `ArchvowEditorTab`, `ArchvowEditorTabPresentation`, `ArchvowOpenOnStartup`, `ShowArchvowAction`, `ShowFileArchvowAction`, `RefreshArchvowAction` |
| L'espace de travail (les deux rôles) | `ArchvowPanel` |
| Lancer la CLI | `ArchvowCli`, `ArchvowProcessCommand`, `ArchvowInterpreter`, `ArchvowWsl`, `ArchvowVersion`, `ArchvowRunLog`, `ArchvowSettings`, `ArchvowSettingsConfigurable` |
| Overview | `ArchvowArchitectureView`, `ArchvowChecksView`, `ArchvowResultFormatter`, `ArchvowRuleDocs`, `ArchvowBranchAdvice`, `ArchvowProjectReadiness` |
| Un composant | `ArchvowSplitView`, `ArchvowItemCards`, `ArchvowDecisionView`, `ArchvowSafetyView`, `ArchvowSafetyState`, `ArchvowDataView`, `ArchvowAnswerEditor`, `ArchvowConfirmPanel`, `ArchvowInlineCommands`, `ArchvowLinks`, `ArchvowVowViewer`, `ArchvowTaskPrompt` |
| Les commandes | `ArchvowGrammar`, `ArchvowCommandPrefill`, `ArchvowInitCatalog`, `ArchvowInitQuestions`, `ArchvowComponentTypes`, `ArchvowIssueAction` |
| Relire quand un vow change | `ArchvowVowWatcher` |
| Apparence | `ArchvowMotion`, `ArchvowNavigationTabs`, `ArchvowCompactViews`, `ArchvowOverflowBar`, `ArchvowUiIcons`, `ArchvowStatusPalette`, `ArchvowHtml`, `ArchvowTextDialog`, `ArchvowWrapLabel`, `ArchvowI18n` |
| Le langage `.vow` | `org.archvow.vow` (branché sur la plateforme) et `org.archvow.vow.core` (Java simple, testé seul) |

**VS Code** (`vscode/`) :

| Rôle | Fichiers |
|---|---|
| Brancher l'extension | `vscode/package.json` (contributions), `vscode/src/extension.ts` |
| L'extension : l'état, la CLI, les messages | `vscode/src/workspace.ts`, `vscode/src/cli.ts` |
| Les règles de l'espace de travail (sans `vscode`) | `vscode/src/workItems.ts`, `vscode/src/projectState.ts`, `vscode/src/grammar.ts`, `vscode/src/formatter.ts`, `vscode/src/model.ts`, `vscode/src/i18n.ts` |
| La page (barre latérale et onglet) | `vscode/media/workspace.js`, `vscode/media/workspace.css` |
| Le langage `.vow` | `vscode/src/vowLanguage.ts`, `vscode/src/vow/scanner.ts`, `vscode/src/vow/document.ts`, `vscode/src/vow/values.ts`, `vscode/src/vow/links.ts`, `vscode/syntaxes/vow.tmLanguage.json` |

Les fichiers « sans `vscode` » ne dépendent pas de l'API VS Code : les tests les chargent dans Node
tels quels. Côté IntelliJ, la même idée : tout ce qui peut être une classe Java simple l'est
(`ArchvowSplitView`, `ArchvowItemCards`, `ArchvowWsl`...), pour être testé sans IDE.

## Ce que lit chaque plugin, et quand

| Moment | Commandes lancées | Où |
|---|---|---|
| Ouverture, Reload, un vow qui change sur le disque, après une action | `workspace --json` (0.21.0, archvow 1.25) : la spec, chaque composant, check, next, les briefs et le catalogue en un seul processus ; `--since` son dernier condensé quand un vow a changé sur le disque | `ArchvowPanel.reload`, `ArchvowProject.refresh` / `load` |
| Un composant choisi | `show <c> --json` | `loadComponent`, `reloadComponent` |
| Un fichier Python enregistré (VS Code) | `check --file <f> --json`, le texte sur stdin | `checkFile` |
| Une action | la commande archvow de l'action, après confirmation pour un changement | page [Les commandes](/09-commands) |
| La première commande | `--version`, une fois par candidat jusqu'à une réponse ; puis `version --json` (le contrat, 0.20.0) | page [Lancer archvow](/05-running-archvow) |
