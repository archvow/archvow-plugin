// Every diagnostic says what to do (guidance.ts), on every case: each code archvow documents (rules.md,
// rules.fr.md) and each message archvow's own tests make it print (contract/cli/messages.json,
// contract/tools/harvest_messages.py). A command a message names becomes a form that runs as it is, or
// that leaves only the person's part (<component>, "...") empty. The IntelliJ plugin gives exactly the
// same (contract/guidance/expected.json, GuidanceRegression): UPDATE_GUIDANCE=1 npm test rewrites it.
import * as assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { test } from 'node:test';
import { Command, planForm } from '../grammar';
import { commandsIn, guide, Guidance } from '../guidance';

const CONTRACT = path.resolve(__dirname, '..', '..', '..', 'contract');
const read = (...parts: string[]): string => fs.readFileSync(path.join(CONTRACT, ...parts), 'utf8');
const COMMANDS = (JSON.parse(read('cli', 'commands.json')) as { commands: Command[] }).commands;
const RULES = { en: read('cli', 'rules.md'), fr: read('cli', 'rules.fr.md') };
interface Message { code: string; severity: string; message: string; file: string; line: number }
const MESSAGES = (JSON.parse(read('cli', 'messages.json')) as { messages: Message[] }).messages;
const DOCUMENTED = [...RULES.en.matchAll(/^\| ([A-Z]{2,12}\d{3}) \|/gm)].map((m) => m[1]);
const FALLBACK = /archvow found this while checking|archvow a trouvé ceci/;
const g = (m: Message, fr = false): Guidance => guide(m, fr ? RULES.fr : RULES.en, COMMANDS, fr);

test('the messages are real: archvow 1.18 printed them, for at least 60 check codes', () => {
  assert.ok(MESSAGES.length > 300, `${MESSAGES.length} messages`);
  assert.ok(new Set(MESSAGES.map((m) => m.code)).size >= 60);
});

test('every code archvow documents gets its meaning and what to do, in English and in French', () => {
  assert.ok(DOCUMENTED.length >= 80, `${DOCUMENTED.length} codes`);
  for (const code of DOCUMENTED) {
    for (const fr of [false, true]) {
      const it = g({ code, severity: 'error', message: '', file: '', line: 0 }, fr);
      assert.ok(it.meaning && !FALLBACK.test(it.meaning), `${code} (${fr ? 'fr' : 'en'}): no meaning`);
      assert.ok(it.todo.length > 10, `${code} (${fr ? 'fr' : 'en'}): no "what to do"`);
      assert.ok(it.who && it.blocking && it.then, code);
      assert.doesNotMatch(it.meaning + it.todo, /\]\(|^\s*\(warning/, `${code}: markdown left`);
    }
  }
});

test('every real message gets a guidance; a warning never says it blocks, an error always does', () => {
  for (const m of MESSAGES) {
    for (const fr of [false, true]) {
      const it = g(m, fr);
      assert.ok(it.meaning && it.todo && it.who && it.blocking && it.then, `${m.code}: ${m.message}`);
      if (m.severity === 'warning') assert.match(it.blocking, /^(Does not block|Non bloquant)/);
      else assert.match(it.blocking, /^(Blocks|Bloquant)/);
      const path = m.file.replace(/\s+\(and \d+ more\)$/, '');
      if (path && !(path.startsWith('.archvow/') && path.endsWith('.vow'))) assert.deepEqual(it.file, { path, line: m.line || 0 });
    }
  }
});

test('a command a message names is found, and its form runs as it is or leaves only the placeholders empty', () => {
  let prepared = 0;
  for (const m of MESSAGES) {
    const named = [...m.message.matchAll(/\barchvow ([a-z]+)(?: ([a-z]+))?/g)]
      .filter((w) => COMMANDS.some((c) => c.path[0] === w[1] && (c.path.length === 1 || c.path[1] === w[2])));
    const found = commandsIn(m.message, COMMANDS);
    if (named.length) assert.ok(found.length > 0, `${m.code}: no command found in ${m.message}`);
    for (const c of found) {
      const command = COMMANDS.find((x) => x.path.join(' ') === c.name)!;
      for (const [name, value] of Object.entries(c.values)) {
        assert.ok(command.parameters.some((p) => p.name === name), `${c.name}: no parameter ${name}`);
        assert.doesNotMatch(value, /<[^>]*>|^\.\.\.$|^…$/, `${c.text}: placeholder kept in ${name}`);
      }
      const plan = planForm(command, c.values, false, {});
      if (c.missing.length === 0) assert.equal(plan.kind, 'run', `${c.text}: ${JSON.stringify(plan)}`);
      else if (plan.kind === 'error') assert.ok(c.missing.some((n) => plan.message.includes(n)), `${c.text}: ${plan.message}`);
      prepared++;
    }
  }
  assert.ok(prepared > 100, `${prepared} commands prepared`);
});

test('the two cases of the screenshot: ARCH012 prepares init --force for an architect; ARCH053 (Copilot in JetBrains) says commit', () => {
  const upgrade = g({ code: 'ARCH012', severity: 'warning', file: '.archvow/spec.vow', line: 0,
    message: 'the vow says pyspark-pipeline@1.11.0, installed is pyspark-pipeline@1.17.0: to adopt it, archvow init acoss_pipeline2 --force (then review and commit)' });
  assert.deepEqual(upgrade.command && { name: upgrade.command.name, values: upgrade.command.values, missing: upgrade.command.missing },
    { name: 'init', values: { package: 'acoss_pipeline2', force: 'true' }, missing: [] });
  assert.match(upgrade.meaning, /set up with pyspark-pipeline 1\.11\.0; the archvow installed here brings 1\.17\.0/);
  assert.match(upgrade.todo, /keeps your spec and your answers.*git diff/);
  assert.match(upgrade.who, /^An architect/);
  const unseen = g({ code: 'ARCH053', severity: 'warning', file: 'scripts/acoss_pipeline2/p0_common/config.py', line: 0,
    message: 'written by copilot, which archvow cannot see in JetBrains IDEs (the Copilot plugin sets no variable archvow knows): nobody checked how it ran (sandbox, YOLO mode)' });
  assert.match(unseen.meaning, /archvow cannot see copilot in JetBrains IDEs/);
  assert.match(unseen.todo, /^Review what copilot wrote, then commit it\. .*disappears with the commit, and it never blocks/);
  assert.match(unseen.todo, /use copilot where archvow sees it/);
  assert.equal(unseen.command, null);
  // archvow names the first file: "config.py (and 3 more)" opens config.py; the project vow of ARCH012 is not to open
  assert.deepEqual(g({ code: 'ARCH053', severity: 'warning', message: '', file: 'scripts/a/config.py (and 3 more)', line: 0 }).file,
    { path: 'scripts/a/config.py', line: 0 });
  assert.equal(upgrade.file, null);
  assert.deepEqual(g({ code: 'ARCH014', severity: 'error', message: 'edited by hand', file: '.archvow/components/x/spec.vow', line: 4 }).file,
    { path: '.archvow/components/x/spec.vow', line: 4 });
  const nowhere = g({ code: 'ARCH053', severity: 'warning', file: 'x.py', line: 0,
    message: 'written by jetbrains-ai-chat, which archvow cannot see when it runs commands (it sets no variable archvow knows): nobody checked how it ran (sandbox, YOLO mode)' });
  assert.match(nowhere.todo, /archvow cannot check jetbrains-ai-chat anywhere/);
});

test('ARCH033: in the IDE a person makes the decision the code took silently (not an AI proposal)', () => {
  const silent = MESSAGES.filter((m) => m.code === 'ARCH033' && /decision propose/.test(m.message));
  assert.ok(silent.length > 0);
  for (const m of silent) {
    const it = g(m);
    assert.equal(it.command?.name, 'decision make', m.message);
    assert.match(it.command?.text ?? '', /^archvow decision make /);
    assert.match(it.who, /^A person/);
  }
});

test('each kind of ARCH053 says its own way out', () => {
  const kinds = [
    [/cannot see/, /then commit it/], [/never ran archvow/, /Ask .* to run archvow check/],
    [/refused to run inside it/, /Start .* protected/], [/no Archvow-Agent line/, /git reset --soft HEAD~1/],
    [/while it ran (in YOLO mode|outside a sandbox)/, /reviewed closely/]] as const;
  for (const [variant, todo] of kinds) {
    const messages = MESSAGES.filter((m) => m.code === 'ARCH053' && variant.test(m.message));
    assert.ok(messages.length > 0, `no real ARCH053 message ${variant}`);
    for (const m of messages) assert.match(g(m).todo, todo, m.message);
  }
});

test('a command with placeholders leaves them to the person; prose after a command is not part of it', () => {
  const [made] = commandsIn("'clean_orders' is implemented but 1 decision(s) are still to make -> archvow decision make clean_orders 3 \"...\" (a person)", COMMANDS);
  assert.deepEqual([made.name, made.values, made.missing], ['decision make', { component: 'clean_orders', question: '3' }, ['answer']]);
  const [accept] = commandsIn('a person runs archvow assumption accept <component> <n> or reject', COMMANDS);
  assert.deepEqual([accept.values, accept.missing], [{}, ['component', 'number']]);
  const [brief] = commandsIn('review them, then: archvow brief accept specs/001-orders/plan.md#paying-an-invoice', COMMANDS);
  assert.deepEqual(brief.values, { brief: 'specs/001-orders/plan.md#paying-an-invoice' });
  const [fenced] = commandsIn('add it: `archvow component edit clean_orders -u config,orders_schema` (architect review)', COMMANDS);
  assert.deepEqual(fenced.values, { component: 'clean_orders', uses: 'config,orders_schema' });
  assert.deepEqual(commandsIn('archvow cannot see it', COMMANDS), []);
});

test('the IntelliJ plugin gives the same guidance on every message (contract/guidance/expected.json)', () => {
  const entries = MESSAGES.flatMap((m) => [false, true].map((fr) => ({ fr, code: m.code, message: m.message, guide: g(m, fr) })));
  const text = JSON.stringify(entries, null, 1) + '\n';
  const file = path.join(CONTRACT, 'guidance', 'expected.json');
  if (process.env.UPDATE_GUIDANCE === '1') {
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, text);
  }
  assert.ok(fs.existsSync(file), 'run UPDATE_GUIDANCE=1 npm test once');
  assert.equal(fs.readFileSync(file, 'utf8'), text, 'guidance.ts changed: UPDATE_GUIDANCE=1 npm test, then GuidanceRegression');
});
