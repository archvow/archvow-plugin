// A command's result as people read it: the same reading as the IntelliJ workspace's
// ArchvowResultFormatter (check, full check, audit, doctor, next, catalog, prompt, init, any other JSON).
// HTML fragments with CSS classes; the page's style gives them the editor's colours. The original
// output stays one click away (Raw output).
import { tr } from './i18n';
import { Json, objects, str } from './workItems';

export function esc(text: unknown): string {
  return String(text ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

const cut = (text: string, n: number): string => (text.length <= n ? text : text.substring(0, n) + '…');
const asText = (v: unknown): string => (v !== null && typeof v === 'object' ? JSON.stringify(v) : String(v ?? ''));
const num = (o: Json | null, k: string): number => Number(o?.[k]) || 0;

type Tone = 'good' | 'bad' | 'warn' | 'blue';

class Out {
  html = '';
  heading(title: string, tone: Tone): void { this.html += `<h3 class="tone-${tone}">${esc(title)}</h3>`; }
  section(label: string): void { this.html += `<h4>${esc(label)}</h4>`; }
  chips(chips: [string, Tone][]): void {
    this.html += '<div class="chips">' + chips.map(([t, tone]) => `<span class="chip tone-${tone}">${esc(t)}</span>`).join('') + '</div>';
  }
  paragraph(text: string, tone?: Tone | 'muted'): void { this.html += `<p class="${tone ? 'tone-' + tone : ''}">${esc(text)}</p>`; }
  card(title: string, body: string, tone: Tone, fields: [string, string][] = [], extraHtml = ''): void {
    this.html += `<div class="card tone-border-${tone}"><div class="card-title">${esc(title)}</div>`
      + (body ? `<div class="card-body">${esc(body)}</div>` : '')
      + fields.map(([k, v]) => `<div class="field"><span class="key">${esc(k)}</span> ${esc(v)}</div>`).join('')
      + extraHtml + '</div>';
  }
  more(count: number, fr: boolean): void {
    this.paragraph(tr(fr, `${count} more in Raw output`, `${count} de plus dans Sortie brute`), 'muted');
  }
}

/** The JSON of an output: the whole text, else a list or an object inside it. */
export function parseResult(raw: string): unknown {
  const text = raw.trim();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch { /* a message before or after the JSON */ }
  for (const [open, close] of [['[', ']'], ['{', '}']]) {
    const start = text.indexOf(open);
    const end = text.lastIndexOf(close);
    if (start >= 0 && end > start) {
      try {
        return JSON.parse(text.substring(start, end + 1));
      } catch { /* not JSON */ }
    }
  }
  return null;
}

/** What the workspace says about an output that looks like a known failure, or ''. */
export function advice(output: string, fr: boolean): string {
  const lower = output.toLowerCase();
  if (lower.includes('no module named archvow')) {
    return tr(fr, 'Archvow is not installed in this Python environment.', "Archvow n'est pas installé dans ce Python. Installe-le dans cet environnement.");
  }
  if (lower.includes('was not found') || lower.includes('no such file or directory')) {
    return tr(fr, 'Executable not found. Check the Python path in the archvow settings.', "L'exécutable est introuvable. Vérifie le chemin Python dans les paramètres archvow.");
  }
  if (lower.includes('permission denied') || lower.includes('access denied')) {
    return tr(fr, 'Access denied: check directory and executable permissions.', 'Accès refusé : vérifie les permissions du dossier ou de l\'exécutable.');
  }
  if (/\barch05[1-4]\b/.test(lower)) {
    return tr(fr, 'archvow stopped: it sees an AI agent (see the message). A person runs archvow doctor to see what archvow sees.',
      "archvow s'est arrêté : il voit un agent IA (voir le message). Une personne lance archvow doctor pour voir ce qu'archvow voit.");
  }
  return '';
}

/** The readable result of `archvow <args>`: `label` is the workspace's name for the command. */
export function renderResult(label: string, exitCode: number, stdout: string, stderr: string, cancelled: boolean, fr: boolean): string {
  const raw = stdout.trim();
  const key = label.toLowerCase().trim();
  const parsed = parseResult(raw);
  const out = new Out();
  let reported = false;
  if (!cancelled && key === 'prompt' && raw && exitCode === 0) {
    out.heading(tr(fr, 'AI handoff · task prompt', "Passage à l'IA · instructions de tâche"), 'blue');
    out.paragraph(tr(fr, 'This is the actual archvow prompt. Copy it to your AI assistant; no decision is made automatically.',
      "Ce sont les instructions réelles d'archvow. Copie-les vers ton assistant IA ; aucune décision n'est prise automatiquement."), 'muted');
    out.html += `<pre class="prompt">${esc(cut(raw, 200000))}</pre>`;
    reported = true;
  }
  if (!cancelled && !reported && parsed !== null) {
    if ((key === 'check' || key === 'full check' || key.startsWith('check ')) && isObject(parsed) && 'violations' in parsed) {
      check(out, parsed, fr, key);
      reported = true;
    } else if (key.startsWith('audit') && Array.isArray(parsed)) {
      audit(out, parsed, fr);
      reported = true;
    } else if (key.startsWith('doctor') && Array.isArray(parsed)) {
      doctor(out, parsed, fr);
      reported = true;
    } else if (key.startsWith('next') && (Array.isArray(parsed) || isObject(parsed))) {
      next(out, parsed as Json[] | Json, fr);
      reported = true;
    } else if (key === 'catalog' && isObject(parsed)) {
      catalog(out, parsed, fr);
      reported = true;
    }
  }
  if (!reported && key === 'init' && parsed === null && exitCode === 0 && !cancelled) {
    out.heading(tr(fr, 'Project initialized', 'Projet initialisé'), 'good');
    out.section(tr(fr, 'archvow response', "Réponse d'archvow"));
    raw.split(/\r?\n/).filter((l) => l.trim()).slice(0, 66).forEach((line) =>
      out.paragraph(line.trim(), line.includes('Next') || line.includes('Étape') ? 'blue' : line.includes('warn') || line.includes('Review') ? 'warn' : undefined));
    reported = true;
  }
  if (!reported) {
    const success = exitCode === 0 && !cancelled;
    const state = success ? tr(fr, 'Completed', 'Terminé') : cancelled ? tr(fr, 'Cancelled or timed out', 'Annulée ou délai dépassé')
      : tr(fr, 'Command failed', 'Échec de la commande');
    out.heading(state + ' · ' + (label || 'archvow'), success ? 'good' : 'bad');
    const all = (raw + '\n' + stderr).trim();
    if (!all) {
      out.paragraph(success ? tr(fr, 'No output: the command succeeded without a message.', 'Aucune sortie : la commande a réussi sans message.')
        : tr(fr, 'No output available. Check the Python settings and the archvow log.', 'Aucune sortie disponible. Vérifie la configuration Python et le journal archvow.'), 'muted');
    } else {
      const tip = advice(all, fr);
      if (tip) out.paragraph(tip, 'bad');
      if (parsed !== null && typeof parsed === 'object') generic(out, parsed, fr);
      else if (raw) out.html += `<pre>${esc(cut(raw, 16000))}</pre>`;
    }
  }
  if (stderr.trim()) out.html += `<pre class="stderr">${esc(cut(stderr.trim(), 16000))}</pre>`;
  out.paragraph(tr(fr, 'Exit code: ', 'Code retour : ') + exitCode + ' · '
    + tr(fr, "Original output available via 'Raw output'", 'Sortie originale accessible via « Sortie brute »'), 'muted');
  return out.html;
}

function isObject(v: unknown): v is Json {
  return v !== null && typeof v === 'object' && !Array.isArray(v);
}

function check(out: Out, report: Json, fr: boolean, label: string): void {
  const errors = num(report, 'errors');
  const warnings = num(report, 'warnings');
  const ok = report.ok === true;
  out.heading((label === 'full check' ? tr(fr, 'Full check', 'Contrôle complet') : tr(fr, 'Check & Gates', 'Vérifications et validations'))
    + ' · ' + (ok ? tr(fr, 'Passed', 'Validé') : tr(fr, 'Action required', 'Action requise')), ok ? 'good' : 'bad');
  out.chips([[errors + tr(fr, ' errors', ' erreurs'), errors > 0 ? 'bad' : 'good'],
    [warnings + tr(fr, ' warnings', ' avertissements'), warnings > 0 ? 'warn' : 'good'],
    [(Array.isArray(report.components) ? report.components.length : 0) + tr(fr, ' components', ' composants'), 'blue']]);
  const violations = objects(report, 'violations');
  out.section(tr(fr, 'Diagnostics', 'Diagnostics') + ` (${violations.length})`);
  if (violations.length === 0) out.paragraph(tr(fr, 'No violations detected.', 'Aucune violation détectée.'), 'muted');
  violations.slice(0, 35).forEach((v) => {
    const error = str(v, 'severity').toLowerCase() === 'error';
    const status = error ? tr(fr, 'ERROR', 'ERREUR') : tr(fr, 'WARNING', 'AVERTISSEMENT');
    const file = str(v, 'file');
    out.card(str(v, 'code') ? str(v, 'code') + ' · ' + status : status, str(v, 'message'), error ? 'bad' : 'warn', [],
      file ? locationHtml(file, num(v, 'line')) : '');
  });
  if (violations.length > 35) out.more(violations.length - 35, fr);
  if (isObject(report.safety) && 'total' in report.safety) {
    out.section(tr(fr, 'Safety', 'Sécurité'));
    out.paragraph(`${num(report.safety, 'answered')} / ${num(report.safety, 'total')}` + tr(fr, ' questions answered', ' questions traitées'));
  }
  if (isObject(report.budget)) budget(out, report.budget, fr);
}

function locationHtml(path: string, line: number): string {
  // The webview expands this marker using its *actual* filesBlock() renderer.
  // That guarantees the same folder/file SVGs and spacing as a command form's Files preview.
  return `<div class="check-files-marker" data-check-path="${encodeURIComponent(path)}" data-check-line="${line}"></div>`;
}

function audit(out: Out, entries: Json[], fr: boolean): void {
  const rows = entries.filter(isObject);
  const flagged = rows.filter((r) => Array.isArray(r.flags) && r.flags.length > 0).length;
  out.heading(tr(fr, 'Audit · Human decisions & traceability', 'Audit · Décisions humaines et traçabilité'), flagged === 0 ? 'good' : 'warn');
  out.chips([[rows.length + tr(fr, ' records', ' enregistrements'), 'blue'], [flagged + tr(fr, ' to review', ' à vérifier'), flagged > 0 ? 'warn' : 'good']]);
  if (rows.length === 0) {
    out.paragraph(tr(fr, 'No recorded decisions, assumptions, hazards or invariants yet.', 'Aucune décision, hypothèse, hazard ou invariant enregistré.'), 'muted');
    return;
  }
  if (flagged > 0) out.section(tr(fr, 'Records to review', 'Enregistrements à vérifier'));
  let rendered = 0;
  for (const pass of [0, 1]) {
    if (pass === 1 && flagged > 0 && flagged < rows.length) out.section(tr(fr, 'Other records', 'Autres enregistrements'));
    for (const row of rows) {
      const review = Array.isArray(row.flags) && row.flags.length > 0;
      if (review !== (pass === 0)) continue;
      const [question, answer] = str(row, 'text').split(/\s+->\s+/, 2);
      const fields: [string, string][] = [];
      if (answer !== undefined) fields.push([tr(fr, 'Recorded answer', 'Réponse enregistrée'), answer]);
      const date = str(row, 'on');
      fields.push([tr(fr, 'Recorded by', 'Enregistré par'), (str(row, 'by') || tr(fr, 'Unknown author', 'Auteur inconnu')) + (date.length >= 10 ? ' · ' + date.substring(0, 10) : '')]);
      fields.push(['Git', isObject(row.commit) ? str(row.commit, 'commit') + ' · ' + str(row.commit, 'subject') : tr(fr, 'Not committed', 'Non commité')]);
      for (const f of Array.isArray(row.flags) ? row.flags : []) fields.push([tr(fr, 'Review', 'À vérifier'), asText(f)]);
      out.card(str(row, 'kind') + ' · ' + str(row, 'ref'), question, review ? 'warn' : 'good', fields);
      if (++rendered === 50) {
        out.more(rows.length - rendered, fr);
        return;
      }
    }
  }
}

function doctor(out: Out, findings: Json[], fr: boolean): void {
  const rows = findings.filter(isObject);
  const fails = rows.filter((r) => str(r, 'status').toLowerCase() === 'fail').length;
  const warns = rows.filter((r) => str(r, 'status').toLowerCase() === 'warn').length;
  out.heading(tr(fr, 'Doctor · Environment diagnostics', "Doctor · Diagnostic de l'environnement"), fails > 0 ? 'bad' : warns > 0 ? 'warn' : 'good');
  out.chips([[fails + tr(fr, ' failures', ' échecs'), fails > 0 ? 'bad' : 'good'], [warns + tr(fr, ' warnings', ' avertissements'), warns > 0 ? 'warn' : 'good'],
    [(rows.length - fails - warns) + ' OK', 'good']]);
  if (rows.length === 0) {
    out.paragraph(tr(fr, 'No environment checks returned.', "Aucun contrôle d'environnement renvoyé."), 'muted');
    return;
  }
  for (const level of ['fail', 'warn', 'ok']) {
    const these = rows.filter((r) => str(r, 'status').toLowerCase() === level);
    if (these.length === 0) continue;
    out.section(level === 'fail' ? tr(fr, 'To fix', 'À corriger') : level === 'warn' ? tr(fr, 'Warnings', 'Avertissements') : tr(fr, 'Working correctly', 'Fonctionne correctement'));
    for (const f of these) {
      out.card(str(f, 'check') + ' · ' + level.toUpperCase(), str(f, 'detail'), level === 'fail' ? 'bad' : level === 'warn' ? 'warn' : 'good',
        str(f, 'fix') ? [[tr(fr, 'Suggested fix', 'Correction proposée'), str(f, 'fix')]] : []);
    }
  }
}

function next(out: Out, json: Json[] | Json, fr: boolean): void {
  const components = Array.isArray(json) ? json.filter(isObject) : objects(json, 'components');
  const budgetInfo = Array.isArray(json) ? null : json.budget;
  const ready = components.filter((c) => str(c, 'readiness').toLowerCase().startsWith('ready')).length;
  const blocked = components.filter((c) => str(c, 'readiness').toLowerCase().startsWith('blocked')).length;
  out.heading(tr(fr, 'Next · What to work on', 'Next · Prochaines actions'), blocked > 0 ? 'warn' : 'blue');
  out.chips([[components.length + tr(fr, ' components', ' composants'), 'blue'], [ready + tr(fr, ' ready', ' prêts'), 'good'],
    [blocked + tr(fr, ' blocked', ' bloqués'), blocked > 0 ? 'warn' : 'good']]);
  if (components.length === 0) {
    out.paragraph(tr(fr, "Nothing planned. Add a component using 'component add'.", 'Aucun composant planifié. Utilise « component add » pour en ajouter un.'), 'muted');
  }
  components.slice(0, 45).forEach((c) => {
    const readiness = str(c, 'readiness');
    const level = readiness.toLowerCase();
    const fields: [string, string][] = [];
    if ('started' in c) fields.push([tr(fr, 'Started', 'Démarré'), str(c, 'started')]);
    for (const note of Array.isArray(c.notes) ? c.notes : []) fields.push([tr(fr, 'Attention', 'Attention'), asText(note)]);
    if ('tokens' in c) fields.push([tr(fr, 'Spec size', 'Taille de la spec'), str(c, 'tokens') + ' tokens']);
    if (level.startsWith('blocked')) fields.push([tr(fr, 'Suggested command', 'Commande suggérée'), 'archvow specify ' + str(c, 'component')]);
    out.card(str(c, 'component') + ' · ' + readiness, str(c, 'type'), level.startsWith('ready') ? 'good' : level.startsWith('blocked') ? 'warn' : 'blue', fields);
  });
  if (components.length > 45) out.more(components.length - 45, fr);
  if (isObject(budgetInfo)) budget(out, budgetInfo, fr);
}

function budget(out: Out, b: Json, fr: boolean): void {
  out.section(tr(fr, 'Change budget', 'Budget de changement'));
  const fields: [string, string][] = [];
  if (str(b, 'base')) fields.push([tr(fr, 'Compared with', 'Comparé à'), str(b, 'base')]);
  if ('files' in b) fields.push([tr(fr, 'Files', 'Fichiers'), str(b, 'files') + ' / ' + str(b, 'max_files')]);
  if ('added_lines' in b) fields.push([tr(fr, 'Lines added', 'Lignes ajoutées'), str(b, 'added_lines') + ' / ' + str(b, 'max_added_lines')]);
  out.card(tr(fr, 'Change budget', 'Budget de changement'), '', b.full === true ? 'warn' : 'blue', fields);
  if (b.full === true) {
    out.paragraph(tr(fr, 'Change budget reached: commit/review before continuing.', 'Budget atteint : valider et revoir les modifications avant de continuer.'), 'warn');
  }
}

function catalog(out: Out, data: Json, fr: boolean): void {
  const templates = objects(data, 'templates');
  out.heading(tr(fr, 'Template catalog', 'Catalogue de modèles'), 'blue');
  out.chips([[templates.length + tr(fr, ' templates', ' modèles'), 'blue']]);
  if (templates.length === 0) {
    out.paragraph(tr(fr, 'No templates found. Configure ARCHVOW_TEMPLATES if using company packs.',
      "Aucun modèle trouvé. Configure ARCHVOW_TEMPLATES pour les modèles d'entreprise."), 'muted');
    return;
  }
  templates.slice(0, 25).forEach((t) => out.card(str(t, 'name'), str(t, 'description'), 'blue', [
    [tr(fr, 'Version', 'Version'), str(t, 'version')],
    [tr(fr, 'Language / Type', 'Langue / Type'), str(t, 'language') + ' · ' + str(t, 'kind')],
    [tr(fr, 'Architecture', 'Architecture'), str(t, 'style')],
    [tr(fr, 'Component types', 'Types de composants'), isObject(t.component_types) ? Object.keys(t.component_types).join(', ') : ''],
    [tr(fr, 'Initialization questions', "Questions d'initialisation"), String(objects(t, 'questions').length)]]));
  out.paragraph(tr(fr, 'To answer a template\'s questions, open Commands → init.', "Pour répondre aux questions d'un modèle, ouvre Commandes → init."), 'muted');
}

// no heading of its own: the card around it already says "Command result" (it said it twice, 0.12.1)
function generic(out: Out, value: unknown, fr: boolean): void {
  if (Array.isArray(value)) {
    out.chips([[value.length + tr(fr, ' items', ' éléments'), 'blue']]);
    value.slice(0, 45).forEach((el, i) => (isObject(el) ? fields(out, el, tr(fr, 'Item', 'Élément') + ' ' + (i + 1), fr, 0)
      : out.card(String(i + 1), asText(el), 'blue')));
    if (value.length > 45) out.more(value.length - 45, fr);
  } else if (isObject(value)) {
    fields(out, value, tr(fr, 'Details', 'Détails'), fr, 0);
  } else {
    out.paragraph(asText(value));
  }
}

function fields(out: Out, object: Json, title: string, fr: boolean, depth: number): void {
  if (depth > 3) {
    out.paragraph(tr(fr, 'Additional details in Raw output', 'Informations supplémentaires dans Sortie brute'), 'muted');
    return;
  }
  const plain: [string, string][] = [];
  const nested: [string, unknown][] = [];
  Object.entries(object).slice(0, 45).forEach(([k, v]) => (v !== null && typeof v === 'object' ? nested.push([k, v]) : plain.push([k, cut(asText(v), 600)])));
  out.card(title, '', 'blue', plain);
  for (const [name, child] of nested) {
    if (Array.isArray(child)) {
      out.section(`${name} (${child.length})`);
      if (child.length === 0) out.paragraph(tr(fr, 'No entries', 'Aucune entrée'), 'muted');
      child.slice(0, 22).forEach((v, i) => (isObject(v)
        ? fields(out, v, str(v, 'name') || str(v, 'id') || str(v, 'code') || `${name} ${i + 1}`, fr, depth + 1)
        : out.card(`${name} ${i + 1}`, cut(asText(v), 600), 'blue')));
      if (child.length > 22) out.more(child.length - 22, fr);
    } else {
      out.section(name);
      fields(out, child as Json, name, fr, depth + 1);
    }
  }
}
