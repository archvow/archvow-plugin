// Runs the archvow CLI: no shell, in the project folder, one command at a time (the next one waits),
// stdout and stderr apart, stopped after the time-out. The extension never writes a vow itself.
import { spawn } from 'node:child_process';
import * as os from 'node:os';

export interface CliSettings {
  /** The archvow command: a name on the PATH or a path. */
  executable: string;
  /** A Python with archvow installed: `<python> -m archvow` (wins over executable). */
  python: string;
  timeoutSeconds: number;
  /** The Python interpreter VS Code uses for the project (the Python extension's), looked at first (0.11.2). */
  interpreter?: string;
  /** archvow.useProjectInterpreter: look in `interpreter` first (default true). */
  useProjectInterpreter?: boolean;
}

// ---------------------------------------------------------------- archvow in WSL, VS Code on Windows
// As the IntelliJ plugin (ArchvowWsl): a Linux program cannot start through its Windows path
// (\\wsl.localhost\Ubuntu\home\...\python3); it runs through wsl.exe with Linux paths, no shell:
// wsl.exe -d Ubuntu --cd /home/marc/proj --exec /home/marc/.venv/bin/python3 -m archvow ...

/** A program or a folder inside WSL: its distribution ('' = the default one) and its Linux path. */
export interface WslTarget { distro: string; path: string }

/** \\wsl.localhost\<distro>\..., \\wsl$\<distro>\... (or with /), wsl://<distro>/...: the WSL target, else null. */
export function wslParse(value: string): WslTarget | null {
  const p = (value || '').trim().replace(/\\/g, '/');
  const unc = /^\/\/wsl(?:\.localhost|\$)\/([^/]+)(\/.*)?$/i.exec(p);
  if (unc) return { distro: unc[1], path: unc[2] || '/' };
  const uri = /^wsl:\/\/([^/]*)(\/.*)?$/i.exec(p);
  if (uri) return { distro: uri[1], path: uri[2] || '/' };
  return null;
}

/** The program to run through wsl.exe on Windows: a WSL path, or a Linux path (the default distribution). */
export function wslProgram(program: string, windows: boolean): WslTarget | null {
  if (!windows) return null;
  const target = wslParse(program);
  if (target) return target;
  const p = program.trim();
  return p.startsWith('/') && !p.startsWith('//') ? { distro: '', path: p } : null;
}

/** A Windows path as WSL sees it: \\wsl.localhost\Ubuntu\home\x -> /home/x, C:\x -> /mnt/c/x; null for another. */
export function linuxPath(value: string): string | null {
  const target = wslParse(value);
  if (target) return target.path;
  const drive = /^([A-Za-z]):[\\/](.*)$/.exec((value || '').trim());
  if (drive) {
    const rest = drive[2].replace(/\\/g, '/').replace(/\/+$/, '');
    return `/mnt/${drive[1].toLowerCase()}${rest ? '/' + rest : ''}`;
  }
  const p = (value || '').trim();
  return p.startsWith('/') && !p.startsWith('//') ? p : null;
}

/** An argument for archvow in WSL: a Windows path becomes its Linux path, anything else stays. */
export function wslArgument(arg: string): string {
  const a = arg.trim();
  if (/^[A-Za-z]:[\\/]/.test(a) || (/^(\\\\|\/\/)/.test(a) && wslParse(a))) return linuxPath(a) ?? arg;
  return arg;
}

/** WSLENV handing NO_COLOR, PYTHONIOENCODING and ARCHVOW_TEMPLATES (a folder list, as Linux paths) to WSL. */
export function wslenv(previous: string | undefined): string {
  const names = (previous || '').split(':').filter((x) => x);
  for (const wanted of ['NO_COLOR', 'PYTHONIOENCODING', 'ARCHVOW_TEMPLATES/l']) {
    if (!names.some((n) => n.split('/')[0] === wanted.split('/')[0])) names.push(wanted);
  }
  return names.join(':');
}

/** Where archvow is looked for, in order: VS Code's interpreter, archvow.python, archvow.executable. */
export interface Candidate { source: 'ide' | 'python' | 'executable'; executable: string; python: string }

export function candidates(settings: CliSettings): Candidate[] {
  const out: Candidate[] = [];
  const ide = (settings.interpreter || '').trim();
  const python = settings.python.trim();
  if (settings.useProjectInterpreter !== false && ide) out.push({ source: 'ide', executable: '', python: ide });
  if (python && !out.some((c) => c.python === python)) out.push({ source: 'python', executable: '', python });
  out.push({ source: 'executable', executable: settings.executable.trim() || 'archvow', python: '' });
  return out;
}

export interface CliResult {
  args: string[];
  exitCode: number;
  stdout: string;
  stderr: string;
  timedOut: boolean;
  /** The program could not start (not installed, wrong path). */
  startError?: string;
  millis: number;
}

/**
 * The program and its arguments for one archvow command (archvow.python wins over archvow.executable);
 * on Windows, a program in WSL runs through wsl.exe in `root` as a Linux path (`root` null: no project).
 */
export function commandLine(settings: CliSettings, args: string[], root: string | null = null,
  windows = process.platform === 'win32'): { program: string; args: string[] } {
  const python = settings.python.trim();
  const program = python !== '' ? python : settings.executable.trim() || 'archvow';
  const module = python !== '' ? ['-m', 'archvow'] : [];
  const wsl = wslProgram(program, windows);
  if (wsl) {
    const folder = root === null ? '~' : linuxPath(root);
    if (folder === null) throw new Error(`WSL cannot reach the project folder: ${root}`);
    return { program: 'wsl.exe', args: [...(wsl.distro ? ['-d', wsl.distro] : []), '--cd', folder, '--exec', wsl.path, ...module, ...args.map(wslArgument)] };
  }
  return { program, args: [...module, ...args] };
}

/** How a command line is shown to a person (in a confirmation, in the log). */
export function display(args: string[]): string {
  return ['archvow', ...args].map((a) => (/^[A-Za-z0-9_@%+=:,./-]+$/.test(a) ? a : JSON.stringify(a))).join(' ');
}

interface Job {
  args: string[];
  stdin?: string;
  /** A person asked for it (it went first): an action in the session log, not a read. */
  first: boolean;
  resolve: (result: CliResult) => void;
}

export class ArchvowCli {
  private readonly waiting: Job[] = [];
  private busy = false;

  constructor(
    private readonly root: string,
    private readonly settings: () => CliSettings,
    /** Each call, once it is done: the session log (`action` when a person asked for it). */
    private readonly onCall: (result: CliResult, action: boolean) => void = () => undefined,
    /** Variables added to the environment (ARCHVOW_TEMPLATES for a template folder). */
    private readonly extraEnv: Record<string, string> = {},
    /** False for a command outside the project (catalog): in WSL it runs from the home folder. */
    private readonly inProject = true,
    private readonly windows = process.platform === 'win32',
  ) {}

  /** The candidate that answered --version, for these settings (their JSON). */
  private chosen: { signature: string; candidate: Candidate } | undefined;
  /** What the last search said when no candidate answered. */
  private notFound = '';

  /** The first candidate that answers `--version` (remembered while the settings stay the same), or null. */
  private async choose(settings: CliSettings): Promise<Candidate | null> {
    const list = candidates(settings);
    const signature = JSON.stringify(list);
    if (this.chosen?.signature === signature) return this.chosen.candidate;
    const lines: string[] = [];
    for (const c of list) {
      // WSL may have to start its machine first: a longer wait through wsl.exe
      const wait = wslProgram(c.python || c.executable, this.windows) ? 60 : 10;
      const probe = await this.spawnOnce({ ...settings, executable: c.executable, python: c.python }, ['--version'], undefined, wait);
      if (probe.startError === undefined && probe.exitCode === 0 && /archvow/i.test(probe.stdout + probe.stderr)) {
        this.chosen = { signature, candidate: c };
        this.notFound = '';
        return c;
      }
      const said = probe.startError ?? (probe.timedOut ? `no answer within ${wait} s`
        : `exit ${probe.exitCode}${(probe.stderr || probe.stdout).trim() ? ': ' + (probe.stderr || probe.stdout).trim().split('\n').pop() : ''}`);
      const where = c.source === 'ide' ? "VS Code's Python interpreter" : c.source === 'python' ? 'archvow.python' : 'archvow.executable';
      lines.push(`- ${where} (${c.python || c.executable}): ${said}`);
    }
    this.notFound = `archvow not found. Tried, in order:\n${lines.join('\n')}\n`
      + 'Install archvow in the project\'s Python interpreter, or set archvow.python to a Python that has it '
      + '(a Python in WSL: \\\\wsl.localhost\\Ubuntu\\home\\you\\.venv\\bin\\python3).';
    return null;
  }

  /**
   * Runs `archvow <args>` after the commands already waiting; `first` puts it before them (what a
   * person asked for goes before the workspace's own background reads).
   */
  run(args: string[], stdin?: string, first = false): Promise<CliResult> {
    return new Promise((resolve) => {
      const job: Job = { args, stdin, first, resolve };
      if (first) this.waiting.unshift(job);
      else this.waiting.push(job);
      void this.pump();
    });
  }

  /** The program archvow runs with (the Python, or the archvow executable), once found: for the session log. */
  get program(): string {
    const c = this.chosen?.candidate;
    return c === undefined ? '' : c.python || c.executable;
  }

  /** Whether a command runs or waits. */
  get running(): boolean {
    return this.busy || this.waiting.length > 0;
  }

  private async pump(): Promise<void> {
    if (this.busy) return;
    this.busy = true;
    try {
      for (let job = this.waiting.shift(); job !== undefined; job = this.waiting.shift()) {
        const result = await this.runNow(job.args, job.stdin);
        this.onCall(result, job.first);
        job.resolve(result);
      }
    } finally {
      this.busy = false;
    }
  }

  /** Runs a --json command and reads its stdout; throws with archvow's own message when it cannot. */
  async json<T>(args: string[], stdin?: string, first = false): Promise<{ data: T; result: CliResult }> {
    const result = await this.run(args, stdin, first);
    if (result.startError !== undefined) throw new Error(result.startError);
    if (result.timedOut) throw new Error(`${display(args)}: stopped after ${this.settings().timeoutSeconds} s`);
    try {
      return { data: JSON.parse(result.stdout) as T, result };
    } catch {
      const said = (result.stderr || result.stdout).trim().split('\n').slice(-6).join('\n');
      throw new Error(`${display(args)} failed (exit ${result.exitCode})${said ? ': ' + said : ''}`);
    }
  }

  private async runNow(args: string[], stdin?: string): Promise<CliResult> {
    const settings = this.settings();
    const chosen = await this.choose(settings);
    if (chosen === null) {
      return { args, exitCode: -1, stdout: '', stderr: '', timedOut: false, startError: this.notFound, millis: 0 };
    }
    return this.spawnOnce({ ...settings, executable: chosen.executable, python: chosen.python }, args, stdin, settings.timeoutSeconds);
  }

  private spawnOnce(settings: CliSettings, args: string[], stdin: string | undefined, timeoutSeconds: number): Promise<CliResult> {
    let program: string;
    let argv: string[];
    try {
      ({ program, args: argv } = commandLine(settings, args, this.inProject ? this.root : null, this.windows));
    } catch (error) {
      return Promise.resolve({ args, exitCode: -1, stdout: '', stderr: '', timedOut: false, startError: String((error as Error).message), millis: 0 });
    }
    const wsl = program === 'wsl.exe';
    const started = Date.now();
    return new Promise((resolve) => {
      const stdout: Buffer[] = [];
      const stderr: Buffer[] = [];
      let timedOut = false;
      let settled = false;
      const finish = (result: Omit<CliResult, 'args' | 'millis'>): void => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        const done: CliResult = { ...result, args, millis: Date.now() - started };
        resolve(done);
      };
      const env: NodeJS.ProcessEnv = { ...process.env, ...this.extraEnv, NO_COLOR: '1', PYTHONIOENCODING: 'utf-8' };
      if (wsl) env.WSLENV = wslenv(process.env.WSLENV);
      const child = spawn(program, argv, {
        // through wsl.exe the Linux folder is --cd; wsl.exe itself starts from the home folder
        cwd: wsl ? os.homedir() : this.root,
        shell: false,
        windowsHide: true,
        env,
      });
      const timer = setTimeout(() => {
        timedOut = true;
        child.kill();
      }, timeoutSeconds * 1000);
      child.stdout.on('data', (chunk: Buffer) => stdout.push(chunk));
      child.stderr.on('data', (chunk: Buffer) => stderr.push(chunk));
      child.on('error', (error: NodeJS.ErrnoException) => {
        finish({
          exitCode: -1, stdout: '', stderr: '', timedOut: false,
          startError: error.code === 'ENOENT'
            ? `archvow could not start: "${program}" was not found. Set archvow.executable, or archvow.python to a Python that has archvow installed.`
            : `archvow could not start: ${error.message}`,
        });
      });
      child.on('close', (code) => {
        finish({
          exitCode: code ?? -1,
          stdout: Buffer.concat(stdout).toString('utf8'),
          stderr: Buffer.concat(stderr).toString('utf8'),
          timedOut,
        });
      });
      child.stdin.on('error', () => undefined); // the program may exit before reading stdin
      child.stdin.end(stdin ?? '');
    });
  }
}
