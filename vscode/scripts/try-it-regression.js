// Offline regression (0.26.1): "Try it" on the welcome page shows its result where the person clicked.
// Before: the extension posted the running state and the result to the editor tab only, so the sidebar's welcome
// page - where Try it is clicked - never changed, and in the editor tab the result sat below the fold.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../..');

// 1. The extension sends a welcome result to the sidebar as well as to the editor tab.
const extension = fs.readFileSync(path.join(root, 'vscode/src/workspace.ts'), 'utf8');
assert.match(extension, /this\.toEditor\(running\);\s*\n\s*if \(target === 'welcome'\) this\.toNav\(running\);/, 'running: also to the sidebar');
assert.match(extension, /this\.toOpenEditor\(message\);[\s\S]{0,300}if \(target === 'welcome'\) this\.toNav\(message\);/, 'result: also to the sidebar');

// 2. The sidebar's welcome page shows it under the Try card, and scrolls it into view.
const script = fs.readFileSync(path.join(root, 'vscode/media/workspace.js'), 'utf8');
let onMessage = null;
const scrolled = [];
const card = { scrollIntoView: (options) => scrolled.push(options) };
const body = { closest: (selector) => (selector === '.card.result' ? card : null) };
const app = { innerHTML: '' };
const document = {
  activeElement: null,
  getElementById: (id) => (id === 'app' ? app : null),
  querySelector: (selector) => (selector === '[data-body="welcome"]' ? body : null),
  querySelectorAll: () => [],
  body: { addEventListener() {}, classList: { add() {}, remove() {}, toggle() {} } },
};
const window = {
  ARCHVOW_ROLE: 'nav', ARCHVOW_LOGO: 'logo', innerHeight: 600, setTimeout,
  addEventListener: (name, fn) => { if (name === 'message') onMessage = fn; },
  removeEventListener() {},
};
vm.runInNewContext(script, { window, document, console, setTimeout, clearTimeout,
  acquireVsCodeApi: () => ({ postMessage() {}, getState() {}, setState() {} }), CSS: { escape: String } });
assert.ok(onMessage, 'the page listens for messages');
const send = (data) => onMessage({ data });
send({ type: 'init', commands: [], catalog: {}, knownVersion: '2.6.1', forms: {}, icons: {}, types: [], tmpdir: '/tmp', rules: { en: '', fr: '' } });
send({ type: 'state', state: { readiness: 'NOT_INITIALIZED', fr: false, project: { root: '/work/new' }, install: {}, snapshots: {} } });
assert.match(app.innerHTML, /Not sure yet\? See it catch a mistake/, 'the Try card is on the sidebar welcome page');
assert.doesNotMatch(app.innerHTML, /Command result/, 'no result before Try it');

send({ type: 'running', target: 'welcome', label: 'try', args: 'archvow try python-app --json' });
const running = app.innerHTML;
assert.match(running, /Command result/, 'running: a result card on the sidebar');
assert.match(running, /Running/, 'running: says so');
assert.ok(running.indexOf('Command result') > running.indexOf('See it catch a mistake'), 'under the Try card');
assert.equal(scrolled.length, 1, 'scrolled into view when it starts');

send({ type: 'result', target: 'welcome', label: 'try', html: '<p>2 errors caught</p>', raw: '{"folder": "/tmp/archvow-try-x/python-app"}', args: 'archvow try python-app --json', ok: false });
assert.match(app.innerHTML, /2 errors caught/, 'the result itself on the sidebar');
assert.match(app.innerHTML, /Read the example&#39;s spec|Read the example's spec/, 'its follow-up under it');
assert.equal(scrolled.length, 2, 'scrolled into view when it arrives');
send({ type: 'state', state: { readiness: 'NOT_INITIALIZED', fr: false, project: { root: '/work/new' }, install: {}, snapshots: {} } });
assert.equal(scrolled.length, 2, 'a later re-render does not scroll again');
console.log('try-it regression: OK (result on the sidebar under the Try card, scrolled into view)');
