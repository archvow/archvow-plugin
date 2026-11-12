// The Examples view and the search (0.18.0, archvow 1.21), on what archvow really prints (contract/cli/python-app/examples,
// contract/cli/<example>/search). The page's functions are read out of media/workspace.js and run here, as
// layout.test.ts does; IntelliJ's ArchvowExamples must say the same (contract/forms/examples.expected.json,
// written by UPDATE_EXAMPLES=1 npm test, read by ExamplesAndSearchRegression).
import * as assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { test } from 'node:test';

const ROOT = path.resolve(__dirname, '..', '..');
const CONTRACT = path.resolve(ROOT, '..', 'contract');
const page = fs.readFileSync(path.join(ROOT, 'media', 'workspace.js'), 'utf8');
const host = fs.readFileSync(path.join(ROOT, 'src', 'workspace.ts'), 'utf8');
const manifest = JSON.parse(fs.readFileSync(path.join(ROOT, 'package.json'), 'utf8'));
type Json = Record<string, any>;
const read = (...parts: string[]): Json => JSON.parse(fs.readFileSync(path.join(CONTRACT, ...parts), 'utf8'));

/** The page's functions (and consts) of these names, with its helpers, in one scope; `ui` and the language given. */
function load(names: string[], ui: Json, french = false): Json {
  const helpers = ['t', 'esc', 'str', 'list', 'btn', 'attrs', 'byOn', 'split', 'answerHead', 'SEARCH_GROUPS', 'terminalCommand'];
  const sources = [...helpers, ...names].map((name) => {
    const fn = page.match(new RegExp(`\\n  function ${name}\\([^)]*\\) \\{[\\s\\S]*?\\n  \\}\\n`));
    const constant = page.match(new RegExp(`\\n  const ${name} = [\\s\\S]*?;\\n`));
    const found = fn ? fn[0] : constant ? constant[0] : '';
    assert.ok(found, `${name} is in the page`);
    return found;
  }).join('\n');
  const body = `const fr = () => ${french}; ${sources}; return { ${names.join(', ')} };`;
  return new Function('ui', body)(ui);
}

const exampleOf = (file: string): Json => read('cli', 'python-app', 'examples', file).examples[0];
const STATES = ['passing', 'failing', 'no-test'];

test('an example says its test\'s verdict, never its own (the test decides, archvow shows)', () => {
  const { exampleState } = load(['exampleState'], {});
  assert.deepEqual(STATES.map((s) => exampleState(exampleOf(`${s}.json`)).result), ['passing', 'failing', 'no_test']);
  const failing = exampleState(exampleOf('failing.json'));
  assert.equal(failing.mark, '✖');
  assert.equal(failing.text, 'failing - invoices/F-7.json: 2 lines differ; stdout.txt: 2 lines differ');
  assert.equal(exampleState(exampleOf('passing.json')).text, 'passing');
  assert.equal(exampleState({ result: 'not_run', run_on: '2026-11-05 10:12' }).text, 'not in the last run (test-results.xml of 2026-11-05 10:12)');
  assert.equal(exampleState({ tested: true }).result, 'not_run'); // an archvow without `result`
  const fr = load(['exampleState'], {}, true).exampleState;
  assert.equal(fr(exampleOf('no-test.json')).text, 'pas encore de test marqué (ARCH056)');
});

test('the chip and the badge count passing and failing examples; the progress counts the passing ones', () => {
  const { exampleCounts, failingBadge } = load(['exampleState', 'exampleCounts', 'failingBadge'], {});
  const view = read('cli', 'python-app', 'examples', 'failing.json');
  assert.deepEqual(exampleCounts(view), { total: 1, passing: 0, failing: 1 });
  assert.match(failingBadge(view), /data-a="view" data-i="8"[^>]*>1 example failing<\/button>/);
  assert.equal(failingBadge(read('cli', 'python-app', 'examples', 'passing.json')), '');
  assert.match(page, /const VIEW_ORDER = \[0, 1, 2, 3, 4, 8, 5, 6, 7\];/); // Examples after Rules, before Tasks
  assert.match(page, /const viewBar = VIEW_ORDER\.map\(/); // always shown, as Decisions and Questions (no filter)
  assert.match(page, /i === 8 \? name \+ \(!ex\.total \? '' :/); // no example: just "Examples"
  assert.match(page, /part\('examples', t\('Examples passing', 'Exemples réussis'\)/);
});

test('the Examples view: cards by rule, the selected one\'s files side by side with the differences marked', () => {
  const ui: Json = { sel: { example: '' }, exampleFile: {}, component: 'invoices_cli' };
  const fns = load(['exampleState', 'exampleGroups', 'examplesView', 'exampleTest', 'exampleDetail', 'exampleDiff'], ui);
  const view = read('cli', 'python-app', 'examples', 'failing.json');
  const html = fns.examplesView(view);
  assert.equal(ui.sel.example, 'US-001-pay-a-sent-invoice'); // the first failing one is selected
  assert.match(html, /<b class="mono">No rule named<\/b>/);
  assert.match(html, /class="item-card example-card selected open"/);
  // the first file that differs: a document, re-written with sorted keys, line by line
  assert.match(html, /<tr class="op-missing"><td class="mark">−<\/td><td class="line">  &quot;status&quot;: &quot;paid&quot;<\/td>/);
  assert.match(html, /<tr class="op-extra"><td class="mark"><\/td><td class="line"><\/td><td class="gap"><\/td><td class="mark">\+<\/td><td class="line">  &quot;status&quot;: &quot;sent&quot;<\/td>/);
  assert.match(html, /Fix the code, not the example\./);
  assert.match(html, /data-text="archvow example add invoices_cli US-001-pay-a-sent-invoice"/);
  assert.match(html, /data-a="openBrief" data-ref="briefs\/invoices.md#paying-a-sent-invoice"/);
  assert.match(html, /data-file="tests\/p4_entrypoints\/test_invoices_cli.py"/); // Open test, from `evidence`
  // the files can be chosen; a table shows its cells, the changed ones marked
  ui.exampleFile['US-001-pay-a-sent-invoice'] = 'stdout.txt';
  const text = fns.examplesView(view);
  assert.match(text, /class="on small" data-a="exampleFile" data-id="US-001-pay-a-sent-invoice" data-key="stdout.txt"/);
  assert.match(text, /<tr class="op-missing"><td class="mark">−<\/td><td class="line">F-7 Ada 9.90 paid<\/td>/);
  const table = fns.exampleDiff({ name: 'orders.csv', kind: 'table', state: 'differs', differences: 2,
    header: { expected: ['order_id', 'amount'], actual: ['order_id', 'amount'] },
    rows: [{ op: 'changed', expected: ['A1', '5.00'], actual: ['A1', '5.0'], cells: [1] }, { op: 'extra', expected: null, actual: ['A2', '0.00'] }] },
  { actual: true });
  assert.match(table, /<td class="">A1<\/td><td class="cell-changed">5.00<\/td>/);
  assert.match(table, /<tr class="op-extra"><td class="mark"><\/td><td class="none" colspan="2"><\/td>/);
  assert.match(table, /<tr class="columns"><th><\/th><th>order_id<\/th><th>amount<\/th>/);
  // no test yet: expected only, and what to ask the AI
  const none = fns.examplesView(read('cli', 'python-app', 'examples', 'no-test.json'));
  assert.match(none, /No test yet, so no actual\//);
  assert.match(none, /expected only: the test has not written actual\/ yet/);
  // a component without examples: the way to record one
  assert.match(fns.examplesView({ examples: [] }), /data-command="example add" data-component="invoices_cli"/);
});

test('the shipped examples come out as designed: a rule heading over its examples (pyspark-pipeline)', () => {
  const ui: Json = { sel: { example: '' }, exampleFile: {}, component: 'clean_orders' };
  const { exampleGroups, examplesView } = load(['exampleState', 'exampleGroups', 'examplesView', 'exampleTest', 'exampleDetail', 'exampleDiff'], ui);
  const view = read('cli', 'pyspark-pipeline', 'show', 'clean_orders.json');
  assert.deepEqual(exampleGroups(view).map((g: Json) => [g.rule, g.items.length]), [['clean_orders.INV1', 1], ['', 1]]);
  assert.match(examplesView(view), /<b class="mono">clean_orders.INV1<\/b> <span class="muted small">every order_id of an input row with amount &gt;= 0 appears in the output<\/span>/);
});

test('the search: groups, places, the selected one\'s lines; a name found nowhere gives its close names', () => {
  const ui: Json = { searchGroup: '', searchSel: 0 };
  const { searchView, searchHits } = load(['searchHits', 'searchView'], ui);
  const found = read('cli', 'python-app', 'search', 'InvoiceNotFoundError.json');
  found.groups[0].hits[0].around = [{ n: 12, text: 'symbols' }, { n: 13, text: 'symbol InvoiceNotFoundError' }];
  found.groups[0].hits[0].line = 13;
  const html = searchView(found);
  assert.match(html, /<h2 class="mono">InvoiceNotFoundError<\/h2><span class="muted">found in \d+ places · a class of invoice_not_found_error/);
  assert.match(html, /data-a="searchGroup" data-id="code"><span>Code<\/span><span class="n">\d+<\/span>/);
  assert.match(html, /<span class="here">  13  symbol InvoiceNotFoundError<\/span>/);
  assert.match(html, /data-a="openFile" data-file="\.archvow\/features\/invoices\/components\/invoice_not_found_error\.vow" data-line="13"/);
  assert.equal(searchHits(found, '').length, found.total);
  ui.searchGroup = 'tests';
  assert.ok(searchHits(found, ui.searchGroup).every((h: Json) => h.group === 'tests'));
  const nowhere = searchView(read('cli', 'python-app', 'search', 'mark_payd.json'));
  assert.match(nowhere, /&#39;mark_payd&#39; is not a component, a file or an item, and is nowhere in the project\./);
  assert.match(nowhere, /data-a="find" data-id="mark_paid" >mark_paid · a component<\/button>/);
});

test('the find field, the host and the editor action', () => {
  assert.match(page, /function findBox\(\)/);
  assert.match(page, /<div class="right">\$\{findBox\(\)\}\$\{reload\}/, 'search is in the shared header');
  assert.match(page, /e\.dataset\.input === 'find' && event\.key === 'Enter'/);
  assert.match(host, /case 'search':\n\s+return this\.search\(String\(m\.name \?\? ''\)\);/);
  assert.match(host, /this\.cli\.run\(\['show', wanted, '--json'\]\)/);
  const command = manifest.contributes.commands.find((c: Json) => c.command === 'archvow.findName');
  assert.equal(command.title, 'Find in archvow project');
  assert.ok(manifest.contributes.menus['editor/context'].some((m: Json) => m.command === 'archvow.findName'));
});

test('IntelliJ shows the same states (contract/forms/examples.expected.json)', () => {
  const states: Json = {};
  for (const french of [false, true]) {
    const { exampleState } = load(['exampleState'], {}, french);
    for (const s of STATES) {
      const e = exampleOf(`${s}.json`);
      states[`${s}.${french ? 'fr' : 'en'}`] = (({ result, mark, tone, text }) => ({ result, mark, tone, text }))(exampleState(e));
    }
  }
  const text = JSON.stringify(states, null, 1) + '\n';
  const file = path.join(CONTRACT, 'forms', 'examples.expected.json');
  if (process.env.UPDATE_EXAMPLES === '1') fs.writeFileSync(file, text);
  assert.ok(fs.existsSync(file), 'run UPDATE_EXAMPLES=1 npm test once');
  assert.equal(fs.readFileSync(file, 'utf8'), text, 'exampleState() changed: UPDATE_EXAMPLES=1 npm test, then ExamplesAndSearchRegression');
});
