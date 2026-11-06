# Archvow IDE UX redesign v3 — retained proposal

This source update implements the approved November 2026 design in **both** IDE integrations:

- **Overview** is a project snapshot. It has a single Webview-header reload (with duplicate VS Code view-title actions removed), a compact header search with a separate filter button, square reload/logs controls, passive pending/resolved pills, and an icon-only project action toolbar with tooltips and separators. The one **Check** runs `check --complete --json`. **Next**, **Doctor**, **Audit**, **Project vow**, and **More** (Budget/Next, Diff, Review) keep their existing command targets. The architecture section displays aggregate counts and links to **Components** rather than repeating the component table. Brief-folder metadata, duplicate Check/Reload, command-reference shortcut, and base-branch selector are removed from Overview. The current Git branch warning remains where applicable.
- **Components** keeps All/Pending/Resolved filters, Add component, the layer `+` actions and per-component numeric pending counters. A **separate lifecycle-state label** (e.g., planned, verified) appears on each tree row. The detail editor retains the approved **AI spec**, **Add**, **Edit spec**, and **Open code** actions and its detailed navigation; a lifecycle-state badge is also shown next to the component heading, separate from its open-item count.
- **Commands** lists **Project & checks** first. **AI skills** is a separate **fourth top-level tab**, not a Commands group, in both IDEs.
- The contract's command-group ordering is shared by VS Code and IntelliJ.

## Verify on a development workstation

```sh
./build.sh                 # all checks, VSIX and IntelliJ distribution (requires internet/dependency caches)
./build.sh vscode          # VS Code only
./build.sh intellij        # JetBrains only
node vscode/scripts/checks-single-detail-regression.js
node vscode/scripts/generic-ui-regression.js
```

### Packaging note

These deliverables are **source updates**, not prebuilt installable extensions. Offline syntax and targeted headless Webview regression checks passed in the delivery environment. The full VS Code TypeScript build requires the npm dependencies, including `@types/node` and `@types/vscode`; the IntelliJ build needs Gradle and its JetBrains dependencies. They were unavailable from the local dependency cache and external downloads were not accessible, so the full IDE builds remain to be verified on a configured development workstation.

The feature branch `feature/ux-redesign-v3` is included in the supplied Git bundle. A unified patch and source ZIP are also provided for environments in which a Git bundle is inconvenient.
