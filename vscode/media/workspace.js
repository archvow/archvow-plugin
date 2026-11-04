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
  let I = { commands: [], catalog: null, knownVersion: '', rules: { en: '', fr: '' }, types: {}, forms: {} };
  /** What the person chose on the page. */
  const ui = {
    tab: 'overview', overSub: 'architecture', forceWorkspace: false,
    /** The editor tab: 'empty', 'component', 'checks', 'project', 'command' or 'result'. */
    mode: 'empty', resultLabel: '', title: '',
    component: '', view: 0, filter: 'all',
    sel: { decision: '', question: '', safety: '', assumption: '', data: '', other: -1 },
    otherList: 0, drafts: {}, severity: {}, changing: {}, suggested: {}, results: {}, raw: {}, confirm: null,
    /** The Spec view: the card (spec.md) or the vow (spec.vow), and the cards read. */
    specPart: 'card', cards: {},
    /** The editor tab: the component (and its view) a command was opened from, for Back; null without. */
    back: null, backArmed: null,
    command: NAV ? '' : 'check', commandFilter: '', commandGroupsOpen: {}, formSections: {}, form: {}, formHelp: '',
    /** The form's live preview (design A): the command line, or what is still missing. */
    preview: { text: '', missing: '' },
    catalog: null, catalogSource: '', initTemplate: '', initAnswers: {}, initTouched: {}, advanced: false,
    /** Edit spec: the values the form opened with (only the changed ones are sent); template new: its settings. */
    formBaseline: {}, tplAnswers: {}, tplLayers: {}, smartRows: {}, smartQuestions: {},
    /** The comparison base: the extension's best one (an existing branch) until the person types another. */
    base: '', baseTouched: false, checkSel: 0, explained: {},
    /** A result's height, once dragged or expanded. */
    resultHeight: {},
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
    }
    vscode.postMessage(m);
  };
  const btn = (label, action, extra = '', disabled = false, cls = '') =>
    `<button class="${cls}" data-a="${action}" ${extra} ${disabled ? 'disabled' : ''}>${esc(label)}</button>`;
  /** A circle turning clockwise, open at the top right, its arrowhead at the top: Reload (the IntelliJ plugin draws the same). */
  const REFRESH = '<svg class="glyph" viewBox="0 0 14 14" aria-hidden="true"><path d="M11.70 5.29 A5 5 0 1 1 7.87 2.08" fill="none" '
    + 'stroke="currentColor" stroke-width="1.55" stroke-linecap="round"/><path d="M10.23 2.50 L7.63 0.01 L6.93 3.95 Z" fill="currentColor"/></svg>';
  const iconBtn = (svg, action, title) => `<button class="icon" data-a="${action}" title="${esc(title)}" aria-label="${esc(title)}">${svg}</button>`;
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
        I = { commands: m.commands || [], catalog: m.catalog, knownVersion: m.knownVersion, rules: m.rules || { en: '', fr: '' }, types: m.types || {}, forms: m.forms || {} };
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
      case 'openCommand':
        ui.forceWorkspace = true;
        ui.back = ui.backArmed;
        ui.backArmed = null;
        ui.mode = 'command';
        setCommand(m.command, m.values || {}, m.help || '');
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
        ui.preview = { text: m.text || '', missing: m.missing || '' };
        const pre = document.getElementById('form-preview');
        if (pre) { pre.textContent = ui.preview.text || ui.preview.missing; pre.classList.toggle('missing', !ui.preview.text); }
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

  function render() {
    const app = document.getElementById('app');
    const active = document.activeElement;
    const focus = active && active.dataset && active.dataset.keep ? { key: active.dataset.keep, start: active.selectionStart, end: active.selectionEnd } : null;
    const scrolls = {};
    document.querySelectorAll('[data-scroll]').forEach((e) => { scrolls[e.dataset.scroll] = e.scrollTop; });
    app.innerHTML = page();
    if (!NAV) {
      const title = editorTitle();
      if (title !== ui.title) {
        ui.title = title;
        post({ type: 'title', text: title });
      }
    }
    document.querySelectorAll('[data-scroll]').forEach((e) => { if (scrolls[e.dataset.scroll]) e.scrollTop = scrolls[e.dataset.scroll]; });
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
    if (ui.mode === 'commandDocs') return 'archvow: ' + t('Command reference', 'Docs des commandes');
    if (ui.mode === 'project') return 'archvow: ' + t('Project items', 'Éléments du projet');
    if (ui.mode === 'command') return 'archvow: ' + ui.command;
    if (ui.mode === 'result') return 'archvow: ' + (ui.resultLabel || t('Result', 'Résultat'));
    return 'archvow';
  }

  function header() {
    const reload = iconBtn(REFRESH, 'refresh', t('Reload Archvow', 'Recharger Archvow'));
    const log = btn('☷', 'log', `title="${esc(t('Open Archvow session log', 'Ouvrir le journal Archvow'))}"`, false, 'icon');
    const progress = S.loading ? `<div class="progress">⏳ ${esc(S.loading)}</div>` : '';
    if (NAV) {
      return `<header class="top">
  <div class="brand"><img src="${esc(window.ARCHVOW_LOGO)}" alt="" class="logo">
    <div><div class="title">Archvow</div><div class="project" title="${esc(S.project.root)}">⌂ ${esc(S.project.name)}</div></div></div>
  <div class="right">${reload}${log}</div></header>${progress}`;
    }
    const what = ui.mode === 'component' ? ui.component
      : ui.mode === 'checks' ? t('Check & Gates', 'Contrôles et validations')
        : ui.mode === 'commandDocs' ? t('Command reference', 'Docs des commandes')
        : ui.mode === 'project' ? t('Project items', 'Éléments du projet')
        : ui.mode === 'command' ? 'archvow ' + ui.command : ui.mode === 'result' ? ui.resultLabel : '';
    const kind = ui.mode === 'component' ? t('Component', 'Composant') : ui.mode === 'command' ? t('Command', 'Commande')
      : ui.mode === 'commandDocs' ? t('Reference', 'Référence')
      : ui.mode === 'checks' ? t('Checks', 'Contrôles') : ui.mode === 'project' ? t('Project', 'Projet') : ui.mode === 'result' ? t('Result', 'Résultat') : '';
    // a component's name is the heading of its page, with its open items (componentDetail)
    const named = ui.mode === 'component' && ui.view !== 7 && ui.component && snapshot();
    const back = ui.back && ui.mode !== 'component' ? `<button class="secondary back" data-a="back" title="${esc(t('Back to ', 'Retour à ') + ui.back.component)}">← ${esc(ui.back.component)}</button>` : '';
    return `<header class="top ${named ? 'slim' : ''}">
  <div class="brand">${back}<img src="${esc(window.ARCHVOW_LOGO)}" alt="" class="logo">
    <div><div class="kind">${esc(kind ? 'Archvow · ' + kind : 'Archvow')}</div>${named ? '' : `<div class="title">${esc(what || t('Specifications → understandable components', 'Des specs aux composants compréhensibles'))}</div>`}
    <div class="project" title="${esc(S.project.root)}">⌂ ${esc(S.project.name)}</div></div></div>
  <div class="right">${reload}${log}</div></header>${progress}`;
  }

  /** The editor tab: the component, the command or the result chosen in the sidebar. */
  function editorBody() {
    if (ui.mode === 'component') return `<section class="detail-only">${componentDetail()}</section>`;
    if (ui.mode === 'checks') return `<section class="detail-only checks-editor">${checksView(true)}</section>`;
    if (ui.mode === 'commandDocs') return `<section class="detail-only">${commandReference()}</section>`;
    if (ui.mode === 'project') return `<section class="detail-only"><div class="card"><h3>${esc(t('Project items', 'Éléments du projet'))}</h3>${otherView()}</div></section>`;
    if (ui.mode === 'command') {
      const command = commandByName(ui.command);
      return `<section class="detail-only">${command ? commandForm(command) : `<p class="muted">${esc(t('Select a command in the Archvow sidebar.', 'Sélectionne une commande dans le panneau Archvow.'))}</p>`}</section>`;
    }
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
      ? `<div class="command">${esc(i.command)}</div><p class="muted small">${esc(t('From', 'Depuis'))}: ${esc(i.source === "pip's configuration" ? t("pip's configuration", 'la configuration de pip') : i.source)} · ${esc(t('setting archvow.installFrom', 'réglage archvow.installFrom'))}</p>`
      : `<p class="tone-warn">${esc(t('No Python found for this project (.venv, venv, the Python extension\'s interpreter, archvow.python): choose one with Configure Python.',
        "Aucun Python trouvé pour ce projet (.venv, venv, l'interpréteur de l'extension Python, archvow.python) : choisis-en un avec Configurer Python."))}</p>`;
    return `<div class="card install"><p><b>${esc(why)}</b></p>${where}
  <div class="row">${btn(t(`Install archvow ${i.version}`, `Installer archvow ${i.version}`), 'install', '', !i.python)}</div></div>`;
  }

  /** The sidebar: Overview, Components (the tree), Commands (the list). */
  function tabs() {
    const names = [['overview', t('Overview', "Vue d'ensemble")], ['components', t('Components', 'Composants')], ['commands', t('Commands', 'Commandes')]];
    const body = ui.tab === 'components' ? `<section class="browser-only">${componentBrowser()}</section>` : ui.tab === 'commands' ? commandsTab() : overviewTab();
    return `<nav class="tabs main">${names.map(([id, l]) => `<button class="tab ${ui.tab === id ? 'current' : ''}" data-a="tab" data-tab="${id}">${esc(l)}</button>`).join('')}</nav>
<main>${body}</main>`;
  }

  // ---------------------------------------------------------------- shared: result card, confirmation

  function resultCard(target) {
    const r = ui.results[target];
    if (!r) return '';
    const raw = !!ui.raw[target];
    const body = r.running ? `<p><b>⏳ ${esc(t('Running', 'Exécution'))}</b> · ${esc(r.args)}</p>`
      : raw ? `<pre class="raw">${esc(r.raw)}</pre>` : r.html;
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
    return `<div class="confirm"><div class="confirm-title">⚠ ${esc(t('Confirm', 'Confirmer'))}: ${esc(c.title)}</div>
  <pre>${esc(c.summary)}</pre><div class="command">${esc(c.command)}</div>
  <div class="row end">${btn(t('Cancel', 'Annuler'), 'cancelConfirm', '', false, 'secondary')}${btn(t('Confirm and run', 'Confirmer et exécuter'), 'confirm')}</div></div>`;
  }

  // ---------------------------------------------------------------- Overview

  /** The project's briefs folders (init answer `briefs`): where --from may draw from. */
  function briefFolders() {
    const b = S && S.briefs;
    if (!b || !S.spec) return '';
    const names = b.folders.length ? b.folders.map((f) => f + '/').join(', ') : t('none declared', 'aucun déclaré');
    return `<p class="brief-folders muted small" title="${esc(t('An architect changes them: archvow init <package> --force -a briefs=...', 'Un architecte les change : archvow init <package> --force -a briefs=...'))}">${esc(t('Brief folders (--from)', 'Dossiers de briefs (--from)'))}: ${esc(names)} · ${b.files.length} ${esc(b.files.length === 1 ? t('file', 'fichier') : t('files', 'fichiers'))}</p>`;
  }

  function overviewTab() {
    const work = S.work;
    const branchWarning = needsFeatureBranch(S.branch) ? `<div class="warning" title="${esc(t('Advice only. Archvow does not automatically change Git branches.',
      'Conseil uniquement. Archvow ne change pas automatiquement de branche Git.'))}">${esc(t(`⚠ You are on ${S.branch}. For development, create a feature branch (e.g. feature/my-change) first.`,
      `⚠ Branche ${S.branch} : crée une branche de fonctionnalité (ex. feature/ma-modification) avant de développer.`))}</div>` : '';
    const toolbar = [
      iconBtn(REFRESH, 'refresh', t('Reload Archvow', 'Recharger Archvow')),
      btn('Check', 'run', attrs({ label: 'check', args: 'check --json', target: 'overview' })),
      btn('Next', 'run', attrs({ label: 'next', args: 'next --json', target: 'overview' })),
      btn('Full check', 'run', attrs({ label: 'full check', args: 'check --complete --json', target: 'overview' })),
      btn('Doctor', 'run', attrs({ label: 'doctor', args: 'doctor --json', target: 'overview' })),
      btn('Audit', 'run', attrs({ label: 'audit', args: 'audit --json', target: 'overview' })),
      btn(t('Project vow', 'Vow du projet'), 'openProjectVow'),
      btn('Budget / Next', 'budget'),
      btn('Diff', 'openCommand', attrs({ command: 'diff', field: 'base' })),
      btn('Review', 'openCommand', attrs({ command: 'review', field: 'base' })),
      btn(t('Command reference', 'Docs des commandes'), 'commandDocs', '', false, 'secondary'),
      `<label class="base" title="${esc(t('Git comparison base for Review / Diff / Budget: a branch of this repository', 'Base de comparaison Git pour Review / Diff / Budget : une branche de ce dépôt'))}">Base: <input list="refs" data-input="base" data-keep="base" value="${esc(baseNow())}" size="14" placeholder="${esc(t('no other branch', "pas d'autre branche"))}"><datalist id="refs">${(S.refs || []).map((r) => `<option value="${esc(r)}">`).join('')}</datalist></label>`,
    ].join('');
    const sub = ui.overSub === 'checks' ? checksView(false) : architectureView();
    const issues = list(S.check, 'violations');
    const failedChecks = issues.filter((v) => str(v, 'severity').toLowerCase() === 'error').length;
    const warnedChecks = issues.filter((v) => str(v, 'severity').toLowerCase() === 'warning').length;
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
  ${branchWarning}${briefFolders()}
  ${S.install && S.install.reason === 'version' ? installCard() + confirmation('welcome') + resultCard('welcome') : ''}
  <div class="toolbar"><div class="row wrap">${toolbar}</div><span class="muted" title="${esc(t('Current Git branch', 'Branche Git courante'))}">Git: ${esc(S.branch || '—')}</span></div>
  <nav class="tabs sub">${[['architecture', 'Architecture'], ['checks', t('Check & Gates', 'Contrôles et validations')]]
    .map(([id, l]) => `<button class="tab ${ui.overSub === id ? 'current' : ''}" data-a="overSub" data-sub="${id}">${esc(l)}${` <span class="count${id === 'checks' ? ' check-count' : ''}">${id === 'architecture' ? architecturePending : checksPending}</span>`}</button>`).join('')}</nav>
  ${issues.length ? `<div class="checks-badges" aria-label="${esc(t('Project checks', 'Contrôles du projet'))}"><span class="chip ${failedChecks ? 'bad' : 'good'}">${failedChecks} ${esc(t('errors', 'erreurs'))}</span><span class="chip ${warnedChecks ? 'warn' : 'good'}">${warnedChecks} ${esc(t('warnings', 'avertissements'))}</span></div>` : ''}
  ${sub}
  <footer class="hint"><label>${esc(t('Language', 'Langue'))} <select data-change="language">${[['auto', t('Follow VS Code', 'Suivre VS Code')], ['en', 'English'], ['fr', 'Français']]
    .map(([v, l]) => `<option value="${v}" ${S.languageMode === v ? 'selected' : ''}>${esc(l)}</option>`).join('')}</select></label>
    <span class="row wrap">${btn(t('Open settings', 'Ouvrir les paramètres'), 'settings', '', false, 'secondary')}
    ${btn(t('Test Archvow (--version)', 'Tester Archvow (--version)'), 'run', attrs({ label: 'version', args: '--version', target: 'overview' }), false, 'secondary')}</span></footer>
</section>`;
  }

  function architectureView() {
    if (!S.spec) {
      return `<div class="card"><h3>${esc(t('Architecture unavailable', 'Architecture indisponible'))}</h3><p>${esc(S.welcomeError || t('Loading architecture…', "Chargement de l'architecture…"))}</p></div>`;
    }
    const name = str(S.spec.system, 'name') || t('Project architecture', 'Architecture du projet');
    // the same table as IntelliJ's: Component, State and Pending always shown; Type, Layer and Container
    // while the sidebar has room (workspace.css hides Container first, then Layer, then Type), the whole
    // row in its tooltip; narrower than the three, the table scrolls sideways
    const heads = [['c-id', t('Component', 'Composant')], ['c-type', 'Type'], ['c-layer', t('Layer', 'Couche')],
      ['c-container', t('Container', 'Conteneur')], ['c-state', t('State', 'État')], ['c-pending', t('Pending', 'En attente')]];
    const rows = S.rows.map((r) => `<tr data-a="openComponentRow" data-id="${esc(r.id)}" class="${r.pending ? 'pending' : 'done'} ${r.id === ui.component ? 'selected' : ''}"
  title="${esc([r.id, r.type, r.layer, r.container, localizedState(r.state)].filter((x) => x).join(' · ') + (r.state === 'drift' ? ' — ' + t('The code differs from its vow', 'Le code diffère de sa vow') : ''))}">
  <td class="c-id"><div class="cut"><b>${esc(r.id)}</b></div></td><td class="c-type">${esc(r.type)}</td><td class="c-layer">${esc(r.layer)}</td><td class="c-container">${esc(r.container)}</td>
  <td class="c-state ${stateTone(r.state)}">${esc(localizedState(r.state))}</td><td class="c-pending">${r.pending ? `<span class="count">${r.pending}</span>` : '<span class="ok">✓</span>'}</td></tr>`).join('');
    return `<div class="card"><h3>${esc(name)}</h3>
  <p class="muted">${S.rows.length} ${esc(t('components', 'composants'))} · ${S.work.pending.length} ${esc(t('pending', 'en attente'))} · ${S.work.resolved.length} ${esc(t('resolved', 'traités'))}</p>
  ${S.rows.length ? `<div class="arch-wrap" data-scroll="architecture"><table class="arch"><thead><tr>${heads.map(([c, l]) => `<th class="${c}">${esc(l)}</th>`).join('')}</tr></thead>
  <tbody>${rows}</tbody></table></div>
  <p class="muted">${esc(t('Click a component to open it in the editor.', "Clique sur un composant pour l'ouvrir dans l'éditeur."))}</p>`
    : `<p>${esc(t('No components declared in the specification. Open Components or create one.', 'Aucun composant déclaré dans la spécification. Ouvre Composants ou crée-en un.'))}</p>`}</div>`;
  }

  /** The colour of a state, as in IntelliJ's table: done green, drift or missing amber. */
  function stateTone(raw) {
    const key = (raw || '').toLowerCase();
    if (['implemented', 'verified', 'tested'].includes(key)) return 'tone-good';
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

  /** The sidebar owns the diagnostics list; the editor shows only the selected detail.
   *  Do not repeat the list in the editor where it would waste half the width. */
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
    const title = `<div class="card-head"><h3>${esc(t('Check & Gates', 'Contrôles et validations'))}</h3>${btn(t('Check reference', 'Référence des contrôles'), 'openChecks', '', false, 'secondary')}</div>`;
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
    return `<div class="card checks-full">${title}${totals}<div class="check-editor-detail">${detail}</div></div>`;
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
    let tree = '';
    for (const c of containers) {
      const layers = new Map();
      for (const comp of list(c, 'components')) {
        const id = str(comp, 'id');
        if (!show(id)) continue;
        const layer = str(comp, 'layer');
        if (!layers.has(layer)) layers.set(layer, []);
        layers.get(layer).push(comp);
      }
      tree += `<li class="container">▾ ${esc(str(c, 'id'))}<ul>${[...layers.entries()].map(([layer, comps]) => `<li class="layer">▾ ${esc(layer)}<ul>${comps.map((comp) => {
        const id = str(comp, 'id');
        const p = pending[id] || 0;
        return `<li class="component ${id === ui.component ? 'selected' : ''} ${p ? 'pending' : 'done'}" data-a="pick" data-id="${esc(id)}">${esc(id)} <span class="muted">· ${esc(str(comp, 'type'))}</span>${aiBadge(comp)} ${p ? `<span class="count">${p}</span>` : '<span class="ok">✓</span>'}</li>`;
      }).join('')}</ul></li>`).join('')}</ul></li>`;
    }
    return `<div class="row">${filters.map(([id, l]) => btn(l, 'filter', attrs({ filter: id }) + ` title="${esc(ui.filter === id ? t('Current filter', 'Filtre actif') : t('Filter components', 'Filtrer les composants'))}"`, ui.filter === id, 'secondary small')).join('')}</div>
  <ul class="tree" data-scroll="tree">${tree || `<li class="muted">${esc(S.spec ? t('No component.', 'Aucun composant.') : t('Loading…', 'Chargement…'))}</li>`}</ul>`;
  }

  const VIEWS = () => [t('Decisions', 'Décisions'), 'Questions', t('Safety', 'Sécurité'), t('Assumptions', 'Hypothèses'), t('Rules', 'Règles'), t('Tasks', 'Tâches'), 'Spec', t('Other', 'Autres')];

  function componentDetail() {
    const actions = [
      btn(t('Clarify', 'Clarifier'), 'clarify', `title="${esc(t('Answer what is still open for this component: its next question, decision or safety question.', 'Répondre à ce qui reste ouvert pour ce composant : sa prochaine question, décision ou question de sécurité.'))}"`),
      btn(t('Open code', 'Ouvrir le code'), 'openCode', `title="${esc(t("Open this component's Python file (and its test). Change the code there; save, then Check.", 'Ouvrir le fichier Python de ce composant (et son test). Modifier le code là ; enregistrer, puis Vérifier.'))}"`, false, 'secondary'),
      btn(t('Edit spec', 'Modifier la spec'), 'openCommand', attrs({ command: 'component edit', component: ui.component }) + ` title="${esc(t('Change the description, ticket, signature, uses, group, owners or brief (archvow component edit). Decisions, questions and hazards stay as they are: review the ones the change touches.', 'Changer la description, le ticket, la signature, les dépendances, le groupe, les responsables ou la brief (archvow component edit). Les décisions, questions et hazards restent tels quels : revoir ceux que le changement touche.'))}"`, false, 'secondary'),
      btn(t('AI prompt', 'Instruction IA'), 'aiPrompt', `title="${esc(t("Copy the instruction to give the AI for this component's next task (archvow prompt).", "Copier l'instruction à donner à l'IA pour la prochaine tâche de ce composant (archvow prompt)."))}"`, false, 'secondary'),
      btn(t('+ Question', '+ Question'), 'openCommand', attrs({ command: 'question add', component: ui.component })
        + ` title="${esc(t('Add a question to this component', 'Ajouter une question à ce composant'))}"`, false, 'secondary'),
      btn(t('+ Rule', '+ Règle'), 'addRule', `title="${esc(t('Add a rule on this component', 'Ajouter une règle sur ce composant'))}"`, false, 'secondary'),
      btn(t('+ Component', '+ Composant'), 'openCommand', attrs({ command: 'component add' })
        + ` title="${esc(t('Add a component', 'Ajouter un composant'))}"`, false, 'secondary'),
    ];
    const view = snapshot();
    const d = derived();
    const counts = d ? d.counts : { pending: [0, 0, 0, 0, 0, 0], total: [0, 0, 0, 0, 0, 0] };
    const names = VIEWS();
    const localOtherPending = S.work.otherPending.filter((item) => isLocalOther(item)).length;
    const localOtherResolved = S.work.otherResolved.filter((item) => isLocalOther(item)).length;
    const viewBar = names.map((name, i) => {
      const label = i < 6 ? name + (counts.pending[i] > 0 ? ' · ' + counts.pending[i] : counts.total[i] > 0 ? ' ✓' : '')
        : i === 7 ? name + (localOtherPending ? ' · ' + localOtherPending : localOtherResolved ? ' ✓' : '') : name;
      const tip = i < 6 ? (counts.pending[i] > 0 ? `${counts.pending[i]} ${t('pending items', 'éléments en attente')}` : t('No open items', 'Aucun élément ouvert'))
        : i === 7 ? t('Only issues of this component; project checks are in Check & Gates.', 'Seulement les éléments de ce composant ; les contrôles globaux sont dans Check & Gates.') : '';
      return `<button class="tab ${ui.view === i ? 'current' : ''} ${(i < 6 && counts.pending[i] > 0) || (i === 7 && localOtherPending > 0) ? 'has-pending' : ''}" data-a="view" data-i="${i}" title="${esc(tip)}">${esc(label)}</button>`;
    }).join('');
    let body;
    if (ui.view === 7) body = otherView();
    else if (!ui.component) body = `<p class="muted">${esc(t('Select a component in the Archvow sidebar.', 'Sélectionne un composant dans le panneau Archvow.'))}</p>`;
    else if (!view) body = `<p class="muted">${esc(t('Loading', 'Chargement'))}: ${esc(ui.component)}…</p>`;
    else body = [decisionsView, questionsView, safetyView, assumptionsView, rulesView, tasksView, vowView][ui.view](view, d);
    // A component's 'Others' belongs to this component; global diagnostics are in Check & Gates.
    const head = view ? `<div class="component-head"><h2>${esc(ui.component)}${openBadge(ui.component)}${aiBadge(view)}</h2>${createdByAi(view)}<p class="meta muted">${esc(meta(view))}</p>${progressBar(view)}
  <p class="description">${esc(str(view, 'description'))}</p>${drawnFrom(view)}${links(view)}${examples(view)}${list(view, 'missing').length || (Array.isArray(view.missing) && view.missing.length)
    ? `<p class="tone-warn">${esc(t('Missing', 'Manquant'))}: ${esc(view.missing.map((x) => (typeof x === 'object' ? JSON.stringify(x) : x)).join(', '))}</p>` : ''}</div>` : '';
    // the head, the actions and the view chips over the whole width; under them, for the views whose
    // items are answered (decisions, questions, safety, assumptions), the items left and the answer right
    return `${head}<div class="row wrap actions">${actions.join('')}</div>
  <nav class="tabs views">${viewBar}</nav>${pendingExplanation()}
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
    return `<ul class="cards" data-scroll="cards-${kind}">${rows.map((row, i) => {
      const key = label(row, i);
      const c = card(row, i);
      return `<li class="item-card ${ui.sel[kind] === key ? 'selected' : ''} ${c.open ? 'open' : 'done'}" data-a="sel" data-kind="${kind}" data-key="${esc(key)}">
  <div class="card-q" title="${esc(c.title)}"><b>${esc(c.number)}</b> · ${esc(c.title)}</div>
  ${c.open ? `<div class="card-open">● ${esc(c.state || t('Open', 'Ouvert'))}</div>${c.note ? `<div class="card-note">${esc(short(c.note, 160))}</div>` : ''}`
    : `<div class="card-a" title="${esc(c.answer || '')}">${esc(short(c.answer || c.state || t('Resolved', 'Traité'), 220))}</div>`}
  ${c.by ? `<div class="card-by muted">${esc(c.by)}</div>` : ''}</li>`;
    }).join('') || `<li class="muted empty">${esc(t('Nothing to display.', 'Rien à afficher.'))}</li>`}</ul>`;
  }

  /** "architect@example.com · 2026-10-08": who recorded an answer, and the day. */
  const byOn = (row) => [str(row, 'by'), str(row, 'on').substring(0, 10)].filter((x) => x).join(' · ');

  /** The answer panel's heading: "ANSWER DECISION 3", "DECISION 1 · ANSWERED". */
  function answerHead(text, state, good) {
    return `<div class="answer-head"><span>${esc(text)}</span>${state ? ` <b class="${good ? 'tone-good' : 'tone-warn'}">${esc(state)}</b>` : ''}</div>`;
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

  /** The component's business examples (show --json `examples`): ✓ when its test is marked and its files
   *  are the recorded ones, else what is missing (ARCH056, ARCH055). As the IntelliJ details show them. */
  function examples(view) {
    const rows = list(view, 'examples');
    if (!rows.length) return '';
    const item = (e) => {
      const ok = e.tested === true && e.unchanged !== false;
      const why = e.unchanged === false ? t('files changed since recorded (ARCH055)', 'fichiers modifiés depuis leur enregistrement (ARCH055)')
        : e.tested === true ? t('tested', 'testé') : t('no marked test yet (ARCH056)', 'pas encore de test marqué (ARCH056)');
      const tip = `${str(e, 'folder')}/ · ${str(e, 'level')} · ${why}`;
      return `<span class="${ok ? 'ok' : 'tone-warn'}" title="${esc(tip)}">${ok ? '✓' : '✖'} ${esc(str(e, 'name'))}${str(e, 'ticket') ? ` (${esc(str(e, 'ticket'))})` : ''}</span>`;
    };
    return `<p class="examples small">${esc(t('Examples', 'Exemples'))}: ${rows.map(item).join('  ·  ')}</p>`;
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
      part('examples', t('Examples tested', 'Exemples testés'), list(view, 'examples'), (e) => e.tested === true && e.unchanged !== false),
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
  <span class="row">${btn(t('Undo changes', 'Annuler les modifications'), 'cancelDraft', attrs({ key, target }) + ` title="${esc(undoTip(edited))}"`, disabled || !edited, 'secondary')}${save}</span></div>`;
  }

  function undoTip(edited) {
    return edited ? t('Put back the recorded text in the field (nothing is saved or removed)', 'Remettre le texte enregistré dans le champ (rien n\'est enregistré ni supprimé)')
      : t('Nothing to undo: the field holds the recorded text', 'Rien à annuler : le champ contient le texte enregistré');
  }

  /** Optional structured editor: relevant only when the question is about column properties.
   * It never changes the CLI schema, guesses no nullability, and does not save on its own. */
  function canEditColumns(question) {
    const q = String(question || '').toLocaleLowerCase();
    const keywords = (((I.forms || {}).smart_answers || {}).schema_columns || {}).keywords || [];
    const aboutColumn = /\bcolumns?|\bfields?|colonnes?|champs?/i.test(q);
    const aboutProperty = /(nullab|not null|required|optional|person|privat|gdpr|rgpd|obligatoire|facultati|manquant|missing|types?|datatype)/i.test(q);
    return aboutColumn && aboutProperty && keywords.some((k) => q.includes(k.toLocaleLowerCase()));
  }

  function knownColumnRows(view) {
    const names = new Map();
    for (const group of list(view, 'known_columns')) {
      for (const name of Array.isArray(group.columns) ? group.columns : [])
        if (typeof name === 'string' && name.trim()) names.set(name.trim(), { name: name.trim(), type: '', nullable: 'unknown', personal: 'unknown' });
    }
    for (const c of list(view, 'columns')) {
      const name = str(c, 'name');
      if (!name) continue;
      const kind = str(c, 'class');
      const base = names.get(name) || { name, type: '', nullable: 'unknown', personal: 'unknown' };
      const personal = ['personal', 'special'].includes(kind) ? 'yes' : kind === 'none' ? 'no' : 'unknown';
      names.set(name, { ...base, type: str(c, 'type') || base.type, personal });
    }
    return Array.from(names.values()).slice(0, 60);
  }

  function structuredAnswer(rows, question = '') {
    const q = question.toLocaleLowerCase();
    const withNull = /(null|requir|option|obligat|facultat)/.test(q);
    const withPersonal = /(person|privat|gdpr|rgpd)/.test(q);
    const withType = /(\btypes?\b|datatype|data type)/.test(q);
    return rows.filter((r) => r.name.trim()).map((r) => {
      const parts = [];
      if (withType) parts.push(r.type.trim() || t('type unknown', 'type inconnu'));
      if (withNull) parts.push(r.nullable === 'yes' ? 'nullable' : r.nullable === 'no' ? t('not null', 'non nullable') : t('nullability unknown', 'nullabilité inconnue'));
      if (withPersonal) parts.push(r.personal === 'yes' ? t('personal data', 'donnée personnelle') : r.personal === 'no' ? t('not personal data', 'non personnel') : t('personal-data classification unknown', 'classification des données inconnue'));
      return r.name.trim() + ': ' + parts.join(', ');
    }).join('\n');
  }

  function smartColumns(view, row, key) {
    if (!canEditColumns(str(row, 'question'))) return '';
    if (!ui.smartRows[key]) ui.smartRows[key] = knownColumnRows(view).map((x) => ({ ...x }));
    const rows = ui.smartRows[key];
    ui.smartQuestions[key] = str(row, 'question');
    const answerText = () => structuredAnswer(rows, ui.smartQuestions[key]);
    const select = (name, idx, value, opts) => `<select data-change="smartField" data-key="${esc(key)}" data-row="${idx}" data-name="${name}">${opts.map(([id, label]) => `<option value="${id}" ${value === id ? 'selected' : ''}>${esc(label)}</option>`).join('')}</select>`;
    const yesNo = [['unknown', t('Unknown', 'Inconnu')], ['no', t('No', 'Non')], ['yes', t('Yes', 'Oui')]];
    return `<details class="card smart-answer"${ui.smartExpanded === key ? ' open' : ''} data-smart-key="${esc(key)}"><summary>${esc(t('Structured answer assistant (optional)', 'Assistant de réponse structurée (facultatif)'))}</summary>
      <p class="muted small">${esc(t('Fill known columns or add new ones. Unknown is intentional. This is a draft only; review the text before saving.', 'Renseigne les colonnes connues ou ajoute les tiennes. Inconnu est volontaire. Il s’agit d’un brouillon à relire avant enregistrement.'))}</p>
      <datalist id="archvow-type-suggestions">${['string','integer','float','double','decimal','boolean','date','timestamp','object'].map((type) => `<option value="${type}">`).join('')}</datalist>
      <div class="smart-table">${rows.map((r, i) => `<div class="smart-row"><label>${esc(t('Column', 'Colonne'))}<input data-input="smartField" data-key="${esc(key)}" data-row="${i}" data-name="name" value="${esc(r.name)}"></label>
        <label>${esc(t('Type', 'Type'))}<input data-input="smartField" data-key="${esc(key)}" data-row="${i}" data-name="type" list="archvow-type-suggestions" value="${esc(r.type)}" placeholder="${esc(t('Unknown', 'Inconnu'))}"></label>
        <label>${esc(t('Nullable', 'Nullable'))}${select('nullable', i, r.nullable, yesNo)}</label>
        <label>${esc(t('Personal', 'Personnel'))}${select('personal', i, r.personal, yesNo)}</label>
        ${btn('×', 'smartRemove', attrs({ key, row: i }), false, 'secondary')}</div>`).join('')}</div>
      <div class="row wrap">${btn(t('Add column', 'Ajouter une colonne'), 'smartAdd', attrs({ key }), false, 'secondary')}
      ${btn(t('Use structured answer', 'Utiliser cette réponse'), 'smartUse', attrs({ key }), !answerText().trim(), 'secondary')}</div>
      <pre class="smart-preview">${esc(answerText() || t('Add a column to prepare a response.', 'Ajoute une colonne pour préparer la réponse.'))}</pre>
    </details>`;
  }

  function decisionsView(view) {
    const rows = list(view, 'decisions');
    const label = (r) => 'D' + str(r, 'number');
    const isOpen = (r) => !str(r, 'answer');
    const row = current('decision', rows, label, isOpen);
    const left = cards('decision', rows, label, (r) => ({ number: str(r, 'number'), title: str(r, 'question'), open: isOpen(r), answer: str(r, 'answer'),
      by: isOpen(r) ? '' : byOn(r), note: str(r, 'proposed') ? t('AI proposal: ', 'Proposition IA : ') + str(r, 'proposed') : '' }));
    let right = `<p class="muted">${esc(t('Select a decision.', 'Sélectionne une décision.'))}</p>`;
    if (row) {
      const number = str(row, 'number');
      const answer = str(row, 'answer');
      const proposed = str(row, 'proposed');
      const suggested = str(row, 'suggested');
      const pending = !answer;
      const key = `d:${ui.component}:${number}`;
      const secondary = [pending ? btn(t('Propose', 'Proposer'), 'decide', attrs({ action: 'decision propose' })
        + ` title="${esc(t('Record the text as a proposal, not yet the answer', 'Enregistrer le texte comme proposition, pas encore comme réponse'))}"`, false, 'secondary') : '',
      pending && proposed ? btn(t('Accept proposal', 'Accepter la proposition'), 'decide', attrs({ action: 'decision accept' }), false, 'secondary') : '',
      suggested ? btn(t('Use suggestion', 'Utiliser la suggestion'), 'useSuggestion', '', false, 'secondary') : ''].join('');
      right = answerHead(pending ? t(`Answer decision ${number}`, `Répondre à la décision ${number}`) : t(`Decision ${number}`, `Décision ${number}`),
        pending ? '' : t('ANSWERED', 'TRAITÉE'), true)
        + `<p class="answer-q">${esc(str(row, 'question'))}</p>`
        + (answer && byOn(row) ? `<p class="muted small">${esc(t('Recorded by ', 'Enregistrée par ') + byOn(row))}</p>` : '')
        + (proposed ? `<p class="hint"><b class="tone-blue">${esc(t('AI proposal', 'Proposition IA'))}</b> ${esc(proposed)}</p>` : '')
        + (suggested && pending ? `<p class="hint"><b>${esc(t('Suggested', 'Suggéré'))}</b> ${esc(suggested)}</p>` : '')
        // archvow 1.19: a question with no safe default offers answers to start from, none chosen
        + (pending && Array.isArray(row.choices) && row.choices.length ? `<p class="hint"><b>${esc(t('No default (it depends on this project) - to start from:', 'Pas de défaut (cela dépend de ce projet) - pour partir de :'))}</b></p>
  <div class="row wrap choices">${row.choices.map((c) => btn(String(c), 'useChoice', attrs({ key, value: String(c) }), false, 'secondary small')).join('')}</div>` : '')
        + smartColumns(view, row, key)
        + answerEditor(key, answer || proposed || suggested, t('Your answer or proposal', 'Votre réponse ou proposition'), secondary,
          btn(t('Save answer', 'Enregistrer la réponse'), 'decide', attrs({ action: 'decision make' })), 'decision')
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
    const left = cards('safety', rows, label, (r, i) => ({ number: label(r), title: str(r, 'question'), open: isOpen(r, i),
      state: isOpen(r, i) ? statusOf(r, i) : '', answer: recorded(r), note: isOpen(r, i) ? recorded(r) : '' }));
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
        block += `<div class="recorded"><div class="row hazard-head"><p class="grow"><b>Hazard ${esc(str(h, 'id'))} · ${esc(str(h, 'severity'))}</b><br>${esc(str(h, 'description'))}</p>`
          + `<span class="row">${btn(t('Change', 'Modifier'), 'hazardChange', attrs({ id: str(h, 'id'), text: str(h, 'description'), severity: str(h, 'severity') }) + ` title="${esc(changeTip)}"`, false, 'secondary small')}`
          + `${btn(t('Remove', 'Supprimer'), 'hazardRemove', attrs({ id: str(h, 'id') }) + ` title="${esc(t('Remove this hazard (asks first)', 'Supprimer ce hazard (demande avant)'))}"`, false, 'secondary small')}</span></div>`;
        for (const g of guards) {
          block += `<p class="tone-good">↳ <b>Invariant ${esc(str(g, 'id'))}</b> <span class="body">${esc(str(g, 'statement'))}</span>${g.tested === false ? ` <span class="muted">[${esc(t('test not verified', 'test non vérifié'))}]</span>` : ''}</p>`;
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
    return `<div class="row">${btn(t('Add rule…', 'Ajouter une règle…'), 'addRule')}${btn(t('Edit rule…', 'Modifier une règle…'), 'choose', attrs({ purpose: 'rule edit' }), false, 'secondary')}
  ${btn(t('Remove rule…', 'Supprimer une règle…'), 'choose', attrs({ purpose: 'rule remove' }), false, 'secondary')}</div>
  <div class="details"><h4>${esc(t('Applicable rules', 'Règles applicables'))}</h4>${rules.length ? rules.map((r) => {
    const scope = readable(r.scope);
    return `<p><b>${esc(str(r, 'id') || '•')}</b> ${esc(str(r, 'text'))}</p>${scope && scope !== '—' ? `<p class="muted small">${esc(t('Scope', 'Portée'))}: ${esc(scope)}</p>` : ''}`;
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
    const command = g.command ? `<dt>${esc(t('The command', 'La commande'))}</dt><dd><code>${esc(g.command.text)}</code>`
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
    ui.formHelp = help || '';
    // Edit spec: what the component has now (not what a diagnostic asks to change); only differences are sent
    ui.formBaseline = name === 'component edit' ? whatTheComponentHas((values.component || '').trim()) : {};
    ui.tplAnswers = {};
    ui.tplLayers = {};
    if (ui.confirm && ui.confirm.target === 'command') ui.confirm = null;
    ui.preview = { text: '', missing: '' };
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
    const expanded = filter || ui.commandGroupsOpen[g.id] !== false;
    return `<div class="command-group"><button class="command-group-toggle" data-a="commandGroup" data-id="${esc(g.id)}" aria-expanded="${expanded ? 'true' : 'false'}"><span>${expanded ? '▾' : '▸'} ${esc(g.label)}</span><span class="count">${g.items.length}</span></button>
    ${expanded ? `<ul class="list items command-children">${g.items.map((c) => `<li class="${c.path.join(' ') === selected ? 'selected' : ''}" data-a="command" data-name="${esc(c.path.join(' '))}" title="${esc(c.description)}"><b>${esc(c.path.join(' '))}</b><div class="muted small">${esc(short(c.description || '', 90))}</div></li>`).join('')}</ul>` : ''}</div>`;
  }).join('')}</div>
  <p class="muted small">${esc(t('Select a command to open its form in the editor.', "Choisis une commande pour ouvrir son formulaire dans l'éditeur."))}</p></section>`;
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

  function commandForm(command) {
    const name = command.path.join(' ');
    const entry = formEntry(name);
    const extra = name === 'specify' ? t('IDE mode: clarification without interactive terminal.', 'Mode IDE : clarification sans terminal interactif.')
      : name === 'rule add' ? t('One argument per line in words.', 'Un argument par ligne dans le champ words.') : '';
    let fields = '';
    let more = '';
    const moreNames = [];
    let expert = '';
    // the essential fields in the order forms.json gives them (invariant add: the hazard before the words)
    const order = Array.isArray(entry.essential) ? entry.essential : [];
    const rank = (p) => (order.includes(p.name) ? order.indexOf(p.name) : order.length);
    const params = command.parameters.map((p, i) => [p, i]).sort((a, b) => rank(a[0]) - rank(b[0]) || a[1] - b[1]).map(([p]) => p);
    for (const p of params) {
      // init: the template is chosen once, in Template questions (under the package); template new: its
      // answers and layer folders are in Settings of the base, under --extends
      if (name === 'init' && (p.name === 'answer' || p.name === 'template')) continue;
      if (name === 'template new' && (p.name === 'answer' || p.name === 'layer')) continue;
      const row = fieldRow(name, p);
      // options archvow hides from --help (the expert checks of invariant add): apart, closed
      if (p.hidden || p.help === '==SUPPRESS==') { expert += row; continue; }
      if (isEssential(name, p)) {
        fields += row;
        if (name === 'init' && p.name === 'package') fields += initWizard();
        if (name === 'template new' && p.name === 'extends') fields += templateWizard();
      } else {
        more += row;
        moreNames.push(fieldLabel(name, p.name).toLowerCase());
      }
    }
    if (expert) {
      more += `<details class="expert" data-form-section="expert"${(ui.formSections.expert ?? ['verify', 'evidence', 'rule'].some((k) => ui.form[k])) ? ' open' : ''}><summary>${esc(t('Expert checks', 'Contrôles experts'))}</summary>`
        + `<p class="muted small">${esc(t('How the invariant is proven when a test is not enough: a model checked or a proof (--verify model_check|proof --evidence <file>), or a rule that enforces it (--verify static --rule <code>). Empty: the weakest test its severity accepts.',
          "Comment l'invariant est prouvé quand un test ne suffit pas : un modèle vérifié ou une preuve (--verify model_check|proof --evidence <fichier>), ou une règle qui l'impose (--verify static --rule <code>). Vide : le test le plus faible que sa gravité accepte."))}</p>${expert}</details>`;
      moreNames.push(t('expert checks', 'contrôles experts'));
    }
    if (more) {
      const open = ui.formSections.more ?? command.parameters.some((p) => !isEssential(name, p) && (ui.form[p.name] || '').trim());
      fields += `<details class="more" data-form-section="more"${open ? ' open' : ''}><summary>${esc(t('More options', "Plus d'options"))} <span class="muted small">- ${esc(moreNames.join(', '))}</span></summary>${more}</details>`;
    }
    if (name === 'init') fields += initOtherAnswers();
    if (['decision make', 'decision propose', 'decision accept', 'question remove'].includes(name)) fields += questionPreview(name);
    const title = pair(entry.title) || command.description;
    const verb = pair(entry.verb) || t('Run command', 'Exécuter la commande');
    const does = pair(entry.does);
    const shown = ui.preview.text || ui.preview.missing;
    const pane = `<aside class="form-pane"><h4>${esc(t('What archvow will do', 'Ce que va faire archvow'))}</h4>
  ${does ? `<p>${esc(does)}</p>` : ''}<div class="muted small">${esc(t('The command', 'La commande'))}</div>
  <pre id="form-preview" class="command-preview${ui.preview.text ? '' : ' missing'}">${esc(shown || 'archvow ' + name)}</pre>
  <p class="muted small">${esc(t('If archvow refuses (a name already taken, words that protect nothing...), its message shows in red under the form, before anything is written.',
    "Si archvow refuse (un nom déjà pris, des mots qui ne protègent rien...), son message s'affiche en rouge sous le formulaire, avant que rien ne soit écrit."))}</p></aside>`;
    return `<div class="form-head"><h3>${esc(title)}</h3><div class="muted small"><code>archvow ${esc(name)}</code> - ${esc(command.description)}${extra ? ' ' + esc(extra) : ''}</div>${ui.formHelp ? `<pre class="help">${esc(ui.formHelp)}</pre>` : ''}</div>
  <div class="form-grid"><div class="form">${fields}${formAlerts()}
  <div class="row end">${btn(t('Cancel', 'Annuler'), 'cancelForm', '', false, 'secondary')}${btn(verb, 'runForm')}</div></div>${pane}</div>${confirmation('command')}${resultCard('command')}`;
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
  function initWizard() {
    const template = templates().find((x) => str(x, 'name') === ui.initTemplate);
    const custom = (ui.form.template || '').trim() && !template;
    let questions = '';
    if (template) {
      for (const q of list(template, 'questions')) {
        const id = str(q, 'id');
        if (!id) continue;
        const essential = q.ask === true;
        if (!essential && !ui.advanced) continue;
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
    return `<fieldset class="card"><legend>${esc(t('Template and its questions', 'Modèle et ses questions'))}</legend>
  <div class="field"><label>* ${esc(t('template', 'modèle'))}</label><div><div class="row wrap"><select data-change="initTemplate">${templates().map((x) => `<option ${str(x, 'name') === ui.initTemplate ? 'selected' : ''}>${esc(str(x, 'name'))}</option>`).join('')}${custom ? `<option selected value="">${esc(t('(a folder, below)', '(un dossier, ci-dessous)'))}</option>` : ''}</select>
  ${btn(t('Reload', 'Actualiser'), 'reloadCatalog', '', false, 'secondary')}</div>
  ${description ? `<div class="muted small">${esc(description)}</div>` : ''}
  <div class="muted small">${esc(t('or the folder of a company template:', "ou le dossier d'un modèle d'entreprise :"))} <input data-input="form" data-name="template" data-keep="f-template-folder" value="${esc(custom ? ui.form.template : '')}" placeholder="/path/to/templates/acme-pipeline"></div></div></div>
  ${here}<p class="muted small">${esc(status)}</p>
  <label class="check"><input type="checkbox" data-change="advanced" ${ui.advanced ? 'checked' : ''}> ${esc(t('Show advanced settings', 'Afficher les réglages avancés'))}</label>
  <div class="form">${questions}</div></fieldset>`;
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
  function templateWizard() {
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
      if (!id || (q.ask !== true && !ui.advanced)) continue;
      const value = id in ui.tplAnswers ? ui.tplAnswers[id] : str(q, 'default');
      const changed = id in ui.tplAnswers && ui.tplAnswers[id] !== str(q, 'default');
      questions += `<div class="field"><label title="${esc(str(q, 'prompt'))}">${esc(id)}${changed ? ' ✎' : ''}</label><div>${questionControl(q, value, 'tpl')}<div class="muted small">${esc(str(q, 'prompt'))}${str(q, 'default') ? esc(t(` (base: ${str(q, 'default')})`, ` (base : ${str(q, 'default')})`)) : ''}</div></div></div>`;
    }
    const layers = list(base, 'layers').map((l) => {
      const layer = str(l, 'name');
      const folder = layer in ui.tplLayers ? ui.tplLayers[layer] : layer;
      return `<div class="field"><label><code>${esc(layer)}</code></label><div><input data-input="tplLayer" data-id="${esc(layer)}" data-keep="tpl-layer-${esc(layer)}" value="${esc(folder)}"><div class="muted small">${esc(str(l, 'description'))}</div></div></div>`;
    }).join('');
    return `<fieldset class="card"><legend>${esc(t(`Settings of ${name}`, `Réglages de ${name}`))}</legend>${fixed}
  <p class="muted small">${esc(t('The company defaults of the init answers: a project can still change them (-a). Only those that differ from the base are written.',
    "Les valeurs par défaut de l'entreprise pour les réponses d'init : un projet peut encore les changer (-a). Seules celles qui diffèrent de la base sont écrites."))}</p>
  <label class="check"><input type="checkbox" data-change="advanced" ${ui.advanced ? 'checked' : ''}> ${esc(t('Show advanced settings', 'Afficher les réglages avancés'))}</label>
  <div class="form">${questions}</div>
  <p class="muted small">${esc(t('The folder of each layer (src/<package>/<folder>/, tests/<folder>/):', 'Le dossier de chaque couche (src/<package>/<dossier>/, tests/<dossier>/) :'))}</p><div class="form">${layers}</div></fieldset>`;
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
    tab: (e) => { ui.tab = e.dataset.tab; },
    back: () => {
      // the component the command was opened from, on its view: the extension shows it again (and reads it)
      const to = ui.back;
      ui.back = null;
      if (!to) return;
      ui.view = to.view;
      post({ type: 'show', component: to.component });
    },
    overSub: (e) => { ui.overSub = e.dataset.sub; },
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
    projectWork: () => post({ type: 'showProjectWork', index: 0 }),
    openRule: (e) => post({ type: 'openRule', code: e.dataset.code }),
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
    filter: (e) => { ui.filter = e.dataset.filter; },
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
      delete ui.results[kind];
    },
    otherList: (e) => { ui.otherList = Number(e.dataset.i); ui.sel.other = -1; },
    otherSel: (e) => { ui.sel.other = Number(e.dataset.i); delete ui.results.quick; },
    quickPrimary, quickSecondary,
    clarify: () => { if (!ui.component) post({ type: 'message', text: t('Select a component.', 'Sélectionne un composant.') }); else chooseNext(); },
    openSpec: () => { if (ui.component) post({ type: 'openSpec', component: ui.component }); },
    openVowFile: () => { if (ui.component) post({ type: 'openVowFile', component: ui.component }); },
    specPart: (e) => { ui.specPart = e.dataset.id; },
    openCode: () => { if (ui.component) post({ type: 'openCode', component: ui.component }); },
    aiPrompt: () => { if (ui.component) post({ type: 'run', label: 'prompt', args: ['prompt', ui.component], target: 'component' }); },
    addRule: () => post({ type: 'openCommand', command: 'rule add', values: ui.component ? { words: ui.component + '\n' } : {}, selected: ui.component }),
    openCommand: (e) => {
      const values = {};
      if (e.dataset.component) values.component = e.dataset.component;
      if (e.dataset.number) values.number = e.dataset.number;
      if (e.dataset.field === 'base' && baseNow().trim()) values.base = baseNow().trim();
      post({ type: 'openCommand', command: e.dataset.command, values, selected: ui.component });
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
      post({ type: 'inline', action, component: ui.component, number: str(row, 'number'), answer, question: str(row, 'question'), proposed: str(row, 'proposed') });
    },
    cancelDraft: (e) => {
      delete ui.drafts[e.dataset.key];
      if (ui.confirm && ui.confirm.target === e.dataset.target) {
        post({ type: 'cancelled', token: ui.confirm.token });
        ui.confirm = null;
      }
    },
    smartAdd: (e) => { (ui.smartRows[e.dataset.key] ||= []).push({ name: '', type: '', nullable: 'unknown', personal: 'unknown' }); ui.smartExpanded = e.dataset.key; },
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
    commandGroup: (e) => { ui.commandGroupsOpen[e.dataset.id] = ui.commandGroupsOpen[e.dataset.id] === false; },
    commandDocs: () => post({ type: 'commandDocs' }),
    removeUse: (e) => { ui.form.uses = (ui.form.uses || '').split(',').map((x) => x.trim()).filter((x) => x && x !== e.dataset.id).join(','); askPreview(0); },
    command: (e) => { ui.command = e.dataset.name; post({ type: 'openCommand', command: e.dataset.name, values: {}, selected: '' }); },
    segment: (e) => { ui.form[e.dataset.name] = e.dataset.value; askPreview(0); },
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
    sel: (e) => { if (e.dataset.kind === 'data') actions.dataAction(); else focusEditor(); },
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

  document.body.addEventListener('input', (event) => {
    const e = event.target;
    switch (e.dataset.input) {
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
        if (row && ['name', 'type', 'nullable', 'personal'].includes(e.dataset.name)) row[e.dataset.name] = e.value;
        const preview = document.querySelector('.smart-answer .smart-preview');
        if (preview) preview.textContent = structuredAnswer(rows, ui.smartQuestions[e.dataset.key] || '');
        return;
      }
      case 'base': ui.base = e.value; ui.baseTouched = true; return;
      case 'commandFilter': ui.commandFilter = e.value; render(); return;
      case 'form':
        ui.form[e.dataset.name] = e.value;
        askPreview();
        if (['component', 'question', 'number', 'type', 'extends'].includes(e.dataset.name)) render();
        return;
      case 'initAnswer': ui.initAnswers[e.dataset.id] = e.value; ui.initTouched[e.dataset.id] = true; return;
      case 'tplAnswer': ui.tplAnswers[e.dataset.id] = e.value; return;
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
        if (row && ['name','type','nullable','personal'].includes(e.dataset.name)) row[e.dataset.name] = e.value;
        ui.smartExpanded = e.dataset.key; render(); return;
      }
      case 'formBool': ui.form[e.dataset.name] = e.checked ? 'true' : ''; askPreview(0); return;
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
      case 'initAnswer': ui.initAnswers[e.dataset.id] = e.value; ui.initTouched[e.dataset.id] = true; return;
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
