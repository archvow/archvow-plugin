# Archvow UX v4 — Overview, hover and responsive controls

Applies to **VS Code** (`vscode/media/workspace.js`, `workspace.css`) and **IntelliJ** (`ArchvowPanel`, `ArchvowOverflowBar`). The accepted UX v3 navigation, Components statuses and component detail are preserved.

## Overview

- Project status: amber *pending* and green *resolved* outlined capsules. In IntelliJ, these are painted as round capsules rather than native rectangular buttons. Clicking a count still opens the corresponding work.
- The Overview has two always-visible summary rows beneath the project toolbar:
  - **Architecture**: declared component count, pending and resolved work. Opens Components for detail.
  - **Checks**: the actual `check --json` error/warning totals. Opens the diagnostics detail.
- Remove free-floating error/warning chips outside the Checks row. No full component inventory in Overview.
- The project toolbar is icon-based and contains Next, Check (`check --complete --json`), Doctor, Audit, Project vow and **Briefs**. Each icon has an accessible command name and a hover tooltip. The single global refresh remains in the header.
- Briefs opens the briefs command group in VS Code and the `brief show` form in IntelliJ.

## Responsive toolbars

- **VS Code:** CSS container queries account for the available Overview width (not only viewport width). Icons that no longer fit disappear from the toolbar and their *same actions* appear in the More dropdown. More always contains Budget / Next, Diff, Review. No wrapped action rows.
- **IntelliJ:** `ArchvowOverflowBar` measures native preferred widths. Hidden actions are re-exposed in its More menu along with Budget / Next, Diff and Review. Only one More control exists.
- The VS Code Webview explicitly highlights icons, tabs, filters and More entries on hover and `:focus-visible`. Buttons keep native title tooltips and accessible labels. Summary rows are clickable with hover feedback.

## Validation

- `node --check vscode/media/workspace.js`
- `node --test vscode/out/test/layout.test.js` — existing layout checks (12 passed in this environment)
- Full available precompiled VS Code tests: 123 passed, 19 skipped, one test process could not run because `vscode-oniguruma` is not installed. New source-level regression assertions are included for the next full npm build.
- Chromium/Playwright smoke run at 1200, 430, 360, 290, 245, 200 pixels: no toolbar clipping, hidden actions appear in More, and button background changes on hover.
- Java syntax parsing of the two touched Swing classes, and a headless Swing smoke test of `ArchvowOverflowBar` in wide/narrow modes.
- Full IntelliJ build unavailable without Gradle distribution and dependencies; the build on macOS is `./build.sh intellij` after network prerequisites are available.
- Full VS Code TypeScript compilation requires npm type dependencies; use `./build.sh vscode` on a connected development machine.
