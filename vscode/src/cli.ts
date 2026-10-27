// Runs the archvow CLI: no shell, in the project folder, one command at a time (the next one waits),
// stdout and stderr apart, stopped after the time-out. The extension never writes a vow itself.
import { spawn } from 'node:child_process';

export interface CliSettings {
  /** The archvow command: a name on the PATH or a path. */
  executable: string;
  /** A Python with archvow installed: `<python> -m archvow` (wins over executable). */
  python: string;
  timeoutSeconds: number;
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

/** The program and its arguments for one archvow command. */
export function commandLine(settings: CliSettings, args: string[]): { program: string; args: string[] } {
  const python = settings.python.trim();
  if (python !== '') {
    return { program: python, args: ['-m', 'archvow', ...args] };
  }
  return { program: settings.executable.trim() || 'archvow', args };
}

/** How a command line is shown to a person (in a confirmation, in the log). */
export function display(args: string[]): string {
  return ['archvow', ...args].map((a) => (/^[A-Za-z0-9_@%+=:,./-]+$/.test(a) ? a : JSON.stringify(a))).join(' ');
}

interface Job {
  args: string[];
  stdin?: string;
  resolve: (result: CliResult) => void;
}

export class ArchvowCli {
  private readonly waiting: Job[] = [];
  private busy = false;

  constructor(
    private readonly root: string,
    private readonly settings: () => CliSettings,
    private readonly log: (line: string) => void = () => undefined,
    /** Variables added to the environment (ARCHVOW_TEMPLATES for a template folder). */
    private readonly extraEnv: Record<string, string> = {},
  ) {}

  /**
   * Runs `archvow <args>` after the commands already waiting; `first` puts it before them (what a
   * person asked for goes before the workspace's own background reads).
   */
  run(args: string[], stdin?: string, first = false): Promise<CliResult> {
    return new Promise((resolve) => {
      const job: Job = { args, stdin, resolve };
      if (first) this.waiting.unshift(job);
      else this.waiting.push(job);
      void this.pump();
    });
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
        job.resolve(await this.runNow(job.args, job.stdin));
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

  private runNow(args: string[], stdin?: string): Promise<CliResult> {
    const settings = this.settings();
    const { program, args: argv } = commandLine(settings, args);
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
        this.log(`${display(args)}  ·  ${done.startError ? 'did not start' : done.timedOut ? 'stopped' : 'exit ' + done.exitCode}  ·  ${done.millis} ms`);
        resolve(done);
      };
      const child = spawn(program, argv, {
        cwd: this.root,
        shell: false,
        windowsHide: true,
        env: { ...process.env, ...this.extraEnv, NO_COLOR: '1', PYTHONIOENCODING: 'utf-8' },
      });
      const timer = setTimeout(() => {
        timedOut = true;
        child.kill();
      }, settings.timeoutSeconds * 1000);
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
