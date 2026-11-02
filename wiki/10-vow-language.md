# 10. Le langage .vow

> 🇬🇧 [English version](/en/10-vow-language) · Version française

Les deux plugins savent lire les fichiers `.vow` (`.archvow/spec.vow`,
`.archvow/components/<c>/spec.vow`, `.archvow/proposals.vow`) : couleurs, erreurs à la lecture,
pliage, structure, liens. Ils lisent **le format, pas une liste de mots-clés** : n'importe quel mot-clé
est accepté à n'importe quelle profondeur, donc un mot-clé nouveau d'archvow (ou d'une entreprise)
n'exige pas de nouveau plugin. Ce que chaque mot-clé veut dire, c'est `archvow check` qui le vérifie.

## Le format, en une minute

```
vow 1 component clean_orders            <- l'en-tête : vow <version> <genre> [<nom>]
type transformation                      <- clé valeur
uses [orders_schema]                     <- une liste
decision 1                               <- une clé avec des lignes dessous : un bloc
  question "Quelles lignes garde-t-elle ?"   <- deux espaces par niveau, jamais de tabulation
  answer "montant > 0"
  by architect@example.com
config = {"retries": 3}                  <- clé = <JSON>, rare
# un commentaire
```

## Le lecteur : Java simple, écrit deux fois

| Rôle | Java (référence) `org.archvow.vow.core` | TypeScript `vscode/src/vow` |
|---|---|---|
| Couper le texte en jetons, sans jamais échouer (un caractère illisible est `BAD`) | `VowScanner`, `VowTokenKind` | `vscode/src/vow/scanner.ts` |
| Lire l'arbre des lignes et les problèmes (en-tête, indentation, tabulations, guillemets, listes) | `VowDocument` | `vscode/src/vow/document.ts` |
| Ce qu'une valeur peut être, les éléments d'une liste | `VowValues` | `vscode/src/vow/values.ts` |
| Un texte JSON (`clé = <JSON>`) | `VowJson` | `checkJson()` dans `values.ts` |
| Les mots qui nomment autre chose (`uses`, `components`, `prevents`, `module`) | `VowLinks` | `vscode/src/vow/links.ts` |

Le Java est la référence : `DumpContract` écrit ce qu'il donne (jetons, problèmes, en-tête, liens) sur
chaque vow du contrat et sur 400 vows cassés exprès, dans `contract/vow-reader/expected.json` ;
`vscode/src/test/vowReader.test.ts` exige que le TypeScript donne **exactement** la même chose. Le
TypeScript imite même le Java là où ils diffèrent (`javaTrim`, `isJavaWhitespace`), pour que les deux
éditeurs soulignent les mêmes caractères.

La coloration suit une règle unique du format : **une couleur par profondeur** de mot-clé (non
indenté, indenté une fois, deux fois, trois fois et plus), plus les identifiants (`H1`, `INV2`, `R1`),
les textes, nombres, booléens, personnes (`@alice`), dates, listes, JSON et commentaires.

## IntelliJ : le lecteur branché sur la plateforme (`org.archvow.vow`)

| Fonction | Classe | Comment |
|---|---|---|
| Type de fichier, icône | `VowFileType`, `VowIcons`, `VowLanguage` | chaque `*.vow` ; l'identifiant `vow` est aussi celui d'un bloc Markdown ` ```vow ` |
| Coloration | `VowLexer`, `VowSyntaxHighlighter`, `VowColorSettingsPage` | le lexer est `VowScanner` derrière l'API Lexer ; couleurs dans Settings > Editor > Color Scheme > Vow, et dans `colorSchemes/VowDefault.xml`, `VowDarcula.xml` |
| Arbre | `VowParser`, `VowParserDefinition`, `VowFile`, `VowLinePsi` | un élément par ligne ; les blocs sont lus par `VowDocument`, partagé avec le pliage et la structure |
| Erreurs | `VowAnnotator` | lit le fichier une fois (`VowDocument`) et souligne ce qui empêche archvow de le lire |
| Pliage, structure | `VowFoldingBuilder`, `VowStructureViewFactory`, `VowStructureViewModel`, `VowStructureElement` | chaque bloc, à toute profondeur |
| Commentaires | `VowCommenter` | Ctrl+/ ajoute ou retire `# ` |
| Ctrl+clic | `VowReference` | un composant de `uses` ouvre son `spec.vow`, un danger de `prevents` va à sa ligne, `module` ouvre le fichier Python ; trouvé par chemin, sans index |
| Bandeau | `VowEditorNotification` | au-dessus du vow : quelle commande archvow le change (on ne l'édite pas à la main) |

## VS Code : le même lecteur, autres branchements

| Fonction | Où |
|---|---|
| Langage, pliage par indentation, commentaires | `vscode/package.json` (`languages`), `vscode/language-configuration.json` (`offSide`) |
| Coloration de base, blocs Markdown ` ```vow ` | `vscode/syntaxes/vow.tmLanguage.json`, `vscode/syntaxes/vow.markdown.tmLanguage.json` |
| Coloration par profondeur | jetons sémantiques tirés du lecteur (`SemanticTokens` dans `vscode/src/vowLanguage.ts`) |
| Erreurs | une collection de diagnostics `vow`, relue à chaque modification (`registerVowLanguage`) |
| Liens, plan, bandeau | `Links` (DocumentLink), `Symbols` (le plan), `Banner` (CodeLens : la commande qui change le fichier) |

## Le vow d'un composant dans l'espace de travail

La vue **Spec** d'un composant montre son `spec.vow` coloré (et sa fiche `spec.md`), en lecture seule (`ArchvowVowViewer` ;
VS Code : `vowView()` dans la page, l'extension lit le fichier). Une couleur par profondeur, comme dans
l'éditeur.
