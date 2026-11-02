// What a command form offers to choose from, so nobody has to know an id by heart: the project's
// components, a component's questions (with their number), its proposals, assumptions, examples, safety
// questions and tasks, the project's hazards, invariants, rules and dependencies, the types and kinds of
// the project's template, the templates to extend, the branches to compare with. Choosing one can fill
// other fields (Edit spec: the component's description, ticket, uses...; rule edit: the rule's text;
// decision make: the answer recorded or suggested). Pure, from what archvow printed: `show --json`,
// `show <component> --json`, `catalog --json` (+ contract/cli/kinds.json for an archvow 1.17) and the
// repository's refs. The IntelliJ plugin computes exactly the same (ArchvowChoices, ChoicesRegression on
// contract/choices/expected.json, which UPDATE_CHOICES=1 npm test writes).
import { Command, label, prefill } from './grammar';
import { tr } from './i18n';
import { Json, objects, str } from './workItems';

/** One thing to choose: the value the command gets, what the person reads, the fields it fills. */
export interface Choice { value: string; label: string; fill?: Record<string, string> }

/**
 * A field of a form: `select` (only these values), `suggest` (these, or anything typed), `note` (the
 * usual field, with a line under it). `by`: the list depends on the form's component or type.
 */
export interface Field {
  kind: 'select' | 'suggest' | 'note';
  by?: 'component' | 'type';
  choices?: Choice[];
  lists?: Record<string, Choice[]>;
  empty?: string;
  note?: string;
  notes?: Record<string, string>;
}

/** command ("decision make") -> parameter ("question") -> what the form offers. */
export type Book = Record<string, Record<string, Field>>;

export interface ChoiceInput {
  spec: Json | null;
  snapshots: Record<string, Json>;
  catalog: Json | null;
  kinds: Json | null;
  refs: string[];
  base: string;
  fr: boolean;
}

const LABEL = 110;
// the same in Java: only ASCII white space is collapsed, code units are counted
const short = (s: string): string => {
  const flat = s.replace(/[ \t\n\r\f\v]+/g, ' ').replace(/^ | $/g, '');
  return flat.length <= LABEL ? flat : flat.substring(0, LABEL - 1) + '…';
};
const item = (value: string, text: string, fill?: Record<string, string>): Choice => {
  const c: Choice = { value, label: short(text ? `${value} — ${text}` : value) };
  if (fill && Object.keys(fill).length) c.fill = fill;
  return c;
};

/** The components of the spec, in its order. */
function components(spec: Json | null): Json[] {
  return objects(spec, 'containers').flatMap((c) => objects(c, 'components'));
}

/** The templates the project was made from (pyspark-pipeline@1.17.0 -> pyspark-pipeline). */
function projectTemplates(spec: Json | null): string[] {
  return [...new Set(objects(spec, 'containers').map((c) => str(c, 'template').split('@')[0]).filter((t) => t))];
}

/** The kinds of a type: the catalog's (archvow 1.18), else contract/cli/kinds.json (archvow 1.17). */
function kindsOf(template: string, type: string, definition: Json | undefined, kinds: Json | null): Json[] {
  if (definition && Array.isArray(definition.kinds)) return objects(definition, 'kinds');
  const byTemplate = kinds && kinds.kinds && typeof kinds.kinds === 'object' ? (kinds.kinds as Json)[template] : undefined;
  return byTemplate && Array.isArray(byTemplate[type]) ? objects(byTemplate, type) : [];
}

/** The types a person can add in this project (its template's), each with its kinds. */
function types(input: ChoiceInput): { type: Choice[]; kinds: Record<string, Choice[]> } {
  const wanted = projectTemplates(input.spec);
  const all = objects(input.catalog, 'templates');
  const used = wanted.length ? all.filter((t) => wanted.includes(str(t, 'name'))) : [];
  const from = used.length ? used : all;  // a template the catalog does not know: every known type
  const type: Choice[] = [];
  const kinds: Record<string, Choice[]> = {};
  for (const template of from) {
    const declared = (template.component_types ?? {}) as Record<string, Json>;
    for (const [name, definition] of Object.entries(declared)) {
      if (!definition || definition.addable !== true || type.some((c) => c.value === name)) continue;
      type.push(item(name, str(definition, 'description')));
      const offered = kindsOf(str(template, 'name'), name, definition, input.kinds);
      kinds[name] = offered.map((k) => item(str(k, 'name'),
        (k.default === true ? tr(input.fr, '(default) ', '(par défaut) ') : '') + str(k, 'description')));
    }
  }
  type.sort((a, b) => (a.value < b.value ? -1 : a.value > b.value ? 1 : 0));
  return { type, kinds };
}

/**
 * The branch a change is compared with: on a feature branch of a Git flow project, develop; otherwise
 * what origin's HEAD names, then main, master, develop - remote ones first, only refs that exist here,
 * never the branch itself. '' when there is none (a repository with one branch, no remote).
 */
export function bestBase(refs: string[], current: string, gitflow: boolean, remoteHead = ''): string {
  const exists = (r: string): boolean => refs.includes(r) && r !== current;
  const feature = /^(feature|bugfix|hotfix)\//.test(current);
  const order = [...(gitflow && feature ? ['origin/develop', 'develop'] : []), remoteHead,
    'origin/main', 'origin/master', 'main', 'master', 'origin/develop', 'develop'];
  return order.find((r) => r && exists(r)) ?? '';
}

/** `git for-each-ref --format=%(refname)` as the names a person types: main, origin/main, v1.2 (no origin/HEAD). */
export function refNames(output: string): string[] {
  const names: string[] = [];
  for (const line of output.split(/\r?\n/)) {
    const ref = line.trim();
    const name = ref.startsWith('refs/heads/') ? ref.substring(11) : ref.startsWith('refs/tags/') ? ref.substring(10)
      : ref.startsWith('refs/remotes/') && !ref.endsWith('/HEAD') ? ref.substring(13) : '';
    if (name && !names.includes(name)) names.push(name);
  }
  return names;
}

/** Why a base cannot be compared with, and what exists instead ('' when it exists). */
export function missingRef(ref: string, refs: string[], fr: boolean): string {
  const value = ref.trim();
  if (!value || refs.includes(value)) return '';
  const remote = value.includes('/') ? value.split('/')[0] : '';
  const remotes = new Set(refs.filter((r) => r.includes('/')).map((r) => r.split('/')[0]));
  const why = remote && !remotes.has(remote)
    ? tr(fr, ` (no remote '${remote}' here, or it was never fetched: git fetch ${remote})`,
      ` (pas de remote « ${remote} » ici, ou jamais récupéré : git fetch ${remote})`)
    : '';
  const branches = refs.filter((r) => !r.endsWith('/HEAD')).slice(0, 12).join(', ');
  return tr(fr, `'${value}' is not a branch of this repository${why}. `, `« ${value} » n'est pas une branche de ce dépôt${why}. `)
    + (branches ? tr(fr, `Choose one that exists: ${branches}.`, `Choisis-en une qui existe : ${branches}.`)
      : tr(fr, 'This repository has no other branch yet.', "Ce dépôt n'a pas encore d'autre branche."));
}

/** What every command form offers, for this project. */
export function choiceBook(commands: Command[], input: ChoiceInput): Book {
  const fr = input.fr;
  const all = components(input.spec);
  const ids = all.map((c) => str(c, 'id'));
  const view = (id: string): Json => input.snapshots[id] ?? {};
  const byComponent = (make: (id: string) => Choice[]): Record<string, Choice[]> =>
    Object.fromEntries(ids.map((id) => [id, make(id)]));
  const decisions = (id: string): Json[] => objects(view(id), 'decisions');
  const answered = (d: Json): string => (str(d, 'answer') ? tr(fr, ' (answered)', ' (répondue)') : '');
  const { type, kinds } = types(input);
  const hazards = objects(input.spec, 'hazards');
  const refs = input.refs.filter((r) => !r.endsWith('/HEAD'));
  const baseNote = refs.length
    ? tr(fr, `The branches and tags of this repository${input.base ? `; a merge request is compared with ${input.base}` : ''}.`,
      `Les branches et tags de ce dépôt${input.base ? ` ; une merge request se compare à ${input.base}` : ''}.`)
    : tr(fr, 'No other branch in this repository yet: nothing to compare with.', "Pas encore d'autre branche dans ce dépôt : rien à comparer.");

  const componentField = (command: string): Field => ({
    kind: 'select',
    choices: all.map((c) => item(str(c, 'id'), str(c, 'type'), command === 'component edit'
      ? Object.fromEntries(Object.entries(prefill('component edit', { component: str(c, 'id') }, '', input.snapshots[str(c, 'id')], c))
        .filter(([k]) => k !== 'component'))
      : undefined)),
    empty: tr(fr, 'No component yet: add one with component add.', 'Aucun composant : en ajouter un avec component add.'),
  });
  const perComponent = (lists: Record<string, Choice[]>, empty: string, kind: 'select' | 'suggest' = 'select'): Field =>
    ({ kind, by: 'component', lists, empty });
  const flagged = (id: string): boolean => decisions(id).some((d) => typeof d.template === 'boolean');

  const book: Book = {};
  for (const command of commands) {
    const name = label(command);
    const fields: Record<string, Field> = {};
    for (const param of command.parameters) {
      const p = param.name;
      let field: Field | undefined;
      if (p === 'component' && name !== 'component add') field = componentField(name);
      else if (name === 'decision make' && p === 'question') {
        field = perComponent(byComponent((id) => {
          const rows = decisions(id);
          const open = rows.filter((d) => !str(d, 'answer')).concat(rows.filter((d) => str(d, 'answer')));
          return [...open.map((d) => item(str(d, 'number'), str(d, 'question') + answered(d),
            { answer: str(d, 'answer') || str(d, 'proposed') || str(d, 'suggested') })),
          ...objects(view(id), 'questions').map((q) => item(str(q, 'key'), str(q, 'question') + answered(q),
            { answer: str(q, 'answer') || str(q, 'suggested') }))];
        }), tr(fr, 'No question on this component.', 'Aucune question sur ce composant.'));
      } else if (name === 'decision propose' && p === 'question') {
        field = perComponent(byComponent((id) => decisions(id).filter((d) => !str(d, 'answer'))
          .map((d) => item(str(d, 'number'), str(d, 'question'), { answer: str(d, 'proposed') || str(d, 'suggested') }))),
        tr(fr, 'Every question of this component is answered.', 'Toutes les questions de ce composant ont une réponse.'));
      } else if (name === 'decision accept' && p === 'number') {
        field = perComponent(byComponent((id) => decisions(id).filter((d) => str(d, 'proposed') && !str(d, 'answer'))
          .map((d) => item(str(d, 'number'), `${str(d, 'question')} → ${str(d, 'proposed')}`))),
        tr(fr, 'No AI proposal waiting on this component.', 'Aucune proposition IA en attente sur ce composant.'));
      } else if (name === 'question remove' && p === 'number') {
        field = perComponent(byComponent((id) => decisions(id)
          .filter((d) => !flagged(id) || d.template !== true)
          .map((d) => item(str(d, 'number'), str(d, 'question') + answered(d)))),
        tr(fr, "No question a person added: the template's questions stay (answer them with decision make).",
          'Aucune question ajoutée par une personne : celles du modèle restent (y répondre avec decision make).'));
        field.note = tr(fr, "Only a question a person added can be removed, with its answer; archvow refuses the template's own.",
          'Seule une question ajoutée par une personne peut être supprimée, avec sa réponse ; archvow refuse celles du modèle.');
      } else if ((name === 'question add' || name === 'decision add') && p === 'question') {
        field = { kind: 'note', by: 'component', notes: Object.fromEntries(ids.map((id) => [id,
          tr(fr, `No number to give: archvow numbers it - it becomes question ${decisions(id).length + 1} of ${id}.`,
            `Pas de numéro à donner : archvow la numérote - elle devient la question ${decisions(id).length + 1} de ${id}.`)])) };
      } else if ((name === 'assumption accept' || name === 'assumption reject') && p === 'number') {
        field = perComponent(byComponent((id) => objects(view(id), 'assumptions')
          .filter((a) => !['accepted', 'rejected'].includes(str(a, 'status')))
          .map((a) => item(str(a, 'number'), str(a, 'text')))),
        tr(fr, 'No assumption waiting for a review on this component.', 'Aucune hypothèse à revoir sur ce composant.'));
      } else if (name === 'example remove' && p === 'name') {
        field = perComponent(byComponent((id) => objects(view(id), 'examples').map((e) => item(str(e, 'name'), ''))),
          tr(fr, 'No business example on this component.', 'Aucun exemple métier sur ce composant.'));
      } else if ((name === 'hazard add' || name === 'hazard dismiss') && p === 'guide_word') {
        field = perComponent(byComponent((id) => {
          const rows = objects(view(id), 'safety');
          const open = rows.filter((q) => str(q, 'status') === 'open').concat(rows.filter((q) => str(q, 'status') !== 'open'));
          return open.map((q) => item(str(q, 'guide_word'), `S${str(q, 'number')} ${str(q, 'question')}`
            + (str(q, 'status') === 'open' ? '' : ` (${str(q, 'status')})`)));
        }), tr(fr, 'No safety question for this component.', 'Aucune question de sécurité pour ce composant.'));
      } else if (name === 'invariant add' && p === 'prevents') {
        field = perComponent(byComponent((id) => hazards.filter((h) => str(h, 'component') === id)
          .map((h) => item(str(h, 'id'), `${str(h, 'severity')}: ${str(h, 'description') || str(h, 'guide_word')}`))),
        tr(fr, 'No hazard declared on this component yet.', 'Aucun hazard déclaré sur ce composant.'));
      } else if (name === 'prompt' && p === 'task') {
        field = perComponent(byComponent((id) => objects(view(id), 'tasks').map((t) => item(str(t, 'task'),
          str(t, 'summary') + (t.done === true ? tr(fr, ' (done)', ' (fait)') : '')))),
        tr(fr, 'No task for this component.', 'Aucune tâche pour ce composant.'));
      } else if (name === 'hazard remove' && p === 'id') {
        field = { kind: 'select', choices: hazards.map((h) => item(str(h, 'id'), `${str(h, 'severity')}: ${str(h, 'description') || str(h, 'guide_word')}`)),
          empty: tr(fr, 'No hazard declared in this project.', 'Aucun hazard déclaré dans ce projet.') };
      } else if (name === 'invariant remove' && p === 'id') {
        field = { kind: 'select', choices: objects(input.spec, 'invariants').map((i) => item(str(i, 'id'), str(i, 'statement'))),
          empty: tr(fr, 'No invariant in this project.', 'Aucun invariant dans ce projet.') };
      } else if ((name === 'rule edit' || name === 'rule remove') && p === 'id') {
        field = { kind: 'select', choices: objects(input.spec, 'project_rules').map((r) => item(str(r, 'id'), str(r, 'text'),
          name === 'rule edit' ? { text: str(r, 'text') } : undefined)),
          empty: tr(fr, 'No rule written for this project.', 'Aucune règle écrite pour ce projet.') };
      } else if (name === 'dependency remove' && p === 'name') {
        field = { kind: 'select', choices: objects(input.spec, 'dependencies').map((d) => item(str(d, 'name'), str(d, 'supplier'))),
          empty: tr(fr, 'No dependency declared.', 'Aucune dépendance déclarée.') };
      } else if (name === 'template new' && p === 'extends') {
        field = { kind: 'suggest', choices: objects(input.catalog, 'templates').map((t) => item(str(t, 'name'), str(t, 'description'))),
          note: tr(fr, "Its settings are filled in below: change what the company wants different; what the base imposes cannot be changed.",
            "Ses réglages sont remplis ci-dessous : changer ce que l'entreprise veut autrement ; ce que la base impose ne se change pas.") };
      } else if (name === 'component add' && p === 'type') {
        field = { kind: 'select', choices: type, note: projectTemplates(input.spec).length
          ? tr(fr, `The types of ${projectTemplates(input.spec).join(', ')}, the template of this project.`,
            `Les types de ${projectTemplates(input.spec).join(', ')}, le modèle de ce projet.`)
          : tr(fr, 'The types of the installed templates.', 'Les types des modèles installés.') };
      } else if (name === 'component add' && p === 'kind') {
        field = { kind: 'select', by: 'type', lists: kinds,
          empty: tr(fr, 'This type has no kinds: leave it empty.', "Ce type n'a pas de sortes : laisser vide.") };
      } else if (p === 'base' || (name === 'changes' && (p === 'start' || p === 'end'))) {
        field = { kind: 'suggest', choices: refs.map((r) => item(r, '')), note: baseNote };
      }
      if (field) fields[p] = field;
    }
    if (Object.keys(fields).length) book[name] = fields;
  }
  return book;
}

/** The choices a field offers now: its list, or the one of the form's component / type. */
export function choicesNow(field: Field, values: Record<string, string>): Choice[] {
  if (!field.by) return field.choices ?? [];
  const key = (values[field.by] ?? '').trim();
  return (field.lists ?? {})[key] ?? [];
}
