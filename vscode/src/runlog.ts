// The session log: what the extension asked archvow, in the "archvow" output channel. A person's
// action is one line with its whole command, its result and archvow's answer; a refresh's reads
// are one summary line (each read at debug level); a failure is an error with archvow's message.
// Pure: the channel is given, so the tests read the lines without VS Code.
import { CliResult, display } from './cli';
import { Command } from './grammar';

/** Where the lines go: VS Code's LogOutputChannel (it adds the time, keeps the levels, rotates). */
export interface LogSink {
  info(line: string): void;
  debug(line: string): void;
  warn(line: string): void;
  error(line: string): void;
}

/** A call longer than this is marked slow. */
export const SLOW_MS = 1000;
/** Lines kept for "Copy the log for support". */
const KEPT = 300;

const outcome = (r: CliResult): string => (r.startError !== undefined ? 'did not start' : r.timedOut ? 'stopped' : `exit ${r.exitCode}`);
const failed = (r: CliResult): boolean => r.startError !== undefined || r.timedOut || r.exitCode !== 0;
const seconds = (ms: number): string => (ms < SLOW_MS ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`);

/** archvow's answer to an action: its last line, when it is text (not JSON). */
export function resultLine(r: CliResult): string {
  const text = r.stdout.trim();
  if (text === '' || text.startsWith('{') || text.startsWith('[')) return '';
  const last = text.split('\n').map((l) => l.trim()).filter((l) => l !== '').pop() ?? '';
  return last.length > 160 ? `${last.slice(0, 157)}…` : last;
}

/** What archvow said when it failed: the last lines of stderr (else stdout), or why it did not start. */
export function failureText(r: CliResult): string {
  if (r.startError !== undefined) return r.startError.trim().split('\n')[0];
  if (r.timedOut) return 'stopped: archvow did not answer within the time-out (archvow.timeoutSeconds)';
  return (r.stderr.trim() || r.stdout.trim()).split('\n').slice(-3).join(' | ');
}

/** Where each command keeps a person's own words (an answer, a rationale...): hidden for support. */
const OWN_WORDS: Record<string, number> = {
  'decision make': 4, 'decision propose': 4, 'decision add': 4, 'question add': 3, 'assumption add': 3,
  'hazard add': 5, 'hazard dismiss': 4, 'invariant add': 3, 'rule add': 3,
};

/** The command line with a person's own words hidden (answers, rationales, statements): for support. */
export function masked(args: string[]): string[] {
  const out = [...args];
  const at = OWN_WORDS[`${out[0]} ${out[1]}`];
  if (at !== undefined && out.length > at) out[at] = '<text>';
  for (let i = 0; i < out.length - 1; i++) {
    if (['--answer', '--rationale', '--statement', '--text', '--description', '-d'].includes(out[i])) out[i + 1] = '<text>';
  }
  return out;
}

interface Kept {
  time: string;
  line: string;
  /** The same line with the answers hidden. */
  hidden: string;
}

export class RunLog {
  private kept: Kept[] = [];
  private headerLines: string[] = [];
  private refresh: { what: string; reads: number; failed: number; started: number } | null = null;

  constructor(private readonly sink: LogSink, private readonly now: () => Date = () => new Date()) {}

  /** Once per session: the plugin, archvow, the Python used, the project, the IDE. */
  header(fields: Record<string, string>): void {
    const line = Object.entries(fields).filter(([, v]) => v !== '').map(([k, v]) => `${k} ${v}`).join('  ·  ');
    this.headerLines = [line];
    this.sink.info(`Session: ${line}`);
  }

  /** A refresh starts: its reads are counted, and told in one line at the end. */
  refreshStarted(what = 'Refresh'): void {
    this.refresh = { what, reads: 0, failed: 0, started: this.now().getTime() };
  }

  refreshEnded(): void {
    const r = this.refresh;
    this.refresh = null;
    if (r === null || r.reads === 0) return;
    const ms = this.now().getTime() - r.started;
    const line = `${r.what}  ·  ${r.reads} read${r.reads > 1 ? 's' : ''}  ·  ${seconds(ms)}${r.failed ? `  ·  ${r.failed} failed` : ''}`;
    this.keep(line);
    if (r.failed) this.sink.warn(line);
    else this.sink.info(line);
  }

  /** One archvow call: `action` when a person asked for it (it goes first in the queue). */
  record(r: CliResult, action: boolean): void {
    const slow = r.millis > SLOW_MS ? '  ·  slow' : '';
    const call = `${display(r.args)}  ·  ${outcome(r)}  ·  ${seconds(r.millis)}${slow}`;
    if (failed(r) && !(r.args[0] === 'check' && r.exitCode === 1 && r.startError === undefined && !r.timedOut)) {
      // `check` exits 1 when it finds errors: that is an answer, not a failure
      const line = `✖ ${call}  -  ${failureText(r)}`;
      this.keep(line, line.replace(display(r.args), display(masked(r.args))));
      this.sink.error(line);
      if (!action && this.refresh) {
        this.refresh.reads += 1;
        this.refresh.failed += 1;
      }
      return;
    }
    if (action) {
      const said = resultLine(r);
      const line = `▶ ${call}${said ? `  -  ${said}` : ''}`;
      this.keep(line, line.replace(display(r.args), display(masked(r.args))));
      this.sink.info(line);
      return;
    }
    if (this.refresh) this.refresh.reads += 1;
    this.sink.debug(call);
    if (slow) this.keep(call);
  }

  /** The header and the last lines, with the time: what a person pastes into a support request. */
  supportText(hideAnswers: boolean): string {
    const lines = this.kept.map((k) => `${k.time} ${hideAnswers ? k.hidden : k.line}`);
    return [...this.headerLines.map((h) => `Session: ${h}`), ...lines].join('\n') + '\n';
  }

  private keep(line: string, hidden = line): void {
    const t = this.now();
    const time = [t.getHours(), t.getMinutes(), t.getSeconds()].map((n) => String(n).padStart(2, '0')).join(':');
    this.kept.push({ time, line, hidden });
    if (this.kept.length > KEPT) this.kept.splice(0, this.kept.length - KEPT);
  }
}

/**
 * The one component an action changes, when its command names it (decision make <component>, rule
 * add <component>...) and it exists: the extension then refreshes only that one. null for the
 * component commands themselves (add, remove, rename change the architecture) and for anything else.
 */
export function touchedComponent(command: Command | undefined, args: string[], known: string[]): string | null {
  if (command === undefined || command.path[0] === 'component' || command.path[0] === 'init') return null;
  const positional = command.parameters.filter((p) => p.flags.length === 0);
  const at = positional.findIndex((p) => p.name === 'component');
  const id = at < 0 ? undefined : args[command.path.length + at];
  return id !== undefined && known.includes(id) ? id : null;
}
