// The TextMate grammar (colours before the extension starts, and in Markdown fences) gives each keyword
// the scope of its depth, like the semantic tokens: checked on every sample vow.
import * as assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { test } from 'node:test';
import { alerts, Command, inline, itemId, prefill, withPluginParameters } from '../grammar';
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

test('Safety view (0.13.1): Remove, Change (remove + add) and the invariant written in place send what archvow 1.18 takes', () => {
  assert.deepEqual(inline.removeHazard('orders_reader', 'orders_reader.H1'), ['hazard', 'remove', 'orders_reader.H1', '--yes', '--json']);
  assert.deepEqual(inline.removeHazard('orders_reader', 'H2'), ['hazard', 'remove', 'orders_reader.H2', '--yes', '--json']);
  assert.deepEqual(inline.invariant('orders_reader', ' every file is read once ', 'orders_reader.H1'),
    ['invariant', 'add', 'orders_reader', 'every file is read once', '--prevents', 'orders_reader.H1', '--json']);
  assert.throws(() => inline.invariant('orders_reader', ' ', 'orders_reader.H1'));
  assert.throws(() => inline.removeHazard('orders_reader', ''));
  const page = fs.readFileSync(path.join(__dirname, '..', '..', 'media', 'workspace.js'), 'utf8');
  for (const action of ['hazardChange', 'hazardRemove', 'invariantInline', 'cancelChange']) assert.match(page, new RegExp(`${action}: \\(`), action);
  // French keeps the keyword hazard (the user's rule): no "danger" in what the page says
  assert.doesNotMatch(page, /'[^']*\b[Dd]angers?\b[^']*'/);
  // invariant add: archvow's hidden expert options are flagged, never shown as "==SUPPRESS=="
  const commands = JSON.parse(fs.readFileSync(path.join(__dirname, '..', '..', '..', 'contract', 'cli', 'commands.json'), 'utf8')).commands;
  const invariant = commands.find((c: { path: string[] }) => c.path.join(' ') === 'invariant add');
  for (const p of invariant.parameters) {
    assert.equal(p.hidden, ['verify', 'evidence', 'rule'].includes(p.name), p.name);
    assert.doesNotMatch(p.help, /SUPPRESS/);
  }
});

test('design A (0.14.0): forms.json names what exists, refusals and doubts show in place, the preview is the planned command', () => {
  const root = path.join(__dirname, '..', '..', '..');
  const forms = JSON.parse(fs.readFileSync(path.join(root, 'contract', 'forms', 'forms.json'), 'utf8'));
  // with the plugins' own fields (extra_parameters: output on changes and diff)
  const commands = withPluginParameters(JSON.parse(fs.readFileSync(path.join(root, 'contract', 'cli', 'commands.json'), 'utf8')).commands as Command[], forms);
  const byName = new Map(commands.map((c) => [c.path.join(' '), c]));
  const params = new Set(commands.flatMap((c) => c.parameters.map((p) => p.name)));
  for (const name of Object.keys(forms.labels)) assert.ok(params.has(name), `label of an unknown parameter: ${name}`);
  for (const p of params) assert.ok(p in forms.labels, `no plain label for ${p}`);
  for (const [name, entry] of Object.entries(forms.commands) as [string, Record<string, unknown>][]) {
    const command = byName.get(name);
    assert.ok(command, `forms.json: no command ${name}`);
    const own = new Set(command.parameters.map((p) => p.name));
    for (const p of [...((entry.essential as string[]) ?? []), ...Object.keys((entry.hints as object) ?? {})]) assert.ok(own.has(p), `${name}: ${p}`);
    for (const key of ['title', 'verb', 'does']) {
      const pair = entry[key] as string[];
      assert.ok(Array.isArray(pair) && pair.length === 2 && pair.every((x) => x.trim()), `${name}.${key}: [English, French]`);
    }
    for (const p of command.parameters) if (p.required && Array.isArray(entry.essential)) assert.ok((entry.essential as string[]).includes(p.name), `${name}: required ${p.name} is essential`);
  }
  // archvow said no: nothing written, its words in red; written with a doubt: amber
  assert.deepEqual(alerts({ exitCode: 2, stdout: '', stderr: '✖ archvow: error: an invariant says what is always true; "none" protects nothing.\n' }),
    { refusal: 'an invariant says what is always true; "none" protects nothing.', warnings: [] });
  assert.deepEqual(alerts({ exitCode: 0, stdout: JSON.stringify({ id: 'c.INV1', warnings: ['c.H1 is critical: an example_test is not enough (ARCH037)'] }), stderr: '' }),
    { refusal: '', warnings: ['c.H1 is critical: an example_test is not enough (ARCH037)'] });
  assert.deepEqual(alerts({ exitCode: 0, stdout: '✔ Changed c.H1\n! c.INV1 is proven by an example_test (ARCH037)\n', stderr: '' }).warnings,
    ['c.INV1 is proven by an example_test (ARCH037)']);
  // the page asks the extension for the preview: one grammar (planForm) for the preview and for Run
  const page = fs.readFileSync(path.join(__dirname, '..', '..', 'media', 'workspace.js'), 'utf8');
  for (const piece of ["type: 'formPreview'", 'function fieldLabel(', 'function isEssential(', "t('More options', \"Plus d'options\")", 'function formAlerts(', "'cancelForm'", 'id="form-preview"']) {
    assert.ok(page.includes(piece), piece);
  }
  const host = fs.readFileSync(path.join(__dirname, '..', '..', 'src', 'workspace.ts'), 'utf8');
  assert.match(host, /case 'formPreview':/);
  assert.match(host, /planForm\(command, values, this\.state\.fr/);
  // the Spec view: the card and the vow in one place; covered questions, choices, AI components
  for (const piece of ["'Spec'", "data-a=\"specPart\"", "action: 'hazard suggested', covered: '1'", "action: 'hazard dismiss', covered: '1'", "'useChoice'", 'function createdByAi(', "command: 'component accept'"]) {
    assert.ok(page.includes(piece), piece);
  }
  assert.ok(!page.includes("'Full vow'"));
});
