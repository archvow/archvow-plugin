// The archvow workspace in VS Code, the same functions as the IntelliJ workspace, in two places:
// - the sidebar (a webview view): Overview, Components (the tree) and Commands (the list), to choose;
// - ONE editor tab: the chosen component, the chosen command's form, or a command's result. A click on
//   another component shows it in the same tab, never a second one.
// This side runs archvow and holds what it said; the page (media/workspace.js, the same script in both
// places) shows it and asks. Every command the page asks for is checked against archvow's own grammar and
// built here; a change runs only after the person confirms it in the page.
import { execFile, spawn } from 'node:child_process';
import * as crypto from 'node:crypto';
import * as fs from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { ArchvowCli, CliResult, CliSettings } from './cli';
import { Briefs, locate, readBriefs } from './briefs';
import { bestBase, Book, choiceBook, missingRef, refNames } from './choices';
import { renderResult } from './formatter';
import { alerts, Command, findCommand, inline, isMutating, itemId, needsFullCard, planForm, prefill, preview, promptCommand } from './grammar';
import { isFrench, tr } from './i18n';
import { installCommand, installPython, installSource, InstallOffer, InstallReason, offer } from './install';
import { knownVersion, parseVersion, problemsOf, versionWarning } from './model';
import { moduleFiles } from './vow/links';
import { architectureRows, buildWork, Json, objects, str, Work } from './workItems';
import { componentTypes, derive, Derived, readiness, Readiness, ruleExplanation } from './projectState';

const QUIET_MS = 800;

/** What archvow said about the project, and what the extension made of it. */
export interface State {
  fr: boolean;
  languageMode: string;
  project: { name: string; root: string };
  readiness: Readiness;
  welcomeError: string;
  branch: string;
  loading: string;
  spec: Json | null;
  check: Json | null;
  checkFailure: string;
  next: Json[] | null;
  snapshots: Record<string, Json>;
  work: Work;
  rows: ReturnType<typeof architectureRows>;
  vows: Record<string, string>;
  knownVersion: string;
  /** Install archvow (0.12.0): shown when it cannot start or is another major.minor. */
  install: InstallOffer;
  /** The briefs folders (init answer `briefs`) and their files' headings, for --from and "Drawn from" (0.12.0). */
  briefs: Briefs;
  /** Per component, what its views show: counts, each safety question's status, columns and tasks. */
  derived: Record<string, Derived>;
  /** The repository's branches and tags, and the one a change is compared with (0.12.1). */
  refs: string[];
  base: string;
  /** What each command form offers to choose from (choices.ts, 0.12.1). */
  choices: Book;
}

type Target = 'overview' | 'component' | 'command' | 'quick' | 'decision' | 'question' | 'safety' | 'data';
/** Which page sent a message: the sidebar or the editor tab. */
type From = 'nav' | 'editor';

/** The archvow project of the window: its CLI, its state, its problems, its sidebar and its one editor tab. */
export class ArchvowProject {
  readonly cli: ArchvowCli;
  readonly commands: Command[];
  private readonly resources: string;
  private readonly bundledCatalog: Json | null;
  /** The kinds of the component types (contract/cli/kinds.json), for an archvow whose catalog lacks them. */
  private readonly bundledKinds: Json | null;
  /** `archvow catalog --json` in the project: its template, with the project's own templates. */
  private projectCatalog: Json | null = null;
  private remoteHead = '';
  readonly known: string;
  /** How each command's form is laid out (contract/forms/forms.json): labels, essentials, verbs. */
  private readonly forms: Json;
  state: State;
  /** The sidebar page (Overview, Components, Commands). */
  private nav: vscode.WebviewView | undefined;
  private navReady = false;
  /** The one editor tab: a component, a command, a result. */
  private panel: vscode.WebviewPanel | undefined;
  private editorReady = false;
  /** What the editor tab must show once its page listens. */
  private editorQueue: Json[] = [];
  private readonly problems: vscode.DiagnosticCollection;
  private readonly changed = new vscode.EventEmitter<void>();
  /** Fired when the state changes: the sidebar tree follows it. */
  readonly onDidChange = this.changed.event;
  private pendingConfirmations = new Map<string, { args: string[]; stdin?: string; label: string; target: Target; then?: () => void }>();
  private refreshing = false;
  private versionChecked = false;
  private timer: NodeJS.Timeout | undefined;
  private lastEditor: vscode.TextEditor | undefined;
  private liveCatalog: { scope: string; data: Json } | undefined;

  constructor(private readonly context: vscode.ExtensionContext, readonly root: string, private readonly log: vscode.LogOutputChannel) {
    this.resources = path.join(context.extensionPath, 'resources', 'archvow');
    this.commands = (JSON.parse(fs.readFileSync(path.join(this.resources, 'commands.json'), 'utf8')) as { commands: Command[] }).commands;
    this.bundledCatalog = JSON.parse(fs.readFileSync(path.join(this.resources, 'catalog.json'), 'utf8')) as Json;
    try {
      this.bundledKinds = JSON.parse(fs.readFileSync(path.join(this.resources, 'kinds.json'), 'utf8')) as Json;
    } catch {
      this.bundledKinds = null;
    }
    this.known = knownVersion(fs.readFileSync(path.join(this.resources, 'version.txt'), 'utf8'));
    this.forms = JSON.parse(fs.readFileSync(path.join(this.resources, 'forms.json'), 'utf8')) as Json;
    this.cli = new ArchvowCli(root, () => this.cliSettings(), (line) => log.info(line));
    void this.readInterpreter();
    this.problems = vscode.languages.createDiagnosticCollection('archvow');
    this.state = this.emptyState();
    const vows = vscode.workspace.createFileSystemWatcher(new vscode.RelativePattern(root, '.archvow/**/*.vow'));
    const soon = (): void => this.refreshSoon();
    this.lastEditor = vscode.window.activeTextEditor;
    context.subscriptions.push(this.problems, vows, vows.onDidChange(soon), vows.onDidCreate(soon), vows.onDidDelete(soon),
      vscode.window.onDidChangeActiveTextEditor((e) => { if (e) this.lastEditor = e; }),
      vscode.workspace.onDidSaveTextDocument((d) => void this.checkFile(d)),
      vscode.workspace.onDidChangeConfiguration((e) => {
        if (e.affectsConfiguration('archvow.language')) {
          this.state.fr = this.french();
          this.state.languageMode = this.languageMode();
          this.recompute();  // the pending items' labels ("Review hazard"...) are made in the language too
          this.publish();
        }
      }));
  }

  /** VS Code's Python interpreter for the project (the Python extension's), looked at first; '' without one. */
  private interpreter = '';

  private cliSettings(): CliSettings {
    const config = vscode.workspace.getConfiguration('archvow');
    return {
      executable: config.get<string>('executable', 'archvow'),
      python: config.get<string>('python', ''),
      timeoutSeconds: config.get<number>('timeoutSeconds', 180),
      interpreter: this.interpreter,
      useProjectInterpreter: config.get<boolean>('useProjectInterpreter', true),
    };
  }

  /** Asks the Python extension (ms-python.python), when installed, which interpreter the project uses. */
  private async readInterpreter(): Promise<void> {
    try {
      const extension = vscode.extensions.getExtension('ms-python.python');
      if (extension === undefined) return;
      const api = (extension.isActive ? extension.exports : await extension.activate()) as {
        environments?: { getActiveEnvironmentPath?: (resource?: vscode.Uri) => { path?: string } | undefined;
          onDidChangeActiveEnvironmentPath?: vscode.Event<unknown> };
        settings?: { getExecutionDetails?: (resource?: vscode.Uri) => { execCommand?: string[] } | undefined };
      } | undefined;
      const folder = vscode.Uri.file(this.root);
      const found = api?.environments?.getActiveEnvironmentPath?.(folder)?.path
        ?? api?.settings?.getExecutionDetails?.(folder)?.execCommand?.[0] ?? '';
      // "python" alone is the extension's default when nothing was chosen: not an interpreter of the project
      this.interpreter = found && found !== 'python' && found !== 'python3' ? found : '';
      if (!this.interpreterWatched && api?.environments?.onDidChangeActiveEnvironmentPath) {
        this.interpreterWatched = true;
        this.context.subscriptions.push(api.environments.onDidChangeActiveEnvironmentPath(() => void this.readInterpreter()));
      }
    } catch (error) {
      this.log.info(`Python extension: no interpreter (${String(error)})`);
    }
  }

  private interpreterWatched = false;

  private languageMode(): string {
    return vscode.workspace.getConfiguration('archvow').get<string>('language', 'auto');
  }

  private french(): boolean {
    return isFrench(this.languageMode(), vscode.env.language);
  }

  private emptyState(): State {
    return {
      fr: this.french(), languageMode: this.languageMode(), project: { name: path.basename(this.root), root: this.root },
      readiness: readiness(this.root), welcomeError: '', branch: '', loading: '', spec: null, check: null, checkFailure: '',
      next: null, snapshots: {}, work: buildWork(null, null, '', {}, false), rows: [], vows: {}, knownVersion: this.known, derived: {},
      install: { reason: '', version: this.known, python: '', command: '', source: '' }, briefs: { folders: [], files: [] },
      refs: [], base: '', choices: {},
    };
  }

  // ------------------------------------------------------------------ reading the project

  refreshSoon(): void {
    clearTimeout(this.timer);
    this.timer = setTimeout(() => void this.refresh(false), QUIET_MS);
  }

  /** show, check, next, then every component's show (behind what a person asks), as the IntelliJ workspace. */
  async refresh(loud: boolean): Promise<void> {
    if (this.refreshing) {
      this.refreshSoon();
      return;
    }
    this.refreshing = true;
    const fr = this.french();
    try {
      this.state.fr = fr;
      this.state.readiness = readiness(this.root);
      await this.readInterpreter();
      void this.readBranch();
      if (this.state.readiness === 'NOT_INITIALIZED') await this.checkVersion();  // init needs archvow too
      if (this.state.readiness !== 'READY') {
        Object.assign(this.state, { spec: null, check: null, next: null, snapshots: {}, checkFailure: '',
          welcomeError: this.state.install.reason === 'missing' ? this.state.welcomeError : '' });
        this.recompute();
        this.publish();
        return;
      }
      await this.checkVersion();
      this.state.loading = tr(fr, 'Loading the architecture…', "Chargement de l'architecture…");
      this.publish();
      const shown = await this.cli.run(['show', '--json']);
      const spec = parse(shown.stdout);
      if (shown.exitCode !== 0 || spec === null) {
        this.state.welcomeError = (shown.startError ?? (shown.stderr || shown.stdout)).trim()
          || tr(fr, 'archvow could not read the specification.', "archvow n'a pas pu lire la spécification.");
        this.state.spec = null;
        this.state.loading = '';
        if (loud) void vscode.window.showErrorMessage(this.state.welcomeError);
        this.recompute();
        this.publish();
        return;
      }
      this.state.welcomeError = '';
      this.state.spec = spec;
      this.state.briefs = readBriefs(this.root, spec);
      // the project's catalog: its template's types and kinds, and the project's own templates (component add, template new)
      const catalog = parse((await this.cli.run(['catalog', '--json'])).stdout);
      if (catalog !== null && objects(catalog, 'templates').length > 0) this.projectCatalog = catalog;
      const checked = await this.cli.run(['check', '--json']);
      this.setCheck(parse(checked.stdout), (checked.startError ?? (checked.stderr || checked.stdout)).trim());
      const next = await this.cli.run(['next', '--json']);
      const rows = parse(next.stdout);
      this.state.next = Array.isArray(rows) ? (rows as Json[]) : null;
      this.state.loading = tr(fr, 'Reading each component…', 'Lecture de chaque composant…');
      this.recompute();
      this.publish();
      const ids = objects(spec, 'containers').flatMap((c) => objects(c, 'components').map((x) => str(x, 'id')));
      const snapshots: Record<string, Json> = {};
      for (let i = 0; i < ids.length; i++) {
        const view = parse((await this.cli.run(['show', ids[i], '--json'])).stdout);
        if (view !== null) snapshots[ids[i]] = view;
        if (i % 5 === 4) {
          this.state.snapshots = { ...this.state.snapshots, ...snapshots };
          this.recompute();
          this.publish();
        }
      }
      this.state.snapshots = snapshots;
      this.state.vows = {};
      this.state.loading = '';
      this.recompute();
      this.publish();
    } finally {
      this.refreshing = false;
    }
  }

  private setCheck(check: Json | null, failure: string): void {
    if (check !== null && Array.isArray(check.violations)) {
      this.state.check = check;
      this.state.checkFailure = '';
      this.showProblems(check);
    } else {
      this.state.check = null;
      this.state.checkFailure = failure;
    }
  }

  private recompute(): void {
    const s = this.state;
    s.work = buildWork(s.spec, s.check, s.checkFailure, s.snapshots, s.fr,
      fs.readFileSync(path.join(this.resources, s.fr ? 'rules.fr.md' : 'rules.md'), 'utf8'), this.commands);
    s.rows = architectureRows(s.spec, s.check, s.snapshots, s.work.pendingByComponent);
    s.derived = Object.fromEntries(Object.entries(s.snapshots).map(([id, view]) => [id, derive(view)]));
    this.recomputeChoices();
    this.changed.fire();
  }

  /** What the forms offer: from the spec, the components read, the catalog and the repository's refs. */
  private recomputeChoices(): void {
    const s = this.state;
    const gitflow = str(s.spec ? s.spec.init_answers : null, 'branch_naming') === 'gitflow';
    s.base = bestBase(s.refs, s.branch, gitflow, this.remoteHead);
    s.choices = choiceBook(this.commands, { spec: s.spec, snapshots: s.snapshots, catalog: this.projectCatalog ?? this.bundledCatalog,
      kinds: this.bundledKinds, refs: s.refs, base: s.base, fr: s.fr });
  }

  private async checkVersion(): Promise<void> {
    if (this.versionChecked) return;
    const result = await this.cli.run(['--version']);
    if (result.startError !== undefined) {
      this.state.welcomeError = result.startError;
      this.offerInstall('missing');
      return;
    }
    this.versionChecked = true;
    const warning = versionWarning(parseVersion(result.stdout + result.stderr), this.known, this.french());
    this.offerInstall(warning === null ? '' : 'version');
    if (warning !== null) {
      const install = tr(this.french(), `Install archvow ${this.known}`, `Installer archvow ${this.known}`);
      void vscode.window.showWarningMessage(warning, install).then((chosen) => {
        if (chosen === install) this.askInstall('nav');
      });
    }
  }

  // ------------------------------------------------------------------ installing archvow (guided, 0.12.0)

  private pendingInstall = new Map<string, { program: string; args: string[]; python: string }>();

  /** The install card: the Python it goes into and the command, or nothing when archvow answers as expected. */
  private offerInstall(reason: InstallReason): void {
    const config = vscode.workspace.getConfiguration('archvow');
    const python = reason === '' ? null : installPython(this.root, this.interpreter, config.get<string>('python', ''), (f) => fs.existsSync(f));
    this.state.install = offer(reason, this.known, python, this.root, installSource(config.get<string>('installFrom', '')));
  }

  /** "Install archvow": the command, shown; it runs only once the person confirms it. */
  private askInstall(from: From): void {
    const fr = this.state.fr;
    const install = this.state.install;
    if (!install.reason) this.offerInstall('missing');
    if (!this.state.install.python) {
      void vscode.window.showInformationMessage(tr(fr, 'No Python found for this project: choose one in the settings (archvow.python), or create .venv.',
        'Aucun Python trouvé pour ce projet : choisis-en un dans les réglages (archvow.python), ou crée .venv.'));
      return;
    }
    const config = vscode.workspace.getConfiguration('archvow');
    const command = installCommand(this.state.install.python, this.root, this.known, installSource(config.get<string>('installFrom', '')));
    const token = crypto.randomBytes(8).toString('hex');
    this.pendingInstall.set(token, { ...command, python: this.state.install.python });
    const summary = tr(fr, `Installs archvow ${this.known} into ${this.state.install.python}, from ${this.state.install.source}. Nothing else changes; afterwards archvow.python points to this Python.`,
      `Installe archvow ${this.known} dans ${this.state.install.python}, depuis ${this.state.install.source === "pip's configuration" ? 'la configuration de pip' : this.state.install.source}. Rien d'autre ne change ; ensuite archvow.python désigne ce Python.`);
    const message = { type: 'confirm', token, title: tr(fr, `Install archvow ${this.known}`, `Installer archvow ${this.known}`), summary,
      target: 'welcome', command: this.state.install.command };
    this.reply(from, message);
  }

  /** pip, after the confirmation: no shell, in the project folder, its output in the result card. */
  private async runInstall(token: string, from: From): Promise<void> {
    const job = this.pendingInstall.get(token);
    this.pendingInstall.delete(token);
    if (!job) return;
    const fr = this.state.fr;
    const label = tr(fr, `Install archvow ${this.known}`, `Installer archvow ${this.known}`);
    const shown = [job.program, ...job.args].join(' ');
    this.reply(from, { type: 'running', target: 'welcome', label, args: shown });
    const wsl = job.program === 'wsl.exe';
    const out = await new Promise<{ code: number; text: string; error?: string }>((resolve) => {
      const chunks: Buffer[] = [];
      let child;
      try {
        child = spawn(job.program, job.args, { cwd: wsl ? os.homedir() : this.root, shell: false, windowsHide: true,
          env: { ...process.env, PYTHONIOENCODING: 'utf-8', PIP_DISABLE_PIP_VERSION_CHECK: '1' } });
      } catch (error) {
        resolve({ code: -1, text: '', error: String((error as Error).message) });
        return;
      }
      const timer = setTimeout(() => child.kill(), 600_000);
      child.stdout.on('data', (c: Buffer) => chunks.push(c));
      child.stderr.on('data', (c: Buffer) => chunks.push(c));
      child.on('error', (error) => { clearTimeout(timer); resolve({ code: -1, text: '', error: error.message }); });
      child.on('close', (code) => { clearTimeout(timer); resolve({ code: code ?? -1, text: Buffer.concat(chunks).toString('utf8') }); });
    });
    this.log.info(`${shown}  ·  ${out.error ? 'did not start' : 'exit ' + out.code}`);
    const ok = out.code === 0;
    const html = renderResult(label, ok ? 0 : out.code, ok ? out.text : '', ok ? '' : (out.error ?? out.text), false, fr);
    const result = { type: 'result', target: 'welcome', label, html, raw: (out.error ?? out.text).trim(), args: shown, ok };
    this.reply(from, result);
    if (!ok) return;
    // archvow now runs from this Python: remembered for the project, then everything is read again
    await vscode.workspace.getConfiguration('archvow').update('python', job.python, vscode.ConfigurationTarget.Workspace);
    this.versionChecked = false;
    this.state.welcomeError = '';
    this.state.install = { reason: '', version: this.known, python: '', command: '', source: '' };
    await this.refresh(true);
  }

  private git(args: string[]): Promise<string | null> {
    return new Promise((resolve) => {
      execFile('git', args, { cwd: this.root, timeout: 3000 }, (error, stdout) => resolve(error ? null : stdout));
    });
  }

  /** The branch, the branches and tags that exist (Diff, Review, Budget compare with one of them). */
  private async readBranch(): Promise<void> {
    const [current, refs, head] = await Promise.all([this.git(['branch', '--show-current']),
      this.git(['for-each-ref', '--format=%(refname)', 'refs/heads', 'refs/remotes', 'refs/tags']),
      this.git(['symbolic-ref', '--quiet', '--short', 'refs/remotes/origin/HEAD'])]);
    const name = current === null ? '—' : current.trim() || '(detached)';
    const known = refNames(refs ?? '');
    this.remoteHead = (head ?? '').trim();
    if (name !== this.state.branch || known.join('\n') !== this.state.refs.join('\n')) {
      this.state.branch = name;
      this.state.refs = known;
      this.recomputeChoices();
      this.publish();
    }
  }

  /** A base that does not exist here: what to choose instead ('' when it exists, or Git knows it as a commit). */
  private async refProblem(ref: string): Promise<string> {
    const problem = missingRef(ref, this.state.refs, this.state.fr);
    if (!problem) return '';
    return (await this.git(['rev-parse', '--verify', '--quiet', ref.trim() + '^{commit}'])) !== null ? '' : problem;
  }

  /** One component again (after a change), without reading the whole project. */
  async reloadComponent(id: string, first = true): Promise<void> {
    const view = parse((await this.cli.run(['show', id, '--json'], undefined, first)).stdout);
    if (view !== null) {
      this.state.snapshots = { ...this.state.snapshots, [id]: view };
      delete this.state.vows[id];
      this.recompute();
      this.publish();
    }
  }

  private showProblems(check: Json): void {
    this.problems.clear();
    const byFile = new Map<string, vscode.Diagnostic[]>();
    for (const p of problemsOf(check as never)) {
      const diagnostic = new vscode.Diagnostic(new vscode.Range(p.line, p.col, p.line, Number.MAX_SAFE_INTEGER), p.message,
        p.error ? vscode.DiagnosticSeverity.Error : vscode.DiagnosticSeverity.Warning);
      diagnostic.source = 'archvow';
      diagnostic.code = p.code;
      byFile.set(p.file, [...(byFile.get(p.file) ?? []), diagnostic]);
    }
    for (const [file, list] of byFile) this.problems.set(vscode.Uri.file(path.join(this.root, file)), list);
  }

  /** `archvow check --file` on a saved Python file: its problems replace the file's. */
  private async checkFile(document: vscode.TextDocument): Promise<void> {
    if (!vscode.workspace.getConfiguration('archvow').get<boolean>('checkOnSave', true)) return;
    if (this.state.readiness !== 'READY' || document.languageId !== 'python' || !document.uri.fsPath.startsWith(this.root + path.sep)) return;
    const relative = path.relative(this.root, document.uri.fsPath).split(path.sep).join('/');
    const result = await this.cli.run(['check', '--file', relative, '--json']);
    const data = parse(result.stdout);
    if (data === null || !Array.isArray(data.violations)) {
      this.log.error(`check --file ${relative}: ${(result.startError ?? result.stderr).trim()}`);
      return;
    }
    this.problems.set(document.uri, problemsOf(data as never).filter((p) => p.file === relative).map((p) => {
      const d = new vscode.Diagnostic(new vscode.Range(p.line, p.col, p.line, Number.MAX_SAFE_INTEGER), p.message,
        p.error ? vscode.DiagnosticSeverity.Error : vscode.DiagnosticSeverity.Warning);
      d.source = 'archvow';
      d.code = p.code;
      return d;
    }));
  }

  // ------------------------------------------------------------------ the sidebar and the editor tab

  /** The sidebar's page: VS Code calls this when the Archvow view is first shown. */
  attachView(view: vscode.WebviewView): void {
    this.nav = view;
    this.navReady = false;
    view.webview.options = { enableScripts: true, localResourceRoots: [vscode.Uri.joinPath(this.context.extensionUri, 'media')] };
    view.webview.html = this.html(view.webview, 'nav');
    view.webview.onDidReceiveMessage((m: Json) => void this.receive(m, 'nav'));
    view.onDidDispose(() => { if (this.nav === view) { this.nav = undefined; this.navReady = false; } });
    if (this.state.spec === null && !this.refreshing) void this.refresh(false);
  }

  /** Shows the sidebar; with a component, shows that component in the editor tab (one tab only). */
  show(component?: string, clarify = false): void {
    if (!component) {
      void vscode.commands.executeCommand('archvow.workspace.focus');
      return;
    }
    this.toEditor({ type: 'select', component, clarify });
    this.toNav({ type: 'selected', component });
    void this.reloadComponent(component);
  }

  /** `archvow show <file>[:<line>]`: the component that owns a file, shown in the editor tab; a file no
   *  component owns gets archvow's own hint (the `component add ... --file` to run). */
  async showFile(file: string, line: number): Promise<void> {
    if (!file.startsWith(this.root + path.sep)) {
      void vscode.window.showInformationMessage(tr(this.state.fr, 'This file is not in the archvow project.', "Ce fichier n'est pas dans le projet archvow."));
      return;
    }
    const relative = path.relative(this.root, file).split(path.sep).join('/');
    const result = await this.cli.run(['show', line > 0 ? `${relative}:${line}` : relative, '--json']);
    const view = parse(result.stdout);
    const component = view === null ? '' : str(view, 'component');
    if (component) {
      this.show(component);
      return;
    }
    const hint = view === null ? (result.startError ?? result.stderr).trim() : str(view, 'hint');
    void vscode.window.showInformationMessage(hint || tr(this.state.fr, 'No component owns this file.', "Aucun composant ne possède ce fichier."));
  }

  /** The editor tab, opened if it is closed; revealed without taking the focus from the sidebar. */
  private editor(): vscode.WebviewPanel {
    if (this.panel !== undefined) {
      if (!this.panel.visible) this.panel.reveal(undefined, true);
      return this.panel;
    }
    const panel = vscode.window.createWebviewPanel('archvow.detail', 'archvow', { viewColumn: vscode.ViewColumn.Active, preserveFocus: true }, {
      enableScripts: true, retainContextWhenHidden: true,
      localResourceRoots: [vscode.Uri.joinPath(this.context.extensionUri, 'media')],
    });
    panel.iconPath = { light: vscode.Uri.joinPath(this.context.extensionUri, 'media', 'vow.svg'),
      dark: vscode.Uri.joinPath(this.context.extensionUri, 'media', 'vow_dark.svg') };
    panel.webview.html = this.html(panel.webview, 'editor');
    this.editorReady = false;
    panel.onDidDispose(() => {
      if (this.panel === panel) {
        this.panel = undefined;
        this.editorReady = false;
        this.editorQueue = [];
        this.toNav({ type: 'selected', component: '' });
      }
    });
    panel.webview.onDidReceiveMessage((m: Json) => void this.receive(m, 'editor'));
    this.panel = panel;
    if (this.state.spec === null && !this.refreshing) void this.refresh(false);
    return panel;
  }

  private html(webview: vscode.Webview, role: From): string {
    const nonce = crypto.randomBytes(16).toString('base64');
    const media = (name: string): vscode.Uri => webview.asWebviewUri(vscode.Uri.joinPath(this.context.extensionUri, 'media', name));
    const csp = `default-src 'none'; img-src ${webview.cspSource} data:; style-src ${webview.cspSource}; script-src 'nonce-${nonce}';`;
    return `<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8">
<meta http-equiv="Content-Security-Policy" content="${csp}">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<link rel="stylesheet" href="${media('workspace.css')}"><title>archvow</title></head>
<body class="${role}"><div id="app"></div>
<script nonce="${nonce}">window.ARCHVOW_ROLE = ${JSON.stringify(role)}; window.ARCHVOW_LOGO = ${JSON.stringify(media('archvow.png').toString())};</script>
<script nonce="${nonce}" src="${media('workspace.js')}"></script></body></html>`;
  }

  /** To the editor tab (opened when closed); kept until its page listens. */
  private toEditor(message: Json): void {
    const panel = this.editor();
    if (this.editorReady) void panel.webview.postMessage(message);
    else this.editorQueue.push(message);
  }

  /** To the editor tab only if it is open: a running command or a result it is waiting for. */
  private toOpenEditor(message: Json): void {
    if (this.panel === undefined) return this.toEditor(message);
    if (this.editorReady) void this.panel.webview.postMessage(message);
    else this.editorQueue.push(message);
  }

  private toNav(message: Json): void {
    if (this.nav !== undefined && this.navReady) void this.nav.webview.postMessage(message);
  }

  private reply(from: From, message: Json): void {
    if (from === 'nav') this.toNav(message);
    else this.toOpenEditor(message);
  }

  private publish(): void {
    const message = { type: 'state', state: this.state };
    this.toNav(message);
    if (this.panel !== undefined && this.editorReady) void this.panel.webview.postMessage(message);
  }

  private sendResult(target: Target, labelText: string, result: CliResult): void {
    const fr = this.state.fr;
    const html = result.startError !== undefined
      ? renderResult(labelText, -1, '', result.startError, false, fr)
      : renderResult(labelText, result.exitCode, result.stdout, result.stderr, result.timedOut, fr);
    this.toOpenEditor({ type: 'result', target, label: labelText, html, raw: (result.stdout + (result.stderr ? '\n' + result.stderr : '')).trim(),
      args: ['archvow', ...result.args].join(' '), ok: result.exitCode === 0 && !result.timedOut && result.startError === undefined,
      ...alerts(result) });
  }

  // ------------------------------------------------------------------ what the page asks

  private async receive(m: Json, from: From): Promise<void> {
    const fr = this.state.fr;
    switch (m.type) {
      case 'ready': {
        const init = { type: 'init', commands: this.commands, catalog: this.bundledCatalog, knownVersion: this.known, forms: this.forms,
          types: componentTypes(this.bundledCatalog),
          rules: { en: fs.readFileSync(path.join(this.resources, 'rules.md'), 'utf8'), fr: fs.readFileSync(path.join(this.resources, 'rules.fr.md'), 'utf8') } };
        const state = { type: 'state', state: this.state };
        if (from === 'nav') {
          this.navReady = true;
          this.toNav(init);
          this.toNav(state);
          return;
        }
        if (this.panel === undefined) return;
        this.editorReady = true;
        void this.panel.webview.postMessage(init);
        void this.panel.webview.postMessage(state);
        for (const queued of this.editorQueue.splice(0)) void this.panel.webview.postMessage(queued);
        return;
      }
      case 'title':
        if (from === 'editor' && this.panel) this.panel.title = String(m.text || 'archvow');
        return;
      case 'show':
        return this.show(String(m.component), false);
      case 'quickList':
        this.toEditor({ type: 'quickList', index: Number(m.index) || 0 });
        return;
      case 'showChecksNav':
        await vscode.commands.executeCommand('archvow.workspace.focus');
        this.toNav({ type: 'showChecks' });
        return;
      case 'initCommand':
        return this.openCommand('init', {}, '');
      case 'browse': {
        const picked = await vscode.window.showOpenDialog({ canSelectFiles: false, canSelectFolders: true, canSelectMany: false,
          title: tr(fr, 'Choose destination folder', 'Choisir le dossier de destination') });
        if (picked && picked[0]) this.reply(from, { type: 'browsed', field: String(m.field ?? 'dir'), path: picked[0].fsPath });
        return;
      }
      case 'explain':
        this.reply(from, { type: 'explained', code: String(m.code), text: this.explain(String(m.code)) });
        return;
      case 'refresh':
        return this.refresh(true);
      case 'run':
        return this.runDirect(String(m.label), stringList(m.args), (m.target as Target) ?? 'overview');
      case 'form':
        return this.runForm(String(m.command), (m.values ?? {}) as Record<string, string>, stringList(m.initAnswers ?? []), String(m.selected ?? ''),
          (m.baseline ?? {}) as Record<string, string>);
      case 'formPreview':
        return this.formPreview(String(m.command), (m.values ?? {}) as Record<string, string>, stringList(m.initAnswers ?? []),
          String(m.selected ?? ''), (m.baseline ?? {}) as Record<string, string>);
      case 'inline':
        return this.inlineAction(m);
      case 'install':
        this.askInstall(from);
        return;
      case 'confirmed': {
        if (this.pendingInstall.has(String(m.token))) return this.runInstall(String(m.token), from);
        const job = this.pendingConfirmations.get(String(m.token));
        this.pendingConfirmations.delete(String(m.token));
        if (job) await this.execute(job.label, job.args, job.target, job.stdin, true, job.then);
        return;
      }
      case 'cancelled':
        this.pendingConfirmations.delete(String(m.token));
        this.pendingInstall.delete(String(m.token));
        return;
      case 'openCommand':
        return this.openCommand(String(m.command), (m.values ?? {}) as Record<string, string>, String(m.selected ?? ''));
      case 'selectComponent':
        this.toNav({ type: 'selected', component: String(m.component) });
        return this.reloadComponent(String(m.component));
      case 'loadQuestion':
        return this.reloadComponent(String(m.component));
      case 'vow':
        return this.readVow(String(m.component), from);
      case 'taskPrompt':
        return this.taskPrompt(String(m.component), String(m.task ?? ''));
      case 'choose':
        return this.choose(m);
      case 'catalog':
        return this.loadCatalog(String(m.template ?? ''), m.force === true, from);
      case 'openSpec':
        return this.openSpec(String(m.component));
      case 'openVowFile':
        return this.openFile(path.join(this.root, '.archvow', 'components', String(m.component), 'spec.vow'), 0, '');
      case 'openCode':
        return this.openCode(String(m.component));
      case 'openProjectVow':
        return this.openFile(path.join(this.root, '.archvow', 'spec.vow'), 0,
          tr(fr, 'Project vow not available: .archvow/spec.vow', 'Vow du projet indisponible : .archvow/spec.vow'));
      case 'openChecks':
        return this.openChecks();
      case 'openRule':
        return this.openRule(String(m.code));
      case 'openFile':
        return this.openProjectFile(String(m.file), Number(m.line) || 0);
      case 'openBrief': {
        const where = locate(this.state.briefs, String(m.ref ?? ''));
        return this.openProjectFile(where.file, where.line);
      }
      case 'copy':
        await vscode.env.clipboard.writeText(String(m.text ?? ''));
        void vscode.window.showInformationMessage(tr(fr, 'Copied.', 'Copié.'));
        return;
      case 'message':
        void vscode.window.showInformationMessage(String(m.text));
        return;
      case 'language':
        await vscode.workspace.getConfiguration('archvow').update('language', String(m.mode), vscode.ConfigurationTarget.Global);
        return;
      case 'settings':
        await vscode.commands.executeCommand('workbench.action.openSettings', 'archvow');
        return;
      case 'log':
        this.log.show();
        return;
      case 'sidebar':
        await vscode.commands.executeCommand('archvow.workspace.focus');
        return;
    }
  }

  /** Check from the sidebar's title bar or the command palette: the result in the editor tab, the checks in Overview. */
  async runCheck(): Promise<void> {
    await vscode.commands.executeCommand('archvow.workspace.focus');
    this.toNav({ type: 'tab', tab: 'overview' });
    await this.runDirect('check', ['check', '--json'], 'overview');
  }

  /** `archvow prompt <component>` into the clipboard. */
  async copyPrompt(component: string): Promise<void> {
    const fr = this.state.fr;
    const result = await this.cli.run(['prompt', component], undefined, true);
    if (result.exitCode !== 0 || result.startError !== undefined) {
      void vscode.window.showErrorMessage(result.startError ?? (result.stderr || result.stdout).trim());
      return;
    }
    await vscode.env.clipboard.writeText(result.stdout);
    void vscode.window.showInformationMessage(tr(fr, `The instruction for ${component} is in the clipboard: paste it to your AI assistant.`,
      `L'instruction pour ${component} est dans le presse-papiers : colle-la à ton assistant IA.`));
  }

  /** A read-only command from a button (Check, Next, Doctor...). Anything else asks for a confirmation. */
  private async runDirect(labelText: string, args: string[], target: Target): Promise<void> {
    if (args.length === 1 && args[0] === '--version') {
      await this.execute(labelText, args, target, undefined, false);
      return;
    }
    const command = this.commandOf(args);
    if (command === undefined) {
      void vscode.window.showErrorMessage(`Unknown archvow command: ${args.join(' ')}`);
      return;
    }
    const at = args.indexOf('--base');
    const problem = at >= 0 ? await this.refProblem(args[at + 1] ?? '') : '';
    if (problem) {
      void vscode.window.showWarningMessage(problem);
      return;
    }
    if (isMutating(command, args)) this.confirm(labelText, args, target, preview(args));
    else await this.execute(labelText, args, target, undefined, false);
  }

  /** The grammar command a command line starts with. */
  private commandOf(args: string[]): Command | undefined {
    return this.commands.find((c) => c.path.every((word, i) => args[i] === word)
      && !this.commands.some((longer) => longer.path.length > c.path.length && longer.path.every((w, i) => args[i] === w)));
  }

  private confirm(labelText: string, args: string[], target: Target, summary: string, stdin?: string, then?: () => void): void {
    const token = crypto.randomBytes(8).toString('hex');
    this.pendingConfirmations.set(token, { args, stdin, label: labelText, target, then });
    this.toEditor({ type: 'confirm', token, title: labelText, summary, target, command: preview(args) });
  }

  private async execute(labelText: string, args: string[], target: Target, stdin: string | undefined, change: boolean,
    then?: () => void): Promise<void> {
    this.toEditor({ type: 'running', target, label: labelText, args: ['archvow', ...args].join(' ') });
    const result = await this.cli.run(args, stdin, true);
    this.sendResult(target, labelText, result);
    if (args[0] === 'check' && !result.timedOut) {
      const parsed = parse(result.stdout);
      if (parsed !== null && Array.isArray(parsed.violations) && !args.includes('--file')) {
        this.setCheck(parsed, '');
        this.recompute();
        this.publish();
        this.toNav({ type: 'showChecks' });
      }
    }
    const ok = result.exitCode === 0 && result.startError === undefined && !result.timedOut;
    if (ok && then) then();
    else if (ok && change) await this.refresh(false);
  }

  /** The command line the form asks for, as it is typed: the live preview (design A), or what is still missing. */
  private formPreview(name: string, values: Record<string, string>, initAnswers: string[], selected: string,
    baseline: Record<string, string>): void {
    const command = findCommand(this.commands, name);
    if (command === undefined) return;
    const plan = planForm(command, values, this.state.fr, { initAnswers, components: Object.keys(this.state.snapshots), selected, baseline });
    const text = plan.kind === 'run' ? preview(plan.args.filter((a) => a !== '--json' && a !== '--no-input' && a !== '--yes')) : '';
    this.toOpenEditor({ type: 'formPreview', command: name, text, missing: plan.kind === 'error' ? plan.message : '' });
  }

  private async runForm(name: string, values: Record<string, string>, initAnswers: string[], selected: string,
    baseline: Record<string, string> = {}): Promise<void> {
    const fr = this.state.fr;
    const command = findCommand(this.commands, name);
    if (command === undefined) return;
    const components = Object.keys(this.state.snapshots);
    const plan = planForm(command, values, fr, { initAnswers, components, selected, baseline });
    if (plan.kind === 'error') {
      void vscode.window.showInformationMessage(plan.message);
      return;
    }
    // a branch that does not exist here: say which exist, rather than git's "Not a valid object name"
    for (const field of ['base', 'start', 'end']) {
      const ref = (values[field] ?? '').trim();
      const problem = ref && command.parameters.some((p) => p.name === field) ? await this.refProblem(ref) : '';
      if (problem) {
        void vscode.window.showWarningMessage(problem);
        return;
      }
    }
    if (plan.kind === 'clarify') {
      this.show(plan.component, true);
      return;
    }
    let args = plan.args;
    let stdin: string | undefined;
    if (plan.stdinFromEditor) {
      const editor = this.lastEditor;
      if (editor === undefined) {
        void vscode.window.showInformationMessage(tr(fr, 'Open the file to check in an editor.', 'Ouvre le fichier à vérifier dans un éditeur.'));
        return;
      }
      const absolute = editor.document.uri.fsPath;
      const at = args.indexOf('--file');
      if (at >= 0) {
        const requested = path.resolve(this.root, args[at + 1]);
        if (requested !== absolute) {
          void vscode.window.showInformationMessage(tr(fr, '--file must match the open file.', '--file doit correspondre au fichier ouvert.'));
          return;
        }
      } else {
        args = [...args, '--file', absolute];
      }
      stdin = editor.document.getText();
    }
    if (plan.mutating) this.confirm(name, args, 'command', preview(args), stdin);
    else await this.execute(name, args, 'command', stdin, false);
  }

  /** The buttons inside a component's views: decide, propose, accept, answer, hazard, dismiss... */
  private async inlineAction(m: Json): Promise<void> {
    const fr = this.state.fr;
    const component = String(m.component ?? '');
    const action = String(m.action ?? '');
    let args: string[];
    let target: Target;
    let summary: string;
    try {
      switch (action) {
        case 'decision make':
        case 'decision propose':
        case 'decision accept': {
          target = 'decision';
          const number = String(m.number ?? '');
          const answer = String(m.answer ?? '').trim();
          if (action !== 'decision accept' && !answer) throw new Error(tr(fr, 'Enter an answer first.', "Saisis d'abord une réponse."));
          args = action === 'decision accept' ? inline.accept(component, number)
            : action === 'decision propose' ? inline.propose(component, number, answer) : inline.decide(component, number, answer);
          summary = `D${number} — ${m.question ?? ''}\n\n` + (action === 'decision accept' ? 'Proposal / Proposition: ' + (m.proposed ?? '')
            : 'Answer / Réponse: ' + answer) + '\n\n' + tr(fr, 'This changes the archvow specification. Confirm?',
            'Cette action modifie la spécification archvow. Confirmer ?');
          break;
        }
        case 'question': {
          target = 'question';
          const answer = String(m.answer ?? '').trim();
          if (!answer) throw new Error(tr(fr, 'Enter an answer first.', "Saisis d'abord une réponse."));
          args = inline.decide(component, String(m.key ?? ''), answer);
          summary = `${m.key} — ${m.question ?? ''}\n\n${tr(fr, 'Answer', 'Réponse')}: ${answer}`;
          break;
        }
        case 'hazard add':
        case 'hazard dismiss':
        case 'hazard suggested': {
          target = 'safety';
          const word = String(m.word ?? '');
          const description = String(m.text ?? '').trim();
          const severity = action === 'hazard suggested' ? 'high' : String(m.severity ?? 'high');
          if (action === 'hazard dismiss' && !description && m.covered !== true) {
            throw new Error(tr(fr, 'Explain why this hazard is not applicable.', 'Justifie pourquoi ce hazard n\'est pas applicable.'));
          }
          args = action === 'hazard dismiss' ? inline.dismiss(component, word, description)
            : inline.hazard(component, word, severity, action === 'hazard suggested' ? '' : description);
          summary = `S${m.number} — ${m.question ?? ''}\n\n` + (action === 'hazard dismiss' ? (description ? '' : String(m.preview ?? '') + '\n')
            + tr(fr, 'Mark as not applicable?', 'Déclarer non applicable ?')
            : action === 'hazard suggested' ? String(m.preview ?? '') + '\n' + tr(fr, 'Accept the suggested hazard and applicable invariant?',
              'Accepter le hazard suggéré et son invariant éventuel ?') : tr(fr, 'Declare this hazard?', 'Déclarer ce hazard ?'));
          if (action === 'hazard add' && ['critical', 'high'].includes(severity) && description) {
            summary += '\n\n' + tr(fr, 'Important: this high/critical hazard will stay pending until an invariant is added.',
              "Important : ce hazard élevé/critique restera en attente jusqu'à l'ajout d'un invariant.");
          }
          break;
        }
        case 'hazard remove': {
          target = 'safety';
          args = inline.removeHazard(component, String(m.id ?? ''));
          summary = `S${m.number} — ${m.question ?? ''}\n\n` + tr(fr, `Remove the hazard ${args[2]}? The invariants that protected only it go with it.`,
            `Supprimer le hazard ${args[2]} ? Les invariants qui ne protégeaient que lui partent avec lui.`);
          break;
        }
        case 'hazard change': {
          // archvow 1.19: hazard edit - same id, its invariants stay and become to review
          target = 'safety';
          const description = String(m.text ?? '').trim();
          if (!description) throw new Error(tr(fr, 'Write what must never happen.', 'Écris ce qui ne doit jamais arriver.'));
          args = inline.editHazard(component, String(m.hazard ?? ''), description, String(m.severity ?? ''));
          summary = `S${m.number} — ${m.question ?? ''}\n\n` + tr(fr, `Change ${args[2]} to (${m.severity}): ${description}\nSame id; the invariants that protect it become to review.`,
            `Modifier ${args[2]} en (${m.severity}) : ${description}\nMême id ; les invariants qui le protègent deviennent à revoir.`);
          break;
        }
        case 'invariant add': {
          target = 'safety';
          args = inline.invariant(component, String(m.text ?? ''), String(m.hazard ?? ''));
          summary = `S${m.number} — ${m.question ?? ''}\n\n` + tr(fr, `Add this invariant, protecting ${m.hazard}?`, `Ajouter cet invariant, qui protège ${m.hazard} ?`)
            + `\n\n${args[3]}`;
          break;
        }
        default:
          return;
      }
    } catch (error) {
      void vscode.window.showInformationMessage((error as Error).message);
      return;
    }
    this.confirm(args[0] + ' ' + args[1], args, target, summary, undefined, () => void this.reloadComponent(component));
  }

  /** Commands tab with a command selected and its known fields filled. */
  private openCommand(name: string, values: Record<string, string>, selected: string): void {
    const fr = this.state.fr;
    const target = values.component ?? selected;
    const snapshot = this.state.snapshots[target];
    const entry = objects(this.state.spec, 'containers').flatMap((c) => objects(c, 'components')).find((x) => str(x, 'id') === target);
    const known = prefill(name, values, selected, snapshot, entry);
    if (name === 'component add' && !known.container && selected) {
      const container = objects(this.state.spec, 'containers').find((c) => objects(c, 'components').some((x) => str(x, 'id') === selected));
      if (container) known.container = str(container, 'id');
    }
    let help = '';
    if (name === 'component edit' && snapshot) {
      help = tr(fr, 'The fields hold what the component has: change what must change; only that is sent. The decisions, hazards and '
        + 'examples recorded stay (from archvow 1.18, a person is asked to look at them again after such a change).',
      'Les champs contiennent ce que le composant a : change ce qui doit changer ; seul cela est envoyé. Les décisions, hazards et '
        + 'exemples enregistrés restent (depuis archvow 1.18, une personne doit les revoir après un tel changement).');
    } else if (Object.keys(known).length) {
      help = tr(fr, 'Known information prefilled from the current spec or diagnostic. Review before running; blank optional fields are unchanged.',
        'Informations connues préremplies depuis la spec ou le diagnostic. Vérifie avant d\'exécuter ; les champs optionnels vides restent inchangés.');
    }
    if (!findCommand(this.commands, name)) {
      void vscode.window.showInformationMessage('Unknown command / Commande inconnue : ' + name);
      return;
    }
    this.toEditor({ type: 'openCommand', command: name, values: known, help });
    this.toNav({ type: 'commandSelected', command: name });
  }

  /** A choice the IntelliJ workspace asks in a dialog: a rule, a hazard, an invariant. */
  private async choose(m: Json): Promise<void> {
    const fr = this.state.fr;
    const component = String(m.component ?? '');
    const view = this.state.snapshots[component];
    if (!view) return;
    const purpose = String(m.purpose);
    if (purpose === 'rule edit' || purpose === 'rule remove') {
      const rules = objects(view, 'rules').filter((r) => str(r, 'id'));
      if (rules.length === 0) {
        void vscode.window.showInformationMessage(tr(fr, 'No rule to edit or remove.', 'Aucune règle à modifier ou supprimer.'));
        return;
      }
      const picked = await vscode.window.showQuickPick(rules.map((r) => ({ label: str(r, 'id'), detail: str(r, 'text') })),
        { placeHolder: tr(fr, 'Select a rule:', 'Sélectionne une règle :') });
      if (!picked) return;
      this.openCommand(purpose, purpose === 'rule edit' ? { id: picked.label, text: picked.detail ?? '' } : { id: picked.label }, component);
      return;
    }
    const question = objects(view, 'safety').find((q) => str(q, 'number') === String(m.number));
    if (!question) return;
    if (purpose === 'invariant add') {
      const ids = objects(question, 'hazards').map((h) => str(h, 'id')).filter((x) => x);
      if (ids.length === 0) {
        void vscode.window.showInformationMessage(tr(fr, 'Declare a hazard before adding an invariant.', "Déclare un hazard avant d'ajouter un invariant."));
        return;
      }
      const picked = ids.length === 1 && m.only ? ids[0] : await vscode.window.showQuickPick(ids, { placeHolder: tr(fr, 'Protect which hazard?', 'Quel hazard protéger ?') });
      if (!picked) return;
      const values: Record<string, string> = { component, prevents: picked };
      const suggestion = str(question.suggestion ?? null, 'invariant');
      if (suggestion) values.statement = suggestion;
      this.openCommand('invariant add', values, component);
      return;
    }
    if (purpose === 'hazard remove' || purpose === 'invariant remove') {
      const ids: string[] = [];
      for (const h of objects(question, 'hazards')) {
        if (purpose === 'hazard remove') { if (str(h, 'id')) ids.push(str(h, 'id')); } else {
          for (const g of objects(h, 'prevented_by')) if (str(g, 'id') && !ids.includes(str(g, 'id'))) ids.push(str(g, 'id'));
        }
      }
      if (ids.length === 0) {
        void vscode.window.showInformationMessage(tr(fr, 'No recorded item to remove. Use Add to create one.', 'Aucun élément enregistré à supprimer. Utilise Ajouter pour en créer un.'));
        return;
      }
      const picked = await vscode.window.showQuickPick(ids, { placeHolder: purpose === 'hazard remove'
        ? tr(fr, 'Select the hazard to remove:', 'Choisis le hazard à supprimer :') : tr(fr, 'Select the invariant to remove:', "Choisis l'invariant à supprimer :") });
      if (picked) this.openCommand(purpose, { id: itemId(component, picked) }, component);
    }
  }

  private async taskPrompt(component: string, task: string): Promise<void> {
    let args: string[];
    try {
      args = promptCommand(component, task);
    } catch {
      args = ['prompt', component]; // a task id that is not a number: the whole card
    }
    this.toOpenEditor({ type: 'running', target: 'data', args: ['archvow', ...args].join(' ') });
    let result = await this.cli.run(args, undefined, true);
    if (args.includes('--task') && needsFullCard(result.exitCode, result.stdout + result.stderr)) {
      result = await this.cli.run(['prompt', component], undefined, true);
    }
    this.sendResult('data', 'prompt', result);
  }

  private async readVow(component: string, from: From): Promise<void> {
    const file = path.join(this.root, '.archvow', 'components', component, 'spec.vow');
    try {
      this.state.vows[component] = fs.readFileSync(file, 'utf8');
    } catch {
      this.state.vows[component] = '';
    }
    let card = '';  // the Spec view shows the card the AI implements and the vow it is written from, side by side
    try {
      card = fs.readFileSync(path.join(this.root, '.archvow', 'components', component, 'spec.md'), 'utf8');
    } catch {
      card = '';
    }
    this.reply(from, { type: 'vow', component, text: this.state.vows[component], card });
  }

  /** The template catalog for init: archvow's own (installed) when it answers, else the bundled one. */
  private async loadCatalog(template: string, force: boolean, from: From): Promise<void> {
    const post = (message: Json): void => this.reply(from, message);
    const fr = this.state.fr;
    let scope = '';
    try {
      if (template && fs.statSync(path.join(path.resolve(template), 'template.yaml')).isFile()) scope = path.resolve(template);
    } catch { /* a template name */ }
    if (!force && this.liveCatalog && this.liveCatalog.scope === scope) {
      post({ type: 'catalog', data: this.liveCatalog.data, source: tr(fr, 'Loaded from installed archvow.', 'Chargé depuis archvow installé.') });
      return;
    }
    post({ type: 'catalog', data: this.bundledCatalog, source: tr(fr, `Built-in archvow ${this.known} templates · Checking installed catalog…`,
      `Modèles intégrés archvow ${this.known} · Vérification du catalogue installé…`) });
    // outside any project, so a project's own templates do not shadow the installed ones
    const neutral = new ArchvowCli(os.tmpdir(), () => this.cliSettings(), (line) => this.log.info(line),
      scope ? { ARCHVOW_TEMPLATES: [process.env.ARCHVOW_TEMPLATES, path.dirname(scope)].filter((x) => x).join(path.delimiter) } : {}, false);
    const result = await neutral.run(['catalog', '--json']);
    const data = parse(result.stdout);
    if (result.exitCode === 0 && data !== null && objects(data, 'templates').length > 0) {
      this.liveCatalog = { scope, data };
      post({ type: 'catalog', data, source: tr(fr, 'Loaded from installed archvow.', 'Chargé depuis archvow installé.') });
    } else {
      post({ type: 'catalog', data: this.bundledCatalog, source: tr(fr, `Built-in archvow ${this.known} catalog (offline). Install/configure archvow to load company templates.`,
        `Catalogue archvow ${this.known} intégré (hors ligne). Configure archvow pour charger les modèles d'entreprise.`) });
    }
  }

  // ------------------------------------------------------------------ opening files

  async openSpec(component: string): Promise<void> {
    const dir = path.join(this.root, '.archvow', 'components', component);
    for (const name of ['spec.md', 'spec.vow']) {
      if (fs.existsSync(path.join(dir, name))) return this.openFile(path.join(dir, name), 0, '');
    }
    void vscode.window.showInformationMessage('Spec not found / Spec introuvable : ' + component);
  }

  async openCode(component: string): Promise<void> {
    for (const container of objects(this.state.spec, 'containers')) {
      const entry = objects(container, 'components').find((c) => str(c, 'id') === component);
      if (!entry) continue;
      const module = str(entry, 'module');
      const parent = str(container, 'path');
      const prefix = parent ? parent.split('/')[0] : 'src';
      const candidates = [path.join(prefix, module.split('.').join('/') + '.py'), ...moduleFiles(module), path.join(parent, component + '.py')];
      for (const candidate of candidates) {
        if (fs.existsSync(path.join(this.root, candidate))) return this.openFile(path.join(this.root, candidate), 0, '');
      }
      void vscode.window.showInformationMessage(`Code not found / Code introuvable pour ${component}\nModule : ${module}`);
      return;
    }
    void vscode.window.showInformationMessage('Code location unknown / Emplacement du code inconnu pour ' + component);
  }

  private async openFile(file: string, line: number, missing: string): Promise<void> {
    if (!fs.existsSync(file)) {
      if (missing) void vscode.window.showInformationMessage(missing);
      return;
    }
    // in the editor, as an ordinary tab next to the archvow tab (the same group)
    const position = new vscode.Position(Math.max(0, line), 0);
    await vscode.window.showTextDocument(vscode.Uri.file(file), { selection: new vscode.Range(position, position),
      viewColumn: this.panel?.viewColumn ?? vscode.ViewColumn.Active });
  }

  /** A file a diagnostic names, only inside the project. */
  private async openProjectFile(relative: string, line: number): Promise<void> {
    const file = path.resolve(this.root, relative);
    if (!relative || !file.startsWith(this.root + path.sep) || !fs.existsSync(file)) {
      this.toNav({ type: 'showChecks' });
      return;
    }
    await this.openFile(file, Math.max(0, line - 1), '');
  }

  private async openChecks(): Promise<void> {
    const fr = this.state.fr;
    const file = path.join(this.root, '.archvow', 'spec.md');
    if (!fs.existsSync(file)) {
      void vscode.window.showInformationMessage(tr(fr, 'The generated project reference .archvow/spec.md is not available. Run archvow docs to generate it.',
        "Le document projet .archvow/spec.md n'est pas disponible. Exécute archvow docs pour le générer."));
      return;
    }
    const text = fs.readFileSync(file, 'utf8');
    let at = text.indexOf('## Checks');
    if (at < 0) at = text.indexOf('## Contrôles');
    await this.openFile(file, at < 0 ? 0 : text.substring(0, at).split('\n').length - 1, '');
  }

  /** A check code: its row in the project's spec.md, else archvow's own explanation. */
  private async openRule(candidate: string): Promise<void> {
    const fr = this.state.fr;
    const code = candidate.trim().toUpperCase();
    if (!/^[A-Z]{2,12}[0-9]{3}$/.test(code)) return;
    for (const relative of ['.archvow/spec.md', 'docs/rules.fr.md', 'docs/rules.md', 'docs/project.md', 'project.md', '.archvow/README.md']) {
      const file = path.join(this.root, relative);
      try {
        if (!fs.statSync(file).isFile() || fs.statSync(file).size > 2_000_000) continue;
      } catch {
        continue;
      }
      const lines = fs.readFileSync(file, 'utf8').split('\n');
      const row = lines.findIndex((l) => new RegExp(`^\\s*\\|\\s*${code}\\s*\\|`).test(l) || new RegExp(`^#{1,6}\\s+${code}(\\s|$)`).test(l));
      if (row >= 0) return this.openFile(file, row, '');
    }
    const explanation = ruleExplanation(fs.readFileSync(path.join(this.resources, fr ? 'rules.fr.md' : 'rules.md'), 'utf8'), code);
    void vscode.window.showInformationMessage(code + '\n\n' + (explanation || tr(fr, 'No bundled definition for this project-specific code.',
      'Aucune définition intégrée pour ce code propre au projet.')) + '\n\n' + tr(fr, 'Project references: .archvow/spec.md (Checks); generate them with archvow docs.',
      'Référence du projet : .archvow/spec.md (Contrôles) ; génère-la avec archvow docs.'), { modal: true });
  }

  /** archvow's explanation of a check code, from its docs/rules (bundled). */
  explain(code: string): string {
    return ruleExplanation(fs.readFileSync(path.join(this.resources, this.state.fr ? 'rules.fr.md' : 'rules.md'), 'utf8'), code);
  }
}

function parse(text: string): Json | null {
  try {
    const value = JSON.parse(text);
    return value !== null && typeof value === 'object' ? (value as Json) : null;
  } catch {
    return null;
  }
}

function stringList(value: unknown): string[] {
  return Array.isArray(value) ? value.map(String) : [];
}
