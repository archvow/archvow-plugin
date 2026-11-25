// The extension reads what archvow 1.x really prints: ../contract/cli/ (contract/tools/regenerate.py).
import * as assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { test } from 'node:test';
import {
  buildTree, CheckResult, componentOfViolation, componentView, describe, knownVersion, Node, NextRow, problemsOf, Spec, statusIcon,
  parseContract, readWorkspace,
} from '../model';
import { compatibility, headerLine, parseRange, placeOf, projectPin } from '../compat';

const CLI = path.resolve(__dirname, '..', '..', '..', 'contract', 'cli');
// an example is a folder with its show.json (contract/cli/errors holds failures, not a project)
const EXAMPLES = fs.readdirSync(CLI).filter((d) => fs.existsSync(path.join(CLI, d, 'show.json')));
const read = <T>(...parts: string[]): T => JSON.parse(fs.readFileSync(path.join(CLI, ...parts), 'utf8')) as T;

function components(nodes: Node[]): (Node & { kind: 'component' })[] {
  return nodes.flatMap((n) => (n.kind === 'component' ? [n] : components(n.children)));
}

test('the contract has the three examples and the briefs scenario', () => {
  assert.deepEqual(EXAMPLES.sort(), ['briefs', 'fastapi-service', 'pyspark-pipeline', 'python-app']);
});

for (const example of EXAMPLES) {
  test(`${example}: every component of the spec is in the tree once, with check's status and next's readiness`, () => {
    const spec = read<Spec>(example, 'show.json');
    const check = read<CheckResult>(example, 'check.json');
    const next = read<NextRow[]>(example, 'next.json');
    const tree = components(buildTree(spec, check, next));
    const ids = spec.containers.flatMap((c) => c.components.map((x) => x.id));
    assert.deepEqual(tree.map((n) => n.id).sort(), [...ids].sort());
    for (const node of tree) {
      assert.equal(node.status?.status, check.components?.find((c) => c.component === node.id)?.status, node.id);
      assert.equal(node.next?.readiness, next.find((n) => n.component === node.id)?.readiness, node.id);
      assert.ok(describe(node).length > 0, node.id);
      assert.ok(statusIcon(node), node.id);
    }
  });

  test(`${example}: check's violations become problems with 0-based lines in project files`, () => {
    const check = read<CheckResult>(example, 'check.json');
    const problems = problemsOf(check);
    assert.equal(problems.length, check.violations.length);
    problems.forEach((p, i) => {
      assert.equal(p.line, Math.max(0, check.violations[i].line - 1));
      assert.equal(p.error, check.violations[i].severity === 'error');
      assert.ok(!path.isAbsolute(p.file), p.file);
    });
    assert.equal(problems.filter((p) => p.error).length, check.errors);
    assert.equal(problems.filter((p) => !p.error).length, check.warnings);
  });

  test(`${example}: show <component> --json has what the panel reads`, () => {
    for (const file of fs.readdirSync(path.join(CLI, example, 'show'))) {
      const view = read<Record<string, unknown>>(example, 'show', file);
      for (const key of ['component', 'type', 'description', 'status', 'decisions', 'rules', 'missing', 'questions', 'safety', 'assumptions', 'tasks']) {
        assert.ok(key in view, `${file}: ${key}`);
      }
      for (const d of view.decisions as Record<string, unknown>[]) {
        for (const key of ['number', 'question', 'answer', 'suggested', 'proposed']) assert.ok(key in d, `${file}: decision ${key}`);
      }
      for (const q of view.questions as Record<string, unknown>[]) assert.match(String(q.key), /^[A-Z][0-9]+$/, file);
      for (const s of view.safety as Record<string, unknown>[]) assert.ok(Number.isInteger(s.number) && typeof s.status === 'string', file);
    }
  });
}

test('the pyspark example: the planned components come first in next, ready or waiting in archvow\'s words', () => {
  const next = read<NextRow[]>('pyspark-pipeline', 'next.json');
  assert.ok(next.some((n) => n.readiness.startsWith('ready')));
  assert.ok(next.some((n) => n.readiness.startsWith('waiting for')));
});

test('a violation names its component by its vow file', () => {
  assert.equal(componentOfViolation({ code: 'ARCH035', severity: 'warning', message: '', file: '.archvow/features/vat_report/components/clean_orders.vow', line: 0, col: 0 }), 'clean_orders');
  assert.equal(componentOfViolation({ code: 'ARCH002', severity: 'error', message: '', file: 'src/a/b.py', line: 3, col: 0 }), null);
});

test('the CLI contract and the range: what the plugin says of the installed archvow, EN and FR (contract/compat/expected.json)', () => {
  const tested = knownVersion(fs.readFileSync(path.join(CLI, 'version.txt'), 'utf8'));
  const wanted = fs.readFileSync(path.join(CLI, 'contract.txt'), 'utf8').trim();
  assert.match(tested, /^\d+\.\d+\.\d+$/);
  assert.deepEqual(parseContract(wanted), [3, 0]);  // 3.0: templates; 2.5 show of a pasted error; 2.4 a rule's scope
  assert.equal(parseContract('1.0.0'), null);
  const range = parseRange(fs.readFileSync(path.join(CLI, '..', 'archvow-range.txt'), 'utf8'))!;
  assert.deepEqual([range.low, range.high], [[3, 3], [4]]);
  assert.equal(placeOf(tested, range), 'in', 'the archvow the plugin is tested with is in its range');
  assert.deepEqual(projectPin([{ name: 'requirements.txt', text: 'pytest\narchvow==2.8.0\n' }]), { version: '2.8.0', file: 'requirements.txt' });
  assert.deepEqual(projectPin([{ name: 'pyproject.toml', text: 'dev = ["archvow==3.0.1", "ruff"]' }]), { version: '3.0.1', file: 'pyproject.toml' });
  assert.equal(projectPin([{ name: 'requirements.txt', text: 'archvow>=3' }]), null);
  const file = path.join(CLI, '..', 'compat', 'expected.json');
  const cases = (JSON.parse(fs.readFileSync(file, 'utf8')) as { cases: CompatCase[] }).cases;
  const actual = cases.map((c) => ({ ...c, expected: compatibility(c.installed, range, c.wanted, c.tested, c.pin, c.fr) }));
  if (process.env.UPDATE_COMPAT === '1') fs.writeFileSync(file, JSON.stringify({ about: ABOUT_COMPAT, cases: actual }, null, 2) + '\n');
  else assert.deepEqual(actual, cases);
  const byName = Object.fromEntries(actual.map((c) => [c.name, c.expected]));
  assert.equal(byName['tested'].state, 'ok');
  assert.equal(byName['a later minor contract'].state, 'ok');
  assert.match(byName['pinned below the range'].text, /pins archvow==2\.8\.0 \(requirements\.txt\).*init <package> --force.*nothing over the pin/);
  assert.equal(byName['pinned below the range'].install, '', 'never installed over the pin');
  assert.equal(byName['not pinned, older'].install, tested);
  assert.equal(byName['pinned in the range, missing'].install, '3.3.1', 'the pin, when it is in the range');
  assert.match(byName['newer'].text, /newer.*update the plugin/);
  assert.match(byName['newer, fr'].text, /plus récent.*mets le plugin à jour/);
  assert.match(byName['missing'].text, /not installed.*python -m pip install archvow==/);
  assert.equal(headerLine('3.3.0', 'ok', range), 'archvow 3.3.0 ✓ · this plugin works with archvow >=3.3,<4');
});

test('show <id> --json is a component\'s view only when it names that component (a search result never is)', () => {
  type View = Record<string, unknown>;
  const view = read<View>('python-app', 'show', 'mark_paid.json');
  assert.equal(componentView(view, 'mark_paid'), view);
  assert.equal(componentView(view, 'invoices_cli'), null);
  assert.equal(componentView(read<View>('python-app', 'search', 'mark_payd.json'), 'mark_payd'), null);
  assert.equal(componentView(read<View>('python-app', 'search', 'InvoiceNotFoundError.json'), 'InvoiceNotFoundError'), null);
  assert.equal(componentView(null, 'mark_paid'), null);
});

test('workspace --json is every command the extension reads, in one answer (archvow 1.25, contract 1.1)', () => {
  type View = Record<string, unknown>;
  const drop = (v: View): View => Object.fromEntries(Object.entries(v).filter(([k]) => k !== 'contract'));
  for (const example of EXAMPLES.filter((e) => fs.existsSync(path.join(CLI, e, 'workspace.json')))) {
    const answer = readWorkspace(read<View>(example, 'workspace.json'));
    assert.ok(answer !== null && answer !== 'unchanged', example);
    assert.equal(answer.digest, '<digest>');
    assert.deepEqual(answer.spec, drop(read<View>(example, 'show.json')));
    assert.deepEqual(answer.next, read<View[]>(example, 'next.json'));
    assert.deepEqual(answer.check, drop(read<View>(example, 'check.json')));
    const shown = fs.readdirSync(path.join(CLI, example, 'show')).map((f) => f.replace(/\.json$/, '')).sort();
    assert.deepEqual(Object.keys(answer.snapshots).sort(), shown);
    for (const id of shown) assert.deepEqual(answer.snapshots[id], drop(read<View>(example, 'show', `${id}.json`)));
    assert.ok(Array.isArray(answer.briefStatus?.files), example);
    assert.ok(Array.isArray(answer.catalog?.templates), example);
  }
  assert.equal(readWorkspace({ unchanged: true, digest: 'abc' }), 'unchanged');
  assert.equal(readWorkspace(JSON.parse(read<{ stdout: string }>('errors', 'no-spec.json').stdout) as View), null);  // the error envelope
  assert.equal(readWorkspace({ digest: 'abc', project: {} }), null);  // a part missing
  assert.equal(readWorkspace(null), null);
});

interface CompatCase {
  name: string;
  installed: { version: string; contract: string } | null;
  wanted: string;
  tested: string;
  pin: { version: string; file: string } | null;
  fr: boolean;
  expected: unknown;
}

const ABOUT_COMPAT = 'What both plugins say of the installed archvow (1.0.0): each case - the installed archvow (null: it did not answer), '
  + 'the contract the plugin speaks, the archvow it is tested with, the project\'s pin - against contract/archvow-range.txt. '
  + 'Written by UPDATE_COMPAT=1 npm test (vscode/compat.ts); the IntelliJ plugin must give the same (CompatRegression).';
