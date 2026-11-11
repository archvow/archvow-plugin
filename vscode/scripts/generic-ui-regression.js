// Offline headless regression of the generic UI. No VS Code runtime or npm packages needed.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../..');
const forms = JSON.parse(fs.readFileSync(path.join(root, 'contract/forms/forms.json'), 'utf8'));
const commands = JSON.parse(fs.readFileSync(path.join(root, 'contract/cli/commands.json'), 'utf8')).commands;
const source = fs.readFileSync(path.join(root, 'vscode/media/workspace.js'), 'utf8');
const names = commands.map(c => c.path.join(' '));
assert.equal(forms.command_groups.length > 3, true);
const all = names.map(name => ({ name, groups: forms.command_groups.filter(g => g.prefixes.includes(name.split(' ')[0])) }));
assert.ok(all.every(item => item.groups.length === 1), 'each CLI command belongs to exactly one shared group');

const state = {
  fr: false, project: { name: 'example', root: '/tmp/example' }, languageMode: 'en', readiness: 'READY',
  spec: { system: { name: 'example' }, features: [{ name: 'orders', description: 'Orders' }, { name: 'shared', description: 'What every feature uses' }],
    containers: [{ id: 'core', components: [
    { id: 'reader', type: 'reader', layer: 'io', feature: 'orders' }, { id: 'schema', type: 'schema', layer: 'domain', feature: 'orders' },
    { id: 'config', type: 'foundation', layer: 'core', feature: 'shared' },
  ] }] },
  snapshots: { schema: { component: 'schema', type: 'schema', layer: 'domain', description: 'Records',
    uses: [], decisions: [{ number: 1, question: 'Which columns are nullable and contain personal data?', answer: '' }],
    known_columns: [{ component: 'schema', columns: ['id', 'comment'] }],
    columns: [{ name: 'id', class: 'none' }, { name: 'comment', class: 'personal' }],
    questions: [], safety: [], assumptions: [], rules: [], tasks: [] },
    reader: { component: 'reader', type: 'reader', description: 'Reads data', decisions: [], questions: [],
      safety: [], assumptions: [], rules: [], tasks: [] } },
  rows: [{ id: 'reader', type: 'reader', state: 'planned', pending: 0 }, { id: 'schema', type: 'schema', state: 'planned', pending: 1 }],
  derived: { schema: { counts: { pending: [1,0,0,0,0,0], total: [1,0,0,0,0,0] }, data: [] },
    reader: { counts: { pending: [0,0,0,0,0,0], total: [0,0,0,0,0,0] }, data: [] } },
  check: { violations: [], errors: 0, warnings: 0 }, work: {
    pending: [{ component: 'schema', command: 'decision make', source: 'decision', preset: {}, title: 'D1', pending: true }], resolved: [],
    otherPending: [], otherResolved: [], pendingByComponent: { schema: 1 }, resolvedByComponent: {} },
  addable: { domain: [{ type: 'schema', description: 'A data schema' }] },
  choices: {}, refs: [], branch: 'feature/example', base: '', briefs: { folders: [], files: [] }, install: {},
  vows: {}, loading: '', welcomeError: ''
};
function make(role) {
  const handlers = {};
  const posted = [];
  const app = { innerHTML: '' };
  const document = {
    activeElement: null,
    getElementById: id => id === 'app' ? app : null,
    querySelectorAll: () => [], querySelector: () => null,
    body: { addEventListener: (name, fn) => { handlers[name] = fn; } },
  };
  const window = { ARCHVOW_ROLE: role, ARCHVOW_LOGO: 'logo',
    addEventListener: (name, fn) => { handlers[name] = fn; }, removeEventListener: () => {}, setTimeout };
  vm.runInNewContext(source, { window, document, console, setTimeout, clearTimeout,
    acquireVsCodeApi: () => ({ postMessage: m => posted.push(m) }), CSS: { escape: String } });
  const message = m => handlers.message({ data: m });
  const click = (a, dataset = {}) => handlers.click({ target: {
    closest: () => ({ dataset: { a, ...dataset }, disabled: false }) } });
  const change = (kind, value, dataset = {}) => handlers.change({ target: { dataset: { change: kind, ...dataset }, value } });
  message({ type: 'init', commands, forms, catalog: null, types: {}, rules: {} });
  message({ type: 'state', state });
  return { app, posted, message, click, change };
}
const nav = make('nav');
assert.equal(forms.command_groups[0].id, 'project', 'Project & checks is the first command group');
assert.match(nav.app.innerHTML, /data-tab="ai"/, 'fourth AI skills tab is present');
assert.match(nav.app.innerHTML, /data-a="openComponents"/, 'Overview links to the Components browser');
assert.doesNotMatch(nav.app.innerHTML, /Command reference|Brief folders|Base: origin/, 'Overview contains no redundant reference, briefs or base');
assert.equal((nav.app.innerHTML.match(/data-a="refresh"/g) || []).length, 1, 'one reload in the header');
assert.equal((nav.app.innerHTML.match(/title="Check"/g) || []).length, 1, 'one complete Check action');
nav.click('tab', { tab: 'components' });
// 0.24.0 (contract 2.0): by feature first - what the product does, shared last - each with its "+"
assert.match(nav.app.innerHTML, /data-a="groupBy" data-id="feature"/, 'the By feature / By layer switch');
assert.match(nav.app.innerHTML, /data-a="addFeature"/, 'Add feature');
assert.match(nav.app.innerHTML, /data-a="addToFeature" data-id="orders"/, 'a feature\'s plus control');
assert.ok(nav.app.innerHTML.indexOf('data-id="orders"') < nav.app.innerHTML.indexOf('data-id="shared"'), 'shared last');
assert.match(nav.app.innerHTML, /class="count">1<\/span>/, 'per-component pending count remains visible');
assert.match(nav.app.innerHTML, /class="component-state tone-warn">planned<\/span>/, 'component status appears alongside counts');
nav.click('pickFeature', { id: 'orders' });
assert.equal(JSON.stringify(nav.posted.at(-1)), JSON.stringify({ type: 'showFeature', feature: 'orders' }), 'a feature\'s name opens its page');
nav.click('groupBy', { id: 'layer' });
assert.match(nav.app.innerHTML, /data-a="addHere"/, 'by layer: the layer plus control remains visible');
assert.match(nav.app.innerHTML, /class="count">1<\/span>/, 'by layer: per-component pending count');
nav.click('groupBy', { id: 'feature' });
nav.click('openComponents');
assert.match(nav.app.innerHTML, /data-tab="components"/, 'Overview shortcut reaches Components');
nav.click('tab', { tab: 'ai' });
assert.match(nav.app.innerHTML, /data-a="openSkill"/, 'AI skills live under their own tab');
nav.click('tab', { tab: 'commands' });
assert.match(nav.app.innerHTML, /command-group-toggle/);
assert.ok(nav.app.innerHTML.indexOf('data-id="project"') < nav.app.innerHTML.indexOf('data-id="components"'), 'project group precedes component group');
assert.doesNotMatch(nav.app.innerHTML, /data-id="ai"|data-a="openSkill"/, 'Commands excludes AI skills');
assert.doesNotMatch(nav.app.innerHTML, /data-name="component edit"/, 'command groups start collapsed');
nav.click('commandGroup', { id: 'components' });
assert.match(nav.app.innerHTML, /data-name="component edit"/, 'expanding a group reveals its children');
nav.click('commandGroup', { id: 'components' });
assert.doesNotMatch(nav.app.innerHTML, /data-name="component edit"/, 'collapsing hides its children again');
nav.click('commandDocs');
assert.equal(nav.posted.at(-1).type, 'commandDocs', 'documentation never runs archvow docs');
const editor = make('editor');
editor.message({ type: 'select', component: 'schema' });
for (const action of ['aiMenu', 'addMenu', 'openCode'])
  assert.match(editor.app.innerHTML, new RegExp('data-a="' + action + '"'), action + ' remains available in component detail');
assert.match(editor.app.innerHTML, /data-command="component edit"/, 'Edit spec remains available');
assert.match(editor.app.innerHTML, /class="component-state tone-warn">planned<\/span>/, 'detail shows state separately from work count');
editor.message({ type: 'commandDocs' });
assert.match(editor.app.innerHTML, /no command is executed/);
assert.match(editor.app.innerHTML, /archvow component edit/);
editor.message({ type: 'openCommand', command: 'component edit', values: { component: 'reader', uses: 'schema' } });
assert.match(editor.app.innerHTML, /uses-picker/);
assert.match(editor.app.innerHTML, /schema/);
assert.doesNotMatch(editor.app.innerHTML, /<option value="reader">/, 'no self-dependency');
editor.change('addUse', 'config');
editor.click('runForm');
assert.equal(editor.posted.at(-1).values.uses, 'schema,config');
editor.message({ type: 'select', component: 'schema' });
assert.match(editor.app.innerHTML, /Answer with a table \(optional\)/);
assert.match(editor.app.innerHTML, /id/);
assert.match(editor.app.innerHTML, /personal-data classification unknown|not personal data/);
// 0.15.0: each row has Never null / Nullable / Unknown and Yes / No / Unknown as buttons, and a bin to remove it
assert.match(editor.app.innerHTML, /data-a="smartSeg"[^>]*data-name="nullable"[^>]*data-value="no"/);
assert.equal((editor.app.innerHTML.match(/data-a="smartRemove"/g) || []).length, 2, 'one bin per column');
editor.click('smartSeg', { kind: 'column', key: 'd:schema:1', row: '0', name: 'nullable', value: 'no' });
editor.click('smartUse', { key: 'd:schema:1' });
assert.match(editor.app.innerHTML, /id: not null, not personal data/);
editor.click('smartRemove', { key: 'd:schema:1', row: '1' });
assert.equal((editor.app.innerHTML.match(/data-a="smartRemove"/g) || []).length, 1, 'the bin removes its row');
assert.equal(editor.posted.filter(m => m.type === 'inline').length, 0, 'smart helper must never save automatically');
console.log('Generic command groups, docs, uses and schema helper: OK');
