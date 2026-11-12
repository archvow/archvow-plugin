// What the command forms offer to choose from (choices.ts), on what archvow 1.18 printed for its three
// examples (contract/cli): nobody has to know a component id, a question number, a hazard id, a kind or
// a branch by heart. The IntelliJ plugin offers exactly the same (contract/choices/expected.json,
// ChoicesRegression): UPDATE_CHOICES=1 npm test rewrites it.
import * as assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { test } from 'node:test';
import { bestBase, Book, choiceBook, choicesNow, Field, missingRef, refKinds, refNames } from '../choices';
import { Command, findCommand, planForm } from '../grammar';
import { Json } from '../workItems';

const CONTRACT = path.resolve(__dirname, '..', '..', '..', 'contract');
const read = (...parts: string[]): Json => JSON.parse(fs.readFileSync(path.join(CONTRACT, ...parts), 'utf8')) as Json;
const COMMANDS = (read('cli', 'commands.json') as { commands: Command[] }).commands;
const EXAMPLES = ['python-app', 'fastapi-service', 'pyspark-pipeline'];
const REFS = ['develop', 'feature/vat', 'main', 'origin/develop', 'origin/main', 'v1.0.0'];

function input(example: string, fr = false, refs = REFS): Parameters<typeof choiceBook>[1] {
  const dir = path.join(CONTRACT, 'cli', example, 'show');
  const snapshots = Object.fromEntries(fs.readdirSync(dir).filter((f) => f.endsWith('.json'))
    .map((f) => [f.replace(/\.json$/, ''), read('cli', example, 'show', f)]));
  return { spec: read('cli', example, 'show.json'), snapshots, catalog: read('cli', 'catalog.json'), kinds: read('cli', 'kinds.json'),
    refs, base: bestBase(refs, 'feature/vat', true, 'origin/main'), fr };
}
const book = (example: string, fr = false): Book => choiceBook(COMMANDS, input(example, fr));
const values = (field: Field, form: Record<string, string>): string[] => choicesNow(field, form).map((c) => c.value);

test('every form that takes a component offers the project\'s components, Edit spec with what each one has', () => {
  const b = book('pyspark-pipeline');
  const ids = (read('cli', 'pyspark-pipeline', 'show.json').containers as Json[]).flatMap((c) => (c.components as Json[]).map((x) => String(x.id)));
  for (const [command, fields] of Object.entries(b)) {
    if (fields.component) assert.deepEqual(values(fields.component, {}), ids, command);
  }
  for (const name of ['component edit', 'component remove', 'show', 'prompt', 'decision make', 'question remove', 'hazard add', 'example remove']) {
    assert.equal(b[name].component.kind, 'select', name);
  }
  const edit = b['component edit'].component.choices!.find((c) => c.value === 'clean_orders')!;
  assert.match(edit.label, /^clean_orders — transformation$/);
  assert.ok(edit.fill && edit.fill.description, 'Edit spec fills the description');
  assert.equal(b['component remove'].component.choices![0].fill, undefined);
});

test('question add: no number to give - archvow numbers it, and the form says which; question remove lists the questions', () => {
  const b = book('pyspark-pipeline');
  const view = read('cli', 'pyspark-pipeline', 'show', 'clean_orders.json');
  const n = (view.decisions as Json[]).length;
  assert.equal(b['question add'].question.kind, 'note');
  assert.match(b['question add'].question.notes!.clean_orders, new RegExp(`becomes question ${n + 1} of clean_orders`));
  assert.match(book('pyspark-pipeline', true)['question add'].question.notes!.clean_orders, new RegExp(`devient la question ${n + 1} de clean_orders`));
  const remove = b['question remove'].number;
  assert.equal(remove.by, 'component');
  // only the questions a person added: the example's are all the template's (archvow 2.0.1)
  assert.deepEqual(values(remove, { component: 'clean_orders' }),
    (view.decisions as Json[]).filter((d) => !d.template).map((d) => String(d.number)));
  assert.deepEqual(values(remove, {}), [], 'nothing before the component is chosen');
});

test('question remove: with archvow 1.18 (template: true on its questions), only those a person added', () => {
  const data = input('pyspark-pipeline');
  const view = JSON.parse(JSON.stringify(data.snapshots.clean_orders)) as Json;
  (view.decisions as Json[]).forEach((d, i) => { d.template = i < 2; });
  (view.decisions as Json[]).push({ number: 9, question: 'Is the currency checked?', answer: null, template: false });
  const field = choiceBook(COMMANDS, { ...data, snapshots: { ...data.snapshots, clean_orders: view } })['question remove'].number;
  assert.deepEqual(values(field, { component: 'clean_orders' }), [...(view.decisions as Json[]).slice(2).map((d) => String(d.number))]);
  assert.match(choicesNow(field, { component: 'clean_orders' }).at(-1)!.label, /^9 — Is the currency checked\?/);
  (view.decisions as Json[]).splice(2);
  const none = choiceBook(COMMANDS, { ...data, snapshots: { ...data.snapshots, clean_orders: view } })['question remove'].number;
  assert.deepEqual(values(none, { component: 'clean_orders' }), []);
  assert.match(none.empty!, /No question a person added: the template's questions stay/);
});

test('decision make: the open questions first, then the answered ones; choosing one fills the answer recorded or suggested', () => {
  const b = book('fastapi-service');
  const field = b['decision make'].question;
  const view = read('cli', 'fastapi-service', 'show', 'orders_repository.json');
  const offered = choicesNow(field, { component: 'orders_repository' });
  const open = (view.decisions as Json[]).filter((d) => !d.answer).length;
  assert.ok(offered.slice(0, open).every((c) => !/\(answered\)$/.test(c.label)));
  assert.ok(offered.some((c) => c.value === 'P1'), 'the personal-data questions too');
  const p1 = offered.find((c) => c.value === 'P1')!;
  assert.equal(p1.fill!.answer, String((view.questions as Json[]).find((q) => q.key === 'P1')!.answer));
  assert.deepEqual(values(b['decision accept'].number, { component: 'orders_repository' }), [], 'no AI proposal recorded');
  assert.match(b['decision accept'].number.empty!, /No AI proposal/);
});

test('component add: only the types of the project\'s template; a reader or a writer offers its kinds, the default first', () => {
  const b = book('pyspark-pipeline');
  const types = values(b['component add'].type, {});
  assert.ok(types.includes('reader') && types.includes('transformation'));
  assert.ok(!types.includes('router'), 'a FastAPI router is not a type of a PySpark pipeline');
  assert.match(b['component add'].type.note!, /pyspark-pipeline, the template of this project/);
  const kind = b['component add'].kind;
  assert.equal(kind.by, 'type');
  assert.deepEqual(values(kind, { type: 'reader' }), ['files', 'table', 'jdbc', 'connector', 'custom']);
  assert.match(choicesNow(kind, { type: 'reader' })[0].label, /^files — \(default\) Reads one dataset/);
  assert.match(choicesNow(kind, { type: 'writer' })[1].label, /^table — Writes one table of the catalog/);
  assert.deepEqual(values(kind, { type: 'transformation' }), []);
  assert.match(kind.empty!, /This type has no kinds: leave it empty/);
  assert.ok(values(book('fastapi-service')['component add'].type, {}).includes('router'));
});

test('the kinds of archvow 1.18\'s catalog come first; kinds.json only for an archvow whose catalog has none', () => {
  const data = input('pyspark-pipeline');
  const catalog = JSON.parse(JSON.stringify(data.catalog)) as Json;
  const pyspark = (catalog.templates as Json[]).find((t) => t.name === 'archvow/pyspark-pipeline')!;
  pyspark.component_types.reader.kinds = [{ name: 'files', description: 'Reads files', default: true }, { name: 'kafka', description: 'Reads a topic', default: false }];
  const kind = choiceBook(COMMANDS, { ...data, catalog })['component add'].kind;
  assert.deepEqual(values(kind, { type: 'reader' }), ['files', 'kafka']);
});

test('the project\'s hazards, invariants, rules; rule edit fills the rule\'s text; invariant add --prevents offers the component\'s hazards', () => {
  const b = book('pyspark-pipeline');
  const spec = read('cli', 'pyspark-pipeline', 'show.json');
  assert.deepEqual(values(b['hazard remove'].id, {}), (spec.hazards as Json[]).map((h) => String(h.id)));
  assert.match(choicesNow(b['hazard remove'].id, {})[0].label, /^clean_orders\.H1 — high: A valid input row is dropped$/);
  assert.deepEqual(values(b['invariant remove'].id, {}), (spec.invariants as Json[]).map((i) => String(i.id)));
  const rule = choicesNow(b['rule edit'].id, {})[0];
  assert.deepEqual([rule.value, rule.fill], ['R1', { text: 'vat is rounded half-up to 2 decimals' }]);
  assert.equal(choicesNow(b['rule remove'].id, {})[0].fill, undefined);
  assert.deepEqual(values(b['invariant add'].prevents, { component: 'clean_orders' }),
    (spec.hazards as Json[]).filter((h) => h.component === 'clean_orders').map((h) => String(h.id)));
  assert.match(b['dependency remove'].name.empty!, /No dependency declared/);
});

test('hazard add / dismiss: the component\'s safety questions, by guide word, the open ones first', () => {
  const b = book('pyspark-pipeline');
  const offered = choicesNow(b['hazard dismiss'].guide_word, { component: 'orders_reader' });
  const view = read('cli', 'pyspark-pipeline', 'show', 'orders_reader.json');
  assert.equal(offered.length, (view.safety as Json[]).length);
  assert.match(offered[0].label, /^missing — S1 Input missing, empty or late/);
});

test('pack template: the templates to extend; the branches to compare with, and the one a merge request targets', () => {
  const b = book('python-app');
  assert.equal(b['pack template'].extends.kind, 'suggest');
  assert.ok(values(b['pack template'].extends, {}).includes('archvow/pyspark-pipeline'));
  for (const command of ['next', 'check', 'diff', 'review']) {
    assert.equal(b[command].base.kind, 'suggest', command);
    assert.deepEqual(values(b[command].base, {}), REFS);
  }
  assert.match(b.diff.base.note!, /Local branches, fetched remote branches and tags/);
  assert.deepEqual(values(b.changes.start, {}), REFS);
  const alone = choiceBook(COMMANDS, input('python-app', false, []));
  assert.match(alone.diff.base.note!, /No other branch in this repository yet/);
});

test('Git reference kinds are derived from full refs, not branch-name conventions', () => {
  assert.deepEqual(refKinds('refs/heads/feature/payments\nrefs/remotes/company/develop\nrefs/tags/release\nrefs/remotes/company/HEAD\n'),
    { 'feature/payments': 'local', 'company/develop': 'remote', release: 'tag' });
});

test('the base: develop on a Git flow feature branch, else what origin\'s HEAD names, main, master - only what exists', () => {
  assert.equal(bestBase(REFS, 'feature/vat', true, 'origin/main'), 'origin/develop');
  assert.equal(bestBase(REFS, 'feature/vat', false, 'origin/main'), 'origin/main');
  assert.equal(bestBase(['main', 'feature/x'], 'feature/x', false), 'main', 'no remote: the local main');
  assert.equal(bestBase(['master'], 'feature/x', true), 'master');
  assert.equal(bestBase(['main'], 'main', false), '', 'never the branch itself');
  assert.equal(bestBase([], '', false), '');
  assert.deepEqual(refNames('refs/heads/main\nrefs/heads/feature/a\nrefs/remotes/origin/HEAD\nrefs/remotes/origin/main\nrefs/tags/v1\n'),
    ['main', 'feature/a', 'origin/main', 'v1']);
});

test('a base that does not exist: which ones do, and why origin/main may be missing (the screenshot)', () => {
  assert.equal(missingRef('main', ['main'], false), '');
  const local = missingRef('origin/main', ['main', 'feature/x'], false);
  assert.match(local, /^'origin\/main' is not a branch of this repository \(no remote 'origin' here, or it was never fetched: git fetch origin\)\. /);
  assert.match(local, /Choose one that exists: main, feature\/x\.$/);
  assert.match(missingRef('origin/main', [], true), /n'a pas encore d'autre branche/);
  assert.doesNotMatch(missingRef('origin/mian', ['origin/main'], false), /no remote/);
});

test('Edit spec sends only what the person changed; nothing changed is said, not run', () => {
  const edit = findCommand(COMMANDS, 'component edit')!;
  const baseline = { description: 'Keep rows where amount > 0', uses: 'config', spec: 'US-1' };
  const plan = planForm(edit, { component: 'clean_orders', ...baseline, description: 'Keep rows where amount > 1' }, false, { baseline });
  assert.deepEqual(plan.kind === 'run' && plan.args, ['component', 'edit', 'clean_orders', '--description', 'Keep rows where amount > 1', '--json']);
  const same = planForm(edit, { component: 'clean_orders', ...baseline }, false, { baseline });
  assert.equal(same.kind, 'error');
  assert.match(same.kind === 'error' ? same.message : '', /^Nothing changed/);
  // from a diagnostic (no baseline): what it gives is sent as it is
  const fix = planForm(edit, { component: 'clean_orders', uses: 'config,orders_schema' }, false);
  assert.deepEqual(fix.kind === 'run' && fix.args, ['component', 'edit', 'clean_orders', '--uses', 'config,orders_schema', '--json']);
});

test('the IntelliJ plugin offers the same on every example, in English and in French (contract/choices/expected.json)', () => {
  const entries = EXAMPLES.flatMap((example) => [false, true].map((fr) => ({ example, fr, book: book(example, fr) })));
  // the base and the refused base, on the cases above
  const bases = [[REFS, 'feature/vat', true, 'origin/main'], [REFS, 'feature/vat', false, 'origin/main'], [['main', 'feature/x'], 'feature/x', false, ''],
    [['master'], 'feature/x', true, ''], [['main'], 'main', false, ''], [[], '', false, '']]
    .map(([refs, current, gitflow, head]) => ({ refs, current, gitflow, head,
      base: bestBase(refs as string[], current as string, gitflow as boolean, head as string) }));
  const missing = [['origin/main', ['main', 'feature/x']], ['origin/mian', ['origin/main']], ['main', ['main']], ['origin/main', []]]
    .flatMap(([ref, refs]) => [false, true].map((fr) => ({ ref, refs, fr, message: missingRef(ref as string, refs as string[], fr) })));
  const names = refNames('refs/heads/main\nrefs/heads/feature/a\nrefs/remotes/origin/HEAD\nrefs/remotes/origin/main\nrefs/tags/v1\n');
  const text = JSON.stringify({ refs: REFS, current: 'feature/vat', gitflow: true, remoteHead: 'origin/main', entries, bases, missing, names }, null, 1) + '\n';
  const file = path.join(CONTRACT, 'choices', 'expected.json');
  if (process.env.UPDATE_CHOICES === '1') {
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, text);
  }
  assert.ok(fs.existsSync(file), 'run UPDATE_CHOICES=1 npm test once');
  assert.equal(fs.readFileSync(file, 'utf8'), text, 'choices.ts changed: UPDATE_CHOICES=1 npm test, then ChoicesRegression');
});
