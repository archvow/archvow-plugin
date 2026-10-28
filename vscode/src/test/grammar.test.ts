// The TextMate grammar (colours before the extension starts, and in Markdown fences) gives each keyword
// the scope of its depth, like the semantic tokens: checked on every sample vow.
import * as assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { test } from 'node:test';
import { itemId, prefill } from '../grammar';
import * as oniguruma from 'vscode-oniguruma';
import * as textmate from 'vscode-textmate';
import { tokenize, TokenKind } from '../vow/scanner';

const ROOT = path.resolve(__dirname, '..', '..');
const VOWS = path.resolve(ROOT, '..', 'contract', 'vows');
const DEPTH_SCOPE: Partial<Record<TokenKind, string>> = {
  [TokenKind.KEY_0]: 'keyword.control.depth0.vow',
  [TokenKind.KEY_1]: 'entity.name.tag.depth1.vow',
  [TokenKind.KEY_2]: 'support.type.property-name.depth2.vow',
  [TokenKind.KEY_3]: 'entity.other.attribute-name.depth3.vow',
};

async function registry(): Promise<textmate.Registry> {
  const wasm = fs.readFileSync(require.resolve('vscode-oniguruma/release/onig.wasm'));
  await oniguruma.loadWASM(wasm.buffer.slice(wasm.byteOffset, wasm.byteOffset + wasm.byteLength));
  return new textmate.Registry({
    onigLib: Promise.resolve({ createOnigScanner: (s) => new oniguruma.OnigScanner(s), createOnigString: (s) => new oniguruma.OnigString(s) }),
    loadGrammar: async (scope) => scope === 'source.vow'
      ? textmate.parseRawGrammar(fs.readFileSync(path.join(ROOT, 'syntaxes', 'vow.tmLanguage.json'), 'utf8'), 'vow.tmLanguage.json')
      : null,
  });
}

function files(dir: string): string[] {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((e) =>
    e.isDirectory() ? files(path.join(dir, e.name)) : e.name.endsWith('.vow') ? [path.join(dir, e.name)] : []);
}

test('every keyword of every sample vow has the scope of its depth', async () => {
  const grammar = await (await registry()).loadGrammar('source.vow');
  assert.ok(grammar);
  let keywords = 0;
  for (const file of files(VOWS)) {
    const text = fs.readFileSync(file, 'utf8');
    const lines = text.split('\n');
    const lineStarts: number[] = [];
    lines.reduce((offset, line) => (lineStarts.push(offset), offset + line.length + 1), 0);
    let state = textmate.INITIAL;
    const scopesAt = new Map<number, string[]>();
    lines.forEach((line, n) => {
      const result = grammar.tokenizeLine(line, state);
      for (const t of result.tokens) scopesAt.set(lineStarts[n] + t.startIndex, t.scopes);
      state = result.ruleStack;
    });
    for (const token of tokenize(text)) {
      const expected = DEPTH_SCOPE[token.kind];
      if (expected === undefined) continue;
      keywords++;
      assert.ok(scopesAt.get(token.start)?.includes(expected),
        `${path.relative(VOWS, file)} at ${token.start}: ${expected} expected, got ${scopesAt.get(token.start)}`);
    }
    const header = grammar.tokenizeLine(lines[0], textmate.INITIAL).tokens[0];
    assert.ok(header.scopes.includes('keyword.other.header.vow'), `${file}: the first line`);
  }
  assert.ok(keywords > 1000, `${keywords} keywords`);
});

test('Edit spec from a component: every field it has, the spec entry giving what show does not (0.11.3)', () => {
  const snapshot = { component: 'sales_reader', description: 'Read partitioned data', uses: ['config', 'sales_schema'] };
  const entry = { id: 'sales_reader', spec: 'FATTRIB-12', group: 'readers', owners: ['@alice', '@data-team'], from: 'briefs/sales.md#reader' };
  const whole = prefill('component edit', { component: 'sales_reader' }, '', snapshot, entry);
  assert.deepEqual(whole, { component: 'sales_reader', description: 'Read partitioned data', spec: 'FATTRIB-12', uses: 'config,sales_schema',
    group: 'readers', owners: '@alice,@data-team', source: 'briefs/sales.md#reader' });
  const fix = prefill('component edit', { component: 'sales_reader', uses: 'config' }, '', snapshot, entry);
  assert.deepEqual(fix, { component: 'sales_reader', uses: 'config' });
});

test('hazard / invariant remove: the id show --json gives (clean_sales.H1) is kept, a bare one gets its component', () => {
  // the IntelliJ form was prefilled clean_sales.clean_sales.H1 and archvow refused it (usage: ... clean_orders H1)
  assert.equal(itemId('clean_sales', 'clean_sales.H1'), 'clean_sales.H1');
  assert.equal(itemId('clean_sales', 'clean_sales.INV2'), 'clean_sales.INV2');
  assert.equal(itemId('clean_sales', 'H1'), 'clean_sales.H1');
  assert.equal(itemId('clean_sales', ' INV3 '), 'clean_sales.INV3');
  assert.equal(itemId('', 'H1'), 'H1');
});
