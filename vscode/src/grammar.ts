// The archvow commands and how a form becomes a command line: the same rules as the IntelliJ workspace
// (ArchvowGrammar, submitForm, ArchvowCommandPrefill, ArchvowInlineCommands, ArchvowTaskPrompt).
// The grammar is archvow's own parser, recorded in ../contract/cli/commands.json.
import { tr } from './i18n';

export interface Param {
  name: string;
  flags: string[];
  required: boolean;
  boolean: boolean;
  append: boolean;
  multiToken: boolean;
  optionalValue: boolean;
  choices: string[];
  help: string;
  metavar: string;
}

export interface Command {
  path: string[];
  description: string;
  json: boolean;
  parameters: Param[];
}

export const label = (command: Command): string => command.path.join(' ');
/** The long option of a parameter (`--answer`), or '' for a positional one. */
export const option = (param: Param): string => (param.flags.length ? param.flags[param.flags.length - 1] : '');

/** Parameters shown as several lines. */
export const LONG_TEXT = new Set(['description', 'answer', 'rationale', 'statement', 'question', 'text', 'words']);
/** Commands that remove something: they get --yes, after the confirmation. */
export const DESTRUCTIVE = new Set(['component remove', 'question remove', 'rule remove', 'hazard remove', 'invariant remove',
  'example remove']);
/** First words of the commands that change nothing. */
export const READ_ONLY = new Set(['show', 'catalog', 'next', 'check', 'audit', 'doctor', 'changes', 'diff', 'prompt']);

export function findCommand(commands: Command[], name: string): Command | undefined {
  return commands.find((c) => label(c) === name);
}

/** Whether a prepared command changes something (and needs a confirmation). */
export function isMutating(command: Command, args: string[]): boolean {
  const name = label(command);
  if (DESTRUCTIVE.has(name)) return true;
  if (name === 'docs') return !args.includes('--check');
  if (name === 'completion') return args.includes('--install');
  return !READ_ONLY.has(command.path[0]);
}

/** How a command line is shown in a confirmation. */
export function preview(args: string[]): string {
  return ['archvow', ...args.map((a) => (a.includes(' ') || a.includes('\n')
    ? '"' + a.replace(/\n/g, '\\n').replace(/"/g, '\\"') + '"' : a))].join(' ');
}

export type FormPlan =
  | { kind: 'run'; args: string[]; mutating: boolean; stdinFromEditor: boolean }
  | { kind: 'clarify'; component: string }
  | { kind: 'error'; message: string };

const lines = (text: string): string[] => text.split(/\r?\n/).map((s) => s.trim()).filter((s) => s !== '');

/**
 * The command line a form asks for. `values` are the fields by parameter name ("true" for a ticked
 * box); `initAnswers` the KEY=VALUE answers of the init wizard; `components` the project's ids.
 */
export function planForm(command: Command, values: Record<string, string>, fr: boolean,
  options: { initAnswers?: string[]; components?: string[]; selected?: string } = {}): FormPlan {
  const name = label(command);
  const value = (key: string): string => (values[key] ?? '').trim();
  if (name === 'specify') {
    // specify asks its questions in a terminal: the workspace opens the component's first open item
    const id = value('component') || options.selected || '';
    if (!id) return { kind: 'error', message: tr(fr, 'Select a component first.', "Sélectionne d'abord un composant.") };
    return { kind: 'clarify', component: id };
  }
  // the component is known but the rule text is a person's: never run "rule add <component>" alone
  const words = value('words');
  if (name === 'rule add' && words && lines(words).length === 1 && (options.components ?? []).includes(words)) {
    return { kind: 'error', message: tr(fr, 'Add the rule text on a second line after the component id.',
      "Ajoute le texte de la règle sur la deuxième ligne après l'identifiant du composant.") };
  }
  const args = [...command.path];
  for (const param of command.parameters) {
    let text = value(param.name);
    if (name === 'prompt' && param.name === 'task' && text) {
      const parsed = taskNumber(text);
      if (!parsed) {
        return { kind: 'error', message: tr(fr, 'Enter a positive task number, e.g. 1 or T1.',
          'Saisis un numéro de tâche positif, par exemple 1 ou T1.') };
      }
      text = parsed;
    }
    if (!text && param.required) {
      return { kind: 'error', message: tr(fr, 'Required field: ', 'Champ obligatoire : ') + param.name };
    }
    if (param.boolean) {
      if (text === 'true') args.push(option(param));
    } else if (text) {
      if (param.multiToken) {
        args.push(...lines(text));
      } else if (param.append) {
        for (const item of lines(text)) {
          if (param.flags.length) args.push(option(param));
          args.push(item);
        }
      } else {
        if (param.flags.length) args.push(option(param));
        args.push(text);
      }
    }
  }
  if (name === 'init' && options.initAnswers) {
    // the answers typed by hand (Other answers) win over the wizard's
    const typed = new Set(lines(value('answer')).map((l) => l.split('=')[0].trim()));
    for (const answer of options.initAnswers) {
      if (!typed.has(answer.split('=')[0])) args.push('--answer', answer);
    }
  }
  if (name === 'init' && !args.includes('--no-input')) args.push('--no-input');
  if (command.json && !args.includes('--json')) args.push('--json');
  const mutating = isMutating(command, args);
  if (DESTRUCTIVE.has(name) && !args.includes('--yes')) args.push('--yes');
  return { kind: 'run', args, mutating, stdinFromEditor: name === 'check' && args.includes('--stdin') };
}

// ------------------------------------------------------------------ prefill

const USES_COMPONENT = new Set(['component edit', 'component remove', 'question add', 'question remove',
  'decision make', 'decision propose', 'decision accept', 'decision add', 'hazard add', 'hazard dismiss',
  'hazard remove', 'invariant add', 'invariant remove', 'assumption add', 'assumption accept',
  'assumption reject', 'column set', 'specify']);

/** A list or a word of the spec as a form field: "a,b" for a list of words, '' for anything else. */
export function printable(value: unknown): string {
  if (value === null || value === undefined) return '';
  if (Array.isArray(value)) {
    if (value.some((v) => v === null || typeof v === 'object')) return '';
    return value.map(String).join(',');
  }
  return typeof value === 'object' ? '' : String(value);
}

/**
 * The fields a command form starts with: what was asked, the selected component, and for a plain
 * "Edit spec" the component's current description, ticket, signature and uses. Nothing is guessed.
 */
/**
 * The known fields of a command opened from a component. Edit spec gets every field the component has:
 * from `show <component>`, then from its entry in the spec (`specEntry`: ticket, owners, group, brief),
 * as the IntelliJ plugin (ArchvowCommandPrefill, 0.11.3). A specific fix (a diagnostic's) stays specific.
 */
export function prefill(command: string, requested: Record<string, string>, selected: string,
  snapshot: Record<string, unknown> | undefined, specEntry?: Record<string, unknown>): Record<string, string> {
  const data: Record<string, string> = { ...requested };
  if (USES_COMPONENT.has(command) && !data.component && selected) data.component = selected;
  const target = data.component ?? '';
  if (command === 'component edit' && snapshot && target === snapshot.component) {
    const specific = Object.keys(data).some((k) => k !== 'component');
    if (!specific) {
      const fields: [string, string][] = [['description', 'description'], ['spec', 'spec'], ['signature', 'signature'],
        ['uses', 'uses'], ['group', 'group'], ['owners', 'owners'], ['source', 'from'], ['source', 'brief']];
      for (const [field, key] of fields) {
        if (field in data) continue;
        const known = printable(snapshot[key]) || (specEntry ? printable(specEntry[key]) : '');
        if (known) data[field] = known;
      }
    }
  }
  return data;
}

// ------------------------------------------------------------------ in-place actions

function required(value: string | undefined, name: string): string {
  if (value === undefined || value.trim() === '') throw new Error('Missing ' + name);
  return value.trim();
}

export const inline = {
  decide: (component: string, number: string, answer: string): string[] =>
    ['decision', 'make', required(component, 'component'), required(number, 'question number'), required(answer, 'answer'), '--json'],
  propose: (component: string, number: string, answer: string): string[] =>
    ['decision', 'propose', required(component, 'component'), required(number, 'question number'), required(answer, 'proposal'), '--json'],
  accept: (component: string, number: string): string[] =>
    ['decision', 'accept', required(component, 'component'), required(number, 'question number'), '--json'],
  hazard: (component: string, question: string, severity: string, description: string): string[] => {
    const base = ['hazard', 'add', required(component, 'component'), required(question, 'safety question'), required(severity, 'severity')];
    const text = (description ?? '').trim();
    return text ? [...base, text, '--json'] : [...base, '--json'];
  },
  dismiss: (component: string, question: string, reason: string): string[] =>
    ['hazard', 'dismiss', required(component, 'component'), required(question, 'safety question'), required(reason, 'reason'), '--json'],
};

// ------------------------------------------------------------------ task prompts

/** T2 or 2 -> "2" (archvow prompt --task takes a number); '' when it is not one. */
export function taskNumber(identifier: string): string {
  const m = /^T?([0-9]+)$/i.exec((identifier ?? '').trim());
  if (!m) return '';
  const n = parseInt(m[1], 10);
  return n > 0 ? String(n) : '';
}

/** `prompt <component> [--task n]`; throws on a task id that is not a number. */
export function promptCommand(component: string, task: string): string[] {
  if (!component) throw new Error('Missing component');
  if (!task) return ['prompt', component];
  const n = taskNumber(task);
  if (!n) throw new Error('Invalid task number: ' + task);
  return ['prompt', component, '--task', n];
}

/** archvow refuses --task on a component too small to cut into tasks: show the whole card then. */
export function needsFullCard(exitCode: number, output: string): boolean {
  if (exitCode === 0 || !output) return false;
  const s = output.toLowerCase();
  return s.includes('is one task: its card is small enough') || s.includes('has tasks t1..t');
}
