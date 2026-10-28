// The briefs folders of archvow 1.17 (0.12.0): the --from choices, archvow's heading slugs, "Drawn from",
// and archvow's refusal turned into the architect's init --force form. The expected slugs are archvow's own
// (briefs.slug / briefs.sections). The same rules as IntelliJ's ArchvowBriefs (BriefsRegression).
import * as assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import { test } from 'node:test';
import { addFolder, choices, drawnFrom, folderOf, folders, locate, readBriefs, sections, slug } from '../briefs';
import { issueFix } from '../workItems';

const ROOT = path.resolve(__dirname, '..', '..');
const CLI = path.resolve(ROOT, '..', 'contract', 'cli');
const json = (...p: string[]) => JSON.parse(fs.readFileSync(path.join(CLI, ...p), 'utf8'));

test('the folders: init_answers.briefs, `briefs` when unset, none when emptied', () => {
  assert.deepEqual(folders(json('briefs', 'show.json')), ['briefs', 'specs']);
  assert.deepEqual(folders(json('python-app', 'show.json')), ['briefs']);
  assert.deepEqual(folders({ init_answers: {} }), ['briefs']);
  assert.deepEqual(folders({ init_answers: { briefs: ' /specs/ , briefs,specs' } }), ['specs', 'briefs']);
  assert.deepEqual(folders({ init_answers: { briefs: '' } }), []);
  assert.equal(folderOf('specs/001-orders/plan.md', ['briefs', 'specs']), 'specs');
  assert.equal(folderOf('specs2/x.md', ['specs']), '');
});

test('the headings as archvow writes them: lower case, no accents, words joined by -, repeats -2, -3', () => {
  assert.equal(slug('Paying an invoice'), 'paying-an-invoice');
  assert.equal(slug("Données d'entrée : fichiers"), 'donnees-d-entree-fichiers');
  assert.equal(slug('Ça & ÇA — Œuvre'), 'ca-ca-uvre');
  assert.equal(slug('Straße 2026'), 'strae-2026');
  assert.equal(slug('日本'), '');
  const found = sections('# Plan\n## Données\ntext\n## Données\n## Données ##\n### Été\n#NoSpace\n####### seven\n');
  assert.deepEqual(found.map((s) => s.slug), ['plan', 'donnees', 'donnees-2', 'donnees-3', 'ete']);
  assert.deepEqual(found.map((s) => s.line), [1, 2, 4, 5, 6]);
  assert.equal(sections('# 日本')[0].slug, 'section');
});

test('the files of the declared folders only, with their headings; free text stays allowed', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'archvow-briefs-'));
  const write = (file: string, text: string) => {
    fs.mkdirSync(path.dirname(path.join(root, file)), { recursive: true });
    fs.writeFileSync(path.join(root, file), text);
  };
  write('specs/001-orders/plan.md', '# Orders plan\n\n## Data model\n\n## Paying an invoice\n');
  write('specs/001-orders/notes.txt', '# not a heading in a text file\n');
  write('briefs/sales.md', '# Input files\n');
  write('docs/other.md', '# Not declared\n');
  write('specs/diagram.png', 'x');
  const briefs = readBriefs(root, { init_answers: { briefs: 'briefs,specs' } });
  assert.deepEqual(briefs.folders, ['briefs', 'specs']);
  assert.deepEqual(briefs.files.map((f) => f.file), ['briefs/sales.md', 'specs/001-orders/notes.txt', 'specs/001-orders/plan.md']);
  assert.deepEqual(choices(briefs), ['briefs/sales.md', 'briefs/sales.md#input-files', 'specs/001-orders/notes.txt',
    'specs/001-orders/plan.md', 'specs/001-orders/plan.md#orders-plan', 'specs/001-orders/plan.md#data-model',
    'specs/001-orders/plan.md#paying-an-invoice']);
  assert.deepEqual(locate(briefs, 'specs/001-orders/plan.md#paying-an-invoice'), { file: 'specs/001-orders/plan.md', line: 5 });
  assert.deepEqual(locate(briefs, 'specs/001-orders/plan.md#gone'), { file: 'specs/001-orders/plan.md', line: 1 });
  assert.deepEqual(readBriefs(root, { init_answers: { briefs: 'missing' } }).files, []);
  fs.rmSync(root, { recursive: true, force: true });
});

test('"Drawn from", the refusal and ARCH039 as archvow 1.17 prints them (contract/cli/briefs)', () => {
  assert.equal(drawnFrom(json('briefs', 'show', 'mark_paid.json')), 'specs/001-orders/plan.md#paying-an-invoice');
  assert.equal(drawnFrom(json('briefs', 'show', 'config.json')), '');
  const refused = fs.readFileSync(path.join(CLI, 'briefs', 'refused.txt'), 'utf8');
  assert.deepEqual(addFolder(refused), { package: 'invoicing', answer: 'briefs=briefs,specs' });
  assert.equal(addFolder('archvow check: all good'), null);
  const changed = json('briefs', 'check.json').violations.find((v: { code: string }) => v.code === 'ARCH039');
  assert.deepEqual(issueFix('ARCH039', changed.message, ''), { command: 'brief accept', values: { brief: 'specs/001-orders/plan.md#paying-an-invoice' },
    label: 'Accept (reviewed) / Accepter (relue)' });
  const outside = 'mark_paid is drawn from docs/x.md, outside the briefs folders (briefs) -> an architect adds it: archvow init invoicing --force -a briefs=briefs,docs';
  assert.deepEqual(issueFix('ARCH039', outside, ''), { command: 'init', values: { package: 'invoicing', force: 'true', answer: 'briefs=briefs,docs' },
    label: 'Add this folder (architect) / Ajouter ce dossier (architecte)' });
});

test('the page: the --from picker, "Drawn from", Add this folder, Review; the folders in Overview', () => {
  const page = fs.readFileSync(path.join(ROOT, 'media', 'workspace.js'), 'utf8');
  const host = fs.readFileSync(path.join(ROOT, 'src', 'workspace.ts'), 'utf8');
  assert.match(page, /p\.flags\.includes\('--from'\)\) \{\n\s+control = briefField\(/);
  assert.match(page, /\$\{drawnFrom\(view\)\}\$\{links\(view\)\}/);
  assert.match(page, /\$\{r\.running \? '' : addFolderButton\(r\.raw\)\}/);
  assert.match(page, /\$\{branchWarning\}\$\{briefFolders\(\)\}/);
  // Add this folder opens the init form, prefilled: it does not run init
  assert.match(page, /addFolder: \(e\) => post\(\{ type: 'openCommand', command: 'init', values: \{ package: e\.dataset\.package, force: 'true', answer: e\.dataset\.answer \}/);
  assert.match(page, /case 'brief accept':\n\s+post\(\{ type: 'openBrief', ref: item\.preset\.brief \|\| '' \}\);/);
  assert.match(host, /this\.state\.briefs = readBriefs\(this\.root, spec\);/);
  assert.match(host, /case 'openBrief': \{\n\s+const where = locate\(this\.state\.briefs/);
  // the page's own addFolder() reads the refusal as the host's does
  const source = page.match(/function addFolder\(text\) \{[\s\S]*?\n  \}/);
  assert.ok(source);
  const pageAdd = new Function(`${source[0]}; return addFolder;`)();
  const refused = fs.readFileSync(path.join(CLI, 'briefs', 'refused.txt'), 'utf8');
  assert.deepEqual(pageAdd(refused), addFolder(refused));
});
