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
plusieurs lignes (`LONG_TEXT`). Le champ `type` de `component add` propose les types du catalogue
(`ArchvowComponentTypes`, `componentTypes()`).

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
  et la signature (de son entrée dans la spec). On voit ce qui est là et on change ce qu'on veut ; un
  champ laissé tel quel ne change rien ;
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
ou modifiées. Un chemin de template personnalisé reste modifiable à la main.

## Afficher archvow pour un fichier

Clic droit dans un éditeur ou sur son onglet : **Show Archvow for This File** lance
`archvow show <fichier>:<ligne>` (la ligne du curseur) et ouvre le composant qui possède le fichier ;
un fichier qu'aucun composant ne possède reçoit l'indication d'archvow (le `component add ... --file` à
lancer). IntelliJ : `ShowFileArchvowAction`, `ArchvowPanel.showFile` ; VS Code : la commande
`archvow.showFile`, `ArchvowProject.showFile`.
