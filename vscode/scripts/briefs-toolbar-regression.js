// Offline regression: the project toolbar opens the real Briefs browser in the ONE editor tab,
// and Draft with AI offers a Back to Briefs action without replacing the navigation sidebar.
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
function view(role) {
  const handlers = {};
  const app = { innerHTML: '' };
  const posted = [];
  const document = {
    activeElement: null,
    getElementById: id => id === 'app' ? app : null,
    querySelectorAll: () => [], querySelector: () => null,
    body: { addEventListener: (name, fn) => { handlers[name] = fn; } },
  };
  const window = { ARCHVOW_ROLE: role, ARCHVOW_LOGO: 'logo',
    addEventListener: (name, fn) => { handlers[name] = fn; }, removeEventListener: () => {}, setTimeout };
  vm.runInNewContext(script, { window, document, console, setTimeout, clearTimeout,
    acquireVsCodeApi: () => ({ postMessage: m => posted.push(m) }), CSS: { escape: String } });
  return {
    app, posted,
    message: m => handlers.message({ data: m }),
    click: (action, dataset = {}) => handlers.click({ target: { closest: () => ({ dataset: { a: action, ...dataset }, disabled: false }) } }),
  };
}
const nav = view('nav');
const editor = view('editor');
const state = {
  fr: false, project: { name: 'sales-pipeline', root: '/tmp/sales-pipeline' }, readiness: 'READY',
  spec: { containers: [{ package: 'sales', components: [] }] }, check: { violations: [], errors: 0, warnings: 0 },
  rows: [], work: { pending: [], resolved: [], otherPending: [], otherResolved: [] }, snapshots: {}, derived: {},
  branch: 'feature/test', base: '', briefs: { folders: ['briefs'], files: [] }, install: {}, aiTools: ['copilot'],
  briefStatus: { folders: ['briefs'], to_draft: 1, changed: 1, files: [{ file: 'briefs/specs.md', sections: [
    { section: 'should-run', items: [] },
    { section: 'should-run-2', changed: true, items: ['ingest_data'] },
  ] }] },
};
for (const v of [nav, editor]) {
  v.message({ type: 'init', commands, forms, icons, catalog: null, types: {}, rules: {} });
  v.message({ type: 'state', state });
}
// Toolbar icons and hover labels remain available in the sidebar.
const actions = [...nav.app.innerHTML.matchAll(/class="project-action" data-a="([^"]+)"[^>]*data-tooltip="([^"]+)"/g)];
assert.equal(actions.length, 9, 'every project action has a visible hover label');
assert.ok(actions.some(([, action, label]) => action === 'openBriefCommands' && label === 'Briefs'));
assert.ok(actions.some(([, action, label]) => action === 'budget' && label === 'Budget / Next'));
assert.match(css, /:hover, :focus-visible\)\:\:after/);
assert.match(css, /\.project-action-bar \.action-more \{ display: none; \}/);
assert.match(css, /@container project-actions \(max-width: 396px\)/);
assert.match(css, /@container project-actions \(max-width: 353px\)/);
assert.match(css, /@container project-actions \(max-width: 52px\)/);
// Open Briefs is a navigation -> host -> shared editor flow, not a replacement Overview page.
nav.click('openBriefCommands');
assert.equal(nav.posted.at(-1).type, 'openBriefs');
assert.match(nav.app.innerHTML, /project-action-container/, 'Overview remains visible in the sidebar');
assert.doesNotMatch(nav.app.innerHTML, /class="briefs-card"/, 'the sidebar is not the briefs browser');
editor.message({ type: 'briefs' });
assert.match(editor.app.innerHTML, /archvow · Briefs/i, 'editor title identifies the browsable briefs');
assert.match(editor.app.innerHTML, /briefs\/specs\.md/);
assert.match(editor.app.innerHTML, /#should-run/);
assert.match(editor.app.innerHTML, /Draft with AI/);
assert.match(editor.app.innerHTML, /Accept change/);
assert.match(editor.app.innerHTML, /data-command="brief accept"/);
assert.doesNotMatch(editor.app.innerHTML, /data-command="brief show"/);
// Drafting from a specific brief stays in the same editor and records a return destination.
editor.click('openSkill', { skill: 'draft-spec', brief: 'briefs/specs.md#should-run' });
assert.match(editor.app.innerHTML, /← Briefs/, 'skill header has the requested back button');
assert.match(editor.app.innerHTML, /briefs\/specs\.md#should-run/, 'selected brief is prefilled');
editor.click('back');
assert.match(editor.app.innerHTML, /class="card briefs-card"/, 'Back goes to the editor Briefs browser');
assert.doesNotMatch(editor.app.innerHTML, /class="skill-form"/);
// Accept is still a real command form request from the editor.
editor.click('openCommand', { command: 'brief accept', brief: 'briefs/specs.md#should-run-2' });
assert.equal(editor.posted.at(-1).type, 'openCommand');
assert.equal(editor.posted.at(-1).command, 'brief accept');
assert.equal(editor.posted.at(-1).values.brief, 'briefs/specs.md#should-run-2');
console.log('Briefs editor browser, AI skill Back to Briefs, accept and toolbar: OK');
