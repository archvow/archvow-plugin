# 6. La barre latérale et l'onglet

> 🇬🇧 [English version](/en/06-sidebar-and-tab) · Version française

Les deux plugins ont la même disposition : **la barre latérale choisit, un seul onglet d'éditeur
montre**. Overview, Components (l'arbre) et Commands (la liste) sont à gauche ; ce qu'on y choisit
(un composant, le formulaire d'une commande, le résultat d'une commande d'Overview) s'ouvre dans
l'onglet *archvow: clean_orders*, réutilisé pour le choix suivant. Jamais un onglet par composant.

## IntelliJ : une classe, deux rôles

`ArchvowPanel` construit tout l'espace de travail et montre ce qui revient à son rôle
(`ArchvowPanel.Role`) :

| Rôle | Où | Ce qu'il montre | Créé par |
|---|---|---|---|
| `NAVIGATION` | la fenêtre d'outils *Archvow*, à gauche | Overview, l'arbre des composants, la liste des commandes | `ArchvowToolWindowFactory` (et rangé dans le projet sous la clé `PANEL`) |
| `DETAIL` | l'onglet *archvow* | le résultat (carte 0), le composant (carte 1), le formulaire de commande (carte 2) ; la barre d'onglets cachée | `ArchvowMainFileEditor`, quand l'onglet s'ouvre |

**L'onglet unique** : `ArchvowEditorTab` est un fichier virtuel (`LightVirtualFile`), un seul par
projet. `ArchvowEditorProvider` l'accepte et cache l'éditeur de texte par défaut
(`HIDE_DEFAULT_EDITOR`) ; il ouvre un `ArchvowMainFileEditor`, qui porte un `ArchvowPanel` en rôle
`DETAIL`. `ArchvowEditorTab.panel(project)` ouvre l'onglet s'il est fermé (sans prendre le focus de la
fenêtre d'outils) ; `openPanel(project)` ne l'ouvre jamais. Le titre (*archvow: clean_orders*) et
l'icône viennent d'`ArchvowEditorTabPresentation`, tenus à jour par `ArchvowEditorTab.setTitle`.

**Qui lit le projet** : la fenêtre d'outils. `reload` dans l'onglet délègue à la fenêtre d'outils ;
celle-ci lit (`show`, `check`, `next`, chaque composant) puis passe ce qu'elle a lu à l'onglet
(`pushToDetail` -> `adoptFrom`). `adoptFrom` ne redessine le composant affiché que si son JSON a changé
(on peut être en train de taper une réponse). Seul, sans fenêtre d'outils, l'onglet lit lui-même.

**Qui suit qui** : un clic dans la fenêtre d'outils route vers l'onglet (`ArchvowEditorTab.showComponent`,
`showCommand`, `runFromOverview`). Quand l'onglet change de lui-même (un lien *Uses*, Clarify, Retour),
`afterSelectionChange` met à jour son titre et appelle `follow` sur la fenêtre d'outils, qui
sélectionne le même composant sans renvoyer de clic (le drapeau `syncing`).

## VS Code : une page, deux rôles

`vscode/media/workspace.js` est une seule page, chargée deux fois avec `window.ARCHVOW_ROLE` :

| Rôle | Où | Créé par |
|---|---|---|
| `nav` | la vue *archvow.workspace* de la barre d'activités | `registerWebviewViewProvider` dans `vscode/src/extension.ts`, puis `ArchvowProject.attachView` |
| `editor` | le *webview panel* `archvow.detail` | `ArchvowProject.editor()`, à la demande ; `retainContextWhenHidden` |

La page n'exécute rien : elle envoie des messages (`postMessage`) et l'extension (`workspace.ts`,
méthode `receive`) décide. L'extension parle à la page par :

| Méthode | À qui |
|---|---|
| `toEditor` | l'onglet ; l'ouvre s'il est fermé ; garde les messages en file tant que la page n'a pas dit `ready` |
| `toOpenEditor` | l'onglet seulement s'il est ouvert (un résultat qu'il attend) |
| `toNav` | la barre latérale |
| `reply` | celui qui a demandé |
| `publish` | les deux : `{type: 'state'}`, tout ce qu'archvow a dit (`State`) |

Au chargement, chaque page envoie `ready` ; l'extension répond `init` (la grammaire, le catalogue,
les règles, les types de composants) puis `state`. La page de l'onglet envoie `title` quand ce qu'elle
montre change : c'est le titre de l'onglet. La sécurité : une Content-Security-Policy stricte
(`html()`), un *nonce* par script, rien chargé de l'extérieur.

## L'onglet Composants (0.15.0)

L'arbre dessine ce qu'est chaque nœud (`contract/forms/icons.json`) : le système, chaque couche, le type de
chaque composant (schema, reader, writer, transformation, job, point d'entrée...) dans la couleur de son état,
et l'onglet Commandes chaque groupe et chaque commande par son verbe. **Ajouter un composant**, à côté de
Tous / En attente / Traités, ouvre le formulaire `component add` ; sur une couche, **+** (VS Code) ou un clic
droit (IntelliJ, `installLayerMenu()`) liste les types de cette couche (`addableByLayer()`, `ArchvowFilePlan`)
et ouvre le formulaire avec le type choisi. Sous Tous, les couches vides du modèle s'affichent aussi (« Pas
encore de composant ») : le premier job ou point d'entrée a sa place. Un bouton dans l'arbre plutôt qu'une
commande de plus : l'ajout se fait là où l'on regarde l'architecture, au bon niveau.

## Retour

Une commande ouverte **depuis un composant** (Modifier la spec, + Question, + Rule, Accepter...)
affiche **← clean_orders** dans l'en-tête de l'onglet ; il ramène au composant, sur la vue où l'on
était (Sécurité...). Un choix fait dans la barre latérale efface le retour : il n'y a plus de composant
d'où l'on vient.

- IntelliJ : `openCommand` retient `backComponent` et `backView` quand l'onglet montre un composant ;
  `showCommand` (appelé par la fenêtre d'outils) met `fromToolWindow`, ce qui l'efface ; `goBack`.
- VS Code : `post()` arme `ui.backArmed` quand la page demande une commande depuis un composant ;
  le message `openCommand` qui revient le garde (`ui.back`), `select` l'efface ; l'action `back` renvoie
  `show`.

## Quand un vow change sur le disque

Qu'il change par une commande dans un terminal, un agent IA, un `git pull` : le plugin relit.
`ArchvowVowWatcher` écoute les changements de fichiers de la plateforme et regroupe les changements
(une relecture `QUIET_MS` = 800 ms après le dernier, et de nouveau plus tard si le plugin était occupé).
VS Code : un `FileSystemWatcher` sur `.archvow/**/*.vow`, puis `refreshSoon` (même délai).

## Au démarrage

IntelliJ : `ArchvowOpenOnStartup` montre la fenêtre d'outils quand le projet a un `.archvow/spec.vow`,
rien pour les autres projets. VS Code : l'extension s'active sur `workspaceContains:.archvow/spec.vow`
ou à l'ouverture d'un `.vow` ; dans un dossier non approuvé, seul le langage `.vow` fonctionne.
