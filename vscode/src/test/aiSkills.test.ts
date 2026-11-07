// The AI view of a component (0.16.0): the origin of each item, what the AI added that waits for a person,
// the next skill and why a skill cannot run yet. IntelliJ computes the same on the same scenarios
// (AiSkillsRegression on contract/forms/ai-skills.expected.json, which UPDATE_AI_SKILLS=1 npm test writes).
import * as assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { test } from 'node:test';
import { aiView, request, skillCommand, SKILLS } from '../aiSkills';
import { Json } from '../workItems';

const CONTRACT = path.resolve(__dirname, '..', '..', '..', 'contract');
const read = (file: string): Json => JSON.parse(fs.readFileSync(file, 'utf8')) as Json;
const SCENARIOS = (read(path.join(CONTRACT, 'forms', 'ai-skills.scenarios.json')).scenarios as Json[]);
const EXPECTED = path.join(CONTRACT, 'forms', 'ai-skills.expected.json');
const FORMS = read(path.join(CONTRACT, 'forms', 'forms.json'));

test('every origin gets its tag, and what the AI added waits for a person', () => {
  const ai = aiView(SCENARIOS[0].view as Json, []);
  assert.deepEqual(ai.decisions.map((o) => o.tag), ['template', 'project', 'brief', 'ai', 'ai', 'ai', '']);
  assert.ok(ai.decisions[3].proposal && ai.decisions[3].toReview);  // an answer the AI proposed
  assert.equal(ai.decisions[4].why, 'the brief shows cancelled orders');
  assert.ok(!ai.decisions[5].toReview);  // answered by a person: kept
  assert.equal(ai.decisions[2].section, 'cleaning');
  assert.deepEqual(ai.safety, [false, true, true, false]);  // the AI's hazard, a person's hazard with the AI's invariant
  assert.equal(ai.hazards['clean_orders.H1'].tag, 'template');
  assert.deepEqual([ai.rules.R1.tag, ai.rules.R2.tag, ai.rules.R2.toReview, ai.rules.R3.tag], ['brief', 'ai', true, '']);
  assert.deepEqual(ai.toReview, { decisions: 2, safety: 2, rules: 1, assumptions: 1, component: 0, total: 6 });
  assert.equal(ai.suggest?.skill, 'to-review');
  assert.match(ai.blocked.implement[0], /6 items from the AI to review/);
});

test('one suggestion at a time, in the order of the work', () => {
  assert.deepEqual(SCENARIOS.slice(1).map((sc) => aiView(sc.view as Json, sc.launched as string[]).suggest?.skill ?? null),
    ['clarify', null, 'analyze', 'implement', 'review', 'to-review']);
  const done = aiView(SCENARIOS[3].view as Json, ['clarify']);
  assert.match(done.suggest?.reason[0] ?? '', /2 safety questions are open/);
  assert.equal(done.blocked.implement, undefined);
  assert.match(aiView(SCENARIOS[2].view as Json, [])?.blocked.implement[1] ?? '', /1 décision encore à prendre/);
  assert.ok(aiView(SCENARIOS[1].view as Json).blocked.review);  // no code yet
});

test('AI handoff: generic instructions are actionable; native slash command stays optional', () => {
  const skills = FORMS.ai_skills as Json;
  assert.deepEqual(SKILLS, (skills.order as string[]));
  const clarify = request(skills, 'clarify', 'claude-code', { component: 'clean_orders', max: '3' }, false);
  assert.match(clarify, /Archvow task: Find missing questions/);
  assert.match(clarify, /Component: clean_orders/);
  assert.match(clarify, /Maximum questions: 3/);
  assert.match(clarify, /Human approval:/);
  assert.match(clarify, /If you cannot access the project files/);
  assert.equal(skillCommand(skills, 'clarify', 'claude-code', { component: 'clean_orders', max: '3' }),
    '/archvow-clarify clean_orders (at most 3 questions)');
  assert.equal(skillCommand(skills, 'clarify', 'copilot', { component: 'clean_orders' }), '');
  const french = request(skills, 'analyze', 'copilot', { component: 'clean_orders' }, true);
  assert.match(french, /Tâche Archvow : Trouver les risques/);
  assert.match(french, /Validation humaine\s*:/);
  const brief = request(skills, 'draft-spec', 'cursor', { brief: 'briefs/orders.md#pricing' }, false);
  assert.match(brief, /Brief \/ section: briefs\/orders\.md#pricing/);
  assert.match(brief, /use that specific section/);
  for (const id of SKILLS) {
    const def = (skills.skills as Json)[id] as Json;
    for (const key of ['label', 'does', 'reads', 'may_add', 'you']) assert.equal((def[key] as string[]).length, 2, `${id}.${key}: [en, fr]`);
  }
});

test('IntelliJ computes the same AI view (contract/forms/ai-skills.expected.json)', () => {
  const got = SCENARIOS.map((sc) => ({ name: sc.name, ai: aiView(sc.view as Json, sc.launched as string[]) }));
  if (process.env.UPDATE_AI_SKILLS === '1') fs.writeFileSync(EXPECTED, JSON.stringify(got, null, 2) + '\n');
  assert.deepEqual(got, JSON.parse(fs.readFileSync(EXPECTED, 'utf8')));
});
