// The Pythons for "Choose the Python that runs archvow" (0.26.1), the folder to open after init, and one language
// at a time in every text (Settings said everything twice, "English / Français").
import * as assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { test } from 'node:test';
import { folderToOpen } from '../initTarget';
import { archvowText, ordered, parse, places, runsText } from '../pythons';
import { candidates } from '../cli';

test('pythons: where, in order, each once; read back; the ones with archvow first', () => {
  const there = new Set(['/usr/local/bin/python3', '/usr/local/bin/python3.12', '/usr/bin/python3', '/work/p/.venv/bin/python',
    '/Users/me/.pyenv/versions/3.11.9/bin/python', '/opt/homebrew/bin/python3.12']);
  const same: Record<string, string> = { '/usr/local/bin/python3.12': '/usr/local/bin/python3' };
  const list = places('/usr/local/bin/python3.12', '/work/p', ['/usr/bin/python3'], '/Users/me', false, '/usr/bin:/opt/homebrew/bin',
    (d) => (d === '/Users/me/.pyenv/versions' ? ['3.11.9'] : []), (p) => there.has(p), (p) => same[p] ?? p);
  assert.deepEqual(list.map(([p, w]) => `${p} ${w}`), ['/usr/local/bin/python3.12 project', '/work/p/.venv/bin/python venv',
    '/usr/bin/python3 ide', '/opt/homebrew/bin/python3.12 system', '/Users/me/.pyenv/versions/3.11.9/bin/python system']);
  const withIt = parse('/usr/local/bin/python3.12', 'project', '3.12.8|2.6.2\n');
  const old = parse('/usr/bin/python3', 'ide', '3.9.6|');
  assert.equal(withIt?.archvow, '2.6.2');
  assert.equal(parse('/x', 'path', 'Python was not found'), null);
  assert.deepEqual(ordered([old!, withIt!]).map((f) => f.path), ['/usr/local/bin/python3.12', '/usr/bin/python3']);
  assert.equal(archvowText(old!, false), 'too old for archvow (3.11+)');
  assert.equal(archvowText(withIt!, true), 'archvow 2.6.2');
  assert.equal(runsText(false, '/usr/local/bin/python3.12', 'ide', '2.6.2'),
    "archvow 2.6.2 with /usr/local/bin/python3.12 — the project's Python interpreter");
});

test('pythons: a Python chosen for archvow comes first, before the project interpreter', () => {
  assert.deepEqual(candidates({ executable: '', python: '/chosen', timeoutSeconds: 1, interpreter: '/ide' }).map((c) => c.source),
    ['python', 'ide', 'executable']);
  assert.deepEqual(candidates({ executable: '', python: '/same', timeoutSeconds: 1, interpreter: '/same' }).map((c) => c.source),
    ['python', 'executable']);
});

test('init: another folder opens as the project; the workspace\'s own does not', () => {
  assert.equal(folderToOpen(['init', 'sales', '--dir', '/Users/me/dev/sales', '--no-input'], '/Users/me/work', path.posix), '/Users/me/dev/sales');
  assert.equal(folderToOpen(['init', 'sales', '--dir', 'sales'], '/Users/me/work', path.posix), '/Users/me/work/sales');
  assert.equal(folderToOpen(['init', 'sales', '--dir', '/Users/me/work/'], '/Users/me/work', path.posix), null);
  assert.equal(folderToOpen(['init', 'sales'], '/Users/me/work', path.posix), null);
  assert.equal(folderToOpen(['check', '--dir', '/x'], '/Users/me/work', path.posix), null);
});

test('one language at a time: no text puts English and French side by side', () => {
  const french = /[éèêàçù]|\b(le|la|les|des|du|une|dans|avec|pour|Choisis|Dossier|Langue|Délai)\b/;
  const slashed = /(['"`])([^'"`\n]*?) \/ ([^'"`\n]*?)\1/g;
  const root = path.join(__dirname, '..', '..');
  const files = [...fs.readdirSync(path.join(root, 'src')).filter((f) => f.endsWith('.ts')).map((f) => path.join(root, 'src', f)),
    ...fs.readdirSync(path.join(root, 'media')).filter((f) => f.endsWith('.js')).map((f) => path.join(root, 'media', f))];
  for (const file of files) {
    const text = fs.readFileSync(file, 'utf8');
    for (const m of text.matchAll(slashed)) {
      assert.equal(french.test(m[2]), french.test(m[3]), `${path.basename(file)}: two languages side by side: ${m[0]}`);
    }
  }
});
