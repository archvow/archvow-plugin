# 12. Languages and look

> 🇫🇷 [Version française](/12-languages-and-look) · English version

## English and French

Both plugins speak English or French, by a setting: the IDE's (the default), `EN` or `FR`. The code is
in English; every text shown goes through a small function that takes both versions.

| | IntelliJ | VS Code |
|---|---|---|
| the choice | `ArchvowI18n.modes()`: `IDE`, `EN`, `FR`; stored in `ArchvowSettings` (`languageMode`) | `archvow.language`: `auto`, `en`, `fr` |
| a text | `ArchvowI18n.text(project, "Save answer", "Enregistrer la réponse")` | `t('Save answer', 'Enregistrer la réponse')` in the page, `tr(fr, ...)` in the extension |
| where to change it | at the bottom of Overview, or Settings > Tools > Archvow | at the bottom of Overview, or the settings |

Changing the language redraws everything open, without closing it (0.13.1): IntelliJ, the tool window
tells the editor tab (`languageChanged()`; an open form keeps what is typed); VS Code also recomputes the
Pending lists' labels (`recompute()`) before sending the state to both pages.

What comes from archvow (its messages, its templates' questions) is shown as it is: the project's
language is the one archvow gave it at `init`. `.vow` keywords and codes (ARCH012) are never translated.

## Colours

- archvow's green, **#1A876B**: the logo (`archvow-logo.png`, `vscode/media/archvow.png`, the SVG icons
  with the same drawing), the chosen view, resolved items.
- Orange for what is waiting ("● Open", a pending counter), green for what is done, red for an error:
  `ArchvowStatusPalette`, the `.tone-*` rules of `vscode/media/workspace.css`.
- VS Code takes all its other colours from the theme (`var(--vscode-...)`): light, dark and high
  contrast with no extra code. IntelliJ follows the theme through `UIManager` and picks between two
  shades depending on whether the theme is dark (`ArchvowMotion.dark()`).

## Text sizes (IntelliJ)

Swing reads a CSS size in `px` as 1.3 points: `font-size:12px` drew text at 15.6 points, and an `<h3>`
larger still (the `doctor` result looked huge). Since 0.11.4, **all HTML shown takes its size from the
IDE's font**, in points: `ArchvowHtml.body` (the body at a label's size), `ArchvowHtml.heading` (a
slightly larger title, without `<h1>`-`<h6>`), `ArchvowHtml.size(factor)`. `TextSizeRegression` refuses
a `px` or an `<h1>`-`<h6>` heading in what the plugin shows.

## Narrow screens

A tool window or a sidebar is often narrow. The rules:

| Screen | What happens when it is narrow | Where |
|---|---|---|
| the tool window's header | the project's name is cut, never under the buttons; the language is at the bottom of Overview | `buildHeader` (the name in the centre of a `BorderLayout`) |
| the branch advice | over several lines | `ArchvowWrapLabel` |
| "Project status" and its counters | the counters go under the title | `buildOverviewStatusStrip` |
| the action bar | what does not fit goes into More ▾ | `ArchvowOverflowBar` |
| a component's views | what does not fit goes into More ▾ | `ArchvowCompactViews` |
| the architecture table | Component, State, Pending stay; the other columns go | `ArchvowArchitectureView.columnWidths`, `@media` rules |
| a component | the answer panel goes under the cards | `ArchvowSplitView.SIDE_BY_SIDE`, `.split` |
| the buttons under a field | the secondary actions go onto a second line | `ArchvowSplitView.actions` |

## Buttons and icons

- IntelliJ keeps its theme's buttons; only a panel's main button (Save answer, Declare hazard,
  Clarify) is painted in the accent colour (`ArchvowSplitView.primary`), to read the same in every
  theme.
- The icons are drawn in code, with no file or icon font: `ArchvowUiIcons` (IntelliJ), the `REFRESH`
  constant (an SVG) in the VS Code page. Reload is the same turning circle in both.
- Hover is discreet (`ArchvowMotion`): never more visible than the selection.

## Dialogs

A text you want to read in full or copy (Test Archvow, what was tried when archvow is not found, the
Python field's help) opens in `ArchvowTextDialog`: a box sized to its text (640 × 320 at most, then it
scrolls), the text selectable, **Copy command** (the exact line, quoted where a terminal needs it),
Copy, Close. A short message stays a plain message.
