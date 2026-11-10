// The workspace's rules, the same as the IntelliJ workspace's, on archvow 1.25's real outputs
// (../contract/cli): forms into command lines, prefill, the pending and resolved work, the counts on each
// view, the formatter, where the project stands.
import * as assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import { test } from 'node:test';
import { renderResult } from '../formatter';
import { Command, findCommand, inline, isMutating, needsFullCard, planForm, prefill, promptCommand, taskNumber } from '../grammar';
import { componentTypes, derive, readiness, ruleExplanation } from '../projectState';
import { architectureRows, buildWork, issueFix, Json, needsFeatureBranch, objects, otherCategory, str, viewForItem } from '../workItems';

const CLI = path.resolve(__dirname, '..', '..', '..', 'contract', 'cli');
const read = (...parts: string[]): Json => JSON.parse(fs.readFileSync(path.join(CLI, ...parts), 'utf8'));
const commands = (read('commands.json') as { commands: Command[] }).commands;
const command = (name: string): Command => {
  const c = findCommand(commands, name);
  assert.ok(c, name);
  return c;
};
const EXAMPLES = ['fastapi-service', 'pyspark-pipeline', 'python-app'];
const snapshots = (example: string): Record<string, Json> => Object.fromEntries(
  fs.readdirSync(path.join(CLI, example, 'show')).map((f) => [f.replace(/\.json$/, ''), read(example, 'show', f)]));

test('the grammar is archvow 1.26\'s, with every command the workspace offers', () => {
  assert.match(fs.readFileSync(path.join(CLI, 'version.txt'), 'utf8'), /1\.26\./);
  for (const name of ['init', 'check', 'next', 'show', 'decision make', 'decision propose', 'decision accept', 'question add',
    'question accept', 'hazard add', 'hazard dismiss', 'hazard edit', 'hazard accept', 'rule accept', 'brief show', 'invariant add', 'invariant edit', 'invariant remove', 'component accept', 'assumption accept', 'rule add', 'column set', 'prompt', 'doctor', 'audit', 'diff', 'review', 'version', 'workspace', 'pack new', 'pack template', 'pack agent', 'pack rule', 'pack add',
    'pack update', 'pack remove', 'pack check']) {
    command(name);
  }
});

test('a form becomes the command line archvow expects (the IntelliJ submitForm rules)', () => {
  const run = (name: string, values: Record<string, string>, options = {}) => planForm(command(name), values, false, options);
  assert.deepEqual(run('decision make', { component: 'clean_orders', question: '5', answer: 'Keep them' }),
    { kind: 'run', args: ['decision', 'make', 'clean_orders', '5', 'Keep them', '--json'], mutating: true, stdinFromEditor: false });
  const removal = run('component remove', { component: 'x' });
  assert.ok(removal.kind === 'run' && removal.mutating && removal.args.includes('--yes'));
  const rule = run('rule add', { words: 'clean_orders\nRows are never dropped silently' });
  assert.ok(rule.kind === 'run' && rule.args.slice(0, 4).join('|') === 'rule|add|clean_orders|Rows are never dropped silently');
  assert.equal(run('rule add', { words: 'clean_orders' }, { components: ['clean_orders'] }).kind, 'error');
  assert.equal(run('question add', { component: 'x' }).kind, 'error'); // the question is required
  const read_ = run('check', { complete: 'true' });
  assert.ok(read_.kind === 'run' && !read_.mutating && read_.args.join(' ') === 'check --complete --json');
  const stdin = run('check', { stdin: 'true' });
  assert.ok(stdin.kind === 'run' && stdin.stdinFromEditor);
  const init = run('init', { package: 'orders', answer: 'mode=guided' }, { initAnswers: ['mode=strict', 'language=fr'] });
  assert.ok(init.kind === 'run');
  if (init.kind === 'run') {
    assert.ok(init.args.includes('--no-input'));
    assert.ok(init.args.includes('mode=guided') && !init.args.includes('mode=strict'), 'a typed answer wins over the wizard');
    assert.ok(init.args.includes('language=fr'));
  }
  assert.deepEqual(run('specify', { component: 'clean_orders' }), { kind: 'clarify', component: 'clean_orders' });
  assert.equal(run('prompt', { component: 'x', task: 'T0' }).kind, 'error');
  const prompt = run('prompt', { component: 'x', task: 'T2' });
  assert.ok(prompt.kind === 'run' && prompt.args.join(' ') === 'prompt x --task 2');
  assert.equal(isMutating(command('docs'), ['docs', '--check']), false);
  assert.equal(isMutating(command('docs'), ['docs']), true);
});

test('prefill: the selected component, and for "Edit spec" what the spec says, nothing guessed', () => {
  const view = read('pyspark-pipeline', 'show', 'clean_orders.json');
  const values = prefill('component edit', {}, 'clean_orders', view);
  assert.equal(values.component, 'clean_orders');
  assert.equal(values.description, str(view, 'description'));
  assert.deepEqual(prefill('question add', {}, 'clean_orders', view), { component: 'clean_orders' });
  assert.deepEqual(prefill('component edit', { component: 'clean_orders', signature: '(df) -> df' }, 'x', view),
    { component: 'clean_orders', signature: '(df) -> df' });
});

test('in-place actions, task prompts', () => {
  assert.deepEqual(inline.decide('c', '2', ' Fail fast '), ['decision', 'make', 'c', '2', 'Fail fast', '--json']);
  assert.deepEqual(inline.hazard('c', 'missing', 'high', ''), ['hazard', 'add', 'c', 'missing', 'high', '--json']);
  // archvow 1.19: without a reason, the decision that says there is nothing to protect is the reason
  assert.deepEqual(inline.dismiss('c', 'S2', ' '), ['hazard', 'dismiss', 'c', 'S2', '--json']);
  assert.deepEqual(inline.dismiss('c', 'S2', 'only one file'), ['hazard', 'dismiss', 'c', 'S2', 'only one file', '--json']);
  // a hazard changed in place keeps its id and its invariants
  assert.deepEqual(inline.editHazard('c', 'H1', ' new words ', 'critical'), ['hazard', 'edit', 'c.H1', 'new words', '--severity', 'critical', '--json']);
  assert.deepEqual(inline.editHazard('c', 'c.H2', '', 'low'), ['hazard', 'edit', 'c.H2', '--severity', 'low', '--json']);
  assert.equal(taskNumber('t3'), '3');
  assert.deepEqual(promptCommand('c', ''), ['prompt', 'c']);
  assert.ok(needsFullCard(1, 'x is one task: its card is small enough'));
});

for (const example of EXAMPLES) {
  test(`${example}: the work, the counts and the architecture table from archvow's outputs`, () => {
    const spec = read(example, 'show.json');
    const check = read(example, 'check.json');
    const views = snapshots(example);
    const work = buildWork(spec, check, '', views, false);
    const ids = objects(spec, 'containers').flatMap((c) => objects(c, 'components').map((x) => str(x, 'id')));
    for (const item of [...work.pending, ...work.resolved]) {
      if (item.component) assert.ok(ids.includes(item.component), item.title);
      assert.ok(viewForItem(item) >= 0 && viewForItem(item) <= 7);
    }
    for (const item of work.otherPending) assert.ok(otherCategory(item), item.title);
    const sum = Object.values(work.pendingByComponent).reduce((a, b) => a + b, 0);
    assert.equal(sum, work.pending.filter((i) => i.component && i.source !== 'check').length);
    for (const [id, view] of Object.entries(views)) {
      const d = derive(view);
      assert.equal(d.counts.total[0], objects(view, 'decisions').length, id);
      assert.equal(d.counts.pending[0], objects(view, 'decisions').filter((x) => !str(x, 'answer')).length, id);
      assert.equal(d.safety.length, objects(view, 'safety').length, id);
      d.safety.forEach((s) => assert.ok(['OPEN', 'NEEDS INVARIANT', 'RESOLVED'].includes(s.status), id));
    }
    const rows = architectureRows(spec, check, views, work.pendingByComponent);
    assert.deepEqual(rows.map((r) => r.id).sort(), [...ids].sort());
    rows.forEach((r) => assert.ok(r.state, r.id));
  });
}

test('project check violations do not become component pending badges', () => {
  const view = { component: 'orders_reader', decisions: [], safety: [], questions: [], assumptions: [], tasks: [] };
  const check = { violations: [{ code: 'ARCH036', severity: 'error', component: 'orders_reader',
    message: 'invariant orders_reader.INV1: must mitigate one hazard', file: '.archvow/spec.vow' }] };
  const work = buildWork(null, check, '', { orders_reader: view }, false);
  assert.equal(work.pending.filter((item) => item.source === 'check').length, 1);
  assert.equal(work.pendingByComponent.orders_reader || 0, 0, 'checks are not component work');
  assert.equal(work.otherPending.filter((item) => item.source === 'check').length, 1, 'diagnostic stays available globally');
});

test('the pyspark example has work to do: decisions or safety questions pending, warnings in Other', () => {
  const work = buildWork(read('pyspark-pipeline', 'show.json'), read('pyspark-pipeline', 'check.json'), '', snapshots('pyspark-pipeline'), true);
  assert.ok(work.pending.length > 0);
  assert.ok(work.pending.some((i) => i.command === 'hazard add'), 'safety questions to answer');
  assert.ok(work.pending.some((i) => i.action === 'Examiner le hazard'), 'French labels');
});

test('a diagnostic with a documented fix prefills a form, never runs it', () => {
  assert.deepEqual(issueFix('ARCH004', 'archvow component edit clean_orders --signature "(df: DataFrame) -> DataFrame"', ''),
    { command: 'component edit', values: { component: 'clean_orders', signature: '(df: DataFrame) -> DataFrame' }, label: 'Review signature / Vérifier la signature' });
  assert.equal(issueFix('ARCH002', 'anything', 'x'), null);
  assert.ok(needsFeatureBranch('develop') && !needsFeatureBranch('feature/US-1-x'));
});

test('the formatter reads check, next and prompt as people read them; text from archvow is escaped', () => {
  const check = read('pyspark-pipeline', 'check.json');
  const html = renderResult('check', 0, JSON.stringify(check), '', false, false);
  assert.ok(html.includes(String(objects(check, 'violations')[0].code)));
  assert.match(html, /class="check-files-marker" data-check-path="/,
    'check locations defer to the exact Files renderer from the webview');
  const next = renderResult('next', 0, JSON.stringify(read('pyspark-pipeline', 'next.json')), '', false, true);
  assert.ok(next.includes('clean_orders'));
  const prompt = renderResult('prompt', 0, '# Task <script>alert(1)</script>', '', false, false);
  assert.ok(!prompt.includes('<script>') && prompt.includes('&lt;script&gt;'));
  assert.ok(renderResult('show', 1, '', 'No module named archvow', false, false).includes('not installed'));
});

test('where the project stands, the catalog\'s component types, the meaning of a code', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'archvow-ready-'));
  assert.equal(readiness(null), 'NO_PROJECT');
  assert.equal(readiness(path.join(dir, 'gone')), 'ROOT_MISSING');
  assert.equal(readiness(dir), 'NOT_INITIALIZED');
  fs.mkdirSync(path.join(dir, '.archvow'));
  fs.writeFileSync(path.join(dir, '.archvow', 'spec.vow'), '');
  assert.equal(readiness(dir), 'SPEC_INACCESSIBLE');
  fs.writeFileSync(path.join(dir, '.archvow', 'spec.vow'), 'vow 1 project\n');
  assert.equal(readiness(dir), 'READY');
  const types = componentTypes(read('catalog.json'));
  assert.ok('transformation' in types && 'reader' in types, Object.keys(types).join(','));
  const rules = fs.readFileSync(path.join(CLI, 'rules.md'), 'utf8');
  assert.match(ruleExplanation(rules, 'ARCH030'), /\w/);
  assert.match(ruleExplanation(rules, 'ARCH054'), /\w/, 'archvow 1.13\'s person-only commands');
  assert.equal(ruleExplanation(rules, 'XYZ999'), '');
});
