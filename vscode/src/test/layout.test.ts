// Where the workspace lives (0.7.0): the sidebar chooses, ONE editor tab shows. These checks hold the
// contributions and the page's two roles to it; the end-to-end run (sidebar + tab in Chromium) is in
// the CHANGELOG's notes.
import * as assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { test } from 'node:test';

const ROOT = path.resolve(__dirname, '..', '..');
const manifest = JSON.parse(fs.readFileSync(path.join(ROOT, 'package.json'), 'utf8'));
const page = fs.readFileSync(path.join(ROOT, 'media', 'workspace.js'), 'utf8');
const host = fs.readFileSync(path.join(ROOT, 'src', 'workspace.ts'), 'utf8');

test('the sidebar is one webview view: Overview, Components, Commands; no tree view left', () => {
  const views = manifest.contributes.views.archvow;
  assert.equal(views.length, 1);
  assert.equal(views[0].type, 'webview');
  assert.equal(views[0].id, 'archvow.workspace');
  assert.equal(JSON.stringify(manifest.contributes).includes('archvow.components'), false);
  assert.deepEqual(manifest.contributes.menus['view/title'].map((m: { command: string }) => m.command), ['archvow.check', 'archvow.refresh']);
  assert.equal(manifest.contributes.viewsContainers.activitybar[0].icon, 'media/activity.svg');
});

test('one editor tab, created in one place and reused', () => {
  assert.equal(host.match(/createWebviewPanel\(/g)?.length, 1);
  assert.match(host, /if \(this\.panel !== undefined\) \{/);
  assert.match(host, /registerWebviewViewProvider|attachView/);
  assert.match(page, /const NAV = window\.ARCHVOW_ROLE !== 'editor'/);
  // the editor tab's title follows what it shows
  assert.match(page, /'archvow: ' \+ ui\.command/);
});

test('the archvow logo: the page header, the editor tab icon, the activity bar', () => {
  assert.match(host, /media\('archvow\.png'\)/);
  assert.match(host, /'media', 'vow\.svg'\)/);
  const logoPath = 'M208 250H470L633 550L795 250H1057L810 705V1013H720V700A88.5 100 0 0 0 543 700V1013H453V705Z';
  for (const icon of ['activity.svg', 'vow.svg', 'vow_dark.svg']) {
    assert.ok(fs.readFileSync(path.join(ROOT, 'media', icon), 'utf8').includes(logoPath), icon);
  }
});

test('Check shows the count per rule archvow gives (check --json `counts`), as IntelliJ does', () => {
  const source = page.match(/function byRule\(check\) \{[\s\S]*?\n  \}/);
  assert.ok(source, 'byRule() is in the page');
  const english = (en: string) => en;
  const byRule = new Function('t', `${source[0]}; return byRule;`)(english);
  const cli = path.resolve(ROOT, '..', 'contract', 'cli');
  const check = JSON.parse(fs.readFileSync(path.join(cli, 'pyspark-pipeline', 'check.json'), 'utf8'));
  const counts = Object.entries(check.counts as Record<string, number>);
  const expected = counts.length < 2 ? '' : 'By rule: ' + counts.map(([code, n]) => `${code} ${n}`).join('  ·  ');
  assert.equal(byRule(check), expected);
  assert.equal(byRule({ counts: { ARCH001: 146, SPK008: 415 } }), 'By rule: ARCH001 146  ·  SPK008 415');
  assert.equal(byRule({ counts: { ARCH001: 1 } }), '');
  assert.equal(byRule({}), '');
  assert.match(page, /<p class="by-rule">/);
});

test('a component shows what it uses and what uses it (show --json `uses`, `used_by`), as IntelliJ does', () => {
  const source = page.match(/function links\(view\) \{[\s\S]*?\n  \}/);
  assert.ok(source, 'links() is in the page');
  const esc = (x: string) => String(x).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;');
  const list = (o: Record<string, unknown>, k: string) => (Array.isArray(o[k]) ? (o[k] as unknown[]) : []);
  const links = new Function('t', 'esc', 'list', `${source[0]}; return links;`)((en: string) => en, esc, list);
  const cli = path.resolve(ROOT, '..', 'contract', 'cli', 'pyspark-pipeline', 'show');
  const view = JSON.parse(fs.readFileSync(path.join(cli, 'orders_reader.json'), 'utf8'));
  assert.deepEqual(view.used_by, ['my_pipeline_job']);
  assert.equal(links(view), '<p class="links muted small">Uses: <span class="link" data-a="pick" data-id="orders_schema">orders_schema</span>'
    + '  ·  Used by: <span class="link" data-a="pick" data-id="my_pipeline_job">my_pipeline_job</span></p>');
  assert.equal(links({ uses: [], used_by: [] }), '');
  assert.match(page, /\$\{links\(view\)\}/);
});

test('"Show archvow for this file" on an editor tab and in the editor: archvow show <file>:<line>', () => {
  const commands = manifest.contributes.commands.map((c: { command: string }) => c.command);
  assert.ok(commands.includes('archvow.showFile'));
  for (const where of ['editor/title/context', 'editor/context']) {
    assert.deepEqual(manifest.contributes.menus[where].map((m: { command: string }) => m.command), ['archvow.showFile']);
  }
  const extension = fs.readFileSync(path.join(ROOT, 'src', 'extension.ts'), 'utf8');
  assert.match(extension, /registerCommand\('archvow\.showFile'/);
  assert.match(host, /\['show', line > 0 \? `\$\{relative\}:\$\{line\}` : relative, '--json'\]/);
});

test('a component shows its business examples (show --json `examples`): tested and unchanged, or what is missing', () => {
  const source = page.match(/function examples\(view\) \{[\s\S]*?\n  \}/);
  assert.ok(source, 'examples() is in the page');
  const esc = (x: string) => String(x).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;');
  const list = (o: Record<string, unknown>, k: string) => (Array.isArray(o[k]) ? (o[k] as unknown[]) : []);
  const str = (o: Record<string, unknown>, k: string) => (o[k] === undefined || o[k] === null ? '' : String(o[k]));
  const examples = new Function('t', 'esc', 'list', 'str', `${source[0]}; return examples;`)((en: string) => en, esc, list, str);
  const folder = 'tests/examples/clean_orders/SALES-5-negative';
  const html = examples({ examples: [
    { name: 'SALES-5-negative', ticket: 'SALES-5', level: 'unit', folder, tested: true, unchanged: true },
    { name: 'SALES-6-empty', level: 'unit', folder, tested: false, unchanged: true },
    { name: 'SALES-7-late', level: 'integration', folder, tested: true, unchanged: false }] });
  assert.match(html, /<span class="ok" title="[^"]*· unit · tested">✓ SALES-5-negative \(SALES-5\)<\/span>/);
  assert.match(html, /<span class="tone-warn" title="[^"]*no marked test yet \(ARCH056\)">✖ SALES-6-empty<\/span>/);
  assert.match(html, /files changed since recorded \(ARCH055\)">✖ SALES-7-late/);
  assert.equal(examples({ examples: [] }), '');
  assert.match(page, /\$\{links\(view\)\}\$\{examples\(view\)\}/);
});

test('a component opens on its first open item: the answer panel is ready (0.11.0)', () => {
  const source = page.match(/function current\(kind, rows, label, isOpen\) \{[\s\S]*?\n  \}/);
  assert.ok(source, 'current() is in the page');
  const ui = { sel: { decision: '' } as Record<string, string> };
  const current = new Function('ui', `${source[0]}; return current;`)(ui);
  const label = (r: { number: number }) => 'D' + r.number;
  const isOpen = (r: { answer: string | null }) => !r.answer;
  const rows = [{ number: 1, answer: 'Fail' }, { number: 2, answer: 'FAILFAST' }, { number: 3, answer: null }, { number: 4, answer: null }];
  assert.equal(current('decision', rows, label, isOpen).number, 3);
  assert.equal(ui.sel.decision, 'D3');
  ui.sel.decision = 'D1'; // a card clicked: kept
  assert.equal(current('decision', rows, label, isOpen).number, 1);
  ui.sel.decision = '';   // nothing open: the first
  assert.equal(current('decision', rows.slice(0, 2), label, isOpen).number, 1);
  assert.equal(current('decision', [], label, isOpen), null);
  // another component starts with no selection (a key like D3 of the previous one would be kept)
  assert.match(page, /if \(ui\.component !== id\) \{\s*\/\/[^\n]*\n\s*for \(const kind of \['decision', 'question', 'safety', 'assumption', 'data'\]\) ui\.sel\[kind\] = '';/);
});

test('the items as cards on the left, the selected one answered on the right (0.11.0)', () => {
  const source = page.match(/function cards\(kind, rows, label, card\) \{[\s\S]*?\n  \}/);
  assert.ok(source, 'cards() is in the page');
  const esc = (x: string) => String(x).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;');
  const short = (s: string, n: number) => (s.length <= n ? s : s.substring(0, n - 1) + '…');
  const ui = { sel: { decision: 'D3' } };
  const cards = new Function('t', 'esc', 'short', 'ui', `${source[0]}; return cards;`)((en: string) => en, esc, short, ui);
  const view = JSON.parse(fs.readFileSync(path.resolve(ROOT, '..', 'contract', 'cli', 'pyspark-pipeline', 'show', 'orders_reader.json'), 'utf8'));
  const rows = view.decisions.slice(0, 2).concat([{ number: 3, question: 'Header row missing: infer the columns or fail?', answer: null }]);
  const html = cards('decision', rows, (r: { number: number }) => 'D' + r.number, (r: Record<string, string>) => ({
    number: String(r.number), title: r.question, open: !r.answer, answer: r.answer || '', by: r.answer ? `${r.by} · ${r.on}` : '' }));
  assert.match(html, /<li class="item-card  done" data-a="sel" data-kind="decision" data-key="D1">/);
  assert.match(html, /<div class="card-a">Fail with a clear error: a missing input is an operations problem<\/div>/);
  assert.match(html, /<div class="card-by muted">architect@example\.com · 2026-10-08<\/div>/);
  assert.match(html, /<li class="item-card selected open" data-a="sel" data-kind="decision" data-key="D3">/);
  assert.match(html, /<div class="card-open">● Open<\/div>/);
  // the four views whose items are answered are split; the answer heading says what is answered
  assert.equal(page.match(/return split\(left, right\);/g)?.length, 4);
  assert.match(page, /t\(`Answer decision \$\{number\}`, `Répondre à la décision \$\{number\}`\)/);
  const css = fs.readFileSync(path.join(ROOT, 'media', 'workspace.css'), 'utf8');
  assert.match(css, /\.split \{ display: grid; grid-template-columns: minmax\(0, 1fr\) minmax\(0, 1fr\);/);
  assert.match(css, /@media \(max-width: 760px\) \{ \.split \{ grid-template-columns: minmax\(0, 1fr\); \} \}/);
});
