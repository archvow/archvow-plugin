# 11. Problèmes et contrôles

> 🇬🇧 [English version](/en/11-problems-and-checks) · Version française

`archvow check --json` donne, pour chaque violation, son code (ARCH002...), sa gravité, son message,
son fichier, sa ligne et sa colonne. Les plugins s'en servent à deux endroits : **Check & Gates**
dans Overview (page [Overview](/07-overview)) et, dans VS Code, **la vue Problèmes** de l'éditeur.

## VS Code : la vue Problèmes

| Quand | Commande | Ce qui change |
|---|---|---|
| chaque relecture du projet, chaque Check | `check --json` | toute la collection de diagnostics `archvow` est remplacée (`showProblems`) |
| un fichier Python du projet enregistré | `check --file <fichier> --json` | les diagnostics de ce fichier seulement (`checkFile`) |

`problemsOf()` dans `vscode/src/model.ts` transforme le JSON en problèmes (fichier relatif au projet,
ligne et colonne à partir de 0, erreur ou avertissement). Chaque diagnostic porte la source `archvow` et
son code. Le contrôle à l'enregistrement se coupe avec `archvow.checkOnSave`.

La relecture tourne dans la file des commandes de fond : ce qu'une personne demande passe avant
(page [Lancer archvow](/05-running-archvow)).

## IntelliJ : Check & Gates, pas encore dans l'éditeur

Dans IntelliJ, les diagnostics sont dans Check & Gates (`ArchvowChecksView`) : choisir un diagnostic
montre son détail et l'explication de sa règle ; **Ouvrir le fichier** va à la ligne
(`openDiagnosticCode`, `openViolationFile`). Ils ne sont **pas encore** soulignés dans l'éditeur Python :
c'est le point 1 de `docs/analyse-intellij.md` (un `ExternalAnnotator` qui lancerait
`archvow check --file <f> --stdin --json` avec le texte de l'éditeur).

## Les erreurs d'un `.vow`

À ne pas confondre : les erreurs d'un fichier `.vow` (une tabulation, un guillemet non fermé) viennent
du lecteur `.vow` des plugins, sans lancer archvow, à chaque frappe (page [Le langage .vow](/10-vow-language)).
Ce qu'un mot-clé veut dire (une décision sans question, un danger sans invariant), c'est `archvow check`
qui le dit.

## Ce qu'un diagnostic peut proposer

Quand le message d'un diagnostic contient une correction documentée par archvow (par exemple la
commande `component edit ... --signature` à lancer), *Autres / Projet* propose de l'ouvrir dans le
formulaire de la commande, pré-rempli et confirmé comme toute commande. `ArchvowIssueAction` et
`issueFix()` ne reconnaissent que des formes connues ; **ils n'exécutent jamais le texte d'un message**.
