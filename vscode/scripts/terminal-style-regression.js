// Offline regression: the terminal is a rendering shell around an unchanged CLI command.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../..');
const script = fs.readFileSync(path.join(root, 'vscode/media/workspace.js'), 'utf8');
const css = fs.readFileSync(path.join(root, 'vscode/media/workspace.css'), 'utf8');
const forms = JSON.parse(fs.readFileSync(path.join(root, 'contract/forms/forms.json'), 'utf8'));
const commands = JSON.parse(fs.readFileSync(path.join(root, 'contract/cli/commands.json'), 'utf8')).commands;
const handlers = {};
const posted = [];
const app = { innerHTML: '' };
const pre = { textContent: '', classList: { toggle() {} },
  closest() { return { querySelector() { return copy; } }; } };
const copy = { dataset: {}, disabled: false, setAttribute(name, value) { this[name] = value; } };
const document = {
  activeElement: null,
  getElementById(id) { return id === 'app' ? app : id === 'form-preview' ? pre : null; },
  querySelectorAll: () => [], querySelector: () => null,
  body: { addEventListener(name, fn) { handlers[name] = fn; } },
};
const window = { ARCHVOW_ROLE: 'editor', ARCHVOW_LOGO: 'logo',
  addEventListener(name, fn) { handlers[name] = fn; }, removeEventListener() {}, setTimeout };
vm.runInNewContext(script, { window, document, console, setTimeout, clearTimeout,
  acquireVsCodeApi: () => ({ postMessage(message) { posted.push(message); } }), CSS: { escape: String } });
const message = m => handlers.message({ data: m });
message({ type: 'init', commands, forms, catalog: null, icons: {}, types: {}, rules: {} });
const state = {
  fr: false, project: { name: 'sales-pipeline', root: '/tmp/sales-pipeline' }, readiness: 'READY',
  spec: { containers: [{ package: 'sales-pipeline', components: [] }] }, check: { violations: [], errors: 0, warnings: 0 },
  rows: [], snapshots: {}, derived: {}, work: { pending: [], resolved: [], otherPending: [], otherResolved: [] },
  branch: 'feature/test', base: '', briefs: { folders: [], files: [] }, install: {}, aiTools: [],
};
message({ type: 'state', state });
message({ type: 'openCommand', command: 'init', values: {} });
assert.match(app.innerHTML, /class="terminal-command"/);
assert.match(app.innerHTML, /id="form-preview"/);
assert.match(app.innerHTML, /terminal-lights/);
assert.match(app.innerHTML, /class="terminal-prompt"/);
assert.match(app.innerHTML, /<pre class="terminal-content"><span class="terminal-prompt"[^>]*>\$<\/span> <span/, 'prompt and command share the first line');
assert.match(app.innerHTML, /terminal-copy/);
assert.doesNotMatch(app.innerHTML, /class="terminal-copy"[^>]*\sdisabled/, 'copy is enabled for visible preview text');
assert.match(css, /\.terminal-command\s*\{/);
assert.match(css, /var\(--vscode-editor-background\)/, 'terminal colors follow the IDE theme');
assert.match(css, /overflow-wrap: anywhere/, 'long parameters never force horizontal scrolling');
assert.match(css, /\.terminal-text\.missing\s*\{[^}]*font-style: normal/, 'incomplete commands are not italic');
const literal = 'archvow init mytemplate --answer ticket_pattern=[A-Z][A-Z0-9]*-[0-9]+';
message({ type: 'formPreview', command: 'init', text: literal, missing: '', files: [] });
assert.equal(pre.textContent, literal, 'preview remains the exact original shell command');
assert.equal(copy.dataset.text, literal, 'terminal copy gets the full command');
assert.equal(copy.disabled, false);
handlers.click({ target: { closest() { return { dataset: { a: 'copyText', text: copy.dataset.text }, disabled: false }; } } });
assert.equal(posted.at(-1).type, 'copy');
assert.equal(posted.at(-1).text, literal, 'the copy button posts the exact original command');
message({ type: 'formPreview', command: 'init', text: '', missing: 'Required field: package', files: [] });
assert.equal(copy.dataset.text, 'Required field: package', 'copy uses the currently displayed preview');
assert.equal(copy.disabled, false, 'incomplete preview remains copyable as text, not as a runnable command');
assert.match(copy.title, /not a runnable command/, 'incomplete preview is clearly identified');
handlers.click({ target: { closest() { return { dataset: { a: 'copyText', text: copy.dataset.text }, disabled: false }; } } });
assert.equal(posted.at(-1).text, 'Required field: package', 'copy preview works before a command is complete');
for (const target of ['terminalCommand(i.command)', 'terminalCommand(c.command)',
  'terminalCommand(g.command.text)', 'terminalCommand(command, { copy: false })', "terminalCommand(shown || 'archvow ' + name"]) {
  assert.ok(script.includes(target), `expected command renderer: ${target}`);
}
assert.match(script, /<pre class="request">/, 'AI instructions remain prose, not a shell command');
console.log('Terminal presentation in forms, installation, confirmations, guidance, examples and live copy: OK');
