// archvow in VS Code: the same workspace as in IntelliJ. The sidebar (activity bar) holds Overview,
// Components and Commands; a component or a command opens in ONE editor tab, reused for the next one.
// archvow check in the Problems view, and the .vow language. Everything is read from the archvow CLI;
// every change is an archvow command a person confirms. The extension never writes a vow.
import * as fs from 'node:fs';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { tr } from './i18n';
import { registerVowLanguage } from './vowLanguage';
import { objects, str } from './workItems';
import { ArchvowProject } from './workspace';

const VIEW = 'archvow.workspace';

export function activate(context: vscode.ExtensionContext): void {
  registerVowLanguage(context);
  const log = vscode.window.createOutputChannel('archvow', { log: true });
  context.subscriptions.push(log);

  let project: ArchvowProject | undefined;
  const start = (): ArchvowProject | undefined => {
    if (project !== undefined) return project;
    if (!vscode.workspace.isTrusted) {
      void vscode.window.showInformationMessage('archvow runs its commands only in a trusted folder: trust this folder first.');
      return undefined;
    }
    const root = findRoot();
    if (root === null) {
      void vscode.window.showInformationMessage('Open a folder first: archvow works on the folder of the project.');
      return undefined;
    }
    project = new ArchvowProject(context, root, log);
    if (pendingView !== undefined) project.attachView(pendingView);
    pendingView = undefined;
    void vscode.commands.executeCommand('setContext', 'archvow.project', true);
    return project;
  };
  /** The sidebar, when VS Code shows it before the project could start (untrusted, no folder). */
  let pendingView: vscode.WebviewView | undefined;
  const provider: vscode.WebviewViewProvider = {
    resolveWebviewView(view: vscode.WebviewView): void {
      const p = findRoot() === null ? undefined : start();
      if (p !== undefined) {
        p.attachView(view);
        return;
      }
      pendingView = view;
      view.webview.options = { enableScripts: true };
      context.subscriptions.push(view.webview.onDidReceiveMessage((message: unknown) => {
        if (message === 'initialize') void vscode.commands.executeCommand('archvow.initialize');
      }));
      view.webview.html = placeholder(vscode.workspace.isTrusted
        ? 'Open a folder first: archvow works on the folder of the project.'
        : 'archvow runs its commands only in a trusted folder: trust this folder first.');
    },
  };
  const withProject = (run: (p: ArchvowProject) => unknown): void => {
    const p = start();
    if (p) void run(p);
  };

  const initialize = async (): Promise<void> => {
    if (!vscode.workspace.isTrusted && findRoot() !== null) {
      void vscode.window.showInformationMessage('Trust this folder before running Archvow.');
      return;
    }
    const root = findRoot();
    if (root === null) {
      const chosen = await vscode.window.showOpenDialog({
        canSelectFiles: false, canSelectFolders: true, canSelectMany: false,
        openLabel: 'Initialize Archvow here', title: 'Choose a new or existing folder for Archvow',
      });
      if (!chosen?.length) return;
      await context.globalState.update('archvow.pendingInitFolder', chosen[0].fsPath);
      await vscode.commands.executeCommand('vscode.openFolder', chosen[0], false);
      return;
    }
    withProject((p) => p.initialize());
  };

  context.subscriptions.push(
    vscode.window.registerWebviewViewProvider(VIEW, provider, { webviewOptions: { retainContextWhenHidden: true } }),
    vscode.commands.registerCommand('archvow.open', () => withProject((p) => p.show())),
    vscode.commands.registerCommand('archvow.initialize', initialize),
    vscode.commands.registerCommand('archvow.refresh', () => withProject((p) => p.refresh(true))),
    vscode.commands.registerCommand('archvow.check', () => withProject((p) => p.runCheck())),
    vscode.commands.registerCommand('archvow.openComponent', (arg?: unknown) => withProject(async (p) => {
      let id = componentId(arg);
      if (id === null) {
        const ids = objects(p.state.spec, 'containers').flatMap((c) => objects(c, 'components').map((x) => str(x, 'id')));
        id = (await vscode.window.showQuickPick(ids, { placeHolder: tr(p.state.fr, 'Which component?', 'Quel composant ?') })) ?? null;
      }
      if (id !== null) p.show(id);
    })),
    vscode.commands.registerCommand('archvow.openSpec', (arg?: unknown) => withProject((p) => { const id = componentId(arg); return id ? p.openSpec(id) : undefined; })),
    vscode.commands.registerCommand('archvow.openCode', (arg?: unknown) => withProject((p) => { const id = componentId(arg); return id ? p.openCode(id) : undefined; })),
    vscode.commands.registerCommand('archvow.copyPrompt', (arg?: unknown) => withProject((p) => { const id = componentId(arg); return id ? p.copyPrompt(id) : undefined; })),
    vscode.commands.registerCommand('archvow.showOutput', () => log.show()),
    vscode.commands.registerCommand('archvow.showFile', (uri?: vscode.Uri) => withProject((p) => {
      const editor = vscode.window.activeTextEditor;
      const target = uri ?? editor?.document.uri;
      if (target === undefined || target.scheme !== 'file') return undefined;
      const same = editor !== undefined && editor.document.uri.toString() === target.toString();
      return p.showFile(target.fsPath, same ? editor.selection.active.line + 1 : 0);
    })),
    vscode.workspace.onDidGrantWorkspaceTrust(() => { if (findRoot() !== null && (hasVow(findRoot()) || pendingView)) start(); void openPendingInit(); }),
  );
  const openPendingInit = async (): Promise<void> => {
    const target = context.globalState.get<string>('archvow.pendingInitFolder');
    const root = findRoot();
    if (!target || !root || path.resolve(target) !== path.resolve(root) || !vscode.workspace.isTrusted) return;
    await context.globalState.update('archvow.pendingInitFolder', undefined);
    const p = start();
    if (p) { await p.refresh(false); p.initialize(); }
  };
  // a project with a vow: read it at once, so the sidebar and the Problems view fill without a click
  const root = findRoot();
  void vscode.commands.executeCommand('setContext', 'archvow.project', hasVow(root));
  if (root !== null && hasVow(root) && vscode.workspace.isTrusted) void start()?.refresh(false);
  void openPendingInit();
  const created = vscode.workspace.createFileSystemWatcher('**/.archvow/spec.vow', false, true, true);
  context.subscriptions.push(created, created.onDidCreate(() => {
    void vscode.commands.executeCommand('setContext', 'archvow.project', true);
    void start()?.refresh(false);
  }));
}

export function deactivate(): void {
  // everything is disposed through context.subscriptions
}

/** The first folder of the window holding `.archvow/spec.vow`, else the first folder. */
function findRoot(): string | null {
  const folders = (vscode.workspace.workspaceFolders ?? []).filter((f) => f.uri.scheme === 'file');
  const withVow = folders.find((f) => hasVow(f.uri.fsPath));
  return (withVow ?? folders[0])?.uri.fsPath ?? null;
}

function hasVow(root: string | null): boolean {
  return root !== null && fs.existsSync(path.join(root, '.archvow', 'spec.vow'));
}

/** The component id a command was given: an id, or nothing. */
function componentId(arg: unknown): string | null {
  return typeof arg === 'string' && arg ? arg : null;
}

/** What the sidebar says while archvow cannot start. */
function placeholder(text: string): string {
  const safe = text.replace(/&/g, '&amp;').replace(/</g, '&lt;');
  return `<!DOCTYPE html><html><head><meta charset="UTF-8"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline';"></head>
<body style="font-family: var(--vscode-font-family); color: var(--vscode-foreground); padding: 8px 12px;"><p>${safe}</p>
<button id="init" style="padding:7px 12px; color:var(--vscode-button-foreground);background:var(--vscode-button-background);border:0;cursor:pointer">Initialize Archvow…</button>
<script>document.getElementById('init').addEventListener('click', () => acquireVsCodeApi().postMessage('initialize'));</script></body></html>`;
}
