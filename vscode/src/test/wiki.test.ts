// The wiki (../wiki, French at the root, English in wiki/en) follows the code, as archvow's does
// (test_the_wiki_follows_the_code): a renamed class, file or function left in a page fails npm test.
import * as assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { test } from 'node:test';

const ROOT = path.resolve(__dirname, '..', '..', '..');
const WIKI = path.join(ROOT, 'wiki');
const CHECKED_FOLDERS = ['intellij/', 'vscode/', 'contract/', 'docs/', 'wiki/'];
// What a build writes: named by the pages, absent from a fresh clone.
const BUILT = ['intellij/build', 'vscode/out', 'vscode/node_modules'];

const pages = (dir: string): string[] =>
  fs.readdirSync(dir).filter((f) => f.endsWith('.md') && !f.startsWith('_')).sort();

function walk(dir: string, keep: (f: string) => boolean, out: string[] = []): string[] {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name === 'out' || entry.name === 'build') continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, keep, out);
    else if (keep(entry.name)) out.push(full);
  }
  return out;
}

const JAVA = new Map(walk(path.join(ROOT, 'intellij', 'src'), (f) => f.endsWith('.java'))
  .map((f) => [path.basename(f, '.java'), fs.readFileSync(f, 'utf8')] as const));
const SCRIPT = walk(path.join(ROOT, 'vscode', 'src'), (f) => f.endsWith('.ts'))
  .concat(walk(path.join(ROOT, 'vscode', 'media'), (f) => f.endsWith('.js')))
  .filter((f) => !f.endsWith('wiki.test.ts'))
  .map((f) => fs.readFileSync(f, 'utf8'))
  .join('\n');
const ALL_CODE = [...JAVA.values()].join('\n') + '\n' + SCRIPT;

const ALL_PAGES = [
  ...pages(WIKI).map((p) => ({ name: p, text: fs.readFileSync(path.join(WIKI, p), 'utf8') })),
  ...pages(path.join(WIKI, 'en')).map((p) => ({ name: 'en/' + p, text: fs.readFileSync(path.join(WIKI, 'en', p), 'utf8') })),
  { name: '_sidebar.md', text: fs.readFileSync(path.join(WIKI, '_sidebar.md'), 'utf8') },
];

/** Inline code spans, without the fenced blocks (diagrams, shell examples). */
function codeSpans(text: string): string[] {
  const prose = text.replace(/```[\s\S]*?```/g, '');
  return [...prose.matchAll(/`([^`\n]+)`/g)].map((m) => m[1].trim());
}

test('every French page has its English twin, and the reverse', () => {
  assert.deepEqual(pages(path.join(WIKI, 'en')), pages(WIKI));
  assert.ok(pages(WIKI).length >= 18, 'home and 17 pages');
});

test('every link of the wiki leads to a page', () => {
  const dead: string[] = [];
  for (const page of ALL_PAGES) {
    for (const m of page.text.matchAll(/\]\(\/([^)#\s]+)(#[^)]*)?\)/g)) {
      if (!fs.existsSync(path.join(WIKI, m[1] + '.md'))) dead.push(`${page.name}: /${m[1]}`);
    }
  }
  assert.deepEqual(dead, []);
});

test('every file of the repository a page names exists', () => {
  const missing: string[] = [];
  for (const page of ALL_PAGES) {
    for (const span of codeSpans(page.text)) {
      const file = span.split(/\s/)[0];
      if (!CHECKED_FOLDERS.some((f) => file.startsWith(f)) || BUILT.some((b) => file.startsWith(b))) continue;
      if (/[<>*{}…]|\.\.\./.test(file)) continue;
      if (!fs.existsSync(path.join(ROOT, file))) missing.push(`${page.name}: ${file}`);
    }
  }
  assert.deepEqual(missing, []);
});

test('every Archvow* and Vow* class a page names exists, with the member it names', () => {
  const missing: string[] = [];
  for (const page of ALL_PAGES) {
    for (const span of codeSpans(page.text)) {
      const m = /^((?:Archvow|Vow)[A-Z]\w*)(?:\.(\w+))?/.exec(span);
      if (!m || /\.(xml|svg|png|json|md)$/.test(span)) continue;
      const [, name, member] = m;
      const java = JAVA.get(name);
      const inScript = new RegExp(`\\b(class|interface|const|function)\\s+${name}\\b`).test(SCRIPT);
      if (!java && !inScript) missing.push(`${page.name}: ${name}`);
      else if (member && !new RegExp(`\\b${member}\\b`).test(java ?? SCRIPT)) missing.push(`${page.name}: ${name}.${member}`);
    }
  }
  assert.deepEqual(missing, []);
});

test('every function a page names, name(), is in the code of a plugin', () => {
  const missing: string[] = [];
  for (const page of ALL_PAGES) {
    for (const span of codeSpans(page.text)) {
      const m = /^(?:[\w]+\.)*(\w+)\(\)$/.exec(span);
      if (m && !new RegExp(`\\b${m[1]}\\s*[(=:]`).test(ALL_CODE)) missing.push(`${page.name}: ${span}`);
    }
  }
  assert.deepEqual(missing, []);
});
