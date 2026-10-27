# 17. Glossaire

> 🇬🇧 [English version](/en/17-glossary) · Version française

Les mots d'archvow (vow, composant, décision, danger, invariant...) sont expliqués dans le glossaire du
wiki d'archvow ; voici ceux des plugins, avec où les trouver dans le code.

| Terme | Sens | Code |
|---|---|---|
| **barre latérale** (navigation) | ce qui choisit : Overview, Components, Commands. IntelliJ : la fenêtre d'outils Archvow à gauche ; VS Code : la barre d'activité | `ArchvowPanel` en rôle `NAVIGATION`, `ArchvowNavigationTabs` ; la page en rôle `nav` |
| **l'onglet** (détail) | **un seul** onglet d'éditeur qui montre ce qui est choisi, réutilisé pour le choix suivant | `ArchvowEditorTab`, `ArchvowEditorProvider`, `ArchvowPanel` en rôle `DETAIL` ; le *webview panel*, la page en rôle `editor` |
| **Overview** | l'écran du projet : son état, l'architecture, Check & Gates, le travail en attente et fait, les commandes de projet | page [Overview](/07-overview) |
| **vue** (d'un composant) | Décisions, Questions, Sécurité, Hypothèses, Règles, Tâches, Full vow, Autres / Projet | `addComponentView`, la constante `VIEWS` de la page |
| **carte** | un élément d'une vue (une décision, une question...) sur une ligne, à gauche | `ArchvowItemCards`, `cards()` |
| **panneau de réponse** | la carte choisie, à droite (ou dessous) : la question entière, le champ, les actions | `ArchvowSplitView`, `split()` |
| **premier élément ouvert** | la carte choisie à l'ouverture d'un composant : le premier élément sans réponse, sinon le premier | `ArchvowItemCards.firstOpen`, `current()` |
| **en attente** (pending) | ce qui demande une personne : une décision sans réponse, une question de sûreté non traitée, une hypothèse proposée... | `ArchvowSafetyState`, `viewCounts()` |
| **action sur place** (inline) | un bouton d'une vue qui lance une commande pour la carte choisie (Enregistrer la réponse, Déclarer le danger) | `ArchvowInlineCommands`, l'objet `inline`, `inlineAction` |
| **confirmation** | la commande exacte, montrée sous le bouton, avant qu'elle parte | `ArchvowConfirmPanel`, `requestConfirmation` ; `confirm` dans l'extension |
| **commande de lecture** | une commande qui ne change rien et part sans confirmation (`show`, `next`, `check`...) | `READ_ONLY` |
| **commande destructive** | une suppression : confirmée, puis lancée avec `--yes` | `DESTRUCTIVE` |
| **grammaire** | toutes les commandes d'archvow et leurs paramètres, tirées de son parseur | `contract/cli/commands.json`, `ArchvowGrammar`, `findCommand()` |
| **pré-remplissage** | les champs d'un formulaire remplis avec ce qui est connu, jamais deviné | `ArchvowCommandPrefill`, `prefill()` |
| **Retour** | dans l'onglet, revenir au composant d'où une commande a été ouverte, sur sa vue | `goBack`, `backComponent` ; `back` dans la page |
| **contrat** | ce que les plugins et la CLI ont en commun : vows d'exemple, JSON enregistré, grammaire, catalogue, sens des codes, version | `contract/`, page [Le contrat](/04-contract) |
| **version connue** | la version d'archvow du contrat ; une autre version mineure est signalée | `ArchvowVersion`, `knownVersion()` |
| **candidat** | une façon de lancer archvow : l'interpréteur de l'IDE, le Python des réglages, l'exécutable ; le premier qui répond à `--version` est gardé | `ArchvowProcessCommand.Candidate`, `candidates()` |
| **WSL** | archvow installé dans Linux sous Windows, lancé par `wsl.exe` avec des chemins Linux | `ArchvowWsl` |
| **instantané** (snapshot) | le dernier `show <composant> --json` lu, affiché tout de suite pendant qu'il est relu | `componentSnapshots` ; `snapshots` dans l'extension |
| **lecteur `.vow`** | le code qui coupe un vow en jetons, lit ses lignes et ses problèmes, sans archvow | `org.archvow.vow.core`, `vscode/src/vow` |
| **profondeur** | le niveau d'indentation d'un mot-clé (0, 1, 2, 3 et plus), qui donne sa couleur | `VowTokenKind` (`KEY_0`...) |
| **doublure** | une classe vide au nom d'une classe de la plateforme IntelliJ, pour compiler sans elle ; hors du dépôt | page [Les tests](/13-tests) |
| **régression** | un test IntelliJ : une classe avec un `main()`, inscrite dans `WorkspaceTests` | `intellij/src/test/java/org/archvow/idea` |
