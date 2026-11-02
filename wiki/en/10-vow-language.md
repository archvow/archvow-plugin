# 10. The .vow language

> 🇫🇷 [Version française](/10-vow-language) · English version

Both plugins can read `.vow` files (`.archvow/spec.vow`, `.archvow/components/<c>/spec.vow`,
`.archvow/proposals.vow`): colours, errors while reading, folding, structure, links. They read **the
format, not a list of keywords**: any keyword is accepted at any depth, so a new archvow (or company)
keyword needs no new plugin. What each keyword means, `archvow check` checks.

## The format, in one minute

```
vow 1 component clean_orders            <- the header: vow <version> <kind> [<name>]
type transformation                      <- key value
uses [orders_schema]                     <- a list
decision 1                               <- a key with lines under it: a block
  question "Which rows does it keep?"    <- two spaces per level, never a tab
  answer "amount > 0"
  by architect@example.com
config = {"retries": 3}                  <- key = <JSON>, rare
# a comment
```

## The reader: plain Java, written twice

| Role | Java (reference) `org.archvow.vow.core` | TypeScript `vscode/src/vow` |
|---|---|---|
| Cut the text into tokens, never failing (an unreadable character is `BAD`) | `VowScanner`, `VowTokenKind` | `vscode/src/vow/scanner.ts` |
| Read the tree of lines and the problems (header, indentation, tabs, quotes, lists) | `VowDocument` | `vscode/src/vow/document.ts` |
| What a value can be, a list's items | `VowValues` | `vscode/src/vow/values.ts` |
| A JSON text (`key = <JSON>`) | `VowJson` | `checkJson()` in `values.ts` |
| The words that name something else (`uses`, `components`, `prevents`, `module`) | `VowLinks` | `vscode/src/vow/links.ts` |

The Java is the reference: `DumpContract` writes what it gives (tokens, problems, header, links) on each
vow of the contract and on 400 vows broken on purpose, into `contract/vow-reader/expected.json`;
`vscode/src/test/vowReader.test.ts` requires the TypeScript to give **exactly** the same. The
TypeScript even copies the Java where they differ (`javaTrim`, `isJavaWhitespace`), so that both editors
underline the same characters.

Highlighting follows one rule of the format: **one colour per keyword depth** (not indented, indented
once, twice, three times and more), plus identifiers (`H1`, `INV2`, `R1`), texts, numbers, booleans,
people (`@alice`), dates, lists, JSON and comments.

## IntelliJ: the reader plugged into the platform (`org.archvow.vow`)

| Function | Class | How |
|---|---|---|
| File type, icon | `VowFileType`, `VowIcons`, `VowLanguage` | every `*.vow`; the id `vow` is also that of a Markdown ` ```vow ` block |
| Highlighting | `VowLexer`, `VowSyntaxHighlighter`, `VowColorSettingsPage` | the lexer is `VowScanner` behind the Lexer API; colours in Settings > Editor > Color Scheme > Vow, and in `colorSchemes/VowDefault.xml`, `VowDarcula.xml` |
| Tree | `VowParser`, `VowParserDefinition`, `VowFile`, `VowLinePsi` | one element per line; blocks are read by `VowDocument`, shared with folding and structure |
| Errors | `VowAnnotator` | reads the file once (`VowDocument`) and underlines what stops archvow from reading it |
| Folding, structure | `VowFoldingBuilder`, `VowStructureViewFactory`, `VowStructureViewModel`, `VowStructureElement` | every block, at any depth |
| Comments | `VowCommenter` | Ctrl+/ adds or removes `# ` |
| Ctrl+click | `VowReference` | a component in `uses` opens its `spec.vow`, a hazard in `prevents` goes to its line, `module` opens the Python file; found by path, no index |
| Banner | `VowEditorNotification` | above the vow: which archvow command changes it (it is not edited by hand) |

## VS Code: the same reader, other plugs

| Function | Where |
|---|---|
| Language, folding by indentation, comments | `vscode/package.json` (`languages`), `vscode/language-configuration.json` (`offSide`) |
| Base highlighting, Markdown ` ```vow ` blocks | `vscode/syntaxes/vow.tmLanguage.json`, `vscode/syntaxes/vow.markdown.tmLanguage.json` |
| Highlighting by depth | semantic tokens drawn from the reader (`SemanticTokens` in `vscode/src/vowLanguage.ts`) |
| Errors | a `vow` diagnostic collection, read again on every change (`registerVowLanguage`) |
| Links, outline, banner | `Links` (DocumentLink), `Symbols` (the outline), `Banner` (CodeLens: the command that changes the file) |

## A component's vow in the workspace

A component's **Spec** view shows its `spec.vow` highlighted (and its card `spec.md`), read-only (`ArchvowVowViewer`;
VS Code: `vowView()` in the page, the extension reads the file). One colour per depth, as in the editor.
