// What the Overview's cards show (0.25.0): the first steps of a new project, the Architecture card's own items
// and the Checks card's first finding. The same reading as the IntelliJ plugin's ArchvowOverview.java; both are
// checked against contract/overview/expected.json.
import { Json, objects, str, Work } from './workItems';

export interface FirstStep {
  step: string;
  title: string;
  detail: string;
  command: string;
  done: boolean;
}

export interface FirstSteps {
  steps: FirstStep[];
  done: number;
  /** The first step not done: the one with the action button. */
  current: number;
}

export interface Finding {
  title: string;
  code: string;
  severity: string;
  file: string;
  line: number;
  /** "ARCH005 · compute_vat.py:16": the code, then the file's name and line. */
  where: string;
}

export interface Overview {
  /** null once the five are done (or with an archvow that does not say them): the card is gone. */
  firstSteps: FirstSteps | null;
  /** The Architecture card counts only its own items: decisions, questions and tasks - not check findings. */
  itemsDone: number;
  itemsTotal: number;
  itemsOpen: number;
  /** Project-wide items, not attributed to a component. */
  projectItems: number;
  errors: number;
  warnings: number;
  firstFinding: Finding | null;
}

/** archvow's `first_steps` (workspace --json, contract 2.1), or null when the card has nothing left to show. */
export function firstSteps(raw: unknown): FirstSteps | null {
  const steps: FirstStep[] = (Array.isArray(raw) ? raw : []).filter((s) => s !== null && typeof s === 'object')
    .map((s: Json) => ({ step: str(s, 'step'), title: str(s, 'title'), detail: str(s, 'detail'), command: str(s, 'command'), done: s.done === true }));
  const done = steps.filter((s) => s.done).length;
  if (steps.length === 0 || done === steps.length) return null;
  return { steps, done, current: steps.findIndex((s) => !s.done) };
}

/** The first of check's findings, as the Checks card shows it. */
export function firstFinding(check: Json | null): Finding | null {
  const v = objects(check, 'violations')[0];
  if (v === undefined) return null;
  const file = str(v, 'file');
  const line = Number(v.line) || 0;
  const name = file.replace(/\\/g, '/').split('/').pop() ?? '';
  const code = str(v, 'code');
  return { title: str(v, 'title') || code, code, severity: str(v, 'severity'), file, line,
    where: code + (name ? ' · ' + name + (line ? ':' + line : '') : '') };
}

export function overview(work: Work, check: Json | null, steps: unknown): Overview {
  const own = (items: Work['pending']) => items.filter((i) => i.source !== 'check' && i.command !== 'open-diagnostics');
  const open = own(work.pending).length;
  const done = own(work.resolved).length;
  const violations = objects(check, 'violations');
  const count = (key: string, severity: string): number => check && check[key] != null ? Number(check[key]) || 0
    : violations.filter((v) => str(v, 'severity').toLowerCase() === severity).length;
  return { firstSteps: firstSteps(steps), itemsDone: done, itemsTotal: open + done, itemsOpen: open,
    projectItems: work.otherPending.filter((i) => !i.component && i.source !== 'check').length,
    errors: count('errors', 'error'), warnings: count('warnings', 'warning'), firstFinding: firstFinding(check) };
}
