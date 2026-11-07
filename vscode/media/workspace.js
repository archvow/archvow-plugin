// The archvow workspace page, the same functions as the IntelliJ workspace, in two roles:
// - 'nav', the sidebar: Overview, Components (the tree) and Commands (the list), to choose;
// - 'editor', the one editor tab: the chosen component, the chosen command's form, a command's result.
// It shows what the extension sends (what archvow said) and asks the extension for every command; the
// extension checks it against archvow's grammar, and a change runs only after "Confirm and run" here.
// Plain script, no framework, nothing loaded from outside.
(function () {
  'use strict';
  const vscode = acquireVsCodeApi();
  const NAV = window.ARCHVOW_ROLE !== 'editor';
  /** What archvow said, from the extension. */
  let S = null;
  /** The grammar, the bundled catalog, the rule docs, the component types. */
  let I = { commands: [], catalog: null, knownVersion: '', rules: { en: '', fr: '' }, types: {}, forms: {}, icons: {} };
  /** What the person chose on the page. */
  const ui = {
    tab: 'overview', overSub: 'architecture', forceWorkspace: false, filterMenu: false, projectMore: false,
    /** The editor tab: 'empty', 'component', 'briefs', 'checks', 'project', 'command', 'result' or 'search'. */
    mode: 'empty', resultLabel: '', title: '',
    component: '', view: 0, filter: 'all',
    sel: { decision: '', question: '', safety: '', assumption: '', data: '', example: '', other: -1 },
    /** 0.18.0: the output file shown per example (Examples view); the search (show <name> --json), its
     *  group filter and selected hit, and the text typed in the sidebar's find field. */
    exampleFile: {}, search: null, searchName: '', searchGroup: '', searchSel: 0, findText: '',
    otherList: 0, drafts: {}, severity: {}, changing: {}, suggested: {}, results: {}, raw: {}, confirm: null,
    /** The Spec view: the card (spec.md) or the vow (spec.vow), and the cards read. */
    specPart: 'card', cards: {},
    /** The editor tab: the component (and its view) a command was opened from, for Back; null without. */
    back: null, backArmed: null,
    command: NAV ? '' : 'check', commandFilter: '', commandGroupsOpen: {}, formSections: {}, form: {}, formHelp: '', formStep: 0, skillStep: 0,
    /** The form's live preview (design A): the command line, or what is still missing. */
    preview: { text: '', missing: '', files: [] },
    catalog: null, catalogSource: '', initTemplate: '', initAnswers: {}, initTouched: {}, advanced: false,
    /** Edit spec: the values the form opened with (only the changed ones are sent); template new: its settings. */
    formBaseline: {}, tplAnswers: {}, tplLayers: {}, smartRows: {}, smartQuestions: {}, paramRows: {},
    /** The layer whose "+" is open in the Components tab (its types to add), or ''. */
    addLayer: '',
    /** The comparison base: the extension's best one (an existing branch) until the person types another. */
    base: '', baseTouched: false, checkSel: 0, explained: {},
    /** A result's height, once dragged or expanded. */
    resultHeight: {},
    /** 0.16.0: the cards shown (all, open, or what the AI added that waits for a person), the "All AI skills"
     *  menu, and the AI skill whose launch form is open with its values. */
    originFilter: 'all', aiMenu: false, addMenu: false, advance: '', skill: '', skillValues: {},
  };

  // ---------------------------------------------------------------- helpers

  const fr = () => !!(S && S.fr);
  const t = (en, frText) => (fr() ? frText : en);
  const esc = (v) => String(v === null || v === undefined ? '' : v).replace(/&/g, '&amp;').replace(/</g, '&lt;')
    .replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  const str = (o, k) => (o && o[k] !== null && o[k] !== undefined ? (typeof o[k] === 'object' ? JSON.stringify(o[k]) : String(o[k])) : '');
  const list = (o, k) => (o && Array.isArray(o[k]) ? o[k].filter((x) => x && typeof x === 'object') : []);
  const short = (s, n) => (s.length <= n ? s : s.substring(0, n - 1) + '…');
  const post = (m) => {
    // a command opened from a component (Edit spec, + Question, Accept...): the editor tab can go back to it
    if (!NAV && ui.mode === 'component' && ui.component && ui.view !== 7 && (m.type === 'openCommand' || m.type === 'choose')) {
      ui.backArmed = { component: ui.component, view: ui.view };
    } else if (!NAV && ui.mode === 'briefs' && m.type === 'openCommand') {
      ui.backArmed = { briefs: true };
    }
    vscode.postMessage(m);
  };
  const btn = (label, action, extra = '', disabled = false, cls = '') =>
    `<button class="${cls}" data-a="${action}" ${extra} ${disabled ? 'disabled' : ''}>${esc(label)}</button>`;
  /** The same terminal treatment for visible shell commands throughout the workspace.
   *  It only changes presentation; the real command string is never reformatted. */
  function terminalCommand(command, options = {}) {
    const raw = String(command ?? '');
    const shown = raw || 'archvow';
    const pending = !!options.missing;
    const copy = options.copy !== false;
    const id = options.id ? ` id="${esc(options.id)}"` : '';
    const copyTitle = pending ? t('Copy preview text (not a runnable command)', 'Copier le texte de prévisualisation (commande non exécutable)')
      : t('Copy command', 'Copier la commande');
    const copyControl = copy ? `<button type="button" class="terminal-copy" data-a="copyText" data-text="${esc(shown)}" title="${esc(copyTitle)}" aria-label="${esc(copyTitle)}"><svg viewBox="0 0 16 16" aria-hidden="true"><rect x="5" y="2" width="9" height="10" rx="1"/><path d="M11 14H3a1 1 0 0 1-1-1V5"/></svg></button>` : '';
    return `<div class="terminal-command" role="group" aria-label="${esc(t('Command', 'Commande'))}">
      <div class="terminal-bar"><span class="terminal-lights" aria-hidden="true"><i></i><i></i><i></i></span><span class="terminal-label">${esc(t('Terminal — archvow', 'Terminal — archvow'))}</span>${copyControl}</div>
      <pre class="terminal-content"><span class="terminal-prompt" aria-hidden="true">$</span> <span${id} class="terminal-text${pending ? ' missing' : ''}">${esc(shown)}</span></pre>
    </div>`;
  }
  // ---------------------------------------------------------------- AI skills and origins (0.16.0)

  /** forms.json ai_skills: the five skills in plain words, the request each sends, the origin tags. */
  const AI = () => (I.forms && I.forms.ai_skills) || { order: [], skills: {}, origins: {} };
  const skillDef = (id) => (AI().skills || {})[id] || null;
  const skillLabel = (id) => { const d = skillDef(id); return d ? pair(d.label) : id; };
  /** The extension's view of a component (aiSkills.ts): origins, what waits for a person, the suggestion. */
  const aiOf = (id) => (S && S.ai && S.ai[id || ui.component]) || null;
  const originWord = (key) => pair((AI().origins || {})[key]) || key;
  /** The tag on a card: Template, Project, Brief #section, AI · agent - nothing for a person's own; To review. */
  function originTag(o) {
    if (!o || !o.tag) return '';
    let text = originWord(o.tag);
    if (o.tag === 'brief' && o.section) text += ' #' + o.section;
    if (o.tag === 'ai') text += o.proposal ? ' · ' + t('proposed answer', 'réponse proposée') : o.who ? ' · ' + o.who : '';
    const tip = o.tag === 'ai' ? (o.why ? t('Why: ', 'Pourquoi : ') + o.why : t('Added by the AI', "Ajouté par l'IA"))
      : o.tag === 'template' ? t('Came with the component type', 'Vient avec le type de composant')
      : o.tag === 'project' ? t('Answered once for every component of this type', 'Répondu une fois pour tous les composants de ce type')
      : t('Drawn from the brief section', 'Tiré de la section du brief');
    return `<span class="origin o-${esc(o.tag)}" title="${esc(tip)}">${esc(text)}</span>`
      + (o.toReview ? ` <span class="to-review">${esc(originWord('to_review'))}</span>` : '');
  }
  /** The "Why the AI asked" box of the answer panel, and the AI's agent. */
  function aiWhy(o, head) {
    if (!o || o.tag !== 'ai' || o.proposal) return '';
    return `<div class="ai-why"><b>${esc(head)}</b>${o.why ? `<div>${esc(o.why)}</div>` : ''}<div class="muted small">${esc(t('Added by ', 'Ajouté par ') + (o.who || 'AI'))}</div></div>`;
  }
  /** The AI's items count of a view, as a blue dot and a number in its chip. */
  const aiDot = (n) => (n > 0 ? ` <span class="ai-dot" title="${esc(t(`${n} from the AI to review`, `${n} de l'IA à relire`))}">● ${n}</span>` : '');
  /** All / Open / To review: which cards the views show. */
  const keepCard = (open, review) => ui.originFilter === 'all' || (ui.originFilter === 'open' ? open || review : review);

  /** A circle turning clockwise, open at the top right, its arrowhead at the top: Reload (the IntelliJ plugin draws the same). */
  const REFRESH = '<svg class="glyph" viewBox="0 0 14 14" aria-hidden="true"><path d="M11.70 5.29 A5 5 0 1 1 7.87 2.08" fill="none" '
    + 'stroke="currentColor" stroke-width="1.55" stroke-linecap="round"/><path d="M10.23 2.50 L7.63 0.01 L6.93 3.95 Z" fill="currentColor"/></svg>';
  /** The trees' icons (contract/forms/icons.json): one drawing each, in the text's colour; IntelliJ paints the same. */
  const ICONS = new Map();
  function icon(kind, cls = '') {
    const key = kind + '|' + cls;
    if (ICONS.has(key)) return ICONS.get(key);
    const shapes = ((I.icons || {}).shapes || {})[kind];
    if (!Array.isArray(shapes)) return '';
    const stroke = `fill="none" stroke="currentColor" stroke-width="${Number((I.icons || {}).stroke) || 1.4}" stroke-linecap="round" stroke-linejoin="round"`;
    const n = (v) => Number(v) || 0;
    const body = shapes.map(([k, ...a]) => {
      if (k === 'l') return `<line x1="${n(a[0])}" y1="${n(a[1])}" x2="${n(a[2])}" y2="${n(a[3])}" ${stroke}/>`;
      if (k === 'c') return `<circle cx="${n(a[0])}" cy="${n(a[1])}" r="${n(a[2])}" ${stroke}/>`;
      if (k === 'C') return `<circle cx="${n(a[0])}" cy="${n(a[1])}" r="${n(a[2])}" fill="currentColor"/>`;
      if (k === 'e') return `<ellipse cx="${n(a[0])}" cy="${n(a[1])}" rx="${n(a[2])}" ry="${n(a[3])}" ${stroke}/>`;
      if (k === 'r') return `<rect x="${n(a[0])}" y="${n(a[1])}" width="${n(a[2])}" height="${n(a[3])}" rx="${n(a[4])}" ${stroke}/>`;
      if (k === 'p') return `<path d="${esc(a[0])}" ${stroke}/>`;
      if (k === 'P') return `<path d="${esc(a[0])}" fill="currentColor"/>`;
      return '';
    }).join('');
    const svg = `<svg class="ico${cls ? ' ' + cls : ''}" viewBox="0 0 14 14" aria-hidden="true">${body}</svg>`;
    ICONS.set(key, svg);
    return svg;
  }
  /** A component's type drawn (schema, reader, writer...), or a page for a type the icons do not know. */
  const typeIcon = (type) => (((I.icons || {}).shapes || {})[type] ? type : 'component');
  /** A command's drawing: its own (init, check...), else its verb's (add, edit, remove...). */
  function commandIcon(path) {
    const ic = I.icons || {};
    const name = path.join(' ');
    return (ic.commands || {})[name] || (ic.commands || {})[path[0]] || (ic.verbs || {})[path[path.length - 1]] || 'other';
  }
  const iconBtn = (svg, action, title) => `<button class="icon" data-a="${action}" title="${esc(title)}" data-tooltip="${esc(title)}" aria-label="${esc(title)}">${svg}</button>`;
  const attrs = (o) => Object.entries(o).map(([k, v]) => `data-${k}="${esc(v)}"`).join(' ');
  const snapshot = () => (S && ui.component ? S.snapshots[ui.component] : null);
  const derived = () => (S && ui.component ? S.derived[ui.component] : null);
  const chip = (text, cls) => `<span class="chip ${cls}">${esc(text)}</span>`;
  const needsFeatureBranch = (b) => ['main', 'master', 'develop', 'development'].includes((b || '').trim().toLowerCase());
  const localizedState = (raw) => {
    if (!raw) return t('Not evaluated', 'Non évalué');
    const key = raw.trim().toLowerCase();
    if (!fr()) return key === 'drifted' ? 'Drift detected' : raw;
    return ({ implemented: 'Implémenté', verified: 'Vérifié', tested: 'Testé', drift: 'Écart détecté', drifted: 'Écart détecté',
      planned: 'Prévu', partial: 'Partiel', missing: 'Manquant', pending: 'En attente', blocked: 'Bloqué', failed: 'Échec', error: 'Échec' })[key] || raw;
  };
  const commandByName = (name) => I.commands.find((c) => c.path.join(' ') === name);
  /** The base Diff, Review and Budget compare with: what the person typed, else an existing branch. */
  const baseNow = () => (ui.baseTouched ? ui.base : (S && S.base) || '');
  /** What a field of the current command offers (choices.ts), and its list now (by component / type). */
  const fieldOf = (command, name) => ((S && S.choices && S.choices[command]) || {})[name];
  const choicesNow = (field) => (!field ? [] : field.by ? (field.lists || {})[(ui.form[field.by] || '').trim()] || [] : field.choices || []);

  // ---------------------------------------------------------------- the extension's messages

  window.addEventListener('message', (event) => {
    const m = event.data;
    switch (m.type) {
      case 'init':
        I = { commands: m.commands || [], catalog: m.catalog, knownVersion: m.knownVersion, rules: m.rules || { en: '', fr: '' }, types: m.types || {}, forms: m.forms || {}, icons: m.icons || {} };
        ICONS.clear();
        break;
      case 'state':
        S = m.state;
        if (ui.component && !(ui.component in (S.snapshots || {})) && S.spec && !componentIds().includes(ui.component)) ui.component = '';
        break;
      case 'select':
        ui.forceWorkspace = true;
        ui.mode = 'component';
        ui.back = null;
        if (ui.view === 7) ui.view = 0;
        selectComponent(m.component, false);
        if (m.clarify) chooseNext();
        break;
      case 'selected':
        ui.component = m.component || '';
        break;
      case 'commandSelected':
        ui.command = m.command;
        break;
      case 'quickList':
        openQuickList(Number(m.index) || 0);
        break;
      case 'tab':
        ui.tab = m.tab;
        break;
      case 'running':
        ui.results[m.target] = { running: true, args: m.args };
        if (m.target === 'overview') { ui.mode = 'result'; ui.resultLabel = m.label || ''; ui.back = null; }
        break;
      case 'result':
        ui.results[m.target] = { html: m.html, raw: m.raw, args: m.args, ok: m.ok, label: m.label, refusal: m.refusal || '', warnings: m.warnings || [] };
        ui.raw[m.target] = false;
        if (ui.confirm && ui.confirm.target === m.target) ui.confirm = null;
        if (m.target === 'overview') { ui.mode = 'result'; ui.resultLabel = m.label || ''; }
        break;
      case 'confirm':
        ui.confirm = { token: m.token, title: m.title, summary: m.summary, target: m.target, command: m.command };
        if (m.target === 'overview') { ui.mode = 'result'; ui.resultLabel = m.title || ''; }
        break;
      case 'commandDocs':
        ui.forceWorkspace = true; ui.mode = 'commandDocs'; ui.back = null;
        break;
      case 'briefs':
        if (NAV) return;
        ui.forceWorkspace = true; ui.mode = 'briefs'; ui.back = null; ui.backArmed = null;
        break;
      case 'search':
        // archvow show <name> --json (0.18.0): where a name is, and the problems naming it
        if (NAV) return;
        ui.forceWorkspace = true; ui.mode = 'search'; ui.back = null;
        ui.search = m.result || null; ui.searchName = m.name || ''; ui.searchGroup = ''; ui.searchSel = 0;
        break;
      case 'openCommand':
        ui.forceWorkspace = true;
        ui.back = ui.backArmed;
        ui.backArmed = null;
        ui.mode = 'command';
        setCommand(m.command, m.values || {}, m.help || '');
        break;
      case 'skill':
        ui.forceWorkspace = true;
        ui.back = ui.backArmed;
        ui.backArmed = null;
        ui.mode = 'skill';
        ui.aiMenu = false;
        ui.skill = m.skill;
        ui.skillStep = 0;
        ui.skillValues = Object.assign({ max: '5' }, m.values || {});
        break;
      case 'vow':
        if (S) S.vows[m.component] = m.text;
        ui.cards[m.component] = m.card || '';
        break;
      case 'catalog':
        ui.catalog = m.data;
        ui.catalogSource = m.source || '';
        if (ui.command === 'init') chooseInitTemplate();
        break;
      case 'showChecks':
        if (!NAV) return;
        ui.tab = 'overview';
        ui.overSub = 'checks';
        break;
      case 'showCheckDetails':
        if (NAV) return;
        ui.forceWorkspace = true;
        ui.mode = 'checks';
        ui.back = null;
        ui.checkSel = Math.max(0, Number(m.index) || 0);
        break;
      case 'showProjectWork':
        if (NAV) return;
        ui.forceWorkspace = true;
        ui.mode = 'project';
        ui.otherList = Number(m.index) || 0;
        ui.sel.other = -1;
        ui.back = null;
        break;
      case 'browsed':
        ui.form[m.field] = m.path;
        break;
      case 'formPreview': {
        // the command line as it is typed: written in place, no redraw (the cursor stays where it is)
        if (m.command !== ui.command) return;
        ui.preview = { text: m.text || '', missing: m.missing || '', files: Array.isArray(m.files) ? m.files : [] };
        const pre = document.getElementById('form-preview');
        if (pre) {
          pre.textContent = ui.preview.text || ui.preview.missing;
          pre.classList.toggle('missing', !ui.preview.text);
          const copy = pre.closest('.terminal-command')?.querySelector('.terminal-copy');
          if (copy) {
            copy.dataset.text = ui.preview.text || ui.preview.missing || '';
            const title = ui.preview.text ? t('Copy command', 'Copier la commande')
              : t('Copy preview text (not a runnable command)', 'Copier le texte de prévisualisation (commande non exécutable)');
            copy.title = title;
            copy.setAttribute('aria-label', title);
          }
        }
        const files = document.getElementById('form-files');
        if (files) files.innerHTML = filesBlock(ui.preview.files);
        return;
      }
      default:
        return;
    }
    render();
  });

  function componentIds() {
    return list(S && S.spec, 'containers').flatMap((c) => list(c, 'components').map((x) => str(x, 'id')));
  }

  // ---------------------------------------------------------------- rendering (focus and scroll kept)

  function keepActiveGuidedStepVisible(steps) {
    if (!steps) return;
    const current = steps.querySelector('.guided-step.current');
    if (!current || typeof current.getBoundingClientRect !== 'function') return;
    // Adjust only the horizontal strip: scrollIntoView() also scrolls the page.
    const frame = steps.getBoundingClientRect();
    const item = current.getBoundingClientRect();
    if (item.right > frame.right - 8) steps.scrollLeft += item.right - frame.right + 8;
    else if (item.left < frame.left + 8) steps.scrollLeft -= frame.left + 8 - item.left;
  }

  function render() {
    const app = document.getElementById('app');
    const active = document.activeElement;
    const focus = active && active.dataset && active.dataset.keep ? { key: active.dataset.keep, start: active.selectionStart, end: active.selectionEnd } : null;
    const scrolls = {};
    document.querySelectorAll('[data-scroll]').forEach((e) => { scrolls[e.dataset.scroll] = e.scrollTop; });
    // The step strip is horizontal and survives field/preview rerenders.
    const previousSteps = document.querySelector('.guided-command-column .guided-steps');
    const previousStepScroll = previousSteps ? previousSteps.scrollLeft : 0;
    app.innerHTML = page();
    if (!NAV) {
      const title = editorTitle();
      if (title !== ui.title) {
        ui.title = title;
        post({ type: 'title', text: title });
      }
    }
    document.querySelectorAll('[data-scroll]').forEach((e) => { if (scrolls[e.dataset.scroll]) e.scrollTop = scrolls[e.dataset.scroll]; });
    const steps = document.querySelector('.guided-command-column .guided-steps');
    if (steps) {
      steps.scrollLeft = previousStepScroll;
      keepActiveGuidedStepVisible(steps);
    }
    if (focus) {
      const again = document.querySelector(`[data-keep="${CSS.escape(focus.key)}"]`);
      if (again) {
        again.focus();
        try { again.setSelectionRange(focus.start, focus.end); } catch (e) { /* not a text field */ }
      }
    }
  }

  function page() {
    if (!S) return `<div class="loading">${esc('Loading archvow…')}</div>`;
    const welcome = !ui.forceWorkspace && (S.readiness !== 'READY' || (!S.spec && S.welcomeError));
    if (NAV) return header() + (welcome ? welcomeView() : tabs());
    if (welcome && ui.mode !== 'command') return header() + welcomeView();
    return header() + `<main>${editorBody()}</main>`;
  }

  /** The editor tab's title: what it shows. */
  function editorTitle() {
    if (ui.mode === 'component') return 'archvow: ' + (ui.component || t('Components', 'Composants'));
    if (ui.mode === 'checks') return 'archvow: ' + t('Check & Gates', 'Contrôles et validations');
    if (ui.mode === 'briefs') return 'archvow: Briefs';
    if (ui.mode === 'commandDocs') return 'archvow: ' + t('Command reference', 'Docs des commandes');
    if (ui.mode === 'project') return 'archvow: ' + t('Project items', 'Éléments du projet');
    if (ui.mode === 'command') return 'archvow: ' + ui.command;
    if (ui.mode === 'skill') return 'archvow: ' + skillLabel(ui.skill);
    if (ui.mode === 'result') return 'archvow: ' + (ui.resultLabel || t('Result', 'Résultat'));
    if (ui.mode === 'search') return 'archvow: ' + t('find ', 'chercher ') + ui.searchName;
    return 'archvow';
  }

  function header() {
    const reload = iconBtn(REFRESH, 'refresh', t('Reload Archvow', 'Recharger Archvow'));
    const log = btn('☷', 'log', `title="${esc(t('Open Archvow session log', 'Ouvrir le journal Archvow'))}" data-tooltip="${esc(t('Open Archvow session log', 'Ouvrir le journal Archvow'))}" aria-label="${esc(t('Open Archvow session log', 'Ouvrir le journal Archvow'))}"`, false, 'icon');
    const progress = S.loading ? `<div class="progress">⏳ ${esc(S.loading)}</div>` : '';
    if (NAV) {
      return `<header class="top">
  <div class="brand"><img src="${esc(window.ARCHVOW_LOGO)}" alt="" class="logo">
    <div><div class="title">Archvow</div><div class="project" title="${esc(S.project.root)}">⌂ ${esc(S.project.name)}</div></div></div>
  <div class="right">${findBox()}${reload}${log}</div></header>${progress}`;
    }
    const what = ui.mode === 'component' ? ui.component
      : ui.mode === 'checks' ? t('Check & Gates', 'Contrôles et validations')
        : ui.mode === 'briefs' ? 'Briefs'
        : ui.mode === 'commandDocs' ? t('Command reference', 'Docs des commandes')
        : ui.mode === 'project' ? t('Project items', 'Éléments du projet')
        : ui.mode === 'command' ? 'archvow ' + ui.command : ui.mode === 'skill' ? skillLabel(ui.skill) : ui.mode === 'result' ? ui.resultLabel
          : ui.mode === 'search' ? ui.searchName : '';
    const kind = ui.mode === 'component' ? t('Component', 'Composant') : ui.mode === 'command' ? t('Command', 'Commande')
      : ui.mode === 'skill' ? t('AI skill', 'Skill IA')
      : ui.mode === 'briefs' ? 'Briefs'
      : ui.mode === 'commandDocs' ? t('Reference', 'Référence')
      : ui.mode === 'checks' ? t('Checks', 'Contrôles') : ui.mode === 'project' ? t('Project', 'Projet') : ui.mode === 'result' ? t('Result', 'Résultat')
        : ui.mode === 'search' ? t('Find', 'Recherche') : '';
    // a component's name is the heading of its page, with its open items (componentDetail)
    const named = ui.mode === 'component' && ui.view !== 7 && ui.component && snapshot();
    const back = ui.back && ui.mode !== 'component'
      ? ui.back.briefs
        ? `<button class="secondary back" data-a="back" title="${esc(t('Back to Briefs', 'Retour aux briefs'))}">← Briefs</button>`
        : `<button class="secondary back" data-a="back" title="${esc(t('Back to ', 'Retour à ') + ui.back.component)}">← ${esc(ui.back.component)}</button>`
      : '';
    return `<header class="top ${named ? 'slim' : ''}">
  <div class="brand">${back}<img src="${esc(window.ARCHVOW_LOGO)}" alt="" class="logo">
    <div><div class="kind">${esc(kind ? 'Archvow · ' + kind : 'Archvow')}</div>${named ? '' : `<div class="title">${esc(what || t('Specifications → understandable components', 'Des specs aux composants compréhensibles'))}</div>`}
    <div class="project" title="${esc(S.project.root)}">⌂ ${esc(S.project.name)}</div></div></div>
  <div class="right">${reload}${log}</div></header>${progress}`;
  }

  /** The editor tab: the component, the command or the result chosen in the sidebar. */
  function editorBody() {
    if (ui.mode === 'component') return `<section class="detail-only">${componentDetail()}</section>`;
    if (ui.mode === 'briefs') return `<section class="detail-only briefs-editor">${briefsView()}</section>`;
    if (ui.mode === 'checks') return `<section class="detail-only checks-editor">${checksView(true)}</section>`;
    if (ui.mode === 'commandDocs') return `<section class="detail-only">${commandReference()}</section>`;
    if (ui.mode === 'project') return `<section class="detail-only"><div class="card"><h3>${esc(t('Project items', 'Éléments du projet'))}</h3>${otherView()}</div></section>`;
    if (ui.mode === 'command') {
      const command = commandByName(ui.command);
      return `<section class="detail-only">${command ? commandForm(command) : `<p class="muted">${esc(t('Select a command in the Archvow sidebar.', 'Sélectionne une commande dans le panneau Archvow.'))}</p>`}</section>`;
    }
    if (ui.mode === 'skill') return `<section class="detail-only">${skillForm()}</section>`;
    if (ui.mode === 'search') return `<section class="detail-only">${searchView(ui.search)}</section>`;
    if (ui.mode === 'result') {
      const body = confirmation('overview') + resultCard('overview');
      return `<section class="detail-only">${body || `<p class="muted">${esc(t('No result. Run a command from Overview in the Archvow sidebar.', "Aucun résultat. Lance une commande depuis la Vue d'ensemble du panneau Archvow."))}</p>`}</section>`;
    }
    return `<section class="detail-only empty"><p class="muted">${esc(t('Choose a component or a command in the Archvow sidebar: it opens here, in this one tab.',
      'Choisis un composant ou une commande dans le panneau Archvow : il s\'ouvre ici, dans cet unique onglet.'))}</p></section>`;
  }

  function welcomeView() {
    const texts = {
      NO_PROJECT: [t('No project', 'Aucun projet'), t('Open a folder to use Archvow.', 'Ouvre un dossier pour utiliser Archvow.')],
      ROOT_MISSING: [t('Project directory unavailable', 'Dossier projet inaccessible'), t('The project directory is not accessible.', 'Le dossier du projet est introuvable ou inaccessible.')],
      NOT_INITIALIZED: [t('Archvow not initialized', 'Archvow non initialisé'), t('This folder does not contain .archvow/spec.vow. Initialize Archvow to begin or open an initialized project.',
        'Ce dossier ne contient pas .archvow/spec.vow. Initialise Archvow ou ouvre un projet existant.')],
      SPEC_INACCESSIBLE: [t('Specification unavailable', 'Spécification inaccessible'), t('.archvow/spec.vow is not readable.', ".archvow/spec.vow n'est pas lisible.")],
    };
    const [title, detail] = texts[S.readiness] || [t('Archvow needs attention', 'Configuration Archvow à vérifier'),
      t('The project is initialized, but Archvow could not load it.', "Le projet est initialisé, mais Archvow n'a pas pu le charger.")];
    return `<section class="welcome card"><h2>${esc(t('Archvow · Getting started', 'Archvow · Démarrage'))}</h2>
  <h3>${esc(title)}</h3><p>${esc(detail)}</p><p class="muted">${esc(S.project.root)}</p>
  ${S.welcomeError ? `<pre class="error">${esc(S.welcomeError)}</pre>` : ''}
  ${installCard()}
  <div class="row">${btn(t('Initialize Archvow', 'Initialiser Archvow'), 'init', '', S.readiness !== 'NOT_INITIALIZED')}
  ${btn(t('Configure Python', 'Configurer Python'), 'settings')}${btn(t('Retry', 'Réessayer'), 'refresh')}</div>
  ${confirmation('welcome')}${resultCard('welcome')}</section>`;
  }

  /** Install archvow (0.12.0): when it cannot start, or is another major.minor than the plugin knows. Nothing runs
   *  before "Confirm and run". */
  function installCard() {
    const i = S.install || {};
    if (!i.reason) return '';
    const why = i.reason === 'missing'
      ? t(`archvow is not installed where the plugin looks. Install archvow ${i.version}:`, `archvow n'est pas installé là où le plugin le cherche. Installer archvow ${i.version} :`)
      : t(`The installed archvow is not ${i.version}, the version this plugin knows:`, `L'archvow installé n'est pas ${i.version}, la version que ce plugin connaît :`);
    const where = i.python
      ? `${terminalCommand(i.command)}<p class="muted small">${esc(t('From', 'Depuis'))}: ${esc(i.source === "pip's configuration" ? t("pip's configuration", 'la configuration de pip') : i.source)} · ${esc(t('setting archvow.installFrom', 'réglage archvow.installFrom'))}</p>`
      : `<p class="tone-warn">${esc(t('No Python found for this project (.venv, venv, the Python extension\'s interpreter, archvow.python): choose one with Configure Python.',
        "Aucun Python trouvé pour ce projet (.venv, venv, l'interpréteur de l'extension Python, archvow.python) : choisis-en un avec Configurer Python."))}</p>`;
    return `<div class="card install"><p><b>${esc(why)}</b></p>${where}
  <div class="row">${btn(t(`Install archvow ${i.version}`, `Installer archvow ${i.version}`), 'install', '', !i.python)}</div></div>`;
  }

  /** The sidebar: Overview, Components (the tree), Commands (the list). */
  function tabs() {
    const names = [['overview', t('Overview', "Vue d'ensemble")], ['components', t('Components', 'Composants')], ['commands', t('Commands', 'Commandes')], ['ai', t('AI skills', 'Skills IA')]];
    const body = ui.tab === 'components' ? `<section class="browser-only">${componentBrowser()}</section>` : ui.tab === 'commands' ? commandsTab() : ui.tab === 'ai' ? aiSkillsTab() : overviewTab();
    return `<nav class="tabs main">${names.map(([id, l]) => `<button class="tab ${ui.tab === id ? 'current' : ''}" data-a="tab" data-tab="${id}">${esc(l)}</button>`).join('')}</nav>
<main>${body}</main>`;
  }

  /** The sidebar's find field (0.18.0): a name - a function, a column, a ticket, an example - and Enter runs
   *  archvow show <name> --json; the result opens in the editor tab. */
  function findBox() {
    const searchTitle = t('Find in the project (Enter to search)', 'Chercher dans le projet (Entrée pour chercher)');
    const filterTitle = t('Filter components', 'Filtrer les composants');
    const filters = [['all', t('All', 'Tous')], ['pending', t('Pending', 'En attente')], ['resolved', t('Resolved', 'Traités')]];
    return `<div class="top-search" role="search"><div class="search-field">${icon('search') || '⌕'}<input id="find" aria-label="${esc(searchTitle)}" data-input="find" data-keep="find" value="${esc(ui.findText)}" placeholder="${esc(t('Search…', 'Rechercher…'))}" title="${esc(searchTitle)}"></div>
      <button class="icon utility ${ui.filter !== 'all' ? 'filter-active' : ''}" data-a="filterMenu" title="${esc(filterTitle)}" data-tooltip="${esc(filterTitle)}" aria-label="${esc(filterTitle)}" aria-expanded="${ui.filterMenu}"><svg class="ico" viewBox="0 0 16 16" aria-hidden="true"><path d="M2 3h12l-5 5v4l-2 1V8z" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/></svg></button>
      ${ui.filterMenu ? `<div class="top-filter-menu" role="group" aria-label="${esc(filterTitle)}">${filters.map(([id,l]) => `<button class="${ui.filter === id ? 'on' : ''}" data-a="filter" data-filter="${id}">${esc(l)}</button>`).join('')}</div>` : ''}</div>`;
  }

  const SEARCH_GROUPS = () => ({ spec: t('Spec', 'Spec'), code: t('Code', 'Code'), tests: t('Tests', 'Tests'), examples: t('Examples', 'Exemples'),
    briefs: t('Briefs', 'Briefs'), template: t('Template', 'Template'), problems: t('Problems', 'Problèmes') });

  /** Every hit of a search, in its groups' order, each with its group: the list the selection indexes. */
  function searchHits(result, only) {
    return list(result, 'groups').filter((g) => !only || str(g, 'group') === only)
      .flatMap((g) => list(g, 'hits').map((h) => Object.assign({ group: str(g, 'group') }, h)));
  }

  /** archvow show <name> --json: groups left (with counts, a click filters), the places in the middle, the
   *  selected one's lines right; a name found nowhere gives its close names. As IntelliJ's search panel. */
  function searchView(result) {
    if (!result) return `<p class="muted">${esc(t('Type a name in the find field of the Archvow sidebar.', 'Tape un nom dans le champ de recherche du panneau Archvow.'))}</p>`;
    const name = str(result, 'name');
    const total = Number(result.total) || 0;
    if (!total) {
      const close = list(result, 'close');
      return `<div class="card search-empty"><h2 class="mono">${esc(name)}</h2>
  <p class="tone-warn">${esc(t(`'${name}' is not a component, a file or an item, and is nowhere in the project.`, `'${name}' n'est ni un composant, ni un fichier, ni un élément, et n'est nulle part dans le projet.`))}</p>
  ${close.length ? `<p class="muted">${esc(t('Close names - click one to search it:', 'Noms proches - clique pour le chercher :'))}</p><div class="row wrap">${close.map((c) => btn(`${str(c, 'name')} · ${str(c, 'what')}`, 'find', attrs({ id: str(c, 'name') }), false, 'secondary')).join('')}</div>` : ''}</div>`;
    }
    const groups = SEARCH_GROUPS();
    const nav = [`<button class="search-group ${ui.searchGroup ? '' : 'on'}" data-a="searchGroup" data-id=""><span>${esc(t('All', 'Tout'))}</span><span class="n">${total}</span></button>`,
      ...list(result, 'groups').map((g) => `<button class="search-group ${ui.searchGroup === str(g, 'group') ? 'on' : ''} ${str(g, 'group') === 'problems' ? 'tone-bad' : ''}" data-a="searchGroup" data-id="${esc(str(g, 'group'))}"><span>${esc(groups[str(g, 'group')] || str(g, 'title'))}</span><span class="n">${list(g, 'hits').length}</span></button>`)].join('');
    const hits = searchHits(result, ui.searchGroup);
    if (ui.searchSel >= hits.length) ui.searchSel = 0;
    let shownGroup = '';
    const rows = hits.map((h, i) => {
      const heading = h.group !== shownGroup ? `<li class="search-heading ${h.group === 'problems' ? 'tone-bad' : ''}">${esc(groups[h.group] || h.group)}${h.group === 'spec' ? ` <span class="muted">${esc(t('(the vow - the spec card shows the same)', '(le vow - la fiche de spec montre la même chose)'))}</span>` : ''}</li>` : '';
      shownGroup = h.group;
      const where = str(h, 'file') + (Number(h.line) ? ':' + h.line : '');
      const part = str(h, 'part') + (str(h, 'what') ? ` (${str(h, 'what')})` : '') + (str(h, 'marks') ? ` [${str(h, 'marks')}]` : '');
      return `${heading}<li class="search-hit ${i === ui.searchSel ? 'selected' : ''}" data-a="searchHit" data-i="${i}" title="${esc(t('Double-click: open the file at this line', 'Double-clic : ouvrir le fichier à cette ligne'))}">
  <span class="hit-component muted">${esc(str(h, 'component') || '—')}</span><span class="hit-part ${h.group === 'problems' ? 'tone-bad' : ''}">${esc(part)}</span>
  <span class="hit-text"><span>${esc(str(h, 'text'))}</span><span class="hit-where mono">${esc(where)}</span></span></li>`;
    }).join('');
    const hit = hits[ui.searchSel];
    const around = hit && Array.isArray(hit.around) ? hit.around : [];
    const preview = hit ? `<div class="answer-head"><span>${esc((groups[hit.group] || hit.group) + ' · ' + (str(hit, 'component') || t('no component', 'aucun composant')))}</span></div>
  <h3>${esc(str(hit, 'part'))}</h3><p class="mono small hit-where">${esc(str(hit, 'file') + (Number(hit.line) ? ':' + hit.line : ''))}</p>
  ${around.length ? `<pre class="plain around">${around.map((l) => `<span class="${Number(l.n) === Number(hit.line) ? 'here' : ''}">${esc(String(l.n).padStart(4))}  ${esc(str(l, 'text'))}</span>`).join('\n')}</pre>` : `<p>${esc(str(hit, 'text'))}</p>`}
  ${hit.group === 'problems' ? `<div class="alert bad"><b>${esc(str(hit, 'part'))}</b> ${esc(str(hit, 'text'))}</div>` : ''}
  <div class="row wrap">${str(hit, 'file') && !str(hit, 'file').startsWith('archvow/') && !str(hit, 'file').startsWith('template ') ? btn(Number(hit.line) ? t(`Open at line ${hit.line}`, `Ouvrir à la ligne ${hit.line}`) : t('Open', 'Ouvrir'), 'openFile', attrs({ file: str(hit, 'file').replace(/\/$/, ''), line: Number(hit.line) || 0 })) : ''}
  ${str(hit, 'component') && str(hit, 'component') !== 'project' ? btn(t(`Show ${str(hit, 'component')}`, `Afficher ${str(hit, 'component')}`), 'pick', attrs({ id: str(hit, 'component') }), false, 'secondary') : ''}
  ${hit.group === 'problems' ? btn(t(`Rule ${str(hit, 'part')}`, `Règle ${str(hit, 'part')}`), 'openRule', attrs({ code: str(hit, 'part') }), false, 'secondary') : ''}</div>` : '';
    return `<div class="search-head"><h2 class="mono">${esc(name)}</h2><span class="muted">${esc(t(`found in ${total} place${total === 1 ? '' : 's'}`, `trouvé à ${total} endroit${total === 1 ? '' : 's'}`))}${str(result, 'summary') ? ' · ' + esc(str(result, 'summary')) : ''}</span>
  <p class="muted small">${esc(t(`archvow show ${name} --json · whole words only`, `archvow show ${name} --json · mots entiers seulement`))}</p></div>
  <div class="search-grid"><nav class="search-groups">${nav}</nav><ul class="search-hits" data-scroll="search-hits">${rows}</ul><aside class="search-preview">${preview}</aside></div>`;
  }

  // ---------------------------------------------------------------- shared: result card, confirmation

  function resultCard(target) {
    const r = ui.results[target];
    if (!r) return '';
    const raw = !!ui.raw[target];
    const body = r.running ? `<p><b>⏳ ${esc(t('Running', 'Exécution'))}</b> · ${esc(r.args)}</p>`
      : raw ? `<pre class="raw">${esc(r.raw)}</pre>` : withCheckFiles(r.html);
    // readable at any length: drag the bar under it, or Expand to the height of the tab
    const height = ui.resultHeight[target];
    const expanded = !!height && height >= expandedHeight() - 2;
    return `<div class="card result"><div class="card-head"><b>▸ ${esc(t('Command result', 'Résultat de la commande'))}</b>
  <span class="row">${btn(expanded ? t('Reduce', 'Réduire') : t('Expand', 'Agrandir'), 'expandResult', attrs({ target })
    + ` title="${esc(t('Show the whole result at the height of the tab (or drag the bar under it)', "Afficher tout le résultat sur la hauteur de l'onglet (ou tirer la barre en dessous)"))}"`, r.running, expanded ? 'on' : 'secondary')}
  ${btn(t('Raw output', 'Sortie brute'), 'raw', attrs({ target }), r.running || !r.raw, raw ? 'on' : 'secondary')}
  ${btn(t('Copy', 'Copier'), 'copyResult', attrs({ target }), r.running || !r.raw, 'secondary')}
  ${btn('×', 'closeResult', attrs({ target }) + ` title="${esc(t('Close result', 'Fermer le résultat'))}"`, false, 'icon')}</span></div>
  <div class="card-body" data-scroll="result-${target}" data-body="${esc(target)}" title="${esc(r.args || '')}"${height ? ` style="height:${height}px;max-height:none"` : ''}>${body}${r.running ? '' : addFolderButton(r.raw)}</div>
  <div class="grip" data-grip="${esc(target)}" title="${esc(t('Drag to make the result taller or shorter', 'Tirer pour agrandir ou réduire le résultat'))}"></div></div>`;
  }

  /** Expand: the result as tall as the tab, its head and the form's buttons still in view. */
  function expandedHeight() {
    return Math.max(260, window.innerHeight - 150);
  }

  function confirmation(target) {
    const c = ui.confirm;
    if (!c || c.target !== target) return '';
    // A command confirmation shows the shell text only once, in the terminal.
    // Other confirmations keep their original human-readable summary.
    return `<div class="confirm"><div class="confirm-title">⚠ ${esc(t('Confirm', 'Confirmer'))}: ${esc(c.title)}</div>
  ${target === 'command' ? '' : `<pre>${esc(c.summary)}</pre>`}${terminalCommand(c.command)}
  <div class="row end">${btn(t('Cancel', 'Annuler'), 'cancelConfirm', '', false, 'secondary')}${btn(t('Confirm and run', 'Confirmer et exécuter'), 'confirm')}</div></div>`;
  }

  // ---------------------------------------------------------------- Overview

  /** The project's briefs folders (init answer `briefs`): where --from may draw from. */
  function briefFolders() {
    const b = S && S.briefs;
    if (!b || !S.spec) return '';
    const names = b.folders.length ? b.folders.map((f) => f + '/').join(', ') : t('none declared', 'aucun déclaré');
    const due = briefsDue();
    const link = S.briefStatus && due ? ` · <button class="link" data-a="openBriefCommands">${esc(t(`${due} section${due === 1 ? '' : 's'} to look at →`, `${due} section${due === 1 ? '' : 's'} à regarder →`))}</button>` : '';
    return `<p class="brief-folders muted small" title="${esc(t('An architect changes them: archvow init <package> --force -a briefs=...', 'Un architecte les change : archvow init <package> --force -a briefs=...'))}">${esc(t('Brief folders (--from)', 'Dossiers de briefs (--from)'))}: ${esc(names)} · ${b.files.length} ${esc(b.files.length === 1 ? t('file', 'fichier') : t('files', 'fichiers'))}${link}</p>`;
  }

  /** Sections not used yet plus sections changed since (archvow brief show, 1.20): the Briefs sub-tab's count. */
  const briefsDue = () => (S && S.briefStatus ? (Number(S.briefStatus.to_draft) || 0) + (Number(S.briefStatus.changed) || 0) : 0);

  /**
   * Overview › Briefs (0.16.0): every brief, section by section - what the spec draws from it (✓), not used yet
   * (○, Draft with AI…), changed since it was drawn (!, Review… opens the section, Accept records it).
   */
  function briefsView() {
    const b = S.briefStatus;
    if (!b) return `<div class="card"><p class="muted">${esc(t('This archvow cannot list the briefs section by section (archvow 1.20 or later does).', 'Cet archvow ne sait pas lister les briefs section par section (archvow 1.20 ou plus récent le fait).'))}</p></div>`;
    const files = list(b, 'files');
    const folders = Array.isArray(b.folders) ? b.folders.map(String) : [];
    const pkg = str(list(S.spec, 'containers')[0], 'package');
    const rows = files.map((f) => {
      const sections = list(f, 'sections');
      const used = sections.filter((r) => list(r, 'items').length || (Array.isArray(r.items) && r.items.length)).length;
      return `<div class="brief-file"><div class="row brief-head"><b class="grow">${esc(str(f, 'file'))}</b><span class="muted small">${esc(t(`${used} of ${sections.length} sections used`, `${used} sections utilisées sur ${sections.length}`))}</span></div>
  ${sections.map((r) => {
    const items = Array.isArray(r.items) ? r.items.map(String) : [];
    const ref = str(f, 'file') + (str(r, 'section') ? '#' + str(r, 'section') : '');
    const name = str(r, 'section') ? '#' + str(r, 'section') : t('(the whole file)', '(tout le fichier)');
    if (r.changed) return `<div class="brief-sec changed"><span class="st warn">!</span><button class="link" data-a="openBrief" data-ref="${esc(ref)}">${esc(name)}</button><span class="muted small grow">${esc(t('changed since: ', 'changé depuis : ') + items.join(', '))}</span>${btn(t('Review…', 'Relire…'), 'openBrief', attrs({ ref }), false, 'secondary small')}${btn(t('Accept change…', 'Accepter le changement…'), 'openCommand', attrs({ command: 'brief accept', brief: ref }), false, 'small')}</div>`;
    if (items.length) return `<div class="brief-sec"><span class="st good">✓</span><button class="link" data-a="openBrief" data-ref="${esc(ref)}">${esc(name)}</button><span class="muted small grow">${esc(items.join(', '))}</span></div>`;
    return `<div class="brief-sec"><span class="st open">○</span><button class="link" data-a="openBrief" data-ref="${esc(ref)}">${esc(name)}</button><span class="muted small grow">${esc(t('not used yet', 'pas encore utilisée'))}</span>${btn(t('Draft with AI…', "Rédiger avec l'IA…"), 'openSkill', attrs({ skill: 'draft-spec', brief: ref }), false, 'secondary small')}</div>`;
  }).join('')}</div>`;
    }).join('');
    return `<div class="card briefs-card"><div class="card-head"><h3>Briefs</h3><span class="muted small">${esc(t('folders: ', 'dossiers : ') + folders.map((x) => x + '/').join(', '))}</span></div>
  ${rows || `<p class="muted">${esc(t('No .md or .txt file in the briefs folders yet.', 'Aucun fichier .md ou .txt dans les dossiers de briefs pour le moment.'))}</p>`}
  <p class="muted small">${esc(t('✓ used by the spec · ○ not used yet · ! changed since accepted (ARCH039)', '✓ utilisée par la spec · ○ pas encore utilisée · ! changée depuis son acceptation (ARCH039)'))}</p>
  <div class="row wrap">${btn(t('✓ Accept brief…', '✓ Accepter un brief…'), 'openCommand', attrs({ command: 'brief accept' }), false, 'secondary small')}${btn(t('Add a briefs folder (architect)…', 'Ajouter un dossier de briefs (architecte)…'), 'addFolder', attrs({ package: pkg, answer: 'briefs=' + folders.join(',') + ',' }), false, 'secondary small')}</div></div>`;
  }

  function overviewTab() {
    const work = S.work;
    const branchWarning = needsFeatureBranch(S.branch) ? `<div class="warning" title="${esc(t('Advice only. Archvow does not automatically change Git branches.',
      'Conseil uniquement. Archvow ne change pas automatiquement de branche Git.'))}">${esc(t(`⚠ You are on ${S.branch}. For development, create a feature branch (e.g. feature/my-change) first.`,
      `⚠ Branche ${S.branch} : crée une branche de fonctionnalité (ex. feature/ma-modification) avant de développer.`))}</div>` : '';
    // Keep actual command handlers in one place; a responsive toolbar moves actions into More.
    // The visible data-tooltip and accessible name work on hover and keyboard focus.
    const projectAction = (kind, label, action, extra = '', index = 0) =>
      `<span class="project-action-slot action-slot-${index}"><button class="project-action" data-a="${action}" ${extra} title="${esc(label)}" data-tooltip="${esc(label)}" aria-label="${esc(label)}">${icon(kind) || ({ next: '➜', check: '✓', health: '✚', search: '⌕', document: '▤', briefs: '▤' }[kind] || '◦')}</button></span>`;
    const overflowItem = (kind, label, action, extra, index) =>
      `<button class="overflow-action overflow-action-${index}" data-a="${action}" ${extra} title="${esc(label)}">${icon(kind)}<span>${esc(label)}</span></button>`;
    const actions = [
      ['next', 'Next', 'run', attrs({ label: 'next', args: 'next --json', target: 'overview' })],
      ['check', 'Check', 'run', attrs({ label: 'full check', args: 'check --complete --json', target: 'overview' })],
      ['health', 'Doctor', 'run', attrs({ label: 'doctor', args: 'doctor --json', target: 'overview' })],
      ['search', 'Audit', 'run', attrs({ label: 'audit', args: 'audit --json', target: 'overview' })],
      ['document', t('Project vow', 'Vow du projet'), 'openProjectVow', ''],
      ['folder', t('Briefs', 'Briefs'), 'openBriefCommands', ''],
      ['list', 'Budget / Next', 'budget', ''],
      ['compare', 'Diff', 'openCommand', attrs({ command: 'diff', field: 'base' })],
      ['propose', 'AI review', 'openCommand', attrs({ command: 'review', field: 'base' })],
    ];
    const toolbar = actions.map(([kind, label, action, extra], index) => projectAction(kind, label, action, extra, index)).join('')
      + `<span class="action-more"><button class="project-action more-control" data-a="projectMore" title="${esc(t('More project commands', 'Autres commandes du projet'))}" data-tooltip="${esc(t('More project commands', 'Autres commandes du projet'))}" aria-label="${esc(t('More project commands', 'Autres commandes du projet'))}" aria-haspopup="menu" aria-expanded="${!!ui.projectMore}">${icon('other') || '⋯'}</button>${ui.projectMore ? `<div class="project-more-menu" role="menu">${actions.map(([kind, label, action, extra], index) => overflowItem(kind, label, action, extra, index)).join('')}</div>` : ''}</span>`;
    const sub = architectureView();
    const issues = list(S.check, 'violations');
    const failedChecks = S.check && S.check.errors != null ? Number(S.check.errors) || 0
      : issues.filter((v) => str(v, 'severity').toLowerCase() === 'error').length;
    const warnedChecks = S.check && S.check.warnings != null ? Number(S.check.warnings) || 0
      : issues.filter((v) => str(v, 'severity').toLowerCase() === 'warning').length;
    const projectPending = S.work.otherPending.filter(isProjectOther).length;
    // Partition the actual pending work, not the number of components or raw check violations.
    // Some checks (e.g. ARCH030) are already represented by a safety item.
    const checksPending = work.pending.filter((item) => item.source === 'check' || item.command === 'open-diagnostics').length;
    const architecturePending = work.pending.length - checksPending;
    return `<section class="overview">
  <div class="strip"><b>${esc(t('Project status', 'État du projet'))}</b><span class="row">
    <button class="badge pending" data-a="quickList" data-i="0" title="${esc(t('See open items by component', 'Voir les éléments ouverts par composant'))}">${work.pending.length} ${esc(t('pending', 'en attente'))}</button>
    <button class="badge resolved" data-a="quickList" data-i="1" title="${esc(t('See completed items by component', 'Voir les éléments traités par composant'))}">${work.resolved.length} ${esc(t('resolved', 'traités'))}</button>
    ${projectPending ? `<button class="badge project" data-a="projectWork" title="${esc(t('Project-wide items, not attributed to components', 'Éléments du projet non attribués aux composants'))}">${projectPending} ${esc(t('project items', 'éléments projet'))}</button>` : ''}</span></div>
  ${branchWarning}
  ${S.install && S.install.reason === 'version' ? installCard() + confirmation('welcome') + resultCard('welcome') : ''}
  <div class="project-action-container"><div class="project-action-bar" role="toolbar" aria-label="${esc(t('Project commands', 'Commandes du projet'))}">${toolbar}</div></div>
  ${sub}
  <div class="card checks-summary" data-a="openChecks" title="${esc(t('Open project diagnostics', 'Afficher les diagnostics du projet'))}"><div class="checks-summary-info">${icon('shield') || '✓'}<div>
    <h3>${esc(t('Checks', 'Contrôles'))}</h3>
    <p class="muted small"><span class="${failedChecks ? 'tone-bad' : ''}">${failedChecks} ${esc(t('errors', 'erreurs'))}</span> · <span class="${warnedChecks ? 'tone-warn' : ''}">${warnedChecks} ${esc(t('warnings', 'avertissements'))}</span></p></div></div>
    <button class="link" data-a="openChecks" title="${esc(t('Open project diagnostics', 'Afficher les diagnostics du projet'))}">${esc(t('Open Checks', 'Voir les contrôles'))} →</button></div>
  <footer class="hint"><label>${esc(t('Language', 'Langue'))} <select data-change="language">${[['auto', t('Follow VS Code', 'Suivre VS Code')], ['en', 'English'], ['fr', 'Français']]
    .map(([v, l]) => `<option value="${v}" ${S.languageMode === v ? 'selected' : ''}>${esc(l)}</option>`).join('')}</select></label>
    <span class="row wrap">${btn(t('Open settings', 'Ouvrir les paramètres'), 'settings', '', false, 'secondary')}
    ${btn(t('Test Archvow (--version)', 'Tester Archvow (--version)'), 'run', attrs({ label: 'version', args: '--version', target: 'overview' }), false, 'secondary')}</span></footer>
</section>`;
  }

  function architectureView() {
    if (!S.spec) return `<div class="card"><h3>${esc(t('Architecture unavailable', 'Architecture indisponible'))}</h3></div>`;
    const count = S.rows.length;
    return `<div class="card architecture-summary" data-a="openComponents" title="${esc(t('Open Components for details', 'Voir les composants en détail'))}"><div class="architecture-summary-info">${icon('system') || '▧'}<div>
      <h3>${esc(t('Architecture', 'Architecture'))}</h3>
      <p class="muted small">${count} ${esc(t('components', 'composants'))} · ${S.work.pending.length} ${esc(t('pending', 'en attente'))} · ${S.work.resolved.length} ${esc(t('resolved', 'traités'))}</p></div></div>
      <button class="link" data-a="openComponents">${esc(t('Open Components for details', 'Voir les composants en détail'))} →</button></div>`;
  }

  /** The colour of a state, as in IntelliJ's table: done green, drift or missing amber. */
  function stateTone(raw) {
    const key = (raw || '').toLowerCase();
    if (['implemented', 'verified', 'tested'].includes(key)) return 'tone-good';
    if (['planned', 'partial', 'pending'].includes(key)) return 'tone-warn';
    if (['drift', 'drifted', 'missing', 'blocked', 'failed', 'error'].includes(key)) return 'tone-warn';
    return 'muted';
  }

  /** Match exact diagnostic + message: two violations may have the same rule code. */
  function checkWorkItem(v) {
    if (!v || !S || !S.work) return null;
    const code = str(v, 'code');
    const message = str(v, 'message');
    return S.work.pending.find((item) => item.source === 'check' &&
      item.title.startsWith(code + ' · ') && item.detail.startsWith(message)) || null;
  }

  /** Clear labels describe the actual target file, without promising a spec for source files. */
  function checkConfigurationFolder(path) {
    const last = String(path || '').replace(/\\/g, '/').split('/').pop().toLowerCase();
    return ['.claude', '.cursor', '.continue', '.gemini', '.codex', '.github'].includes(last);
  }

  function checkFileLabel(path) {
    if (checkConfigurationFolder(path)) return t('Show configuration folder', 'Afficher le dossier de configuration');
    const lower = String(path || '').toLowerCase();
    if (lower.endsWith('.vow')) return t('Open vow', 'Ouvrir la vow');
    if (lower.endsWith('spec.md')) return t('Open spec', 'Ouvrir la spec');
    if (/\.(py|java|ts|js)$/.test(lower)) return t('Open source', 'Ouvrir le code source');
    if (lower.endsWith('.md')) return t('Open document', 'Ouvrir le document');
    return t('Open configuration', 'Ouvrir la configuration');
  }

  function checkFixable(item) {
    return item && !['open-file', 'open-diagnostics', 'open-project-vow', 'component-show',
      'prompt', 'brief accept'].includes(item.command);
  }

  /** The editor shows every diagnostic as a selectable row and the full selected
   *  explanation below it. The sidebar may show the same project checks. */
  function checksView(inEditor = false) {
    const check = S.check;
    const items = list(check, 'violations');
    const ok = check && check.ok === true;
    const rules = byRule(check);
    const totals = !check
      ? `<p class="tone-bad">${esc(t('No check result available', 'Résultat indisponible'))}</p><p class="muted">${esc(S.checkFailure || t('Run Check or verify the CLI.', 'Exécute le contrôle ou vérifie le CLI.'))}</p>`
      : `<p class="${ok ? 'tone-good' : 'tone-bad'}"><b>${esc(ok ? t('PASSED', 'VALIDÉ') : t('FAILED', 'NON VALIDÉ'))}</b> · ${Number(check.errors) || 0} ${esc(t('errors', 'erreurs'))} · ${Number(check.warnings) || 0} ${esc(t('warnings', 'avertissements'))}</p>
        ${rules ? `<p class="by-rule">${esc(rules)}</p>` : ''}`;
    if (ui.checkSel >= items.length) ui.checkSel = 0;
    const rows = items.map((v, i) => `<li class="${i === ui.checkSel ? 'selected' : ''} sev-${esc(str(v, 'severity'))}" data-a="checkSel" data-i="${i}" title="${esc(str(v, 'message'))}">
      <b>${esc(str(v, 'code'))}</b> <span class="chip ${str(v, 'severity') === 'error' ? 'bad' : 'warn'}">${esc(str(v, 'severity'))}</span> ${esc(short(str(v, 'message'), inEditor ? 180 : 120))}</li>`).join('');
    const listPart = `<ul class="list check-list" data-scroll="checks">${rows || `<li class="muted empty">${esc(t('No detailed diagnostics.', 'Aucun diagnostic détaillé.'))}</li>`}</ul>`;
    const title = `<div class="card-head"><h3>${esc(t('Check & Gates', 'Contrôles et validations'))}</h3>${btn(t('Check reference', 'Référence des contrôles'), 'openChecksReference', '', false, 'secondary')}</div>`;
    if (!inEditor) return `<div class="card checks-summary">${title}${totals}
      <p class="muted small">${esc(t('Select a diagnostic to open its details in the editor; double-click to open the rule.',
        'Sélectionne un diagnostic pour ouvrir ses détails dans l’éditeur ; double-clique pour ouvrir la règle.'))}</p>${listPart}</div>`;
    const selected = items[ui.checkSel];
    let detail = `<p class="muted">${esc(t('Select a diagnostic for details.', 'Sélectionne un diagnostic pour afficher ses détails.'))}</p>`;
    if (selected) {
      const code = str(selected, 'code');
      const explanation = ruleExplanation(code);
      const work = checkWorkItem(selected);
      const fixable = checkFixable(work);
      const file = (work && work.guide && work.guide.file ? work.guide.file.path : str(selected, 'file'));
      const line = (work && work.guide && work.guide.file ? work.guide.file.line : Number(selected.line) || 0);
      detail = `<div class="check-detail"><h4>${esc(code)}${str(selected, 'title') ? ' — ' + esc(str(selected, 'title')) : ''}</h4>
        <div class="row wrap">${chip(str(selected, 'severity'), str(selected, 'severity') === 'error' ? 'bad' : 'warn')}</div>
        <p class="check-message">${esc(str(selected, 'message'))}</p>
        ${file ? `<p class="muted">${esc(file)}${Number(selected.line) ? ':' + Number(selected.line) : ''}</p>` : ''}
        ${explanation ? `<p><b>${esc(t('Explanation', 'Explication'))}:</b> ${esc(explanation)}</p>` : ''}
        ${work && work.guide && work.guide.todo ? `<p><b>${esc(t('Suggested action', 'Action recommandée'))}:</b> ${esc(work.guide.todo)}</p>` : ''}
        <div class="row wrap">${fixable ? btn(t('Prepare fix: ', 'Préparer la correction : ') + work.command, 'prepareCheck', attrs({ i: ui.checkSel })) : ''}${file ? btn(checkFileLabel(file), 'openFile', attrs({ file, line }) + ` title="${esc(file + (line ? ':' + line : ''))}"`) : ''}${btn(t('View rule', 'Voir la règle'), 'openRule', attrs({ code }), false, 'secondary')}</div></div>`;
    }
    return `<div class="card checks-full">${title}${totals}${listPart}<div class="check-editor-detail">${detail}</div></div>`;
  }

  /** "By rule: ARCH001 146 · SPK008 415" from check's `counts` (archvow sorts them, most first); empty
   *  without counts or with a single rule (the totals already say it). Same text as the IntelliJ plugin. */
  function byRule(check) {
    const counts = check && typeof check.counts === 'object' && check.counts ? Object.entries(check.counts) : [];
    if (counts.length < 2) return '';
    return t('By rule: ', 'Par règle : ') + counts.map(([code, n]) => `${code} ${Number(n) || 0}`).join('  ·  ');
  }

  /** archvow's explanation of a check code, from its rules table (bundled with the extension). */
  function ruleExplanation(code) {
    const md = fr() ? I.rules.fr : I.rules.en;
    for (const line of (md || '').split('\n')) {
      const m = new RegExp(`^\\s*\\|\\s*${code}\\s*\\|(.*)$`).exec(line);
      if (m) return m[1].split('|').map((c) => c.trim()).filter((c) => c).join(' — ').replace(/`/g, '');
    }
    return '';
  }

  // ---------------------------------------------------------------- Components

  function componentBrowser() {
    const filters = [['all', t('All', 'Tous')], ['pending', t('Pending', 'En attente')], ['resolved', t('Resolved', 'Traités')]];
    const pending = S.work.pendingByComponent || {};
    const resolved = S.work.resolvedByComponent || {};
    const show = (id) => (ui.filter === 'pending' ? (pending[id] || 0) > 0 : ui.filter === 'resolved' ? (resolved[id] || 0) > 0 : true);
    const containers = list(S.spec, 'containers');
    const addable = S.addable || {};
    let tree = '';
    containers.forEach((c, index) => {
      // the template's layers in their order (an empty one too, under All: it can get its first component)
      const layers = new Map(index === 0 && ui.filter === 'all' ? Object.keys(addable).map((l) => [l, []]) : []);
      for (const comp of list(c, 'components')) {
        const id = str(comp, 'id');
        if (!show(id)) continue;
        const layer = str(comp, 'layer');
        if (!layers.has(layer)) layers.set(layer, []);
        layers.get(layer).push(comp);
      }
      tree += `<li class="container"><div class="node">${icon('system')}<span>${esc(str(c, 'id'))}</span></div><ul>${[...layers.entries()].map(([layer, comps]) => {
        const types = index === 0 ? addable[layer] || [] : [];
        const open = ui.addLayer === layer && types.length > 0;
        const plus = types.length ? `<button class="icon add-here${open ? ' on' : ''}" data-a="addHere" data-id="${esc(layer)}" aria-expanded="${open}" title="${esc(t(`Add a component to ${layer}: ${types.map((x) => x.type).join(', ')}`, `Ajouter un composant à ${layer} : ${types.map((x) => x.type).join(', ')}`))}">${icon('add')}</button>` : '';
        const chooser = open ? `<div class="add-types" role="group" aria-label="${esc(t('Types of ' + layer, 'Types de ' + layer))}"><span class="muted small">${esc(t('Add:', 'Ajouter :'))}</span>${types.map((x) => `<button class="secondary small" data-a="addType" data-id="${esc(x.type)}" title="${esc(x.description)}">${icon(typeIcon(x.type))}${esc(x.type)}</button>`).join('')}</div>` : '';
        const rows = comps.map((comp) => {
          const id = str(comp, 'id');
          const p = pending[id] || 0;
          const state = (S.rows.find((r) => r.id === id) || {}).state || str(comp, 'status') || 'planned';
          return `<li class="component ${id === ui.component ? 'selected' : ''} ${p ? 'pending' : 'done'}" data-a="pick" data-id="${esc(id)}">${icon(typeIcon(str(comp, 'type')), p ? 'warn' : 'good')}<span>${esc(id)} <span class="muted">· ${esc(str(comp, 'type'))}</span></span>${aiBadge(comp)} <span class="component-state ${esc(stateTone(state))}">${esc(localizedState(state))}</span>${p ? `<span class="count">${p}</span>` : '<span class="ok">✓</span>'}</li>`;
        }).join('') || `<li class="muted small empty-layer">${esc(t('No component yet', 'Pas encore de composant'))}</li>`;
        return `<li class="layer"><div class="node">${icon('layer')}<span>${esc(layer)}</span>${plus}</div>${chooser}<ul>${rows}</ul></li>`;
      }).join('')}</ul></li>`;
    });
    const add = S.spec ? `<button class="secondary small add-component" data-a="addComponent" title="${esc(t('Add a component (archvow component add): its type, name and what it must do', 'Ajouter un composant (archvow component add) : son type, son nom et ce qu\'il doit faire'))}">${icon('add')}${esc(t('Add component', 'Ajouter un composant'))}</button>` : '';
    return `<div class="row tree-tools">${filters.map(([id, l]) => btn(l, 'filter', attrs({ filter: id }) + ` title="${esc(ui.filter === id ? t('Current filter', 'Filtre actif') : t('Filter components', 'Filtrer les composants'))}"`, ui.filter === id, 'secondary small')).join('')}<span class="grow"></span>${add}</div>
  <ul class="tree" data-scroll="tree">${tree || `<li class="muted">${esc(S.spec ? t('No component.', 'Aucun composant.') : t('Loading…', 'Chargement…'))}</li>`}</ul>`;
  }

  const VIEWS = () => [t('Decisions', 'Décisions'), 'Questions', t('Safety', 'Sécurité'), t('Assumptions', 'Hypothèses'), t('Rules', 'Règles'), t('Tasks', 'Tâches'), 'Spec', t('Other', 'Autres'), t('Examples', 'Exemples')];
  /** The order of the view chips (0.18.0): Examples (view 8) right after Rules, as IntelliJ shows it. */
  const VIEW_ORDER = [0, 1, 2, 3, 4, 8, 5, 6, 7];

  function componentDetail() {
    // 0.17.2 (the user's order): AI, Add, Edit spec, Open code - what you ask the AI first, the one filled button
    // of the tab (no suggestion strip any more: AI ▾ marks the suggested step); then what you add, change, open.
    const actions = [
      `<span class="ai-anchor">${btn(t('✦ AI ▾', '✦ IA ▾'), 'aiMenu', `title="${esc(t('What you ask the AI for this component: it adds, you decide', "Ce que tu demandes à l'IA pour ce composant : elle ajoute, tu décides"))}" aria-haspopup="menu" aria-expanded="${ui.aiMenu ? 'true' : 'false'}"`, false, 'ai-skills')}${ui.aiMenu ? aiMenu() : ''}</span>`,
      `<span class="ai-anchor">${btn(t('+ Add ▾', '+ Ajouter ▾'), 'addMenu', `title="${esc(t('What you add yourself to this component', 'Ce que tu ajoutes toi-même à ce composant'))}" aria-haspopup="menu" aria-expanded="${ui.addMenu ? 'true' : 'false'}"`, false, 'secondary')}${ui.addMenu ? addMenu() : ''}</span>`,
      btn('✎ ' + t('Edit spec', 'Modifier la spec'), 'openCommand', attrs({ command: 'component edit', component: ui.component }) + ` title="${esc(t('Change the description, ticket, signature, uses, group, owners or brief (archvow component edit). Decisions, questions and hazards stay as they are: review the ones the change touches.', 'Changer la description, le ticket, la signature, les dépendances, le groupe, les responsables ou la brief (archvow component edit). Les décisions, questions et hazards restent tels quels : revoir ceux que le changement touche.'))}"`, false, 'secondary'),
      btn('⌘ ' + t('Open code', 'Ouvrir le code'), 'openCode', `title="${esc(t("Open this component's Python file (and its test). Change the code there; save, then Check.", 'Ouvrir le fichier Python de ce composant (et son test). Modifier le code là ; enregistrer, puis Vérifier.'))}"`, false, 'secondary'),
    ];
    const view = snapshot();
    const ai = aiOf();
    const d = derived();
    const counts = d ? d.counts : { pending: [0, 0, 0, 0, 0, 0], total: [0, 0, 0, 0, 0, 0] };
    const names = VIEWS();
    const localOtherPending = S.work.otherPending.filter((item) => isLocalOther(item)).length;
    const localOtherResolved = S.work.otherResolved.filter((item) => isLocalOther(item)).length;
    const aiCount = (i) => (!ai ? 0 : i === 0 ? ai.toReview.decisions : i === 2 ? ai.toReview.safety : i === 3 ? ai.toReview.assumptions : i === 4 ? ai.toReview.rules : 0);
    const ex = exampleCounts(view);
    const viewBar = VIEW_ORDER.map((i) => {  // every view always shows, Examples too (before Tasks)
      const name = names[i];
      const label = i < 6 ? name + (counts.pending[i] > 0 ? ' · ' + counts.pending[i] : counts.total[i] > 0 ? ' ✓' : '')
        : i === 7 ? name + (localOtherPending ? ' · ' + localOtherPending : localOtherResolved ? ' ✓' : '')
        : i === 8 ? name + (!ex.total ? '' : ' ' + ex.total + (ex.failing ? ' · ' + ex.failing + ' ✖' : ex.passing === ex.total ? ' ✓' : '')) : name;
      const dot = aiCount(i);
      const tip = i < 6 ? (counts.pending[i] > 0 ? `${counts.pending[i]} ${t('pending items', 'éléments en attente')}` : t('No open items', 'Aucun élément ouvert'))
        : i === 7 ? t('Only issues of this component; project checks are in Check & Gates.', 'Seulement les éléments de ce composant ; les contrôles globaux sont dans Check & Gates.')
        : i === 8 ? t(`Business examples: ${ex.passing} passing, ${ex.failing} failing, ${ex.total - ex.passing - ex.failing} without a passing run`,
          `Exemples métier : ${ex.passing} réussis, ${ex.failing} en échec, ${ex.total - ex.passing - ex.failing} sans lancement réussi`) : '';
      const pendingTab = (i < 6 && counts.pending[i] > 0) || (i === 7 && localOtherPending > 0) || (i === 8 && ex.failing > 0);
      return `<button class="tab ${ui.view === i ? 'current' : ''} ${pendingTab ? 'has-pending' : ''}${i === 8 ? ' examples-tab' : ''}" data-a="view" data-i="${i}" title="${esc(tip)}">${esc(label)}${aiDot(dot)}</button>`;
    }).join('');
    const listBar = listFilter(counts, aiCount, localOtherPending);
    let body;
    if (ui.view === 7) body = otherView();
    else if (!ui.component) body = `<p class="muted">${esc(t('Select a component in the Archvow sidebar.', 'Sélectionne un composant dans le panneau Archvow.'))}</p>`;
    else if (!view) body = `<p class="muted">${esc(t('Loading', 'Chargement'))}: ${esc(ui.component)}…</p>`;
    else if (ui.view === 8) body = examplesView(view);
    else body = [decisionsView, questionsView, safetyView, assumptionsView, rulesView, tasksView, vowView][ui.view](view, d);
    // A component's 'Others' belongs to this component; global diagnostics are in Check & Gates.
    const waiting = ai ? ai.toReview.total - ai.toReview.component : 0;
    const waitingBadge = waiting > 0 ? ` <button class="badge-ai" data-a="toReview" title="${esc(t('Show only what the AI added and no person kept yet', "N'afficher que ce que l'IA a ajouté et qu'aucune personne n'a encore gardé"))}">${esc(t(`${waiting} from AI to review`, `${waiting} de l'IA à relire`))}</button>` : '';
    const componentState = (S.rows.find((r) => r.id === ui.component) || {}).state || str(view, 'status');
    const head = view ? `<div class="component-head"><h2>${esc(ui.component)}<span class="component-state ${esc(stateTone(componentState))}">${esc(localizedState(componentState))}</span>${openBadge(ui.component)}${aiBadge(view)}${waitingBadge}${failingBadge(view)}</h2>${createdByAi(view)}<p class="meta muted">${esc(meta(view))}</p>${progressBar(view)}
  <p class="description">${esc(str(view, 'description'))}</p>${drawnFrom(view)}${links(view)}${examples(view)}${list(view, 'missing').length || (Array.isArray(view.missing) && view.missing.length)
    ? `<p class="tone-warn">${esc(t('Missing', 'Manquant'))}: ${esc(view.missing.map((x) => (typeof x === 'object' ? JSON.stringify(x) : x)).join(', '))}</p>` : ''}</div>` : '';
    // the head, the actions and the view chips over the whole width; under them, for the views whose
    // items are answered (decisions, questions, safety, assumptions), the items left and the answer right
    return `${head}<div class="row wrap actions">${actions.join('')}</div>
  <nav class="tabs views">${viewBar}</nav>${listBar || pendingExplanation()}
  <div class="view-body" data-scroll="view-${ui.view}">${body}</div>${confirmation('component')}${resultCard('component')}`;
  }

  /** archvow 1.19, modes guided and free: a component the AI added is the AI's until a person accepts it. */
  const aiBadge = (comp) => (str(comp, 'origin') === 'ai'
    ? ` <span class="chip blue" title="${esc(t('Created by the AI (', "Créé par l'IA (") + (str(comp, 'created_by') || 'AI') + t('): to review, then Accept', ') : à relire, puis Accepter'))}">${esc(t('AI: to review', 'IA : à relire'))}</span>` : '');
  function createdByAi(view) {
    if (str(view, 'origin') !== 'ai') return '';
    return `<div class="alert warn"><b>${esc(t('Created by the AI', "Créé par l'IA") + (str(view, 'created_by') ? ` (${str(view, 'created_by')})` : ''))}</b>
  <div>${esc(t('Read what it must do, its type and its uses; change it with Edit spec if needed. The release gate fails until a person accepts it (ARCH043).',
    "Lire ce qu'il doit faire, son type et ce qu'il utilise ; le changer avec Modifier la spec si besoin. Le contrôle de livraison échoue tant qu'une personne ne l'a pas accepté (ARCH043)."))}</div>
  <div class="row end">${btn(t('Remove…', 'Retirer…'), 'openCommand', attrs({ command: 'component remove', component: ui.component }), false, 'secondary')}${btn(t('Accept…', 'Accepter…'), 'openCommand', attrs({ command: 'component accept', component: ui.component }))}</div></div>`;
  }

  /**
   * 0.17.0: the list's own line, under the views - small text, never buttons like the views: "Show All · Open 2 ·
   * To review 1" (To review only when the AI added something here), and when the view has nothing open left,
   * where the work is ("Decisions: all done · Safety: 2 open ›"). Decisions, Safety and Rules carry origins.
   */
  function listFilter(counts, aiCount, otherPending) {
    if (!ui.component || ![0, 2, 4].includes(ui.view)) return '';
    const open = ui.view === 4 ? 0 : counts.pending[ui.view] || 0;
    const review = aiCount(ui.view);
    if (ui.originFilter === 'review' && !review) ui.originFilter = 'all';
    if (ui.originFilter === 'open' && !open && ui.view !== 4) ui.originFilter = 'all';
    const item = (id, label, n, tone) => `<button class="filter-word ${ui.originFilter === id ? 'on' : ''}" data-a="originFilter" data-id="${id}" aria-pressed="${ui.originFilter === id ? 'true' : 'false'}">${esc(label)}${n ? `<span class="n ${tone}">${n}</span>` : ''}</button>`;
    const words = [item('all', t('All', 'Tout'), 0, ''), ui.view !== 4 ? item('open', t('Open', 'Ouverts'), open, 'n-open') : '',
      review ? item('review', t('To review', 'À relire'), review, 'n-ai') : ''].join('');
    return `<div class="list-filter" role="group" aria-label="${esc(t('Show', 'Afficher'))}"><span class="muted small">${esc(t('Show', 'Afficher'))}</span>${words}<span class="grow"></span>${ui.view !== 4 ? viewDone(counts, open, otherPending) : ''}</div>`;
  }

  /** "Decisions: all done · Safety: 2 open ›" - only when this view has nothing open left (Clarify's job, 0.17.0). */
  function viewDone(counts, open, otherPending) {
    const names = VIEWS();
    if (open || !(counts.total[ui.view] > 0)) return '';
    const next = [0, 1, 2, 3, 4, 5].find((i) => i !== ui.view && counts.pending[i] > 0);
    const other = next === undefined && otherPending ? 7 : next;
    const n = other === 7 ? otherPending : other === undefined ? 0 : counts.pending[other];
    return `<span class="small view-done"><span class="tone-good">${esc(names[ui.view] + t(': all done', ' : tout est traité'))}</span>${other === undefined ? ''
      : ` · <button class="link" data-a="view" data-i="${other}">${esc(names[other] + t(`: ${n} open ›`, ` : ${n} ouvert${n > 1 ? 's' : ''} ›`))}</button>`}</span>`;
  }

  /** "+ Add": what a person adds to this component (a new component is added from the Components tab). */
  function addMenu() {
    const item = (label, cmd, does) => `<button class="ai-item" role="menuitem" ${cmd}><span class="grow"><b>${esc(label)}</b></span><span class="mono small muted">${esc(does)}</span></button>`;
    return `<div class="ai-menu add-menu" role="menu">${item(t('A question', 'Une question'), `data-a="openCommand" data-command="question add" data-component="${esc(ui.component)}"`, 'question add')}
  ${item(t('A rule', 'Une règle'), 'data-a="addRule"', 'rule add')}
  ${item(t('An assumption', 'Une hypothèse'), `data-a="openCommand" data-command="assumption add" data-component="${esc(ui.component)}"`, 'assumption add')}</div>`;
  }

  /** "All AI skills": the five, in the order of the work, plain names, the skill small, and why one cannot run yet. */
  function aiMenu() {
    const ai = aiOf();
    const sg = ai && ai.suggest ? ai.suggest.skill : '';
    const items = (AI().order || []).map((id) => {
      const def = skillDef(id);
      if (!def) return '';
      const blocked = ai && ai.blocked && ai.blocked[id];
      return `<button class="ai-item ${sg === id ? 'suggested' : ''}" role="menuitem" data-a="openSkill" data-skill="${esc(id)}" ${blocked ? 'disabled' : ''}>
  <span class="grow"><b>${esc(pair(def.label))}</b>${sg === id ? ` <span class="sugg">${esc(t('SUGGESTED', 'SUGGÉRÉ'))}</span>` : ''}<span class="small ${blocked ? 'tone-warn' : ''}">${esc(blocked ? pair(blocked) : pair(def.does))}</span></span>
  <span class="mono small muted">${esc(id)}</span></button>`;
    }).join('');
    const copy = `<button class="ai-item" role="menuitem" data-a="aiPrompt"><span class="grow"><b>${esc(t('Copy the card for another AI', 'Copier la fiche pour une autre IA'))}</b><span class="small">${esc(t("for an assistant that cannot read the project's files", 'pour un assistant qui ne lit pas les fichiers du projet'))}</span></span><span class="mono small muted">prompt</span></button>`;
    return `<div class="ai-menu" role="menu"><div class="ai-menu-head">${esc(t('WHAT THE AI CAN DO · in the order of the work', "CE QUE L'IA PEUT FAIRE · dans l'ordre du travail"))}</div>${items}<div class="menu-sep"></div>${copy}</div>`;
  }

  /**
   * The launch form of an AI skill (design A): what the AI reads, at most how many, where the request goes; on
   * the right what will happen, what the AI may add, what you still decide, and the exact request. Nothing runs
   * here: the request goes to the assistant, which runs archvow; what it adds comes back marked as the AI's.
   */
  function skillForm() {
    const id = ui.skill;
    const def = skillDef(id);
    if (!def) return `<p class="muted">${esc(t('Unknown skill.', 'Skill inconnu.'))}</p>`;
    const v = ui.skillValues;
    const tools = (S && S.aiTools && S.aiTools.length ? S.aiTools : ['copilot']);
    if (!v.tool || !tools.includes(v.tool)) v.tool = tools[0];
    const ids = componentIds();
    const field = (name, label, control) => `<div class="field"><label for="sk-${name}">${esc(label)}</label>${control}</div>`;
    const controls = [];
    if (def.target === 'brief') {
      const refs = (S.briefStatus ? list(S.briefStatus, 'files') : []).flatMap((f) => [str(f, 'file'), ...list(f, 'sections').filter((r) => str(r, 'section')).map((r) => str(f, 'file') + '#' + str(r, 'section'))]);
      controls.push(field('brief', t('The brief, or one section of it', "Le brief, ou une de ses sections"), `<input id="sk-brief" list="sk-briefs" data-input="skill" data-name="brief" data-keep="sk-brief" value="${esc(v.brief || '')}" placeholder="briefs/orders.md#cleaning"><datalist id="sk-briefs">${refs.map((r) => `<option value="${esc(r)}">`).join('')}</datalist>`));
    } else {
      controls.push(field('component', t('Component', 'Composant'), `<select id="sk-component" data-change="skill" data-name="component">${ids.map((c) => `<option ${c === v.component ? 'selected' : ''}>${esc(c)}</option>`).join('')}</select>`));
      if (!v.component && ids.length) v.component = ids[0];
    }
    if (def.max) controls.push(field('max', t('At most', 'Au plus'), `<span class="segmented">${['3', '5', '10'].map((n) => `<button class="${v.max === n ? 'on' : 'secondary'}" data-a="skillValue" data-name="max" data-value="${n}">${n}</button>`).join('')}</span><div class="muted small">${esc(t('The riskiest first. Fewer questions get read.', 'Les plus risquées d\'abord. Moins de questions, mieux lues.'))}</div>`));
    controls.push(field('tool', t('AI assistant (optional)', 'Assistant IA (optionnel)'), `<span class="segmented">${tools.map((x) => `<button class="${v.tool === x ? 'on' : 'secondary'}" data-a="skillValue" data-name="tool" data-value="${esc(x)}">${esc(x)}</button>`).join('')}</span><div class="muted small">${esc(t("Choose an integration; Copy instructions works with any AI that can read the project.", "Choisis une intégration ; Copier les consignes fonctionne avec toute IA ayant accès au projet."))}</div>`));
    const ai = aiOf(v.component);
    const blocked = def.target !== 'brief' && ai && ai.blocked && ai.blocked[id];
    const ready = def.target === 'brief' ? !!(v.brief || '').trim() : !!v.component;
    const text = skillRequest(id, v);
    const chat = v.tool === 'copilot';
    const shortcut = skillCommand(id, v);
    const steps = [
      chat ? t('You can copy the full instructions or open Copilot Chat with them already typed.', 'Tu peux copier les consignes complètes ou ouvrir Copilot Chat avec celles-ci préremplies.')
        : t('Copy the full instructions and paste them into an AI assistant with access to the project.', 'Copie les consignes complètes et colle-les dans un assistant IA ayant accès au projet.'),
      pair(def.reads), pair(def.may_add),
      t('What it adds appears in the component\'s tabs, marked AI and To review, for you to keep, change or remove.',
        'Ce qu\'elle ajoute apparaît dans les onglets du composant, marqué IA et À relire, pour que tu le gardes, le changes ou le retires.')];
    const skillStages = [t('Target', 'Cible'), t('AI handoff', 'Transmission IA'), t('Review', 'Récapitulatif')];
    ui.skillStep = Math.max(0, Math.min(ui.skillStep, skillStages.length - 1));
    const skillNav = `<nav class="guided-steps" aria-label="${esc(t('Skill steps', 'Étapes du skill'))}">${skillStages.map((label, index) =>
      `<button type="button" class="guided-step ${index === ui.skillStep ? 'current' : ''}" data-a="skillStage" data-index="${index}"><span>${index + 1}</span>${esc(label)}</button>`).join('')}</nav>`;
    const shownControls = ui.skillStep === 0 ? controls.slice(0, -1).join('')
      : ui.skillStep === 1 ? controls.slice(-1).join('')
      : `<div class="guided-review"><div class="guided-review-item"><b>${esc(def.target === 'brief' ? t('Brief', 'Brief') : t('Component', 'Composant'))}</b><span>${esc(def.target === 'brief' ? (v.brief || '—') : (v.component || '—'))}</span></div>
      ${def.max ? `<div class="guided-review-item"><b>${esc(t('At most', 'Au plus'))}</b><span>${esc(v.max || '5')}</span></div>` : ''}
      <div class="guided-review-item"><b>${esc(t('AI assistant', 'Assistant IA'))}</b><span>${esc(v.tool)}</span></div></div>`;
    const skillPager = `<div class="guided-skill-actions row wrap">${ui.skillStep > 0 ? btn(t('← Previous', '← Précédent'), 'skillPrevious', '', false, 'secondary') : ''}${ui.skillStep < 2 ? btn(t('Next →', 'Suivant →'), 'skillNext', '', false, 'secondary') : ''}</div>`;
    const right = `<aside class="what"><div class="what-head">${esc(t('WHAT WILL HAPPEN', 'CE QUI VA SE PASSER'))}</div>
  <ol class="steps">${steps.map((x) => `<li>${esc(x)}</li>`).join('')}</ol>
  <div class="two"><div class="box"><b class="tone-blue">${esc(t('The AI can', "L'IA peut"))}</b><div class="small">${esc(pair(def.may_add))}</div></div>
  <div class="box"><b class="tone-good">${esc(t('You still', 'Toi, tu'))}</b><div class="small">${esc(pair(def.you))}</div></div></div>
  <div class="muted small">${esc(t('Instructions to copy (for any AI)', "Consignes à copier (pour toute IA)"))}</div><pre class="request">${esc(text)}</pre></aside>`;
    return `<div class="skill-form"><h2>${esc(pair(def.label))} <span class="mono small muted">skill: archvow-${esc(id)}</span></h2>
  <p>${esc(pair(def.does))}</p>${blocked ? `<div class="alert warn">${esc(pair(blocked))}</div>` : ''}
  <div class="split"><div class="split-list">${skillNav}<section class="guided-panel">${shownControls}</section>${skillPager}</div>${right}</div>
  <div class="row end form-actions">${btn(t('Cancel', 'Annuler'), 'back', '', false, 'secondary')}
  ${shortcut ? btn(t('Copy skill command', 'Copier la commande skill'), 'askAi', attrs({ send: '0', format: 'skill' }), !ready, 'secondary') : ''}
  ${chat ? btn(t('Open in Copilot Chat', 'Ouvrir dans Copilot Chat'), 'askAi', attrs({ send: '1', format: 'instructions' }), !ready, 'secondary') : ''}
  ${btn(t('Copy instructions', 'Copier les consignes'), 'askAi', attrs({ send: '0', format: 'instructions' }), !ready)}</div></div>`;
  }

  /** A complete instruction for any agent, including one without an installed slash command. */
  function skillRequest(id, v) {
    const def = skillDef(id) || {};
    const wording = (key) => pair(def[key]) || '';
    const isBrief = def.target === 'brief';
    const target = String(v[isBrief ? 'brief' : 'component'] || '').trim();
    if (fr()) return [
      `Tâche Archvow : ${wording('label')}`,
      `Skill, s'il est installé : archvow-${id}`,
      `Répertoire de travail : le projet actuellement ouvert dans l'IDE`,
      `${isBrief ? 'Brief / section' : 'Composant'} : ${target}`,
      ...(id === 'clarify' ? [`Nombre maximum de questions : ${v.max || '5'}`] : []),
      '', `Objectif : ${wording('does')}`, `À lire : ${wording('reads')}`,
      `Modifications ou propositions possibles : ${wording('may_add')}`,
      `Validation humaine : ${wording('you')}`,
      '', 'Consignes :',
      '1. Lis le brief ou composant ciblé dans le dépôt, ainsi que les spécifications Archvow et les instructions locales pertinentes. Si le chemin contient #, utilise cette section précise.',
      `2. Si le skill archvow-${id} est disponible, utilise-le. Sinon, réalise l'objectif décrit, sans inventer de commande Archvow.`,
      '3. Respecte le mode Archvow du projet et les validations humaines ; ne présente jamais une proposition comme acceptée.',
      "4. Si tu ne peux pas lire les fichiers du projet, demande leur contenu avant de travailler ; un chemin seul n'est pas leur contenu.",
      '5. Résume les changements, propositions et points restant à valider.',
    ].join('\n');
    return [
      `Archvow task: ${wording('label')}`,
      `Skill, if installed: archvow-${id}`,
      'Workspace: the project currently open in the IDE',
      `${isBrief ? 'Brief / section' : 'Component'}: ${target}`,
      ...(id === 'clarify' ? [`Maximum questions: ${v.max || '5'}`] : []),
      '', `Goal: ${wording('does')}`, `Read first: ${wording('reads')}`,
      `You may add or propose: ${wording('may_add')}`,
      `Human approval: ${wording('you')}`,
      '', 'Instructions:',
      '1. Read the targeted brief or component in this repository, the Archvow specs and relevant local instructions. If the path includes #, use that specific section.',
      `2. If the archvow-${id} skill is installed, use it. Otherwise perform the described goal without inventing Archvow commands.`,
      "3. Respect the project's Archvow mode and human approval gates; do not treat a proposal as accepted.",
      '4. If you cannot access the project files, ask me for their contents before acting; the path alone is not the document.',
      '5. Summarize changes, proposals and items requiring human review.',
    ].join('\n');
  }

  function skillCommand(id, v) {
    const def = skillDef(id);
    const req = (def && def.request) || {};
    return String(req[v.tool] || '').replace(/\{(\w+)\}/g, (_, k) => v[k] || '');
  }

  /** "1 open item" next to the component's name (the count the sidebar shows), "✓ resolved" without. */
  function openBadge(id) {
    if (!id || !S || !S.snapshots[id]) return '';
    const n = (S.work.pendingByComponent || {})[id] || 0;
    if (!n) return `<span class="badge-done">✓ ${esc(t('resolved', 'traité'))}</span>`;
    return `<span class="badge-open">${n} ${esc(n === 1 ? t('open item', 'élément ouvert') : t('open items', 'éléments ouverts'))}</span>`;
  }

  /** type · layer · module · ticket · status: the component's place, from show --json and the spec. */
  function meta(view) {
    const entry = list(S.spec, 'containers').flatMap((c) => list(c, 'components')).find((x) => str(x, 'id') === ui.component) || {};
    return [str(view, 'type'), str(view, 'layer') || str(entry, 'layer'),
      str(entry, 'module') ? 'module ' + str(entry, 'module') : '',
      str(entry, 'spec') ? t('ticket ', 'ticket ') + str(entry, 'spec') : '',
      localizedState(str(view, 'status'))].filter((x) => x).join(' · ');
  }

  /** The items of a view on the left, the selected one's answer panel on the right (stacked when narrow);
   *  a view with no item shows "Nothing to display." alone, without an answer panel. */
  function split(left, right) {
    if (!right) return `<div class="split single"><div class="split-list">${left}</div></div>`;
    return `<div class="split"><div class="split-list">${left}</div><aside class="split-answer">${right}</aside></div>`;
  }

  /** The items as cards: "n · question", then the recorded answer (green) with who and when, or "● Open". */
  function cards(kind, rows, label, card) {
    const shown = rows.map((row, i) => [row, i, card(row, i)]).filter(([, , c]) => !c.origin || keepCard(c.open, c.origin.toReview));
    const hidden = rows.length - shown.length;
    return `<ul class="cards" data-scroll="cards-${kind}">${shown.map(([row, i, c]) => {
      const key = label(row, i);
      return `<li class="item-card ${ui.sel[kind] === key ? 'selected' : ''} ${c.open ? 'open' : 'done'}${c.origin && c.origin.toReview ? ' ai-wait' : ''}" data-a="sel" data-kind="${kind}" data-key="${esc(key)}">
  <div class="card-q" title="${esc(c.title)}"><b>${esc(c.number)}</b>${c.origin ? ' ' + originTag(c.origin) : ''} · ${esc(c.title)}</div>
  ${c.open ? `<div class="card-open">● ${esc(c.state || t('Open', 'Ouvert'))}</div>${c.note ? `<div class="card-note">${esc(short(c.note, 160))}</div>` : ''}`
    : `<div class="card-a" title="${esc(c.answer || '')}">${esc(short(c.answer || c.state || t('Resolved', 'Traité'), 220))}</div>`}
  ${c.by ? `<div class="card-by muted">${esc(c.by)}</div>` : ''}</li>`;
    }).join('') || `<li class="muted empty">${esc(t('Nothing to display.', 'Rien à afficher.'))}</li>`}${hidden ? `<li class="muted small hidden-note">${esc(t(`${hidden} hidden by the filter`, `${hidden} masqué${hidden === 1 ? '' : 's'} par le filtre`))}</li>` : ''}</ul>`;
  }

  /** "architect@example.com · 2026-10-08": who recorded an answer, and the day. */
  const byOn = (row) => [str(row, 'by'), str(row, 'on').substring(0, 10)].filter((x) => x).join(' · ');

  /** The answer panel's heading: "ANSWER DECISION 3", "DECISION 1 · ANSWERED". */
  /** A panel's head: its title and a state, green when `good` is true, amber when false, or in the tone named. */
  function answerHead(text, state, good) {
    const tone = typeof good === 'string' ? good : good ? 'good' : 'warn';
    return `<div class="answer-head"><span>${esc(text)}</span>${state ? ` <b class="tone-${tone}">${esc(state)}</b>` : ''}</div>`;
  }

  /** What the component uses and what uses it (show --json `uses`, `used_by`), each one a link: what a
   *  change to it may break. As the IntelliJ component header shows it. */
  function links(view) {
    const link = (id) => `<span class="link" data-a="pick" data-id="${esc(id)}">${esc(id)}</span>`;
    const uses = list(view, 'uses').map(String);
    const usedBy = list(view, 'used_by').map(String);
    const parts = [uses.length ? `${esc(t('Uses', 'Utilise'))}: ${uses.map(link).join(', ')}` : '',
      usedBy.length ? `${esc(t('Used by', 'Utilisé par'))}: ${usedBy.map(link).join(', ')}` : ''].filter(Boolean);
    return parts.length ? `<p class="links muted small">${parts.join('  ·  ')}</p>` : '';
  }

  /** The component's business examples (show --json `examples`) in its head: each one's state from its test's
   *  own verdict (archvow 1.21 `result`: ✔ passing, ✖ failing, ○ no test yet, · not run), or its files changed
   *  since recorded (ARCH055). The Examples view shows them whole. As the IntelliJ details show them. */
  function examples(view) {
    const rows = list(view, 'examples');
    if (!rows.length) return '';
    const item = (e) => {
      const state = exampleState(e);
      const changed = e.unchanged === false;
      const why = changed ? t('files changed since recorded (ARCH055)', 'fichiers modifiés depuis leur enregistrement (ARCH055)') : state.text;
      const tip = `${str(e, 'folder')}/ · ${str(e, 'level')} · ${why}`;
      return `<span class="${state.tone === 'good' && !changed ? 'ok' : state.tone === 'bad' || changed ? 'tone-bad' : 'tone-warn'}" title="${esc(tip)}">${changed ? '✖' : state.mark} ${esc(str(e, 'name'))}${str(e, 'ticket') ? ` (${esc(str(e, 'ticket'))})` : ''}</span>`;
    };
    return `<p class="examples small">${esc(t('Examples', 'Exemples'))}: ${rows.map(item).join('  ·  ')}</p>`;
  }

  /** One example's state, from its test's verdict (the test decides; archvow only shows): mark, tone, words. */
  function exampleState(e) {
    const result = str(e, 'result') || (e.tested === true ? 'not_run' : 'no_test');
    const files = list(e, 'files').filter((f) => ['differs', 'missing', 'extra'].includes(str(f, 'state')));
    const differ = files.map((f) => {
      const n = Number(f.differences) || 0;
      const table = ['table', 'records'].includes(str(f, 'kind'));
      if (str(f, 'state') === 'missing') return t(`${str(f, 'name')} missing in actual/`, `${str(f, 'name')} absent d'actual/`);
      if (str(f, 'state') === 'extra') return t(`${str(f, 'name')} only in actual/`, `${str(f, 'name')} seulement dans actual/`);
      return t(`${str(f, 'name')}: ${n} ${table ? 'row' : 'line'}${n === 1 ? ' differs' : 's differ'}`, `${str(f, 'name')} : ${n} ligne${n === 1 ? ' diffère' : 's diffèrent'}`);
    }).join('; ');
    if (result === 'passing') return { result, mark: '✔', tone: 'good', text: t('passing', 'réussi') + (differ ? ' · ' + t('actual/ differs in form only', 'actual/ ne diffère que par la forme') : '') };
    if (result === 'failing') return { result, mark: '✖', tone: 'bad', text: t('failing', 'en échec') + (differ ? ' - ' + differ : e.actual === false ? ' - ' + t('actual/ not written by the test', "actual/ pas écrit par le test") : '') };
    if (result === 'no_test') return { result, mark: '○', tone: 'warn', text: t('no marked test yet (ARCH056)', 'pas encore de test marqué (ARCH056)') };
    if (result === 'skipped') return { result, mark: '·', tone: 'muted', text: t('skipped in test-results.xml', 'sauté dans test-results.xml') };
    if (str(e, 'run_on')) return { result, mark: '·', tone: 'muted', text: t(`not in the last run (test-results.xml of ${str(e, 'run_on')})`, `absent du dernier lancement (test-results.xml du ${str(e, 'run_on')})`) };
    return { result, mark: '·', tone: 'muted', text: t('not run yet (make test writes test-results.xml)', 'pas encore lancé (make test écrit test-results.xml)') };
  }

  /** How many examples pass, fail, and in all: the Examples chip and the head's badge. */
  function exampleCounts(view) {
    const rows = view ? list(view, 'examples') : [];
    const states = rows.map((e) => exampleState(e).result);
    return { total: rows.length, passing: states.filter((x) => x === 'passing').length, failing: states.filter((x) => x === 'failing').length };
  }

  /** "1 example failing" next to the component's name: opens the Examples view. */
  function failingBadge(view) {
    const n = exampleCounts(view).failing;
    return n ? ` <button class="badge-bad" data-a="view" data-i="8" title="${esc(t('Show the Examples view', 'Afficher la vue Exemples'))}">${esc(t(`${n} example${n === 1 ? '' : 's'} failing`, `${n} exemple${n === 1 ? '' : 's'} en échec`))}</button>` : '';
  }

  /** The examples grouped by the rule they show (the scenario's Rule:), in their order; "No rule named" last. */
  function exampleGroups(view) {
    const rows = list(view, 'examples');
    const texts = view && typeof view.rule_texts === 'object' && view.rule_texts ? view.rule_texts : {};
    const rules = [...new Set(rows.map((e) => str(e, 'rule')))];
    const ordered = [...rules.filter((r) => r), ...(rules.includes('') ? [''] : [])];
    return ordered.map((rule) => ({ rule, text: rule ? str(texts, rule) : '', items: rows.filter((e) => str(e, 'rule') === rule) }));
  }

  /** The Examples view (0.18.0): the examples as cards grouped by rule, left; the selected one, right - its
   *  scenario, then each output file expected/ | actual/ side by side, by kind, with the differences marked. */
  function examplesView(view) {
    const rows = list(view, 'examples');
    if (!rows.length) {
      return split(`<p class="muted">${esc(t('No business example on this component.', "Aucun exemple métier sur ce composant."))}</p>
  <div class="row">${btn(t('Record an example…', 'Enregistrer un exemple…'), 'openCommand', attrs({ command: 'example add', component: ui.component }), false, 'secondary')}</div>`, '');
    }
    if (!rows.some((e) => str(e, 'name') === ui.sel.example)) {
      const first = rows.find((e) => exampleState(e).result === 'failing') || rows[0];
      ui.sel.example = str(first, 'name');
    }
    const grouped = exampleGroups(view).map((g) => `<div class="example-rule"><b class="mono">${esc(g.rule || t('No rule named', 'Aucune règle nommée'))}</b>${g.text ? ` <span class="muted small">${esc(g.text)}</span>` : ''}</div>
  <ul class="cards" data-scroll="cards-example-${esc(g.rule || 'none')}">${g.items.map((e) => {
    const state = exampleState(e);
    const name = str(e, 'name');
    return `<li class="item-card example-card ${ui.sel.example === name ? 'selected' : ''} ${state.result === 'passing' ? 'done' : 'open'}" data-a="sel" data-kind="example" data-key="${esc(name)}">
  <div class="card-q" title="${esc(name)}"><b class="mark tone-${state.tone}">${esc(state.mark)}</b> ${esc(name)}</div>
  <div class="card-note tone-${state.tone}" title="${esc(state.text)}">${esc(state.result === 'passing' ? [str(e, 'level'), byOn(e)].filter(Boolean).join(' · ') : state.text)}</div></li>`;
  }).join('')}</ul>`).join('');
    const selected = rows.find((e) => str(e, 'name') === ui.sel.example);
    return split(grouped, exampleDetail(view, selected));
  }

  /** The test of an example, from show --json `evidence` ("example NAME" -> ["file::test"]). */
  function exampleTest(view, name) {
    const row = list(view, 'evidence').find((r) => str(r, 'item') === 'example ' + name);
    const first = row && Array.isArray(row.tests) ? row.tests[0] : '';  // "file::test" texts (list() keeps objects only)
    return first ? String(first).split('::')[0] : '';
  }

  /** The selected example: what the business team said, and what the code gave. */
  function exampleDetail(view, e) {
    const name = str(e, 'name');
    const state = exampleState(e);
    const folder = str(e, 'folder');
    const files = list(e, 'files');
    let shown = files.find((f) => str(f, 'name') === ui.exampleFile[name]);
    if (!shown) shown = files.find((f) => ['differs', 'missing', 'extra'].includes(str(f, 'state'))) || files[0];
    const src = e.source && typeof e.source === 'object' ? e.source : null;
    const ref = src && str(src, 'file') ? (str(src, 'section') ? `${str(src, 'file')}#${str(src, 'section')}` : str(src, 'file')) : '';
    const test = exampleTest(view, name);
    const command = `archvow example add ${ui.component} ${name}`;
    const fileButtons = files.length > 1 ? `<div class="row wrap example-files">${files.map((f) => btn(str(f, 'name'), 'exampleFile', attrs({ id: name, key: str(f, 'name') })
      + ` title="${esc(str(f, 'kind') + ' · ' + str(f, 'state'))}"`, false, shown && str(shown, 'name') === str(f, 'name') ? 'on small' : 'secondary small')).join('')}</div>` : '';
    const opens = [
      shown ? btn(t('Open expected/', 'Ouvrir expected/'), 'openFile', attrs({ file: `${folder}/expected/${str(shown, 'name')}`, line: 0 }), str(shown, 'state') === 'extra', 'secondary small') : '',
      shown && e.actual ? btn(t('Open actual/', 'Ouvrir actual/'), 'openFile', attrs({ file: `${folder}/actual/${str(shown, 'name')}`, line: 0 }), str(shown, 'state') === 'missing', 'secondary small') : '',
      test ? btn(t('Open test', 'Ouvrir le test'), 'openFile', attrs({ file: test, line: 0 }), false, 'secondary small') : '',
    ].join('');
    const fix = state.result === 'failing' ? `<div class="alert bad"><b>${esc(t('Fix the code, not the example.', "Corrige le code, pas l'exemple."))}</b>
  <div>${esc(t('The AI is told the same. If the business team says the expected result is wrong, a person replaces the files and records them again:', "L'IA a la même consigne. Si le métier dit que le résultat attendu est faux, une personne remplace les fichiers et les enregistre à nouveau :"))}</div>
  <div class="row">${terminalCommand(command, { copy: false })}${btn(t('Copy command', 'Copier la commande'), 'copyText', attrs({ text: command }), false, 'small')}</div></div>`
      : state.result === 'no_test' ? `<div class="alert warn"><b>${esc(t('No test yet, so no actual/.', "Pas encore de test, donc pas d'actual/."))}</b>
  <div>${esc(t(`Ask the AI: implement the test of example ${name}. Its card lists the files and the mark @pytest.mark.example("${name}").`,
    `Demande à l'IA : implémente le test de l'exemple ${name}. Sa fiche donne les fichiers et la marque @pytest.mark.example("${name}").`))}</div></div>` : '';
    return `${answerHead(t('Example', 'Exemple') + (str(e, 'ticket') ? ' · ' + str(e, 'ticket') : ''), state.mark + ' ' + (state.result === 'failing' ? t('failing', 'en échec') : state.result === 'passing' ? t('passing', 'réussi') : state.result === 'no_test' ? t('no test yet', 'pas encore de test') : t('not run', 'pas lancé')), state.tone)}
  <h3 class="example-name">${esc(name)}</h3>
  <p class="muted small">${esc([str(e, 'rule') ? t('rule ', 'règle ') + str(e, 'rule') : t('no rule named', 'aucune règle nommée'), str(e, 'level'), byOn(e),
    str(e, 'run_on') ? t('last run ', 'dernier lancement ') + str(e, 'run_on') : ''].filter(Boolean).join(' · '))}</p>
  ${ref ? `<p class="drawn-from muted small">${esc(t('Drawn from', 'Tiré de'))}: <span class="link" data-a="openBrief" data-ref="${esc(ref)}">${esc(ref)}</span></p>` : ''}
  ${e.unchanged === false ? `<p class="tone-bad small">${esc(t('Its files changed since they were recorded (ARCH055): put them back, or a person records them again.', 'Ses fichiers ont changé depuis leur enregistrement (ARCH055) : les remettre, ou une personne les enregistre à nouveau.'))}</p>` : ''}
  ${str(e, 'scenario') ? `<pre class="plain scenario">${esc(str(e, 'scenario'))}</pre>` : ''}
  ${fileButtons}${shown ? exampleDiff(shown, e) : ''}
  <div class="row wrap">${opens}</div>${fix}`;
  }

  /** One output file, expected/ | actual/ side by side (show --json `files`): a table (CSV, JSON Lines) row by
   *  row, the changed cells marked; a document or a text line by line. + only in actual, − only in expected,
   *  ~ changed. archvow compared texts; the test decided. */
  function exampleDiff(f, e) {
    const table = ['table', 'records'].includes(str(f, 'kind'));
    const state = str(f, 'state');
    const rows = list(f, 'rows');
    const header = f.header && typeof f.header === 'object' ? f.header : {};
    const head = table ? (Array.isArray(header.expected) ? header.expected : Array.isArray(header.actual) ? header.actual : []) : [];
    const width = Math.max(1, head.length, ...rows.map((r) => Math.max(Array.isArray(r.expected) ? r.expected.length : 0, Array.isArray(r.actual) ? r.actual.length : 0)));
    const mark = { same: '', missing: '−', extra: '+', changed: '~' };
    const cells = (values, changed) => {
      if (!table) return `<td class="line">${values === null || values === undefined ? '' : esc(String(values))}</td>`;
      if (!Array.isArray(values)) return `<td class="none" colspan="${width}"></td>`;
      return Array.from({ length: width }, (_, i) => `<td class="${changed.includes(i) ? 'cell-changed' : ''}">${esc(values[i] === undefined ? '' : String(values[i]))}</td>`).join('');
    };
    const body = rows.map((r) => {
      const op = str(r, 'op');
      const changed = Array.isArray(r.cells) ? r.cells : [];
      return `<tr class="op-${esc(op)}"><td class="mark">${esc(op === 'extra' ? '' : mark[op] || '')}</td>${cells(r.expected, changed)}<td class="gap"></td><td class="mark">${esc(op === 'missing' ? '' : mark[op] || '')}</td>${cells(r.actual, changed)}</tr>`;
    }).join('');
    const colspan = table ? width : 1;
    const titles = `<tr class="sides"><th></th><th colspan="${colspan}">expected/ <span class="muted">· ${esc(t('locked', 'verrouillé'))}</span></th><th class="gap"></th><th></th><th colspan="${colspan}">actual/ <span class="muted">· ${esc(e.actual ? (str(e, 'actual_on') ? t('written ', 'écrit le ') + str(e, 'actual_on') : t('last run', 'dernier lancement')) : t('none yet', 'pas encore'))}</span></th></tr>`;
    const columns = table && head.length ? `<tr class="columns"><th></th>${head.map((h) => `<th>${esc(String(h))}</th>`).join('')}${head.length < width ? `<th colspan="${width - head.length}"></th>` : ''}<th class="gap"></th><th></th>${(Array.isArray(header.actual) ? header.actual : head).map((h) => `<th>${esc(String(h))}</th>`).join('')}</tr>` : '';
    const summary = state === 'no_actual' ? t('expected only: the test has not written actual/ yet', "expected seulement : le test n'a pas encore écrit actual/")
      : state === 'same' ? t('same as expected/', 'identique à expected/')
        : state === 'missing' ? t('missing in actual/', "absent d'actual/") : state === 'extra' ? t('only in actual/', 'seulement dans actual/')
          : t(`${f.differences} difference${Number(f.differences) === 1 ? '' : 's'}`, `${f.differences} différence${Number(f.differences) === 1 ? '' : 's'}`);
    return `<div class="example-file"><div class="row"><b class="mono">${esc(str(f, 'name'))}</b><span class="muted small">${esc(str(f, 'kind'))} · ${esc(summary)}</span></div>
  ${str(f, 'note') ? `<p class="tone-warn small">${esc(str(f, 'note'))}</p>` : ''}
  ${state === 'no_actual' ? `<p class="muted small">${esc(t('Open expected/ to read it.', 'Ouvre expected/ pour le lire.'))}</p>` : `<div class="diff-box" data-scroll="diff-${esc(str(f, 'name'))}"><table class="example-diff ${table ? 'table' : 'lines'}">${titles}${columns}${body}</table></div>
  <p class="legend muted small"><span class="op-extra">+</span> ${esc(t('only in actual (the code kept it)', 'seulement dans actual (le code l\'a gardé)'))} · <span class="op-missing">−</span> ${esc(t('only in expected (the code dropped it)', 'seulement dans expected (le code l\'a écarté)'))}${table ? ` · <span class="op-changed">~</span> ${esc(t('same place, another value', 'même place, autre valeur'))}` : ''}</p>`}</div>`;
  }

  /** The briefs folders of archvow 1.17 (S.briefs, read by the extension): the --from field offers their
   *  files and headings (file#section, archvow's slugs); free text stays allowed, archvow decides. */
  function briefChoices() {
    const b = (S && S.briefs) || { folders: [], files: [] };
    return b.files.flatMap((f) => [f.file, ...f.sections.map((x) => `${f.file}#${x.slug}`)]);
  }
  function briefField(name, value, id) {
    const b = (S && S.briefs) || { folders: [], files: [] };
    const options = briefChoices().map((c) => `<option value="${esc(c)}">`).join('');
    const where = b.folders.length ? b.folders.map((f) => f + '/').join(', ') : t('none declared', 'aucun déclaré');
    return `<input list="brief-choices" data-input="form" data-name="${esc(name)}" data-keep="${id}" value="${esc(value)}"><datalist id="brief-choices">${options}</datalist>
  <div class="muted small">${esc(t('Brief folders', 'Dossiers de briefs'))}: ${esc(where)}${b.files.length ? '' : '  ·  ' + esc(t('no .md or .txt file in them yet', 'aucun fichier .md ou .txt pour le moment'))}</div>`;
  }

  /** "Drawn from: specs/001-orders/plan.md#paying-an-invoice" (show --json `source`): opens it at that heading. */
  function drawnFrom(view) {
    const src = view && typeof view.source === 'object' && view.source ? view.source : null;
    const file = src ? str(src, 'file') : '';
    if (!file) return '';
    const ref = str(src, 'section') ? `${file}#${str(src, 'section')}` : file;
    return `<p class="drawn-from muted small">${esc(t('Drawn from', 'Tiré de'))}: <span class="link" data-a="openBrief" data-ref="${esc(ref)}" title="${esc(t('Open the brief at this heading', 'Ouvrir la brief à ce titre'))}">${esc(ref)}</span></p>`;
  }

  /** archvow's way to add a briefs folder (its refusal of --from, ARCH039): "archvow init <pkg> --force -a briefs=a,b". */
  function addFolder(text) {
    const m = /archvow init (\S+) --force -a briefs=(\S+)/.exec(text || '');
    return m ? { package: m[1], answer: 'briefs=' + m[2] } : null;
  }

  /** The button under a result that says so: opens the architect's init --force form, prefilled (shown, then confirmed). */
  function addFolderButton(text) {
    const add = addFolder(text);
    if (!add) return '';
    return `<div class="row">${btn(t('Add this folder (architect)', 'Ajouter ce dossier (architecte)'), 'addFolder', attrs({ package: add.package, answer: add.answer })
      + ` title="${esc(`archvow init ${add.package} --force -a ${add.answer}`)}"`, false, 'secondary')}</div>`;
  }

  /** The component's progress, read from show --json (no engine change): decisions made, safety answered,
   *  tasks done, examples tested; a part with nothing to count is left out. As IntelliJ's ArchvowProgress. */
  function progress(view) {
    const part = (key, label, rows, done) => ({ key, label, done: rows.filter(done).length, total: rows.length });
    return [
      part('decisions', t('Decisions made', 'Décisions prises'), list(view, 'decisions'), (d) => d.answer !== null && d.answer !== undefined && String(d.answer).trim() !== ''),
      part('safety', t('Safety answered', 'Sûreté traitée'), list(view, 'safety'), (q) => q.status === 'answered'),
      part('tasks', t('Tasks done', 'Tâches faites'), list(view, 'tasks'), (x) => x.done === true),
      part('examples', t('Examples passing', 'Exemples réussis'), list(view, 'examples'), (e) => exampleState(e).result === 'passing' && e.unchanged !== false),
    ].filter((p) => p.total > 0).map((p) => ({ ...p, complete: p.done === p.total }));
  }

  /** One bar under the component's name: a segment per part, green when complete, amber otherwise. */
  function progressBar(view) {
    const parts = progress(view);
    if (!parts.length) return '';
    const said = (p) => `${p.label}: ${p.done}/${p.total}`;
    // 10 % steps (no inline style in the page); never full when not complete, never empty when started
    const fill = (p) => (p.complete ? 100 : Math.min(90, Math.max(p.done ? 10 : 0, Math.round((10 * p.done) / p.total) * 10)));
    return `<div class="progress" title="${esc(parts.map(said).join(' · '))}" aria-label="${esc(t('Progress', 'Avancement'))}">${parts.map((p) =>
      `<div class="progress-part ${p.complete ? 'complete' : 'partial'}" data-part="${p.key}" title="${esc(said(p))}">
  <div class="progress-label small">${esc(p.label)} <b>${p.done}/${p.total}</b></div><div class="progress-track"><div class="progress-fill p${fill(p)}"></div></div></div>`).join('')}</div>`;
  }

  /** Why the component's total can exceed a view's count (as the IntelliJ workspace says). */
  function pendingExplanation() {
    if (!ui.component) return '';
    let total = 0;
    let data = 0;
    let other = 0;
    for (const item of S.work.pending) {
      if (item.component !== ui.component || item.source === 'check') continue;
      total++;
      const category = item.preset.view || 'other';
      if (category === 'data') data++;
      if (category === 'other' && isLocalOther(item)) other++;
    }
    if (!total) return '';
    return `<p class="muted small" title="${esc(t('Component-local pending work only. Project checks are separate.', 'Uniquement le travail local au composant ; les contrôles du projet sont distincts.'))}">${esc(fr()
      ? `${total} en attente pour ${ui.component} · ${data} dans Données et tâches` + (other ? ` · ${other} dans Autres` : '')
      : `${total} pending for ${ui.component} · ${data} in Data & tasks` + (other ? ` · ${other} in Others` : ''))}</p>`;
  }

  /** The selected row of a view; none yet (a component just opened): the first open one, else the first. */
  function current(kind, rows, label, isOpen) {
    if (!rows.length) return null;
    let i = rows.findIndex((r, n) => label(r, n) === ui.sel[kind]);
    // 0.17.0 (Clarify is no button any more): once the saved item is answered, the next open one shows
    if (ui.advance === kind && i >= 0 && !isOpen(rows[i], i)) {
      ui.advance = '';
      const after = [...rows.keys()].slice(i + 1).concat([...rows.keys()].slice(0, i)).find((n) => isOpen(rows[n], n));
      if (after !== undefined) { i = after; ui.sel[kind] = label(rows[i], i); }
    }
    if (i < 0) {
      i = Math.max(0, rows.findIndex((r, n) => isOpen(r, n)));
      ui.sel[kind] = label(rows[i], i);
    }
    return rows[i];
  }

  function draft(key, initial) {
    return key in ui.drafts ? ui.drafts[key] : initial;
  }

  /**
   * The answer field and its buttons: secondary ones on the left, Undo changes and the main one on the
   * right. Undo changes can be pressed only once the text differs from the recorded one: it puts that
   * text back, and saves or removes nothing.
   */
  function answerEditor(key, initial, placeholder, secondary, save, target, disabled = false) {
    const edited = key in ui.drafts && ui.drafts[key] !== initial;
    return `<textarea class="answer-field" rows="5" data-input="draft" data-key="${esc(key)}" data-keep="${esc(key)}" data-initial="${esc(initial)}" placeholder="${esc(placeholder)}" ${disabled ? 'disabled' : ''}>${esc(draft(key, initial))}</textarea>
  <div class="answer-actions"><span class="row wrap">${secondary}</span>
  <span class="row">${btn(t('Undo', 'Annuler'), 'cancelDraft', attrs({ key, target }) + ` title="${esc(undoTip(edited))}"`, disabled || !edited, 'link undo')}${save}</span></div>`;
  }

  function undoTip(edited) {
    return edited ? t('Put back the recorded text in the field (nothing is saved or removed)', 'Remettre le texte enregistré dans le champ (rien n\'est enregistré ni supprimé)')
      : t('Nothing to undo: the field holds the recorded text', 'Rien à annuler : le champ contient le texte enregistré');
  }

  /** Optional structured editor: relevant only when the question is about column properties.
   * It never changes the CLI schema, guesses no nullability, and does not save on its own. */
  function canEditColumns(question) {
    if (canEditParameters(question)) return false;  // name: type = default: the parameters table
    const q = String(question || '').toLocaleLowerCase();
    const keywords = (((I.forms || {}).smart_answers || {}).schema_columns || {}).keywords || [];
    const aboutColumn = /\bcolumns?|\bfields?|colonnes?|champs?/i.test(q);
    const aboutProperty = /(nullab|not null|required|optional|person|privat|gdpr|rgpd|obligatoire|facultati|manquant|missing|types?|datatype)/i.test(q);
    return aboutColumn && aboutProperty && keywords.some((k) => q.includes(k.toLocaleLowerCase()));
  }

  function knownColumnRows(view) {
    const names = new Map();
    const blank = (name) => ({ name, type: '', nullable: 'unknown', personal: 'unknown', note: '' });
    for (const group of list(view, 'known_columns')) {
      for (const name of Array.isArray(group.columns) ? group.columns : [])
        if (typeof name === 'string' && name.trim()) names.set(name.trim(), blank(name.trim()));
    }
    for (const c of list(view, 'columns')) {
      const name = str(c, 'name');
      if (!name) continue;
      const kind = str(c, 'class');
      const base = names.get(name) || blank(name);
      const personal = ['personal', 'special'].includes(kind) ? 'yes' : kind === 'none' ? 'no' : 'unknown';
      names.set(name, { ...base, type: str(c, 'type') || base.type, personal });
    }
    return Array.from(names.values()).slice(0, 60);
  }

  /** What a column question asks about: its type, whether it may be null, whether it is personal data. */
  function columnAsks(question) {
    const q = String(question || '').toLocaleLowerCase();
    return { type: /(\btypes?\b|datatype|data type)/.test(q), nullable: /(null|requir|option|obligat|facultat)/.test(q),
      personal: /(person|privat|gdpr|rgpd)/.test(q) };
  }

  function structuredAnswer(rows, question = '') {
    const asks = columnAsks(question);
    return rows.filter((r) => r.name.trim()).map((r) => {
      const parts = [];
      if (asks.type) parts.push(r.type.trim() || t('type unknown', 'type inconnu'));
      if (asks.nullable) parts.push(r.nullable === 'yes' ? 'nullable' : r.nullable === 'no' ? t('not null', 'non nullable') : t('nullability unknown', 'nullabilité inconnue'));
      if (asks.personal) parts.push(r.personal === 'yes' ? t('personal data', 'donnée personnelle') : r.personal === 'no' ? t('not personal data', 'non personnel') : t('personal-data classification unknown', 'classification des données inconnue'));
      const note = String(r.note || '').trim();
      return r.name.trim() + ': ' + parts.join(', ') + (note ? ` (${note})` : '');
    }).join('\n');
  }

  /** Three fixed answers as buttons, the chosen one pressed (the row's Nullable, Personal...). */
  function smartSegments(kind, key, row, name, value, options) {
    return `<div class="segmented compact" role="radiogroup">${options.map(([id, label]) => `<button type="button" role="radio" aria-checked="${value === id}" class="${value === id ? 'on' : 'secondary'}" data-a="smartSeg" ${attrs({ kind, key, row, name, value: id })}>${esc(label)}</button>`).join('')}</div>`;
  }

  const TRASH = () => icon('remove');

  function smartColumns(view, row, key) {
    if (!canEditColumns(str(row, 'question'))) return '';
    if (!ui.smartRows[key]) ui.smartRows[key] = knownColumnRows(view).map((x) => ({ ...x }));
    const rows = ui.smartRows[key];
    if (!rows.length) rows.push({ name: '', type: '', nullable: 'unknown', personal: 'unknown', note: '' });
    ui.smartQuestions[key] = str(row, 'question');
    const asks = columnAsks(ui.smartQuestions[key]);
    const answerText = structuredAnswer(rows, ui.smartQuestions[key]);
    // only what this question asks about: a personal-data question has no Nullable column
    const heads = [t('Column', 'Colonne'), ...(asks.type ? [t('Type', 'Type')] : []), ...(asks.nullable ? [t('Nullable?', 'Nullable ?')] : []),
      ...(asks.personal ? [t('Personal data?', 'Donnée personnelle ?')] : []), t('Notes', 'Notes'), ''];
    const cols = ['minmax(110px,1.2fr)', ...(asks.type ? ['minmax(90px,.9fr)'] : []), ...(asks.nullable ? ['auto'] : []), ...(asks.personal ? ['auto'] : []), 'minmax(90px,1fr)', '28px'].join(' ');
    const field = (i, name, value, extra = '') => `<input data-input="smartField" data-key="${esc(key)}" data-row="${i}" data-name="${name}" value="${esc(value)}" ${extra}>`;
    return `<details class="card smart-answer"${ui.smartExpanded === key ? ' open' : ''} data-smart-key="${esc(key)}"><summary>${esc(t('Answer with a table (optional)', 'Répondre avec un tableau (facultatif)'))}</summary>
      <p class="muted small">${esc(t('One row per column: what is not known stays Unknown. It writes the answer below for you to read before saving.', 'Une ligne par colonne : ce qui n’est pas connu reste Inconnu. Cela écrit la réponse ci-dessous, à relire avant d’enregistrer.'))}</p>
      <datalist id="archvow-type-suggestions">${['string','integer','long','float','double','decimal','boolean','date','timestamp'].map((type) => `<option value="${type}">`).join('')}</datalist>
      <div class="smart-grid" style="grid-template-columns:${cols}">${heads.map((h) => `<div class="smart-head">${esc(h)}</div>`).join('')}
      ${rows.map((r, i) => [field(i, 'name', r.name, `placeholder="${esc(t('column', 'colonne'))}"`),
        ...(asks.type ? [field(i, 'type', r.type, `list="archvow-type-suggestions" placeholder="${esc(t('Unknown', 'Inconnu'))}"`)] : []),
        ...(asks.nullable ? [smartSegments('column', key, i, 'nullable', r.nullable, [['no', t('Never null', 'Jamais nul')], ['yes', 'Nullable'], ['unknown', t('Unknown', 'Inconnu')]])] : []),
        ...(asks.personal ? [smartSegments('column', key, i, 'personal', r.personal, [['yes', t('Yes', 'Oui')], ['no', t('No', 'Non')], ['unknown', t('Unknown', 'Inconnu')]])] : []),
        field(i, 'note', r.note || '', `placeholder="${esc(t('optional', 'facultatif'))}"`),
        `<button class="icon smart-remove" data-a="smartRemove" ${attrs({ key, row: i })} title="${esc(t('Remove this column from the table', 'Retirer cette colonne du tableau'))}" aria-label="${esc(t('Remove', 'Retirer'))}">${TRASH()}</button>`].join('')).join('')}</div>
      <div class="row wrap">${btn(t('+ Add column', '+ Ajouter une colonne'), 'smartAdd', attrs({ key }), false, 'secondary')}
      ${btn(t('Use this answer', 'Utiliser cette réponse'), 'smartUse', attrs({ key }), !answerText.trim(), 'secondary')}</div>
      <div class="muted small">${esc(t('Answer preview', 'Aperçu de la réponse'))}</div>
      <pre class="smart-preview" data-preview="column">${esc(answerText || t('Add a column to prepare a response.', 'Ajoute une colonne pour préparer la réponse.'))}</pre>
    </details>`;
  }

  // ---------------------------------------------------------------- parameters: name: type = default

  /** A question that asks for parameters as `name: type = default` (the config, settings of a template). */
  function canEditParameters(question) {
    const q = String(question || '').toLocaleLowerCase();
    const markers = ((((I.forms || {}).smart_answers || {}).parameters || {}).markers) || ['name: type', 'nom: type'];
    return markers.some((m) => q.includes(String(m).toLocaleLowerCase()));
  }

  /** `input_path: str, run_date: date = today (the day to read)` -> rows; null for an answer in sentences.
   * The same reading as archvow's runline.parameters: no default = required, the older (required) too. */
  function parseParameters(text) {
    const flat = String(text || '').trim().replace(/\.$/, '');
    if (!flat || /^(none|aucun|aucune|no|non|-)$/i.test(flat)) return [];
    const rows = [];
    for (const part of flat.split(/[,;\n]\s*(?=[a-z_][a-z0-9_]*\s*:)/)) {
      const m = /^\s*([a-z_][a-z0-9_]*)\s*:\s*([^=(]+?)\s*(?:=\s*([^()]+?))?\s*(\((.*)\))?\s*$/.exec(part);
      // a type is a word (str, date, list[str], str | None): a sentence is not a list of parameters
      if (!m || !/^[A-Za-z_][\w.]*(\[[\w., |]*\])?( *\| *[A-Za-z_][\w.]*)*$/.test(m[2].trim())) return null;
      const note = (m[5] || '').replace(/^(required|obligatoire)$/i, '');
      rows.push({ name: m[1], type: m[2].trim(), default: (m[3] || '').trim(), note: note.trim() });
    }
    return rows;
  }

  function parametersAnswer(rows) {
    return rows.filter((r) => r.name.trim()).map((r) => {
      const type = r.type.trim() || 'str';
      const value = String(r.default || '').trim();
      const note = String(r.note || '').trim().replace(/[()]/g, '');
      return `${r.name.trim()}: ${type}${value ? ' = ' + value : ''}${note ? ` (${note})` : ''}`;
    }).join(', ');
  }

  function smartParameters(row, key, initial) {
    if (!canEditParameters(str(row, 'question'))) return '';
    if (!ui.paramRows[key]) ui.paramRows[key] = (parseParameters(initial) || []).map((x) => ({ ...x }));
    const rows = ui.paramRows[key];
    if (!rows.length) rows.push({ name: '', type: 'str', default: '', note: '' });
    const conf = (((I.forms || {}).smart_answers || {}).parameters || {});
    const types = Array.isArray(conf.types) ? conf.types : ['str', 'int', 'float', 'bool', 'date'];
    const answerText = parametersAnswer(rows);
    const field = (i, name, value, extra = '') => `<input data-input="paramField" data-key="${esc(key)}" data-row="${i}" data-name="${name}" value="${esc(value)}" ${extra}>`;
    return `<details class="card smart-answer"${ui.smartExpanded === key ? ' open' : ''} data-smart-key="${esc(key)}"><summary>${esc(t('Answer with a table (optional)', 'Répondre avec un tableau (facultatif)'))}</summary>
      <p class="muted small">${esc(pair(conf.note) || t('One row per parameter. No default = required.', 'Une ligne par paramètre. Sans défaut = obligatoire.'))}</p>
      <datalist id="archvow-param-types">${types.map((x) => `<option value="${esc(x)}">`).join('')}</datalist>
      <div class="smart-grid" style="grid-template-columns:minmax(110px,1.1fr) minmax(70px,.6fr) minmax(110px,1fr) auto minmax(90px,1fr) 28px">
      ${[t('Parameter', 'Paramètre'), t('Type', 'Type'), t('Default', 'Défaut'), '', t('Notes', 'Notes'), ''].map((h) => `<div class="smart-head">${esc(h)}</div>`).join('')}
      ${rows.map((r, i) => [field(i, 'name', r.name, 'placeholder="input_path"'), field(i, 'type', r.type, 'list="archvow-param-types"'),
        field(i, 'default', r.default, `placeholder="${esc(t('none: required', 'aucun : obligatoire'))}"`),
        `<span class="chip ${String(r.default || '').trim() ? '' : 'warn'}" data-required="${i}">${esc(String(r.default || '').trim() ? t('optional', 'facultatif') : t('required', 'obligatoire'))}</span>`,
        field(i, 'note', r.note || '', `placeholder="${esc(t('optional', 'facultatif'))}"`),
        `<button class="icon smart-remove" data-a="paramRemove" ${attrs({ key, row: i })} title="${esc(t('Remove this parameter from the table', 'Retirer ce paramètre du tableau'))}" aria-label="${esc(t('Remove', 'Retirer'))}">${TRASH()}</button>`].join('')).join('')}</div>
      <div class="row wrap">${btn(t('+ Add parameter', '+ Ajouter un paramètre'), 'paramAdd', attrs({ key }), false, 'secondary')}
      ${btn(t('Use this answer', 'Utiliser cette réponse'), 'paramUse', attrs({ key }), !answerText.trim(), 'secondary')}</div>
      <div class="muted small">${esc(t('Answer preview', 'Aperçu de la réponse'))}</div>
      <pre class="smart-preview" data-preview="param">${esc(answerText || t('Add a parameter to prepare a response.', 'Ajoute un paramètre pour préparer la réponse.'))}</pre>
    </details>`;
  }

  function decisionsView(view) {
    const rows = list(view, 'decisions');
    const label = (r) => 'D' + str(r, 'number');
    const isOpen = (r) => !str(r, 'answer');
    const row = current('decision', rows, label, isOpen);
    const ai = aiOf();
    const originOf = (i) => (ai && ai.decisions[i]) || null;
    const left = cards('decision', rows, label, (r, i) => ({ number: str(r, 'number'), title: str(r, 'question'), open: isOpen(r), answer: str(r, 'answer'),
      by: isOpen(r) ? '' : byOn(r), note: str(r, 'proposed') ? t('AI proposal: ', 'Proposition IA : ') + str(r, 'proposed') : '', origin: originOf(i) }));
    let right = `<p class="muted">${esc(t('Select a decision.', 'Sélectionne une décision.'))}</p>`;
    if (row) {
      const number = str(row, 'number');
      const answer = str(row, 'answer');
      const proposed = str(row, 'proposed');
      const suggested = str(row, 'suggested');
      const pending = !answer;
      const key = `d:${ui.component}:${number}`;
      const origin = originOf(rows.indexOf(row));
      const asked = origin && origin.tag === 'ai' && origin.toReview && !origin.proposal;  // the AI's question, not kept yet
      // 0.17.0: one button (Save answer); the rest are links where they belong - Use beside the suggestion,
      // Accept beside the AI's proposal, Keep / Remove beside why the AI asked, Save as a proposal under ▾
      const keepRemove = asked ? `<p class="small links">${btn(t('Keep, answer later', 'Garder, répondre plus tard'), 'openCommand', attrs({ command: 'question accept', component: ui.component, number })
        + ` title="${esc(t('The question stays open, as the team\'s own (archvow question accept)', "La question reste ouverte, comme celle de l'équipe (archvow question accept)"))}"`, false, 'link')} · ${btn(t('Remove question', 'Retirer la question'), 'openCommand', attrs({ command: 'question remove', component: ui.component, number }), false, 'link')}</p>` : '';
      const save = btn(t('Save answer', 'Enregistrer la réponse'), 'decide', attrs({ action: 'decision make' }));
      // 0.17.2: Save as a proposal is a real button beside Save answer (the ▾ menu of 0.17.0 hid it)
      const proposeBtn = pending ? btn(t('Save as a proposal', 'Enregistrer comme proposition'), 'decide', attrs({ action: 'decision propose' })
        + ` title="${esc(t('Recorded, not yet the answer (archvow decision propose)', 'Enregistrée, pas encore la réponse (archvow decision propose)'))}"`, false, 'secondary') : '';
      right = answerHead(pending ? t(`Answer decision ${number}`, `Répondre à la décision ${number}`) : t(`Decision ${number}`, `Décision ${number}`),
        pending ? '' : t('ANSWERED', 'TRAITÉE'), true) + (origin && origin.tag ? `<p>${originTag(origin)}</p>` : '')
        + `<p class="answer-q">${esc(str(row, 'question'))}</p>` + aiWhy(origin, t('Why the AI asked', "Pourquoi l'IA a demandé")) + keepRemove
        + (asked ? `<p class="muted small">${esc(t('Save answer keeps the question and records your decision in one step.', 'Enregistrer la réponse garde la question et enregistre ta décision en une fois.'))}</p>` : '')
        + (answer && byOn(row) ? `<p class="muted small">${esc(t('Recorded by ', 'Enregistrée par ') + byOn(row))}</p>` : '')
        + (proposed ? `<p class="hint"><b class="tone-blue">${esc(t('AI proposal', 'Proposition IA'))}</b> ${esc(proposed)}${pending ? ' · ' + btn(t('Accept', 'Accepter'), 'decide', attrs({ action: 'decision accept' }) + ` title="${esc(t('Take the AI\'s proposal as the answer (decision accept)', "Prendre la proposition de l'IA comme réponse (decision accept)"))}"`, false, 'link') : ''}</p>` : '')
        + (suggested && pending ? `<p class="hint"><b>${esc(t('Suggested', 'Suggéré'))}</b> ${esc(suggested)} · ${btn(t('Use', 'Utiliser'), 'useSuggestion', ` title="${esc(t('Put the suggestion in the answer field', 'Mettre la suggestion dans le champ de réponse'))}"`, false, 'link')}</p>` : '')
        // archvow 1.19: a question with no safe default offers answers to start from, none chosen
        + (pending && Array.isArray(row.choices) && row.choices.length ? `<p class="hint"><b>${esc(t('No default (it depends on this project) - to start from:', 'Pas de défaut (cela dépend de ce projet) - pour partir de :'))}</b></p>
  <div class="row wrap choices">${row.choices.map((c) => btn(String(c), 'useChoice', attrs({ key, value: String(c) }), false, 'secondary small')).join('')}</div>` : '')
        + smartColumns(view, row, key) + smartParameters(row, key, draft(key, answer || proposed || suggested))
        + answerEditor(key, answer || proposed || suggested, t('Your answer or proposal', 'Votre réponse ou proposition'), proposeBtn, save, 'decision')
        + confirmation('decision') + resultCard('decision');
    }
    return split(left, rows.length ? right : '');
  }

  function questionsView(view) {
    const rows = list(view, 'questions');
    const label = (r) => str(r, 'key') || str(r, 'number');
    const isOpen = (r) => !str(r, 'answer');
    const row = current('question', rows, label, isOpen);
    const left = cards('question', rows, label, (r) => ({ number: label(r), title: str(r, 'question'), open: isOpen(r), answer: str(r, 'answer'),
      by: isOpen(r) ? '' : byOn(r) }));
    let right = `<p class="muted">${esc(t('Select a question.', 'Sélectionne une question.'))}</p>`;
    if (row) {
      const answer = str(row, 'answer');
      const pending = !answer;
      const key = `q:${ui.component}:${label(row)}`;
      const about = [str(row, 'title'), str(row, 'rule')].filter((x) => x).join(' · ');
      right = answerHead(pending ? t(`Answer question ${label(row)}`, `Répondre à la question ${label(row)}`) : `Question ${label(row)}`,
        pending ? '' : t('ANSWERED', 'RÉPONDUE'), true)
        + (about ? `<p class="muted small">${esc(about)}</p>` : '')
        + `<p class="answer-q">${esc(str(row, 'question'))}</p>`
        + (answer && byOn(row) ? `<p class="muted small">${esc(t('Recorded by ', 'Enregistrée par ') + byOn(row))}</p>` : '')
        + answerEditor(key, answer || str(row, 'suggested'), t('Your answer', 'Votre réponse'), '',
          btn(t('Save answer', 'Enregistrer la réponse'), 'answerQuestion'), 'question')
        + confirmation('question') + resultCard('question');
    }
    return split(left, rows.length ? right : '');
  }

  const open0 = (row) => str(row, 'status').toLowerCase() === 'open';

  function safetyView(view, d) {
    const rows = list(view, 'safety');
    const label = (r) => 'S' + str(r, 'number');
    const statusOf = (r, i) => (d && d.safety[i] ? d.safety[i].status : str(r, 'status'));
    const isOpen = (r, i) => statusOf(r, i) !== 'RESOLVED';
    const row = current('safety', rows, label, isOpen);
    const recorded = (r) => {
      const hazard = list(r, 'hazards')[0];
      if (str(r, 'dismissed')) return t('Not applicable: ', 'Non applicable : ') + str(r, 'dismissed');
      return hazard ? `Hazard ${str(hazard, 'id')} · ${str(hazard, 'severity')}: ${str(hazard, 'description')}` : '';
    };
    const ai = aiOf();
    const hazardOrigin = (h) => (ai && ai.hazards[str(h, 'id')]) || null;
    // the card's tag: the first hazard's origin (the AI's when one of its hazards waits for a person)
    const safetyOrigin = (r, i) => {
      const hz = list(r, 'hazards');
      const waiting = hz.map(hazardOrigin).find((o) => o && o.toReview);
      return waiting || (hz.length ? hazardOrigin(hz[0]) : null) || { tag: '', toReview: !!(ai && ai.safety[i]) };
    };
    const left = cards('safety', rows, label, (r, i) => ({ number: label(r), title: str(r, 'question'), open: isOpen(r, i),
      state: isOpen(r, i) ? statusOf(r, i) : '', answer: recorded(r), note: isOpen(r, i) ? recorded(r) : '', origin: safetyOrigin(r, i) }));
    let right = `<p class="muted">${esc(t('Select a safety question to see details.', 'Choisis une question de sécurité pour afficher les détails.'))}</p>`;
    if (row) {
      const i = rows.indexOf(row);
      const status = statusOf(row, i);
      const pending = status !== 'RESOLVED';
      const suggestion = row.suggestion || {};
      const hazards = list(row, 'hazards');
      // archvow 1.18 gives the suggestion only while the question is open: remember it for the invariant
      const remembered = `${ui.component}:${str(row, 'number')}`;
      if (str(suggestion, 'invariant')) ui.suggested[remembered] = str(suggestion, 'invariant');
      let block = `<p class="answer-q">${esc(str(row, 'question'))}</p>`;
      if (str(suggestion, 'hazard')) block += `<p class="hint"><b>${esc(t('Proposed hazard', 'Hazard proposé'))}</b> ${esc(str(suggestion, 'hazard'))}</p>`;
      if (str(suggestion, 'invariant') && !suggestion.decision) block += `<p class="hint"><b>${esc(t('Proposed invariant', 'Invariant proposé'))}</b> ${esc(str(suggestion, 'invariant'))}</p>`;
      // archvow 1.19: a decision already answers the question - Confirm records it as that decision
      const decision = suggestion.decision && typeof suggestion.decision === 'object' ? suggestion.decision : null;
      const covered = decision && open0(row) ? (str(suggestion, 'not_applicable')
        ? `<div class="alert good"><b>${esc(t(`Decision ${str(decision, 'number')} says there is nothing to protect`, `La décision ${str(decision, 'number')} dit qu'il n'y a rien à protéger`))}</b>
  <div>${esc(str(decision, 'answer'))}</div><div class="row end">${btn(t('Confirm not applicable', 'Confirmer non applicable'), 'safety', attrs({ action: 'hazard dismiss', covered: '1',
    preview: t('Reason: ', 'Raison : ') + str(suggestion, 'not_applicable') }))}</div></div>`
        : `<div class="alert good"><b>${esc(t(`Covered by decision ${str(decision, 'number')}`, `Couvert par la décision ${str(decision, 'number')}`))}</b>
  <div>${esc(str(decision, 'answer'))}</div><div class="muted small">${esc(t('Confirm records the hazard and an invariant that is this decision: no words copied, its test marked decision(' + str(decision, 'number') + ') proves it.',
    "Confirmer enregistre le hazard et un invariant qui est cette décision : aucun mot recopié, son test marqué decision(" + str(decision, 'number') + ') le prouve.'))}</div>
  <div class="row end">${btn(t('Confirm', 'Confirmer'), 'safety', attrs({ action: 'hazard suggested', covered: '1', preview: t('Hazard: ', 'Hazard : ') + str(suggestion, 'hazard') + '\n' + t('Invariant: decision ', 'Invariant : décision ') + str(decision, 'number') + ' - ' + str(decision, 'answer') }))}</div></div>`) : '';
      block += covered;
      if (!hazards.length && !str(suggestion, 'hazard') && str(row, 'status').toLowerCase() === 'open') {
        block += `<p class="tone-warn"><b>${esc(t('No hazard recorded yet. Declare one or mark the question not applicable.', "Aucun hazard déclaré. Déclare un hazard ou indique que la question ne s'applique pas."))}</b></p>`;
      }
      for (const h of hazards) {
        const guards = list(h, 'prevented_by');
        // Change = remove + add again: safe only while no invariant points to it (archvow keeps no link otherwise)
        const changeTip = guards.length ? t('Change its words or severity: same id, its invariants stay and become to review (hazard edit)',
          'Changer ses mots ou sa gravité : même id, ses invariants restent et deviennent à revoir (hazard edit)')
          : t('Change its words or severity: same id (hazard edit)', 'Changer ses mots ou sa gravité : même id (hazard edit)');
        const ho = hazardOrigin(h);
        const keep = ho && ho.toReview ? btn(t('Keep', 'Garder'), 'openCommand', attrs({ command: 'hazard accept', id: str(h, 'id') })
          + ` title="${esc(t('Keep this hazard and the invariants the AI added to prevent it (archvow hazard accept)', "Garder ce hazard et les invariants que l'IA a ajoutés pour l'empêcher (archvow hazard accept)"))}"`, false, 'small') : '';
        block += `<div class="recorded ${ho && ho.toReview ? 'ai-wait' : ''}"><div class="row hazard-head"><p class="grow"><b>Hazard ${esc(str(h, 'id'))} · ${esc(str(h, 'severity'))}</b>${ho && ho.tag ? ' ' + originTag(ho) : ''}<br>${esc(str(h, 'description'))}</p>`
          + `<span class="row">${keep}${btn(t('Change', 'Modifier'), 'hazardChange', attrs({ id: str(h, 'id'), text: str(h, 'description'), severity: str(h, 'severity') }) + ` title="${esc(changeTip)}"`, false, 'secondary small')}`
          + `${btn(t('Remove', 'Supprimer'), 'hazardRemove', attrs({ id: str(h, 'id') }) + ` title="${esc(t('Remove this hazard (asks first)', 'Supprimer ce hazard (demande avant)'))}"`, false, 'secondary small')}</span></div>`;
        if (ho && ho.why) block += `<p class="muted small">${esc(t('Why: ', 'Pourquoi : ') + ho.why)}</p>`;
        for (const g of guards) {
          block += `<p class="tone-good">↳ <b>Invariant ${esc(str(g, 'id'))}</b>${g.to_accept === true ? ` <span class="to-review">${esc(originWord('to_review'))}</span>` : ''} <span class="body">${esc(str(g, 'statement'))}</span>${g.tested === false ? ` <span class="muted">[${esc(t('test not verified', 'test non vérifié'))}]</span>` : ''}</p>`;
        }
        if (!guards.length && ['high', 'critical'].includes(str(h, 'severity').toLowerCase())) {
          block += `<p class="tone-warn"><b>ARCH030 — ${esc(t('An invariant is required before this hazard can be considered protected.', 'Un invariant est obligatoire pour que ce hazard soit considéré protégé.'))}</b></p>`;
        }
        block += '</div>';
      }
      if (str(row, 'dismissed')) block += `<div class="recorded"><p><b>${esc(t('Not applicable', 'Non applicable'))}:</b> ${esc(str(row, 'dismissed'))}</p></div>`;
      const open = str(row, 'status').toLowerCase() === 'open';
      const missing = d && d.safety[i] ? d.safety[i].unprotected : '';
      const key = `s:${ui.component}:${str(row, 'number')}`;
      const severity = ui.severity[key] || 'high';
      const proposed = !!str(suggestion, 'hazard') && !suggestion.decision;  // covered: Confirm above does it
      // a change that is done (or a hazard that is gone): back to the normal view
      if (ui.changing[key] && !hazards.some((h) => str(h, 'id') === ui.changing[key])) { delete ui.changing[key]; delete ui.drafts[key]; }
      const changing = ui.changing[key] || '';
      const editor = open || changing ? `<div class="row answer-label"><span>${esc(changing ? t('Change hazard ', 'Modifier le hazard ') + changing
          : t('Hazard description or reason', 'Hazard ou justification'))}</span>
  <label>${esc(t('Severity', 'Sévérité'))} <select data-change="severity" data-key="${esc(key)}">${['critical', 'high', 'medium', 'low'].map((s) => `<option ${s === severity ? 'selected' : ''}>${s}</option>`).join('')}</select></label></div>`
        + (changing
          ? answerEditor(key, '', t('What must never happen', 'Ce qui ne doit jamais arriver'),
            btn(t('Keep it as it is', 'Le garder tel quel'), 'cancelChange', attrs({ key }), false, 'secondary'),
            btn(t('Save the change', 'Enregistrer la modification'), 'safety', attrs({ action: 'hazard change' })), 'safety')
          : answerEditor(key, str(suggestion, 'hazard'), t('What must never happen, or why it cannot', "Ce qui ne doit jamais arriver, ou pourquoi c'est impossible"),
            btn(t('Not applicable', 'Non applicable'), 'safety', attrs({ action: 'hazard dismiss' }), false, 'secondary')
            + (proposed ? btn(str(suggestion, 'invariant') ? t('Use proposed hazard + invariant', 'Utiliser hazard + invariant proposés') : t('Use proposed hazard', 'Utiliser le hazard proposé'),
              'safety', attrs({ action: 'hazard suggested' }) + ` title="${esc(d && d.safety[i] ? d.safety[i].preview : '')}"`, false, 'secondary') : ''),
            btn(t('Declare hazard', 'Déclarer un hazard'), 'safety', attrs({ action: 'hazard add' })), 'safety')) : '';
      // a high or critical hazard without its invariant: the invariant is written right here, filled with the suggestion
      const ikey = `i:${ui.component}:${str(row, 'number')}`;
      const invariantEditor = missing && !changing ? `<div class="row answer-label"><span>${esc(t('Invariant - what is always true, so that ', 'Invariant - ce qui est toujours vrai, pour que ')
          + missing + t(' cannot happen', ' ne puisse pas arriver'))}</span></div>`
        + answerEditor(ikey, str(suggestion, 'invariant') || ui.suggested[remembered] || '', t('What is always true in the code', 'Ce qui est toujours vrai dans le code'), '',
          btn(t('Add invariant', "Ajouter l'invariant"), 'invariantInline', attrs({ hazard: missing })), 'safety') : '';
      const more = `<div class="row wrap more">
  ${btn(t('Add invariant…', 'Ajouter un invariant…'), 'choose', attrs({ purpose: 'invariant add' }), false, 'secondary small')}
  ${btn(t('Remove invariant…', 'Supprimer un invariant…'), 'choose', attrs({ purpose: 'invariant remove' }), false, 'secondary small')}</div>`;
      right = answerHead(`${t('Safety', 'Sécurité')} S${str(row, 'number')}${str(row, 'guide_word') ? ' · ' + str(row, 'guide_word') : ''}`, status, !pending)
        + block + editor + invariantEditor + more + confirmation('safety') + resultCard('safety');
    }
    return split(left, rows.length ? right : '');
  }

  function assumptionsView(view) {
    const rows = list(view, 'assumptions');
    const label = (r) => 'A' + str(r, 'number');
    const state = (r) => str(r, 'status').toLowerCase();
    const closed = (r) => state(r) === 'accepted' || state(r) === 'rejected';
    const row = current('assumption', rows, label, (r) => !closed(r));
    const left = cards('assumption', rows, label, (r) => ({ number: label(r), title: str(r, 'text'), open: !closed(r),
      state: t('To review', 'À examiner'), answer: state(r) === 'accepted' ? t('Accepted', 'Acceptée') : t('Rejected', 'Rejetée'), by: closed(r) ? byOn(r) : '' }))
      + `<div class="row">${btn(t('+ Assumption', '+ Hypothèse'), 'openCommand', attrs({ command: 'assumption add', component: ui.component })
        + ` title="${esc(t('Add an assumption', 'Ajouter une hypothèse'))}"`, false, 'secondary small')}</div>`;
    let right = `<p class="muted">${esc(t('No assumption selected.', 'Aucune hypothèse sélectionnée.'))}</p>`;
    if (row) {
      const pending = !closed(row);
      right = answerHead(pending ? t(`Review assumption ${label(row)}`, `Examiner l'hypothèse ${label(row)}`) : t(`Assumption ${label(row)}`, `Hypothèse ${label(row)}`),
        pending ? '' : state(row).toUpperCase(), state(row) === 'accepted')
        + `<p class="answer-q">${esc(str(row, 'text'))}</p>`
        + (pending ? `<div class="answer-actions"><span></span><span class="row">${btn(t('Reject', 'Rejeter'), 'openCommand', attrs({ command: 'assumption reject', component: ui.component, number: str(row, 'number') }), false, 'secondary')}
  ${btn(t('Accept', 'Accepter'), 'openCommand', attrs({ command: 'assumption accept', component: ui.component, number: str(row, 'number') }))}</span></div>` : '');
    }
    return split(left, rows.length ? right : '');
  }

  function readable(v) {
    if (v === null || v === undefined) return '—';
    if (Array.isArray(v)) return v.map(readable).join(', ');
    if (typeof v === 'object') return Object.entries(v).map(([k, x]) => k + '=' + readable(x)).join('; ');
    return String(v);
  }

  function rulesView(view) {
    const rules = list(view, 'rules');
    const ai = aiOf();
    return `<div class="row">${btn(t('Add rule…', 'Ajouter une règle…'), 'addRule')}${btn(t('Edit rule…', 'Modifier une règle…'), 'choose', attrs({ purpose: 'rule edit' }), false, 'secondary')}
  ${btn(t('Remove rule…', 'Supprimer une règle…'), 'choose', attrs({ purpose: 'rule remove' }), false, 'secondary')}</div>
  <div class="details"><h4>${esc(t('Applicable rules', 'Règles applicables'))}</h4>${rules.length ? rules.filter((r) => {
    const o = ai && ai.rules[str(r, 'id')];
    return keepCard(true, !!(o && o.toReview));
  }).map((r) => {
    const scope = readable(r.scope);
    const o = ai && ai.rules[str(r, 'id')];
    const waiting = o && o.toReview;
    const keep = waiting ? `<span class="row">${btn(t('Keep', 'Garder'), 'openCommand', attrs({ command: 'rule accept', id: str(r, 'id') }), false, 'small')}${btn(t('Remove', 'Retirer'), 'openCommand', attrs({ command: 'rule remove', id: str(r, 'id') }), false, 'secondary small')}</span>` : '';
    return `<div class="rule ${waiting ? 'ai-wait' : ''}"><div class="row"><p class="grow"><b>${esc(str(r, 'id') || '•')}</b>${o && o.tag ? ' ' + originTag(o) : ''} ${esc(str(r, 'text'))}</p>${keep}</div>${o && o.why ? `<p class="muted small">${esc(t('Why: ', 'Pourquoi : ') + o.why)}</p>` : ''}${scope && scope !== '—' ? `<p class="muted small">${esc(t('Scope', 'Portée'))}: ${esc(scope)}</p>` : ''}</div>`;
  }).join('') : `<p class="muted">${esc(t('No applicable rules.', 'Aucune règle applicable.'))}</p>`}</div>`;
  }

  function tasksView(view, d) {
    const items = d ? [...d.data.filter((x) => x.pending), ...d.data.filter((x) => !x.pending)] : [];
    const key = (x) => x.kind + ':' + x.id;
    let selected = items.find((x) => key(x) === ui.sel.data);
    if (!selected && items.length) {
      selected = items[0];
      ui.sel.data = key(selected);
    }
    const rows = items.map((x) => `<li class="${key(x) === ui.sel.data ? 'selected' : ''} ${x.pending ? 'pending' : 'done'}" data-a="sel" data-kind="data" data-key="${esc(key(x))}">${x.kind === 'COLUMN' ? '◉' : '▸'} ${esc(x.title)} · ${esc(x.pending ? t('Pending', 'En attente') : t('Resolved', 'Traité'))}</li>`).join('');
    const detail = selected ? `${esc(selected.title)} · ${esc(selected.pending ? t('Pending', 'En attente') : t('Resolved', 'Traité'))}\n\n${esc(selected.detail
      || t('No further description is recorded for this item in the component spec.', "Aucune description supplémentaire n'est enregistrée pour cet élément dans la spec du composant."))}`
      : esc(t('Select a task or column to see its details.', 'Sélectionne une tâche ou une colonne pour voir ses détails.'));
    const outstanding = items.filter((x) => x.pending);
    const summary = [`<h4>${esc(t('Data & tasks', 'Données et tâches'))}</h4>`,
      `<p class="${outstanding.length ? 'tone-warn' : 'tone-good'}"><b>${esc(t('Pending actions', 'Actions en attente'))}: ${outstanding.length}</b></p>`,
      outstanding.map((x) => `<p class="tone-warn">● ${esc(x.kind === 'COLUMN' ? t('Column', 'Colonne') : t('Task', 'Tâche'))} : ${esc(x.title)}</p>`).join(''),
      `<h5>${esc(t('Columns and classifications', 'Colonnes et classifications'))}</h5>`,
      items.filter((x) => x.kind === 'COLUMN').map((x) => `<p class="${x.pending ? 'tone-warn' : 'tone-good'}">${x.pending ? '●' : '✓'} ${esc(x.title)} — ${esc(x.detail)}</p>`).join('')
        || `<p class="muted">${esc(t('No columns declared.', 'Aucune colonne déclarée.'))}</p>`,
      `<h5>${esc(t('Personal data', 'Données personnelles'))}</h5>`,
      view.personal && typeof view.personal === 'object' && Object.keys(view.personal).length
        ? Object.entries(view.personal).map(([k, v]) => `<p>${esc(k)}: ${esc(readable(v))}</p>`).join('')
        : `<p class="muted">${esc(t('No personal data flows declared.', 'Aucun flux de données personnelles déclaré.'))}</p>`].join('');
    return `<h4>${esc(t('Actions to complete', 'Actions à terminer'))}</h4><ul class="list items" data-scroll="list-data">${rows || `<li class="muted">${esc(t('Nothing to display.', 'Rien à afficher.'))}</li>`}</ul>
  <div class="row">${btn(selected && selected.kind === 'COLUMN' ? t('Classify column…', 'Classifier la colonne…') : t('Open task prompt', 'Ouvrir les instructions de la tâche'), 'dataAction', '', !selected)}</div>
  <div class="details"><h5>${esc(t('Details and description', 'Détails et description'))}</h5><pre class="plain">${detail}</pre>
  ${btn(t('Edit component description…', 'Modifier la description du composant…'), 'openCommand', attrs({ command: 'component edit', component: ui.component }), false, 'secondary')}</div>
  ${resultCard('data')}<div class="details">${summary}</div>`;
  }

  /**
   * Spec: one view of the component's spec - the card the AI implements (spec.md, written by archvow)
   * and the vow it is written from (spec.vow). Two files on disk, one place to read them; both read only.
   */
  function vowView() {
    const text = S.vows[ui.component];
    if (text === undefined) {
      post({ type: 'vow', component: ui.component });
      return `<p class="muted">${esc(t('Loading', 'Chargement'))}…</p>`;
    }
    const part = ui.specPart === 'vow' ? 'vow' : 'card';
    const toggle = `<div class="segmented" role="radiogroup">${[['card', t('The card the AI implements', "La fiche que l'IA implémente")], ['vow', t('The vow it is written from', 'Le vow dont elle est écrite')]]
      .map(([id, l]) => `<button type="button" role="radio" aria-checked="${part === id}" class="${part === id ? 'on' : 'secondary'}" data-a="specPart" data-id="${id}">${esc(l)}</button>`).join('')}</div>`;
    const file = `.archvow/components/${ui.component}/${part === 'vow' ? 'spec.vow' : 'spec.md'}`;
    const bar = `<div class="row wrap">${toggle}<span class="muted small"><code>${esc(file)}</code> - ${esc(t('read only: change it with the commands (Edit spec, + Question, + Rule...)', 'en lecture seule : la changer avec les commandes (Modifier la spec, + Question, + Règle...)'))}</span>
  ${btn(t('Open in the editor', "Ouvrir dans l'éditeur"), part === 'vow' ? 'openVowFile' : 'openSpec', '', false, 'secondary')}${btn(t('Project vow', 'Vow du projet'), 'openProjectVow', '', false, 'secondary')}</div>`;
    if (part === 'card') {
      const card = ui.cards[ui.component] || '';
      return bar + (card ? `<pre class="plain card-md">${esc(card)}</pre>` : `<p class="muted">${esc(t('No spec.md yet: archvow docs writes it.', 'Pas encore de spec.md : archvow docs l\'écrit.'))}</p>`);
    }
    if (!text) return bar + `<p class="muted">${esc(t('No spec.vow for this component.', 'Pas de spec.vow pour ce composant.'))}</p>`;
    const lines = text.split('\n').map((line) => {
      const m = /^(\s*)("(?:[^"\\]|\\.)*"|[A-Za-z_][A-Za-z0-9_./-]*)(.*)$/.exec(line);
      if (line.trimStart().startsWith('#') || !m) return `<span class="${line.trimStart().startsWith('#') ? 'comment' : ''}">${esc(line)}</span>`;
      const depth = Math.min(3, Math.floor(m[1].length / 2));
      return `${esc(m[1])}<span class="key${depth}">${esc(m[2])}</span>${esc(m[3])}`;
    });
    return bar + `<pre class="vow">${lines.join('\n')}</pre>`;
  }

  /** Component and project issues have distinct destinations; check violations stay in Check & Gates. */
  const isLocalOther = (item) => item.component === ui.component && item.source !== 'check' && item.preset.view === 'other';
  const isProjectOther = (item) => !item.component && item.source !== 'check';
  function otherItems(index) {
    const items = index === 0 ? S.work.otherPending : S.work.otherResolved;
    return items.filter(ui.mode === 'project' ? isProjectOther : isLocalOther);
  }

  function otherView() {
    const pendingItems = otherItems(0);
    const resolvedItems = otherItems(1);
    const items = ui.otherList === 0 ? pendingItems : resolvedItems;
    if (ui.sel.other >= items.length) ui.sel.other = -1;
    const rows = items.map((item, i) => `<li class="${i === ui.sel.other ? 'selected' : ''} ${item.pending ? 'pending' : 'done'}" data-a="otherSel" data-i="${i}">
  ${chip(item.kind, item.kind === 'ERROR' ? 'bad' : item.kind === 'RESOLVED' ? 'good' : 'warn')} <b>${esc(item.title)}</b><div class="muted small">${esc(short(item.detail, 150))}</div></li>`).join('');
    const item = items[ui.sel.other];
    let selected = '';
    if (item) {
      const secondary = quickSecondaryLabel(item);
      selected = `<div class="details"><h4>${esc(item.title)}</h4>${chip(item.pending ? t('Pending', 'En attente') : t('Resolved', 'Traité'), item.pending ? 'warn' : 'good')}
  ${item.guide ? `<p class="muted small">${esc(t('archvow says', 'archvow dit'))}</p>` : ''}<pre class="plain">${esc(item.detail)}</pre>${item.component ? `<p><b>${esc(t('Component', 'Composant'))}:</b> ${esc(item.component)}</p>` : ''}
  ${item.guide ? guideHtml(item.guide) : ''}
  <div class="row">${btn(item.action, 'quickPrimary')}${secondary ? btn(secondary, 'quickSecondary', '', false, 'secondary') : ''}</div></div>${confirmation('quick')}${resultCard('quick')}`;
    }
    return `<p class="muted">${esc(ui.mode === 'project' ? t('Project-wide work, separate from components and Check & Gates.', 'Travail global du projet, distinct des composants et des contrôles.') : t('Only uncategorised work of this component. Project checks are in Check & Gates.', 'Uniquement les éléments non classés de ce composant. Les contrôles globaux sont dans Check & Gates.'))}</p>
  <nav class="tabs sub">${btn(`${t('Pending', 'En attente')} (${pendingItems.length})`, 'otherList', attrs({ i: 0 }), false, 'tab ' + (ui.otherList === 0 ? 'current' : ''))}
  ${btn(`${t('Resolved', 'Traités')} (${resolvedItems.length})`, 'otherList', attrs({ i: 1 }), false, 'tab ' + (ui.otherList === 1 ? 'current' : ''))}</nav>
  <ul class="list items" data-scroll="other">${rows || `<li class="muted">${esc(t('Nothing to display.', 'Rien à afficher.'))}</li>`}</ul>${selected}`;
  }

  /** A diagnostic's guidance (guidance.ts): what it means, what to do, who, whether it blocks, the command, then. */
  function guideHtml(g) {
    const md = (text) => esc(text || '').replace(/`([^`]+)`/g, '<code>$1</code>');
    const command = g.command ? `<dt>${esc(t('The command', 'La commande'))}</dt><dd>${terminalCommand(g.command.text)}`
      + (g.command.missing.length ? ` <span class="muted">— ${esc(t('the form leaves for you to fill in: ', 'le formulaire vous laisse remplir : '))}${esc(g.command.missing.join(', '))}</span>` : '')
      + ` <span class="muted">— ${esc(g.command.readOnly ? t('it changes nothing.', 'elle ne change rien.') : t('prepared in a form; nothing runs before you confirm.', 'préparée dans un formulaire ; rien ne tourne avant votre confirmation.'))}</span></dd>` : '';
    return `<dl class="guide"><dt>${esc(t('What it means', 'Ce que cela veut dire'))}</dt><dd>${md(g.meaning)}</dd>
  <dt>${esc(t('What to do', 'Que faire'))}</dt><dd>${md(g.todo)}</dd>${command}
  <dt>${esc(t('Who', 'Qui'))}</dt><dd>${md(g.who)}</dd><dt>${esc(t('Blocks?', 'Bloquant ?'))}</dt><dd>${md(g.blocking)}</dd>
  <dt>${esc(t('Then', 'Ensuite'))}</dt><dd>${md(g.then)}</dd></dl>`;
  }

  /** The second button of an Other / Project item: its other way out, or the file a diagnostic is about. */
  function quickSecondaryLabel(item) {
    const special = { 'brief accept': t('Review the brief', 'Relire la brief'), 'hazard add': t('Not applicable', 'Non applicable'), 'assumption accept': t('Reject', 'Rejeter'), 'decision accept': t('Edit answer', 'Autre réponse'),
      'schema-create': t('Edit reader dependencies', 'Modifier les dépendances du lecteur') }[item.command];
    if (special) return special;
    const file = item.guide && item.guide.file;
    if (file && item.command !== 'open-file') return t('Open ', 'Ouvrir ') + file.path + (file.line ? ':' + file.line : '');
    return ['open-file', 'component edit', 'invariant add'].includes(item.command) && item.component ? t('Open component', 'Ouvrir le composant') : '';
  }

  // ---------------------------------------------------------------- Commands

  const LONG_TEXT = new Set(['description', 'answer', 'rationale', 'statement', 'question', 'text', 'words']);
  const option = (p) => (p.flags.length ? p.flags[p.flags.length - 1] : '');

  function setCommand(name, values, help) {
    ui.command = name;
    ui.form = Object.assign({}, values);
    ui.formSections = {};
    ui.formStep = 0;
    ui.formHelp = help || '';
    // Edit spec: what the component has now (not what a diagnostic asks to change); only differences are sent
    ui.formBaseline = name === 'component edit' ? whatTheComponentHas((values.component || '').trim()) : {};
    ui.tplAnswers = {};
    ui.tplLayers = {};
    if (ui.confirm && ui.confirm.target === 'command') ui.confirm = null;
    ui.preview = { text: '', missing: '', files: [] };
    delete ui.results.command;
    askPreview(0);
    if (name === 'init') {
      if (!('no_input' in ui.form)) ui.form.no_input = 'true';
      post({ type: 'catalog', template: ui.form.template || '' });
    }
    // template new: the templates to extend and their settings, from the installed archvow
    if (name === 'template new') post({ type: 'catalog', template: '' });
  }

  /** Edit spec's baseline: the fields the component already has, as choosing the component fills them. */
  function whatTheComponentHas(id) {
    const picked = choicesNow(fieldOf('component edit', 'component')).find((c) => c.value === id);
    return Object.assign({}, (picked && picked.fill) || {});
  }

  /** Command groups belong to the shared form contract, never to a particular project template. */
  function groupsOfCommands() {
    const definitions = (I.forms && I.forms.command_groups) || [];
    const remaining = I.commands.slice();
    const groups = definitions.map((g) => {
      const prefixes = g.prefixes || [];
      const items = remaining.filter((c) => prefixes.includes(c.path[0]));
      for (const c of items) remaining.splice(remaining.indexOf(c), 1);
      return { id: g.id, label: pair(g.label) || g.id, items };
    }).filter((g) => g.items.length);
    if (remaining.length) groups.push({ id: 'other', label: t('Other commands', 'Autres commandes'), items: remaining });
    return groups;
  }

  function commandsTab() {
    const filter = ui.commandFilter.toLocaleLowerCase();
    const groups = groupsOfCommands().map((g) => ({ ...g,
      items: g.items.filter((c) => !filter || g.label.toLocaleLowerCase().includes(filter)
        || (c.path.join(' ') + ' ' + c.description).toLocaleLowerCase().includes(filter))
    })).filter((g) => g.items.length);
    const selected = ui.command;
    return `<section class="browser-only commands-list"><input data-input="commandFilter" data-keep="commandFilter" placeholder="${esc(t('Filter commands', 'Filtrer les commandes'))}" value="${esc(ui.commandFilter)}">
  <div class="command-tree" data-scroll="commands">${groups.map((g) => {
    const expanded = !!filter || ui.commandGroupsOpen[g.id] === true;
    const groupIcon = ((I.icons || {}).groups || {})[g.id] || 'other';
    return `<div class="command-group"><button class="command-group-toggle" data-a="commandGroup" data-id="${esc(g.id)}" aria-expanded="${expanded ? 'true' : 'false'}"><span class="node">${expanded ? '▾' : '▸'} ${icon(groupIcon)}<span>${esc(g.label)}</span></span><span class="count">${g.items.length}</span></button>
    ${expanded ? `<ul class="list items command-children">${g.items.map((c) => `<li class="${c.path.join(' ') === selected ? 'selected' : ''}" data-a="command" data-name="${esc(c.path.join(' '))}" title="${esc(c.description)}"><b class="node">${icon(commandIcon(c.path))}<span>${esc(c.path.join(' '))}</span></b><div class="muted small">${esc(short(c.description || '', 90))}</div></li>`).join('')}</ul>` : ''}</div>`;
  }).join('')}</div>
  <p class="muted small">${esc(t('Select a command to open its form in the editor.', "Choisis une commande pour ouvrir son formulaire dans l'éditeur."))}</p></section>`;
  }

  function aiSkillsTab() {
    return `<section class="browser-only ai-skills-tab">${aiSkillsGroup('')}</section>`;
  }

  /** Commands: the AI skills, one group after archvow's, plain names (0.16.0). They open the launch form. */
  function aiSkillsGroup(filter) {
    const items = (AI().order || []).filter((id) => skillDef(id)).filter((id) => !filter
      || (skillLabel(id) + ' ' + id + ' ' + pair(skillDef(id).does)).toLocaleLowerCase().includes(filter));
    if (!items.length) return '';
    const expanded = filter || ui.commandGroupsOpen.ai !== false;
    return `<div class="command-group ai-group"><button class="command-group-toggle" data-a="commandGroup" data-id="ai" aria-expanded="${expanded ? 'true' : 'false'}"><span class="node">${expanded ? '▾' : '▸'} <svg class="spark small" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3l2.2 5.8L20 11l-5.8 2.2L12 19l-2.2-5.8L4 11l5.8-2.2z"/></svg><span>${esc(t('AI skills', 'Skills IA'))}</span></span><span class="count">${items.length}</span></button>
    ${expanded ? `<p class="muted small ai-note">${esc(t('The AI proposes or adds, you decide.', "L'IA propose ou ajoute, tu décides."))}</p><ul class="list items command-children">${items.map((id) => `<li data-a="openSkill" data-skill="${esc(id)}" title="${esc(pair(skillDef(id).does))}"><b class="node"><span>${esc(skillLabel(id))}</span><span class="mono small muted">${esc(id)}</span></b><div class="muted small">${esc(short(pair(skillDef(id).does), 90))}</div></li>`).join('')}</ul>` : ''}</div>`;
  }

  /** Read-only reference generated from the installed CLI grammar and the shared form descriptions. */
  function commandReference() {
    const groups = groupsOfCommands();
    return `<div class="card"><h3>${esc(t('Command reference', 'Documentation des commandes'))}</h3>
    <p class="muted">${esc(t('Commands documented by this plugin version. Your configured CLI is authoritative; no command is executed.', 'Commandes documentées par cette version du plugin. La CLI configurée fait autorité ; aucune commande n\'est exécutée.'))}</p>
    ${groups.map((g) => `<h4>${esc(g.label)}</h4>${g.items.map((c) => {
      const name = c.path.join(' ');
      return `<div class="reference-command"><b>archvow ${esc(name)}</b><p>${esc(c.description)}</p>${pair(formEntry(name).does) ? `<p class="muted small">${esc(pair(formEntry(name).does))}</p>` : ''}
      ${c.parameters.length ? `<div class="muted small">${c.parameters.map((p) => `<code>${esc(p.flags.join(', ') || p.name)}</code>${p.required ? ' *' : ''}`).join(' · ')}</div>` : ''}
      ${btn(t('Open form', 'Ouvrir le formulaire'), 'openCommand', attrs({ command: name }), false, 'secondary small')}</div>`;
    }).join('')}`).join('')}</div>`;
  }

  /**
   * A field that offers what exists (choices.ts): a list to choose from (a component, a question with
   * its number, a hazard...), suggestions with free typing (a branch, a template), or the usual field
   * with a line under it (question add: the number archvow will give).
   */
  function choiceControl(p, field, value, id, usual) {
    if (field.kind === 'note') {
      const note = field.by ? (field.notes || {})[(ui.form[field.by] || '').trim()] || '' : field.note || '';
      return usual + (note ? `<div class="note small">${esc(note)}</div>` : '');
    }
    const choices = choicesNow(field);
    if (field.kind === 'suggest') {
      return `<input list="dl-${esc(p.name)}" data-input="form" data-name="${esc(p.name)}" data-keep="${id}" value="${esc(value)}">`
        + `<datalist id="dl-${esc(p.name)}">${choices.map((c) => `<option value="${esc(c.value)}" label="${esc(c.label)}">`).join('')}</datalist>`;
    }
    if (field.by && !(ui.form[field.by] || '').trim()) {
      const first = field.by === 'type' ? t('Choose the type first', "Choisis d'abord le type") : t('Choose the component first', "Choisis d'abord le composant");
      return `<select disabled><option>${esc(first)}</option></select>`;
    }
    if (!choices.length) {
      return `<select disabled><option>${esc(t('(none)', '(aucun)'))}</option></select><div class="note warn small">${esc(field.empty || '')}</div>`;
    }
    const known = choices.some((c) => c.value === value);
    const placeholder = p.name === 'kind' ? t('— the default —', '— celle par défaut —') : t('— choose —', '— choisir —');
    return `<select data-change="choice" data-name="${esc(p.name)}" data-keep="${id}"><option value="">${esc(placeholder)}</option>`
      + (value && !known ? `<option selected value="${esc(value)}">${esc(value)}</option>` : '')
      + choices.map((c) => `<option value="${esc(c.value)}" ${c.value === value ? 'selected' : ''}>${esc(c.label)}</option>`).join('') + '</select>';
  }

  /** Multi-select uses only declared components; existing unknown values remain visible and removable. */
  function usesPicker(value) {
    const current = (ui.form.component || '').trim();
    const selected = value.split(',').map((v) => v.trim()).filter(Boolean);
    const known = componentIds().filter((id) => id !== current);
    const choices = known.filter((id) => !selected.includes(id));
    const display = (id) => {
      const component = list(S && S.spec, 'containers').flatMap((c) => list(c, 'components'))
        .find((c) => str(c, 'id') === id);
      return [id, str(component, 'type'), str(component, 'layer')].filter(Boolean).join(' · ');
    };
    return `<div class="uses-picker"><div class="uses-tags">${selected.map((id) => `<span class="use-tag">${esc(id)} <button class="icon" type="button" data-a="removeUse" data-id="${esc(id)}" aria-label="${esc(t('Remove dependency', 'Retirer la dépendance') + ': ' + id)}">×</button></span>`).join('')}
       ${!selected.length ? `<span class="muted small">${esc(t('No dependencies', 'Aucune dépendance'))}</span>` : ''}</div>
     <select data-change="addUse"><option value="">${esc(t('Add an existing component…', 'Ajouter un composant existant…'))}</option>${choices.map((id) => `<option value="${esc(id)}">${esc(display(id))}</option>`).join('')}</select>
     <details class="use-manual"><summary>${esc(t('Advanced: edit the list manually', 'Avancé : saisir la liste manuellement'))}</summary>
     <input data-input="form" data-name="uses" data-keep="f-uses" value="${esc(value)}" aria-label="--uses"></details></div>`;
  }

  // ---------------------------------------------------------------- a command's form (design A)

  /** [English, French] from contract/forms/forms.json, in the page's language. */
  const pair = (p) => (Array.isArray(p) && p.length === 2 ? t(p[0], p[1]) : '');
  const formEntry = (name) => (((I.forms || {}).commands || {})[name]) || {};
  /** A parameter in plain words (forms.json), else archvow's own name. */
  function fieldLabel(name, param) {
    return pair((formEntry(name).labels || {})[param]) || pair(((I.forms || {}).labels || {})[param]) || param.replace(/_/g, ' ');
  }
  /** Shown first: the command's essential fields (forms.json) and every required one; the rest under More options. */
  function isEssential(name, p) {
    const essential = formEntry(name).essential;
    return p.required || !Array.isArray(essential) || essential.includes(p.name);
  }
  let previewTimer = null;
  /** The live preview: the extension plans the command as Run would (one grammar), a moment after typing stops. */
  function askPreview(delay = 150) {
    if (NAV || !ui.command) return;
    clearTimeout(previewTimer);
    previewTimer = setTimeout(() => post({ type: 'formPreview', command: ui.command, values: Object.assign({}, ui.form),
      initAnswers: ui.command === 'init' ? initAnswers() : [], selected: ui.component, baseline: ui.formBaseline }), delay);
  }

  /** A few fixed values (severity, verify, criticality...): one button each, the chosen one pressed. */
  function segmented(p, value) {
    return `<div class="segmented" role="radiogroup">${p.choices.map((c) => `<button type="button" role="radio" aria-checked="${c === value}" class="${c === value ? 'on' : 'secondary'}" data-a="segment" ${attrs({ name: p.name, value: c === value && !p.required ? '' : c })}>${esc(c)}</button>`).join('')}</div>`;
  }

  function fieldRow(name, p) {
    const value = ui.form[p.name] || '';
    const id = 'f-' + p.name;
    let control;
    if (p.boolean) {
      control = `<label class="check"><input type="checkbox" data-change="formBool" data-name="${esc(p.name)}" ${value === 'true' ? 'checked' : ''}> ${esc(p.help || '')}</label>`;
    } else if (p.name === 'uses' && ['component add', 'component edit'].includes(name)) {
      control = usesPicker(value);
    } else if (p.flags.includes('--from')) {
      control = briefField(p.name, value, id);
    } else if (p.choices.length && p.choices.length <= 4) {
      control = segmented(p, value);
    } else if (p.choices.length) {
      control = `<select data-change="form" data-name="${esc(p.name)}"><option value=""></option>${p.choices.map((c) => `<option ${c === value ? 'selected' : ''}>${esc(c)}</option>`).join('')}</select>`;
    } else if (LONG_TEXT.has(p.name)) {
      control = `<textarea rows="3" data-input="form" data-name="${esc(p.name)}" data-keep="${id}">${esc(value)}</textarea>`;
    } else {
      control = `<input data-input="form" data-name="${esc(p.name)}" data-keep="${id}" value="${esc(value)}">`;
    }
    const field = p.boolean ? null : fieldOf(name, p.name);
    if (field) control = choiceControl(p, field, value, id, control);
    if (name === 'component add' && p.name === 'type') {
      const def = I.types[value];
      control += `<div class="muted small">${esc(def ? `${value} — ${def.description}${def.hint ? ' | ' + t('Name', 'Nom') + ': ' + def.hint : ''}${def.pattern ? ' | name_pattern: ' + def.pattern : ''}`
        : t('Choose a type: its name rule and its kinds follow.', 'Choisis un type : sa règle de nom et ses sortes suivent.'))}</div>`;
    }
    if (name === 'init' && p.name === 'dir') control = `<div class="row">${control}${btn(t('Browse…', 'Parcourir…'), 'browse', attrs({ field: 'dir' }), false, 'secondary')}</div>`;
    let help = pair((formEntry(name).hints || {})[p.name]) || (p.boolean ? '' : p.help);
    if (field && field.note && field.kind !== 'note') help += (help ? '  ·  ' : '') + field.note;
    if (help === '==SUPPRESS==') help = '';  // an older grammar: archvow's hidden marker is not a help text
    const flag = p.flags.length ? option(p) : '';
    return `<div class="field"><label>${esc(fieldLabel(name, p.name))}${p.required ? ' <span class="req">*</span>' : ''}${flag ? ` <code class="flag">${esc(flag)}</code>` : ''}</label><div>${control}${help ? `<div class="muted small">${esc(help)}</div>` : ''}</div></div>`;
  }

  /** archvow said no (nothing written), or wrote it with a doubt: in place, above the buttons. */
  function formAlerts() {
    const r = ui.results.command;
    if (!r || r.running) return '';
    const refusal = r.refusal ? `<div class="alert error" role="alert"><b>${esc(t('archvow refuses:', 'archvow refuse :'))}</b><div>${esc(r.refusal)}</div></div>` : '';
    const warnings = (r.warnings || []).map((w) => `<div class="alert warn" role="status"><b>${esc(t('Written, but:', 'Écrit, mais :'))}</b><div>${esc(w)}</div></div>`).join('');
    return refusal + warnings;
  }

  // Guided forms change presentation only. Command values, preview, validation and
  // confirmation still use exactly the same grammar and state as the previous form.
  // Every CLI parameter, including hidden parameters, belongs to one stage.
  function commandStages(command, params) {
    const name = command.path.join(' ');
    const seen = new Set();
    const possible = params.filter((p) => !((name === 'init' && ['template', 'answer'].includes(p.name))
      || (name === 'template new' && ['answer', 'layer'].includes(p.name))));
    const stage = (en, fr, names, extra = '') => {
      const own = names.filter((key) => possible.some((p) => p.name === key) && !seen.has(key));
      own.forEach((key) => seen.add(key));
      return { title: t(en, fr), names: own, extra };
    };
    const stages = [];
    if (name === 'init') {
      stages.push(stage('Project', 'Projet', ['package', 'description', 'dir', 'src'], 'init-template'));
      const template = templates().find((x) => str(x, 'name') === ui.initTemplate);
      const questions = template ? list(template, 'questions').filter((q) => !!str(q, 'id')) : [];
      const used = new Set();
      const qStage = (en, fr, keys) => {
        const matching = keys.filter((key) => questions.some((q) => str(q, 'id') === key));
        matching.forEach((key) => used.add(key));
        if (matching.length) stages.push({ title: t(en, fr), names: [], questions: matching });
      };
      qStage('Governance', 'Gouvernance', ['language', 'architects', 'mode', 'sandbox', 'rules', 'auto_approve']);
      qStage('AI & briefs', 'IA et briefs', ['ai_tools', 'reviewers', 'briefs', 'ticket_pattern']);
      qStage('CI & quality', 'CI et qualité', ['platform', 'ci', 'sonar', 'git_hooks', 'branch_naming', 'line_length']);
      qStage('Runtime', 'Exécution', ['python_version', 'spark_version', 'fastapi_version', 'scheduler', 'dependencies']);
      qStage('Limits & integration', 'Limites et intégration', ['max_components', 'max_code_lines', 'pip_index_url', 'ci_image']);
      const rest = questions.map((q) => str(q, 'id')).filter((id) => !used.has(id));
      for (let i = 0; i < rest.length; i += 5) stages.push({ title: t('Other template answers', 'Autres réponses du modèle'), names: [], questions: rest.slice(i, i + 5) });
      stages.push(stage('Advanced CLI', 'Options CLI avancées', ['force', 'no_git', 'no_input', 'adopt'], 'init-other'));
    } else if (name === 'component add') {
      stages.push(stage('Basics', 'Essentiel', ['type', 'name', 'description']));
      stages.push(stage('Architecture', 'Architecture', ['kind', 'spec', 'uses', 'container', 'group']));
      stages.push(stage('Source & advanced', 'Source et options', ['signature', 'route', 'source', 'file']));
    } else if (name === 'component edit') {
      stages.push(stage('Identity & changes', 'Identité et modifications', ['component', 'description', 'spec', 'uses']));
      stages.push(stage('Advanced changes', 'Modifications avancées', ['signature', 'group', 'route', 'owners', 'source']));
    } else if (name === 'template new') {
      stages.push(stage('Template basics', 'Création du modèle', ['name', 'extends', 'dir', 'description'], 'template-basics'));
      stages.push(stage('Other options', 'Autres options', ['allowed_paths', 'verbatim_folders', 'no_input'], 'template-layers'));
      const base = baseTemplate();
      const qs = base ? list(base, 'questions').map((q) => str(q, 'id')).filter(Boolean) : [];
      for (let i = 0; i < qs.length; i += 5) stages.push({ title: t('Template answers', 'Réponses du modèle'), names: [], questions: qs.slice(i, i + 5), extra: 'template-answers' });
    } else if (name === 'check') {
      stages.push(stage('Scope', 'Périmètre', ['base', 'complete', 'file', 'stdin']));
      stages.push(stage('Validation options', 'Options de validation', ['accept_weakening', 'all', 'test_report']));
    } else {
      const essential = possible.filter((p) => !p.hidden && isEssential(name, p)).map((p) => p.name);
      if (essential.length) for (let i = 0; i < essential.length; i += 5)
        stages.push(stage(i ? 'Main settings (continued)' : 'Main settings', i ? 'Paramètres principaux (suite)' : 'Paramètres principaux', essential.slice(i, i + 5)));
      const other = possible.filter((p) => !p.hidden && !isEssential(name, p)).map((p) => p.name);
      for (let i = 0; i < other.length; i += 5) stages.push(stage('Other options', 'Autres options', other.slice(i, i + 5)));
    }
    const remaining = possible.filter((p) => !seen.has(p.name) && !p.hidden).map((p) => p.name);
    for (let i = 0; i < remaining.length; i += 5)
      stages.push(stage('Additional settings', 'Paramètres complémentaires', remaining.slice(i, i + 5)));
    const expert = possible.filter((p) => p.hidden).map((p) => p.name);
    for (let i = 0; i < expert.length; i += 5) stages.push(stage('Expert checks', 'Contrôles experts', expert.slice(i, i + 5)));
    const result = stages.filter((s) => s.names.length || (s.questions && s.questions.length) || s.extra);
    // A parameterless/read-only command stays a single direct form.
    if (result.length <= 1 && name !== 'init' && name !== 'template new') return result;
    result.push({ title: t('Review', 'Récapitulatif'), names: [], review: true });
    return result;
  }

  function guidedFormStage(command, stage, params) {
    const name = command.path.join(' ');
    if (stage.review) {
      const chosen = params.filter((p) => !p.plugin).map((p) => {
        let value = ui.form[p.name] || '';
        if (p.boolean) value = value === 'true' ? t('Yes', 'Oui') : t('No', 'Non');
        return `<div class="guided-review-item"><b>${esc(fieldLabel(name, p.name))}</b><span>${esc(value || '—')}</span></div>`;
      }).join('');
      const template = name === 'init' ? templates().find((x) => str(x, 'name') === ui.initTemplate) : name === 'template new' ? baseTemplate() : null;
      const answers = template ? list(template, 'questions').map((q) => {
        const id = str(q, 'id');
        const value = name === 'init' ? (id in ui.initAnswers ? ui.initAnswers[id] : str(q, 'default'))
          : (id in ui.tplAnswers ? ui.tplAnswers[id] : str(q, 'default'));
        return `<div class="guided-review-item"><b>${esc(id)}</b><span>${esc(value || '—')}</span></div>`;
      }).join('') : '';
      return `<div class="guided-review">${chosen}${name === 'init' ? `<div class="guided-review-item"><b>${esc(t('Template', 'Modèle'))}</b><span>${esc(ui.form.template || ui.initTemplate)}</span></div>` : ''}${answers}</div>`;
    }
    const fieldMap = new Map(params.map((p) => [p.name, p]));
    let html = stage.names.map((key) => fieldMap.get(key)).filter(Boolean).map((p) => fieldRow(name, p)).join('');
    if (stage.extra === 'init-template') html += initWizard([], true);
    if (stage.questions && name === 'init') html += initWizard(stage.questions, false);
    if (stage.extra === 'init-other') html += initOtherAnswers();
    if (stage.extra === 'template-basics') html += templateWizard([], true, false);
    if (stage.extra === 'template-layers') html += templateWizard([], false, true);
    if (stage.extra === 'template-answers') html += templateWizard(stage.questions || [], false, false);
    if (['decision make', 'decision propose', 'decision accept', 'question remove'].includes(name)
      && stage.names.some((n) => ['component', 'number', 'question'].includes(n))) html += questionPreview(name);
    return html || `<p class="muted small">${esc(t('No parameters for this step.', 'Aucun paramètre pour cette étape.'))}</p>`;
  }

  /** A step is green only when its required values are present; orange means a required
   *  choice/answer is still missing. Optional settings never block a step. */
  function commandStageReady(stage, command, params) {
    if (stage.review) return false;
    const fieldsReady = (stage.names || []).every((name) => {
      const param = params.find((p) => p.name === name);
      if (!param || !param.required) return true;
      return String(ui.form[name] ?? '').trim().length > 0;
    });
    if (!fieldsReady) return false;
    if (stage.extra === 'init-template' && !String(ui.form.template || ui.initTemplate || '').trim()) return false;
    const source = command.path.join(' ') === 'init' ? templates().find((x) => str(x, 'name') === ui.initTemplate)
      : command.path.join(' ') === 'template new' ? baseTemplate() : null;
    const questions = source ? list(source, 'questions') : [];
    return (stage.questions || []).every((id) => {
      const question = questions.find((q) => str(q, 'id') === id);
      if (!question || question.ask !== true) return true;
      const input = command.path.join(' ') === 'init' ? ui.initAnswers : ui.tplAnswers;
      const value = id in input ? input[id] : str(question, 'default');
      return String(value ?? '').trim().length > 0;
    });
  }

  /** Update step indicators in-place while typing; no form rerender or focus loss. */
  function refreshCommandStepIndicators() {
    const nav = document.querySelector('.guided-command-column .guided-steps');
    if (!nav) return;
    const command = I.commands.find((candidate) => candidate.path.join(' ') === ui.command);
    if (!command) return;
    const stages = commandStages(command, command.parameters);
    const allReady = stages.filter((s) => !s.review).every((s) => commandStageReady(s, command, command.parameters));
    nav.querySelectorAll('.guided-step').forEach((button, index) => {
      const stage = stages[index];
      if (!stage) return;
      const ready = stage.review ? allReady : commandStageReady(stage, command, command.parameters);
      button.classList.toggle('complete', ready);
      button.classList.toggle('incomplete', !ready);
      button.title = ready ? t('Step complete', 'Étape complète') : t('Needs attention', 'À compléter');
    });
  }

  function commandForm(command) {
    const name = command.path.join(' ');
    const entry = formEntry(name);
    const extra = name === 'specify' ? t('IDE mode: clarification without interactive terminal.', 'Mode IDE : clarification sans terminal interactif.')
      : name === 'rule add' ? t('One argument per line in words.', 'Un argument par ligne dans le champ words.') : '';
    const order = Array.isArray(entry.essential) ? entry.essential : [];
    const rank = (p) => (order.includes(p.name) ? order.indexOf(p.name) : order.length);
    const params = command.parameters.map((p, i) => [p, i]).sort((a, b) => rank(a[0]) - rank(b[0]) || a[1] - b[1]).map(([p]) => p);
    const stages = commandStages(command, params);
    ui.formStep = Math.min(ui.formStep, Math.max(0, stages.length - 1));
    const current = stages[ui.formStep];
    const wizard = stages.length > 1;
    const allReady = stages.filter((stage) => !stage.review).every((stage) => commandStageReady(stage, command, params));
    const nav = wizard ? `<nav class="guided-steps" aria-label="${esc(t('Form steps', 'Étapes du formulaire'))}">${stages.map((s, i) => {
      const ready = s.review ? allReady : commandStageReady(s, command, params);
      const status = ready ? 'complete' : 'incomplete';
      return `<button type="button" class="guided-step ${status} ${i === ui.formStep ? 'current' : ''}" data-a="formStage" data-index="${i}" aria-current="${i === ui.formStep ? 'step' : 'false'}" title="${esc(ready ? t('Step complete', 'Étape complète') : t('Needs attention', 'À compléter'))}"><span>${i + 1}</span>${esc(s.title)}</button>`;
    }).join('')}</nav>` : '';
    const fields = current ? guidedFormStage(command, current, params) : '';
    const counter = wizard ? `<div class="guided-counter">${esc(current.title)} · ${ui.formStep + 1} / ${stages.length}</div>` : '';
    const previous = wizard && ui.formStep > 0 ? btn(t('← Previous', '← Précédent'), 'formPrevious', '', false, 'secondary') : '';
    const next = wizard && ui.formStep < stages.length - 1 ? btn(t('Next →', 'Suivant →'), 'formNext', '', false, 'secondary') : '';
    const title = pair(entry.title) || command.description;
    const verb = (ui.form.output || '').trim() && command.parameters.some((p) => p.plugin && p.name === 'output')
      ? t('Save the report', 'Enregistrer le rapport') : pair(entry.verb) || t('Run command', 'Exécuter la commande');
    const does = pair(entry.does);
    const shown = ui.preview.text || ui.preview.missing;
    const pane = `<aside class="form-pane"><h4>${esc(t('What archvow will do', 'Ce que va faire archvow'))}</h4>
  ${does ? `<p>${esc(does)}</p>` : ''}<div class="muted small">${esc(t('The command', 'La commande'))}</div>
  ${terminalCommand(shown || 'archvow ' + name, { id: 'form-preview', missing: !ui.preview.text })}
  ${name === 'init' ? `<p class="muted small">${esc(t('Main files preview; the chosen template and answers may generate additional guard files. No source code folders are created.', 'Aperçu des principaux fichiers ; le modèle et les réponses peuvent générer d’autres fichiers de garde. Aucun dossier de code source n’est créé.'))}</p>` : ''}
  <div id="form-files">${filesBlock(ui.preview.files)}</div>
  <p class="muted small">${esc(t('If archvow refuses (a name already taken, words that protect nothing...), its message shows in red under the form, before anything is written.',
    "Si archvow refuse (un nom déjà pris, des mots qui ne protègent rien...), son message s'affiche en rouge sous le formulaire, avant que rien ne soit écrit."))}</p></aside>`;
    // Keep Cancel, Run and the result in their original location. Next/Previous
    // affect only the visible form fields, never the command submitted.
    return `<div class="form-head"><h3>${esc(title)}</h3><div class="muted small"><code>archvow ${esc(name)}</code> - ${esc(command.description)}${extra ? ' ' + esc(extra) : ''}</div>${ui.formHelp ? `<pre class="help">${esc(ui.formHelp)}</pre>` : ''}</div>
  <div class="form-grid guided-command-layout${ui.confirm && ui.confirm.target === 'command' ? ' has-confirm' : ''}"><div class="form guided-command-column">${nav}<div class="guided-command-fields" data-scroll="command-fields"><section class="guided-panel">${counter}${fields}</section></div>${formAlerts()}
  <div class="row end guided-actions">${previous}${next}${btn(t('Cancel', 'Annuler'), 'cancelForm', '', false, 'secondary')}${btn(verb, 'runForm')}</div></div>${pane}${confirmation('command')}</div>${resultCard('command')}`;
  }

  /** The files the command writes (filePlan.ts): a small tree per folder, then the code it plans. */
  function filesBlock(rows) {
    if (!Array.isArray(rows) || !rows.length) return '';
    const word = { new: t('new', 'nouveau'), updated: t('updated', 'modifié'), removed: t('removed', 'retiré'), kept: t('kept', 'gardé'), planned: t('planned', 'prévu') };
    const part = (items) => {
      let html = '';
      let folder = null;
      for (const r of items) {
        const path = String(r.path || '');
        const isDir = path.endsWith('/');
        const cut = isDir ? path.slice(0, -1).lastIndexOf('/') : path.lastIndexOf('/');
        const dir = cut >= 0 ? path.slice(0, cut + 1) : '';
        const leaf = cut >= 0 ? path.slice(cut + 1) : path;
        if (dir !== folder) {
          if (folder !== null) html += '</ul>';
          // Root files are siblings of the folder groups; do not render a "./" group.
          if (dir) html += `<div class="file-dir">${icon('folder')}<span>${esc(dir)}</span></div>`;
          html += `<ul class="file-list${dir ? '' : ' root-files'}">`;
          folder = dir;
        }
        const locationOnly = r.change === 'location';
        const status = locationOnly ? '' : `<span class="badge ${esc(r.change)}">${esc(word[r.change] || r.change)}</span>`;
        const line = locationOnly && Number(r.line) > 0 ? `<span class="muted small">${esc(t('line', 'ligne'))} ${Number(r.line)}</span>` : '';
        html += `<li class="file-row change-${esc(r.change)}">${icon(isDir ? 'folder' : 'document')}<code>${esc(leaf)}</code>${status}${line}${r.note ? `<div class="muted small file-note">${esc(r.note)}</div>` : ''}</li>`;
      }
      return html + (folder !== null ? '</ul>' : '');
    };
    const now = rows.filter((r) => r.change !== 'planned');
    const later = rows.filter((r) => r.change === 'planned');
    return (now.length ? `<div class="muted small files-head">${esc(t('Files', 'Fichiers'))}</div>${part(now)}` : '')
      + (later.length ? `<div class="muted small files-head">${esc(t('Planned - written later by the AI or a developer (archvow never writes code)', "Prévus - écrits plus tard par l'IA ou un développeur (archvow n'écrit jamais de code)"))}</div>${part(later)}` : '');
  }

  /** Check diagnostics use the identical Files renderer as the command form (not a copied icon). */
  function withCheckFiles(html) {
    return String(html || '').replace(/<div class="check-files-marker" data-check-path="([^"]*)" data-check-line="(\d+)"><\/div>/g,
      (_marker, encodedPath, line) => {
        let file;
        try { file = decodeURIComponent(encodedPath); } catch (_e) { return ''; }
        return filesBlock([{ path: file, change: 'location', line: Number(line) || 0 }]);
      });
  }

  function questionPreview(name) {
    const component = (ui.form.component || '').trim();
    const number = ((name === 'decision accept' || name === 'question remove' ? ui.form.number : ui.form.question) || '').trim();
    const view = S.snapshots[component];
    let hint;
    if (!component || !number) hint = `<i>${esc(t('Choose the component, then the question: it shows here with its answer.', "Choisis le composant, puis la question : elle s'affiche ici avec sa réponse."))}</i>`;
    else if (!view) hint = esc(t("Question not loaded. Click 'Load question' below.", 'Question non chargée. Clique sur « Charger la question ».'));
    else {
      // a question's number is its decision's (question add appends one); P1, O2... are the other questions
      let match = list(view, 'decisions').find((r) => str(r, 'number') === number);
      if (!match && name === 'decision make') match = list(view, 'questions').find((r) => str(r, 'key') === number);
      if (!match) hint = esc(t('Question not found for this component.', 'Question introuvable pour ce composant.'));
      else {
        const answer = str(match, 'answer');
        const proposed = str(match, 'proposed');
        hint = `<b>${esc(component + ' · ' + number)}</b> ${chip(answer ? t('Answered', 'Répondu') : t('Pending', 'En attente'), answer ? 'good' : 'warn')}
  <p><b>${esc(str(match, 'question'))}</b></p>${answer ? `<p>${esc(t('Answer', 'Réponse'))}: ${esc(answer)}</p>` : ''}${proposed ? `<p>${esc(t('AI proposal', 'Proposition IA'))}: ${esc(proposed)}</p>` : ''}`;
      }
    }
    return `<fieldset class="card"><legend>${esc(t('Question and status', 'Question et statut'))}</legend>${hint}<div class="row">${btn(t('Load question', 'Charger la question'), 'loadQuestion', '', !component, 'secondary')}</div></fieldset>`;
  }

  // ---------------------------------------------------------------- init wizard (archvow catalog --json)

  function templates() {
    return list(ui.catalog, 'templates');
  }

  /** The project's template (pyspark-pipeline@1.17.0 -> pyspark-pipeline), '' outside a project. */
  function projectTemplate() {
    const container = list(S && S.spec, 'containers')[0];
    return container ? str(container, 'template').split('@')[0] : '';
  }

  function chooseInitTemplate() {
    const requested = (ui.form.template || '').trim();
    const names = templates().map((x) => str(x, 'name'));
    const match = templates().find((x) => requested && (str(x, 'name') === requested || str(x, 'source') === requested || requested.endsWith('/' + str(x, 'name'))));
    if (match) ui.initTemplate = str(match, 'name');
    else if (requested) ui.initTemplate = ''; // a custom template path: its questions are not in this catalog
    else {
      // in a project: its own template (init --force there rewrites its files); else python-app
      const own = projectTemplate();
      ui.initTemplate = names.includes(own) ? own : names.includes('python-app') ? 'python-app' : names[0] || '';
      if (ui.initTemplate) ui.form.template = ui.initTemplate;
    }
  }

  /** A question of a template as a field: its choices only, several of them, or text (checked against its format). */
  function questionControl(q, value, kind) {
    const id = str(q, 'id');
    const choices = Array.isArray(q.choices) ? q.choices.map(String) : [];
    if (q.multiple === true && choices.length) {
      const picked = new Set(value.split(',').map((x) => x.trim()));
      return `<div class="row wrap">${choices.map((c) => `<label class="check"><input type="checkbox" data-change="${kind}Multi" data-id="${esc(id)}" data-choice="${esc(c)}" ${picked.has(c) ? 'checked' : ''}> ${esc(c)}</label>`).join('')}</div>`;
    }
    if (choices.length) {
      return `<select data-change="${kind}Answer" data-id="${esc(id)}">${choices.map((c) => `<option ${c === value ? 'selected' : ''}>${esc(c)}</option>`).join('')}</select>`;
    }
    const bad = value && str(q, 'pattern') && !new RegExp(str(q, 'pattern')).test(value);
    return `<input class="${bad ? 'invalid' : ''}" data-input="${kind}Answer" data-id="${esc(id)}" data-keep="${kind}-${esc(id)}" value="${esc(value)}">`
      + (str(q, 'pattern_hint') ? `<div class="muted small">${esc(t('Format: ', 'Format : ') + str(q, 'pattern_hint'))}</div>` : '');
  }

  /** init: the template, chosen once here, and its questions (under the package's name). */
  function initWizard(questionIds = null, showSelector = true) {
    const template = templates().find((x) => str(x, 'name') === ui.initTemplate);
    const custom = (ui.form.template || '').trim() && !template;
    let questions = '';
    if (template) {
      for (const q of list(template, 'questions')) {
        const id = str(q, 'id');
        if (!id) continue;
        const essential = q.ask === true;
        if (questionIds ? !questionIds.includes(id) : !essential && !ui.advanced) continue;
        const value = id in ui.initAnswers ? ui.initAnswers[id] : str(q, 'default');
        questions += `<div class="field"><label title="${esc(str(q, 'prompt'))}">${esc(id)}${essential ? '  •' : ''}</label><div>${questionControl(q, value, 'init')}<div class="muted small">${esc(str(q, 'prompt'))}</div></div></div>`;
      }
    }
    const status = custom ? t('Custom template path: retaining your input; its questions are not in this catalog.', 'Modèle personnalisé : conserve le chemin saisi ; les questions ne sont pas connues.')
      : ui.catalogSource || t('Loading archvow catalog --json…', 'Chargement depuis archvow catalog --json…');
    const own = projectTemplate();
    const here = own ? `<p class="note small">${esc(t(`This folder is already an archvow project (template ${own}): init needs --force, which rewrites the generated files (AGENTS.md, CI, hooks...) and keeps the committed spec.`,
      `Ce dossier est déjà un projet archvow (modèle ${own}) : init demande --force, qui réécrit les fichiers générés (AGENTS.md, CI, hooks...) et garde la spec commitée.`))}</p>` : '';
    const description = template ? str(template, 'description') : '';
    return `<fieldset class="card"><legend>${esc(showSelector ? t('Template and its questions', 'Modèle et ses questions') : t('Template answers', 'Réponses du modèle'))}</legend>${showSelector ? `
  <div class="field"><label>* ${esc(t('template', 'modèle'))}</label><div><div class="row wrap"><select data-change="initTemplate">${templates().map((x) => `<option ${str(x, 'name') === ui.initTemplate ? 'selected' : ''}>${esc(str(x, 'name'))}</option>`).join('')}${custom ? `<option selected value="">${esc(t('(a folder, below)', '(un dossier, ci-dessous)'))}</option>` : ''}</select>
  ${btn(t('Reload', 'Actualiser'), 'reloadCatalog', '', false, 'secondary')}</div>
  ${description ? `<div class="muted small">${esc(description)}</div>` : ''}
  <div class="muted small">${esc(t('or the folder of a company template:', "ou le dossier d'un modèle d'entreprise :"))} <input data-input="form" data-name="template" data-keep="f-template-folder" value="${esc(custom ? ui.form.template : '')}" placeholder="/path/to/templates/acme-pipeline"></div></div></div>
  ${here}<p class="muted small">${esc(status)}</p>
  <label class="check"><input type="checkbox" data-change="advanced" ${ui.advanced ? 'checked' : ''}> ${esc(t('Show advanced settings', 'Afficher les réglages avancés'))}</label>
  ` : ''}<div class="form">${questions}</div></fieldset>`;
  }

  function initOtherAnswers() {
    return `<fieldset class="card"><legend>${esc(t('Other answers (optional)', 'Autres réponses (facultatif)'))}</legend>
  <textarea rows="2" data-input="form" data-name="answer" data-keep="f-answer" title="${esc(t('Optional extra answers, one KEY=VALUE per line', 'Réponses supplémentaires : KEY=VALUE par ligne'))}">${esc(ui.form.answer || '')}</textarea></fieldset>`;
  }

  /** template new: the base chosen in --extends, as the catalog knows it. */
  function baseTemplate() {
    const name = (ui.form.extends || '').trim();
    return name ? templates().concat(list(I.catalog, 'templates')).find((x) => str(x, 'name') === name) : undefined;
  }

  /**
   * template new: the settings of the base, filled in with its values. What the company may set - the
   * defaults of the init answers, the folder of each layer - is editable, within what the base allows
   * (its choices, its formats); what the base imposes - its layers, its version, what it extends - is shown,
   * not editable. Only what differs from the base is written into the new template.
   */
  function templateWizard(questionIds = null, showHeader = true, showLayers = true) {
    const name = (ui.form.extends || '').trim();
    if (!name) return '';
    const base = baseTemplate();
    if (!base) {
      return `<p class="note small">${esc(t(`The catalog does not list '${name}': its settings cannot be shown here; archvow checks them when the command runs.`,
        `Le catalogue ne liste pas « ${name} » : ses réglages ne peuvent pas être montrés ici ; archvow les vérifie à l'exécution.`))}</p>`;
    }
    const chain = [str(base, 'name') + '@' + str(base, 'version'), ...(Array.isArray(base.extends) ? base.extends.map(String) : [])].join(' → ');
    const fixed = `<div class="field"><label>${esc(t('imposed', 'imposé'))}</label><div class="muted small">${esc(t(
      `Builds on ${chain}. The new template starts at version 0.1.0. Its layers are the base's (${list(base, 'layers').map((l) => str(l, 'name')).join(', ')}): only their folders can be renamed. Choices and formats are the base's.`,
      `S'appuie sur ${chain}. Le nouveau modèle démarre en version 0.1.0. Ses couches sont celles de la base (${list(base, 'layers').map((l) => str(l, 'name')).join(', ')}) : seuls leurs dossiers peuvent être renommés. Les choix et formats sont ceux de la base.`))}</div></div>`;
    let questions = '';
    for (const q of list(base, 'questions')) {
      const id = str(q, 'id');
      if (!id || (questionIds ? !questionIds.includes(id) : q.ask !== true && !ui.advanced)) continue;
      const value = id in ui.tplAnswers ? ui.tplAnswers[id] : str(q, 'default');
      const changed = id in ui.tplAnswers && ui.tplAnswers[id] !== str(q, 'default');
      questions += `<div class="field"><label title="${esc(str(q, 'prompt'))}">${esc(id)}${changed ? ' ✎' : ''}</label><div>${questionControl(q, value, 'tpl')}<div class="muted small">${esc(str(q, 'prompt'))}${str(q, 'default') ? esc(t(` (base: ${str(q, 'default')})`, ` (base : ${str(q, 'default')})`)) : ''}</div></div></div>`;
    }
    const layers = list(base, 'layers').map((l) => {
      const layer = str(l, 'name');
      const folder = layer in ui.tplLayers ? ui.tplLayers[layer] : layer;
      return `<div class="field"><label><code>${esc(layer)}</code></label><div><input data-input="tplLayer" data-id="${esc(layer)}" data-keep="tpl-layer-${esc(layer)}" value="${esc(folder)}"><div class="muted small">${esc(str(l, 'description'))}</div></div></div>`;
    }).join('');
    return `<fieldset class="card"><legend>${esc(t(`Settings of ${name}`, `Réglages de ${name}`))}</legend>${showHeader ? fixed : ''}
  ${showHeader ? `<p class="muted small">${esc(t('The company defaults of the init answers: a project can still change them (-a). Only those that differ from the base are written.',
    "Les valeurs par défaut de l'entreprise pour les réponses d'init : un projet peut encore les changer (-a). Seules celles qui diffèrent de la base sont écrites."))}</p>
  <label class="check"><input type="checkbox" data-change="advanced" ${ui.advanced ? 'checked' : ''}> ${esc(t('Show advanced settings', 'Afficher les réglages avancés'))}</label>
  ` : ''}<div class="form">${questions}</div>
  ${showLayers ? `<p class="muted small">${esc(t('The folder of each layer (src/<package>/<folder>/, tests/<folder>/):', 'Le dossier de chaque couche (src/<package>/<dossier>/, tests/<dossier>/) :'))}</p><div class="form">${layers}</div>` : ''}</fieldset>`;
  }

  /** template new: the -a and --layer lines of what differs from the base; a format the base refuses, as a message. */
  function templateSettings() {
    const base = baseTemplate();
    const out = { answer: [], layer: [], problem: '' };
    if (!base) return out;
    for (const q of list(base, 'questions')) {
      const id = str(q, 'id');
      if (!(id in ui.tplAnswers)) continue;
      let value = ui.tplAnswers[id].trim();
      if (q.multiple === true) {
        const picked = value.split(',').map((x) => x.trim()).filter((x) => x);
        value = picked.includes('none') ? 'none' : picked.includes('all') ? 'all' : picked.join(',');
      }
      if (value === str(q, 'default')) continue;
      if (value && str(q, 'pattern') && !new RegExp(str(q, 'pattern')).test(value)) {
        out.problem = t(`${id}: ${str(q, 'pattern_hint') || 'not in the format of the base'}`, `${id} : ${str(q, 'pattern_hint') || "pas au format de la base"}`);
      }
      out.answer.push(`${id}=${value}`);
    }
    for (const l of list(base, 'layers')) {
      const layer = str(l, 'name');
      const folder = (ui.tplLayers[layer] || '').trim();
      if (folder && folder !== layer) out.layer.push(`${layer}=${folder}`);
    }
    return out;
  }

  /** The -a KEY=VALUE answers of the wizard: the shown questions, and those a person changed. */
  function initAnswers() {
    const template = templates().find((x) => str(x, 'name') === ui.initTemplate);
    if (!template) return [];
    const answers = [];
    for (const q of list(template, 'questions')) {
      const id = str(q, 'id');
      if (!id) continue;
      const shown = q.ask === true || ui.advanced;
      if (!shown && !ui.initTouched[id]) continue;
      let value = id in ui.initAnswers ? ui.initAnswers[id] : str(q, 'default');
      if (q.multiple === true) {
        const picked = value.split(',').map((x) => x.trim()).filter((x) => x);
        value = picked.includes('none') ? 'none' : picked.includes('all') ? 'all' : picked.join(',');
      }
      if (value.trim()) answers.push(id + '=' + value.trim());
    }
    return answers;
  }

  // ---------------------------------------------------------------- navigation

  function selectComponent(id, reload = true) {
    if (ui.component !== id && ui.confirm && ['decision', 'question', 'safety'].includes(ui.confirm.target)) {
      post({ type: 'cancelled', token: ui.confirm.token });
      ui.confirm = null;
    }
    if (ui.component !== id) {
      // another component: its views start on their first open item (current())
      for (const kind of ['decision', 'question', 'safety', 'assumption', 'data']) ui.sel[kind] = '';
      ui.sel.other = -1; // Do not carry an Other selection from the previous component.
      for (const kind of ['decision', 'question', 'safety', 'assumption']) delete ui.results[kind];
    }
    ui.component = id;
    if (reload) post({ type: 'selectComponent', component: id });
  }

  /** Clarify: the first decision, then question, then safety question still open (IntelliJ guidedNext). */
  function chooseNext() {
    const view = snapshot();
    if (!view) return;
    ui.mode = 'component';
    const decision = list(view, 'decisions').find((r) => !str(r, 'answer'));
    if (decision) { ui.view = 0; ui.sel.decision = 'D' + str(decision, 'number'); return; }
    const question = list(view, 'questions').find((r) => !str(r, 'answer'));
    if (question) { ui.view = 1; ui.sel.question = str(question, 'key') || str(question, 'number'); return; }
    const safety = list(view, 'safety').find((r) => str(r, 'status').toLowerCase() === 'open');
    if (safety) { ui.view = 2; ui.sel.safety = 'S' + str(safety, 'number'); return; }
    post({ type: 'message', text: t('All decision and safety questions are answered for this component.', 'Toutes les décisions et questions de sécurité sont traitées pour ce composant.') });
  }

  function viewForItem(item) {
    const byView = { decision: 0, question: 1, safety: 2, assumption: 3, data: 5, other: 7 };
    if (item.preset.view in byView) return byView[item.preset.view];
    switch (item.command) {
      case 'decision accept': case 'decision propose': return 0;
      case 'decision make': return /^[0-9]+$/.test(item.preset.question || '') ? 0 : 1;
      case 'hazard add': case 'hazard dismiss': case 'invariant add': return 2;
      case 'assumption accept': case 'assumption reject': return 3;
      case 'column set': case 'prompt': return 5;
      default: return 6;
    }
  }

  function selectFromItem(item) {
    const p = item.preset;
    const number = p.number || p.question || '';
    if (p.guide_word) ui.sel.safety = p.guide_word.startsWith('S') ? p.guide_word : 'S' + p.guide_word;
    else if (/^[0-9]+$/.test(number)) {
      if (p.view === 'assumption' || item.command.startsWith('assumption')) ui.sel.assumption = 'A' + number;
      else ui.sel.decision = 'D' + number;
    } else if (number) ui.sel.question = number;
  }

  const isOther = (item) => {
    if (!item.component) return true;
    if (['decision make', 'decision propose', 'decision accept', 'hazard add', 'hazard dismiss', 'assumption accept', 'assumption reject',
      'invariant add', 'column set', 'prompt'].includes(item.command)) return false;
    if (item.command === 'component-show') return item.kind === 'WARNING' || item.kind === 'ERROR';
    return true;
  };

  /** The pending / resolved counters: the first component with such work, else Other / Project. */
  function openQuickList(index) {
    ui.forceWorkspace = true;
    ui.mode = 'component';
    const all = index === 0 ? S.work.pending : S.work.resolved;
    const first = all.find((item) => item.component && item.source !== 'check' && !isOther(item))
      || all.find((item) => item.component && item.source !== 'check');
    if (first) {
      ui.filter = index === 0 ? 'pending' : 'resolved';
      selectComponent(first.component);
      ui.view = viewForItem(first);
      selectFromItem(first);
    } else {
      ui.filter = 'all';
      ui.otherList = index;
      ui.sel.other = -1;
      const projectItems = all.filter(isProjectOther);
      if (projectItems.length) { ui.mode = 'project'; }
      else { ui.mode = 'checks'; ui.checkSel = 0; }
    }
  }

  function quickItem() {
    const items = otherItems(ui.otherList);
    return items[ui.sel.other];
  }

  function goToComponent(id) {
    ui.mode = 'component';
    selectComponent(id);
  }

  function quickPrimary() {
    const item = quickItem();
    if (!item) return;
    switch (item.command) {
      // the invariant a hazard needs is written in the component's Safety view, filled with the suggestion
      case 'decision make': case 'decision accept': case 'decision propose': case 'hazard add': case 'hazard dismiss': case 'invariant add':
        if (!item.component) return;
        goToComponent(item.component);
        ui.view = item.command.startsWith('hazard') || item.command === 'invariant add' ? 2 : viewForItem(item);
        selectFromItem(item);
        return;
      case 'schema-create': {
        const container = list(S.spec, 'containers').find((c) => list(c, 'components').some((x) => str(x, 'id') === item.component));
        post({ type: 'openCommand', command: 'component add', values: Object.assign({ type: 'schema' }, container ? { container: str(container, 'id') } : {}), selected: ui.component });
        return;
      }
      case 'component edit':
        post({ type: 'openCommand', command: item.command, values: item.preset, selected: ui.component });
        return;
      case 'open-file':
        post({ type: 'openFile', file: item.preset.file || '', line: Number(item.preset.line) || 0 });
        return;
      case 'open-diagnostics':
        post({ type: 'showCheckDetails', index: 0 });
        return;
      case 'open-project-vow':
        post({ type: 'openProjectVow' });
        return;
      case 'component-show':
        if (item.component) goToComponent(item.component);
        return;
      case 'prompt':
        post({ type: 'run', label: 'prompt', args: ['prompt', item.component], target: 'quick' });
        return;
      default:
        post({ type: 'openCommand', command: item.command, values: item.preset, selected: ui.component });
    }
  }

  function quickSecondary() {
    const item = quickItem();
    if (!item) return;
    switch (item.command) {
      case 'hazard add':
        post({ type: 'openCommand', command: 'hazard dismiss', values: { component: item.component, guide_word: item.preset.guide_word || '' }, selected: ui.component });
        return;
      case 'assumption accept':
        post({ type: 'openCommand', command: 'assumption reject', values: item.preset, selected: ui.component });
        return;
      case 'decision accept':
        post({ type: 'openCommand', command: 'decision make', values: { component: item.component, question: item.preset.number || '' }, selected: ui.component });
        return;
      case 'schema-create':
        post({ type: 'openCommand', command: 'component edit', values: { component: item.component }, selected: ui.component });
        return;
      case 'brief accept':
        post({ type: 'openBrief', ref: item.preset.brief || '' });
        return;
      default:
        if (item.guide && item.guide.file && item.command !== 'open-file' && !['open-file', 'component edit', 'invariant add'].includes(item.command)) {
          post({ type: 'openFile', file: item.guide.file.path, line: item.guide.file.line });
        } else if (item.component) goToComponent(item.component);
    }
  }

  // ---------------------------------------------------------------- the person's actions

  function selectedRow(kind) {
    const view = snapshot();
    if (!view) return null;
    const lists = { decision: ['decisions', (r) => 'D' + str(r, 'number')], question: ['questions', (r) => str(r, 'key') || str(r, 'number')],
      safety: ['safety', (r) => 'S' + str(r, 'number')], assumption: ['assumptions', (r) => 'A' + str(r, 'number')] };
    const [key, label] = lists[kind];
    return list(view, key).find((r) => label(r) === ui.sel[kind]) || null;
  }

  const actions = {
    tab: (e) => { ui.tab = e.dataset.tab; ui.filterMenu = false; ui.projectMore = false; },
    openComponents: () => { ui.tab = 'components'; ui.filter = 'all'; },
    openBriefCommands: () => {
      ui.projectMore = false;
      if (NAV) post({ type: 'openBriefs' });
      else { ui.forceWorkspace = true; ui.mode = 'briefs'; ui.back = null; }
    },
    projectMore: () => { ui.projectMore = !ui.projectMore; },
    filterMenu: () => { ui.filterMenu = !ui.filterMenu; },
    back: () => {
      // the component the command was opened from, on its view: the extension shows it again (and reads it)
      const to = ui.back;
      ui.back = null;
      if (!to) return;
      if (to.briefs) {
        ui.forceWorkspace = true;
        ui.mode = 'briefs';
        return;
      }
      ui.view = to.view;
      post({ type: 'show', component: to.component });
    },
    overSub: (e) => {
      if (e.dataset.sub === 'briefs') { actions.openBriefCommands(); return; }
      ui.overSub = e.dataset.sub;
    },
    refresh: () => post({ type: 'refresh' }),
    install: () => post({ type: 'install' }),
    openBrief: (e) => post({ type: 'openBrief', ref: e.dataset.ref }),
    addFolder: (e) => post({ type: 'openCommand', command: 'init', values: { package: e.dataset.package, force: 'true', answer: e.dataset.answer }, selected: ui.component }),
    log: () => post({ type: 'log' }),
    settings: () => post({ type: 'settings' }),
    sidebar: () => post({ type: 'sidebar' }),
    init: () => {
      if (NAV) { post({ type: 'initCommand' }); return; }
      ui.forceWorkspace = true;
      ui.mode = 'command';
      setCommand('init', {}, '');
    },
    run: (e) => post({ type: 'run', label: e.dataset.label, args: e.dataset.args.split(' '), target: e.dataset.target }),
    budget: () => {
      const base = baseNow().trim();
      if (base) post({ type: 'run', label: 'next --base', args: ['next', '--base', base, '--json'], target: 'overview' });
      else post({ type: 'message', text: t('No branch to compare with: this repository has no other branch yet.', "Aucune branche à comparer : ce dépôt n'a pas encore d'autre branche.") });
    },
    openProjectVow: () => post({ type: 'openProjectVow' }),
    openChecks: () => post({ type: 'openChecks' }),
    openChecksReference: () => post({ type: 'openChecksReference' }),
    projectWork: () => post({ type: 'showProjectWork', index: 0 }),
    openRule: (e) => post({ type: 'openRule', code: e.dataset.code }),
    find: (e) => {
      const name = (e.dataset.id || ui.findText || '').trim();
      if (e.dataset.id) ui.findText = e.dataset.id;
      if (name) post({ type: 'search', name });
    },
    searchGroup: (e) => { ui.searchGroup = e.dataset.id || ''; ui.searchSel = 0; },
    searchHit: (e) => { ui.searchSel = Number(e.dataset.i) || 0; },
    exampleFile: (e) => { ui.exampleFile[e.dataset.id] = e.dataset.key; },
    copyText: (e) => post({ type: 'copy', text: e.dataset.text || '' }),
    prepareCheck: (e) => {
      const v = list(S.check, 'violations')[Number(e.dataset.i)];
      const item = checkWorkItem(v);
      if (!checkFixable(item)) return;
      if (item.command === 'schema-create') {
        const container = list(S.spec, 'containers').find((c) => list(c, 'components').some((x) => str(x, 'id') === item.component));
        post({ type: 'openCommand', command: 'component add', values: Object.assign({ type: 'schema' }, container ? { container: str(container, 'id') } : {}), selected: item.component });
        return;
      }
      post({ type: 'openCommand', command: item.command, values: item.preset, selected: item.component || ui.component });
    },
    openFile: (e) => post({ type: 'openFile', file: e.dataset.file, line: Number(e.dataset.line) || 0 }),
    quickList: (e) => {
      // the sidebar shows the components with such work; the editor tab opens the first of them
      const index = Number(e.dataset.i);
      const all = index === 0 ? S.work.pending : S.work.resolved;
      const componentsExist = all.some((item) => item.component && item.source !== 'check');
      ui.tab = componentsExist ? 'components' : 'overview';
      if (!componentsExist) ui.overSub = 'checks';
      ui.filter = componentsExist ? (index === 0 ? 'pending' : 'resolved') : 'all';
      post({ type: 'quickList', index });
    },
    checkSel: (e) => {
      ui.checkSel = Number(e.dataset.i);
      if (NAV) post({ type: 'showCheckDetails', index: ui.checkSel });
    },
    // the sidebar marks its choice at once; the editor tab changes component when the extension says so
    openComponentRow: (e) => { if (NAV) ui.component = e.dataset.id; post({ type: 'show', component: e.dataset.id }); },
    pick: (e) => { if (NAV) ui.component = e.dataset.id; post({ type: 'show', component: e.dataset.id }); },
    filter: (e) => { ui.filterMenu = false; ui.filter = e.dataset.filter; ui.tab = 'components'; },
    addComponent: () => post({ type: 'openCommand', command: 'component add', values: {}, selected: '' }),
    addHere: (e) => { ui.addLayer = ui.addLayer === e.dataset.id ? '' : e.dataset.id; },
    addType: (e) => { ui.addLayer = ''; post({ type: 'openCommand', command: 'component add', values: { type: e.dataset.id }, selected: '' }); },
    view: (e) => {
      const i = Number(e.dataset.i);
      if (ui.view !== i && ui.confirm && ['decision', 'question', 'safety'].includes(ui.confirm.target)) {
        post({ type: 'cancelled', token: ui.confirm.token });
        ui.confirm = null;
      }
      ui.view = i;
    },
    sel: (e) => {
      const kind = e.dataset.kind;
      if (ui.sel[kind] !== e.dataset.key && ui.confirm && ui.confirm.target === kind) {
        post({ type: 'cancelled', token: ui.confirm.token });
        ui.confirm = null;
      }
      ui.sel[kind] = e.dataset.key;
      ui.advance = '';
      delete ui.results[kind];
    },
    otherList: (e) => { ui.otherList = Number(e.dataset.i); ui.sel.other = -1; },
    otherSel: (e) => { ui.sel.other = Number(e.dataset.i); delete ui.results.quick; },
    quickPrimary, quickSecondary,
    openSpec: () => { if (ui.component) post({ type: 'openSpec', component: ui.component }); },
    openVowFile: () => { if (ui.component) post({ type: 'openVowFile', component: ui.component }); },
    specPart: (e) => { ui.specPart = e.dataset.id; },
    openCode: () => { if (ui.component) post({ type: 'openCode', component: ui.component }); },
    aiPrompt: () => { ui.aiMenu = false; if (ui.component) post({ type: 'run', label: 'prompt', args: ['prompt', ui.component], target: 'component' }); },
    addRule: () => post({ type: 'openCommand', command: 'rule add', values: ui.component ? { words: ui.component + '\n' } : {}, selected: ui.component }),
    openCommand: (e) => {
      const values = {};
      if (e.dataset.component) values.component = e.dataset.component;
      if (e.dataset.number) values.number = e.dataset.number;
      if (e.dataset.id) values.id = e.dataset.id;  // hazard accept, rule accept / remove (0.16.0)
      if (e.dataset.brief) values.brief = e.dataset.brief;  // brief accept, from Overview › Briefs
      if (e.dataset.field === 'base' && baseNow().trim()) values.base = baseNow().trim();
      post({ type: 'openCommand', command: e.dataset.command, values, selected: ui.component });
    },
    // ---- AI skills (0.16.0)
    aiMenu: () => { ui.aiMenu = !ui.aiMenu; ui.addMenu = false; },
    addMenu: () => { ui.addMenu = !ui.addMenu; ui.aiMenu = false; },
    openSkill: (e) => {
      const values = { component: e.dataset.component || (ui.mode === 'component' ? ui.component : ''), brief: e.dataset.brief || '' };
      if (NAV) { post({ type: 'openSkill', skill: e.dataset.skill, values }); return; }
      if (ui.mode === 'component' && ui.component) ui.back = { component: ui.component, view: ui.view };
      else if (ui.mode === 'briefs') ui.back = { briefs: true };
      else ui.back = null;
      ui.aiMenu = false;
      ui.mode = 'skill';
      ui.skill = e.dataset.skill;
      ui.skillStep = 0;
      ui.skillValues = Object.assign({ max: '5' }, values);
    },
    skillValue: (e) => { ui.skillValues[e.dataset.name] = e.dataset.value; },
    skillStage: (e) => { ui.skillStep = Math.max(0, Math.min(2, Number(e.dataset.index) || 0)); },
    skillNext: () => { ui.skillStep = Math.min(2, ui.skillStep + 1); },
    skillPrevious: () => { ui.skillStep = Math.max(0, ui.skillStep - 1); },
    askAi: (e) => post({ type: 'askAi', skill: ui.skill, tool: ui.skillValues.tool || '', values: ui.skillValues, send: e.dataset.send === '1', format: e.dataset.format || 'instructions' }),
    originFilter: (e) => { ui.originFilter = e.dataset.id; },
    toReview: () => {
      // what the AI added and no person kept yet: the first view that has some, filtered to them
      const ai = aiOf();
      ui.originFilter = 'review';
      if (ai) ui.view = ai.toReview.decisions ? 0 : ai.toReview.safety ? 2 : ai.toReview.rules ? 4 : ai.toReview.assumptions ? 3 : ui.view;
    },
    choose: (e) => {
      const row = selectedRow('safety');
      post({ type: 'choose', purpose: e.dataset.purpose, component: ui.component, number: row ? str(row, 'number') : '', only: e.dataset.only === 'true' });
    },
    decide: (e) => {
      const row = selectedRow('decision');
      if (!row) return;
      const key = `d:${ui.component}:${str(row, 'number')}`;
      const answer = draft(key, str(row, 'answer') || str(row, 'proposed') || str(row, 'suggested'));
      const action = e.dataset.action;
      if (action === 'decision accept' && !str(row, 'proposed')) {
        post({ type: 'message', text: t('No AI proposal to accept.', 'Aucune proposition IA à accepter.') });
        return;
      }
      if (action === 'decision propose' && str(row, 'answer')) {
        post({ type: 'message', text: t('This decision is already answered.', 'Cette décision a déjà une réponse.') });
        return;
      }
      if (action !== 'decision propose') ui.advance = 'decision';
      post({ type: 'inline', action, component: ui.component, number: str(row, 'number'), answer, question: str(row, 'question'), proposed: str(row, 'proposed') });
    },
    cancelDraft: (e) => {
      delete ui.drafts[e.dataset.key];
      if (ui.confirm && ui.confirm.target === e.dataset.target) {
        post({ type: 'cancelled', token: ui.confirm.token });
        ui.confirm = null;
      }
    },
    smartAdd: (e) => { (ui.smartRows[e.dataset.key] ||= []).push({ name: '', type: '', nullable: 'unknown', personal: 'unknown', note: '' }); ui.smartExpanded = e.dataset.key; },
    smartSeg: (e) => {
      const rows = e.dataset.kind === 'param' ? ui.paramRows[e.dataset.key] : ui.smartRows[e.dataset.key];
      const row = (rows || [])[Number(e.dataset.row)];
      if (row) row[e.dataset.name] = e.dataset.value;
      ui.smartExpanded = e.dataset.key;
    },
    paramAdd: (e) => { (ui.paramRows[e.dataset.key] ||= []).push({ name: '', type: 'str', default: '', note: '' }); ui.smartExpanded = e.dataset.key; },
    paramRemove: (e) => { (ui.paramRows[e.dataset.key] ||= []).splice(Number(e.dataset.row), 1); ui.smartExpanded = e.dataset.key; },
    paramUse: (e) => {
      const text = parametersAnswer(ui.paramRows[e.dataset.key] || []);
      if (text.trim()) { ui.drafts[e.dataset.key] = text; ui.smartExpanded = e.dataset.key; }
    },
    smartRemove: (e) => { (ui.smartRows[e.dataset.key] ||= []).splice(Number(e.dataset.row), 1); ui.smartExpanded = e.dataset.key; },
    smartUse: (e) => {
      const text = structuredAnswer(ui.smartRows[e.dataset.key] || [], ui.smartQuestions[e.dataset.key] || '');
      if (text.trim()) { ui.drafts[e.dataset.key] = text; ui.smartExpanded = e.dataset.key; }
    },
    useSuggestion: () => {
      const row = selectedRow('decision');
      if (row) ui.drafts[`d:${ui.component}:${str(row, 'number')}`] = str(row, 'suggested');
    },
    answerQuestion: () => {
      const row = selectedRow('question');
      if (!row) return;
      const label = str(row, 'key') || str(row, 'number');
      const answer = draft(`q:${ui.component}:${label}`, str(row, 'answer') || str(row, 'suggested'));
      ui.advance = 'question';
      post({ type: 'inline', action: 'question', component: ui.component, key: label, answer, question: str(row, 'question') });
    },
    safety: (e) => {
      const row = selectedRow('safety');
      if (!row) return;
      const key = `s:${ui.component}:${str(row, 'number')}`;
      const suggestion = row.suggestion || {};
      const d = derived();
      const i = list(snapshot(), 'safety').indexOf(row);
      post({ type: 'inline', action: e.dataset.action, component: ui.component, number: str(row, 'number'),
        word: str(row, 'guide_word') || 'S' + str(row, 'number'), text: draft(key, str(suggestion, 'hazard')), severity: ui.severity[key] || 'high',
        question: str(row, 'question'), preview: e.dataset.covered ? e.dataset.preview : d && d.safety[i] ? d.safety[i].preview : '', hazard: ui.changing[key] || '',
        ...(e.dataset.covered ? { covered: true, text: '' } : {}) });
    },
    useChoice: (e) => { ui.drafts[e.dataset.key] = e.dataset.value; },
    hazardChange: (e) => {
      const row = selectedRow('safety');
      if (!row) return;
      const key = `s:${ui.component}:${str(row, 'number')}`;
      ui.changing[key] = e.dataset.id;
      ui.drafts[key] = e.dataset.text || '';
      ui.severity[key] = e.dataset.severity || 'high';
    },
    cancelChange: (e) => {
      delete ui.changing[e.dataset.key];
      delete ui.drafts[e.dataset.key];
      delete ui.severity[e.dataset.key];
    },
    hazardRemove: (e) => {
      const row = selectedRow('safety');
      post({ type: 'inline', action: 'hazard remove', component: ui.component, id: e.dataset.id,
        number: row ? str(row, 'number') : '', question: row ? str(row, 'question') : '' });
    },
    invariantInline: (e) => {
      const row = selectedRow('safety');
      if (!row) return;
      const suggestion = row.suggestion || {};
      const initial = str(suggestion, 'invariant') || ui.suggested[`${ui.component}:${str(row, 'number')}`] || '';
      post({ type: 'inline', action: 'invariant add', component: ui.component, hazard: e.dataset.hazard, number: str(row, 'number'),
        text: draft(`i:${ui.component}:${str(row, 'number')}`, initial), question: str(row, 'question') });
    },
    dataAction: () => {
      const d = derived();
      const item = d && d.data.find((x) => x.kind + ':' + x.id === ui.sel.data);
      if (!item) return;
      if (item.kind === 'COLUMN') post({ type: 'openCommand', command: 'column set', values: { component: ui.component, names: item.id }, selected: ui.component });
      else post({ type: 'taskPrompt', component: ui.component, task: item.id });
    },
    commandGroup: (e) => { ui.commandGroupsOpen[e.dataset.id] = ui.commandGroupsOpen[e.dataset.id] !== true; },
    commandDocs: () => post({ type: 'commandDocs' }),
    removeUse: (e) => { ui.form.uses = (ui.form.uses || '').split(',').map((x) => x.trim()).filter((x) => x && x !== e.dataset.id).join(','); askPreview(0); },
    command: (e) => { ui.command = e.dataset.name; post({ type: 'openCommand', command: e.dataset.name, values: {}, selected: '' }); },
    segment: (e) => { ui.form[e.dataset.name] = e.dataset.value; askPreview(0); },
    formStage: (e) => { ui.formStep = Math.max(0, Number(e.dataset.index) || 0); },
    formNext: () => { ui.formStep++; },
    formPrevious: () => { ui.formStep = Math.max(0, ui.formStep - 1); },
    cancelForm: () => { setCommand(ui.command, {}, ''); },
    runForm: () => {
      const values = Object.assign({}, ui.form);
      if (ui.command === 'template new') {
        const settings = templateSettings();
        if (settings.problem) { post({ type: 'message', text: settings.problem }); return; }
        values.answer = settings.answer.join('\n');
        values.layer = settings.layer.join('\n');
      }
      post({ type: 'form', command: ui.command, values, initAnswers: ui.command === 'init' ? initAnswers() : [], selected: ui.component, baseline: ui.formBaseline });
    },
    expandResult: (e) => {
      const target = e.dataset.target;
      if (ui.resultHeight[target] && ui.resultHeight[target] >= expandedHeight() - 2) delete ui.resultHeight[target];
      else ui.resultHeight[target] = expandedHeight();
    },
    loadQuestion: () => post({ type: 'loadQuestion', component: (ui.form.component || '').trim() }),
    browse: (e) => post({ type: 'browse', field: e.dataset.field }),
    reloadCatalog: () => post({ type: 'catalog', template: ui.form.template || '', force: true }),
    confirm: () => { if (ui.confirm) post({ type: 'confirmed', token: ui.confirm.token }); ui.confirm = null; },
    cancelConfirm: () => { if (ui.confirm) post({ type: 'cancelled', token: ui.confirm.token }); ui.confirm = null; },
    raw: (e) => { ui.raw[e.dataset.target] = !ui.raw[e.dataset.target]; },
    copyResult: (e) => { const r = ui.results[e.dataset.target]; if (r && r.raw) post({ type: 'copy', text: r.raw }); },
    closeResult: (e) => { delete ui.results[e.dataset.target]; },
  };

  /** Two clicks on the same row: what a double-click does (the page is drawn again between them). */
  const DOUBLE = {
    checkSel: (e) => { const v = list(S.check, 'violations')[Number(e.dataset.i)]; if (v) post({ type: 'openRule', code: str(v, 'code') }); },
    otherSel: () => quickPrimary(),
    sel: (e) => { if (e.dataset.kind === 'data') actions.dataAction(); else if (e.dataset.kind !== 'example') focusEditor(); },
    searchHit: () => {
      const hit = searchHits(ui.search, ui.searchGroup)[ui.searchSel];
      if (hit && str(hit, 'file') && !str(hit, 'file').startsWith('template ')) post({ type: 'openFile', file: str(hit, 'file').replace(/\/$/, ''), line: Number(hit.line) || 0 });
    },
  };
  let last = { signature: '', time: 0 };

  function focusEditor() {
    window.setTimeout(() => {
      const field = document.querySelector('.split-answer textarea:not([disabled])');
      if (field) field.focus();
    }, 0);
  }

  document.body.addEventListener('click', (event) => {
    const target = event.target.closest('[data-a]');
    // The responsive More menu closes after an action or on an outside click.
    // The same action is used in either the toolbar or its overflow menu.
    if (ui.projectMore && (!target || target.dataset.a !== 'projectMore')) {
      ui.projectMore = false;
      if (!target) { render(); return; }
    }
    // a click anywhere but on a menu's own button closes the open menus (AI, Add)
    const menus = ['aiMenu', 'addMenu'];
    if ((ui.aiMenu || ui.addMenu) && !(target && menus.includes(target.dataset.a))) {
      const wasOpen = ui.aiMenu || ui.addMenu;
      ui.aiMenu = false; ui.addMenu = false;
      if (wasOpen && (!target || target.disabled)) { render(); return; }
    }
    if (!target || target.disabled) return;
    const action = actions[target.dataset.a];
    if (!action) return;
    const signature = [target.dataset.a, target.dataset.i, target.dataset.id, target.dataset.key, target.dataset.kind].join('|');
    const now = Date.now();
    const double = signature === last.signature && now - last.time < 450 && DOUBLE[target.dataset.a];
    last = double ? { signature: '', time: 0 } : { signature, time: now };
    action(target);
    if (double) DOUBLE[target.dataset.a](target);
    render();
  });

  document.body.addEventListener('keydown', (event) => {
    const e = event.target;
    if (e && e.dataset && e.dataset.input === 'find' && event.key === 'Enter') {
      ui.findText = e.value;
      actions.find(e);
    }
  });

  document.body.addEventListener('input', (event) => {
    const e = event.target;
    switch (e.dataset.input) {
      case 'find':
        ui.findText = e.value;
        return;
      case 'draft': {
        ui.drafts[e.dataset.key] = e.value;
        // Undo changes: pressable as soon as the text differs from the recorded one (no redraw while typing)
        const undo = document.querySelector(`[data-a="cancelDraft"][data-key="${CSS.escape(e.dataset.key)}"]`);
        if (undo) {
          const edited = e.value !== (e.dataset.initial || '');
          undo.disabled = !edited || e.disabled;
          undo.title = undoTip(edited);
        }
        return;
      }
      case 'smartField': {
        const rows = ui.smartRows[e.dataset.key] || [];
        const row = rows[Number(e.dataset.row)];
        if (row && ['name', 'type', 'nullable', 'personal', 'note'].includes(e.dataset.name)) row[e.dataset.name] = e.value;
        const preview = document.querySelector('.smart-answer .smart-preview[data-preview="column"]');
        if (preview) preview.textContent = structuredAnswer(rows, ui.smartQuestions[e.dataset.key] || '');
        return;
      }
      case 'paramField': {
        const rows = ui.paramRows[e.dataset.key] || [];
        const row = rows[Number(e.dataset.row)];
        if (row && ['name', 'type', 'default', 'note'].includes(e.dataset.name)) row[e.dataset.name] = e.value;
        const preview = document.querySelector('.smart-answer .smart-preview[data-preview="param"]');
        if (preview) preview.textContent = parametersAnswer(rows);
        const chip = row && e.dataset.name === 'default' ? document.querySelector(`.smart-answer [data-required="${Number(e.dataset.row)}"]`) : null;
        if (chip) { chip.textContent = e.value.trim() ? t('optional', 'facultatif') : t('required', 'obligatoire'); chip.classList.toggle('warn', !e.value.trim()); }
        return;
      }
      case 'base': ui.base = e.value; ui.baseTouched = true; return;
      case 'commandFilter': ui.commandFilter = e.value; render(); return;
      case 'skill': {
        ui.skillValues[e.dataset.name] = e.value;
        const pre = document.querySelector('.skill-form pre.request');
        if (pre) pre.textContent = skillRequest(ui.skill, ui.skillValues);
        document.querySelectorAll('.skill-form [data-a="askAi"]').forEach((b) => { b.disabled = !e.value.trim(); });
        return;
      }
      case 'form':
        ui.form[e.dataset.name] = e.value;
        askPreview();
        refreshCommandStepIndicators();
        if (['component', 'question', 'number', 'type', 'extends'].includes(e.dataset.name)
          || (e.dataset.name === 'output' && !!e.value.trim() !== !!(ui.outputShown || ''))) { ui.outputShown = e.value.trim() ? '1' : ''; render(); }
        return;
      case 'initAnswer': ui.initAnswers[e.dataset.id] = e.value; ui.initTouched[e.dataset.id] = true; refreshCommandStepIndicators(); return;
      case 'tplAnswer': ui.tplAnswers[e.dataset.id] = e.value; refreshCommandStepIndicators(); return;
      case 'tplLayer': ui.tplLayers[e.dataset.id] = e.value; return;
      default:
    }
  });

  /**
   * A value chosen in a list: it can fill other fields (Edit spec: the component's description, ticket,
   * uses...; rule edit: its text; decision make: the recorded or suggested answer), and a choice that
   * depended on the old component or type goes.
   */
  function choose(name, value) {
    const field = fieldOf(ui.command, name);
    const picked = field ? choicesNow(field).find((c) => c.value === value) : null;
    const fill = (picked && picked.fill) || {};
    if (ui.command === 'component edit' && name === 'component') {
      ui.form = Object.assign({ component: value }, fill);
      ui.formBaseline = Object.assign({}, fill);
      return;
    }
    ui.form[name] = value;
    Object.assign(ui.form, fill);
    const book = (S && S.choices && S.choices[ui.command]) || {};
    for (const [other, f] of Object.entries(book)) {
      if (f.by === name && f.kind === 'select' && ui.form[other] && !choicesNow(f).some((c) => c.value === ui.form[other])) delete ui.form[other];
    }
  }

  // the bar under a result: drag it to make the result taller or shorter (kept when the page is drawn again)
  document.body.addEventListener('mousedown', (event) => {
    const grip = event.target.closest && event.target.closest('[data-grip]');
    if (!grip) return;
    const target = grip.dataset.grip;
    const body = document.querySelector(`[data-body="${CSS.escape(target)}"]`);
    if (!body) return;
    event.preventDefault();
    const startY = event.clientY;
    const startHeight = body.getBoundingClientRect().height;
    const move = (e) => {
      const height = Math.max(80, Math.round(startHeight + e.clientY - startY));
      body.style.height = height + 'px';
      body.style.maxHeight = 'none';
      ui.resultHeight[target] = height;
    };
    const up = () => {
      window.removeEventListener('mousemove', move);
      window.removeEventListener('mouseup', up);
      render();
    };
    window.addEventListener('mousemove', move);
    window.addEventListener('mouseup', up);
  });

  document.body.addEventListener('toggle', (event) => {
    const e = event.target;
    if (e.dataset && e.dataset.smartKey) ui.smartExpanded = e.open ? e.dataset.smartKey : '';
    if (e.dataset && e.dataset.formSection) ui.formSections[e.dataset.formSection] = e.open;
  }, true);

  document.body.addEventListener('change', (event) => {
    const e = event.target;
    switch (e.dataset.change) {
      case 'language': post({ type: 'language', mode: e.value }); return;
      case 'severity': ui.severity[e.dataset.key] = e.value; return;
      case 'form':
        ui.form[e.dataset.name] = e.value;
        if (ui.command === 'init' && e.dataset.name === 'template') post({ type: 'catalog', template: e.value });
        askPreview(0);
        render();
        return;
      case 'addUse':
        if (e.value && !((ui.form.uses || '').split(',').map((x) => x.trim()).includes(e.value)))
          ui.form.uses = [...(ui.form.uses || '').split(',').map((x) => x.trim()).filter(Boolean), e.value].join(',');
        askPreview(0); render(); return;
      case 'smartField': {
        const rows = ui.smartRows[e.dataset.key] || [];
        const row = rows[Number(e.dataset.row)];
        if (row && ['name','type','nullable','personal','note'].includes(e.dataset.name)) row[e.dataset.name] = e.value;
        ui.smartExpanded = e.dataset.key; render(); return;
      }
      case 'formBool': ui.form[e.dataset.name] = e.checked ? 'true' : ''; askPreview(0); refreshCommandStepIndicators(); return;
      case 'choice': choose(e.dataset.name, e.value); askPreview(0); render(); return;
      case 'tplAnswer': ui.tplAnswers[e.dataset.id] = e.value; render(); return;
      case 'tplMulti': {
        const q = list(baseTemplate(), 'questions').find((x) => str(x, 'id') === e.dataset.id);
        const current = (e.dataset.id in ui.tplAnswers ? ui.tplAnswers[e.dataset.id] : str(q, 'default')).split(',').map((x) => x.trim()).filter((x) => x);
        const set = new Set(current);
        if (e.checked) set.add(e.dataset.choice); else set.delete(e.dataset.choice);
        ui.tplAnswers[e.dataset.id] = [...set].join(',');
        render();
        return;
      }
      case 'initTemplate':
        ui.initTemplate = e.value;
        ui.form.template = e.value;
        ui.initAnswers = {};
        ui.initTouched = {};
        render();
        return;
      case 'initAnswer': ui.initAnswers[e.dataset.id] = e.value; ui.initTouched[e.dataset.id] = true; refreshCommandStepIndicators(); return;
      case 'initMulti': {
        const template = templates().find((x) => str(x, 'name') === ui.initTemplate);
        const q = template && list(template, 'questions').find((x) => str(x, 'id') === e.dataset.id);
        const current = (e.dataset.id in ui.initAnswers ? ui.initAnswers[e.dataset.id] : str(q, 'default')).split(',').map((x) => x.trim()).filter((x) => x);
        const set = new Set(current);
        if (e.checked) set.add(e.dataset.choice); else set.delete(e.dataset.choice);
        ui.initAnswers[e.dataset.id] = [...set].join(',');
        ui.initTouched[e.dataset.id] = true;
        return;
      }
      case 'advanced': ui.advanced = e.checked; render(); return;
      case 'skill': ui.skillValues[e.dataset.name] = e.value; render(); return;
      default:
    }
  });

  // template field typed by hand (init): reload the catalog for a template folder
  document.body.addEventListener('focusout', (event) => {
    const e = event.target;
    if (ui.command === 'init' && e.dataset && e.dataset.input === 'form' && e.dataset.name === 'template') {
      post({ type: 'catalog', template: e.value });
    }
  });

  render();
  post({ type: 'ready' });
}());
