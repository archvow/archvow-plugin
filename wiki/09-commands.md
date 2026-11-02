# 9. Les commandes

> 🇬🇧 [English version](/en/09-commands) · Version française

L'onglet Commands de la barre latérale liste **chaque commande archvow** ; une commande choisie ouvre
son formulaire dans l'onglet. Les actions d'un composant (Modifier la spec, + Question, Accepter une
hypothèse...) ouvrent les mêmes formulaires, pré-remplis.

## Des formulaires tirés du parseur d'archvow

Les formulaires ne sont pas écrits à la main : ils sont construits à partir de
`contract/cli/commands.json`, que `contract/tools/export_grammar.py` tire du parseur argparse
d'archvow (page [Le contrat](/04-contract)). Une option nouvelle dans archvow est dans le formulaire
dès que le contrat est régénéré, sans changer le code.

Chaque paramètre a un nom, ses options (`-d`, `--description`), obligatoire ou non, une case à cocher
(booléen), une liste de choix, plusieurs valeurs (une par ligne), et son aide (affichée sous le champ).
IntelliJ : `ArchvowGrammar.COMMANDS`, `renderForm` ; VS Code : `commandForm()` dans la page,
`findCommand()`. Les champs de texte long (`description`, `answer`, `statement`, `words`...) sont sur
plusieurs lignes (`LONG_TEXT`). Les options qu'archvow cache dans `--help` (`argparse.SUPPRESS` :
`--verify`, `--evidence`, `--rule` d'`invariant add`) sont marquées `hidden` par
`contract/tools/export_grammar.py` et rangées sous « Contrôles experts », fermé (0.13.1 : leur aide
affichait `==SUPPRESS==`).

## La mise en page d'un formulaire (design A, 0.14.0)

La grammaire dit ce que prend une commande ; `contract/forms/forms.json` (le fichier des plugins, lu par
les deux) dit comment son formulaire se lit :

| Dans forms.json | Dans le formulaire | Code |
|---|---|---|
| `labels` (par paramètre), `commands.<c>.labels` | le champ en mots simples (« Ce qu'il doit faire », « Toujours vrai »), l'option d'archvow en petit à côté | `fieldLabel()`, `ArchvowForms.label` |
| `commands.<c>.essential` | ces champs d'abord, dans cet ordre (et chaque champ obligatoire) ; les autres sous **Plus d'options**, ouvert quand l'un a une valeur ; les contrôles experts dedans | `isEssential()`, `ArchvowForms.essential` / `ordered` |
| `commands.<c>.title`, `verb`, `does` | le titre du formulaire ; le bouton (« Ajouter l'invariant », à côté d'Annuler) ; « Ce que va faire archvow » à droite | `commandForm()`, `buildForm` |
| `commands.<c>.hints` | la ligne sous un champ, à la place de l'aide d'archvow | |

Une commande ou un paramètre absent y prend les mots d'archvow. Un test dans chaque IDE vérifie que chaque
nom de forms.json est l'un de `commands.json`, et celui de VS Code que chaque champ obligatoire est
essentiel. Un paramètre d'au plus quatre valeurs fixes (gravité, criticité, verify...) est un bouton par
valeur (`segmented()`, `ArchvowSegmented`).

À droite, **la commande telle qu'elle se tape** : la page de VS Code la demande à l'extension
(`formPreview`, après une courte pause), qui la planifie avec `planForm` - la grammaire de Run - et l'écrit
en place (sans redessiner : le curseur reste) ; IntelliJ calcule `ArchvowForms.commandLine` à chaque
changement. Un champ obligatoire encore vide est nommé à la place.

**Le refus d'archvow en place** : après Run, le code de sortie 2 (archvow a refusé, rien d'écrit) affiche
ses mots en rouge sous le formulaire ; les avertissements de ce qu'il a écrit quand même (les `warnings`
d'une entrée `--json`, ou les lignes `!`) en orange (`alerts()` dans `grammar.ts`, `ArchvowForms.refusal`
/ `warnings`, `showFormAlerts`).

## Ce qu'un champ propose

Personne n'a à connaître un identifiant par cœur (0.13.0). `choiceBook()` dans `vscode/src/choices.ts`
(IntelliJ : `ArchvowChoices.book`, les mêmes règles : `ChoicesRegression` compare les deux sur
`contract/choices/expected.json`) dit, pour chaque commande et paramètre, ce que le formulaire propose, à
partir de ce qu'archvow a affiché (`show --json`, `show <composant> --json`, `catalog --json` lancé dans
le projet, les références du dépôt) :

| Champ | Propose |
|---|---|
| `component` (toutes les commandes sauf `component add`) | les composants du projet, « clean_orders — transformation » ; pour **Edit spec**, en choisir un remplit les champs avec ce qu'il a |
| question de `decision make` | les questions du composant, les ouvertes d'abord, puis P1, O1... ; en choisir une remplit la réponse enregistrée, proposée ou suggérée |
| `decision propose` / `decision accept` | les questions ouvertes / les propositions de l'IA en attente |
| numéro de `question remove` | les questions ajoutées par une personne (archvow 1.18 marque celles du modèle : `template: true` ; elles restent) |
| id de `hazard edit` / `invariant edit` | les hazards / invariants du projet ; en choisir un remplit ses mots (et sa gravité) ; un invariant lié à une décision n'a pas de mots à remplir |
| `--from-decision` d'`invariant add` | les décisions du composant qui ont une réponse : l'invariant est ce que cette décision répond |
| `question add` / `decision add` | pas de numéro à donner : la ligne sous le champ dit lequel archvow donnera |
| `assumption accept/reject`, `example remove`, `prompt --task` | les hypothèses en attente, exemples, tâches du composant |
| mot guide de `hazard add/dismiss`, `invariant add --prevents` | les questions de sécurité (ouvertes d'abord) / les dangers du composant |
| `hazard remove`, `invariant remove`, `rule edit/remove`, `dependency remove` | les identifiants du projet ; `rule edit` remplit le texte de la règle |
| type et `--kind` de `component add` | les types du modèle du projet seulement ; les sortes du type choisi, celle par défaut d'abord (le catalogue d'archvow 1.18, sinon `contract/cli/kinds.json` pour 1.17) |
| `--extends` de `template new` | les modèles du catalogue |
| `--base`, début/fin de `changes` | les branches et tags du dépôt |

Un champ `select` ne prend que ce qu'il liste ; une liste qui dépend du composant ou du type l'attend
(« Choisis d'abord le composant ») ; une liste vide dit pourquoi (« Aucun danger déclaré dans ce
projet. »). Un champ `suggest` (une branche, un modèle) prend aussi ce qui est tapé. Dans IntelliJ la
liste montre le libellé de chaque valeur à la largeur de son champ, sa liste ouverte plus large
(`readableChoices`), et les lignes sous les champs passent à la ligne à la largeur du formulaire
(`WidthTrackingPanel`, `ArchvowWrapLabel`) : elles dépassaient le bord de la fenêtre.

Le champ `--from` (component add / edit, question add, decision add, rule add) propose les fichiers
`.md` et `.txt` des dossiers de briefs du projet et chaque titre Markdown en `fichier#section`, avec les
ancres d'archvow lui-même : minuscules, sans accents, mots joints par `-`, un titre répété `-2`, `-3`
(`ArchvowBriefs.slug`, `slug()` et `sections()` dans `vscode/src/briefs.ts`). Le texte libre reste
permis : archvow décide. Quand archvow refuse un fichier hors des dossiers, son message nomme
l'`archvow init <package> --force -a briefs=...` qui l'ajoute ; le résultat affiche **Ajouter ce dossier
(architecte)**, qui ouvre le formulaire init pré-rempli (`addFolderButton()`,
`ArchvowBriefs.withAddFolder`) - l'architecte le vérifie et confirme ; rien ne se lance tout seul.

## Du formulaire à la ligne de commande

`planForm()` (IntelliJ : `submitForm`) transforme les champs en arguments, dans l'ordre de la
grammaire : un champ obligatoire vide est refusé ; une case cochée donne son option ; plusieurs
valeurs donnent l'option devant chacune ; `--json` est ajouté quand la commande le connaît.
Quelques cas sont tenus à la main :

| Cas | Pourquoi |
|---|---|
| `specify` n'est pas lancé : l'onglet ouvre le premier élément ouvert du composant | `specify` pose ses questions dans un terminal |
| `rule add` refuse un composant seul, sans texte de règle | le texte d'une règle est celui d'une personne, jamais deviné |
| `prompt --task T2` devient `--task 2` | la CLI attend un nombre (`ArchvowTaskPrompt`, `taskNumber()`) |
| `init` reçoit `--no-input` et les réponses de l'assistant (`--answer CLÉ=VALEUR`) | pas de terminal pour poser les questions |

## Confirmer avant de changer

Une commande qui ne change rien (premier mot dans `READ_ONLY` : `show`, `catalog`, `next`, `check`,
`audit`, `doctor`, `changes`, `diff`, `prompt`) part tout de suite. **Toute autre commande demande une
confirmation** qui montre la commande exacte (`preview()`) sous le bouton qui l'a demandée ; rien ne part
avant « Confirmer et exécuter » (`isMutating()`). Une suppression (`DESTRUCTIVE` : `component remove`,
`question remove`, `rule remove`, `hazard remove`, `invariant remove`) reçoit en plus `--yes`, après la
confirmation de la personne. Après une commande réussie qui change quelque chose, le plugin relit le
projet.

## Le pré-remplissage

`ArchvowCommandPrefill.prepare` (VS Code : `prefill()` dans `vscode/src/grammar.ts`) remplit ce qui est
connu, jamais ce qui serait deviné :

- le composant de la commande, quand elle en prend un et qu'un composant est choisi (`usesComponent`) ;
- **Modifier la spec** depuis un composant : chaque champ que le composant a déjà : la description et
  `uses` (de `show <composant>`), le ticket (`--spec`), les propriétaires, le groupe, le brief (`--from`)
  et la signature (de son entrée dans la spec). On voit ce qui est là et on change ce qu'on veut ; seuls
  les champs changés sont envoyés (`formBaseline` : ce que le composant a, même ouvert depuis un
  diagnostic ; `planForm(..., { baseline })` ; IntelliJ : `whatTheComponentHas`, `submitForm`), et rien
  de changé est dit, pas lancé (« Rien n'a changé ») : archvow 1.18 ne marque donc à revoir que ce qui a
  vraiment changé ;
- une correction proposée par un diagnostic reste **précise** : elle ne remplit que son propre champ,
  pour ne pas changer autre chose par mégarde ;
- `component add` reçoit le conteneur du composant choisi, jamais un nom inventé.

Pour `decision make`, `decision propose`, `decision accept` et `question remove`, le formulaire montre
la question et son état (« Charger la question ») avant qu'on réponde.

## L'assistant init

Dans un dossier sans `.archvow/spec.vow`, **Initialiser Archvow** ouvre le formulaire d'`init` avec un
assistant : le template, puis ses questions (les essentielles ; les autres avec « réglages avancés »).
Les questions viennent d'`archvow catalog --json` lancé hors du projet (les templates installés, ceux
d'entreprise compris, `ARCHVOW_TEMPLATES`), sinon de la copie du catalogue embarquée
(`ArchvowInitCatalog`, le catalogue de `contract/cli/catalog.json`). `ArchvowInitQuestions` (VS Code :
`initWizard()`) n'émet que des réponses documentées, `--answer CLÉ=VALEUR`, et seulement celles affichées
ou modifiées. Le modèle se choisit **une seule fois**, dans « Modèle et ses questions », juste sous le nom
du paquet (0.13.0 : le champ `--template` du formulaire en faisait un second endroit) : un modèle du
catalogue, ou le dossier d'un modèle d'entreprise ; dans un projet, son propre modèle d'abord, avec une
ligne qui dit qu'`init` y demande `--force`.

## Créer un modèle d'entreprise

`template new` : choisir le modèle de base (`--extends`, la liste du catalogue) remplit sous lui
« Réglages de <base> » avec les valeurs de la base (`ArchvowTemplateSettings` ; VS Code :
`templateWizard()`). Ce que l'entreprise peut régler - les valeurs par défaut des réponses d'init, le
dossier de chaque couche - se modifie, dans ce que la base permet : ses choix seulement (une liste, rien
à taper), ses formats (le champ passe en rouge, l'envoi est refusé avec le format attendu). Ce que la base
impose - ce qu'elle étend, sa version, ses couches, ses choix - est affiché, pas modifiable. Seul ce qui
diffère de la base devient `-a CLÉ=VALEUR` et `--layer ANCIEN=NOUVEAU`.

## Des résultats lisibles

Un résultat de commande s'ouvre à 225 px : **Agrandir** le met à la hauteur de la fenêtre (de l'onglet
dans VS Code), et la barre sous lui se tire pour le grandir ou le réduire (`resultGrip`,
`expandedResultHeight` ; VS Code : `expandResult`, `data-grip`). Partout où un résultat s'affiche. Le
titre « Résultat de la commande » n'y est plus répété (`generic` dans les deux formateurs).

## Afficher archvow pour un fichier

Clic droit dans un éditeur ou sur son onglet : **Show Archvow for This File** lance
`archvow show <fichier>:<ligne>` (la ligne du curseur) et ouvre le composant qui possède le fichier ;
un fichier qu'aucun composant ne possède reçoit l'indication d'archvow (le `component add ... --file` à
lancer). IntelliJ : `ShowFileArchvowAction`, `ArchvowPanel.showFile` ; VS Code : la commande
`archvow.showFile`, `ArchvowProject.showFile`.
