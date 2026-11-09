// The session log: actions in full, a refresh in one line, failures with archvow's message, slow calls marked.
import * as assert from 'node:assert/strict';
import { test } from 'node:test';
import { CliResult } from '../cli';
import { masked, resultLine, RunLog } from '../runlog';

function sink() {
  const lines: string[] = [];
  return {
    lines,
    info: (l: string) => lines.push(`info ${l}`),
    debug: (l: string) => lines.push(`debug ${l}`),
    warn: (l: string) => lines.push(`warn ${l}`),
    error: (l: string) => lines.push(`error ${l}`),
  };
}

const call = (args: string[], over: Partial<CliResult> = {}): CliResult =>
  ({ args, exitCode: 0, stdout: '', stderr: '', timedOut: false, millis: 300, ...over });

test('an action: its whole command, its exit, its time and what archvow answered', () => {
  const s = sink();
  new RunLog(s).record(call(['decision', 'make', 'logging_setup', '1', 'json lines'],
    { stdout: 'some detail\n✔ Decision 1 of logging_setup made\n' }), true);
  assert.deepEqual(s.lines, ['info ▶ archvow decision make logging_setup 1 "json lines"  ·  exit 0  ·  300 ms  -  ✔ Decision 1 of logging_setup made']);
});

test('a refresh: each read at debug level, one line for all of them', () => {
  const s = sink();
  let now = 0;
  const log = new RunLog(s, () => new Date(now));
  log.refreshStarted();
  for (const args of [['show', '--json'], ['check', '--json'], ['next', '--json']]) log.record(call(args), false);
  now = 2400;
  log.refreshEnded();
  assert.equal(s.lines.filter((l) => l.startsWith('debug archvow ')).length, 3);
  assert.equal(s.lines[s.lines.length - 1], 'info Refresh  ·  3 reads  ·  2.4 s');
});

test('check finding errors is an answer; a failing call is an error with what archvow said', () => {
  const s = sink();
  const log = new RunLog(s);
  log.record(call(['check', '--json'], { exitCode: 1, stdout: '{"violations": []}' }), false);
  log.record(call(['show', 'nope', '--json'], { exitCode: 2, stderr: 'Traceback...\nSpecError: unknown component nope\n' }), false);
  log.record(call(['next'], { startError: 'archvow not found. Tried, in order:\n- ...' }), true);
  assert.equal(s.lines[0], 'debug archvow check --json  ·  exit 1  ·  300 ms');
  assert.equal(s.lines[1], 'error ✖ archvow show nope --json  ·  exit 2  ·  300 ms  -  Traceback... | SpecError: unknown component nope');
  assert.equal(s.lines[2], 'error ✖ archvow next  ·  did not start  ·  300 ms  -  archvow not found. Tried, in order:');
});

test('a call over a second is marked slow', () => {
  const s = sink();
  new RunLog(s).record(call(['check'], { millis: 4200 }), true);
  assert.match(s.lines[0], /exit 0  ·  4\.2 s  ·  slow/);
});

test('the support text: the session, the lines with their time, answers hidden on request', () => {
  const log = new RunLog(sink(), () => new Date(2026, 10, 8, 22, 46, 49));
  log.header({ plugin: '0.19.0', archvow: '1.22.0', python: '', project: '/work/shop', ide: 'VS Code 1.95.0' });
  log.record(call(['decision', 'make', 'settings', '2', 'the secret is hunter2']), true);
  const hidden = log.supportText(true);
  assert.match(hidden, /^Session: plugin 0\.19\.0  ·  archvow 1\.22\.0  ·  project \/work\/shop  ·  ide VS Code 1\.95\.0\n/);
  assert.match(hidden, /22:46:49 ▶ archvow decision make settings 2 "<text>"/);
  assert.doesNotMatch(hidden, /hunter2/);
  assert.match(log.supportText(false), /hunter2/);
});

test('masked keeps the command and hides a person\'s own words', () => {
  assert.deepEqual(masked(['hazard', 'dismiss', 'tool', 'leak', 'because']), ['hazard', 'dismiss', 'tool', 'leak', '<text>']);
  assert.deepEqual(masked(['component', 'add', 'node', 'x', '-d', 'what it does']), ['component', 'add', 'node', 'x', '-d', '<text>']);
  assert.deepEqual(masked(['next']), ['next']);
});

test('resultLine: the last text line, never JSON', () => {
  assert.equal(resultLine(call(['x'], { stdout: '{"a": 1}' })), '');
  assert.equal(resultLine(call(['x'], { stdout: 'a\n\nb  \n' })), 'b');
});
