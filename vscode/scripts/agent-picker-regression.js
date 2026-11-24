// Offline regression (0.26.1): the AI assistants of init (ai_tools, reviewers) are picked from the list archvow
// gives (catalog `suggestions`), and any other name can be added - archvow takes any agent name, so the list is
// never closed. Before: one text box, "comma-separated", with nothing to choose from.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../..');
const read = (...pieces) => JSON.parse(fs.readFileSync(path.join(root, ...pieces), 'utf8'));
const catalog = read('contract', 'cli', 'catalog.json');
let source = fs.readFileSync(path.join(root, 'vscode', 'media', 'workspace.js'), 'utf8');
source = source.replace("  render();\n  post({ type: 'ready' });\n}());", `  window.audit = { ui, initWizard, agentsWith, answerOf, actions };
  render();
  post({ type: 'ready' });
}());`);
const handlers = {};
const app = { innerHTML: '' };
const document = {
  getElementById: (name) => (name === 'app' ? app : null), querySelectorAll: () => [], querySelector: () => null, activeElement: null,
  body: { addEventListener: (name, fn) => { handlers[name] = fn; } },
};
const window = { ARCHVOW_ROLE: 'editor', ARCHVOW_LOGO: '', addEventListener: (name, fn) => { handlers[name] = fn; }, removeEventListener() {}, setTimeout };
const posted = [];
vm.runInNewContext(source, { document, window, console, setTimeout, clearTimeout,
  acquireVsCodeApi: () => ({ postMessage: (msg) => posted.push(msg) }), CSS: { escape: String } });
const audit = window.audit;
handlers.message({ data: { type: 'init', commands: [], forms: {}, catalog, types: {}, icons: {} } });
handlers.message({ data: { type: 'state', state: { fr: false, project: { name: 't', root: '/tmp/t' }, install: {}, snapshots: {}, readiness: 'NOT_INITIALIZED' } } });
audit.ui.catalog = catalog;
audit.ui.initTemplate = 'python-app';

// 1. ai_tools: a chip per chosen assistant, a list of the others archvow knows, a box for any other name
let html = audit.initWizard();
assert.match(html, /data-change="agentAdd" data-kind="init" data-id="ai_tools"/, 'ai_tools: a list to pick from');
assert.match(html, /<span class="chip blue"[^>]*>copilot <button class="link" data-a="agentRemove"/, 'the default, copilot, as a chip');
const offered = /data-id="ai_tools" aria-label[^>]*>(.*?)<\/select>/.exec(html)[1];
assert.match(offered, /value="claude-code"/, 'claude-code offered');
assert.doesNotMatch(offered, /value="copilot"/, 'copilot already chosen: not offered again');
assert.match(offered, /none \(no AI assistant\)/, 'none, said plainly');
assert.match(html, /data-input="agentOther" data-kind="init" data-id="ai_tools"/, 'a box for another assistant');
assert.match(html, /Any other name works too/, 'what another name gets, said');

// 2. adding and removing: none alone, each name once
assert.deepEqual([...audit.agentsWith(['copilot'], 'claude-code')], ['copilot', 'claude-code']);
assert.deepEqual([...audit.agentsWith(['copilot', 'claude-code'], 'none')], ['none']);
assert.deepEqual([...audit.agentsWith(['none'], 'cursor')], ['cursor']);
assert.deepEqual([...audit.agentsWith(['cursor'], 'cursor')], ['cursor']);
handlers.change({ target: { dataset: { change: 'agentAdd', kind: 'init', id: 'ai_tools' }, value: 'claude-code' } });
assert.equal(audit.answerOf('init', 'ai_tools'), 'copilot,claude-code', 'picked from the list');
assert.equal(audit.ui.initTouched.ai_tools, true, 'a picked assistant is sent as an answer');

// 3. another assistant, typed: any agent name archvow takes, lowercased; a wrong one is refused, said why
audit.ui.agentOther['init:ai_tools'] = 'My-Agent';
audit.actions.agentOther({ dataset: { kind: 'init', id: 'ai_tools' } });
assert.equal(audit.answerOf('init', 'ai_tools'), 'copilot,claude-code,my-agent', 'another assistant added');
html = audit.initWizard();
assert.match(html, /<span class="chip warn" title="Not known to archvow: my-agent gets the standard files/, 'an unknown one says what it gets');
audit.ui.agentOther['init:ai_tools'] = 'my agent';
audit.actions.agentOther({ dataset: { kind: 'init', id: 'ai_tools' } });
assert.equal(audit.answerOf('init', 'ai_tools'), 'copilot,claude-code,my-agent', 'a wrong name is not added');
assert.match(posted.at(-1).text, /lowercase letters and digits/, 'and the person is told why');
audit.actions.agentRemove({ dataset: { kind: 'init', id: 'ai_tools', name: 'copilot' } });
assert.equal(audit.answerOf('init', 'ai_tools'), 'claude-code,my-agent', 'removed');

// 4. reviewers too, empty by default
audit.ui.advanced = true;
html = audit.initWizard();
assert.match(html, /data-change="agentAdd" data-kind="init" data-id="reviewers"/, 'reviewers: the same picker');
assert.equal((html.match(/None chosen yet/g) || []).length, 1, 'reviewers: nothing chosen yet, said (ai_tools has its chips)');
console.log('agent-picker regression: OK (known assistants from the list, any other by name, none alone)');
