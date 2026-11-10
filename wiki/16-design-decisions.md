# 16. Choix de conception et limites

> 🇬🇧 [English version](/en/16-design-decisions) · Version française

Pourquoi le code est tel qu'il est. À lire avant de « simplifier » quoi que ce soit.

## Choix

| Choix | Pourquoi | Conséquence dans le code |
|---|---|---|
| **La CLI est la seule à écrire** | une seule source de vérité, validée et tamponnée (qui, quand) par archvow ; un plugin qui écrirait un vow aurait ses propres règles | les plugins lisent `show`, `next`, `check --json` et lancent une commande pour chaque changement ; `VowEditorNotification` et le bandeau VS Code disent quelle commande change un vow ouvert |
| **Une confirmation qui montre la commande exacte** | la personne voit ce qui va changer la spec, et peut le refaire dans un terminal | `isMutating()`, `preview()`, `ArchvowConfirmPanel` ; `--yes` n'est ajouté qu'après la confirmation, pour une suppression |
| **Pas de shell, une commande à la fois, un délai** | une réponse peut contenir `;`, `$()`, des guillemets ; deux écritures en même temps se marcheraient dessus ; une commande bloquée ne doit pas bloquer l'IDE | `ArchvowProcessCommand`, `ArchvowCli` ; `vscode/src/cli.ts` (`spawnOnce`, la file) ; stdout seul est lu comme JSON |
| **La même disposition dans les deux IDE** | une équipe a les deux ; une personne qui change d'éditeur ne doit rien réapprendre | une barre latérale qui choisit, **un seul** onglet qui montre ; les mêmes vues, les mêmes mots |
| **Les mêmes fonctions, pas le même code** | Swing et une page web n'ont rien en commun ; un pont (une page web dans IntelliJ) ajouterait une couche à maintenir dans les deux | `ArchvowPanel` en Swing ; `media/workspace.js` en JavaScript simple ; les fichiers TypeScript nomment la classe Java qu'ils suivent |
| **Des formulaires tirés du parseur d'archvow** | quarante commandes écrites à la main seraient fausses à la version suivante | `contract/cli/commands.json`, `ArchvowGrammar`, `commandForm()` ; une option nouvelle est là sans code |
| **Un contrat enregistré depuis le vrai archvow** | tester sans archvow installé, et voir dans un diff ce qu'une version change | `contract/`, `regenerate.py` ; les tests lisent le JSON enregistré |
| **Un lecteur `.vow` qui lit le format, pas une liste de mots-clés** | un mot-clé nouveau d'archvow ou d'une entreprise ne doit pas demander un plugin | `VowScanner`, `VowDocument` ; couleurs par profondeur ; le sens reste à `archvow check` |
| **Le Java est la référence du lecteur, le TypeScript la suit** | deux lecteurs écrits à part divergeraient sur les cas limites | `DumpContract` -> `contract/vow-reader/expected.json` -> `vowReader.test.ts`, au caractère près |
| **Le plugin n'installe pas archvow** | archvow est un outil Python du projet (son environnement, sa version, l'index de l'entreprise) ; un plugin qui lance `pip` choisirait à la place de l'équipe | archvow introuvable : un message qui dit ce qui a été essayé et quoi régler (`ArchvowCli.NotFound`) ; une installation guidée, confirmée, est prévue en 0.12.0 |
| **L'interpréteur de l'IDE d'abord** | archvow est installé dans l'environnement du projet, que l'IDE connaît déjà ; le chercher dans le `PATH` trouvait souvent un autre | `ArchvowInterpreter`, `readInterpreter()` ; puis le Python des réglages, puis l'exécutable ; le premier qui répond à `--version` gagne |
| **WSL par `wsl.exe`, pas par le chemin `\\wsl.localhost`** | Windows ne peut pas lancer un Python Linux par son chemin réseau | `ArchvowWsl` et les fonctions WSL de `vscode/src/cli.ts` : `wsl.exe -d <distribution> --cd <dossier> --exec ...`, chemins traduits |
| **Rien téléchargé à l'exécution** | chaîne d'approvisionnement : les projets critiques n'ont pas d'accès extérieur | pas de CDN dans la page (CSP avec nonce, `default-src 'none'`), le catalogue d'init embarqué (`ArchvowInitCatalog`), les icônes dessinées en code |
| **Installer archvow guidé, jamais automatique** (0.12.0) | le mauvais Python ou venv, un index interne ou une wheel au lieu de PyPI, une version qui doit correspondre (pas de compatibilité ascendante) : une personne décide | la commande pip affichée, puis lancée seulement après Confirmer et exécuter ; le venv du projet d'abord, jamais un `python` nu ; la configuration de pip sauf si *Installer archvow depuis* dit autre chose (`ArchvowInstall`, `vscode/src/install.ts`) |
| **Le texte d'un diagnostic n'est jamais exécuté** | un message est une donnée ; un fichier du projet pourrait y glisser une commande | `ArchvowIssueAction`, `issueFix()` ne reconnaissent que des formes documentées et ouvrent un formulaire |
| **Swing simple, tailles relatives à l'IDE** | un plugin doit ressembler à l'IDE, dans chaque thème et chaque taille de police | pas de bibliothèque d'interface ; `ArchvowHtml` en points ; un seul bouton coloré par panneau |
| **La logique sort du panneau** | le panneau demande un IntelliJ qui tourne ; une petite classe se teste en une ligne | `ArchvowSplitView`, `ArchvowItemCards`, `ArchvowCommandPrefill`, `ArchvowWsl`... et une régression chacune |
| **Des tests sans framework** | ils tournent partout, même sans les dépôts JetBrains ni JUnit | un `main()` par régression, `WorkspaceTests` ; `node:test` côté VS Code |
| **Un numéro de version à eux, un seul contrat d'archvow** (0.20.0) | archvow ne garde pas de compatibilité ; lire deux formats de JSON doublerait le code ; mais une version du plugin par version d'archvow est du gaspillage quand le JSON n'a pas changé | `contract/cli/contract.txt`, `contract/cli/version.txt`, `ArchvowVersion`, `contractWarning()` ; le plugin accepte la même MAJEURE et une MINEURE au moins la sienne, et installe l'archvow testé |
| **Un seul appel pour lire un projet** (0.21.0) | un processus par commande faisait d'un rafraîchissement 16 processus sur l'exemple FastAPI ; chaque `show <composant>` relançait `check` ; ArchvowCli lance une commande à la fois, donc une chaîne était le seul ordre sûr | `archvow workspace --json` (archvow 1.25) dans chaque plugin, lu par `readWorkspace()` / `WorkspaceAnswer` ; `--since` seulement quand un vow a changé sur le disque |
| **Les packs dans la vue d'ensemble, pas un assistant de plus** (0.22.0) | archvow 1.26 met templates, agents et règles d'entreprise dans des packs copiés dans le projet ; un assistant à sept écrans dupliquerait ce que les huit commandes `pack` font déjà, chacune avec son formulaire et son plan de fichiers | une ligne Packs (lue dans `workspace --json`, sans appel de plus) et sa liste avec Mettre à jour / Retirer / Vérifier ; un formulaire par commande `pack` ; le catalogue d'init lu dans le dossier du projet |
| **Le wiki vit avec le code** | une documentation interne qui n'est pas tenue à jour trompe | `wiki/` dans le dépôt, même commit que le code, `wiki.test.ts` |

## Ce que les plugins ne font pas, exprès

| Pas fait | Pourquoi |
|---|---|
| modifier un vow directement (un formulaire qui écrit le fichier) | la CLI est la seule à écrire |
| lancer une commande sans confirmation, même « sûre » | une seule règle simple : ce qui change la spec se confirme |
| deviner une valeur pour pré-remplir un champ | un champ pré-rempli se lit comme une réponse déjà donnée ; seul ce qui est connu est rempli |
| lancer `specify` | il pose ses questions dans un terminal ; l'onglet ouvre le premier élément ouvert à la place |
| garder une copie des règles d'archvow | elles changent à chaque version ; l'explication d'un code vient de `rules.md` du contrat |

## Limites connues

| Limite | Où on en est |
|---|---|
| IntelliJ ne souligne pas les violations de `check` dans l'éditeur Python | point 1 de `docs/analyse-intellij.md` (un `ExternalAnnotator`) ; VS Code le fait |
| une règle d'archvow est encore lue dans les plugins (danger élevé ou critique sans invariant, ARCH030) | point 3 de `docs/analyse-intellij.md` : la mettre dans le JSON d'archvow |
| `ArchvowPanel` fait environ 4 000 lignes | point 5 de `docs/analyse-intellij.md` : sortir l'état, puis une classe par vue ; en attendant, chaque nouvelle logique va dans une petite classe |
| le panneau IntelliJ et la page VS Code ne sont testés qu'à la main | point 6 de `docs/analyse-intellij.md` ; `runIde` et F5 avant chaque version |
| archvow doit être installé à part | installation guidée prévue en 0.12.0 |
| une seule version mineure d'archvow à la fois | voulu (voir plus haut) : une version des plugins par version d'archvow |
