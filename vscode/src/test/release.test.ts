// What must hold before a release, without building anything: one version everywhere, the same archvow
// for both plugins, both IDEs following the same rules, and the manifests (package.json, plugin.xml)
// naming only what exists. CI runs it with the other tests (npm test); the GitHub Actions job `vscode`.
import * as assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { test } from 'node:test';
import { Command, DESTRUCTIVE, label, LONG_TEXT, READ_ONLY } from '../grammar';
import { knownVersion } from '../model';
import { requirement } from '../install';

const ROOT = path.resolve(__dirname, '..', '..', '..');
const read = (...parts: string[]): string => fs.readFileSync(path.join(ROOT, ...parts), 'utf8');
/** A Markdown text with its line breaks as spaces: a sentence is found wherever the lines break. */
const prose = (...parts: string[]): string => read(...parts).replace(/\s+/g, ' ');
const exists = (...parts: string[]): boolean => fs.existsSync(path.join(ROOT, ...parts));

const PACKAGE = JSON.parse(read('vscode', 'package.json')) as {
  version: string; icon: string; main: string;
  contributes: {
    commands: { command: string }[];
    menus: Record<string, { command: string }[]>;
    configuration: { properties: Record<string, unknown> };
    languages: { configuration: string; icon: { light: string; dark: string } }[];
    grammars: { path: string }[];
    views: Record<string, { id: string; icon?: string }[]>;
    viewsContainers: Record<string, { icon: string }[]>;
  };
};
const GRADLE = Object.fromEntries(read('intellij', 'gradle.properties').split('\n')
  .filter((l) => /^\w+=/.test(l)).map((l) => l.split('=', 2) as [string, string]));
const VERSION = PACKAGE.version;
const ARCHVOW = knownVersion(read('contract', 'cli', 'version.txt'));
const COMMANDS = (JSON.parse(read('contract', 'cli', 'commands.json')) as { commands: Command[] }).commands;
const NAMES = new Set(COMMANDS.map(label));
const PANEL = read('intellij', 'src', 'main', 'java', 'org', 'archvow', 'idea', 'ArchvowPanel.java');
const EXTENSION_SOURCES = fs.readdirSync(path.join(ROOT, 'vscode', 'src')).filter((f) => f.endsWith('.ts'))
  .map((f) => read('vscode', 'src', f)).join('\n');

/** A `Set.of("a", "b")` constant of a Java class, as a sorted list. */
function javaSet(source: string, name: string): string[] {
  const m = new RegExp(`Set<String> ${name} = Set\\.of\\(([^)]*)\\)`, 's').exec(source);
  assert.ok(m, `${name} not found`);
  return [...m[1].matchAll(/"([^"]*)"/g)].map((x) => x[1]).sort();
}

test('one version: package.json, gradle.properties, the README and both changelogs', () => {
  assert.match(VERSION, /^\d+\.\d+\.\d+$/);
  assert.equal(GRADLE.pluginVersion, VERSION, 'intellij/gradle.properties pluginVersion');
  const readme = prose('README.md');
  assert.ok(readme.includes(`Both plugins have the same version (${VERSION})`), 'README: Versions');
  assert.ok(readme.includes(`dist/archvow-intellij-${VERSION}.zip, dist/archvow-${VERSION}.vsix`), 'README: build line');
  for (const ide of ['vscode', 'intellij']) {
    const first = /^## (\S+)/m.exec(read(ide, 'CHANGELOG.md'));
    assert.equal(first?.[1], VERSION, `${ide}/CHANGELOG.md: its newest entry`);
  }
});

test('one archvow: the contract\'s version is the one both plugins install, name and build on', () => {
  assert.match(ARCHVOW, /^\d+\.\d+$/);
  assert.equal(requirement(ARCHVOW), `archvow==${ARCHVOW}.*`);
  assert.ok(prose('README.md').includes(`run the archvow CLI ${ARCHVOW}`), 'README');
  assert.ok(prose('contract', 'README.md').includes(`Recorded from ${read('contract', 'cli', 'version.txt').trim()}.`),
    'contract/README.md');
  assert.ok(read('intellij', 'gradle.properties').includes(`(${ARCHVOW})`), 'gradle.properties comment');
  for (const ide of ['vscode', 'intellij']) {
    const newest = read(ide, 'CHANGELOG.md').split(/^## /m)[1].replace(/\s+/g, ' ');
    assert.ok(newest.includes(`Built on archvow ${ARCHVOW}`), `${ide}/CHANGELOG.md`);
  }
});

test('both builds package the same files of the contract (what archvow says about itself)', () => {
  const vscode = /for \(const name of \[([^\]]*)\]\)/.exec(read('vscode', 'scripts', 'sync-contract.js'));
  const gradle = /include\(([^)]*)\)\s*into\("archvow"\)/s.exec(read('intellij', 'build.gradle.kts'));
  const names = (s: string | undefined) => [...(s ?? '').matchAll(/["']([^"']+)["']/g)].map((m) => m[1]).sort();
  assert.ok(vscode && gradle);
  assert.deepEqual(names(vscode[1]), names(gradle[1]));
  for (const name of names(vscode[1])) assert.ok(exists('contract', 'cli', name), name);
});

test('the command lists of both IDEs name only archvow commands, and are the same in both', () => {
  for (const name of DESTRUCTIVE) assert.ok(NAMES.has(name), `DESTRUCTIVE: ${name}`);
  const first = new Set(COMMANDS.map((c) => c.path[0]));
  for (const name of READ_ONLY) assert.ok(first.has(name), `READ_ONLY: ${name}`);
  const parameters = new Set(COMMANDS.flatMap((c) => c.parameters.map((p) => p.name)));
  for (const name of LONG_TEXT) assert.ok(parameters.has(name), `LONG_TEXT: no parameter ${name}`);
  assert.deepEqual(javaSet(PANEL, 'DESTRUCTIVE'), [...DESTRUCTIVE].sort(), 'ArchvowPanel.DESTRUCTIVE');
  assert.deepEqual(javaSet(PANEL, 'READ_ONLY'), [...READ_ONLY].sort(), 'ArchvowPanel.READ_ONLY');
  assert.deepEqual(javaSet(PANEL, 'LONG_TEXT'), [...LONG_TEXT].sort(), 'ArchvowPanel.LONG_TEXT');
});

test('a command that asks "are you sure?" in a terminal gets --yes from both IDEs (no terminal there)', () => {
  // archvow refuses a removal without --yes when it has no terminal - which is always the case in an IDE
  const asking = COMMANDS.filter((c) => c.parameters.some((p) => p.flags.includes('--yes'))).map(label).sort();
  assert.deepEqual([...DESTRUCTIVE].sort(), asking);
});

test('a read-only command is one archvow cannot ask to change anything with', () => {
  for (const command of COMMANDS.filter((c) => READ_ONLY.has(c.path[0]))) {
    assert.ok(!command.parameters.some((p) => p.flags.includes('--yes')), label(command));
  }
});

test('package.json: every command declared is registered, every one registered is declared', () => {
  const declared = PACKAGE.contributes.commands.map((c) => c.command).sort();
  const registered = [...read('vscode', 'src', 'extension.ts').matchAll(/registerCommand\('([^']+)'/g)].map((m) => m[1]).sort();
  assert.deepEqual(registered, declared);
  for (const [menu, items] of Object.entries(PACKAGE.contributes.menus)) {
    for (const item of items) assert.ok(declared.includes(item.command), `${menu}: ${item.command}`);
  }
  const views = Object.values(PACKAGE.contributes.views).flat().map((v) => v.id);
  for (const id of views) assert.ok(EXTENSION_SOURCES.includes(id.split('.').pop() ?? id), `view ${id}`);
});

test('package.json: every setting read is declared, and every one declared is read', () => {
  const declared = Object.keys(PACKAGE.contributes.configuration.properties).map((k) => k.replace(/^archvow\./, '')).sort();
  const read = new Set([...EXTENSION_SOURCES.matchAll(/\.(?:get|update)(?:<[^>]*>)?\('(\w+)'/g)].map((m) => m[1]));
  for (const key of declared) assert.ok(read.has(key), `archvow.${key} is declared but never read`);
  const getConfiguration = [...EXTENSION_SOURCES.matchAll(/getConfiguration\('archvow'\)\s*\.(?:get|update)(?:<[^>]*>)?\('(\w+)'/g)];
  for (const m of getConfiguration) assert.ok(declared.includes(m[1]), `archvow.${m[1]} is read but not declared`);
});

test('package.json: every file it names is in the extension folder', () => {
  const c = PACKAGE.contributes;
  const files = [PACKAGE.icon, ...c.languages.flatMap((l) => [l.configuration, l.icon.light, l.icon.dark]),
    ...c.grammars.map((g) => g.path), ...Object.values(c.viewsContainers).flat().map((v) => v.icon),
    ...Object.values(c.views).flat().flatMap((v) => (v.icon ? [v.icon] : []))];
  for (const file of files) assert.ok(exists('vscode', file), file);
  assert.equal(PACKAGE.main, './out/extension.js');
  assert.ok(exists('vscode', 'src', 'extension.ts'));
  const ignore = read('vscode', '.vscodeignore');
  for (const left of ['src/**', 'out/test/**', 'node_modules/**']) assert.ok(ignore.includes(left), `.vscodeignore: ${left}`);
});

test('plugin.xml: every class it names is in the source, every file it names is a resource', () => {
  const xml = read('intellij', 'src', 'main', 'resources', 'META-INF', 'plugin.xml');
  const java = path.join(ROOT, 'intellij', 'src', 'main', 'java');
  const classes = [...xml.matchAll(/(?:implementationClass|implementation|class|factoryClass|instance)="([\w.]+)"/g)].map((m) => m[1]);
  assert.ok(classes.length >= 15, `only ${classes.length} classes found`);
  for (const name of classes) assert.ok(fs.existsSync(path.join(java, ...name.split('.')) + '.java'), name);
  const resources = [...xml.matchAll(/(?:icon|file)="\/?([^"]+\.(?:svg|xml|png))"/g)].map((m) => m[1]);
  for (const file of resources) assert.ok(exists('intellij', 'src', 'main', 'resources', file), file);
  for (const icon of ['pluginIcon.svg', 'pluginIcon_dark.svg']) assert.ok(exists('intellij', 'src', 'main', 'resources', 'META-INF', icon));
  assert.match(xml, /<id>org\.archvow\.intellij<\/id>/);
  assert.match(GRADLE.sinceBuild ?? '', /^\d{3}$/);
});
