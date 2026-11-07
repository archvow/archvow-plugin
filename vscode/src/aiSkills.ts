// The AI skills and the origin of each item (0.16.0, archvow 1.20). From one component's `show --json`:
// where each decision, hazard and rule came from (the tag on its card), what the AI added that no person
// kept yet ("To review"), which skill to suggest next and why a skill cannot run yet. The page draws it;
// IntelliJ's ArchvowAiSkills computes the same (AiSkillsRegression, contract/forms/ai-skills.expected.json).

type Json = Record<string, unknown>;
type Pair = [string, string];

/** Where an item came from: the template, the project's answer for a type, a brief, the AI - or '' (a person). */
export type Tag = 'template' | 'project' | 'brief' | 'ai' | '';

export interface Origin {
  tag: Tag;
  /** Added by the AI (or an answer it proposed) and no person kept it yet. */
  toReview: boolean;
  /** The agent that added it (`added_by`), and why. */
  who: string;
  why: string;
  /** The brief section, for `brief`. */
  section: string;
  /** An answer the AI proposed (decision propose), waiting for a person. */
  proposal: boolean;
}

export interface Counts { decisions: number; safety: number; rules: number; assumptions: number; component: number; total: number }

export interface Suggestion {
  /** A skill id of forms.json ai_skills, or 'to-review' when what the AI added waits for a person first. */
  skill: string;
  count: number;
  reason: Pair;
}

export interface AiView {
  decisions: Origin[];
  hazards: Record<string, Origin>;
  /** Per safety question (in order): something the AI added there waits for a person. */
  safety: boolean[];
  rules: Record<string, Origin>;
  toReview: Counts;
  suggest: Suggestion | null;
  /** skill id -> why it cannot run yet ([en, fr]); absent: it can. */
  blocked: Record<string, Pair>;
}

export const SKILLS = ['draft-spec', 'clarify', 'analyze', 'implement', 'review'];

const s = (v: unknown): string => (typeof v === 'string' ? v : '');
const list = (v: unknown): Json[] => (Array.isArray(v) ? v.filter((x): x is Json => !!x && typeof x === 'object') : []);
const none = (): Origin => ({ tag: '', toReview: false, who: '', why: '', section: '', proposal: false });

function aiOrigin(item: Json): Origin | null {
  if (!s(item.added_by)) return null;
  return { ...none(), tag: 'ai', toReview: item.to_accept === true, who: s(item.added_by), why: s(item.why) };
}

function briefOrigin(item: Json): Origin | null {
  const source = item.source && typeof item.source === 'object' ? (item.source as Json) : null;
  if (!source || !s(source.file)) return null;
  return { ...none(), tag: 'brief', section: s(source.section) };
}

/** A decision: the AI's question or proposal first, then the project's answer, the brief, the template. */
export function decisionOrigin(d: Json): Origin {
  const ai = aiOrigin(d);
  if (ai) return ai;
  if (s(d.proposed) && !s(d.answer)) return { ...none(), tag: 'ai', toReview: true, proposal: true };
  if (s(d.origin) === 'type') return { ...none(), tag: 'project' };
  const brief = briefOrigin(d);
  if (brief) return brief;
  return d.template === true ? { ...none(), tag: 'template' } : none();
}

/** A hazard: the AI's (its own or one of the invariants it added to prevent it), or the template's words. */
export function hazardOrigin(h: Json): Origin {
  const ai = aiOrigin(h);
  const guards = list(h.prevented_by).filter((i) => i.to_accept === true);
  if (ai) return { ...ai, toReview: ai.toReview || guards.length > 0 };
  if (guards.length) return { ...none(), tag: 'ai', toReview: true, who: s(guards[0].added_by), why: s(guards[0].why) };
  return h.suggested === true ? { ...none(), tag: 'template' } : none();
}

export function ruleOrigin(r: Json): Origin {
  return aiOrigin(r) ?? briefOrigin(r) ?? none();
}

const openDecisions = (view: Json): number => list(view.decisions).filter((d) => !s(d.answer) && !s(d.proposed)).length;
const openSafety = (view: Json): number => list(view.safety).filter((q) => s(q.status) === 'open').length;

/** Everything the page needs about AI for one component. `launched`: the skills a person started on it here. */
export function aiView(view: Json | null, launched: string[] = []): AiView {
  const out: AiView = { decisions: [], hazards: {}, safety: [], rules: {}, blocked: {}, suggest: null,
    toReview: { decisions: 0, safety: 0, rules: 0, assumptions: 0, component: 0, total: 0 } };
  if (!view) return out;
  out.decisions = list(view.decisions).map(decisionOrigin);
  for (const q of list(view.safety)) {
    let waiting = false;
    for (const h of list(q.hazards)) {
      const o = hazardOrigin(h);
      out.hazards[s(h.id)] = o;
      waiting = waiting || o.toReview;
    }
    out.safety.push(waiting);
  }
  for (const r of list(view.rules)) out.rules[s(r.id)] = ruleOrigin(r);
  const t = out.toReview;
  t.decisions = out.decisions.filter((o) => o.toReview).length;
  t.safety = out.safety.filter((x) => x).length;
  t.rules = Object.values(out.rules).filter((o) => o.toReview).length;
  t.assumptions = list(view.assumptions).filter((a) => s(a.status).toLowerCase() === 'proposed').length;
  t.component = s(view.origin) === 'ai' ? 1 : 0;
  t.total = t.decisions + t.safety + t.rules + t.assumptions + t.component;
  const decisions = openDecisions(view);
  if (t.total > 0) out.blocked.implement = [`Not yet: ${t.total} item${t.total === 1 ? '' : 's'} from the AI to review`,
    `Pas encore : ${t.total} élément${t.total === 1 ? '' : 's'} de l'IA à relire`];
  else if (decisions > 0) out.blocked.implement = [`Not yet: ${decisions} decision${decisions === 1 ? '' : 's'} still to make`,
    `Pas encore : ${decisions} décision${decisions === 1 ? '' : 's'} encore à prendre`];
  if (s(view.status) === 'planned') out.blocked.review = ['Nothing to review yet: no code', 'Rien à relire encore : pas de code'];
  out.suggest = suggest(view, out, launched, decisions);
  return out;
}

/** One suggestion at a time, from the component's state: what the AI added first, then the work in order. */
function suggest(view: Json, ai: AiView, launched: string[], decisions: number): Suggestion | null {
  const t = ai.toReview.total;
  if (t > 0) return { skill: 'to-review', count: t,
    reason: [`${t} item${t === 1 ? '' : 's'} added by the AI wait${t === 1 ? 's' : ''} for you: keep, change or remove each one.`,
      `${t} élément${t === 1 ? '' : 's'} ajouté${t === 1 ? '' : 's'} par l'IA ${t === 1 ? "t'attend" : "t'attendent"} : garder, changer ou retirer chacun.`] };
  const planned = s(view.status) === 'planned';
  if (!planned) return launched.includes('review') ? null : { skill: 'review', count: 0,
    reason: ['The code exists: the AI reviews it against the spec and writes a report.',
      "Le code existe : l'IA le relit par rapport à la spec et écrit un rapport."] };
  if (!launched.includes('clarify') && !list(view.decisions).some((d) => s(d.added_by))) return { skill: 'clarify', count: 0,
    reason: ['No one has looked for what the spec leaves open yet: the AI adds up to 5 questions, you answer them.',
      "Personne n'a encore cherché ce que la spec laisse ouvert : l'IA ajoute jusqu'à 5 questions, tu y réponds."] };
  if (decisions > 0) return null;  // a person's turn: the decisions first
  const safety = openSafety(view);
  if (safety > 0) return launched.includes('analyze') ? null : { skill: 'analyze', count: safety,
    reason: [`${safety} safety question${safety === 1 ? ' is' : 's are'} open. The AI proposes a hazard and an invariant for each; nothing counts until you keep it.`,
      `${safety} question${safety === 1 ? '' : 's'} de sûreté ${safety === 1 ? 'est ouverte' : 'sont ouvertes'}. L'IA propose un hazard et un invariant pour chacune ; rien ne compte tant que tu ne l'as pas gardé.`] };
  return { skill: 'implement', count: 0,
    reason: ['Nothing is open: the AI can write the code and the tests.', "Rien n'est ouvert : l'IA peut écrire le code et les tests."] };
}

/** Clear, actionable handoff for any AI, not merely an agent-specific slash command plus a path. */
export function request(skills: Json, skill: string, _tool: string, values: Record<string, string>, fr: boolean): string {
  const def = ((skills.skills ?? {}) as Json)[skill] as Json | undefined;
  const wording = (key: string): string => {
    const value = def?.[key];
    return Array.isArray(value) ? String(value[fr && value.length > 1 ? 1 : 0] ?? '') : '';
  };
  const isBrief = def?.target === 'brief';
  const target = String(values[isBrief ? 'brief' : 'component'] ?? '').trim();
  if (fr) return [
    `Tâche Archvow : ${wording('label')}`,
    `Skill, s'il est installé : archvow-${skill}`,
    `Répertoire de travail : le projet actuellement ouvert dans l'IDE`,
    `${isBrief ? 'Brief / section' : 'Composant'} : ${target}`,
    ...(skill === 'clarify' ? [`Nombre maximum de questions : ${values.max ?? '5'}`] : []),
    '', `Objectif : ${wording('does')}`, `À lire : ${wording('reads')}`,
    `Modifications ou propositions possibles : ${wording('may_add')}`,
    `Validation humaine : ${wording('you')}`,
    '', 'Consignes :',
    '1. Lis le brief ou composant ciblé dans le dépôt, ainsi que les spécifications Archvow et les instructions locales pertinentes. Si le chemin contient #, utilise cette section précise.',
    `2. Si le skill archvow-${skill} est disponible, utilise-le. Sinon, réalise l'objectif décrit, sans inventer de commande Archvow.`,
    '3. Respecte le mode Archvow du projet et les validations humaines ; ne présente jamais une proposition comme acceptée.',
    "4. Si tu ne peux pas lire les fichiers du projet, demande leur contenu avant de travailler ; un chemin seul n'est pas leur contenu.",
    '5. Résume les changements, propositions et points restant à valider.',
  ].join('\n');
  return [
    `Archvow task: ${wording('label')}`, `Skill, if installed: archvow-${skill}`,
    'Workspace: the project currently open in the IDE',
    `${isBrief ? 'Brief / section' : 'Component'}: ${target}`,
    ...(skill === 'clarify' ? [`Maximum questions: ${values.max ?? '5'}`] : []),
    '', `Goal: ${wording('does')}`, `Read first: ${wording('reads')}`,
    `You may add or propose: ${wording('may_add')}`, `Human approval: ${wording('you')}`,
    '', 'Instructions:',
    '1. Read the targeted brief or component in this repository, the Archvow specs and relevant local instructions. If the path includes #, use that specific section.',
    `2. If the archvow-${skill} skill is installed, use it. Otherwise perform the described goal without inventing Archvow commands.`,
    "3. Respect the project's Archvow mode and human approval gates; do not treat a proposal as accepted.",
    '4. If you cannot access the project files, ask me for their contents before acting; the path alone is not the document.',
    '5. Summarize changes, proposals and items requiring human review.',
  ].join('\n');
}

/** An optional shorthand for agents that actually have the named skill installed. */
export function skillCommand(skills: Json, skill: string, tool: string, values: Record<string, string>): string {
  const def = ((skills.skills ?? {}) as Json)[skill] as Json | undefined;
  const req = (def?.request ?? {}) as Json;
  const own = s(req[tool]);
  return own.replace(/\{(\w+)\}/g, (_, key: string) => values[key] ?? '');
}
