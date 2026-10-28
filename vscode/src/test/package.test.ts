// What a team installs: the .vsix and the IntelliJ .zip in ../dist (./build.sh, or the GitHub Actions jobs
// `vscode` and `intellij`), opened as VS Code and IntelliJ open them. As archvow's CI installs its wheel
// in a clean venv before publishing it, these check the packages, not the source tree:
// the version, the files the manifests name, the contract inside, nothing left out, nothing extra.
//
// Skipped when ../dist holds no package (a plain `npm test`); PACKAGES_REQUIRED=1 (the GitHub Actions job
// `packages`) turns the skip into a failure.
import * as assert from 'node:assert/strict';
import { builtinModules } from 'node:module';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { test } from 'node:test';
import { readZip, Zip } from './zip';

const ROOT = path.resolve(__dirname, '..', '..', '..');
const DIST = process.env.ARCHVOW_DIST ? path.resolve(process.env.ARCHVOW_DIST) : path.join(ROOT, 'dist');
const REQUIRED = process.env.PACKAGES_REQUIRED === '1';
const VERSION = (JSON.parse(fs.readFileSync(path.join(ROOT, 'vscode', 'package.json'), 'utf8')) as { version: string }).version;
const CONTRACT_FILES = ['commands.json', 'catalog.json', 'rules.md', 'rules.fr.md', 'version.txt'];
const contract = (name: string): Buffer => fs.readFileSync(path.join(ROOT, 'contract', 'cli', name));
const inDist = (name: string): string | null => (fs.existsSync(path.join(DIST, name)) ? path.join(DIST, name) : null);
const VSIX = inDist(`archvow-${VERSION}.vsix`);
const INTELLIJ = inDist(`archvow-intellij-${VERSION}.zip`);
const text = (zip: Zip, name: string): string => {
  const entry = zip.get(name);
  assert.ok(entry, `${name} is not in the package`);
  return entry().toString('utf8');
};

test('the packages of this version are in dist/ when the job requires them', () => {
  if (!REQUIRED) return;
  assert.ok(VSIX, `${DIST}/archvow-${VERSION}.vsix is missing`);
  assert.ok(INTELLIJ, `${DIST}/archvow-intellij-${VERSION}.zip is missing`);
});

test('vsix: the manifest, the version, the compiled extension and what package.json names', { skip: VSIX ? false : 'no .vsix in dist/' }, () => {
  const zip = readZip(fs.readFileSync(VSIX!));
  const manifest = JSON.parse(text(zip, 'extension/package.json')) as {
    version: string; main: string; icon: string;
    contributes: { languages: { configuration: string }[]; grammars: { path: string }[] };
  };
  assert.equal(manifest.version, VERSION);
  assert.match(text(zip, 'extension.vsixmanifest'), new RegExp(`Version="${VERSION.replace(/\./g, '\\.')}"`));
  const named = [manifest.main, manifest.icon, ...manifest.contributes.languages.map((l) => l.configuration),
    ...manifest.contributes.grammars.map((g) => g.path)];
  for (const file of named) assert.ok(zip.has(`extension/${path.posix.normalize(file)}`), file);
  // every module of the extension is compiled in, the tests are not
  const sources = fs.readdirSync(path.join(ROOT, 'vscode', 'src')).filter((f) => f.endsWith('.ts'));
  for (const source of sources) assert.ok(zip.has(`extension/out/${source.replace(/\.ts$/, '.js')}`), source);
  for (const media of fs.readdirSync(path.join(ROOT, 'vscode', 'media'))) assert.ok(zip.has(`extension/media/${media}`), media);
});

test('vsix: the contract inside is the repository\'s (what the extension knows of archvow)', { skip: VSIX ? false : 'no .vsix in dist/' }, () => {
  const zip = readZip(fs.readFileSync(VSIX!));
  for (const name of CONTRACT_FILES) {
    const entry = zip.get(`extension/resources/archvow/${name}`);
    assert.ok(entry, name);
    assert.ok(entry().equals(contract(name)), `${name} differs from contract/cli/${name}`);
  }
});

test('vsix: nothing the extension does not need (sources, tests, maps, node_modules)', { skip: VSIX ? false : 'no .vsix in dist/' }, () => {
  const names = [...readZip(fs.readFileSync(VSIX!)).keys()];
  const extra = names.filter((n) => /^extension\/(src|out\/test|node_modules|scripts)\//.test(n) || n.endsWith('.map')
    || n.endsWith('tsconfig.json'));
  assert.deepEqual(extra, []);
});

test('vsix: every module the extension requires is in it, or is Node\'s or VS Code\'s', { skip: VSIX ? false : 'no .vsix in dist/' }, () => {
  // packaged with --no-dependencies: a require of an npm package would fail on the user's machine
  const zip = readZip(fs.readFileSync(VSIX!));
  const builtins = new Set([...builtinModules, ...builtinModules.map((m) => `node:${m}`), 'vscode']);
  for (const name of [...zip.keys()].filter((n) => /^extension\/out\/.*\.js$/.test(n))) {
    for (const m of text(zip, name).matchAll(/require\("([^"]+)"\)/g)) {
      const wanted = m[1];
      if (builtins.has(wanted)) continue;
      assert.ok(wanted.startsWith('.'), `${name} requires ${wanted}, which is not packaged`);
      const target = path.posix.join(path.posix.dirname(name), wanted);
      assert.ok(zip.has(target) || zip.has(`${target}.js`) || zip.has(`${target}/index.js`), `${name}: ${wanted} not found`);
    }
  }
});

test('intellij zip: the plugin jar, its plugin.xml with this version, and the classes it names', { skip: INTELLIJ ? false : 'no IntelliJ .zip in dist/' }, () => {
  const zip = readZip(fs.readFileSync(INTELLIJ!));
  const jarName = [...zip.keys()].find((n) => /^[^/]+\/lib\/archvow-intellij-[^/]*\.jar$/.test(n));
  assert.ok(jarName, `no lib/archvow-intellij-*.jar in ${[...zip.keys()].join(', ')}`);
  const jar = readZip(zip.get(jarName)!());
  const xml = text(jar, 'META-INF/plugin.xml');
  assert.match(xml, new RegExp(`<version>${VERSION.replace(/\./g, '\\.')}</version>`));
  const since = /^sinceBuild=(\d+)/m.exec(fs.readFileSync(path.join(ROOT, 'intellij', 'gradle.properties'), 'utf8'))?.[1];
  assert.match(xml, new RegExp(`<idea-version since-build="${since}"`));
  const classes = [...xml.matchAll(/(?:implementationClass|implementation|class|factoryClass|instance)="([\w.]+)"/g)].map((m) => m[1]);
  for (const name of classes) assert.ok(jar.has(`${name.replace(/\./g, '/')}.class`), name);
  for (const icon of ['META-INF/pluginIcon.svg', 'META-INF/pluginIcon_dark.svg']) assert.ok(jar.has(icon), icon);
});

test('intellij zip: the contract inside is the repository\'s, and no test class is packaged', { skip: INTELLIJ ? false : 'no IntelliJ .zip in dist/' }, () => {
  const zip = readZip(fs.readFileSync(INTELLIJ!));
  const jarName = [...zip.keys()].find((n) => /\/lib\/archvow-intellij-[^/]*\.jar$/.test(n))!;
  const jar = readZip(zip.get(jarName)!());
  for (const name of CONTRACT_FILES) {
    const entry = jar.get(`archvow/${name}`);
    assert.ok(entry, name);
    assert.ok(entry().equals(contract(name)), `${name} differs from contract/cli/${name}`);
  }
  const tests = [...jar.keys()].filter((n) => /(Regression|Tests|Smoke|DumpContract)\.class$/.test(n));
  assert.deepEqual(tests, []);
});
