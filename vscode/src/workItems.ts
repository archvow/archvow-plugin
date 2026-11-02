// The work of the project as the IntelliJ workspace lists it: what is pending and what is resolved, per
// component and for the project, the counts on each view, the Architecture table. A faithful port of
// ArchvowPanel.rebuildQuickModels, ArchvowSafetyState, ArchvowDataView.items, ArchvowIssueAction and
// ArchvowArchitectureView, so both editors show the same work. Pure: tested on ../contract/cli.
import { Guidance, guide } from './guidance';
import { Command } from './grammar';
import { addFolder } from './briefs';
import { tr } from './i18n';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type Json = Record<string, any>;

/** A field as text, as the Java workspace reads it ('' when absent or null). */
export function str(obj: Json | null | undefined, key: string): string {
  if (!obj) return '';
  const v = obj[key];
  if (v === null || v === undefined) return '';
  return typeof v === 'object' ? JSON.stringify(v) : String(v);
}

/** The objects of a list field. */
export function objects(obj: Json | null | undefined, key: string): Json[] {
  const v = obj ? obj[key] : undefined;
  return Array.isArray(v) ? v.filter((x) => x !== null && typeof x === 'object' && !Array.isArray(x)) : [];
}

export function flag(obj: Json | null | undefined, key: string): boolean {
  return !!obj && obj[key] === true;
}

// ------------------------------------------------------------------ safety

export const safety = {
  requiresInvariant: (hazard: Json): boolean => ['high', 'critical'].includes(str(hazard, 'severity').toLowerCase()),
  /** The first high or critical hazard of a safety question that no invariant prevents, or ''. */
  unprotectedHazard(question: Json): string {
    for (const hazard of objects(question, 'hazards')) {
      if (safety.requiresInvariant(hazard) && objects(hazard, 'prevented_by').length === 0) return str(hazard, 'id');
    }
    return '';
  },
  pending: (question: Json | null): boolean =>
    !!question && (str(question, 'status').toLowerCase() === 'open' || safety.unprotectedHazard(question) !== ''),
  status(question: Json): 'OPEN' | 'NEEDS INVARIANT' | 'RESOLVED' {
    if (str(question, 'status').toLowerCase() === 'open') return 'OPEN';
    return safety.unprotectedHazard(question) ? 'NEEDS INVARIANT' : 'RESOLVED';
  },
  suggestedHazard: (q: Json | null): string => str(q?.suggestion ?? null, 'hazard'),
  suggestedInvariant: (q: Json | null): string => str(q?.suggestion ?? null, 'invariant'),
  /** The suggestion and the hazards with what prevents them, as plain lines. */
  preview(question: Json): string {
    let text = '';
    const hazard = safety.suggestedHazard(question);
    const invariant = safety.suggestedInvariant(question);
    if (hazard) text += 'Suggested hazard / Hazard suggéré: ' + hazard + '\n';
    if (invariant) text += 'Suggested invariant / Invariant suggéré: ' + invariant + '\n';
    for (const h of objects(question, 'hazards')) {
      text += `Hazard ${str(h, 'id')} (${str(h, 'severity')}) — ${str(h, 'description')}\n`;
      const protections = objects(h, 'prevented_by');
      for (const i of protections) {
        text += `  ↳ Invariant ${str(i, 'id')}: ${str(i, 'statement')}`;
        if (i.tested === false) text += ' [test not verified / test non vérifié]';
        text += '\n';
      }
      if (protections.length === 0 && safety.requiresInvariant(h)) text += '  ⚠ ARCH030 — invariant required / invariant obligatoire\n';
    }
    return text;
  },
};

// ------------------------------------------------------------------ columns and tasks (Tasks view)

export interface DataItem {
  kind: 'COLUMN' | 'TASK';
  id: string;
  title: string;
  detail: string;
  pending: boolean;
}

export function dataItems(component: Json): DataItem[] {
  const items: DataItem[] = [];
  for (const column of objects(component, 'columns')) {
    const name = str(column, 'name') || '(unnamed column)';
    const classification = str(column, 'class');
    items.push({ kind: 'COLUMN', id: name, title: name,
      detail: classification ? 'Classification: ' + classification : 'Classification missing', pending: !classification });
  }
  for (const task of objects(component, 'tasks')) {
    const id = str(task, 'task');
    const description = str(task, 'summary') || (id ? 'Task ' + id : 'Unnamed task');
    const details = objects(task, 'items').map((item) => {
      const content = ((str(item, 'kind') ? str(item, 'kind') + ' ' : '') + (str(item, 'key') ? str(item, 'key') + ': ' : '')
        + str(item, 'text')).trim();
      return content ? (flag(item, 'done') ? '✓ ' : '• ') + content : '';
    }).filter((s) => s !== '');
    items.push({ kind: 'TASK', id, title: description, detail: details.join('\n'), pending: !flag(task, 'done') });
  }
  return items;
}

// ------------------------------------------------------------------ check diagnostics with a known fix

export interface Fix {
  command: string;
  values: Record<string, string>;
  label: string;
}

/** A component id quoted in a message ('clean_orders'), when it is one of `known`. */
export function quotedComponent(message: string, known: Iterable<string>): string {
  const ids = new Set(known);
  for (const m of (message ?? '').matchAll(/'([A-Za-z_][A-Za-z0-9_.-]*)'/g)) if (ids.has(m[1])) return m[1];
  return '';
}

/** The documented fixes a diagnostic gives (never run: they prefill a form). */
export function issueFix(code: string, message: string, component: string): Fix | null {
  const text = message ?? '';
  const c = code.toUpperCase();
  if (c === 'ARCH019' && component) return { command: 'component add', values: { type: 'schema' }, label: 'Create schema / Créer un schéma' };
  if (c === 'ARCH004') {
    const m = /archvow\s+component\s+edit\s+([A-Za-z_][A-Za-z0-9_.-]*)\s+--signature\s+"([^"]+)"/.exec(text);
    if (m) return { command: 'component edit', values: { component: m[1], signature: m[2] }, label: 'Review signature / Vérifier la signature' };
  }
  if (c === 'ARCH039') {
    // drawn from a folder no longer declared: the architect adds it; a brief changed: accept it once reviewed
    const folder = addFolder(text);
    if (folder) return { command: 'init', values: { package: folder.package, force: 'true', answer: folder.answer }, label: 'Add this folder (architect) / Ajouter ce dossier (architecte)' };
    const m = /archvow\s+brief\s+accept\s+(\S+)/.exec(text);
    if (m) return { command: 'brief accept', values: { brief: m[1] }, label: 'Accept (reviewed) / Accepter (relue)' };
  }
  if (c === 'ARCH030') {
    const m = /archvow\s+invariant\s+add\s+([A-Za-z_][A-Za-z0-9_.-]*)\s+"[^"]*"\s+--prevents\s+([A-Za-z_][A-Za-z0-9_.-]*)/.exec(text);
    if (m) return { command: 'invariant add', values: { component: m[1], prevents: m[2] }, label: 'Add invariant / Ajouter un invariant' };
  }
  return null;
}

// ------------------------------------------------------------------ the work items

export type ItemKind = 'ERROR' | 'WARNING' | 'QUESTION' | 'DECISION' | 'SUGGESTION' | 'RESOLVED';

export interface WorkItem {
  kind: ItemKind;
  title: string;
  detail: string;
  /** '' for a project-wide item. */
  component: string;
  pending: boolean;
  /** The label of the button that treats it. */
  action: string;
  /** What treating it does: an archvow command name, or component-show, open-file, open-diagnostics... */
  command: string;
  preset: Record<string, string>;
  /** For a check diagnostic: what it means, what to do, who, whether it blocks (guidance.ts). */
  guide?: Guidance;
}

export interface Work {
  pending: WorkItem[];
  resolved: WorkItem[];
  /** Diagnostics, project-wide and unclassified items: the Other / Project view. */
  otherPending: WorkItem[];
  otherResolved: WorkItem[];
  pendingByComponent: Record<string, number>;
  resolvedByComponent: Record<string, number>;
}

const short = (text: string, n: number): string => (text.length <= n ? text : text.substring(0, n - 1) + '…');

/** Whether an item belongs to Other / Project rather than to one of the component's views. */
export function otherCategory(item: WorkItem): boolean {
  if (!item.component) return true;
  switch (item.command) {
    case 'decision make': case 'decision propose': case 'decision accept': case 'hazard add': case 'hazard dismiss':
    case 'assumption accept': case 'assumption reject': case 'invariant add': case 'column set': case 'prompt':
      return false;
    case 'component-show':
      return item.kind === 'WARNING' || item.kind === 'ERROR';
    default:
      return true; // a future command stays visible
  }
}

/** The component view an item opens: decisions, questions, safety, assumptions, rules, tasks, vow, other. */
export function viewForItem(item: WorkItem): number {
  switch (item.preset.view ?? '') {
    case 'decision': return 0;
    case 'question': return 1;
    case 'safety': return 2;
    case 'assumption': return 3;
    case 'data': return 5;
    case 'other': return 7;
  }
  switch (item.command) {
    case 'decision accept': case 'decision propose': return 0;
    case 'decision make': return /^[0-9]+$/.test(item.preset.question ?? '') ? 0 : 1;
    case 'hazard add': case 'hazard dismiss': case 'invariant add': return 2;
    case 'assumption accept': case 'assumption reject': return 3;
    case 'column set': case 'prompt': return 5;
    default: return 6;
  }
}

/**
 * Every pending and resolved item of the project: check's violations, the project's own records, and
 * for each component its decisions, questions, safety questions, assumptions, columns, missing parts
 * and tasks. `snapshots` are `archvow show <component> --json` by id.
 */
export function buildWork(spec: Json | null, check: Json | null, checkFailure: string,
  snapshots: Record<string, Json>, fr: boolean, rules = '', commands: Command[] = []): Work {
  const work: Work = { pending: [], resolved: [], otherPending: [], otherResolved: [], pendingByComponent: {}, resolvedByComponent: {} };
  const keys = new Set<string>();
  const add = (item: WorkItem): void => {
    const key = [item.pending, item.command, item.component, JSON.stringify(item.preset), item.title, item.detail].join('|');
    if (keys.has(key)) return;
    keys.add(key);
    (item.pending ? work.pending : work.resolved).push(item);
    if (otherCategory(item)) (item.pending ? work.otherPending : work.otherResolved).push(item);
  };
  const known = Object.keys(snapshots);
  for (const violation of objects(check, 'violations')) {
    const code = str(violation, 'code') || 'CHECK';
    const description = str(violation, 'message') || JSON.stringify(violation, null, 2);
    const file = str(violation, 'file');
    let referenced = str(violation, 'component');
    if (!(referenced in snapshots)) referenced = quotedComponent(description, known);
    const fix = issueFix(code, description, referenced);
    if (code === 'ARCH030' && fix && referenced) {
      // already an item of the component's safety view
      const covered = objects(snapshots[referenced], 'safety').some((q) => safety.unprotectedHazard(q) !== '');
      if (covered) continue;
    }
    const kind: ItemKind = str(violation, 'severity').toLowerCase() === 'warning' ? 'WARNING' : 'ERROR';
    const line = Number(violation.line) || 0;
    const guidance = guide({ code, severity: str(violation, 'severity'), message: description, file, line }, rules, commands, fr);
    // no fix of its own: the command the message names, as a prefilled form; else the file; else the list
    const named = fix === null ? guidance.command : null;
    const open = guidance.file;
    const command = fix !== null ? (code === 'ARCH019' ? 'schema-create' : fix.command)
      : named ? named.name : open ? 'open-file' : 'open-diagnostics';
    const action = fix !== null ? fix.label
      : named ? tr(fr, 'Prepare: ', 'Préparer : ') + short(named.text, 60)
        : open ? tr(fr, 'Open ', 'Ouvrir ') + open.path + (open.line ? ':' + open.line : '') : tr(fr, 'Open diagnostics', 'Ouvrir le diagnostic');
    add({ kind, title: code + ' · ' + short(description, 70),
      detail: description + (file ? '\n\n' + file + ':' + line : ''),
      component: referenced, pending: true, action, command,
      preset: fix !== null ? fix.values : named ? named.values : { file: open?.path ?? '', line: String(open?.line ?? 0) }, guide: guidance });
  }
  for (const category of ['decisions', 'questions', 'assumptions', 'tasks']) {
    for (const record of objects(spec, category)) {
      const title = str(record, 'question') || str(record, 'text') || str(record, 'summary') || JSON.stringify(record, null, 2);
      const state = str(record, 'status').toLowerCase();
      const answer = str(record, 'answer');
      const closed = category === 'tasks' ? flag(record, 'done')
        : category === 'assumptions' ? state === 'accepted' || state === 'rejected'
          : answer !== '' || state === 'resolved' || state === 'answered';
      add({ kind: closed ? 'RESOLVED' : 'QUESTION',
        title: tr(fr, 'Project', 'Projet') + ' · ' + category + ' · ' + short(title, 65),
        detail: title + (answer ? '\nAnswer / Réponse: ' + answer : ''), component: '', pending: !closed,
        action: tr(fr, 'Open project specification', 'Ouvrir la spécification du projet'), command: 'open-project-vow', preset: {} });
    }
  }
  if (check === null && checkFailure) {
    add({ kind: 'WARNING', title: tr(fr, 'Check not available', 'Vérification indisponible'), detail: checkFailure, component: '',
      pending: true, action: tr(fr, 'Open diagnostics', 'Ouvrir le diagnostic'), command: 'open-diagnostics', preset: {} });
  }
  const open = tr(fr, 'Open component', 'Ouvrir le composant');
  for (const data of Object.values(snapshots)) {
    const id = str(data, 'component');
    if (!id) continue;
    for (const d of objects(data, 'decisions')) {
      const number = str(d, 'number');
      const question = str(d, 'question');
      const proposed = str(d, 'proposed');
      const answer = str(d, 'answer');
      const pending = answer === '';
      const suggested = proposed !== '';
      add({ kind: pending ? (suggested ? 'SUGGESTION' : 'DECISION') : 'RESOLVED',
        title: `${id} · D${number} ${short(question, 60)}`,
        detail: question + '\n' + (pending ? (suggested ? 'Proposal / Proposition: ' + proposed : 'Suggested / Suggéré: ' + str(d, 'suggested')) : 'Answer / Réponse: ' + answer),
        component: id, pending,
        action: pending ? (suggested ? tr(fr, 'Accept proposal', 'Accepter la proposition') : tr(fr, 'Answer', 'Répondre')) : open,
        command: !pending ? 'component-show' : suggested ? 'decision accept' : 'decision make',
        preset: !pending ? { view: 'decision', number } : suggested ? { component: id, number, view: 'decision' }
          : { component: id, question: number, answer: str(d, 'suggested'), view: 'decision' } });
    }
    for (const q of objects(data, 'questions')) {
      const key = str(q, 'key');
      const text = str(q, 'question');
      const pending = str(q, 'answer') === '';
      add({ kind: pending ? 'QUESTION' : 'RESOLVED', title: `${id} · ${key} ${short(text, 60)}`,
        detail: text + '\n' + (pending ? '' : 'Answer / Réponse: ' + str(q, 'answer')), component: id, pending,
        action: pending ? tr(fr, 'Answer', 'Répondre') : open, command: pending ? 'decision make' : 'component-show',
        preset: pending ? { component: id, question: key, view: 'question' } : { view: 'question', question: key } });
    }
    for (const s of objects(data, 'safety')) {
      const number = str(s, 'number');
      const question = str(s, 'question');
      const pending = safety.pending(s);
      const missing = safety.unprotectedHazard(s);
      const word = 'S' + number;
      const preset: Record<string, string> = { component: id, guide_word: word, view: 'safety' };
      if (missing) preset.prevents = missing;
      add({ kind: pending ? 'QUESTION' : 'RESOLVED', title: `${id} · ${word} ${short(question, 60)}`,
        detail: question + '\n' + safety.preview(s), component: id, pending,
        action: pending ? (missing ? tr(fr, 'Add required invariant', "Ajouter l'invariant requis") : tr(fr, 'Review hazard', 'Examiner le hazard')) : open,
        command: pending ? (missing ? 'invariant add' : 'hazard add') : 'component-show', preset });
    }
    for (const a of objects(data, 'assumptions')) {
      const number = str(a, 'number');
      const status = str(a, 'status').toLowerCase();
      const pending = status !== 'accepted' && status !== 'rejected';
      const title = str(a, 'text');
      add({ kind: pending ? 'SUGGESTION' : 'RESOLVED', title: `${id} · A${number} ${short(title, 60)}`,
        detail: title + '\nStatus: ' + status, component: id, pending, action: pending ? tr(fr, 'Accept', 'Accepter') : open,
        command: pending ? 'assumption accept' : 'component-show',
        preset: pending ? { component: id, number, view: 'assumption' } : { view: 'assumption', number } });
    }
    for (const item of dataItems(data)) {
      if (item.kind !== 'COLUMN') continue;
      add({ kind: item.pending ? 'QUESTION' : 'RESOLVED', title: `${id} · ${short(item.id, 60)}`, detail: item.detail,
        component: id, pending: item.pending, action: item.pending ? tr(fr, 'Classify', 'Classifier') : open,
        command: item.pending ? 'column set' : 'component-show',
        preset: item.pending ? { component: id, names: item.id, view: 'data' } : { view: 'data' } });
    }
    for (const missing of Array.isArray(data.missing) ? data.missing : []) {
      if (missing === null || missing === undefined) continue;
      let detail = typeof missing === 'object' ? str(missing, 'message') || str(missing, 'text') : String(missing);
      if (!detail) detail = JSON.stringify(missing, null, 2);
      const schemaMissing = detail.trim().toLowerCase() === 'schema';
      if (schemaMissing && hasSchemaDiagnostic(check, id, known)) continue; // the same gap as ARCH019
      add({ kind: 'WARNING', title: `${id} · ${tr(fr, 'Missing contract', 'Contrat manquant')} · ${short(detail, 65)}`, detail,
        component: id, pending: true, action: schemaMissing ? tr(fr, 'Create schema', 'Créer un schéma') : open,
        command: schemaMissing ? 'schema-create' : 'component-show', preset: { view: 'other' } });
    }
    for (const item of dataItems(data)) {
      if (item.kind !== 'TASK') continue;
      add({ kind: item.pending ? 'SUGGESTION' : 'RESOLVED', title: `${id} · ${item.id} ${short(item.title, 60)}`,
        detail: item.title + (item.detail ? '\n' + item.detail : ''), component: id, pending: item.pending,
        action: item.pending ? tr(fr, 'View task prompt', 'Voir la tâche') : open,
        command: item.pending ? 'prompt' : 'component-show', preset: { view: 'data' } });
    }
  }
  for (const item of work.pending) if (item.component) work.pendingByComponent[item.component] = (work.pendingByComponent[item.component] ?? 0) + 1;
  for (const item of work.resolved) if (item.component) work.resolvedByComponent[item.component] = (work.resolvedByComponent[item.component] ?? 0) + 1;
  return work;
}

function hasSchemaDiagnostic(check: Json | null, component: string, known: string[]): boolean {
  for (const violation of objects(check, 'violations')) {
    if (str(violation, 'code').toUpperCase() !== 'ARCH019') continue;
    let referenced = str(violation, 'component');
    if (!known.includes(referenced)) referenced = quotedComponent(str(violation, 'message'), known);
    if (referenced === component) return true;
  }
  return false;
}

// ------------------------------------------------------------------ counts on the component views

/** Pending and total per view: decisions, questions, safety, assumptions, rules, tasks. */
export function viewCounts(data: Json): { pending: number[]; total: number[] } {
  const pending = [0, 0, 0, 0, 0, 0];
  const total = [0, 0, 0, 0, 0, 0];
  for (const d of objects(data, 'decisions')) { total[0]++; if (!str(d, 'answer')) pending[0]++; }
  for (const q of objects(data, 'questions')) { total[1]++; if (!str(q, 'answer')) pending[1]++; }
  for (const q of objects(data, 'safety')) { total[2]++; if (safety.pending(q)) pending[2]++; }
  for (const a of objects(data, 'assumptions')) {
    total[3]++;
    const state = str(a, 'status').toLowerCase();
    if (state !== 'accepted' && state !== 'rejected') pending[3]++;
  }
  total[4] = objects(data, 'rules').length;
  const items = dataItems(data);
  total[5] = items.length;
  pending[5] = items.filter((i) => i.pending).length;
  return { pending, total };
}

// ------------------------------------------------------------------ the Architecture table

export interface ArchitectureRow {
  id: string;
  type: string;
  layer: string;
  container: string;
  state: string;
  pending: number;
}

/** One row per component of the spec, with check's status (or the component's own when check has none). */
export function architectureRows(spec: Json | null, check: Json | null, snapshots: Record<string, Json>,
  pendingByComponent: Record<string, number>): ArchitectureRow[] {
  const states = new Map(objects(check, 'components').map((c) => [str(c, 'component'), str(c, 'status')]));
  const rows: ArchitectureRow[] = [];
  for (const container of objects(spec, 'containers')) {
    for (const component of objects(container, 'components')) {
      const id = str(component, 'id');
      let state = states.get(id) ?? '';
      if (!state) state = str(snapshots[id], 'status');
      if (!state) state = str(component, 'status');
      rows.push({ id, type: str(component, 'type'), layer: str(component, 'layer'), container: str(container, 'id'),
        state, pending: pendingByComponent[id] ?? 0 });
    }
  }
  return rows;
}

/** main, master, develop: the workspace advises a feature branch (advice only). */
export function needsFeatureBranch(branch: string): boolean {
  return ['main', 'master', 'develop', 'development'].includes((branch ?? '').trim().toLowerCase());
}
