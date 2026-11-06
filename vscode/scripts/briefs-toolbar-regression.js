// Offline regression for the project toolbar, visible hover labels and real briefs browser.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../..');
const script = fs.readFileSync(path.join(root, 'vscode/media/workspace.js'), 'utf8');
const css = fs.readFileSync(path.join(root, 'vscode/media/workspace.css'), 'utf8');
const forms = JSON.parse(fs.readFileSync(path.join(root, 'contract/forms/forms.json'), 'utf8'));
const icons = JSON.parse(fs.readFileSync(path.join(root, 'contract/forms/icons.json'), 'utf8'));
const commands = JSON.parse(fs.readFileSync(path.join(root, 'contract/cli/commands.json'), 'utf8')).commands;
const handlers = {};
const app = { innerHTML: '' };
const posted = [];
const document = {
  activeElement: null,
  getElementById: id => id === 'app' ? app : null,
  querySelectorAll: () => [], querySelector: () => null,
  body: { addEventListener: (name, fn) => { handlers[name] = fn; } },
};
const window = { ARCHVOW_ROLE: 'nav', ARCHVOW_LOGO: 'logo',
  addEventListener: (name, fn) => { handlers[name] = fn; }, removeEventListener: () => {}, setTimeout };
vm.runInNewContext(script, { window, document, console, setTimeout, clearTimeout,
  acquireVsCodeApi: () => ({ postMessage: m => posted.push(m) }), CSS: { escape: String } });
const message = m => handlers.message({ data: m });
const click = (a, dataset = {}) => handlers.click({ target: { closest: () => ({ dataset: { a, ...dataset }, disabled: false }) } });
message({ type: 'init', commands, forms, icons, catalog: null, types: {}, rules: {} });
message({ type: 'state', state: {
  fr: false, project: { name: 'sales-pipeline', root: '/tmp/sales-pipeline' }, readiness: 'READY',
  spec: { containers: [{ package: 'sales', components: [] }] }, check: { violations: [], errors: 0, warnings: 0 },
  rows: [], work: { pending: [], resolved: [], otherPending: [], otherResolved: [] }, snapshots: {}, derived: {},
  branch: 'feature/test', base: '', briefs: { folders: ['briefs'], files: [] }, install: {},
  briefStatus: { folders: ['briefs'], to_draft: 1, changed: 1, files: [{ file: 'briefs/specs.md', sections: [
    { section: 'should-run', items: [] },
    { section: 'should-run-2', changed: true, items: ['ingest_data'] },
  ] }] },
} });
// All nine actions have a precise, visible (not just native title) hover label.
const actions = [...app.innerHTML.matchAll(/class="project-action" data-a="([^"]+)"[^>]*data-tooltip="([^"]+)"/g)];
assert.equal(actions.length, 9, 'every project action has a visible hover label');
assert.ok(actions.some(([, action, label]) => action === 'openBriefCommands' && label === 'Briefs'));
assert.ok(actions.some(([, action, label]) => action === 'budget' && label === 'Budget / Next'));
assert.match(css, /:hover, :focus-visible\)\:\:after/, 'hover and focus labels are painted in CSS');
// At wide sizes, no More button; only the genuinely overflowing actions enter it.
assert.match(css, /\.project-action-bar \.action-more \{ display: none; \}/);
assert.match(css, /@container project-actions \(max-width: 396px\)/);
assert.match(css, /@container project-actions \(max-width: 353px\)/);
assert.match(css, /@container project-actions \(max-width: 52px\)/);
click('openBriefCommands');
assert.match(app.innerHTML, /briefs\/specs.md/, 'folder opens real status browser');
assert.match(app.innerHTML, /#should-run/, 'brief sections are visible');
assert.match(app.innerHTML, /Draft with AI/, 'not-yet-used sections can be drafted');
assert.match(app.innerHTML, /Accept change/, 'changed sections can be accepted');
assert.match(app.innerHTML, /data-command="brief accept"/, 'standalone accept action opens a command form');
assert.doesNotMatch(app.innerHTML, /data-command="brief show"/, 'opening Briefs is not merely a CLI form');
click('openCommand', { command: 'brief accept', brief: 'briefs/specs.md#should-run-2' });
assert.equal(posted.at(-1).type, 'openCommand');
assert.equal(posted.at(-1).command, 'brief accept');
assert.equal(posted.at(-1).values.brief, 'briefs/specs.md#should-run-2');
click('overSub', { sub: 'architecture' });
assert.match(app.innerHTML, /project-action-container/, 'Back returns to Overview');
console.log('Briefs browser, accept action, hover labels and natural toolbar overflow: OK');
