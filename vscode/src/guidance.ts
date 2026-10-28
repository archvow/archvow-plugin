// What to do about a check diagnostic, in plain words: what it means, what to do, who does it, whether it
// blocks, and the archvow command its message names as a prefilled form (never run without a confirmation).
// The same rules as the IntelliJ plugin (ArchvowGuidance). Every code archvow documents (rules.md) and
// every message archvow's tests make it print (contract/cli/messages.json) is covered by guidance.test.ts.
import { Command, label, READ_ONLY } from './grammar';
import { tr } from './i18n';

export interface GuideCommand {
  /** The archvow command: "init", "decision make"... */
  name: string;
  /** Its form's fields by parameter name ("true" for a ticked box), as the message gives them. */
  values: Record<string, string>;
  /** The command as the message writes it. */
  text: string;
  /** The fields the message leaves to a person (<component>, "...", S<number>): left empty in the form. */
  missing: string[];
  /** A command that changes nothing (show, check, next...). */
  readOnly: boolean;
}

export interface Guidance {
  code: string;
  /** What the diagnostic means. */
  meaning: string;
  /** What to do about it. */
  todo: string;
  /** Who does it. */
  who: string;
  /** Whether it blocks the commit and CI. */
  blocking: string;
  /** What happens once it is done. */
  then: string;
  /** The archvow command the message names, for a prefilled form; null when it names none. */
  command: GuideCommand | null;
  /** The file to open, when the diagnostic is about one. */
  file: { path: string; line: number } | null;
}

// ------------------------------------------------------------------ archvow's rules table

/** The cells of a check code's row in archvow's rules.md (Code | Fails when | Fix), '' when absent. */
export function ruleRow(markdown: string, code: string): { when: string; fix: string } {
  for (const line of (markdown ?? '').split('\n')) {
    const m = new RegExp(`^\\s*\\|\\s*${code}\\s*\\|(.*)\\|\\s*$`).exec(line);
    if (m) {
      const cells = m[1].split(/(?<!\\)\|/).map((c) => clean(c.replace(/\\\|/g, '|')));
      return { when: cells[0] ?? '', fix: cells.slice(1).join(' ').trim() };
    }
  }
  return { when: '', fix: '' };
}

/** A cell as plain text: links as their words, the severity in brackets left to `blocking`. */
function clean(cell: string): string {
  return cell.replace(/\[([^\]]+)\]\([^)]*\)/g, '$1').replace(/^\s*\((?:a )?warning[^)]*\)\s*/i, '')
    .replace(/^\s*\((?:un )?avertissement[^)]*\)\s*/i, '').replace(/^\s*\(always on\)\s*/i, '')
    .replace(/^\s*\(toujours actif\)\s*/i, '').trim();
}

// ------------------------------------------------------------------ the commands a message names

const STOP_WORDS = new Set(['or', 'and', 'then', 'to', 'after', 'before', 'if', 'when', 'with', 'ou', 'et', 'puis',
  'pour', 'après', 'avant', 'si', 'quand', 'avec']);
const PLACEHOLDER = /<[^>]*>|\{[^}]*\}|^(\.\.\.|…)$/;

interface Token { text: string; quoted: boolean }

/** The words of a command written in a sentence, up to where the sentence goes on. */
export function commandTokens(text: string, wholeText = false): Token[] {
  const tokens: Token[] = [];
  let i = 0;
  while (i < text.length) {
    while (i < text.length && /\s/.test(text[i])) i++;
    if (i >= text.length) break;
    if (text[i] === '"') {
      let j = i + 1;
      let value = '';
      while (j < text.length && text[j] !== '"') {
        if (text[j] === '\\' && j + 1 < text.length) j++;
        value += text[j++];
      }
      tokens.push({ text: value, quoted: true });
      i = j + 1;
      if (!wholeText && i < text.length && /[,;:)]/.test(text[i])) break;
      if (!wholeText && i < text.length && text[i] === '.' && (i + 1 >= text.length || /\s/.test(text[i + 1]))) break;
      continue;
    }
    let j = i;
    while (j < text.length && !/\s/.test(text[j])) j++;
    let raw = text.slice(i, j);
    i = j;
    if (!wholeText) {
      if (raw.startsWith('(') || ['->', '→', '|', ';', '—', '-', '–'].includes(raw) || STOP_WORDS.has(raw.toLowerCase())) break;
      let end = false;
      if (/[,;:)]$/.test(raw)) { raw = raw.slice(0, -1); end = true; }
      else if (raw.endsWith('.') && !raw.endsWith('...') && raw.length > 1) { raw = raw.slice(0, -1); end = true; }
      if (raw) tokens.push({ text: raw, quoted: false });
      if (end) break;
    } else {
      tokens.push({ text: raw, quoted: false });
    }
  }
  return tokens;
}

/** The archvow commands a message names, in order, as prefilled forms. */
export function commandsIn(message: string, commands: Command[]): GuideCommand[] {
  const found: GuideCommand[] = [];
  const pattern = /(`?)\barchvow\s+/g;
  for (let m = pattern.exec(message ?? ''); m !== null; m = pattern.exec(message ?? '')) {
    const after = message.slice(m.index + m[0].length);
    const fenced = m[1] === '`';
    const close = fenced ? after.indexOf('`') : -1;
    const tokens = fenced && close >= 0 ? commandTokens(after.slice(0, close), true) : commandTokens(after);
    const words = tokens.map((t) => t.text);
    const command = commands.find((c) => c.path.length === 2 && c.path[0] === words[0] && c.path[1] === words[1])
      ?? commands.find((c) => c.path.length === 1 && c.path[0] === words[0]);
    if (!command || tokens.some((t, k) => k < command.path.length && t.quoted)) continue;
    found.push(formOf(command, tokens.slice(command.path.length)));
  }
  return found;
}

function formOf(command: Command, args: Token[]): GuideCommand {
  const values: Record<string, string> = {};
  const missing: string[] = [];
  const positional = command.parameters.filter((p) => p.flags.length === 0);
  const set = (param: Command['parameters'][number], token: Token): void => {
    const choice = (!token.quoted && token.text.includes('|')) || (param.choices.length > 0 && !param.choices.includes(token.text));
    if (PLACEHOLDER.test(token.text) || choice) { if (!missing.includes(param.name)) missing.push(param.name); return; }
    const append = param.append || param.multiToken;
    values[param.name] = append && values[param.name] ? values[param.name] + '\n' + token.text : token.text;
  };
  let next = 0;
  for (let k = 0; k < args.length; k++) {
    const token = args[k];
    const flagged = !token.quoted && token.text.startsWith('-')
      ? command.parameters.find((p) => p.flags.includes(token.text.split('=')[0])) : undefined;
    if (flagged) {
      if (flagged.boolean) { values[flagged.name] = 'true'; continue; }
      const inline = token.text.includes('=') ? token.text.slice(token.text.indexOf('=') + 1) : undefined;
      const value = inline !== undefined ? { text: inline, quoted: false } : args[++k];
      if (value) set(flagged, value);
      continue;
    }
    if (!token.quoted && token.text.startsWith('-')) continue; // an option this archvow does not have
    const param = positional[next];
    if (!param) continue;
    set(param, token);
    if (!param.multiToken) next++;
  }
  for (const p of command.parameters) if (p.required && !(p.name in values) && !missing.includes(p.name)) missing.push(p.name);
  const text = ['archvow', ...command.path, ...args.map((t) => (t.quoted || /\s/.test(t.text) ? `"${t.text}"` : t.text))].join(' ');
  return { name: label(command), values, text, missing, readOnly: READ_ONLY.has(command.path[0]) };
}

/** The command to offer first: one that changes something (the fix), else the first. */
export function mainCommand(found: GuideCommand[]): GuideCommand | null {
  return found.find((c) => !c.readOnly) ?? found[0] ?? null;
}

// ------------------------------------------------------------------ who does it

/** The commands only a person runs (archvow's agent.PERSON_ONLY): an AI agent is stopped (ARCH054). */
const PERSON_ONLY = ['component', 'decision make', 'decision add', 'decision accept', 'question remove', 'assumption accept',
  'assumption reject', 'hazard', 'invariant', 'rule', 'column', 'dependency', 'brief', 'example', 'specify', 'freeze'];
const ARCHITECT_CODES = new Set(['ARCH012', 'ARCH020', 'ARCH023', 'ARCH024', 'ARCH040', 'ARCH041']);
const PERSON_CODES = new Set(['ARCH009', 'ARCH014', 'ARCH036', 'ARCH039', 'ARCH043', 'ARCH051', 'ARCH052', 'ARCH054']);

type Who = 'architect' | 'person' | 'anyone' | 'developer' | 'you';

function whoOf(code: string, command: GuideCommand | null): Who {
  if (ARCHITECT_CODES.has(code)) return 'architect';
  if (command) {
    if (command.name === 'init') return 'architect';
    if (command.readOnly) return PERSON_CODES.has(code) ? 'person' : 'anyone';
    if (PERSON_ONLY.some((p) => command.name === p || command.name.startsWith(p + ' '))) return 'person';
    return 'you';
  }
  return PERSON_CODES.has(code) ? 'person' : 'developer';
}

function whoText(who: Who, fr: boolean): string {
  switch (who) {
    case 'architect': return tr(fr, 'An architect: it changes the guards of the whole project.', "Un architecte : cela change les garde-fous de tout le projet.");
    case 'person': return tr(fr, 'A person, here or in their own terminal - not an AI agent: it decides or changes the spec.',
      "Une personne, ici ou dans son propre terminal - pas un agent IA : cela décide ou change la spec.");
    case 'anyone': return tr(fr, 'Anyone: the command changes nothing.', 'Tout le monde : la commande ne change rien.');
    case 'you': return tr(fr, 'You (or the AI, with your review).', "Vous (ou l'IA, sous votre relecture).");
    default: return tr(fr, 'Whoever writes the code: you, or the AI with your review.', "Celui qui écrit le code : vous, ou l'IA sous votre relecture.");
  }
}

// ------------------------------------------------------------------ the file to open

/** Codes about what a vow says: their file is worth opening. Other vows are archvow's, not to edit. */
const VOW_CODES = new Set(['ARCH009', 'ARCH014', 'ARCH036', 'ARCH050']);

/** The file a diagnostic is about, to open: "a.py (and 3 more)" is a.py; a vow only for a vow's own codes. */
export function fileOf(code: string, file: string, line: number): { path: string; line: number } | null {
  const path = file.replace(/\s+\(and \d+ more\)\s*$/, '').trim();
  if (!path || (path.startsWith('.archvow/') && path.endsWith('.vow') && !VOW_CODES.has(code))) return null;
  return { path, line: Math.max(0, line) };
}

// ------------------------------------------------------------------ the guidance

/** What to do about one diagnostic. `rules` is archvow's rules.md (rules.fr.md in French). */
export function guide(violation: { code?: string; severity?: string; message?: string; file?: string; line?: number },
  rules: string, commands: Command[], fr: boolean): Guidance {
  const code = String(violation.code ?? '').toUpperCase();
  const message = String(violation.message ?? '');
  const warning = String(violation.severity ?? '').toLowerCase() === 'warning';
  const row = ruleRow(rules, code);
  const command = personal(code, mainCommand(commandsIn(message, commands)));
  const file = fileOf(code, String(violation.file ?? ''), Number(violation.line) || 0);
  const base: Guidance = {
    code,
    meaning: row.when || tr(fr, 'archvow found this while checking the project (see the message above).',
      "archvow a trouvé ceci en vérifiant le projet (voir le message ci-dessus)."),
    todo: row.fix || (command ? tr(fr, 'Run the command the message gives.', 'Lancez la commande que donne le message.')
      : file ? tr(fr, 'Open the file and fix what the message says.', 'Ouvrez le fichier et corrigez ce que dit le message.')
        : tr(fr, 'Do what the message says.', 'Faites ce que dit le message.')),
    who: whoText(whoOf(code, command), fr),
    blocking: warning
      ? tr(fr, 'Does not block: a warning - the commit and CI pass. Some warnings become errors in mode strict or with check --complete.',
        'Non bloquant : un avertissement - le commit et la CI passent. Certains avertissements deviennent des erreurs en mode strict ou avec check --complete.')
      : tr(fr, 'Blocks: archvow check fails until it is fixed - the Git hook refuses the commit and CI fails.',
        "Bloquant : archvow check échoue tant que ce n'est pas corrigé - le hook Git refuse le commit et la CI échoue."),
    then: tr(fr, 'Then click Check (↻): the item disappears once it is fixed.', 'Ensuite cliquez sur Vérifier (↻) : l\'élément disparaît une fois corrigé.'),
    command,
    file,
  };
  return { ...base, ...specific(code, message, fr, command) };
}

/** In the IDE a person treats it: the decision the AI would propose (ARCH033), the person makes. */
function personal(code: string, command: GuideCommand | null): GuideCommand | null {
  if (code !== 'ARCH033' || !command || command.name !== 'decision propose') return command;
  return { ...command, name: 'decision make', text: command.text.replace('archvow decision propose', 'archvow decision make') };
}

/** Where archvow's rules table is not enough: the codes that confused people, by message variant. */
function specific(code: string, message: string, fr: boolean, command: GuideCommand | null): Partial<Guidance> {
  if (code === 'ARCH012') {
    const m = /says ([\w.-]+)@([\d.]+), installed is [\w.-]+@([\d.]+)/.exec(message);
    const [template, old, now] = m ? [m[1], m[2], m[3]] : ['the template', 'an older version', 'a newer one'];
    return {
      meaning: tr(fr, `The project was set up with ${template} ${old}; the archvow installed here brings ${now}. archvow's own files in the project (CI job, Git hooks, AGENTS.md, ruff and test settings) are still those of ${old}.`,
        `Le projet a été créé avec ${template} ${old} ; l'archvow installé ici apporte ${now}. Les fichiers d'archvow dans le projet (job CI, hooks Git, AGENTS.md, réglages ruff et tests) sont encore ceux de ${old}.`),
      todo: tr(fr, `Upgrade the project to ${now}: run the prepared command${command ? ` (${command.text})` : ''}. It rewrites archvow's files with the new template and keeps your spec and your answers. Then review git diff - keep your own lines if you had edited a generated file such as the README - and commit.`,
        `Mettez le projet en ${now} : lancez la commande préparée${command ? ` (${command.text})` : ''}. Elle réécrit les fichiers d'archvow avec le nouveau template et garde votre spec et vos réponses. Relisez ensuite git diff - gardez vos propres lignes si vous aviez modifié un fichier généré comme le README - et commitez.`),
      then: tr(fr, 'Then commit: the warning disappears.', 'Ensuite commitez : l\'avertissement disparaît.'),
    };
  }
  if (code === 'ARCH053') {
    const tool = /written by ([\w.-]+)/.exec(message)?.[1] ?? /écrit par ([\w.-]+)/.exec(message)?.[1] ?? tr(fr, 'the AI tool', "l'outil IA");
    const meaningBase = tr(fr, `${tool} wrote these files (their header says @assisted ${tool}). Before an agent's code is committed, archvow checks that the agent ran protected: in its sandbox, without an option that lets it act without asking (YOLO).`,
      `${tool} a écrit ces fichiers (leur en-tête dit @assisted ${tool}). Avant qu'un code d'agent soit commité, archvow vérifie que l'agent a tourné protégé : dans son bac à sable, sans option qui le laisse agir sans demander (YOLO).`);
    if (/cannot see|ne peut pas voir/.test(message)) {
      const where = /cannot see (in [^:]+?)(?: \(|:)/.exec(message)?.[1] ?? '';
      return {
        meaning: meaningBase + ' ' + tr(fr, `archvow cannot see ${tool}${where ? ' ' + where : ''}, so nobody checked how it ran.`,
          `archvow ne peut pas voir ${tool}${where ? ' ' + where : ''} : personne n'a vérifié comment il a tourné.`),
        todo: tr(fr, `Review what ${tool} wrote, then commit it. This warning only concerns files not committed yet: it disappears with the commit, and it never blocks. `
          + (where ? `For archvow to check the agent next time, use ${tool} where archvow sees it (archvow doctor says where).` : `archvow cannot check ${tool} anywhere: review its code with care before each commit.`),
        `Relisez ce que ${tool} a écrit, puis commitez. Cet avertissement ne concerne que les fichiers pas encore commités : il disparaît avec le commit et ne bloque jamais. `
          + (where ? `Pour qu'archvow vérifie l'agent la prochaine fois, utilisez ${tool} là où archvow le voit (archvow doctor dit où).` : `archvow ne peut vérifier ${tool} nulle part : relisez son code avec soin avant chaque commit.`)),
        who: tr(fr, 'You, who commit the code.', 'Vous, qui commitez le code.'),
        then: tr(fr, 'Then commit: the warning disappears.', "Ensuite commitez : l'avertissement disparaît."),
      };
    }
    if (/never ran archvow|n'a jamais lancé archvow/.test(message)) {
      return {
        meaning: meaningBase + ' ' + tr(fr, `${tool} never ran archvow in this project, so nobody checked how it ran.`, `${tool} n'a jamais lancé archvow dans ce projet : personne n'a vérifié comment il a tourné.`),
        todo: tr(fr, `Ask ${tool} to run archvow check here, in its sandbox; then commit again. The commit message then records how it ran (an Archvow-Agent line).`,
          `Demandez à ${tool} de lancer archvow check ici, dans son bac à sable ; puis commitez à nouveau. Le message de commit note alors comment il a tourné (une ligne Archvow-Agent).`),
        who: tr(fr, `You, then ${tool}.`, `Vous, puis ${tool}.`),
        then: tr(fr, 'Then commit again.', 'Ensuite commitez à nouveau.'),
      };
    }
    if (/refused to run inside it|a refusé de tourner/.test(message)) {
      return {
        meaning: meaningBase + ' ' + tr(fr, `The last time ${tool} ran archvow here, it was not protected, and archvow stopped it.`, `La dernière fois que ${tool} a lancé archvow ici, il n'était pas protégé, et archvow l'a arrêté.`),
        todo: tr(fr, `Start ${tool} protected - its sandbox on, without the option that lets it act without asking - let it run archvow check, then commit again. archvow doctor says what archvow sees.`,
          `Démarrez ${tool} protégé - bac à sable actif, sans l'option qui le laisse agir sans demander - laissez-le lancer archvow check, puis commitez à nouveau. archvow doctor dit ce qu'archvow voit.`),
        who: tr(fr, `You, who start ${tool}.`, `Vous, qui démarrez ${tool}.`),
        then: tr(fr, 'Then commit again.', 'Ensuite commitez à nouveau.'),
      };
    }
    if (/no Archvow-Agent line|aucune ligne Archvow-Agent|pas de ligne Archvow-Agent/.test(message)) {
      return {
        meaning: tr(fr, `A commit of this branch changes code ${tool} wrote, and its message has no Archvow-Agent line: it was made without the Git hooks (git commit --no-verify?), so nobody checked how ${tool} ran.`,
          `Un commit de cette branche modifie du code écrit par ${tool}, et son message n'a pas de ligne Archvow-Agent : il a été fait sans les hooks Git (git commit --no-verify ?), personne n'a vérifié comment ${tool} a tourné.`),
        todo: tr(fr, 'Redo the commit with the hooks on: git reset --soft HEAD~1, then git commit. If the hooks never run on your machine: git config core.hooksPath .githooks.',
          'Refaites le commit avec les hooks : git reset --soft HEAD~1, puis git commit. Si les hooks ne tournent jamais sur votre poste : git config core.hooksPath .githooks.'),
        who: tr(fr, 'You, who made the commit.', 'Vous, qui avez fait le commit.'),
        then: tr(fr, 'Then push again: CI checks the branch again.', 'Ensuite poussez à nouveau : la CI revérifie la branche.'),
      };
    }
    if (/YOLO|outside a sandbox|hors bac à sable/.test(message)) {
      return {
        meaning: tr(fr, `A commit of this branch changes code ${tool} wrote while it was not protected (outside its sandbox, or in YOLO mode).`,
          `Un commit de cette branche modifie du code que ${tool} a écrit sans être protégé (hors de son bac à sable, ou en mode YOLO).`),
        todo: tr(fr, `Have that code reviewed closely, or redo it with ${tool} protected; the architects decide whether the branch can be merged.`,
          `Faites relire ce code de près, ou refaites-le avec ${tool} protégé ; les architectes décident si la branche peut être fusionnée.`),
        who: tr(fr, 'You, then the architects.', 'Vous, puis les architectes.'),
      };
    }
  }
  if (code === 'ARCH014') {
    return {
      todo: tr(fr, 'This entry of a vow was edited by hand, and archvow writes it. Undo the hand edit (git checkout -- the file, or Edit back), then make the change with the commands: Edit spec, + Question, + Rule...',
        "Cette entrée d'un vow a été modifiée à la main, et c'est archvow qui l'écrit. Annulez la modification (git checkout -- le fichier), puis faites le changement avec les commandes : Modifier la spec, + Question, + Règle..."),
    };
  }
  if (code === 'ARCH035' || code === 'ARCH038') {
    if (/^\s*\d+\s/.test(message)) {
      return {
        todo: code === 'ARCH035'
          ? tr(fr, "Open each component's Safety view: for every open question, add the hazard it could cause, or mark it not applicable with a reason. archvow check --all lists them one by one.",
            "Ouvrez la vue Sécurité de chaque composant : pour chaque question ouverte, ajoutez le danger qu'elle pourrait causer, ou déclarez-la non applicable avec une raison. archvow check --all les liste une par une.")
          : tr(fr, 'Mark the tests: @pytest.mark.decision(<n>) on the test of each decision, @pytest.mark.rule("R<n>") for a rule. archvow check --all lists them one by one.',
            'Marquez les tests : @pytest.mark.decision(<n>) sur le test de chaque décision, @pytest.mark.rule("R<n>") pour une règle. archvow check --all les liste un par un.'),
      };
    }
  }
  return {};
}
