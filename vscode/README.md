# ArchVow for VS Code

**Understand, review and maintain your software architecture without leaving your editor.**

ArchVow connects VS Code to the **ArchVow CLI** and gives you a visual workspace for your project's components, decisions, questions and checks. The CLI remains the source of truth; the extension makes it easier to explore the architecture and act on it.

## Explore your architecture

Open **ArchVow** from the Activity Bar to access:

- **Overview** — See the project's architecture status, pending and resolved items, and quick actions such as Check, Next, Doctor and Audit.
- **Components** — Browse components by layer and inspect their decisions, questions, safety, assumptions, rules, tasks and specifications. Follow links to related components, briefs and code.
- **Commands** — Run ArchVow commands through guided forms. Review the options, see the exact command and read its result without switching to a terminal.
- **AI skills** — Prepare prompts to draft from a brief, find missing questions or risks, implement a component or review changes. Copy them to your AI assistant, and review proposed items before keeping them.

Selecting a component, command or result opens it in **one reusable editor tab**, so your workspace stays uncluttered.

## Work alongside your code

- **`.vow` files** — Syntax highlighting, inline errors and warnings, Outline, folding, comments and navigation to referenced components, hazards and Python modules. Vow code blocks in Markdown are highlighted too.
- **Architecture checks** — Run project checks and see diagnostics in the VS Code Problems view. Python files can also be checked automatically on save.
- **Contextual navigation** — Right-click a file to find its owning ArchVow component, or jump from a component to its specification and code.
- **Automatic refresh** — The workspace updates when `.vow` files change, including changes made by CLI commands or Git.

## Changes stay under your control

ArchVow **does not change architecture files directly**. When an action changes the project, the extension shows the CLI command and asks you to **Confirm and run**. AI-suggested items are marked for review until you accept or remove them.

## Getting started

1. Install the **ArchVow CLI** and verify it with `archvow --version`.
2. Open your project folder in VS Code. If it does not contain `.archvow/spec.vow`, choose **Initialize ArchVow**.
3. Open **ArchVow** in the Activity Bar to explore the project and run commands.

The extension looks for ArchVow in your project's Python interpreter, then in `archvow.python`, then in `archvow.executable`. It can offer to install the supported CLI version, but only after your confirmation. VS Code Remote - SSH and WSL environments are supported.

**Language:** English and French (`archvow.language`). **Checks on save:** `archvow.checkOnSave`. In untrusted workspaces, `.vow` language support remains available, but commands are disabled.
