// Headless webview wiring regression: no browser dependencies or VS Code installation needed.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.resolve(__dirname, '../media/workspace.js'), 'utf8');
const mockState = {
  fr: false, languageMode: 'en', project: { name: 'sales-pipeline', root: '/tmp/sales-pipeline' },
  readiness: 'READY', loading: '', spec: { containers: [{ id: 'p1', components: [{ id: 'orders_reader' }] }] },
  check: { ok: false, errors: 1, warnings: 1, counts: { ARCH036: 1, ARCH040: 1 }, violations: [
    { code: 'ARCH036', severity: 'error', message: 'Invariant has no hazard', component: 'orders_reader', file: '.archvow/spec.vow', line: 4 },
    { code: 'ARCH040', severity: 'warning', message: 'Unapproved assistant configured', file: 'CLAUDE.md', line: 2 },
  ] }, checkFailure: '', snapshots: { orders_reader: { component: 'orders_reader', description: 'Read orders', decisions: [], questions: [], safety: [], assumptions: [], tasks: [], rules: [] } },
  rows: [{ id: 'orders_reader', state: 'planned', type: 'reader', pending: 0, layer: 'p1' }], derived: { orders_reader: { counts: { pending: [0,0,0,0,0,0], total: [0,0,0,0,0,0] } } },
  work: { pending: [{ kind: 'ERROR', component: 'orders_reader', source: 'check', command: 'open-file', preset: {}, title: 'ARCH036', detail: 'check violation', pending: true }],
    resolved: [], otherPending: [{ kind: 'ERROR', component: 'orders_reader', source: 'check', command: 'open-file', preset: {}, title: 'ARCH036', detail: 'check violation', pending: true }], otherResolved: [],
    pendingByComponent: {}, resolvedByComponent: {} },
  branch: 'develop', refs: [], base: '', choices: {}, briefs: { folders: [], files: [] }, install: {},
};
function webview(role) {
  const handlers = {};
  const posted = [];
  const app = { innerHTML: '' };
  const doc = {
    activeElement: null,
    getElementById: (id) => id === 'app' ? app : null,
    querySelectorAll: () => [],
    querySelector: () => null,
    body: { addEventListener: (event, handler) => { handlers[event] = handler; } },
  };
  const win = { ARCHVOW_ROLE: role, ARCHVOW_LOGO: 'logo.png', addEventListener: (event, handler) => { handlers[event] = handler; }, removeEventListener: () => {} };
  const sandbox = { window: win, document: doc, console, setTimeout, clearTimeout,
    acquireVsCodeApi: () => ({ postMessage: (message) => posted.push(message) }),
    CSS: { escape: String }, };
  vm.runInNewContext(source, sandbox, { filename: 'workspace.js' });
  const message = (m) => handlers.message({ data: m });
  const click = (action, dataset = {}) => {
    assert.equal(typeof handlers.click, 'function');
    handlers.click({ target: { closest: () => ({ dataset: { a: action, ...dataset } }) }, preventDefault: () => {} });
  };
  message({ type: 'init', commands: [], rules: { en: '', fr: '' } });
  message({ type: 'state', state: mockState });
  return { app, posted, message, click };
}
const nav = webview('nav');
const pendingBadges = (html) => {
  const tabs = html.match(/<nav class="tabs sub">([\s\S]*?)<\/nav>/);
  assert.ok(tabs, 'overview tabs exist');
  const numbers = [...tabs[1].matchAll(/<span class="count(?: check-count)?">(\d+)<\/span>/g)].map((row) => Number(row[1]));
  assert.equal(numbers.length, 2, 'one Architecture and one Check & Gates count');
  return numbers;
};
assert.deepEqual(pendingBadges(nav.app.innerHTML), [0, 1], 'a check issue is not an architecture action');
nav.click('overSub', { sub: 'checks' });
assert.match(nav.app.innerHTML, /ARCH036/);
assert.doesNotMatch(nav.app.innerHTML, /class="check-detail"/, 'sidebar must not render diagnostic details');
nav.click('checkSel', { i: '1' });
const toEditor = nav.posted.findLast((m) => m.type === 'showCheckDetails');
assert.equal(toEditor.index, 1);
const editor = webview('editor');
editor.message(toEditor);
assert.match(editor.app.innerHTML, /class="check-editor-detail"/, 'editor shows one full-width detail panel');
assert.doesNotMatch(editor.app.innerHTML, /class="check-list"/, 'editor must not duplicate sidebar checks list');
assert.match(editor.app.innerHTML, /Unapproved assistant configured/);
assert.match(editor.app.innerHTML, /Open rule/);
editor.message({ type: 'select', component: 'orders_reader' });
editor.click('view', { i: '7' });
assert.doesNotMatch(editor.app.innerHTML, /check violation/, 'global check must not be a component Other item');
assert.match(editor.app.innerHTML, /No.*display|Nothing to display/);
// A real project record must not disappear just because check violations were moved.
const projectState = structuredClone(mockState);
projectState.work.pending.push({ kind: 'QUESTION', component: '', command: 'open-project-vow', preset: {},
  title: 'Project-wide decision', detail: 'Approve project settings', pending: true });
projectState.work.otherPending.push(projectState.work.pending.at(-1));
nav.message({ type: 'state', state: projectState });
assert.deepEqual(pendingBadges(nav.app.innerHTML), [1, 1], 'project work belongs to non-check pending work');
assert.match(nav.app.innerHTML, /project items/);
nav.click('projectWork');
const projectMessage = nav.posted.findLast((m) => m.type === 'showProjectWork');
assert.ok(projectMessage, 'project badge opens project items in the editor');
editor.message({ type: 'state', state: projectState });
editor.message(projectMessage);
assert.match(editor.app.innerHTML, /Project-wide decision/);
assert.doesNotMatch(editor.app.innerHTML, /check violation/);
editor.message({ type: 'select', component: 'orders_reader' });
editor.click('view', { i: '7' });
assert.doesNotMatch(editor.app.innerHTML, /Project-wide decision/, 'project records must not appear as component records');
const componentState = structuredClone(projectState);
componentState.work.otherPending.push({ kind: 'WARNING', component: 'orders_reader',
  command: 'component-show', preset: { view: 'other' }, title: 'Missing schema', detail: 'Schema missing', pending: true });
componentState.work.pendingByComponent.orders_reader = 1;
editor.message({ type: 'state', state: componentState });
assert.match(editor.app.innerHTML, /Missing schema/, 'local component warnings must remain visible');
assert.doesNotMatch(editor.app.innerHTML, /check violation/);
// Counts must be taken from deduplicated Work.pending, not the raw CLI violations.
const mixed = structuredClone(mockState);
mixed.work.pending = [
  ...mixed.work.pending,
  { kind: 'QUESTION', component: 'orders_reader', command: 'decision make', preset: {}, title: 'D1', pending: true },
  { kind: 'QUESTION', component: 'orders_reader', command: 'hazard add', preset: {}, title: 'S1', pending: true },
  { kind: 'ERROR', component: '', source: 'check', command: 'open-diagnostics', preset: {}, title: 'ARCH040', pending: true },
  { kind: 'WARNING', component: '', source: 'check', command: 'open-file', preset: {}, title: 'ARCH053', pending: true },
];
nav.message({ type: 'state', state: mixed });
assert.deepEqual(pendingBadges(nav.app.innerHTML), [2, 3], 'Architecture (2) + Checks (3) = total pending (5)');
assert.match(nav.app.innerHTML, /5 pending/);
console.log('VS Code checks layout: one full-width editor detail; Architecture + Checks = pending: OK');
