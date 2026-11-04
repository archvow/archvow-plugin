# Generic UI helpers (IntelliJ and VS Code)

These helpers use the Archvow CLI grammar and actual project state, never a project-specific component name.

- **Command groups:** the `command_groups` in `contract/forms/forms.json` order the commands from `contract/cli/commands.json`. A command whose prefix is unknown goes under *Other commands*. The grouped list is only navigation.
- **Command reference:** generated from the bundled grammar, strictly read-only. It is deliberately not `archvow docs`, which writes `spec.md` files. The installed CLI remains authoritative.
- **Component `--uses`:** options come only from components declared in the current manifest. The selected component cannot depend on itself. Existing unknown values stay editable and are not erased by the UI. The CLI still validates dependency rules, cycles and permissions.
- **Structured column answers:** offered only when the question mentions columns/fields *and* type, nullability, requiredness or privacy. Known column names and existing recorded classifications are reused; unknown values remain explicitly *unknown*. The helper fills the existing answer field as plain text **only when the user requests it**. The usual save/proposal/confirmation flow remains unchanged. Other questions keep the free-text answer.
- **No implicit reconciliation:** overlapping questions are never merged, answered, accepted or removed automatically. Their original identifiers and history remain the CLI's responsibility.
- **Responsive sections:** VS Code remembers manual expansion of a command's advanced fields on redraw. Both IDEs retain their original command form and runtime validation.

Regression entrypoints: `node vscode/scripts/generic-ui-regression.js` and `./gradlew workspaceTest` in `intellij` (includes `GenericUiRegression`).
