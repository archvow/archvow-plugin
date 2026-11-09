// The real archvow, run the way the extension runs it (ArchvowCli: no shell, in the project folder, JSON
// from stdout), on the contract's three examples. The other tests read what archvow printed when the
// contract was recorded; these check that the archvow installed now still prints it, and that what the
// workspace does with it (the tree, the problems, a change, then a reread) works end to end.
//
// Skipped unless an archvow is given (as the Spark scenario of archvow's tests without Java):
//   ARCHVOW_PYTHON=/path/venv/bin/python npm test     (or ARCHVOW_EXECUTABLE=/path/archvow)
// archvow's examples (contract/archvow/examples, or ARCHVOW_SOURCE=<archvow's source folder>) are run with
// their code, and every output must be exactly the recorded one. The archvow to give:
//   python3 -m venv .venv && .venv/bin/pip install contract/archvow/archvow-*.whl ARCHVOW_LIVE=required (the GitHub Actions job `live`) turns the
// skips into failures.
// Run it from your own terminal: inside an AI agent, archvow stops (ARCH051, ARCH052, ARCH054).
import * as assert from 'node:assert/strict';
import * as crypto from 'node:crypto';
import * as fs from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import { test } from 'node:test';
import { ArchvowCli, CliSettings } from '../cli';
import { Command, DESTRUCTIVE, findCommand, isMutating, READ_ONLY } from '../grammar';
import { buildTree, CheckResult, componentView, contractWarning, knownVersion, NextRow, problemsOf, Spec } from '../model';
import { derive } from '../projectState';

const CONTRACT = path.resolve(__dirname, '..', '..', '..', 'contract');
const EXAMPLES = ['python-app', 'fastapi-service', 'pyspark-pipeline'];
const PYTHON = process.env.ARCHVOW_PYTHON ?? '';
const EXECUTABLE = process.env.ARCHVOW_EXECUTABLE ?? '';
// archvow's examples and rules: those of the archvow the plugins are tested with (contract/archvow/), or another
const SOURCE = process.env.ARCHVOW_SOURCE || path.join(CONTRACT, 'archvow');
const REQUIRED = process.env.ARCHVOW_LIVE === 'required';
const skip = PYTHON || EXECUTABLE ? false : 'no archvow given (ARCHVOW_PYTHON or ARCHVOW_EXECUTABLE)';
const skipExact = skip || (SOURCE ? false : "no archvow source folder (ARCHVOW_SOURCE): the examples' code is not here");
const recorded = <T>(...parts: string[]): T => JSON.parse(fs.readFileSync(path.join(CONTRACT, 'cli', ...parts), 'utf8')) as T;
const COMMANDS = recorded<{ commands: Command[] }>('commands.json').commands;

const settings = (): CliSettings => ({ executable: EXECUTABLE || 'archvow', python: PYTHON, timeoutSeconds: 120, useProjectInterpreter: false });

/** An example of archvow's source, code included, copied to a new folder (as regenerate.py does). */
function example(name: string): string {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), `archvow-exact-${name}-`));
  fs.cpSync(path.join(SOURCE, 'examples', name), path.join(root, name), { recursive: true });
  return path.join(root, name);
}

/** A project made of an example's vows only (the code is not in the contract), in a new folder. */
function project(example: string): string {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), `archvow-live-${example}-`));
  fs.cpSync(path.join(CONTRACT, 'vows', example, '.archvow'), path.join(root, '.archvow'), { recursive: true });
  return root;
}

/** The vows of a project, as one fingerprint: what a read-only command must leave as it is. */
function vows(root: string): string {
  const hash = crypto.createHash('sha256');
  const walk = (dir: string): void => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name))) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (entry.name.endsWith('.vow')) hash.update(full).update(fs.readFileSync(full));
    }
  };
  walk(path.join(root, '.archvow'));
  return hash.digest('hex');
}

test('an archvow and its source are given when the job requires them', () => {
  if (!REQUIRED) return;
  assert.ok(PYTHON || EXECUTABLE, 'ARCHVOW_LIVE=required, but neither ARCHVOW_PYTHON nor ARCHVOW_EXECUTABLE is set');
  assert.ok(SOURCE && fs.existsSync(path.join(SOURCE, 'examples')), `ARCHVOW_LIVE=required: ARCHVOW_SOURCE (${SOURCE}) has no examples/`);
});

test('live: the installed archvow speaks the plugin\'s contract and is the one it is tested with (no warning)', { skip }, async () => {
  const cli = new ArchvowCli(os.tmpdir(), settings, () => undefined, {}, false);
  const { data } = await cli.json<{ archvow: string; contract: string }>(['version', '--json']);
  const tested = knownVersion(fs.readFileSync(path.join(CONTRACT, 'cli', 'version.txt'), 'utf8'));
  const wanted = fs.readFileSync(path.join(CONTRACT, 'cli', 'contract.txt'), 'utf8').trim();
  assert.equal(data.archvow, tested);
  assert.equal(data.contract, wanted);
  assert.equal(contractWarning({ version: data.archvow, contract: data.contract }, wanted, tested), null);
});

test('live: catalog --json outside a project is the recorded catalog (the init wizard\'s questions)', { skip }, async () => {
  const cli = new ArchvowCli(fs.mkdtempSync(path.join(os.tmpdir(), 'archvow-live-none-')), settings, () => undefined, {}, false);
  const { data } = await cli.json<{ templates: { name: string; source?: string }[] }>(['catalog', '--json']);
  for (const t of data.templates) {  // where archvow is installed here, as regenerate.py records it
    if (t.source && /[\\/]archvow[\\/]packs[\\/]/.test(t.source)) t.source = `<archvow>/packs/${path.basename(t.source)}`;
  }
  assert.deepEqual(data, recorded('catalog.json'));
});

for (const name of EXAMPLES) {
  test(`live: ${name}, with its code: show, show <component>, next and check are exactly the contract`, { skip: skipExact }, async () => {
    const cli = new ArchvowCli(example(name), settings);
    const { data: spec } = await cli.json<Spec>(['show', '--json']);
    assert.deepEqual(spec, recorded(name, 'show.json'));
    for (const id of spec.containers.flatMap((c) => c.components.map((x) => x.id))) {
      const { data: view } = await cli.json<object>(['show', id, '--json']);
      assert.deepEqual(view, recorded(name, 'show', `${id}.json`), id);
    }
    assert.deepEqual((await cli.json<NextRow[]>(['next', '--json'])).data, recorded(name, 'next.json'));
    const { data: check, result } = await cli.json<CheckResult>(['check', '--json']);
    assert.deepEqual(check, recorded(name, 'check.json'));
    assert.doesNotMatch(result.stderr, /Traceback/);
  });
}

for (const example of EXAMPLES) {
  test(`live: ${example}: show, show <component>, next and check, as the workspace reads them`, { skip }, async () => {
    const root = project(example);
    const cli = new ArchvowCli(root, settings);
    // show: the spec, exactly as recorded (it depends on the vows only)
    const { data: spec } = await cli.json<Spec>(['show', '--json']);
    assert.deepEqual(spec, recorded(example, 'show.json'));
    const ids = spec.containers.flatMap((c) => c.components.map((x) => x.id));
    for (const id of ids) {  // the same fields; their values may depend on the code, which is not here
      const { data: view } = await cli.json<Record<string, unknown>>(['show', id, '--json']);
      assert.deepEqual(Object.keys(view).sort(), Object.keys(recorded<object>(example, 'show', `${id}.json`)).sort(), id);
      assert.ok(derive(view as never), id);
    }
    // next and check: the code is not here, so the values differ from the contract - the shape does not
    const { data: next } = await cli.json<NextRow[]>(['next', '--json']);
    const { data: check, result } = await cli.json<CheckResult>(['check', '--json']);
    assert.ok([0, 1].includes(result.exitCode), `check exit ${result.exitCode}: ${result.stderr}`);
    assert.doesNotMatch(result.stderr, /Traceback/);
    assert.deepEqual(Object.keys(check).sort(), Object.keys(recorded<object>(example, 'check.json')).sort());
    // every row has what the workspace needs, and no field the recorded rows never had
    const known = new Set(EXAMPLES.flatMap((e) => recorded<NextRow[]>(e, 'next.json')).flatMap((r) => Object.keys(r)));
    for (const row of next) {
      for (const key of ['component', 'type', 'readiness']) assert.ok(key in row, `a next row without ${key}`);
      for (const key of Object.keys(row)) assert.ok(known.has(key), `next: a field the contract does not have: ${key}`);
      assert.ok(ids.includes(row.component), `next names ${row.component}, not in the spec`);
    }
    for (const v of check.violations) {
      for (const key of ['code', 'severity', 'message', 'file', 'line']) assert.ok(key in v, `violation without ${key}`);
    }
    // what the workspace builds from them
    const tree = JSON.stringify(buildTree(spec, check, next));
    for (const id of ids) assert.ok(tree.includes(`"id":"${id}"`), `${id} not in the tree`);
    for (const p of problemsOf(check)) assert.ok(p.line >= 0 && !path.isAbsolute(p.file), `${p.file}:${p.line}`);
  });

  test(`live: ${example}: the read-only commands change no vow`, { skip }, async () => {
    const root = project(example);
    const cli = new ArchvowCli(root, settings);
    const before = vows(root);
    for (const args of [['show', '--json'], ['next', '--json'], ['check', '--json'], ['audit', '--json'], ['doctor', '--json']]) {
      const command = findCommand(COMMANDS, args[0]);
      assert.ok(command && READ_ONLY.has(args[0]) && !isMutating(command, args), args[0]);
      const result = await cli.run(args);
      assert.equal(result.startError, undefined);
      assert.doesNotMatch(result.stderr, /Traceback/, args.join(' '));
      assert.equal(vows(root), before, `archvow ${args.join(' ')} changed a vow`);
    }
  });
}

test('live: a change goes through the CLI, and the reread shows it (question add, then question remove --yes)', { skip }, async () => {
  const root = project('python-app');
  const cli = new ArchvowCli(root, settings);
  const spec = recorded<Spec>('python-app', 'show.json');
  const component = spec.containers[0].components[0].id;
  const text = 'Which encoding do the input files use?';
  const add = findCommand(COMMANDS, 'question add');
  assert.ok(add && isMutating(add, []));
  const before = vows(root);
  const added = await cli.run(['question', 'add', component, text]);
  assert.equal(added.exitCode, 0, added.stderr);
  assert.notEqual(vows(root), before, 'question add changed no vow');
  const { data: view } = await cli.json<Record<string, unknown>>(['show', component, '--json']);
  assert.ok(JSON.stringify(view).includes(text), 'the question is not in show <component> --json');
  // a removal: the IDE has no terminal, so archvow refuses it without --yes (DESTRUCTIVE adds it)
  const number = /question (\d+)/i.exec(added.stdout)?.[1];
  assert.ok(number, `question add did not say the question's number: ${added.stdout}`);
  const refused = await cli.run(['question', 'remove', component, number]);
  assert.notEqual(refused.exitCode, 0, 'question remove without --yes ran without a terminal');
  assert.match(refused.stderr + refused.stdout, /--yes/);
  assert.ok(DESTRUCTIVE.has('question remove'), 'the IDEs must add --yes to question remove');
  const removed = await cli.run(['question', 'remove', component, number, '--yes']);
  assert.equal(removed.exitCode, 0, removed.stderr);
  assert.equal(vows(root), before, 'question remove did not bring the vows back');
});

test('live: an archvow error is a sentence, never a traceback, and the JSON reader says it', { skip }, async () => {
  const cli = new ArchvowCli(project('python-app'), settings);
  const result = await cli.run(['show', 'no_such_component', '--json']);
  assert.notEqual(result.exitCode, 0);
  assert.doesNotMatch(result.stderr + result.stdout, /Traceback/);
  // a name that is not a component is searched: its result (found nowhere, close names) is never a component's view
  const { data } = await cli.json<Record<string, unknown>>(['show', 'no_such_component', '--json']);
  assert.equal(data.total, 0);
  assert.equal(componentView(data, 'no_such_component'), null);
  assert.equal(componentView((await cli.json<Record<string, unknown>>(['show', 'mark_paid', '--json'])).data, 'mark_paid')?.component, 'mark_paid');
  await assert.rejects(cli.json(['show', '--no-such-option']), /failed \(exit 2\)/);
});
