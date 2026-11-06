// Root files in a command preview must not be rendered under a synthetic "./" folder.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../media/workspace.js'), 'utf8');
const css = fs.readFileSync(path.join(__dirname, '../media/workspace.css'), 'utf8');
const begin = source.indexOf('  function filesBlock(rows) {');
const end = source.indexOf('  /** Check diagnostics use the identical Files renderer', begin);
assert.ok(begin >= 0 && end > begin, 'locate the common Files renderer');
const filesBlock = new Function('icon', 't', 'esc', `${source.slice(begin, end)}\nreturn filesBlock;`)(
  (name) => `<svg data-icon="${name}"></svg>`,
  (en) => en,
  (value) => String(value).replace(/&/g, '&amp;').replace(/</g, '&lt;'),
);
const preview = filesBlock([
  { change: 'new', path: '.archvow/spec.vow', note: 'project specification' },
  { change: 'new', path: '.archvow/spec.md', note: 'overview' },
  { change: 'new', path: 'AGENTS.md', note: 'AI rules' },
  { change: 'new', path: 'README.md', note: 'project instructions' },
  { change: 'new', path: 'Makefile', note: 'template commands' },
]);
assert.match(preview, /class="file-dir"[^>]*><svg data-icon="folder"><\/svg><span>\.archvow\/<\/span>/);
assert.equal((preview.match(/class="file-dir"/g) || []).length, 1, 'only the real folder is shown');
assert.doesNotMatch(preview, /<span>\.\/<\/span>/, 'no artificial root folder');
assert.match(preview, /<ul class="file-list root-files"><li class="file-row change-new"><svg data-icon="document"><\/svg><code>AGENTS\.md<\/code>/);
assert.match(preview, /<code>README\.md<\/code>/);
assert.match(preview, /<code>Makefile<\/code>/);
assert.match(css, /ul\.file-list\.root-files\s*\{[^}]*margin-left:\s*0;[^}]*padding-left:\s*0;[^}]*border-left:\s*0;/);
assert.equal(filesBlock([{ change: 'location', path: 'AGENTS.md' }]).includes('root-files'), true,
  'root check locations use the same alignment');
console.log('Root files preview: no ./; root siblings align with folder groups: OK');
