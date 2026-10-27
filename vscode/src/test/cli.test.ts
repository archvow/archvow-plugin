// The CLI runner, with a fake archvow: stdout and stderr apart, one command at a time, the time-out.
import * as assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import { test } from 'node:test';
import { ArchvowCli, candidates, commandLine, display, linuxPath, wslArgument, wslParse, wslenv } from '../cli';

function fakeArchvow(): string {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'archvow-fake-'));
  const script = path.join(dir, 'archvow');
  fs.writeFileSync(script, `#!${process.execPath}
const args = process.argv.slice(2);
if (args[0] === '--version') process.stdout.write('archvow 1.16.0\\n');
else if (args[0] === 'sleep') setTimeout(() => {}, 60000);
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

test('archvow in WSL, VS Code on Windows: wsl.exe with Linux paths, no shell (0.11.2)', () => {
  assert.deepEqual(wslParse('\\\\wsl.localhost\\Ubuntu\\home\\marc\\.venv\\bin\\python3'), { distro: 'Ubuntu', path: '/home/marc/.venv/bin/python3' });
  assert.deepEqual(wslParse('\\\\wsl$\\Ubuntu-22.04\\usr\\bin\\python3'), { distro: 'Ubuntu-22.04', path: '/usr/bin/python3' });
  assert.deepEqual(wslParse('wsl://Debian/opt/py/bin/python'), { distro: 'Debian', path: '/opt/py/bin/python' });
  assert.equal(wslParse('C:\\Python312\\python.exe'), null);
  assert.equal(linuxPath('C:\\work\\acoss pipeline2\\'), '/mnt/c/work/acoss pipeline2');
  assert.equal(linuxPath('\\\\wsl.localhost\\Ubuntu\\home\\marc\\p'), '/home/marc/p');
  assert.equal(wslArgument('C:\\tmp\\report.xml'), '/mnt/c/tmp/report.xml');
  assert.equal(wslArgument('Fail: the header is part of the contract'), 'Fail: the header is part of the contract');
  const settings = { executable: 'archvow', python: '\\\\wsl.localhost\\Ubuntu\\home\\marc\\.venv\\bin\\python3', timeoutSeconds: 1 };
  assert.deepEqual(commandLine(settings, ['check', '--json'], '\\\\wsl.localhost\\Ubuntu\\home\\marc\\acoss_pipeline2', true), {
    program: 'wsl.exe',
    args: ['-d', 'Ubuntu', '--cd', '/home/marc/acoss_pipeline2', '--exec', '/home/marc/.venv/bin/python3', '-m', 'archvow', 'check', '--json'] });
  assert.deepEqual(commandLine({ ...settings, python: '/home/marc/.venv/bin/python3' }, ['next'], 'C:\\work\\p', true).args,
    ['--cd', '/mnt/c/work/p', '--exec', '/home/marc/.venv/bin/python3', '-m', 'archvow', 'next'], 'a Linux path: the default distribution');
  assert.deepEqual(commandLine(settings, ['catalog', '--json'], null, true).args.slice(0, 4), ['-d', 'Ubuntu', '--cd', '~']);
  assert.deepEqual(commandLine({ ...settings, python: '/venv/bin/python3' }, ['next'], '/work/p', false),
    { program: '/venv/bin/python3', args: ['-m', 'archvow', 'next'] }, 'Linux / macOS: unchanged');
  assert.equal(wslenv('PATH/l'), 'PATH/l:NO_COLOR:PYTHONIOENCODING:ARCHVOW_TEMPLATES/l');
});

test("the search order: VS Code's interpreter, archvow.python, archvow.executable; the first that answers runs", async () => {
  assert.deepEqual(candidates({ executable: '', python: '/p2', timeoutSeconds: 1, interpreter: '/p1' }).map((c) => c.source), ['ide', 'python', 'executable']);
  assert.deepEqual(candidates({ executable: '', python: '/p2', timeoutSeconds: 1, interpreter: '/p1', useProjectInterpreter: false }).map((c) => c.source), ['python', 'executable']);
  assert.equal(candidates({ executable: '', python: '', timeoutSeconds: 1 })[0].executable, 'archvow');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'archvow-order-'));
  const without = path.join(dir, 'python-without');
  fs.writeFileSync(without, '#!/bin/sh\necho "No module named archvow" >&2\nexit 1\n');
  fs.chmodSync(without, 0o755);
  // the IDE's interpreter has no archvow, archvow.python does not exist: archvow.executable answers
  const cli = new ArchvowCli(os.tmpdir(), () => ({ executable: fakeArchvow(), python: '/nonexistent/python3', timeoutSeconds: 10, interpreter: without }));
  const { data } = await cli.json<{ args: string[] }>(['next', '--json']);
  assert.deepEqual(data.args, ['next', '--json']);
  // none answers: what each one said
  const none = new ArchvowCli(os.tmpdir(), () => ({ executable: '/nonexistent/archvow', python: '', timeoutSeconds: 10, interpreter: without }));
  const result = await none.run(['next']);
  assert.match(result.startError ?? '', /archvow not found\. Tried, in order:\n- VS Code's Python interpreter \(.*python-without\): exit 1: No module named archvow\n- archvow\.executable \(\/nonexistent\/archvow\)/);
});
