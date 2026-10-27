// The CLI runner, with a fake archvow: stdout and stderr apart, one command at a time, the time-out.
import * as assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import { test } from 'node:test';
import { ArchvowCli, commandLine, display } from '../cli';

function fakeArchvow(): string {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'archvow-fake-'));
  const script = path.join(dir, 'archvow');
  fs.writeFileSync(script, `#!${process.execPath}
const args = process.argv.slice(2);
if (args[0] === 'sleep') setTimeout(() => {}, 60000);
else if (args[0] === 'stdin') process.stdin.on('data', (d) => process.stdout.write(JSON.stringify({ got: String(d) })));
else {
  process.stderr.write('a warning on stderr\\n');
  process.stdout.write(JSON.stringify({ args, cwd: process.cwd(), noColor: process.env.NO_COLOR }));
  process.exitCode = args[0] === 'check' ? 1 : 0;
}
`);
  fs.chmodSync(script, 0o755);
  return script;
}

const settings = (executable: string, timeoutSeconds = 30) => () => ({ executable, python: '', timeoutSeconds });

test('the command line: archvow, or <python> -m archvow', () => {
  assert.deepEqual(commandLine({ executable: 'archvow', python: '', timeoutSeconds: 1 }, ['next']), { program: 'archvow', args: ['next'] });
  assert.deepEqual(commandLine({ executable: 'archvow', python: ' /venv/bin/python3 ', timeoutSeconds: 1 }, ['next']),
    { program: '/venv/bin/python3', args: ['-m', 'archvow', 'next'] });
  assert.equal(display(['decision', 'make', 'c', '1', 'Keep the rows']), 'archvow decision make c 1 "Keep the rows"');
});

test('JSON is read from stdout only: a warning on stderr does not break it; exit 1 still gives the JSON', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'archvow-root-'));
  const cli = new ArchvowCli(root, settings(fakeArchvow()));
  const { data, result } = await cli.json<{ args: string[]; cwd: string; noColor: string }>(['check', '--json']);
  assert.deepEqual(data.args, ['check', '--json']);
  assert.equal(fs.realpathSync(data.cwd), fs.realpathSync(root));
  assert.equal(data.noColor, '1');
  assert.equal(result.exitCode, 1);
  assert.match(result.stderr, /warning/);
});

test('an unsaved buffer goes on stdin', async () => {
  const cli = new ArchvowCli(os.tmpdir(), settings(fakeArchvow()));
  const { data } = await cli.json<{ got: string }>(['stdin'], 'def f(): pass\n');
  assert.equal(data.got, 'def f(): pass\n');
});

test('commands run one at a time, in order', async () => {
  const cli = new ArchvowCli(os.tmpdir(), settings(fakeArchvow()));
  const order: string[] = [];
  await Promise.all(['a', 'b', 'c'].map((x) => cli.run([x]).then(() => order.push(x))));
  assert.deepEqual(order, ['a', 'b', 'c']);
});

test('a command is stopped after the time-out, and the next one still runs', async () => {
  const cli = new ArchvowCli(os.tmpdir(), settings(fakeArchvow(), 1));
  const slow = await cli.run(['sleep']);
  assert.equal(slow.timedOut, true);
  assert.equal((await cli.run(['next'])).exitCode, 0);
});

test('archvow not installed: a sentence that says what to set', async () => {
  const cli = new ArchvowCli(os.tmpdir(), settings('/nowhere/archvow'));
  const result = await cli.run(['next']);
  assert.match(result.startError ?? '', /archvow\.executable/);
  await assert.rejects(cli.json(['next']), /archvow\.python/);
});
