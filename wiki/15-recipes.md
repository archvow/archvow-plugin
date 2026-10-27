# 15. Recettes

> 🇬🇧 [English version](/en/15-recipes) · Version française

Pas à pas pour les changements les plus fréquents. Une règle pour tous : **les deux plugins changent
ensemble** (même fonction, même mot en anglais et en français), et **les pages du wiki concernées
changent en français et en anglais dans le même commit**.

## Ajouter une action dans une vue d'un composant

Un bouton qui lance une commande archvow (par exemple « Rejeter la proposition »).

1. **La commande** : une fonction qui rend les arguments, sans rien lancer.
   IntelliJ : une méthode statique de `ArchvowInlineCommands` (comme `decide`, `dismiss`) ;
   VS Code : la même dans l'objet `inline` de `vscode/src/grammar.ts`. Un argument obligatoire vide lève
   une erreur avec une phrase pour la personne.
2. **Le bouton** : IntelliJ, dans `ArchvowPanel` avec les autres boutons de la vue, passés à
   `ArchvowSplitView.actions` (à gauche les actions secondaires, à droite le bouton principal) ;
   VS Code, dans la vue de la page (`media/workspace.js`) qui envoie
   `post({ type: 'inline', action: '...', component, ... })`.
3. **La confirmation** : IntelliJ, `requestConfirmation` avec la commande et un résumé ; VS Code,
   un `case` de plus dans `inlineAction` (`vscode/src/workspace.ts`) qui construit `args` et `summary`.
   Rien ne part sans « Confirmer et exécuter » ; après, le composant est relu (`reloadComponent`).
4. **Les tests** : la ligne de commande dans `InlineCommandRegression` et `workspace.test.ts`
   (« in-place actions »).

Une action qui ne change rien (lire, copier une instruction) ne demande pas de confirmation, mais
passe quand même par une fonction qui rend les arguments.

## Suivre un nouveau champ du JSON d'archvow

1. Régénérer le contrat (page [Le contrat](/04-contract)) : le champ apparaît dans
   `contract/cli/<exemple>/*.json`.
2. VS Code : le lire dans `vscode/src/workItems.ts` ou `vscode/src/model.ts` (avec `str()`,
   `objects()`, jamais de supposition sur un champ absent), l'afficher dans la page.
3. IntelliJ : le lire dans la classe de la vue (Gson, `JsonObject`), l'afficher.
4. Un test sur le JSON enregistré, dans les deux (un exemple du contrat qui a le champ).
5. Un champ absent (une archvow plus ancienne) ne doit rien casser : rien affiché, pas d'erreur.

## Ajouter un réglage

| | IntelliJ | VS Code |
|---|---|---|
| déclarer | un champ public de `ArchvowSettings.Values`, avec sa valeur par défaut | `contributes.configuration.properties` dans `vscode/package.json` (`archvow.<nom>`, type, défaut, description) |
| afficher | `ArchvowSettingsConfigurable` : le champ dans `createComponent`, puis `isModified`, `apply`, `reset` | rien : VS Code l'affiche |
| lire | `ArchvowSettings.get(project).getState().<nom>` | `vscode.workspace.getConfiguration('archvow').get(...)` ; pour la CLI, dans `cliSettings()` |

Le choix de l'interpréteur est mémorisé pour une liste de candidats (`ArchvowCli.choose`, `choose()`
côté VS Code) : un réglage qui change la façon de lancer archvow doit changer cette liste
(`ArchvowProcessCommand.candidates`, `candidates()` dans `vscode/src/cli.ts`), sinon l'ancien choix reste.

## Ajouter ou changer un texte

- IntelliJ : `ArchvowI18n.text(project, "English", "Français")` ; dans une classe sans projet, un
  booléen `fr` passé par l'appelant (comme `ArchvowResultFormatter`).
- VS Code : `t('English', 'Français')` dans la page, `tr(fr, 'English', 'Français')` dans l'extension.
- Le même mot dans les deux plugins (Sécurité, Hypothèses, « Rien à afficher. ») : chercher le texte
  anglais dans l'autre plugin avant d'en inventer un.
- Les mots d'archvow ne se traduisent pas : mots-clés `.vow`, codes de règles, noms de commandes.

## Afficher du HTML dans IntelliJ

Toujours partir de `ArchvowHtml.body` (la police et la taille de l'IDE) ; un titre avec
`ArchvowHtml.heading`, une taille relative avec `ArchvowHtml.size`. Jamais de `px` ni de `<h1>`-`<h6>` :
Swing les dessine trop gros (page [Langues et apparence](/12-languages-and-look)). Échapper tout texte
venu d'archvow. Ajouter la sortie à `TextSizeRegression` si c'est un nouvel écran.

## Corriger un écran trop étroit

1. Trouver la largeur où ça casse : IntelliJ, dans `runIde` en tirant la fenêtre d'outils ; VS Code,
   la barre latérale.
2. Choisir ce qui reste et ce qui part : le nom d'abord, puis l'état, puis le reste. Un texte d'avis
   passe à la ligne (`ArchvowWrapLabel`) ; des boutons passent dans Plus ▾ (`ArchvowOverflowBar`) ;
   une colonne se cache (`ArchvowArchitectureView.columnWidths`, une règle `@media` dans
   `vscode/media/workspace.css`).
3. Une régression à la largeur trouvée (`ResponsiveOverflowRegression`, `ArchitectureViewRegression`,
   `layout.test.ts`).

## Changer le lecteur `.vow`

1. **Le Java d'abord** (`org.archvow.vow.core`, la référence), avec un cas dans `CoreTests`.
2. `(cd intellij && ./gradlew contractDump coreTest)` réécrit `contract/vow-reader/expected.json`.
3. Le même changement dans `vscode/src/vow`, jusqu'à ce que `vowReader.test.ts` passe : le TypeScript
   doit donner exactement ce que le Java donne.
4. Relire `git diff contract/vow-reader/` : chaque jeton changé doit être voulu.

## Ajouter une régression IntelliJ

Une classe `intellij/src/test/java/org/archvow/idea/<Sujet>Regression.java` avec un `main()` qui lève
une `AssertionError` au premier échec et affiche une ligne `OK: ...` à la fin ; l'inscrire dans
`WorkspaceTests.main`. Pas de framework, pas d'IntelliJ : si le test en demande un, la logique doit
sortir du panneau dans une petite classe.

## Ajouter un test VS Code

Un `test('ce qui est vrai, en une phrase', ...)` dans le fichier du sujet (`vscode/src/test/*.test.ts`),
avec `node:test` et `node:assert/strict`. Il lit le contrat (`contract/cli`), jamais un archvow
installé ; pour lancer quelque chose, un faux `archvow` écrit par le test (`cli.test.ts`).
