// The Overview's cards (overview.ts, 0.25.0) on what archvow prints for its examples (contract/cli/<example>/
// workspace.json): the first steps, the first finding, the suggested answers of each row of next. The IntelliJ
// plugin reads them the same (contract/overview/expected.json, OverviewRegression): UPDATE_OVERVIEW=1 npm test
// rewrites it.
import * as assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { test } from 'node:test';
import { suggested } from '../formatter';
import { firstFinding, firstSteps, overview } from '../overview';
import { buildWork, Json } from '../workItems';

const CONTRACT = path.resolve(__dirname, '..', '..', '..', 'contract');
const EXAMPLES = ['fastapi-service', 'pyspark-pipeline', 'python-app'];
const workspace = (example: string): Json => JSON.parse(fs.readFileSync(path.join(CONTRACT, 'cli', example, 'workspace.json'), 'utf8')) as Json;

test('the first steps: the first not done is the current one; all done, the card is gone', () => {
  const step = (name: string, done: boolean): Json => ({ step: name, title: name, detail: '', command: 'archvow ' + name, done });
  assert.equal(firstSteps([step('a', true), step('b', true)]), null);
  assert.equal(firstSteps([]), null);
  assert.equal(firstSteps(undefined), null);
  const some = firstSteps([step('a', true), step('b', false), step('c', false)]);
  assert.deepEqual([some?.done, some?.current, some?.steps.length], [1, 1, 3]);
  // a spec-phase project (pyspark-pipeline recorded without its code): built not yet, so the card shows
  const spec = firstSteps(workspace('pyspark-pipeline').first_steps);
  assert.ok(spec !== null && spec.steps[spec.current].step === 'build');
  assert.deepEqual(spec.steps.map((s) => s.step), ['foundations', 'feature', 'component', 'build', 'commit']);
});

test('the first finding leads with its rule in words; the Architecture card counts its own items only', () => {
  const f = firstFinding({ violations: [{ code: 'ARCH005', severity: 'error', title: 'Illegal dependency between layers',
    message: "layer 'p1_domain' may not import from 'p2_io'", file: 'src/x/p1_domain/compute_vat.py', line: 16 }] });
  assert.deepEqual([f?.title, f?.where], ['Illegal dependency between layers', 'ARCH005 · compute_vat.py:16']);
  assert.equal(firstFinding({ violations: [] }), null);
  assert.equal(firstFinding({ violations: [{ code: 'ARCH035', file: '' }] })?.where, 'ARCH035');
  const w = workspace('pyspark-pipeline');
  const work = buildWork(w.project, w.check, '', w.components, false);
  const o = overview(work, w.check, w.first_steps);
  const checks = work.pending.filter((i) => i.source === 'check' || i.command === 'open-diagnostics').length;
  assert.equal(o.itemsOpen + checks, work.pending.length);  // architecture + checks = pending, as before
  assert.equal(o.itemsTotal, o.itemsOpen + o.itemsDone);
  assert.equal(o.errors, Number(w.check.errors));
});

test('the suggested answers: "4 of the 9 decisions", none when archvow offers none', () => {
  assert.equal(suggested(4, 'blocked: 9 decisions to make', false), '4 of the 9 decisions');
  assert.equal(suggested(1, 'blocked: 1 decision to make', true), '1 des 1 décision');
  assert.equal(suggested(0, 'blocked: 2 decisions to make', false), 'none');
  assert.equal(suggested(2, 'ready - 3 decisions to propose first', false), '2 of the 3 decisions');
});

test('both IDEs read the examples the same (contract/overview/expected.json)', () => {
  const cases = EXAMPLES.map((example) => {
    const w = workspace(example);
    const rows = (w.next as Json[]).filter((r) => 'suggested' in r);
    return { example, firstSteps: firstSteps(w.first_steps), firstFinding: firstFinding(w.check),
      suggested: rows.map((r) => ({ component: String(r.component), en: suggested(Number(r.suggested), String(r.readiness), false),
        fr: suggested(Number(r.suggested), String(r.readiness), true) })) };
  });
  const text = JSON.stringify(cases, null, 1) + '\n';
  const file = path.join(CONTRACT, 'overview', 'expected.json');
  if (process.env.UPDATE_OVERVIEW === '1') {
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, text);
  }
  assert.ok(fs.existsSync(file), 'run UPDATE_OVERVIEW=1 npm test once');
  assert.equal(fs.readFileSync(file, 'utf8'), text, 'overview.ts changed: UPDATE_OVERVIEW=1 npm test, then OverviewRegression');
});
