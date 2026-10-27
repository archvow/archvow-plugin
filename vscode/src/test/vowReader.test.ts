// The .vow reader of the extension must give exactly what the IntelliJ plugin's Java reader gives:
// ../contract/vow-reader/expected.json was written by the Java reader (DumpContract) on every sample vow
// and on 400 vows broken on purpose.
import * as assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { test } from 'node:test';
import { VowDocument } from '../vow/document';
import { linksOf } from '../vow/links';
import { tokenize, Scanner } from '../vow/scanner';

const CONTRACT = path.resolve(__dirname, '..', '..', '..', 'contract');

interface Case {
  name: string;
  text?: string;
  tokens: [string, number, number, number][];
  kind: string | null;
  name_in_header: string | null;
  version: string | null;
  problems: [number, number, number, string, boolean][];
  links: [string, string, number, number][];
  exception?: string;
}

const expected: { cases: Case[] } = JSON.parse(
  fs.readFileSync(path.join(CONTRACT, 'vow-reader', 'expected.json'), 'utf8'),
);

function textOf(c: Case): string {
  return c.text ?? fs.readFileSync(path.join(CONTRACT, 'vows', c.name), 'utf8');
}

test('the contract holds samples and broken vows', () => {
  assert.ok(expected.cases.filter((c) => c.text === undefined).length >= 29);
  assert.ok(expected.cases.filter((c) => c.text !== undefined).length >= 400);
});

test('tokens are the Java scanner\'s, on every case', () => {
  for (const c of expected.cases) {
    const tokens = tokenize(textOf(c)).map((t) => [t.kind, t.start, t.end, t.state]);
    assert.deepEqual(tokens, c.tokens, c.name);
  }
});

test('the scanner restarts at any token with that token\'s state', () => {
  for (const c of expected.cases.filter((x) => x.text === undefined)) {
    const text = textOf(c);
    const all = tokenize(text);
    for (let i = 0; i < all.length; i += Math.max(1, Math.floor(all.length / 25))) {
      const scanner = new Scanner();
      scanner.start(text, all[i].start, text.length, all[i].state);
      for (let j = i; j < Math.min(all.length, i + 40); j++) {
        assert.deepEqual(scanner.token(), all[j], `${c.name} restarted at ${i}`);
        scanner.advance();
      }
    }
  }
});

test('header, problems and links are the Java reader\'s, on every case', () => {
  for (const c of expected.cases) {
    const doc = VowDocument.parse(textOf(c));
    assert.equal(doc.kind, c.kind, c.name);
    assert.equal(doc.name, c.name_in_header, c.name);
    assert.equal(doc.version, c.version, c.name);
    const problems = doc.problems.map((p) => [p.line, p.start, p.end, p.message, p.warning]);
    assert.deepEqual(problems, c.problems, c.name);
    const links = doc.lines.flatMap((l) => linksOf(l)).map((l) => [l.kind, l.target, l.start, l.end]);
    assert.deepEqual(links, c.links, c.name);
  }
});

test('the sample vows have no problem', () => {
  for (const c of expected.cases.filter((x) => x.text === undefined)) {
    assert.deepEqual(VowDocument.parse(textOf(c)).problems, [], c.name);
  }
});
