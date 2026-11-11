// What the workspace makes of the project before and after asking archvow, with no VS Code in it, so
// it is tested on ../contract: where the project stands, what each component's views show, the component
// types of the catalog, the meaning of a check code.
import * as fs from 'node:fs';
import * as path from 'node:path';
import { DataItem, dataItems, Json, objects, safety, str, viewCounts } from './workItems';

/** Where the project stands before archvow is asked anything (the IntelliJ ArchvowProjectReadiness). */
export type Readiness = 'NO_PROJECT' | 'ROOT_MISSING' | 'NOT_INITIALIZED' | 'SPEC_INACCESSIBLE' | 'READY';

export function readiness(root: string | null): Readiness {
  if (!root) return 'NO_PROJECT';
  try {
    if (!fs.statSync(root).isDirectory()) return 'ROOT_MISSING';
  } catch {
    return 'ROOT_MISSING';
  }
  const spec = path.join(root, '.archvow', 'project.vow');
  if (!fs.existsSync(spec)) return 'NOT_INITIALIZED';
  try {
    const stat = fs.statSync(spec);
    fs.accessSync(spec, fs.constants.R_OK);
    return stat.isFile() && stat.size > 0 ? 'READY' : 'SPEC_INACCESSIBLE';
  } catch {
    return 'SPEC_INACCESSIBLE';
  }
}

export interface Derived {
  counts: { pending: number[]; total: number[] };
  safety: { status: string; unprotected: string; preview: string }[];
  data: DataItem[];
}

/** What the views of a component show, from `archvow show <component> --json` (same rules as IntelliJ). */
export function derive(view: Json): Derived {
  return {
    counts: viewCounts(view),
    safety: objects(view, 'safety').map((q) => ({ status: safety.status(q), unprotected: safety.unprotectedHazard(q), preview: safety.preview(q) })),
    data: dataItems(view),
  };
}

/** The component types a template lets people add (component add), from the catalog. */
export function componentTypes(catalog: Json | null): Record<string, { description: string; hint: string; pattern: string }> {
  const types: Record<string, { description: string; hint: string; pattern: string }> = {};
  for (const template of objects(catalog, 'templates')) {
    const declared = template.component_types;
    if (declared === null || typeof declared !== 'object') continue;
    for (const [name, definition] of Object.entries(declared as Record<string, Json>)) {
      if (definition && definition.addable === true && !(name in types)) {
        types[name] = { description: str(definition, 'description'), hint: str(definition, 'name_hint'), pattern: str(definition, 'name_pattern') };
      }
    }
  }
  return Object.fromEntries(Object.entries(types).sort(([a], [b]) => a.localeCompare(b)));
}

/** The row of a check code in archvow's rules table, as one sentence. */
export function ruleExplanation(markdown: string, code: string): string {
  for (const line of markdown.split('\n')) {
    const m = new RegExp(`^\\s*\\|\\s*${code}\\s*\\|(.*)$`).exec(line);
    if (m) {
      return m[1].split('|').map((c) => c.trim()).filter((c) => c).join(' — ').replace(/`/g, '');
    }
  }
  return '';
}

