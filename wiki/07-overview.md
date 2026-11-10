# 7. Overview

> 🇬🇧 [English version](/en/07-overview) · Version française

Overview est le premier onglet de la barre latérale : où en est le projet, son architecture, ses
contrôles, et les commandes qui portent sur tout le projet.

## Avant de lancer quoi que ce soit : le projet est-il prêt ?

`ArchvowProjectReadiness` (VS Code : `readiness()` dans `vscode/src/projectState.ts`) regarde le
disque seulement, sans lancer Python pour un projet qui n'est pas un projet archvow :

| État | Ce que montre le plugin |
|---|---|
| `NO_PROJECT` | « Ouvre un dossier » |
| `ROOT_MISSING` | le dossier du projet est introuvable |
| `NOT_INITIALIZED` | pas de `.archvow/spec.vow` : **Initialiser Archvow** (l'assistant `init`, page [Les commandes](/09-commands)) |
| `SPEC_INACCESSIBLE` | `spec.vow` vide ou illisible |
| `READY` | l'espace de travail ; si la CLI échoue malgré tout, son message, avec Configurer Python et Réessayer |

## La bande « État du projet »

Deux compteurs : **en attente** et **traités**. Ils comptent les éléments de travail que les plugins
construisent à partir de ce qu'archvow a dit : décisions et questions sans réponse, questions de
sûreté ouvertes ou sans invariant, hypothèses à examiner, colonnes à classer, tâches, et les
diagnostics de `check` rattachés à un composant. IntelliJ : `rebuildQuickModels` ; VS Code :
`buildWork()` dans `vscode/src/workItems.ts`, qui suit la même règle (et le dit dans ses commentaires).

Un clic sur un compteur ouvre, dans l'onglet, le premier composant qui a de ce travail, sur la bonne vue
et le bon élément (`openQuickList`) ; s'il n'y a que du travail de projet, la vue *Autres / Projet*.

Sous la bande, sur `main`, `master`, `develop` ou `development`, un conseil : créer une branche de
fonctionnalité (`ArchvowBranchAdvice`, `needsFeatureBranch()`). C'est un conseil, rien de plus : la
politique de branches appartient au dépôt et à la CI. Il passe sur plusieurs lignes dans une fenêtre
étroite (`ArchvowWrapLabel`).

Puis les **dossiers de briefs** du projet (la réponse d'init `briefs` d'archvow 1.17 : là où `--from`
peut puiser) et combien de fichiers `.md` / `.txt` ils contiennent : `ArchvowBriefs.foldersLine`
(IntelliJ), `briefFolders()` (VS Code). Un architecte les change par
`archvow init <package> --force -a briefs=...`.

## La barre d'actions

| Bouton | Commande | Où va le résultat |
|---|---|---|
| ↻ | relit le projet | - |
| Check | `check --json` | l'onglet ; et Check & Gates se met à jour |
| Next | `next --json` | l'onglet |
| Full check | `check --complete --json` | l'onglet |
| Doctor | `doctor --json` | l'onglet |
| Audit | `audit --json` | l'onglet |
| Project vow | ouvre `.archvow/spec.vow` | un onglet d'éditeur ordinaire |
| Budget / Next | `next --base <Base> --json` | l'onglet |
| Diff, Review | le formulaire de `diff` / `review`, `--base` déjà rempli | l'onglet |

Dans IntelliJ, les boutons qui ne tiennent pas passent dans **Plus ▾** (`ArchvowOverflowBar` : il
mesure la largeur réelle des boutons, sans seuil fixe). Le champ **Base** sert à Budget, Diff et Review :
la meilleure branche qui existe ici (`bestBase` : `develop` sur une branche de fonctionnalité Git flow, sinon
ce que nomme `origin/HEAD`, puis `origin/main`, `main`, `master`...), jusqu'à ce qu'une personne en
choisisse une dans la liste des branches et tags du dépôt (`git for-each-ref`, `refNames`). Il valait
`origin/main` en dur, qu'un dépôt sans ce remote n'a pas. Une base qui n'existe pas est refusée avant de
lancer quoi que ce soit, avec les branches qui existent (`missingRef`, `refProblem`) - plus de « Not a valid
object name » de Git.

## Les résultats

Un résultat n'est pas du JSON brut : `ArchvowResultFormatter` (VS Code : `renderResult()` dans
`vscode/src/formatter.ts`) le met en forme selon la commande : `check` (totaux, puis chaque
diagnostic), `audit`, `doctor` (à corriger, avertissements, ce qui va bien), `next`, `budget`,
`catalog`, et une forme générale pour le reste. Il n'affiche que les valeurs données par archvow,
jamais un texte `.vow` reconstruit. **Sortie brute** montre ce que la CLI a écrit, **Copier** le copie.

Pour une erreur connue (« No module named archvow », un exécutable introuvable, un accès refusé),
`advice()` ajoute en tête ce qu'il faut faire.

## L'architecture

Une ligne par composant : Composant, Type, Couche, Conteneur, **État**, **En attente**.
`ArchvowArchitectureView` (VS Code : `architectureView()` et le tableau `table.arch`). L'état vient de
`check --json` quand il le donne, sinon du `show` du composant ; il est traduit (`localizedState`).

Dans une barre latérale étroite, **Composant, État et En attente restent visibles** : Conteneur
disparaît d'abord, puis Couche, puis Type (l'infobulle de la ligne les donne tous) ; ensuite les noms
de composants sont coupés avec « … » ; seulement en dessous, le tableau défile sur le côté.
IntelliJ : `ArchvowArchitectureView.columnWidths` calcule les largeurs à partir du texte de chaque
colonne ; VS Code : des règles `@media` dans `vscode/media/workspace.css`.

Un double clic (IntelliJ) ou un clic (VS Code) ouvre le composant dans l'onglet.

## Check & Gates

`ArchvowChecksView` (VS Code : `checksView()`) : PASSED ou FAILED, les erreurs et avertissements, le
nombre par règle quand il y en a plusieurs (`counts` de `check --json`, « Par règle : ARCH012 1 ·
ARCH053 1 »), puis la liste des diagnostics. Le diagnostic choisi montre son message, son fichier,
et **l'explication de sa règle**, tirée de `contract/cli/rules.md` (`ArchvowRuleDocs`,
`ruleExplanation()`). Voir la règle ouvre la référence des contrôles ; Ouvrir le fichier va à la ligne.

## Briefs (0.16.0)

Un troisième sous-onglet à côté d'Architecture et de Check & Gates, depuis `brief show --json` d'archvow 1.20 (lu
avec l'architecture ; un archvow plus ancien n'a pas cette commande : l'onglet le dit). Chaque fichier de brief,
section par section : ✓ utilisée (les éléments qui en sont tirés), ○ pas encore utilisée avec **Rédiger avec
l'IA…** (le formulaire de draft-spec, la section remplie), ! changée depuis avec **Relire…** (ouvre la section) et
**Accepter le changement…** (le formulaire de `brief accept`). Son compte est les sections pas utilisées plus
celles qui ont changé ; la ligne « Dossiers de briefs » de la bande d'état y mène. **Ajouter un dossier de briefs
(architecte)…** ouvre le formulaire d'init prérempli. VS Code : `briefsView()`, `briefsDue()` ; IntelliJ :
`ArchvowBriefs.statusHtml`, `ArchvowBriefs.due`, liens `DRAFT_LINK`, `ACCEPT_LINK`.

## Packs (0.22.0)

Une troisième ligne sous Architecture et Contrôles, depuis `packs` de `workspace --json` d'archvow 1.26 (lu avec
le reste, sans appel de plus) : « 2 packs · 1 modifié à la main ». **Voir les packs →** liste chaque pack de
`.archvow/packs/` : sa version, ce qu'il apporte (templates, agents, règles), sa source, et s'il est tel
qu'ajouté ou modifié à la main (ARCH060, avec ce qui diffère). Chacun a **Mettre à jour**, **Retirer** et
**Vérifier** (les formulaires de `pack update`, `pack remove`, `pack check`, son nom rempli) ; un pack modifié
a aussi **Voir le changement** (`diff`). En haut, **Ajouter un pack** et **Nouveau pack** (`pack add`, `pack
new`). Les deux plugins font la même chose : VS Code `packsSummary()` / `packsView()` dans
`vscode/media/workspace.js` ; IntelliJ `ArchvowPacks` (la ligne, la liste, ce qu'ouvre chaque lien `archvow-pack:`),
testé par `PacksRegression`.

## La langue

En bas d'Overview : la langue de l'espace de travail (celle de l'IDE, anglais ou français). Elle est
là, et pas dans l'en-tête, pour que l'en-tête garde la place du nom du projet dans une fenêtre étroite.
