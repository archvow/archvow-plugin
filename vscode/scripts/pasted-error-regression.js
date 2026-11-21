// Offline regression (archvow 2.7.0, contract 2.5): an error pasted in the find field is shown as an error - what
// went wrong, the template's known errors it matches with their fix and why, where it broke with buttons to go
// there - from archvow's real output (contract/cli/python-app/pasted-error.json). Before, it was a search found
// nowhere, or the component alone (its known errors lost).
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../..');
const real = JSON.parse(fs.readFileSync(path.join(root, 'contract', 'cli', 'python-app', 'pasted-error.json'), 'utf8'));
let source = fs.readFileSync(path.join(root, 'vscode', 'media', 'workspace.js'), 'utf8');
source = source.replace("  render();\n  post({ type: 'ready' });\n}());", `  window.audit = { ui, joinLines, pastedErrorView };
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
vm.runInNewContext(source, { document, window, console, setTimeout, clearTimeout,
  acquireVsCodeApi: () => ({ postMessage: () => {} }), CSS: { escape: String } });
const audit = window.audit;
handlers.message({ data: { type: 'init', commands: [], forms: {}, catalog: {}, types: {}, icons: {} } });
handlers.message({ data: { type: 'state', state: { fr: false, project: { name: 't', root: '/tmp/t' }, install: {}, snapshots: {} } } });

// 1. archvow's real answer: no known error, the file where it broke
assert.equal(real.contract, '2.5');
handlers.message({ data: { type: 'search', name: real.error.error, result: { ...real, around: [{ n: 28, text: '        raise ValueError(...)' }] } } });
let html = app.innerHTML;
assert.match(html, /class="card pasted-error"/, 'an error page, not a search');
assert.match(html, /ValueError: invoice F-1 is a draft/, 'what went wrong');
assert.match(html, /Not a known error of this project&#39;s template|Not a known error of this project's template/, 'no known error: said');
assert.match(html, /Where it broke/, 'where it broke');
assert.match(html, /src\/invoicing\/p3_services\/mark_paid\.py:28/, 'its file and line');
assert.match(html, /code of mark_paid, inside mark_paid/, 'its component and function');
assert.match(html, /data-a="openFile"[^>]*data-file="src\/invoicing\/p3_services\/mark_paid\.py" data-line="28"/, 'open at the line');
assert.match(html, /data-a="pick"[^>]*data-id="mark_paid"/, 'show the component');
assert.match(html, /<span class="here">  28  /, 'the line itself, marked');
assert.match(html, /Fixed\? Record the lesson/, 'where the lesson goes');

// 2. a known error of the template, and no file of the project
html = audit.pastedErrorView({ error: { error: "KeyError: 'run_date'", frames: [], known: [{ code: 'ACME012', message: 'The run date comes from datetime.now()',
  fix: 'read it from config', why: 'INC-12', severity: 'error' }] } });
assert.match(html, /<b>ACME012<\/b> The run date comes from datetime\.now\(\)/, 'the known error');
assert.match(html, /Fix: read it from config/, 'its fix');
assert.match(html, /Why: INC-12/, 'its why');
assert.match(html, /No file of this project in it/, 'no file: said');
assert.doesNotMatch(html, /data-a="openFile"/, 'nothing to open');

// 3. a traceback pasted in the one-line find field keeps its lines apart
assert.equal(audit.joinLines('Traceback (most recent call last):\n  File "a.py", line 2, in f\nKeyError: \'x\'\n'),
  'Traceback (most recent call last): File "a.py", line 2, in f KeyError: \'x\'');
const field = { id: 'find', value: '', selectionStart: 0, selectionEnd: 0, setRangeText(text) { this.value = text; } };
let prevented = false;
handlers.paste({ target: field, clipboardData: { getData: () => 'in mark_paid\nValueError: x' }, preventDefault: () => { prevented = true; } });
assert.equal(field.value, 'in mark_paid ValueError: x', 'one space between the lines, not glued');
assert.equal(audit.ui.findText, 'in mark_paid ValueError: x');
assert.ok(prevented, 'the pasted text is the joined one');
console.log('pasted-error regression: OK (known errors, where it broke, the lines kept apart)');
