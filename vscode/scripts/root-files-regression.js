// The Files preview is a tree (0.24.1): each folder once, its children under it with ├── └── │ guides, the folders a
// path passes through drawn too, root files beside .archvow/ and never under a synthetic "./".
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../media/workspace.js'), 'utf8');
const css = fs.readFileSync(path.join(__dirname, '../media/workspace.css'), 'utf8');
const begin = source.indexOf('  function fileTree(rows, compact) {');
const end = source.indexOf('  /** Check diagnostics use the identical Files renderer', begin);
assert.ok(begin >= 0 && end > begin, 'locate the common Files renderer');
const { filesBlock, fileTreeLines } = new Function('icon', 't', 'esc', `${source.slice(begin, end)}\nreturn { filesBlock, fileTreeLines };`)(
  (name) => `<svg data-icon="${name}"></svg>`,
  (en) => en,
  (value) => String(value).replace(/&/g, '&amp;').replace(/</g, '&lt;'),
);
const text = (rows, compact = false) => fileTreeLines(rows, compact).map((l) => l.guide + l.name).join('\n');

// init: the tree of image 2 - .archvow/ once, features/ › shared/ under it, the root files beside it
const init = [
  { change: 'new', path: '.archvow/project.vow', note: 'project specification' },
  { change: 'new', path: '.archvow/features/shared/shared.vow', note: 'what every feature uses' },
  { change: 'new', path: '.archvow/features/shared/components/', note: "the template's own components" },
  { change: 'new', path: '.archvow/README.md', note: 'architecture guide' },
  { change: 'new', path: 'AGENTS.md', note: 'AI rules' },
  { change: 'new', path: 'README.md', note: 'project instructions' },
];
assert.equal(text(init), [
  '.archvow/',
  '├── project.vow',
  '├── features/',
  '│   └── shared/',
  '│       ├── shared.vow',
  '│       └── components/',
  '└── README.md',
  'AGENTS.md',
  'README.md'].join('\n'));
const preview = filesBlock(init);
assert.equal((preview.match(/<span>\.archvow\/<\/span>/g) || []).length, 1, '.archvow/ shown once, even when its files are not together');
assert.doesNotMatch(preview, /\.\/</, 'no artificial root folder');
assert.match(preview, /<ul class="file-tree"><li><div class="file-row change-folder file-dir"><svg data-icon="folder"><\/svg><span>\.archvow\/<\/span><\/div><ul><li>/,
  '.archvow/ is a root item, its children nested under it');
assert.match(preview, /<\/ul><\/li><li><div class="file-row change-new"><svg data-icon="document"><\/svg><code>AGENTS\.md<\/code>/, 'root files beside .archvow/');
assert.match(preview, /<code>project\.vow<\/code><span class="badge new">new<\/span><\/div><div class="file-note muted small">project specification<\/div>/);
assert.match(preview, /<span>features\/<\/span><\/div><ul><li><div class="file-row change-folder file-dir"><svg data-icon="folder"><\/svg><span>shared\/<\/span>/,
  'a folder only passed through is drawn, without a badge');
assert.match(css, /ul\.file-tree ul > li::before \{[^}]*border-left:[^}]*border-bottom:/, 'the connectors are drawn by CSS');
assert.match(css, /ul\.file-tree ul > li:not\(:last-child\)::after \{[^}]*bottom: 0;/, 'the vertical line runs on beside a long note');

// a component's vow in its feature, and a move: every level drawn
assert.equal(text([
  { change: 'removed', path: '.archvow/features/vat_report/components/compute_vat.vow' },
  { change: 'new', path: '.archvow/features/refunds/components/compute_vat.vow' }]), [
  '.archvow/',
  '└── features/',
  '    ├── vat_report/',
  '    │   └── components/',
  '    │       └── compute_vat.vow',
  '    └── refunds/',
  '        └── components/',
  '            └── compute_vat.vow'].join('\n'));
// an absolute folder (pack rule --pack /srv/packs/acme)
assert.equal(text([{ change: 'new', path: '/srv/packs/acme/rules/' }]), ['/srv/', '└── packs/', '    └── acme/', '        └── rules/'].join('\n'));
// a check's location: the folders that only lead to the file are one line
assert.equal(text([{ change: 'location', path: 'src/sales/p2_io/orders_reader.py' }], true), ['src/sales/p2_io/', '└── orders_reader.py'].join('\n'));
assert.equal(text([{ change: 'location', path: 'AGENTS.md' }], true), 'AGENTS.md');
assert.match(filesBlock([{ change: 'location', path: 'AGENTS.md', line: 3 }]), /<code>AGENTS\.md<\/code><span class="muted small">line 3<\/span>/);
console.log('Files preview: one tree, every folder once, root files beside .archvow/, compact check locations: OK');
