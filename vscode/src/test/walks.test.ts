// The walks (walks.ts, 1.1.0) on what archvow prints for its examples (contract/cli/<example>/workspace.json), as
// recorded and in each mode: each feature's Copy AI prompt, the Specify walks (the foundations, each feature, from
// each component) and the Build walks. The IntelliJ plugin reads them the same (contract/walks/expected.json,
// WalksRegression): UPDATE_WALKS=1 npm test rewrites it.
import * as assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { test } from 'node:test';
import { budgetReached, buildQueue, featureOf, membersOf, promptState, readyToBuild, specifyQueue, walkState } from '../walks';
import { Json } from '../workItems';

const CONTRACT = path.resolve(__dirname, '..', '..', '..', 'contract');
const EXAMPLES = ['fastapi-service', 'pyspark-pipeline', 'python-app'];
const MODES = ['', 'strict', 'guided'];  // '' as recorded
/** As recorded, or changed: `blocked` - nothing of a feature ready (decisions to make); `budget` - the merge request full. */
const VARIANTS = ['', 'blocked', 'budget'];
const workspace = (example: string, mode: string, variant = ''): Json => {
  const w = JSON.parse(fs.readFileSync(path.join(CONTRACT, 'cli', example, 'workspace.json'), 'utf8')) as Json;
  if (mode) (w.project as Json).mode = mode;
  const rows = w.next as Json[];
  if (variant === 'blocked') for (const r of rows) if (featureOf(w, String(r.component)) !== 'shared') r.readiness = 'blocked: 1 decision to make';
  if (variant === 'budget' && rows.length) rows[rows.length - 1].readiness = 'next merge request (change budget reached)';
  return w;
};

function walks(w: Json): Json {
  const features = Object.keys(w.features as Json).sort();
  const components = features.flatMap((f) => membersOf(w, f));
  return {
    prompt: Object.fromEntries(features.map((f) => [f, promptState(w, f)])),
    specify: {
      foundations: specifyQueue(w, { kind: 'foundations' }),
      features: Object.fromEntries(features.map((f) => [f, specifyQueue(w, { kind: 'feature', name: f })])),
      components: Object.fromEntries(components.map((id) => [id, specifyQueue(w, { kind: 'component', id })])),
    },
    build: Object.fromEntries(features.map((f) => [f, buildQueue(w, f)])),
    featureOf: Object.fromEntries(components.map((id) => [id, featureOf(w, id)])),
    budget: budgetReached(w),
    ready: readyToBuild(w),
  };
}

test('the walks: Copy AI prompt by state, Specify and Build in build order, the same in both IDEs (contract/walks/expected.json)', () => {
  const cases = EXAMPLES.flatMap((example) => MODES.flatMap((mode) => VARIANTS.map((variant) =>
    ({ example, mode, variant, walks: walks(workspace(example, mode, variant)) }))));
  const text = JSON.stringify({ about: 'What the walks give (1.1.0) on each example, as recorded and in each mode. Written by UPDATE_WALKS=1 npm test (vscode/walks.ts); the IntelliJ plugin must give the same (WalksRegression).', cases }, null, 1) + '\n';
  const file = path.join(CONTRACT, 'walks', 'expected.json');
  if (process.env.UPDATE_WALKS === '1') {
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, text);
  }
  assert.ok(fs.existsSync(file), 'run UPDATE_WALKS=1 npm test once');
  assert.equal(fs.readFileSync(file, 'utf8'), text, 'walks.ts changed: UPDATE_WALKS=1 npm test, then WalksRegression');
});

test('a walk goes in build order, over what is open only; a feature with nothing ready says what Copy AI prompt does', () => {
  const w = workspace('pyspark-pipeline', 'strict');
  const order = (w.next as Json[]).map((r) => String(r.component));
  for (const f of Object.keys(w.features as Json)) {
    const queue = specifyQueue(w, { kind: 'feature', name: f }).map((s) => s.component);
    assert.deepEqual(queue, [...queue].sort((a, b) => order.indexOf(a) - order.indexOf(b)), `${f}: build order`);
    for (const s of specifyQueue(w, { kind: 'feature', name: f })) assert.ok(s.open > 0, `${s.component}: only what is open`);
  }
  // a component's walk starts on it, then the rest of its feature
  const open = specifyQueue(w, { kind: 'feature', name: 'vat_report' });
  if (open.length > 1) assert.equal(specifyQueue(w, { kind: 'component', id: open[1].component })[0].component, open[1].component);
  // strict, nothing ready: Specify first; guided: the AI proposes first, on the feature's first planned component
  assert.equal(promptState(w, 'vat_report').state, 'ready', 'as recorded, a component of vat_report is ready');
  const strict = promptState(workspace('pyspark-pipeline', 'strict', 'blocked'), 'vat_report');
  const guided = promptState(workspace('pyspark-pipeline', 'guided', 'blocked'), 'vat_report');
  assert.equal(strict.state, 'specify-first');
  assert.equal(guided.state, 'propose-first');
  assert.ok(guided.component && guided.open === strict.open && strict.open > 0);
  assert.deepEqual(buildQueue(workspace('pyspark-pipeline', 'strict', 'blocked'), 'vat_report'), []);
  assert.ok(budgetReached(workspace('pyspark-pipeline', '', 'budget')));
  // a finished example: nothing planned, Add component leads again
  const done = workspace('python-app', '');
  for (const f of Object.keys(done.features as Json)) assert.ok(['built', 'ready'].includes(promptState(done, f).state), f);
  // what the page receives: the same answers, worked out once
  const state = walkState(w);
  assert.deepEqual(state.features.vat_report, specifyQueue(w, { kind: 'feature', name: 'vat_report' }));
  assert.deepEqual(state.build.vat_report, buildQueue(w, 'vat_report'));
  assert.equal(state.featureOf[open.length ? open[0].component : 'config'], featureOf(w, open.length ? open[0].component : 'config'));
});

test('the page uses the walks: the strip in place of the modal, Build apart from Check, right-click menus (1.1.0)', () => {
  const page = fs.readFileSync(path.resolve(__dirname, '..', '..', 'media', 'workspace.js'), 'utf8');
  assert.doesNotMatch(page, /All decision and safety questions are answered for this component/, 'no modal at the end of a component');
  assert.match(page, /onSpecified\(ui\.component\);/);
  for (const action of ['walk', 'walkSpecifyNext', 'walkStop', 'walkBuild', 'walkCopy', 'walkCheck', 'walkBuildNext']) assert.match(page, new RegExp(`    ${action}: \\(`), action);
  assert.match(page, /\$\{head\}\$\{walkStrip\(\)\}<div class="row wrap actions">/, 'the strip sits under the head, over the actions');
  for (const kind of ['component', 'feature', 'decision', 'question', 'safety', 'task', 'file']) assert.match(page, new RegExp(`case '${kind}'`), `a menu for ${kind}`);
  assert.match(page, /data-menu="component"/);
  assert.match(page, /data-menu="feature"/);
  // the answer tables write the answer field: no preview, no "Use this answer"
  assert.doesNotMatch(page, /Answer preview|Use this answer/);
  assert.match(page, /data-input="paramRequired"/, 'Required is a box');
  assert.doesNotMatch(page, /none: required/);
  // tasks are work to build: never in the pending count, not amber
  assert.match(page, /' to build', ' à construire'/);
  assert.doesNotMatch(page, /Pending actions/);
});
