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
| `NOT_INITIALIZED` | pas de `.archvow/project.vow` : **Initialiser Archvow** (l'assistant `init`, page [Les commandes](/09-commands)), comment adopter du code déjà là, et (0.25.0) **Pas encore sûr ? Regarde-le attraper une erreur** : `archvow try <exemple>` sur un exemple fini dans un dossier jetable, son résultat sous la carte, puis *Installer archvow dans ce dépôt* ou *Lire la spec de l'exemple* |
| `SPEC_INACCESSIBLE` | `project.vow` vide ou illisible |
| `READY` | l'espace de travail ; si la CLI échoue malgré tout, son message, avec Configurer Python et Réessayer |

## Le haut d'Overview (0.25.0)

De haut en bas, le même dans les deux IDE (VS Code : `overviewTab()` dans `vscode/media/workspace.js` ;
IntelliJ : `overviewPanel()`, les cartes d'`ArchvowOverviewCards`) : le conseil de branche, **Premiers pas**
(pour un nouveau projet), la barre des commandes, **Architecture**, **Contrôles**, **Packs**. La bande « État
du projet » (en attente, traités) a disparu en 0.25.0 : ses compteurs sont les liens de la carte Architecture.

Sur `main`, `master`, `develop` ou `development`, un conseil : créer une branche de fonctionnalité
(`ArchvowBranchAdvice`, `needsFeatureBranch()`). C'est un conseil, rien de plus : la politique de branches
appartient au dépôt et à la CI.

## Premiers pas (0.25.0, archvow 2.1)

Cinq étapes qu'archvow lit dans la spec, la vérification et Git (`workspace --json` « first_steps », contrat
2.1 ; `views/first_steps.py` côté archvow) : répondre aux fondations, ajouter une feature, ajouter son premier
composant et répondre à ses décisions, le construire puis Check, commiter. Le plugin ne calcule rien : il
affiche « N sur 5 faits », la première étape non faite avec son bouton (Spécifier, Ajouter une feature,
Ajouter un composant, Check ; aucun pour Commiter) et le lien « Essayer archvow sur un exemple d'abord »
(`archvow try`). La carte part quand les cinq sont faites : les exemples, finis, ne la montrent jamais.
Lecture : `firstSteps()` dans `vscode/src/overview.ts`, `ArchvowOverview.firstSteps` ; les deux lisent les
exemples à l'identique (`contract/overview/expected.json`, `OverviewRegression`).

## La barre des commandes

| Bouton | Commande | Où va le résultat |
|---|---|---|
| Next | `next --json` | l'onglet |
| Check | `check --complete --json` | l'onglet ; et Check & Gates se met à jour |
| Plus ▾ › Doctor | `doctor --json` | l'onglet |
| Plus ▾ › Audit | `audit --json` | l'onglet |
| Plus ▾ › Briefs | le navigateur des briefs | l'onglet |
| Plus ▾ › Budget / Next | `next --base <Base> --json` | l'onglet |
| Plus ▾ › Diff, AI review | le formulaire de `diff` / `review`, `--base` déjà rempli | l'onglet |

Next et Check sont nommés ; **Plus ▾** ouvre les six autres, chacune avec son icône (`projectToolbar()` ;
`ArchvowOverviewCards.toolbar`). ↻ (relire le projet) est dans l'en-tête. Le **.vow du projet** n'a pas de
bouton (0.24.3) : il se lit par les commandes et les pages. La **Base** sert à Budget, Diff et Review :
la meilleure branche qui existe ici (`bestBase` : `develop` sur une branche de fonctionnalité Git flow, sinon
ce que nomme `origin/HEAD`, puis `origin/main`, `main`, `master`...), jusqu'à ce qu'une personne en
choisisse une dans la liste des branches et tags du dépôt (`git for-each-ref`, `refNames`). Une base qui
n'existe pas est refusée avant de lancer quoi que ce soit, avec les branches qui existent (`missingRef`,
`refProblem`).

## Les cartes Architecture et Contrôles (0.25.0)

**Architecture** compte ses propres éléments - décisions, questions, questions de sûreté, hypothèses,
colonnes, tâches : « Éléments faits 45 sur 70 », une barre, « 25 ouverts : décisions, questions et tâches.
Les constats des contrôles comptent dans Contrôles. » (sauf tant que Premiers pas est là), puis **Éléments
ouverts**, **Éléments faits** et, s'il y en a, **Éléments projet** : le premier composant qui a de ce travail,
sur la bonne vue et le bon élément (`openQuickList`), ou la vue *Autres / Projet*. Les éléments viennent de
`buildWork()` (`vscode/src/workItems.ts`) et `rebuildQuickModels` (IntelliJ) ; les constats de `check` n'y
comptent pas (`overview()` dans `vscode/src/overview.ts`, `ownItem` dans `ArchvowPanel`).

**Contrôles** donne les erreurs et avertissements, puis le **premier constat** : sa règle en mots (le
`title` de `check --json`), `ARCH005 · compute_vat.py:16`, et le bouton qui ouvre le fichier, nommé par ce
qu'il est (Ouvrir le code source, Ouvrir la vow, Afficher le dossier de configuration : `checkFileLabel`,
`ArchvowCheckActions.fileLabel`). Sans constat, une ligne : les totaux et « Voir les contrôles → ».

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
ARCH053 1 »), puis la liste des diagnostics, chaque ligne menée par sa règle en mots (le `title` d'archvow 2.1), puis
son code et sa gravité, puis le message (0.25.0). Le diagnostic choisi montre son message, son fichier,
et **l'explication de sa règle**, tirée de `contract/cli/rules.md` (`ArchvowRuleDocs`,
`ruleExplanation()`). Voir la règle ouvre la référence des contrôles ; Ouvrir le fichier va à la ligne.

## Briefs (0.16.0)

Un troisième sous-onglet à côté d'Architecture et de Check & Gates, depuis `brief show --json` d'archvow 1.20 (lu
avec l'architecture ; un archvow plus ancien n'a pas cette commande : l'onglet le dit). Chaque fichier de brief,
section par section : ✓ utilisée (les éléments qui en sont tirés), ○ pas encore utilisée avec **Rédiger avec
l'IA…** (le formulaire de draft-spec, la section remplie), ! changée depuis avec **Relire…** (ouvre la section) et
**Accepter le changement…** (le formulaire de `brief accept`). Son compte est les sections pas utilisées plus
celles qui ont changé ; Plus ▾ › Briefs l'ouvre. **Ajouter un dossier de briefs
(architecte)…** ouvre le formulaire d'init prérempli. VS Code : `briefsView()`, `briefsDue()` ; IntelliJ :
`ArchvowBriefs.statusHtml`, `ArchvowBriefs.due`, liens `DRAFT_LINK`, `ACCEPT_LINK`.

## Packs (0.22.0)

Une troisième ligne sous Architecture et Contrôles, depuis `packs` de `workspace --json` d'archvow 1.26 (lu avec
le reste, sans appel de plus) : « 2 packs · 1 modifié à la main ». **Voir les packs →** liste chaque pack de
`.archvow/packs/` : sa version, ce qu'il apporte (templates, agents, règles, régimes de protection), sa source, et s'il est tel
qu'ajouté ou modifié à la main (ARCH060, avec ce qui diffère). Chacun a **Mettre à jour**, **Retirer** et
**Vérifier** (les formulaires de `pack update`, `pack remove`, `pack check`, son nom rempli) ; un pack modifié
a aussi **Voir le changement** (`diff`). En haut, **Ajouter un pack** et **Nouveau pack** (`pack add`, `pack
new`). Les deux plugins font la même chose : VS Code `packsSummary()` / `packsView()` dans
`vscode/media/workspace.js` ; IntelliJ `ArchvowPacks` (la ligne, la liste, ce qu'ouvre chaque lien `archvow-pack:`),
testé par `PacksRegression`.

## La langue

En bas d'Overview : la langue de l'espace de travail (celle de l'IDE, anglais ou français). Elle est
là, et pas dans l'en-tête, pour que l'en-tête garde la place du nom du projet dans une fenêtre étroite.
