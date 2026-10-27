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
