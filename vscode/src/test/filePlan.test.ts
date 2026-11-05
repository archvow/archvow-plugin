// What a form shows it will write (Files), the plugins' own fields (Save to a file), the Components tab's
// "+" per layer and the trees' icons. The IntelliJ plugin computes the same files on the same examples
// (FilePlanRegression on contract/forms/file-plans.expected.json, which UPDATE_FILE_PLANS=1 npm test writes).
import * as assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { test } from 'node:test';
import { addableByLayer, filePlan, nameValues, PlanContext } from '../filePlan';
import { Command, findCommand, planForm, preview, previewWithOutput, withPluginParameters } from '../grammar';
import { Json } from '../workItems';

const CONTRACT = path.resolve(__dirname, '..', '..', '..', 'contract');
const read = (file: string): Json => JSON.parse(fs.readFileSync(file, 'utf8')) as Json;
const FORMS = read(path.join(CONTRACT, 'forms', 'forms.json'));
const ICONS = read(path.join(CONTRACT, 'forms', 'icons.json'));
const CATALOG = read(path.join(CONTRACT, 'cli', 'catalog.json'));
const COMMANDS = withPluginParameters((read(path.join(CONTRACT, 'cli', 'commands.json')).commands as Command[]), FORMS);
const spec = (example: string): Json => read(path.join(CONTRACT, 'cli', example, 'show.json'));
const ctx = (example: string, fr = false): PlanContext => ({ spec: spec(example), catalog: CATALOG, forms: FORMS, fr });

/** The examples both plugins compute: command, form values, example project. */
const SCENARIOS: [string, Record<string, string>, string][] = [
  ['component add', { type: 'reader', name: 'orders', description: 'Read the orders', kind: 'files' }, 'pyspark-pipeline'],
  ['component add', { type: 'transformation', name: 'clean_refunds', description: 'x', group: 'refunds' }, 'pyspark-pipeline'],
  ['component add', { type: 'transformation', name: 'join_stores', description: 'x', signature: '(sales: DataFrame, stores: DataFrame) -> DataFrame' }, 'pyspark-pipeline'],
  ['component add', { type: 'reader', name: 'legacy', description: 'x', file: 'src/my_pipeline/legacy/read.py' }, 'pyspark-pipeline'],
  ['component add', { type: 'router', name: 'orders', description: 'x', route: 'list_orders: GET /orders' }, 'fastapi-service'],
  ['component add', { type: 'unknown_type', name: 'x', description: 'x' }, 'pyspark-pipeline'],
  ['component edit', { component: 'clean_orders', description: 'Keep rows where amount > 1' }, 'pyspark-pipeline'],
  ['component remove', { component: 'compute_vat' }, 'pyspark-pipeline'],
  ['decision make', { component: 'clean_orders', question: '3', answer: 'x' }, 'pyspark-pipeline'],
  ['decision propose', { component: 'clean_orders', question: '3', answer: 'x' }, 'pyspark-pipeline'],
  ['question add', { component: 'orders_reader', question: 'x?' }, 'pyspark-pipeline'],
  ['hazard add', { component: 'vat_writer', guide_word: 'partial', severity: 'critical' }, 'pyspark-pipeline'],
  ['rule add', { words: 'clean_orders,compute_vat\nAmounts are rounded half up', type: '' }, 'pyspark-pipeline'],
  ['rule add', { words: 'Every log line has the run date', all: 'true' }, 'pyspark-pipeline'],
  ['rule add', { words: 'Readers fail on a missing file', type: 'reader' }, 'pyspark-pipeline'],
  ['example add', { component: 'compute_vat', name: 'vat-20' }, 'pyspark-pipeline'],
  ['dependency add', { name: 'acme_io', supplier: 'ACME', licence: 'MIT', criticality: 'important' }, 'pyspark-pipeline'],
  ['template new', { name: 'acme-pyspark', extends: 'pyspark-pipeline', dir: '/srv/templates/' }, 'pyspark-pipeline'],
  ['changes', { start: 'v1.0.0', output: 'CHANGES.md' }, 'pyspark-pipeline'],
  ['changes', { start: 'v1.0.0' }, 'pyspark-pipeline'],
  ['check', {}, 'pyspark-pipeline'],
  ['init', { package: 'sales_pipeline' }, 'pyspark-pipeline'],
  ['init', { package: 'sales_pipeline', force: 'true' }, 'pyspark-pipeline'],
];

test('init: wizard preview is the actual non-interactive command, even with overridden answers', () => {
  const command = findCommand(COMMANDS, 'init')!;
  const plan = planForm(command, { package: 'demo_app', answer: 'language=en' }, false,
    { initAnswers: ['language=fr', 'architects=@reviewer'] });
  assert.equal(plan.kind, 'run');
  if (plan.kind !== 'run') return;
  assert.ok(preview(plan.args).includes('--answer language=en'));
  assert.ok(preview(plan.args).includes('--answer architects=@reviewer'));
  assert.ok(!preview(plan.args).includes('language=fr'));
  assert.ok(preview(plan.args).includes('--no-input'));
});

test('init: preview the primary guard-file tree, including --force behavior', () => {
  const rows = filePlan('init', { package: 'sales_pipeline' }, ctx('pyspark-pipeline'));
  assert.ok(rows.some((r) => r.path === '.archvow/spec.vow'));
  assert.ok(rows.some((r) => r.path === 'AGENTS.md'));
  assert.ok(rows.some((r) => r.path === 'README.md'));
  assert.ok(rows.every((r) => r.change === 'new'));
  const revised = filePlan('init', { package: 'sales_pipeline', force: 'true' }, ctx('pyspark-pipeline'));
  assert.ok(revised.every((r) => r.change === 'updated' || r.change === 'kept'));
});

test('component add: the vow and the card it writes, and the code and test it plans', () => {
  const rows = filePlan('component add', SCENARIOS[0][1], ctx('pyspark-pipeline'));
  assert.deepEqual(rows.map((r) => [r.change, r.path]), [
    ['new', '.archvow/components/orders_reader/spec.vow'], ['new', '.archvow/components/orders_reader/spec.md'],
    ['updated', '.archvow/spec.md'], ['planned', 'src/my_pipeline/p2_io/orders_reader.py'], ['planned', 'tests/p2_io/test_orders_reader.py']]);
  assert.equal(rows[3].note, 'read_orders(spark: SparkSession, path: str) -> DataFrame');
  // a group is a sub-folder of the layer, for the code and for its test
  const grouped = filePlan('component add', SCENARIOS[1][1], ctx('pyspark-pipeline')).map((r) => r.path);
  assert.ok(grouped.includes('src/my_pipeline/p1_domain/refunds/clean_refunds.py') && grouped.includes('tests/p1_domain/refunds/test_clean_refunds.py'));
  // the signature given replaces the type's; an existing file is adopted where it is (no test planned)
  assert.equal(filePlan('component add', SCENARIOS[2][1], ctx('pyspark-pipeline'))[3].note, 'join_stores(sales: DataFrame, stores: DataFrame) -> DataFrame');
  const adopted = filePlan('component add', SCENARIOS[3][1], ctx('pyspark-pipeline'));
  assert.deepEqual(adopted.slice(3).map((r) => [r.change, r.path]), [['kept', 'src/my_pipeline/legacy/read.py']]);
  // a type the catalog does not know: only the project page (archvow refuses it, in red under the form)
  assert.deepEqual(filePlan('component add', SCENARIOS[5][1], ctx('pyspark-pipeline')).map((r) => r.path), ['.archvow/spec.md']);
  // in French, the notes are French
  assert.match(filePlan('component add', SCENARIOS[0][1], ctx('pyspark-pipeline', true))[1].note, /sa fiche/);
  assert.deepEqual(nameValues('orders_clean'), { name: 'orders_clean', NAME: 'ORDERS_CLEAN', Name: 'OrdersClean' });
});

test('the other commands: what they update, remove or keep; rules name the cards they reach', () => {
  const p = ctx('pyspark-pipeline');
  const removed = filePlan('component remove', { component: 'compute_vat' }, p);
  assert.deepEqual(removed.map((r) => [r.change, r.path]), [['removed', '.archvow/components/compute_vat/'], ['updated', '.archvow/spec.md'],
    ['kept', 'src/my_pipeline/p1_domain/compute_vat.py']]);
  assert.deepEqual(filePlan('decision propose', { component: 'clean_orders' }, p).map((r) => r.path), ['.archvow/proposals.vow']);
  assert.deepEqual(filePlan('rule add', { words: 'clean_orders,compute_vat\nAmounts are rounded half up' }, p).slice(2).map((r) => r.path),
    ['.archvow/components/clean_orders/spec.md', '.archvow/components/compute_vat/spec.md']);
  const all = filePlan('rule add', { words: 'x', all: 'true' }, p);
  assert.equal(all.length, 2 + 4);
  assert.match(all[2].note, /and \d+ more$/);
  assert.deepEqual(filePlan('rule add', { words: 'x', type: 'reader' }, p).slice(2).map((r) => r.path), ['.archvow/components/orders_reader/spec.md']);
  assert.deepEqual(filePlan('example add', { component: 'compute_vat', name: 'vat-20' }, p).map((r) => r.path).pop(), 'tests/examples/compute_vat/vat-20/');
  assert.deepEqual(filePlan('template new', { name: 'acme-pyspark', dir: '' }, p).map((r) => r.path), ['.archvow/templates/acme-pyspark/']);
  // no component chosen yet: nothing about it is shown
  assert.deepEqual(filePlan('decision make', {}, p), []);
  assert.deepEqual(filePlan('check', {}, p), [], 'a command that reads only writes nothing');
});

test('Save to a file: changes and diff write their report there, as archvow changes ... --markdown > CHANGES.md', () => {
  const changes = findCommand(COMMANDS, 'changes')!;
  const output = changes.parameters.find((p) => p.name === 'output')!;
  assert.ok(output.plugin && output.markdown && !output.flags.length, 'the plugin\'s own field, never on archvow\'s line');
  assert.equal(changes.parameters[changes.parameters.indexOf(output) - 1].name, 'markdown', 'after As Markdown');
  const plan = planForm(changes, { start: 'a1b2c3d', end: 'v1.0.0', output: 'CHANGES.md' }, false);
  assert.deepEqual(plan.kind === 'run' && [plan.args, plan.output, plan.mutating], [['changes', '--from', 'a1b2c3d', '--to', 'v1.0.0', '--markdown'], 'CHANGES.md', false]);
  assert.equal(plan.kind === 'run' && previewWithOutput(plan.args, plan.output), 'archvow changes --from a1b2c3d --to v1.0.0 --markdown > CHANGES.md');
  const shown = planForm(changes, { start: 'v1.0.0', markdown: 'true' }, false);
  assert.deepEqual(shown.kind === 'run' && shown.args, ['changes', '--from', 'v1.0.0', '--markdown', '--json'], 'without a file: shown here, as before');
  assert.ok(findCommand(COMMANDS, 'diff')!.parameters.some((p) => p.plugin && p.name === 'output'));
  assert.equal(filePlan('changes', { start: 'v1', output: 'CHANGES.md' }, { ...ctx('pyspark-pipeline'), outputExists: true })[0].change, 'updated');
  // the host writes it inside the project only, and opens it
  const host = fs.readFileSync(path.join(__dirname, '..', '..', 'src', 'workspace.ts'), 'utf8');
  assert.match(host, /private async writeReport\(/);
  assert.match(host, /'rev-list', '--max-parents=0', 'HEAD'/);
});

test('the Components tab: a "+" per layer offers the types of that layer, in the template\'s order', () => {
  const layers = addableByLayer(spec('pyspark-pipeline'), CATALOG);
  assert.deepEqual(Object.keys(layers), ['p0_common', 'p1_domain', 'p2_io', 'p3_jobs', 'p4_entrypoints']);
  assert.deepEqual(layers.p2_io.map((x) => x.type), ['reader', 'writer']);
  assert.deepEqual(layers.p1_domain.map((x) => x.type), ['schema', 'transformation']);
  assert.ok(!layers.p0_common.some((x) => x.type === 'foundation'), 'the template\'s own types are not offered');
  const page = fs.readFileSync(path.join(__dirname, '..', '..', 'media', 'workspace.js'), 'utf8');
  for (const piece of ["data-a=\"addComponent\"", 'data-a="addHere"', 'data-a="addType"', "command: 'component add', values: { type: e.dataset.id }"]) {
    assert.ok(page.includes(piece), piece);
  }
});

test('the trees\' icons: a drawing for every type, command group and command (contract/forms/icons.json)', () => {
  const shapes = ICONS.shapes as Record<string, unknown[]>;
  for (const template of CATALOG.templates as Json[]) {
    for (const type of Object.keys(template.component_types as Json)) assert.ok(type in shapes, `no icon for the type ${type}`);
  }
  for (const group of FORMS.command_groups as Json[]) assert.ok((ICONS.groups as Json)[group.id] in shapes, `no icon for the group ${group.id}`);
  const verbs = ICONS.verbs as Record<string, string>;
  const own = ICONS.commands as Record<string, string>;
  for (const c of COMMANDS) {
    const kind = own[c.path.join(' ')] ?? own[c.path[0]] ?? verbs[c.path[c.path.length - 1]];
    assert.ok(kind && kind in shapes, `no icon for ${c.path.join(' ')}`);
  }
  for (const [name, list] of Object.entries(shapes)) {
    for (const shape of list as unknown[][]) {
      assert.ok(['l', 'c', 'C', 'e', 'r', 'p', 'P'].includes(String(shape[0])), `${name}: unknown shape ${shape[0]}`);
      if (shape[0] === 'p' || shape[0] === 'P') assert.match(String(shape[1]), /^[MLQCZ0-9 .\-]+$/, `${name}: absolute M L Q C Z only`);
      else for (const n of shape.slice(1)) assert.ok(typeof n === 'number' && n >= 0 && n <= 14, `${name}: inside the 14 x 14 grid`);
    }
  }
});

test('the IntelliJ plugin computes the same files (contract/forms/file-plans.expected.json)', () => {
  const plans = SCENARIOS.flatMap(([command, values, example]) => [false, true].map((fr) => ({
    command, values, example, fr, rows: filePlan(command, values, ctx(example, fr)) })));
  const addable = ['pyspark-pipeline', 'fastapi-service', 'python-app'].map((example) => ({ example, layers: addableByLayer(spec(example), CATALOG) }));
  const cases: { command: string; values: Record<string, string> }[] = [{ command: 'changes', values: { start: 'a1b2c3d', end: 'v1.0.0', output: 'CHANGES.md' } },
    { command: 'changes', values: { start: 'v1' } }, { command: 'diff', values: { base: 'develop', output: 'SPEC-CHANGES.md' } }];
  const outputs = cases.map(({ command, values }) => {
    const plan = planForm(findCommand(COMMANDS, command)!, values, false);
    return { command, values, line: plan.kind === 'run' ? previewWithOutput(plan.args, plan.output) : '', output: plan.kind === 'run' ? plan.output ?? '' : '' };
  });
  const text = JSON.stringify({ plans, addable, outputs }, null, 1) + '\n';
  const file = path.join(CONTRACT, 'forms', 'file-plans.expected.json');
  if (process.env.UPDATE_FILE_PLANS === '1') fs.writeFileSync(file, text);
  assert.ok(fs.existsSync(file), 'run UPDATE_FILE_PLANS=1 npm test once');
  assert.equal(fs.readFileSync(file, 'utf8'), text, 'filePlan.ts changed: UPDATE_FILE_PLANS=1 npm test, then FilePlanRegression');
});
