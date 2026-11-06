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
  assert.deepEqual(manifest.contributes.menus['view/title'], [], 'no duplicate Check/Reload outside the Webview header');
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
  assert.deepEqual(manifest.contributes.menus['editor/title/context'].map((m: { command: string }) => m.command), ['archvow.showFile']);
  // 0.18.0: in the editor, "Find in archvow project" (archvow show <name>) under it
  assert.deepEqual(manifest.contributes.menus['editor/context'].map((m: { command: string }) => m.command), ['archvow.showFile', 'archvow.findName']);
  const extension = fs.readFileSync(path.join(ROOT, 'src', 'extension.ts'), 'utf8');
  assert.match(extension, /registerCommand\('archvow\.showFile'/);
  assert.match(host, /\['show', line > 0 \? `\$\{relative\}:\$\{line\}` : relative, '--json'\]/);
});

test('a component shows its business examples (show --json `examples`): tested and unchanged, or what is missing', () => {
  const source = page.match(/function examples\(view\) \{[\s\S]*?\n  \}/);
  assert.ok(source, 'examples() is in the page');
  const state = page.match(/function exampleState\(e\) \{[\s\S]*?\n  \}/);
  assert.ok(state, 'exampleState() is in the page');
  const esc = (x: string) => String(x).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;');
  const list = (o: Record<string, unknown>, k: string) => (Array.isArray(o[k]) ? (o[k] as unknown[]) : []);
  const str = (o: Record<string, unknown>, k: string) => (o[k] === undefined || o[k] === null ? '' : String(o[k]));
  const examples = new Function('t', 'esc', 'list', 'str', `${state[0]}; ${source[0]}; return examples;`)((en: string) => en, esc, list, str);
  const folder = 'tests/examples/clean_orders/SALES-5-negative';
  // archvow 1.21: each example's state is its test's own verdict (result)
  const html = examples({ examples: [
    { name: 'SALES-5-negative', ticket: 'SALES-5', level: 'unit', folder, tested: true, unchanged: true, result: 'passing' },
    { name: 'SALES-6-empty', level: 'unit', folder, tested: false, unchanged: true, result: 'no_test' },
    { name: 'SALES-7-late', level: 'integration', folder, tested: true, unchanged: false, result: 'passing' },
    { name: 'SALES-8-wrong', level: 'unit', folder, tested: true, unchanged: true, result: 'failing', files: [] }] });
  assert.match(html, /<span class="ok" title="[^"]*· unit · passing">✔ SALES-5-negative \(SALES-5\)<\/span>/);
  assert.match(html, /<span class="tone-warn" title="[^"]*no marked test yet \(ARCH056\)">○ SALES-6-empty<\/span>/);
  assert.match(html, /files changed since recorded \(ARCH055\)">✖ SALES-7-late/);
  assert.match(html, /<span class="tone-bad" title="[^"]*· failing">✖ SALES-8-wrong<\/span>/);
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
  assert.match(html, /<div class="card-a" title="Fail with a clear error: a missing input is an operations problem">Fail with a clear error: a missing input is an operations problem<\/div>/);
  assert.match(html, /<div class="card-by muted">architect@example\.com · 2026-10-08<\/div>/);
  assert.match(html, /<li class="item-card selected open" data-a="sel" data-kind="decision" data-key="D3">/);
  assert.match(html, /<div class="card-open">● Open<\/div>/);
  // the four views whose items are answered are split; the answer heading says what is answered
  assert.equal(page.match(/return split\(left, rows\.length \? right : ''\);/g)?.length, 4);
  // a view with no item: "Nothing to display." alone, no answer panel (0.11.1)
  const splitSource = page.match(/function split\(left, right\) \{[\s\S]*?\n  \}/);
  assert.ok(splitSource, 'split() is in the page');
  const split = new Function(`${splitSource[0]}; return split;`)();
  assert.equal(split('<ul class="cards"><li class="muted empty">Nothing to display.</li></ul>', ''),
    '<div class="split single"><div class="split-list"><ul class="cards"><li class="muted empty">Nothing to display.</li></ul></div></div>');
  assert.match(split('L', 'R'), /<aside class="split-answer">R<\/aside>/);
  assert.equal(cards('question', [], (r: { key: string }) => r.key, () => ({})), '<ul class="cards" data-scroll="cards-question"><li class="muted empty">Nothing to display.</li></ul>');
  assert.match(page, /t\(`Answer decision \$\{number\}`, `Répondre à la décision \$\{number\}`\)/);
  const css = fs.readFileSync(path.join(ROOT, 'media', 'workspace.css'), 'utf8');
  assert.match(css, /\.split \{ display: grid; grid-template-columns: minmax\(0, 1fr\) minmax\(0, 1fr\);/);
  assert.match(css, /@media \(max-width: 760px\) \{ \.split \{ grid-template-columns: minmax\(0, 1fr\); \} \}/);
});

test('Overview is a summary only; the Components browser owns the full status + pending inventory', () => {
  assert.match(page, /function architectureView\(\)/);
  assert.match(page, /class="card architecture-summary"/);
  assert.match(page, /data-a="openComponents"/);
  assert.doesNotMatch(page, /<table class="arch"><thead>/, 'Overview no longer contains the component table');
  assert.match(page, /class="component-state \$\{esc\(stateTone\(state\)\)\}"/, 'component rows show their own status');
  assert.match(page, /<span class="count">\$\{p\}<\/span>/, 'pending count is preserved');
  assert.match(page, /data-a="addHere"/, 'per-layer add component control remains available');
});

test('the editor tab goes back to the component a command was opened from (0.11.3)', () => {
  // armed when the page asks for a command from a component, kept when the command opens, cleared otherwise
  assert.match(page, /if \(!NAV && ui\.mode === 'component' && ui\.component && ui\.view !== 7 && \(m\.type === 'openCommand' \|\| m\.type === 'choose'\)\)/);
  assert.match(page, /case 'openCommand':\s*ui\.forceWorkspace = true;\s*ui\.back = ui\.backArmed;\s*ui\.backArmed = null;/);
  assert.match(page, /case 'select':\s*ui\.forceWorkspace = true;\s*ui\.mode = 'component';\s*ui\.back = null;/);
  assert.match(page, /<button class="secondary back" data-a="back"/);
  assert.match(page, /ui\.view = to\.view;\s*post\(\{ type: 'show', component: to\.component \}\);/);
  // the cards: one line each, whole in the tooltip; Reload drawn, not a "↻" character
  const css = fs.readFileSync(path.join(ROOT, 'media', 'workspace.css'), 'utf8');
  assert.match(css, /\.item-card \.card-q, \.item-card \.card-a, \.item-card \.card-open, \.item-card \.card-note \{ white-space: nowrap; overflow: hidden; text-overflow: ellipsis; \}/);
  assert.equal(page.includes("btn('↻'"), false);
  assert.equal(page.match(/iconBtn\(REFRESH, 'refresh'/g)?.length, 1, 'one shared header refresh; none in Overview');
});

test('a component shows its progress in one bar (show --json), as IntelliJ\'s ArchvowProgress does', () => {
  const source = page.match(/function progress\(view\) \{[\s\S]*?\n  \}/);
  const bar = page.match(/function progressBar\(view\) \{[\s\S]*?\n  \}/);
  const state = page.match(/function exampleState\(e\) \{[\s\S]*?\n  \}/);
  assert.ok(source && bar && state, 'progress(), progressBar() and exampleState() are in the page');
  const esc = (x: string) => String(x).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;');
  const list = (o: Record<string, unknown>, k: string) => (Array.isArray(o[k]) ? (o[k] as unknown[]) : []);
  const str = (o: Record<string, unknown>, k: string) => (o[k] === undefined || o[k] === null ? '' : String(o[k]));
  const [progress, progressBar] = new Function('t', 'esc', 'list', 'str', `${state[0]}; ${source[0]}; ${bar[0]}; return [progress, progressBar];`)(
    (en: string) => en, esc, list, str);
  const cli = path.resolve(ROOT, '..', 'contract', 'cli', 'pyspark-pipeline', 'show');
  const view = JSON.parse(fs.readFileSync(path.join(cli, 'clean_orders.json'), 'utf8'));
  const parts = progress(view).map((p: { key: string; done: number; total: number; complete: boolean }) => [p.key, p.done, p.total, p.complete]);
  assert.deepEqual(parts, [
    ['decisions', 9, 9, true],
    ['safety', 1, 5, false],
    ['tasks', 0, 3, false],
    ['examples', 0, 2, false],  // archvow 1.21: the shipped project's two examples, no test yet
  ]);
  const html = progressBar(view);
  assert.match(html, /title="Decisions made: 9\/9 · Safety answered: 1\/5 · Tasks done: 0\/3 · Examples passing: 0\/2"/);
  assert.match(html, /progress-part complete" data-part="decisions"[\s\S]*progress-fill p100/);
  assert.match(html, /progress-part partial" data-part="safety"[\s\S]*?progress-fill p20"/);
  // examples count when the component has some; a part with nothing to count is left out
  // archvow 1.21: an example counts once its test passes (the test's verdict, result), files unchanged
  const examples = progress({ decisions: [{ answer: null }, { answer: 'yes' }], safety: [], examples: [
    { name: 'a', tested: true, unchanged: true, result: 'passing' }, { name: 'b', tested: true, unchanged: false, result: 'passing' },
    { name: 'd', tested: true, unchanged: true, result: 'failing' }, { name: 'c', tested: false }] });
  assert.deepEqual(examples.map((p: { key: string; done: number; total: number }) => [p.key, p.done, p.total]), [['decisions', 1, 2], ['examples', 1, 4]]);
  assert.match(progressBar({ decisions: [{ answer: null }, { answer: 'a' }] }), /p50/);
  assert.match(progressBar({ tasks: Array.from({ length: 20 }, (_, i) => ({ done: i > 0 })) }), /partial[\s\S]*p90/);
  assert.equal(progressBar({}), '');
  assert.match(page, /\$\{progressBar\(view\)\}/);
});


test('UX v4: VS Code hover, responsive toolbar, and Checks overview row', () => {
  const css = fs.readFileSync(path.join(ROOT, 'media', 'workspace.css'), 'utf8');
  assert.match(css, /\.project-action:hover:not\(\[disabled\]\)/);
  assert.match(css, /\.project-action:focus-visible/);
  assert.match(css, /\.project-action-container \{ container: project-actions \/ inline-size/);
  // Nine 42px actions plus separators and the bar chrome take 397px.
  // The More menu appears only when that width is no longer available;
  // subsequent breakpoints remove one action at a time, without leaving gaps.
  assert.match(css, /\.project-action-bar \.action-more \{ display: none; \}/);
  for (const [width, index] of [[396, 8], [353, 7], [310, 6], [267, 5], [224, 4], [181, 3], [138, 2], [95, 1], [52, 0]]) {
    assert.match(css, new RegExp(`@container project-actions \\(max-width: ${width}px\\)`));
    assert.match(css, new RegExp(`\\.project-action-slot\\.action-slot-${index} \\{ display: none; \\}`));
    assert.match(css, new RegExp(`\\.project-more-menu \\.overflow-action-${index} \\{ display: flex; \\}`));
  }
  assert.match(page, /class="project-action-container"/);
  assert.match(page, /class="project-action-slot action-slot-\$\{index\}"/);
  assert.match(page, /data-a="openBriefCommands"|\['briefs', t\('Briefs'/);
  assert.match(page, /openBriefCommands: \(\) =>/);
  assert.match(page, /class="card checks-summary"/);
  assert.match(page, /data-a="openChecks"/);
  assert.doesNotMatch(page, /class="checks-badges"/, 'diagnostic counters live in the Checks card');
});


test('Overview Checks opens the complete diagnostics in the editor, not the generated spec', () => {
  assert.match(host, /case 'openChecks':[\s\S]*?this\.toEditor\(\{ type: 'showCheckDetails', index: 0 \}\)/);
  assert.match(host, /case 'openChecksReference':[\s\S]*?this\.openChecksReference\(\)/);
  assert.match(page, /openChecksReference: \(\) => post\(\{ type: 'openChecksReference' \}\)/);
  assert.match(page, /const listPart = `<ul class="list check-list"/);
  assert.match(page, /checks-full">\$\{title\}\$\{totals\}\$\{listPart\}/);
});
