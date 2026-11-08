// Offline regression: Expand covers the whole VS Code editor webview (as in IntelliJ),
// rather than merely stretching a result body inside a narrow component/form column.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../..');
const css = fs.readFileSync(path.join(root, 'vscode/media/workspace.css'), 'utf8');
let script = fs.readFileSync(path.join(root, 'vscode/media/workspace.js'), 'utf8');
const end = "  render();\n  post({ type: 'ready' });\n}());";
assert.ok(script.includes(end), 'inject test hook without changing shipped code');
script = script.replace(end, `  window.audit = { ui, resultCard, layoutExpandedResult, actions };
${end}`);
const handlers = {};
const app = { innerHTML: '' };
const topRect = { top: 0, bottom: 75 };
const card = { style: {} };
const document = {
  activeElement: null,
  getElementById: id => id === 'app' ? app : null,
  querySelector: selector => selector === '.top' ? { getBoundingClientRect: () => topRect } : null,
  querySelectorAll: selector => selector === '.card.result.result-expanded' ? [card] : [],
  body: { addEventListener: (name, fn) => { handlers[name] = fn; } },
};
const window = {
  ARCHVOW_ROLE: 'editor', ARCHVOW_LOGO: 'logo', innerHeight: 900,
  addEventListener: (name, fn) => { handlers[name] = fn; },
  removeEventListener() {}, setTimeout,
};
vm.runInNewContext(script, { window, document, console, setTimeout, clearTimeout,
  acquireVsCodeApi: () => ({ postMessage() {} }), CSS: { escape: String } });
const { ui, resultCard, actions, layoutExpandedResult } = window.audit;
const targets = ['component', 'decision', 'question', 'safety', 'data', 'command', 'overview', 'quick'];
for (const target of targets) {
  const markup = `<p>Saved answer for ${target}</p>`;
  ui.results[target] = { html: markup, raw: `source output ${target}`, args: 'archvow decision make', running: false };
  ui.resultHeight[target] = 320; // a prior manual drag must be restored on Reduce
  const before = resultCard(target);
  assert.match(before, /class="card result"/, `${target}: normal inline result`);
  assert.match(before, /style="height:320px;max-height:none"/, `${target}: manual height preserved`);
  actions.expandResult({ dataset: { target } });
  const full = resultCard(target);
  assert.match(full, /class="card result result-expanded"/, `${target}: full editor class`);
  assert.match(full, /aria-expanded="true"/, `${target}: expanded state accessibility`);
  assert.match(full, /Reduce/, `${target}: button changes to Reduce`);
  assert.ok(full.includes(markup), `${target}: formatted result unchanged`);
  assert.match(full, /data-a="raw"/, `${target}: Raw output action retained`);
  assert.match(full, /data-a="copyResult"/, `${target}: Copy action retained`);
  assert.match(full, /data-a="closeResult"/, `${target}: Close action retained`);
  assert.equal(ui.resultHeight[target], 320, `${target}: expanding does not alter resize state`);
  actions.expandResult({ dataset: { target } });
  assert.match(resultCard(target), /class="card result"/, `${target}: Reduce returns inline`);
  assert.match(resultCard(target), /style="height:320px;max-height:none"/, `${target}: restores prior size`);
}
actions.expandResult({ dataset: { target: 'component' } });
layoutExpandedResult();
assert.equal(card.style.top, '83px', 'full result starts beneath the Archvow header');
topRect.bottom = 110;
handlers.resize();
assert.equal(card.style.top, '118px', 'full result tracks the header on resize');
ui.raw.component = true;
assert.match(resultCard('component'), /source output component/, 'raw view also expands full editor');
actions.closeResult({ dataset: { target: 'component' } });
assert.equal(ui.resultExpanded.component, undefined, 'closing resets expanded state');
assert.doesNotMatch(resultCard('component'), /result-expanded/, 'closed result disappears');
assert.match(css, /body\.editor \.card\.result\.result-expanded\s*\{[^}]*position:\s*fixed/s,
  'expanded result is viewport-fixed, not confined to an inline column');
assert.match(css, /body\.editor \.card\.result\.result-expanded \.card-body\s*\{[^}]*flex:\s*1 1 auto/s,
  'result body fills available editor height and can scroll');
assert.match(css, /body\.editor \.card\.result\.result-expanded \.grip\s*\{\s*display:\s*none/s,
  'drag handle is hidden only in full mode');
console.log(`Full-editor Expand/Reduce, layout, resize, Raw, Copy, Close and size restore for ${targets.length} result targets: OK`);
