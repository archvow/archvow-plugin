# 14. Publier une version

> 🇬🇧 [English version](/en/14-release) · Version française

Les deux plugins ont **le même numéro**, le leur (0.11.4), pas celui d'archvow. Une version est un
commit `v<version>: <ce qui change, en une ligne>` sur `main`.

## Les numéros à changer

| Fichier | Quoi |
|---|---|
| `intellij/gradle.properties` | `pluginVersion` |
| `vscode/package.json`, `vscode/package-lock.json` | `"version"` (le lock deux fois : la racine et `packages[""]`) |
| `README.md` | la ligne `./build.sh` (les noms des fichiers construits) et la section Versions |
| `intellij/README.md` | le `.zip` construit, la liste de contrôle d'installation, la section de version |
| `vscode/README.md` | le `.vsix` construit |
| `intellij/CHANGELOG.md`, `vscode/CHANGELOG.md` | une section `## <version> - <date>` en tête |
| `wiki/home.md`, `wiki/en/home.md` | « Version décrite » et les nombres de lignes et de tests |

Un CHANGELOG dit ce qu'une personne voit changer, en gras d'abord, puis la classe ou la fonction ;
la dernière ligne nomme les tests ajoutés et ce qui a été vérifié à la main. Le CHANGELOG IntelliJ et
celui de VS Code disent la même chose quand le changement touche les deux.

## La liste avant de publier

```bash
(cd intellij && ./gradlew check buildPlugin)     # coreTest, workspaceTest, puis le .zip
python3 intellij/tools/verify_built_plugin.py    # l'identifiant et since-build du .zip construit
(cd intellij && ./gradlew runIde)                # un essai : ouvrir un exemple, un composant, une commande
(cd vscode && npm ci && npm test && npm run package)
./build.sh                                       # ou les deux d'un coup -> dist/
```

Puis, dans `runIde` et dans VS Code (F5 ou le `.vsix` installé), sur un exemple d'archvow : ouvrir
un composant (le premier élément ouvert est choisi), répondre à une décision (la confirmation montre la
commande), rétrécir la barre latérale (Composant, État, En attente restent), lancer Doctor (texte à la
taille de l'IDE).

## Livrer

`make release VERSION=0.11.4` écrit un *bundle* Git de toutes les branches et un `.zip` du dépôt. Une
version est aussi livrée en **patches** : `git format-patch` du commit précédent, que l'on applique avec
`git am`, ou avec `./apply_patch.sh <fichier.patch>` (qui fait un `git apply` du contenu, sans créer le
commit). Une livraison vérifiée applique le patch sur la version précédente et compare l'arbre obtenu
à celui du commit.

Pour une équipe, le `.zip` et le `.vsix` vont dans un dépôt de plugins interne (un `updatePlugins.xml`
JetBrains, un Open VSX hébergé) ou un partage : les plugins ne téléchargent jamais rien eux-mêmes.

## Une nouvelle version d'archvow

archvow ne garde pas de compatibilité ascendante : chaque version mineure peut changer ses commandes ou
son JSON. Les plugins suivent **une** version, celle de `contract/cli/version.txt` (`ArchvowVersion.KNOWN`),
et disent quand celle qui est installée en est une autre (page [Lancer archvow](/05-running-archvow)).

1. Régénérer le contrat depuis le nouvel archvow (page [Le contrat](/04-contract)) :
   `python3 contract/tools/regenerate.py ../archvow`.
2. Lire `git diff contract/` : c'est exactement ce qui a changé pour les plugins (une option, un champ
   du JSON, un code de règle, une question de template).
3. `(cd intellij && ./gradlew contractDump check)` et `(cd vscode && npm test)` : les formulaires
   suivent `commands.json` sans code ; un champ JSON renommé casse un test.
4. Adapter ce que le diff demande, des deux côtés, avec une ligne de CHANGELOG chacun.
5. Mettre à jour les nombres « archvow 1.16 » : `README.md`, `intellij/README.md`, `contract/README.md`,
   `intellij/gradle.properties` (le commentaire), `wiki/home.md` et sa jumelle.

## Le wiki dans la même version

Une version qui change un écran, une classe ou un réglage change les pages du wiki concernées, **en
français et en anglais, dans le même commit**. `wiki.test.ts` refuse un lien mort, une page sans
jumelle, un fichier ou une classe qui n'existe plus (page [Les tests](/13-tests)).
