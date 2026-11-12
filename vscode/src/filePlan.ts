// The files a command writes, shown under the command in the form (Files): what is new, updated, removed
// or kept, and the code and test files a new component plans (archvow never writes code: the AI or a
// developer writes them later). From contract/forms/forms.json `files` and what the project already
// says: its containers, components and rules (`show --json`) and its template's types (`catalog --json`).
// The IntelliJ plugin computes exactly the same (ArchvowFilePlan, FilePlanRegression on
// contract/forms/file-plans.expected.json, which UPDATE_FILE_PLANS=1 npm test writes).
import { Json, objects, str } from './workItems';

export type Change = 'new' | 'updated' | 'removed' | 'kept' | 'planned';
export interface FileRow { change: Change; path: string; note: string }

export interface PlanContext {
  spec: Json | null;
  catalog: Json | null;
  forms: Json | null;
  fr: boolean;
  /** The output file exists already (changes and diff write it). */
  outputExists?: boolean;
}

/**
 * Where a pack command writes (0.22.0): `{dir}` the folder `pack new --dir` names (empty: inside this project,
 * .archvow/packs); `{pack}` a pack's folder, or the name of one of the project's packs (.archvow/packs/<name>).
 */
function packFolder(value: string | undefined, parent: boolean): string {
  const text = (value ?? '').trim().replace(/\/+$/, '');
  if (parent) return text || '.archvow/packs';
  return /[/\\]/.test(text) || text.startsWith('.') ? text : `.archvow/packs/${text}`;
}

/** {name} orders_clean, {NAME} ORDERS_CLEAN, {Name} OrdersClean: archvow's manifest.name_values. */
export function nameValues(name: string): Record<string, string> {
  return { name, NAME: name.toUpperCase(), Name: name.split('_').map((p) => (p ? p[0].toUpperCase() + p.slice(1) : '')).join('') };
}

function format(pattern: string, values: Record<string, string>): string {
  return pattern.replace(/\{(name|NAME|Name)\}/g, (_, k: string) => values[k]);
}

function containers(spec: Json | null): Json[] {
  return objects(spec, 'containers');
}

function componentsOf(spec: Json | null): { comp: Json; container: Json }[] {
  return containers(spec).flatMap((container) => objects(container, 'components').map((comp) => ({ comp, container })));
}

/** archvow's manifest.source_path: <container path>/<layer>/[<group>/]<id>.py, from the module. */
export function sourcePath(container: Json, comp: Json): string {
  const module = str(comp, 'module');
  if (!module) return '';
  return `${str(container, 'path')}/${module.split('.').slice(1).join('/')}.py`;
}

/** archvow's manifest.test_path: tests/<layer>/[<group>/]test_<id>.py. */
export function testPath(comp: Json): string {
  const group = str(comp, 'group') ? `${str(comp, 'group')}/` : '';
  return `tests/${str(comp, 'layer')}/${group}test_${str(comp, 'id')}.py`;
}

/** The type of the project's template: its layer, module pattern and symbols. */
function typeOf(ctx: PlanContext, container: Json | undefined, type: string): Json | undefined {
  const wanted = container ? str(container, 'template').split('@')[0] : '';
  const templates = objects(ctx.catalog, 'templates');
  const ordered = [...templates.filter((t) => str(t, 'name') === wanted), ...templates.filter((t) => str(t, 'name') !== wanted)];
  for (const t of ordered) {
    const declared = t.component_types && typeof t.component_types === 'object' ? (t.component_types as Json)[type] : undefined;
    if (declared && typeof declared === 'object') return declared as Json;
  }
  return undefined;
}

/** The component component add would make, as archvow makes it (specs.add_component). */
export function newComponent(ctx: PlanContext, values: Record<string, string>): { comp: Json; container: Json } | null {
  const type = (values.type ?? '').trim();
  const name = (values.name ?? '').trim();
  if (!type || !name) return null;
  const all = containers(ctx.spec);
  const wanted = (values.container ?? '').trim();
  const container = all.find((c) => str(c, 'id') === wanted) ?? all[0];
  if (!container) return null;
  const def = typeOf(ctx, container, type);
  if (!def || !str(def, 'module') || !str(def, 'layer')) return null;
  const v = nameValues(name);
  const id = format(str(def, 'module'), v);
  const group = (values.group ?? '').trim();
  const layer = str(def, 'layer');
  const module = [str(container, 'package'), layer, ...(group ? [group] : []), id].join('.');
  const symbols = objects(def, 'symbols').map((s) => ({ ...s, name: format(str(s, 'name'), v), signature: format(str(s, 'signature'), v) }));
  const signature = (values.signature ?? '').trim();
  if (signature && symbols.length) symbols[0].signature = signature;
  return { comp: { id, layer, group, module, type, symbols }, container };
}

/** What the planned code declares: read_orders(spark: SparkSession, path: str) -> DataFrame, class JobConfig... */
function symbolsNote(comp: Json, routes: string): string {
  const parts = objects(comp, 'symbols').map((s) => {
    const name = str(s, 'name');
    const kind = str(s, 'kind');
    if (kind === 'class') return `class ${name}`;
    if (kind === 'route') return name;
    return name + (str(s, 'signature') || '(...)');
  });
  const declared = routes.split('\n').map((r) => r.trim()).filter((r) => r);
  return [...parts, ...declared].join(', ');
}

/** The feature a new component goes to without --feature (archvow's features.home_for): the project's one feature
 *  besides `shared`; none yet: one named after the system; several: none (the form asks). */
export function defaultFeature(spec: Json | null): string {
  const names = objects(spec, 'features').map((f) => str(f, 'name')).filter((n) => n && n !== 'shared');
  if (names.length === 1) return names[0];
  if (names.length === 0) return str((spec?.system ?? null) as Json | null, 'name');
  return '';
}

const MAX_EACH = 6;

/**
 * Whether an entry of the files table applies (its 4th item): `with:<field>` when the form fills that field,
 * `without:<field>` when it does not, `new-feature` when component add makes a feature (the project has none yet).
 */
function holds(condition: string, name: string, values: Record<string, string>, ctx: PlanContext): boolean {
  if (!condition) return true;
  const [kind, field] = condition.split(':');
  if (kind === 'with') return !!(values[field] ?? '').trim();
  if (kind === 'without') return !(values[field] ?? '').trim();
  if (kind === 'new-feature') {
    const feature = (values.feature ?? '').trim() || (name === 'component add' ? defaultFeature(ctx.spec) : '');
    return !!feature && !objects(ctx.spec, 'features').some((f) => str(f, 'name') === feature);
  }
  return true;
}

/** The rows of the Files block for a command and what its form holds now. */
export function filePlan(name: string, values: Record<string, string>, ctx: PlanContext): FileRow[] {
  const table = ctx.forms && ctx.forms.files && typeof ctx.forms.files === 'object' ? (ctx.forms.files as Json)[name] : undefined;
  if (!Array.isArray(table)) return [];
  const pick = (pair: unknown): string => (Array.isArray(pair) ? String(pair[ctx.fr ? 1 : 0] ?? pair[0] ?? '') : '');
  const component = (values.component ?? '').trim();
  const known = componentsOf(ctx.spec).find((x) => str(x.comp, 'id') === component);
  const made = name === 'component add' ? newComponent(ctx, values) : null;
  const rows: FileRow[] = [];
  for (const entry of table) {
    if (!Array.isArray(entry)) continue;
    let change = String(entry[0]) as Change;
    const pattern = String(entry[1] ?? '');
    let note = pick(entry[2]);
    if (!holds(String(entry[3] ?? ''), name, values, ctx)) continue;
    let paths: string[];
    if (pattern === '{code}') {
      if (name === 'component add' && (values.file ?? '').trim()) {
        paths = [values.file.trim()];
        change = 'kept';
        note = ctx.fr ? 'le fichier existant, adopté là où il est' : 'the existing file, adopted where it is';
      } else {
        const target = made ?? known;
        paths = target ? [sourcePath(target.container, target.comp)].filter((p) => p) : [];
        if (note.includes('{symbols}')) note = target ? symbolsNote(target.comp, values.route ?? '') : '';
      }
    } else if (pattern === '{test}') {
      if (name === 'component add' && (values.file ?? '').trim()) continue;
      const target = made ?? known;
      paths = target ? [testPath(target.comp)] : [];
    } else if (pattern === '{output}') {
      const output = (values.output ?? '').trim();
      paths = output ? [output] : [];
      if (ctx.outputExists) change = 'updated';
    } else {
      const id = pattern.includes('{new}') ? (made ? str(made.comp, 'id') : '') : component;
      if ((pattern.includes('{new}') || pattern.includes('{component}')) && !id) continue;
      // {home}: the feature the component is in; {feature}: the one the form names (component add: else the default)
      const home = known ? str(known.comp, 'feature') : '';
      const feature = (values.feature ?? '').trim() || (name === 'component add' ? defaultFeature(ctx.spec) : '');
      if ((pattern.includes('{home}') && !home) || (pattern.includes('{feature}') && !feature)) continue;
      if (pattern.includes('{name}') && !(values.name ?? '').trim()) continue;
      if (pattern.includes('{pack}') && !(values.pack ?? '').trim()) continue;
      const one = pattern.replace('{new}', id).replace(/\{component\}/g, id).replace(/\{name\}/g, (values.name ?? '').trim())
        .replace(/\{home\}/g, home).replace(/\{feature\}/g, feature)
        .replace('{dir}', packFolder(values.dir, true)).replace('{pack}', packFolder(values.pack, false));
      // {member}: each component of the form's feature; {each_feature}: each feature of the project
      const each = one.includes('{member}') ? componentsOf(ctx.spec).filter((x) => str(x.comp, 'feature') === feature).map((x) => str(x.comp, 'id'))
        : one.includes('{each_feature}') ? objects(ctx.spec, 'features').map((f) => str(f, 'name')) : null;
      paths = each === null ? [one] : each.slice(0, MAX_EACH).map((x) => one.replace('{member}', x).replace('{each_feature}', x));
      if (each !== null && each.length > MAX_EACH) {
        note = (note ? note + ' - ' : '') + (ctx.fr ? `et ${each.length - MAX_EACH} autres` : `and ${each.length - MAX_EACH} more`);
      }
    }
    for (const p of paths) {
      const initForce = name === 'init' && values.force === 'true';
      const action = initForce ? 'updated' : change;
      rows.push({ change: action, path: p, note });
      note = '';  // said once, on the first file of the entry
    }
  }
  return rows;
}

/** The types a person can add in each layer of the project's template, the layers in the template's
 * order (the Components tab's "+" on a layer): { layer: [{ type, description }] }, for the first container. */
export function addableByLayer(spec: Json | null, catalog: Json | null): Record<string, { type: string; description: string }[]> {
  const container = containers(spec)[0];
  const wanted = container ? str(container, 'template').split('@')[0] : '';
  const template = objects(catalog, 'templates').find((t) => str(t, 'name') === wanted);
  const out: Record<string, { type: string; description: string }[]> = {};
  if (!template || !template.component_types || typeof template.component_types !== 'object') return out;
  for (const layer of objects(template, 'layers')) out[str(layer, 'name')] = [];  // the template's order, p0 first
  for (const [type, def] of Object.entries(template.component_types as Record<string, Json>)) {
    if (!def || def.addable !== true || !str(def, 'layer')) continue;
    (out[str(def, 'layer')] ??= []).push({ type, description: str(def, 'description') });
  }
  return out;
}
