// Offline formatter coverage: all command result families share the card presentation,
// while each command's actual data, links, error text and raw-output affordance survive.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
// npm test installs TypeScript locally. The global fallback also supports offline audits.
let ts;
try { ts = require('typescript'); }
catch {
  const { execFileSync } = require('node:child_process');
  ts = require(path.join(execFileSync('npm', ['root', '-g'], { encoding: 'utf8' }).trim(), 'typescript'));
}
const root = path.resolve(__dirname, '../..');
const formatter = fs.readFileSync(path.join(root, 'vscode/src/formatter.ts'), 'utf8');
const css = fs.readFileSync(path.join(root, 'vscode/media/workspace.css'), 'utf8');
const commands = JSON.parse(fs.readFileSync(path.join(root, 'contract/cli/commands.json'), 'utf8')).commands;
const compiled = ts.transpileModule(formatter, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
const context = { exports: {}, require(id) {
  if (id === './i18n') return { tr: (fr, en, french) => fr ? french : en };
  if (id === './workItems') return { str: (v,k) => String(v?.[k] ?? ''),
    objects: (o,k) => Array.isArray(o?.[k]) ? o[k].filter(x => x && typeof x === 'object' && !Array.isArray(x)) : [] };
  throw new Error('Unknown dependency ' + id);
} };
vm.runInNewContext(compiled, context);
const render = context.exports.renderResult;
assert.equal(typeof render, 'function');
const data = read => JSON.stringify(read);
const cases = [
  ['next', 0, data([{ component: 'config', type: 'foundation', readiness: 'blocked: 2 decisions to make', tokens: 659, notes: ['examine decisions'] }]), '', ['config', '659 tokens', 'archvow specify config']],
  ['full check', 1, data({ ok: false, errors: 1, warnings: 0, components: [], violations: [{ code: 'ARCH008', severity: 'error', message: 'missing file', file: '.archvow/spec.vow' }] }), '', ['ARCH008', 'check-files-marker']],
  ['audit', 0, data([{ kind: 'decision', ref: 'R1', text: 'Proceed? -> yes', by: '@team', flags: ['review'] }]), '', ['R1', '@team']],
  ['doctor', 0, data([{ check: 'Python', status: 'fail', detail: 'Interpreter not available', fix: 'Choose Python' }]), '', ['Python', 'Choose Python']],
  ['catalog', 0, data({ templates: [{ name: 'python-app', description: 'Python', version: '1', kind: 'app' }] }), '', ['python-app']],
  ['show', 0, data({ name: 'system', properties: { values: [1, 2] } }), '', ['system', 'properties']],
  ['status', 0, data([{ name: 'one', state: 'pending' }, { name: 'two', state: 'resolved' }]), '', ['pending', 'resolved']],
  ['init', 0, 'Created .archvow/\nNext: create a component', '', ['Created .archvow/', 'create a component', 'class="result-section">Next<']],
  ['prompt', 0, 'Inspect <script>alert(1)</script>', '', ['&lt;script&gt;']],
  ['show', 1, '', 'No module named archvow', ['not installed', 'Exit code: 1']],
  ['changes', 0, 'Output from an ordinary command', '', ['Output from an ordinary command']],
];
for (const [label, code, stdout, stderr, expected] of cases) {
  const html = render(label, code, stdout, stderr, false, false);
  assert.match(html, /^<div class="archvow-result">/);
  assert.match(html, /class="result-footnote"/);
  assert.match(html, /class="result-heading /, `Result heading: ${label}`);
  assert.match(html, /class="result-heading-eyebrow">ARCHVOW · RESULT</,
    `Clearly identifiable visual result header: ${label}`);
  for (const fragment of expected) assert.ok(html.includes(fragment), `${label}: missing ${fragment}`);
  if ((/next|full check|audit|doctor|catalog|show|status/.test(label)) && (stdout.startsWith('{') || stdout.startsWith('['))) {
    assert.ok(html.includes('class="card result-entry'), `${label}: expected shared card`);
  }
  assert.doesNotMatch(html, /<script>/, 'Output never injects HTML');
}
const french = render('next', 0, data([{ component: 'config', readiness: 'blocked' }]), '', false, true);
assert.match(french, /bloqués/);
assert.match(css, /\.archvow-result \.card\.result-entry/);
assert.match(css, /\.archvow-result \.result-chip/);
assert.match(css, /\.archvow-result \.result-footnote/);
assert.match(css, /\.archvow-result \.result-console/);
assert.match(css, /\.archvow-result \.result-heading-icon/);
assert.match(render('changes', 0, 'An ordinary output', '', false, false), /class="result-console"/);
assert.match(css, /@media \(max-width: 480px\)/);
// Every registered CLI command reaches the same formatted-result entry point.
// Synthetic output is used here only to exercise presentation, never to replace the
// real command's data or alter its execution.
let covered = 0;
for (const command of commands) {
  const label = command.path.join(' ');
  for (const fr of [false, true]) {
    const html = render(label, 0, JSON.stringify({ name: 'sample', detail: 'unchanged value', count: 2 }), '', false, fr);
    assert.match(html, /^<div class="archvow-result">/);
    assert.match(html, /result-heading/);
    if (!/^(next|catalog|prompt)$/.test(label)) {
      assert.match(html, /result-entry/);
      assert.ok(html.includes('unchanged value'), `${label}: lost generic result data`);
    }
    assert.ok(html.includes('result-footnote'), `${label}: lost exit status`);
  }
  covered++;
}
assert.ok(covered >= 49, `Expected all commands, received ${covered}`);
for (const label of ['next', 'check', 'doctor', 'audit', 'init', 'component add', 'review', 'brief show']) {
  const fail = render(label, 1, '', 'Permission denied', false, false);
  assert.ok(fail.includes('Command failed'), `${label}: error status missing`);
  assert.ok(fail.includes('Access denied'), `${label}: error guidance missing`);
  const cancelled = render(label, -1, '', '', true, false);
  assert.ok(cancelled.includes('Cancelled or timed out'), `${label}: cancelled state missing`);
}
console.log(`${cases.length} representative scenarios, ${covered} CLI commands in EN/FR, failure/cancel paths and responsive card CSS: OK`);
