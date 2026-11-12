// Runs the archvow CLI: no shell, in the project folder, one command at a time (the next one waits),
// stdout and stderr apart, stopped after the time-out. The extension never writes a vow itself.
import { spawn } from 'node:child_process';
import * as fs from 'node:fs';
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

/** WSLENV handing NO_COLOR, PYTHONIOENCODING and ARCHVOW_LOG to WSL. */
export function wslenv(previous: string | undefined): string {
  const names = (previous || '').split(':').filter((x) => x);
  for (const wanted of ['NO_COLOR', 'PYTHONIOENCODING', 'ARCHVOW_LOG']) {
    if (!names.some((n) => n.split('/')[0] === wanted.split('/')[0])) names.push(wanted);
  }
  return names.join(':');
}

/** Where archvow is looked for, in order: VS Code's interpreter, archvow.python, the folder's .venv (0.25.2),
 *  archvow.executable, then archvow where pip and pipx put it (0.25.2) - as IntelliJ's ArchvowProcessCommand. */
export interface Candidate { source: 'ide' | 'python' | 'venv' | 'executable' | 'found'; executable: string; python: string }

export function candidates(settings: CliSettings, found: Candidate[] = []): Candidate[] {
  const out: Candidate[] = [];
  const ide = (settings.interpreter || '').trim();
  const python = settings.python.trim();
  const program = (c: Candidate): string => c.python || c.executable;
  if (settings.useProjectInterpreter !== false && ide) out.push({ source: 'ide', executable: '', python: ide });
  if (python && !out.some((c) => c.python === python)) out.push({ source: 'python', executable: '', python });
  for (const c of found) if (c.source === 'venv' && !out.some((o) => program(o) === program(c))) out.push(c);
  out.push({ source: 'executable', executable: settings.executable.trim() || 'archvow', python: '' });
  for (const c of found) if (c.source !== 'venv' && !out.some((o) => program(o) === program(c))) out.push(c);
  return out;
}

/** Only what exists: the Pythons of the folder's .venv or venv, and the archvow executable in ~/.local/bin (pip
 *  --user, pipx), /opt/homebrew/bin, /usr/local/bin and pyenv's shims - an editor started from the Dock or a
 *  desktop menu may not see the shell's PATH. */
export function foundCandidates(root: string, home: string, windows: boolean, exists: (p: string) => boolean): Candidate[] {
  const out: Candidate[] = [];
  const sep = windows ? '\\' : '/';
  const base = root.replace(/[\\/]+$/, '');
  if (base) {
    const venvs = windows ? ['.venv', 'venv'].map((n) => [base, n, 'Scripts', 'python.exe'].join(sep)) : [];
    for (const v of [...venvs, ...['.venv', 'venv'].map((n) => [base, n, 'bin', 'python'].join(sep))]) if (exists(v)) out.push({ source: 'venv', executable: '', python: v });
  }
  const places = windows
    ? [process.env.APPDATA ? `${process.env.APPDATA}\\Python\\Scripts\\archvow.exe` : '', home ? `${home}\\.local\\bin\\archvow.exe` : '']
    : [home ? `${home}/.local/bin/archvow` : '', home ? `${home}/.pyenv/shims/archvow` : '', '/opt/homebrew/bin/archvow', '/usr/local/bin/archvow'];
  for (const p of places) if (p && exists(p)) out.push({ source: 'found', executable: p, python: '' });
  return out;
}

export interface CliResult {
  args: string[];
  exitCode: number;
  stdout: string;
  /** What archvow said on stderr, without its log lines (they are in `log`). */
  stderr: string;
  timedOut: boolean;
  /** The program could not start (not installed, wrong path). */
  startError?: string;
  millis: number;
  /** archvow's own log lines (ARCHVOW_LOG=info, archvow 1.23): for the session log, never shown as its answer. */
  log?: string[];
  /** With --json, the error archvow printed as an object on stdout (archvow 1.23); stdout is then ''. */
  error?: CliError;
}

/** archvow's error envelope: `{"error": {"type", "message", "hint", "exit"}}` on stdout with --json. */
export interface CliError {
  type: string;
  message: string;
  hint: string;
  exit: number;
}

/** A line of archvow's text log on stderr: `archvow 20:12:37.383 info    start show ...`. */
const LOG_LINE = /^archvow \d\d:\d\d:\d\d\.\d{3} (debug|info|warning|error|critical)\b/;

/**
 * archvow's log lines apart from what it said: a log line, and the traceback right after one (debug
 * only: its indented lines and the exception's line that ends it).
 */
export function splitLog(stderr: string): { log: string[]; said: string } {
  const log: string[] = [];
  const said: string[] = [];
  let traceback = false;
  for (const line of stderr.split(/\r?\n/)) {
    if (LOG_LINE.test(line)) {
      log.push(line);
      traceback = false;
    } else if (log.length && (line === 'Traceback (most recent call last):' || (traceback && /^\s/.test(line)))) {
      log.push(line);
      traceback = true;
    } else if (traceback && line.trim() !== '') {
      log.push(line);  // "ValueError: ...": the end of the traceback
      traceback = false;
    } else {
      said.push(line);
      traceback = false;
    }
  }
  return { log, said: said.join('\n') };
}

/** The error envelope on stdout, or null (any other output, a list, a broken JSON). */
export function errorOf(stdout: string): CliError | null {
  const text = stdout.trim();
  if (!text.startsWith('{')) return null;
  try {
    const parsed = JSON.parse(text) as Record<string, unknown>;
    const e = parsed.error;
    if (e === null || typeof e !== 'object' || Array.isArray(e)) return null;
    const o = e as Record<string, unknown>;
    if (typeof o.message !== 'string') return null;
    return { type: String(o.type ?? ''), message: o.message, hint: typeof o.hint === 'string' ? o.hint : '', exit: Number(o.exit ?? 2) };
  } catch {
    return null;
  }
}

/**
 * What a person reads when a command failed: why it did not start or stopped, archvow's error and its hint
 * (the envelope), else its last `lines` lines on stderr (else stdout). One rule for every failure message.
 */
export function failure(r: Pick<CliResult, 'exitCode' | 'stdout' | 'stderr' | 'timedOut' | 'startError' | 'error'>, lines = 6): string {
  if (r.startError !== undefined) return r.startError.trim();
  if (r.timedOut) return 'stopped: archvow did not answer within the time-out (archvow.timeoutSeconds)';
  if (r.error) return r.error.hint ? `${r.error.message}\n${r.error.hint}` : r.error.message;
  return (r.stderr.trim() || r.stdout.trim()).split('\n').slice(-lines).join('\n');
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
    /** Variables added to the environment. */
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
    const list = candidates(settings, this.inProject ? foundCandidates(this.root, os.homedir(), this.windows, (p) => {
      try { fs.accessSync(p, fs.constants.X_OK); return true; } catch { return false; }
    }) : []);
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
      const where = { ide: "VS Code's Python interpreter", python: 'archvow.python', venv: "the folder's virtual environment",
        executable: 'archvow.executable', found: 'archvow where pip or pipx install it' }[c.source];
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
    if (result.error === undefined) {
      try {
        return { data: JSON.parse(result.stdout) as T, result };
      } catch {
        // not JSON: the message below
      }
    }
    const said = failure(result);
    throw new Error(`${display(args)} failed (exit ${result.exitCode})${said ? ': ' + said : ''}`);
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
        // archvow's log lines go to `log`, its error envelope to `error`: stdout and stderr say what they said before
        const { log, said } = splitLog(done.stderr);
        if (log.length) {
          done.log = log;
          done.stderr = said;
        }
        const error = done.exitCode !== 0 ? errorOf(done.stdout) : null;
        if (error !== null) {
          done.error = error;
          done.stdout = '';
        }
        resolve(done);
      };
      // ARCHVOW_LOG: what archvow did, for the session log (a level the person set is kept)
      const env: NodeJS.ProcessEnv = { ...process.env, ...this.extraEnv, NO_COLOR: '1', PYTHONIOENCODING: 'utf-8',
        ARCHVOW_LOG: process.env.ARCHVOW_LOG || 'info' };
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
