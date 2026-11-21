// Offline regression (archvow 2.8.0): a feature's page offers "Check before coding" next to "Copy AI prompt" - the
// AI checks the whole feature before any code (archvow prompt --feature <feature> --clarify), where its components
// disagree or leave a gap between them, and records each finding as a question. In English and in French.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../..');
const workspace = JSON.parse(fs.readFileSync(path.join(root, 'contract', 'cli', 'fastapi-service', 'workspace.json'), 'utf8'));
let source = fs.readFileSync(path.join(root, 'vscode', 'media', 'workspace.js'), 'utf8');
source = source.replace("  render();\n  post({ type: 'ready' });\n}());", `  window.audit = { actions };
  render();
  post({ type: 'ready' });
}());`);
const handlers = {};
const posted = [];
const app = { innerHTML: '' };
const document = {
  getElementById: (name) => (name === 'app' ? app : null), querySelectorAll: () => [], querySelector: () => null, activeElement: null,
  body: { addEventListener: (name, fn) => { handlers[name] = fn; } },
};
const window = { ARCHVOW_ROLE: 'editor', ARCHVOW_LOGO: '', addEventListener: (name, fn) => { handlers[name] = fn; }, removeEventListener() {}, setTimeout };
vm.runInNewContext(source, { document, window, console, setTimeout, clearTimeout,
  acquireVsCodeApi: () => ({ postMessage: (m) => posted.push(m) }), CSS: { escape: String } });
assert.ok(window.audit, 'the page exposes its actions to this test');
handlers.message({ data: { type: 'init', commands: [], forms: {}, catalog: {}, types: {}, icons: {} } });
const state = (fr) => ({ fr, project: { name: 't', root: '/tmp/t' }, install: {}, snapshots: {}, features: workspace.features,
  spec: workspace.project, work: { pending: [], pendingByComponent: {} } });

handlers.message({ data: { type: 'state', state: state(false) } });
handlers.message({ data: { type: 'feature', feature: 'orders' } });
let html = app.innerHTML;
const prompt = html.indexOf('data-a="featurePrompt"');
const check = html.indexOf('data-a="featureCheck"');
assert.ok(prompt > 0 && check > prompt, 'Check before coding comes after Copy AI prompt: ' + html.slice(0, 400));
assert.match(html, /data-a="featureCheck"[^>]*data-id="orders"/, 'for this feature');
assert.match(html, />Check before coding</, 'its label');
assert.match(html, /archvow prompt --feature orders --clarify: the AI checks the whole feature before any code/, 'what it does, on hover');
assert.match(html, /It writes no code\./, 'and what it does not');

window.audit.actions.featureCheck({ dataset: { id: 'orders' } });
assert.deepEqual({ ...posted.at(-1) }, { type: 'featureCheck', feature: 'orders' }, 'the extension copies the check');
window.audit.actions.featurePrompt({ dataset: { id: 'orders' } });
assert.deepEqual({ ...posted.at(-1) }, { type: 'featurePrompt', feature: 'orders' }, 'Copy AI prompt is unchanged');

handlers.message({ data: { type: 'state', state: state(true) } });
html = app.innerHTML;
assert.match(html, />Vérifier avant de coder</, 'in French');
assert.match(html, /l&#39;IA vérifie toute la fonctionnalité avant tout code|l'IA vérifie toute la fonctionnalité avant tout code/, 'what it does, in French');

const extension = fs.readFileSync(path.join(root, 'vscode', 'src', 'workspace.ts'), 'utf8');
assert.match(extension, /case 'featureCheck':\n\s+return this\.copyPrompt\('', String\(m\.feature\), true\);/, 'the extension answers the button');
console.log('OK: a feature page offers Check before coding (archvow prompt --feature <feature> --clarify), in English and French');
