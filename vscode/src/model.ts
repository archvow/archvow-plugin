// What the archvow CLI prints with --json, as the extension reads it, and the pure functions that turn
// it into what VS Code shows. No archvow rule is decided here: statuses, readiness and problems are the
// CLI's; this file only arranges them. The shapes are checked against ../contract/cli/ (test/model.test.ts).

/** `archvow show --json`: the whole spec. */
export interface Spec {
  system?: { name?: string; description?: string };
  containers: Container[];
}

export interface Container {
  id: string;
  package?: string;
  path?: string;
  components: SpecComponent[];
}

export interface SpecComponent {
  id: string;
  type: string;
  layer: string;
  module?: string;
  description?: string;
  uses?: string[];
}

/** `archvow check --json`. */
export interface CheckResult {
  ok: boolean;
  errors: number;
  warnings: number;
  violations: Violation[];
  components?: ComponentStatus[];
  safety?: { answered: number; total: number };
}

export interface Violation {
  code: string;
  severity: 'error' | 'warning' | string;
  message: string;
  /** Relative to the project root. */
  file: string;
  /** 1-based; 0 when the violation is about the whole file. */
  line: number;
  /** 0-based. */
  col: number;
  title?: string;
}

export interface ComponentStatus {
  container: string;
  component: string;
  type: string;
  layer: string;
  /** planned, implemented, tested, verified, drift. */
  status: string;
  tested: boolean;
  invariants_total: number;
  invariants_verified: number;
  open_questions: number;
  pending_assumptions: number;
  hazards: number;
}

/** `archvow next --json`: the planned components in order, with what archvow says about each. */
export interface NextRow {
  component: string;
  type: string;
  /** In archvow's words: "ready", "ready - task 1 of 3", "waiting for x, y", "blocked: 2 decisions to make"... */
  readiness: string;
  tokens?: number;
  invariants?: number;
}

/** `archvow show <component> --json`. */
export interface ComponentView {
  component: string;
  type: string;
  description: string;
  status: string;
  decisions: Decision[];
  rules: { id: string; text: string; scope?: unknown }[];
  /** What the component lacks, in archvow's words (e.g. `schema`). */
  missing: string[];
  questions: OtherQuestion[];
  safety: SafetyQuestion[];
  assumptions: Assumption[];
  tasks: Task[];
}

export interface Decision {
  number: number;
  question: string;
  answer: string | null;
  suggested: string | null;
  proposed: string | null;
}

/** A personal-data, operations or company question: P1, O2, C1. */
export interface OtherQuestion {
  /** What `decision make` takes: P1, O2, C1. */
  key: string;
  set?: string;
  title?: string;
  id?: string;
  rule?: string;
  question: string;
  answer?: string | null;
  suggested?: string | null;
}

export interface SafetyQuestion {
  number: number;
  guide_word: string;
  question: string;
  status: string;
  suggestion?: { hazard?: string; invariant?: string };
  hazards?: { id: string; severity: string; description?: string; prevented_by?: { id: string; statement: string; tested: boolean }[] }[];
  /** Why the guide word does not apply here, when it was dismissed. */
  dismissed?: string;
}

export interface Assumption {
  number: number;
  text: string;
  /** proposed (by the AI), accepted, rejected. */
  status?: string;
}

export interface Task {
  task: string;
  done: boolean;
  core?: boolean;
  items?: { kind: string; key: string; text: string; answer?: string | null; done: boolean }[];
}

// ------------------------------------------------------------------ the tree of components

export type Node =
  | { kind: 'container'; id: string; label: string; children: Node[] }
  | { kind: 'layer'; id: string; label: string; children: Node[] }
  | { kind: 'component'; id: string; label: string; component: SpecComponent; container: string;
      status?: ComponentStatus; next?: NextRow; problems: number };

/** Containers, then layers in the spec's order, then components, with check's status and next's readiness. */
export function buildTree(spec: Spec, check: CheckResult | null, next: NextRow[] | null): Node[] {
  const statuses = new Map((check?.components ?? []).map((c) => [c.component, c]));
  const readiness = new Map((next ?? []).map((n) => [n.component, n]));
  const problems = problemsByComponent(check);
  const containers: Node[] = [];
  for (const container of spec.containers ?? []) {
    const layers = new Map<string, Node & { kind: 'layer' }>();
    for (const component of container.components ?? []) {
      let layer = layers.get(component.layer);
      if (!layer) {
        layer = { kind: 'layer', id: `${container.id}/${component.layer}`, label: component.layer, children: [] };
        layers.set(component.layer, layer);
      }
      layer.children.push({
        kind: 'component', id: component.id, label: component.id, component, container: container.id,
        status: statuses.get(component.id), next: readiness.get(component.id),
        problems: problems.get(component.id) ?? 0,
      });
    }
    containers.push({ kind: 'container', id: container.id, label: container.id, children: [...layers.values()] });
  }
  // one container (the usual case): its layers at the top
  return containers.length === 1 ? (containers[0] as { children: Node[] }).children : containers;
}

/** How many check violations name each component (in their file path or their message). */
export function problemsByComponent(check: CheckResult | null): Map<string, number> {
  const counts = new Map<string, number>();
  for (const v of check?.violations ?? []) {
    const id = componentOfViolation(v);
    if (id !== null) counts.set(id, (counts.get(id) ?? 0) + 1);
  }
  return counts;
}

/** The component a violation is about, when its file says it (`.archvow/components/<id>/spec.vow`). */
export function componentOfViolation(v: Violation): string | null {
  const m = /^\.archvow\/components\/([^/]+)\//.exec(v.file.replace(/\\/g, '/'));
  return m ? m[1] : null;
}

/** One line of description under a component in the tree: archvow's own words. */
export function describe(node: Node & { kind: 'component' }): string {
  const parts: string[] = [];
  if (node.status) parts.push(node.status.status);
  if (node.next) parts.push(node.next.readiness);
  if (node.problems > 0) parts.push(`${node.problems} problem${node.problems > 1 ? 's' : ''}`);
  return parts.join(' · ');
}

/** The look of a component: from archvow's readiness first (what to do now), else its status. */
export function statusIcon(node: Node & { kind: 'component' }): 'error' | 'ready' | 'waiting' | 'blocked' | 'done' | 'planned' | 'drift' {
  if (node.status?.status === 'drift') return 'drift';
  const readiness = node.next?.readiness ?? '';
  if (readiness.startsWith('blocked')) return 'blocked';
  if (readiness.startsWith('waiting') || readiness.startsWith('next merge request')) return 'waiting';
  if (readiness.startsWith('ready')) return 'ready';
  if (node.status && node.status.status !== 'planned') return node.problems > 0 ? 'error' : 'done';
  return 'planned';
}

// ------------------------------------------------------------------ problems

export interface FileProblem {
  /** Relative to the project root, with / */
  file: string;
  /** 0-based line; whole-file problems are on line 0. */
  line: number;
  col: number;
  error: boolean;
  code: string;
  message: string;
  title?: string;
}

/** check's violations as editor problems: files, 0-based lines, errors and warnings. */
export function problemsOf(check: CheckResult): FileProblem[] {
  return (check.violations ?? []).map((v) => ({
    file: (v.file || '.archvow/spec.vow').replace(/\\/g, '/'),
    line: Math.max(0, (v.line ?? 0) - 1),
    col: Math.max(0, v.col ?? 0),
    error: v.severity === 'error',
    code: v.code,
    message: v.message,
    title: v.title,
  }));
}

// ------------------------------------------------------------------ the CLI version

/** `archvow 1.14.0` -> [1, 14, 0]; null when it is not a version. */
export function parseVersion(output: string): number[] | null {
  const m = /(\d+)\.(\d+)\.(\d+)/.exec(output);
  return m ? [Number(m[1]), Number(m[2]), Number(m[3])] : null;
}

/** "1.14" from the contract's version.txt ("archvow 1.14.0"). */
export function knownVersion(versionTxt: string): string {
  const v = parseVersion(versionTxt);
  return v === null ? '?' : `${v[0]}.${v[1]}`;
}

/**
 * A sentence when the installed archvow is another major.minor than the one the extension was built
 * for (its contract), else null: archvow keeps no backward compatibility. The IntelliJ plugin says the same.
 */
export function versionWarning(installed: number[] | null, known: string, fr = false): string | null {
  if (installed === null) {
    return fr ? "archvow n'a pas donné sa version (archvow --version) : est-il installé ?"
      : 'archvow did not say its version (archvow --version): is it installed?';
  }
  const [major, minor] = installed;
  if (`${major}.${minor}` === known) return null;
  const [kMajor, kMinor] = known.split('.').map(Number);
  const older = major < kMajor || (major === kMajor && minor < kMinor);
  const shown = installed.join('.');
  if (fr) {
    return `archvow ${shown} est ${older ? 'plus ancien' : 'plus récent'} que la version que ce plugin connaît (${known}) : `
      + (older ? 'mets archvow à jour.' : 'mets le plugin à jour.');
  }
  return `archvow ${shown} is ${older ? 'older' : 'newer'} than the version this plugin knows (${known}): `
    + (older ? 'update archvow.' : 'update the plugin.');
}
