# 4. Le contrat

> 🇬🇧 [English version](/en/04-contract) · Version française

`contract/` est ce que les deux plugins et la CLI archvow ont en commun. Les deux plugins sont
construits à partir de ces fichiers et testés contre eux : **un changement ici est un changement que
les plugins doivent suivre**. Il est enregistré depuis une vraie CLI archvow, jamais écrit à la main.

## Ce qu'il contient

| Dossier ou fichier | Quoi | Écrit par | Lu par |
|---|---|---|---|
| `contract/cli/version.txt` | `archvow --version` (aujourd'hui `archvow 1.17.0`) | `contract/tools/regenerate.py` | `ArchvowVersion.KNOWN`, `knownVersion()` : la version que le plugin connaît |
| `contract/cli/commands.json` | chaque commande et ses paramètres, tirés du parseur argparse d'archvow | `contract/tools/export_grammar.py` | `ArchvowGrammar`, `vscode/src/grammar.ts` : les formulaires de Commands |
| `contract/cli/catalog.json` | `archvow catalog --json` hors projet : les templates et leurs questions | `regenerate.py` | `ArchvowInitCatalog`, l'assistant `init` quand la CLI ne répond pas ; `componentTypes()` |
| `contract/cli/rules.md`, `rules.fr.md` | la signification de chaque code de contrôle (`docs/rules*.md` d'archvow) | `regenerate.py` (copie) | `ArchvowRuleDocs`, `ruleExplanation()` : l'explication sous un diagnostic |
| `contract/cli/<exemple>/show.json`, `next.json`, `check.json`, `show/<composant>.json` | ce que la CLI affiche sur les trois exemples d'archvow | `regenerate.py` | les tests VS Code (`vscode/src/test/workspace.test.ts`, `layout.test.ts`) et les montages de test des deux plugins |
| `contract/cli/briefs/` (`show.json`, `next.json`, `check.json`, `show/<composant>.json`, `refused.txt`) | python-app avec le `specs/` de Spec Kit déclaré comme dossier de briefs (`-a briefs=briefs,specs`) : `mark_paid` tiré de `specs/001-orders/plan.md#paying-an-invoice` (`source`), ARCH039 une fois cette section changée, et le refus de `--from` par archvow avant que le dossier soit déclaré | `regenerate.py` (`briefs()`) | `vscode/src/test/briefs.test.ts`, `BriefsRegression` |
| `contract/vows/<exemple>/.archvow/**/*.vow` | les vows écrits par archvow sur ses exemples | `regenerate.py` | les deux lecteurs `.vow` (`CoreTests`, `vscode/src/test/vowReader.test.ts`) |
| `contract/archvow/` | l'archvow avec lequel les plugins sont testés : le wheel de `contract/cli/version.txt`, les trois exemples d'archvow avec leur code, `docs/rules*.md` | `contract/tools/take_archvow.sh` | `regenerate.py` (job CI `contract`), `vscode/src/test/live.test.ts` |
| `contract/cli/messages.json` | chaque message de vérification que la suite de tests d'archvow lui fait afficher (archvow 1.17) : 491 messages, 70 codes | `contract/tools/harvest_messages.py` (lance les tests d'archvow avec un crochet) | `vscode/src/test/guidance.test.ts`, `GuidanceRegression` |
| `contract/guidance/expected.json` | ce que dit `guidance.ts` sur chacun de ces messages, en anglais et en français | `UPDATE_GUIDANCE=1 npm test` | `GuidanceRegression` : `ArchvowGuidance` doit dire exactement la même chose |
| `contract/vow-reader/expected.json` | ce que donne le lecteur Java (jetons, problèmes, en-tête, liens) sur chaque vow et sur 400 vows cassés exprès | `./gradlew contractDump` (`DumpContract`) | le lecteur TypeScript doit donner exactement la même chose |

## Comment les plugins l'embarquent

Le contrat n'est pas lu dans `contract/` à l'exécution : il est copié dans chaque plugin à la
construction.

- **IntelliJ** : la tâche `processResources` de `intellij/build.gradle.kts` copie `commands.json`,
  `catalog.json`, `rules.md`, `rules.fr.md` et `version.txt` dans les ressources, sous `archvow/`.
- **VS Code** : `vscode/scripts/sync-contract.js` copie les mêmes fichiers dans
  `vscode/resources/archvow/`, à chaque `npm run compile`, `npm test` et `npm run package`.

## Après une nouvelle version d'archvow

```bash
contract/tools/take_archvow.sh ../archvow v1.18.0 ../archvow/dist/archvow-1.18.0-py3-none-any.whl
python3 -m venv /tmp/av && /tmp/av/bin/pip install contract/archvow/archvow-*.whl
python3 contract/tools/regenerate.py contract/archvow --python /tmp/av/bin/python
(cd intellij && ./gradlew contractDump check)
(cd vscode && npm test)
git diff contract/          # à relire : c'est ce qui a changé pour les plugins
```

`regenerate.py` copie chaque exemple d'archvow dans un dossier temporaire (rien n'est écrit dans
l'arbre d'archvow), y lance `show`, `next`, `check` et `show <composant>`, et écrit le JSON indenté.
Une commande qui sort en erreur (code autre que 0 ou 1) ou n'écrit rien arrête tout : un contrat à
moitié régénéré serait pire que l'ancien.

Ce qu'il faut ensuite regarder dans le diff :

| Changement | Ce qu'il faut faire |
|---|---|
| une commande ou une option nouvelle dans `commands.json` | rien : les formulaires sont construits à partir de la grammaire |
| un champ nouveau dans `show --json` | l'afficher là où il a un sens (en-tête du composant, carte, panneau) dans les deux plugins |
| un champ renommé ou retiré | corriger les deux lecteurs (`ArchvowPanel`, `workspace.js`), les tests le signalent |
| `version.txt` | les deux plugins diront « autre version » pour l'ancienne CLI : changer le texte des README (version de la CLI lancée) |
| `expected.json` | régénéré par `contractDump` : si `npm test` échoue, le lecteur TypeScript ne suit plus le Java |

## La version d'archvow

`ArchvowVersion.warning` et `versionWarning()` comparent le `--version` de la CLI installée avec
`version.txt` : même majeure.mineure, rien ; une autre, un avertissement qui dit les deux versions.
archvow ne garde pas de compatibilité ascendante : une autre mineure peut afficher ce que le plugin lit
mal, et il vaut mieux le dire que mal afficher.
