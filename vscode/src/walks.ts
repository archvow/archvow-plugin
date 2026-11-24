// The two walks of the workspace (1.1.0): Specify - answer what a person must decide, component after component -
// and Build - each ready component, its code, its check, the next one. Both read only `workspace --json` (archvow
// does not change): the `next` rows give the build order and each component's readiness, the check gives what is
// still open. The same reading as the IntelliJ plugin's ArchvowWalks.java; both are checked against
// contract/walks/expected.json.
import { Json, objects, str } from './workItems';

/** One component of a walk: its id and what is still open in it (decisions, questions, safety). */
export interface Step {
  component: string;
  open: number;
}

/** Where a walk starts: the foundations (the shared components), a feature, or a component and the rest of its feature. */
export type Scope = { kind: 'foundations' } | { kind: 'feature'; name: string } | { kind: 'component'; id: string };

/** What the Copy AI prompt of a feature does now (1.1.0). */
export interface PromptState {
  /** ready: a component to build · specify-first: strict, nothing ready · propose-first: guided or free, nothing ready · built: nothing planned. */
  state: 'ready' | 'specify-first' | 'propose-first' | 'built';
  /** The component the prompt builds (ready, propose-first); '' otherwise. */
  component: string;
  /** What is still open in the feature's components. */
  open: number;
}

const SHARED = 'shared';

/** What is still open in each component, as the check counts it. */
export function openOf(w: Json): Map<string, number> {
  const out = new Map<string, number>();
  const check = (w.check ?? {}) as Json;
  for (const c of objects(check, 'components')) out.set(str(c, 'component'), Number(c.open_questions) || 0);
  return out;
}

/** The ids of a feature's components, in the feature's order. */
export function membersOf(w: Json, feature: string): string[] {
  const view = ((w.features ?? {}) as Json)[feature] as Json | undefined;
  return objects(view, 'components').map((c) => str(c, 'id'));
}

/** The feature a component belongs to ('' when none). */
export function featureOf(w: Json, id: string): string {
  for (const name of Object.keys((w.features ?? {}) as Json)) if (membersOf(w, name).includes(id)) return name;
  return '';
}

/** The `next` rows, in archvow's build order. */
function rows(w: Json): Json[] {
  return Array.isArray(w.next) ? (w.next as Json[]) : [];
}

/** `ids` in archvow's build order (the `next` rows), the others after them in their own order. */
export function inBuildOrder(w: Json, ids: string[]): string[] {
  const order = rows(w).map((r) => str(r, 'component'));
  const known = order.filter((id) => ids.includes(id));
  return [...known, ...ids.filter((id) => !known.includes(id))];
}

/** The components a Specify walk goes through: those of the scope with something open, in build order. */
export function specifyQueue(w: Json, scope: Scope): Step[] {
  const open = openOf(w);
  let ids: string[];
  if (scope.kind === 'foundations') ids = inBuildOrder(w, membersOf(w, SHARED));
  else if (scope.kind === 'feature') ids = inBuildOrder(w, membersOf(w, scope.name));
  else {
    const rest = inBuildOrder(w, membersOf(w, featureOf(w, scope.id))).filter((id) => id !== scope.id);
    ids = [scope.id, ...rest];
  }
  return ids.filter((id) => (open.get(id) ?? 0) > 0).map((id) => ({ component: id, open: open.get(id) ?? 0 }));
}

/** The components a Build walk goes through: the feature's ready ones (nothing left to decide), in build order. */
export function buildQueue(w: Json, feature: string): string[] {
  const members = membersOf(w, feature);
  return rows(w).filter((r) => members.includes(str(r, 'component')) && str(r, 'readiness').startsWith('ready'))
    .map((r) => str(r, 'component'));
}

/** True when `next` says the merge request is full (ARCH022's budget): Build stops there. */
export function budgetReached(w: Json): boolean {
  return rows(w).some((r) => str(r, 'readiness').startsWith('next merge request'));
}

/** All the project's components ready to build (the Overview's "Ready to build: N"). */
export function readyToBuild(w: Json): string[] {
  return rows(w).filter((r) => str(r, 'readiness').startsWith('ready')).map((r) => str(r, 'component'));
}

/** What Copy AI prompt does on a feature's page, from the readiness and the project's mode. */
export function promptState(w: Json, feature: string): PromptState {
  const members = membersOf(w, feature);
  const open = openOf(w);
  const total = members.reduce((n, id) => n + (open.get(id) ?? 0), 0);
  const ready = buildQueue(w, feature);
  if (ready.length) return { state: 'ready', component: ready[0], open: total };
  // as `prompt --feature` picks one when none is ready: the feature's first planned component, in its own order
  const status = new Map(objects((w.check ?? {}) as Json, 'components').map((c) => [str(c, 'component'), str(c, 'status')]));
  const planned = members.filter((id) => (status.get(id) || 'planned') === 'planned');
  if (!planned.length) return { state: 'built', component: '', open: total };
  const mode = str((w.project ?? {}) as Json, 'mode') || 'strict';
  if (mode === 'strict') return { state: 'specify-first', component: '', open: total };
  return { state: 'propose-first', component: planned[0], open: total };
}

/** What the page needs of the walks, worked out once per answer (the page is plain JavaScript): sent in the state. */
export interface WalkState {
  /** Each component's feature. */
  featureOf: Record<string, string>;
  /** What is still open in each component. */
  open: Record<string, number>;
  /** Each feature's Copy AI prompt. */
  prompt: Record<string, PromptState>;
  /** The Specify walk of the foundations, and of each feature (build order, open ones only). */
  foundations: Step[];
  features: Record<string, Step[]>;
  /** Each feature's components in build order (a component's walk: it, then the rest of these). */
  order: Record<string, string[]>;
  /** Each feature's Build walk: its ready components, in build order. */
  build: Record<string, string[]>;
  /** Each feature's planned components (the end of a Specify walk: "Ready to build: ..."). */
  planned: Record<string, string[]>;
  budget: boolean;
  ready: string[];
}

export function walkState(w: Json): WalkState {
  const names = Object.keys((w.features ?? {}) as Json);
  const featureOfId: Record<string, string> = {};
  for (const name of names) for (const id of membersOf(w, name)) featureOfId[id] = name;
  const status = new Map(objects((w.check ?? {}) as Json, 'components').map((c) => [str(c, 'component'), str(c, 'status')]));
  return {
    featureOf: featureOfId,
    open: Object.fromEntries(openOf(w)),
    prompt: Object.fromEntries(names.map((n) => [n, promptState(w, n)])),
    foundations: specifyQueue(w, { kind: 'foundations' }),
    features: Object.fromEntries(names.map((n) => [n, specifyQueue(w, { kind: 'feature', name: n })])),
    order: Object.fromEntries(names.map((n) => [n, inBuildOrder(w, membersOf(w, n))])),
    build: Object.fromEntries(names.map((n) => [n, buildQueue(w, n)])),
    planned: Object.fromEntries(names.map((n) => [n, membersOf(w, n).filter((id) => (status.get(id) || 'planned') === 'planned')])),
    budget: budgetReached(w),
    ready: readyToBuild(w),
  };
}
