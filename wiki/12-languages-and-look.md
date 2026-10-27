# 12. Langues et apparence

> 🇬🇧 [English version](/en/12-languages-and-look) · Version française

## Anglais et français

Les deux plugins parlent anglais ou français, selon un réglage : celui de l'IDE (par défaut), `EN` ou
`FR`. Le code est en anglais ; chaque texte affiché passe par une petite fonction qui prend les deux
versions.

| | IntelliJ | VS Code |
|---|---|---|
| le choix | `ArchvowI18n.modes()` : `IDE`, `EN`, `FR` ; rangé dans `ArchvowSettings` (`languageMode`) | `archvow.language` : `auto`, `en`, `fr` |
| un texte | `ArchvowI18n.text(project, "Save answer", "Enregistrer la réponse")` | `t('Save answer', 'Enregistrer la réponse')` dans la page, `tr(fr, ...)` dans l'extension |
| où le changer | en bas d'Overview, ou Settings > Tools > Archvow | en bas d'Overview, ou les réglages |

Ce qui vient d'archvow (ses messages, les questions de ses templates) est affiché tel quel : la langue
du projet est celle qu'archvow lui a donnée à `init`. Les mots-clés `.vow` et les codes (ARCH012) ne se
traduisent jamais.

## Les couleurs

- Le vert d'archvow, **#1A876B** : le logo (`archvow-logo.png`, `vscode/media/archvow.png`, les icônes
  SVG au même tracé), la vue choisie, les éléments traités.
- Orange pour ce qui attend (« ● Ouvert », un compteur en attente), vert pour ce qui est fait, rouge pour
  une erreur : `ArchvowStatusPalette`, les règles `.tone-*` de `vscode/media/workspace.css`.
- VS Code prend toutes ses autres couleurs du thème (`var(--vscode-...)`) : clair, sombre et contraste
  élevé sans code en plus. IntelliJ suit le thème par `UIManager` et choisit entre deux teintes selon
  que le thème est sombre (`ArchvowMotion.dark()`).

## Les tailles de texte (IntelliJ)

Swing lit une taille CSS en `px` comme 1,3 point : `font-size:12px` dessinait du texte à 15,6 points, et
un `<h3>` plus gros encore (le résultat de `doctor` paraissait énorme). Depuis 0.11.4, **tout le HTML
affiché prend sa taille de la police de l'IDE**, en points : `ArchvowHtml.body` (le corps à la taille
d'une étiquette), `ArchvowHtml.heading` (un titre un peu plus grand, sans `<h1>`-`<h6>`),
`ArchvowHtml.size(facteur)`. `TextSizeRegression` refuse un `px` ou un titre `<h1>`-`<h6>` dans ce
que le plugin affiche.

## Les écrans étroits

Une fenêtre d'outils ou une barre latérale est souvent étroite. Les règles :

| Écran | Ce qui se passe quand c'est étroit | Où |
|---|---|---|
| l'en-tête de la fenêtre d'outils | le nom du projet se coupe, jamais sous les boutons ; la langue est en bas d'Overview | `buildHeader` (le nom au centre d'un `BorderLayout`) |
| le conseil de branche | sur plusieurs lignes | `ArchvowWrapLabel` |
| « État du projet » et ses compteurs | les compteurs passent sous le titre | `buildOverviewStatusStrip` |
| la barre d'actions | ce qui ne tient pas passe dans Plus ▾ | `ArchvowOverflowBar` |
| les vues d'un composant | ce qui ne tient pas passe dans Plus ▾ | `ArchvowCompactViews` |
| le tableau d'architecture | Composant, État, En attente restent ; les autres colonnes partent | `ArchvowArchitectureView.columnWidths`, règles `@media` |
| un composant | le panneau de réponse passe sous les cartes | `ArchvowSplitView.SIDE_BY_SIDE`, `.split` |
| les boutons sous un champ | les actions secondaires passent sur une seconde ligne | `ArchvowSplitView.actions` |

## Les boutons et icônes

- IntelliJ garde les boutons de son thème ; seul le bouton principal d'un panneau (Enregistrer la
  réponse, Déclarer le danger, Clarifier) est peint en couleur d'accent (`ArchvowSplitView.primary`),
  pour se lire pareil dans tous les thèmes.
- Les icônes sont dessinées en code, sans fichier ni police d'icônes : `ArchvowUiIcons` (IntelliJ),
  la constante `REFRESH` (un SVG) dans la page VS Code. Recharger est le même cercle qui tourne dans les
  deux.
- Le survol est discret (`ArchvowMotion`) : jamais plus visible que la sélection.

## Les dialogues

Un texte qu'on veut lire en entier ou copier (Tester Archvow, ce qui a été essayé quand archvow est
introuvable, l'aide du champ Python) s'ouvre dans `ArchvowTextDialog` : une boîte à la taille du texte
(640 × 320 au plus, puis elle défile), le texte sélectionnable, **Copier la commande** (la ligne exacte,
entre guillemets là où il faut pour un terminal), Copier, Fermer. Un message court reste un simple
message.
