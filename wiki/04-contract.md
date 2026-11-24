# 4. Le contrat

> 🇬🇧 [English version](/en/04-contract) · Version française

`contract/` est ce que les deux plugins et la CLI archvow ont en commun. Les deux plugins sont
construits à partir de ces fichiers et testés contre eux : **un changement ici est un changement que
les plugins doivent suivre**. Il est enregistré depuis une vraie CLI archvow, jamais écrit à la main.

## Ce qu'il contient

| Dossier ou fichier | Quoi | Écrit par | Lu par |
|---|---|---|---|
| `contract/cli/version.txt` | `archvow --version` (aujourd'hui `archvow 2.0.1`) | `contract/tools/regenerate.py` | `ArchvowVersion.KNOWN`, `knownVersion()` : l'archvow avec lequel le plugin est testé et qu'il installe |
| `contract/archvow-range.txt` (1.0.0) | les versions d'archvow avec lesquelles les plugins fonctionnent (`>=3.0,<4`) | à la main, avec une version des plugins | `ArchvowCompat.RANGE_OF_PLUGIN`, `compat.ts` ; `release.test.ts` vérifie que chaque endroit le dit |
| `contract/compat/expected.json` (1.0.0) | ce qu'un plugin dit de l'archvow installé, cas par cas (EN, FR) | `UPDATE_COMPAT=1 npm test` | `CompatRegression` |
| `contract/cli/contract.txt` (0.20.0) | le numéro de contrat de `archvow version --json` (MAJEURE.MINEURE, aujourd'hui `3.0`) | `regenerate.py` | `ArchvowVersion.CONTRACT`, `compatibility()` |
| `contract/cli/errors/` (0.20.0) | trois commandes qui échouent, telles que les plugins les reçoivent (`ARCHVOW_LOG=info`) : `no-spec`, `refused` (l'enveloppe d'erreur sur stdout, le message et les lignes de journal d'archvow sur stderr), `usage` (un refus d'argparse : pas d'enveloppe) | `regenerate.py` (`errors()`) | `cli.test.ts`, `OutputRegression` |
| `contract/cli/commands.json` | chaque commande et ses paramètres, tirés du parseur argparse d'archvow | `contract/tools/export_grammar.py` | `ArchvowGrammar`, `vscode/src/grammar.ts` : les formulaires de Commands |
| `contract/forms/forms.json` | comment se lit chaque formulaire (0.14.0, design A) : les libellés en mots simples, les champs essentiels et leur ordre, le verbe du bouton, ce que va faire archvow - le fichier des plugins, pas d'archvow | à la main | `ArchvowForms`, `commandForm()` de la page ; `FormsRegression`, `grammar.test.ts` vérifient ses noms face à `commands.json` |
| `contract/forms/forms.json` (0.15.0) | aussi : `files` (ce que chaque commande écrit, le bloc Fichiers du formulaire), `extra_parameters` (les champs propres aux plugins : `output`, Enregistrer dans un fichier, sur `changes` et `diff`), `smart_answers.parameters` (le tableau des paramètres `nom: type = défaut`) | à la main | `filePlan()`, `ArchvowFilePlan`, `withPluginParameters()`, `ArchvowGrammar`, `ArchvowColumnAnswerPanel` |
| `contract/forms/forms.json` (0.16.0) | aussi : `ai_skills` - les cinq skills IA (nom, ce que chacun fait, lit, peut ajouter, ce que la personne décide, la demande que reçoit chaque outil) et les mots des étiquettes d'origine | à la main | `vscode/src/aiSkills.ts`, `skillForm()` et `aiMenu()` de la page, `ArchvowAiSkills`, `ArchvowSkillForm` |
| `contract/forms/ai-skills.scenarios.json`, `ai-skills.expected.json` | des vues de composant (champs de `show --json` d'archvow 1.20) et la vue IA que les deux plugins en calculent : origines, nombres à relire, suggestion, skills bloqués | scénarios à la main ; attendu : `UPDATE_AI_SKILLS=1 npm test` | `aiSkills.test.ts`, `AiSkillsRegression` : IntelliJ doit donner exactement la même chose |
| `contract/forms/icons.json` | les icônes des arbres Composants et Commandes (0.15.0) : un dessin par type de composant, groupe de commandes et verbe, sur une grille de 14 x 14 | à la main | `icon()` de la page (en SVG), `ArchvowTreeIcons` (Java2D) ; `filePlan.test.ts`, `FilePlanRegression` vérifient qu'aucun type, groupe ou commande n'en manque |
| `contract/forms/file-plans.expected.json` | ce que `filePlan()` donne sur 21 exemples en anglais et en français, les couches où ajouter un composant, les lignes d'Enregistrer dans un fichier | `UPDATE_FILE_PLANS=1 npm test` | `FilePlanRegression` : `ArchvowFilePlan` doit donner exactement la même chose |
| `contract/forms/examples.expected.json` | l'état de chaque exemple (réussi, en échec, sans test) tel que la page le dit, en anglais et en français | `UPDATE_EXAMPLES=1 npm test` | `ExamplesAndSearchRegression` : `ArchvowExamples.state` doit dire la même chose |
| `contract/cli/python-app/examples/` (`passing.json`, `failing.json`, `no-test.json`), `contract/cli/<exemple>/search/<nom>.json` | `show invoices_cli --json` avec un exemple réussi, en échec, sans test ; `show <nom> --json` sur des noms trouvés et un nom introuvable (`mark_payd`) | `regenerate.py` (`examples()`, `SEARCHES`) | `examples.test.ts`, `ExamplesAndSearchRegression` |
| `contract/cli/python-app/pasted-error.json` | `show "<une trace>" --json` (archvow 2.7.0, contrat 2.5) : une erreur collée qui nomme `mark_paid.py:28` | `regenerate.py` (`PASTED_ERRORS`) | `pasted-error-regression.js`, `PastedErrorRegression` |
| `contract/cli/catalog.json` | `archvow catalog --json` hors projet : les templates et leurs questions | `regenerate.py` | `ArchvowInitCatalog`, l'assistant `init` quand la CLI ne répond pas ; `componentTypes()` |
| `contract/cli/rules.md`, `rules.fr.md` | la signification de chaque code de contrôle (`docs/rules*.md` d'archvow) | `regenerate.py` (copie) | `ArchvowRuleDocs`, `ruleExplanation()` : l'explication sous un diagnostic |
| `contract/cli/<exemple>/show.json`, `next.json`, `check.json`, `show/<composant>.json` | ce que la CLI affiche sur les trois exemples d'archvow | `regenerate.py` | les tests VS Code (`vscode/src/test/workspace.test.ts`, `layout.test.ts`) et les montages de test des deux plugins |
| `contract/cli/briefs/` (`show.json`, `next.json`, `check.json`, `show/<composant>.json`, `refused.txt`) | python-app avec le `specs/` de Spec Kit déclaré comme dossier de briefs (`-a briefs=briefs,specs`) : `mark_paid` tiré de `specs/001-orders/plan.md#paying-an-invoice` (`source`), ARCH039 une fois cette section changée, et le refus de `--from` par archvow avant que le dossier soit déclaré | `regenerate.py` (`briefs()`) | `vscode/src/test/briefs.test.ts`, `BriefsRegression` |
| `contract/vows/<exemple>/.archvow/**/*.vow` | les vows écrits par archvow sur ses exemples | `regenerate.py` | les deux lecteurs `.vow` (`CoreTests`, `vscode/src/test/vowReader.test.ts`) |
| `contract/archvow/` | l'archvow avec lequel les plugins sont testés : le wheel de `contract/cli/version.txt`, les trois exemples d'archvow avec leur code, `docs/rules*.md` | `contract/tools/take_archvow.sh` | `regenerate.py` (job CI `contract`), `vscode/src/test/live.test.ts` |
| `contract/cli/messages.json` | chaque message de vérification que la suite de tests d'archvow lui fait afficher (archvow 2.0) | `contract/tools/harvest_messages.py` (lance les tests d'archvow avec un crochet) | `vscode/src/test/guidance.test.ts`, `GuidanceRegression` |
| `contract/overview/expected.json` | ce que les cartes d'Overview lisent du `workspace.json` de chaque exemple (0.25.0) : premiers pas, premier constat, réponses suggérées | `UPDATE_OVERVIEW=1 npm test` | `OverviewRegression` : `ArchvowOverview` doit lire exactement la même chose |
| `contract/guidance/expected.json` | ce que dit `guidance.ts` sur chacun de ces messages, en anglais et en français | `UPDATE_GUIDANCE=1 npm test` | `GuidanceRegression` : `ArchvowGuidance` doit dire exactement la même chose |
| `contract/cli/kinds.json` | les sortes de chaque type de composant (`component add -k`), pour un archvow dont le catalogue ne les liste pas (1.17 ; archvow 1.18 les donne dans `catalog --json`) | `contract/tools/export_kinds.py` (par `regenerate.py`) | `choices.ts`, `ArchvowChoices` : la liste de `--kind` |
| `contract/choices/expected.json` | ce que propose chaque formulaire de commande (`choiceBook()`) sur les trois exemples, en anglais et en français, et la base de comparaison sur quelques dépôts | `UPDATE_CHOICES=1 npm test` | `ChoicesRegression` : `ArchvowChoices` doit proposer exactement la même chose |
| `contract/vow-reader/expected.json` | ce que donne le lecteur Java (jetons, problèmes, en-tête, liens) sur chaque vow et sur 400 vows cassés exprès | `./gradlew contractDump` (`DumpContract`) | le lecteur TypeScript doit donner exactement la même chose |

## Comment les plugins l'embarquent

Le contrat n'est pas lu dans `contract/` à l'exécution : il est copié dans chaque plugin à la
construction.

- **IntelliJ** : la tâche `processResources` de `intellij/build.gradle.kts` copie `commands.json`,
  `catalog.json`, `kinds.json`, `rules.md`, `rules.fr.md` et `version.txt` dans les ressources, sous `archvow/`.
- **VS Code** : `vscode/scripts/sync-contract.js` copie les mêmes fichiers dans
  `vscode/resources/archvow/`, à chaque `npm run compile`, `npm test` et `npm run package`.

## Après une nouvelle version d'archvow

```bash
contract/tools/take_archvow.sh ../archvow v1.19.0 ../archvow/dist/archvow-1.19.0-py3-none-any.whl
python3 -m venv /tmp/av && /tmp/av/bin/pip install contract/archvow/archvow-*.whl
python3 contract/tools/regenerate.py contract/archvow --python /tmp/av/bin/python
(cd intellij && ./gradlew contractDump check)
(cd vscode && npm test)
git diff contract/          # à relire : c'est ce qui a changé pour les plugins
```

`regenerate.py` copie chaque exemple d'archvow dans un dossier temporaire (rien n'est écrit dans
l'arbre d'archvow), y lance `show`, `next`, `check`, `show <composant>` et `workspace`, et écrit le JSON
indenté (le condensé de `workspace.json`, fait des heures des fichiers, est écrit `<digest>`).
Une commande qui sort en erreur (code autre que 0 ou 1) ou n'écrit rien arrête tout : un contrat à
moitié régénéré serait pire que l'ancien.

Ce qu'il faut ensuite regarder dans le diff :

| Changement | Ce qu'il faut faire |
|---|---|
| une commande ou une option nouvelle dans `commands.json` | rien : les formulaires sont construits à partir de la grammaire |
| un champ nouveau dans `show --json` | l'afficher là où il a un sens (en-tête du composant, carte, panneau) dans les deux plugins |
| un champ renommé ou retiré | corriger les deux lecteurs (`ArchvowPanel`, `workspace.js`), les tests le signalent |
| `version.txt` | l'archvow avec lequel les plugins sont testés et qu'ils installent : changer le texte des README (`release.test.ts` dit où) |
| `contract.txt` | MINEURE qui monte : archvow a ajouté ce que le plugin peut utiliser ; MAJEURE qui monte : ce qu'il lit a été retiré ou a changé de sens - les deux lecteurs doivent suivre, et les plugins refusent alors l'ancien archvow |
| `expected.json` | régénéré par `contractDump` : si `npm test` échoue, le lecteur TypeScript ne suit plus le Java |

## La version d'archvow et le contrat (0.20.0)

archvow 1.23 dit son contrat : `archvow version --json` donne `{"contract": "3.0", "archvow": "3.0.0",
...}`, et chaque objet `--json` commence par `"contract"`. Depuis 1.0.0 les plugins ont leur propre numérotation
et disent l'intervalle des versions d'archvow avec lesquelles ils fonctionnent : `contract/archvow-range.txt`
(`>=3.0,<4`), une seule valeur, dite dans le README, les deux textes des Marketplaces et l'en-tête de la fenêtre
(`archvow 3.0.0 ✓ · ce plugin fonctionne avec archvow >=3.0,<4`). `compatibility()` (`compat.ts`) et
`ArchvowCompat.of` jugent l'archvow installé face à l'intervalle et à `contract.txt` (même MAJEURE et une MINEURE
au moins celle du plugin), avec la version que fixe le projet (`archvow==X` dans son requirements.txt ou son
pyproject.toml, `projectPin()`) : dans l'intervalle, rien ; plus ancien, Installer archvow 3.0.0 - ou la version
fixée, si elle est dans l'intervalle ; une version fixée plus ancienne, comment les architectes du projet le
mettent à jour (`pip install archvow==3.0.0`, `archvow init <package> --force`), et rien n'est installé
par-dessus ; plus récent, « mets le plugin à jour » ; absent, la commande d'installation exacte. Les deux disent
la même chose sur chaque cas de `contract/compat/expected.json` (`UPDATE_COMPAT=1 npm test`,
`CompatRegression`). Une version d'archvow dans l'intervalle ne demande donc pas de nouvelle version du plugin,
et archvow ne garde pas de compatibilité ascendante d'une MAJEURE à l'autre.

Le contrat 1.1 (archvow 1.25, plugin 0.21.0) ajoute `archvow workspace --json` : ce que les plugins
chargent, en un seul processus (`readWorkspace()` dans `model.ts`, `WorkspaceAnswer` dans IntelliJ, tous
deux testés sur `contract/cli/<exemple>/workspace.json`). Chaque partie a la forme de sa propre commande
(`project` est `show --json`, `components.<id>` est `show <id> --json`, puis `next`, `check`, `briefs` et
`catalog`) ; seul le haut de la réponse dit `"contract"`. `--since <condensé>` répond `{"unchanged": true}`
tant qu'aucun fichier du projet n'a changé (chemins, tailles et heures de modification).

Le contrat 3.0 (archvow 3.0, plugin 1.0.0) remplace les packs par les templates : `workspace --json` a
`templates` (la chaîne des copies du projet, bases d'abord - nom, version, `extends`, source, `state` `ok` ou
`changed`, `why` - puis `project`, ses ajouts propres), `catalog --json` a `templates` (des noms simples,
`default`, `chain`) et `project_templates` ; les commandes `template new/rule/question/agent/check/update`
remplacent celles des packs ; chaque question d'un composant a un `id`. Les plugins lisent `templates` (la ligne
Templates, page 7) et ont un formulaire par commande `template`.

Le contrat 1.2 (archvow 1.26, plugin 0.22.0) ajoutait les packs : `workspace --json` a `packs` (chaque pack du
projet, ce qu'il apporte, sa source, `state` `ok` ou `changed`, `why`), `catalog --json` a `packs` et `agents`, et
les modèles sont nommés `<pack>/<modèle>` (`archvow/python-app`) ; les commandes `pack new/template/agent/rule/
add/update/remove/check` remplacent `template new`. Les plugins lisent `packs` (la ligne Packs, page 7), ont un
formulaire par commande `pack` et ne passent plus `ARCHVOW_TEMPLATES`.

Le contrat 2.0 (archvow 2.0, plugin 0.24.0) range la spec par fonctionnalité, un nouveau MAJEUR : les
engagements sont `.archvow/project.vow` et `.archvow/features/<f>/{<f>.vow, components/<id>.vow, proposals.vow}`
(`layout.ts`, `ArchvowLayout`), rien de généré n'est commité (pas de `spec.md` : la vue Spec lance `archvow prompt`,
la référence des contrôles lance `archvow doc`), `show --json` liste les `features` et chaque composant sa `feature`,
`workspace --json` ajoute `features` (chacune comme `show <fonctionnalité> --json` : ses composants avec leur statut,
fichier et engagement, `uses` et `used_by` par fonctionnalité), et les commandes `feature add/rename/remove`,
`component move`, `doc`, `prompt --feature`, `next --feature` sont nouvelles. Les plans de fichiers nomment `{home}`
(la fonctionnalité d'un composant) et `{feature}` (celle que nomme le formulaire).

Le contrat 1.3 (archvow 1.27, plugin 0.23.0) ajoute les régimes de protection : `catalog --json` (et le catalogue
de `workspace --json`) a `regimes` (nom, préfixe, lettre, titre, loi, les codes et le registre de chacun), un pack
dit les régimes qu'il contient, `column set` prend `--tag`, et les codes des données personnelles sont PRIV001-006
(GDPR001, 004-007, 009 avant) à côté des codes propres à chaque régime (GDPR002, USP001, GLBA003...). Rien dans les
plugins ne nomme un régime : les questions de chacun (P1, U1, G1...) sont des questions de composant comme les
autres, les choix de `rules` viennent du catalogue, et les codes d'un régime s'expliquent par les pages de règles
d'archvow.
