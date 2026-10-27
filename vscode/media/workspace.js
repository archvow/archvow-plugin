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
  let I = { commands: [], catalog: null, knownVersion: '', rules: { en: '', fr: '' }, types: {} };
  /** What the person chose on the page. */
  const ui = {
    tab: 'overview', overSub: 'architecture', forceWorkspace: false,
    /** The editor tab: 'empty', 'component', 'command' or 'result' (a command run from Overview). */
    mode: 'empty', resultLabel: '', title: '',
    component: '', view: 0, filter: 'all',
    sel: { decision: '', question: '', safety: '', assumption: '', data: '', other: -1 },
    otherList: 0, drafts: {}, severity: {}, results: {}, raw: {}, confirm: null,
    command: NAV ? '' : 'check', commandFilter: '', form: {}, formHelp: '',
    catalog: null, catalogSource: '', initTemplate: '', initAnswers: {}, initTouched: {}, advanced: false,
    base: 'origin/main', checkSel: 0, explained: {},
  };

  // ---------------------------------------------------------------- helpers

  const fr = () => !!(S && S.fr);
  const t = (en, frText) => (fr() ? frText : en);
  const esc = (v) => String(v === null || v === undefined ? '' : v).replace(/&/g, '&amp;').replace(/</g, '&lt;')
    .replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  const str = (o, k) => (o && o[k] !== null && o[k] !== undefined ? (typeof o[k] === 'object' ? JSON.stringify(o[k]) : String(o[k])) : '');
  const list = (o, k) => (o && Array.isArray(o[k]) ? o[k].filter((x) => x && typeof x === 'object') : []);
  const short = (s, n) => (s.length <= n ? s : s.substring(0, n - 1) + '…');
  const post = (m) => vscode.postMessage(m);
  const btn = (label, action, extra = '', disabled = false, cls = '') =>
    `<button class="${cls}" data-a="${action}" ${extra} ${disabled ? 'disabled' : ''}>${esc(label)}</button>`;
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

  // ---------------------------------------------------------------- the extension's messages

  window.addEventListener('message', (event) => {
    const m = event.data;
    switch (m.type) {
      case 'init':
        I = { commands: m.commands || [], catalog: m.catalog, knownVersion: m.knownVersion, rules: m.rules || { en: '', fr: '' }, types: m.types || {} };
        break;
      case 'state':
        S = m.state;
        if (ui.component && !(ui.component in (S.snapshots || {})) && S.spec && !componentIds().includes(ui.component)) ui.component = '';
        break;
      case 'select':
        ui.forceWorkspace = true;
        ui.mode = 'component';
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
        if (m.target === 'overview') { ui.mode = 'result'; ui.resultLabel = m.label || ''; }
        break;
      case 'result':
        ui.results[m.target] = { html: m.html, raw: m.raw, args: m.args, ok: m.ok, label: m.label };
        ui.raw[m.target] = false;
        if (ui.confirm && ui.confirm.target === m.target) ui.confirm = null;
        if (m.target === 'overview') { ui.mode = 'result'; ui.resultLabel = m.label || ''; }
        break;
      case 'confirm':
        ui.confirm = { token: m.token, title: m.title, summary: m.summary, target: m.target, command: m.command };
        if (m.target === 'overview') { ui.mode = 'result'; ui.resultLabel = m.title || ''; }
        break;
      case 'openCommand':
        ui.forceWorkspace = true;
        ui.mode = 'command';
        setCommand(m.command, m.values || {}, m.help || '');
        break;
      case 'vow':
        if (S) S.vows[m.component] = m.text;
        break;
      case 'catalog':
        ui.catalog = m.data;
        ui.catalogSource = m.source || '';
        chooseInitTemplate();
        break;
      case 'showChecks':
        if (!NAV) return;
        ui.tab = 'overview';
        ui.overSub = 'checks';
        break;
      case 'browsed':
        ui.form[m.field] = m.path;
        break;
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
    if (ui.mode === 'component') return 'archvow: ' + (ui.view === 7 ? t('Other / Project', 'Autres / Projet') : ui.component || t('Components', 'Composants'));
    if (ui.mode === 'command') return 'archvow: ' + ui.command;
    if (ui.mode === 'result') return 'archvow: ' + (ui.resultLabel || t('Result', 'Résultat'));
    return 'archvow';
  }

  function header() {
    const reload = btn('↻', 'refresh', `title="${esc(t('Reload Archvow', 'Recharger Archvow'))}"`, false, 'icon');
    const log = btn('☷', 'log', `title="${esc(t('Open Archvow session log', 'Ouvrir le journal Archvow'))}"`, false, 'icon');
    const progress = S.loading ? `<div class="progress">⏳ ${esc(S.loading)}</div>` : '';
    if (NAV) {
      return `<header class="top">
  <div class="brand"><img src="${esc(window.ARCHVOW_LOGO)}" alt="" class="logo">
    <div><div class="title">Archvow</div><div class="project" title="${esc(S.project.root)}">⌂ ${esc(S.project.name)}</div></div></div>
  <div class="right">${reload}${log}</div></header>${progress}`;
    }
    const what = ui.mode === 'component' ? (ui.view === 7 ? t('Other / Project', 'Autres / Projet') : ui.component)
      : ui.mode === 'command' ? 'archvow ' + ui.command : ui.mode === 'result' ? ui.resultLabel : '';
    const kind = ui.mode === 'component' ? t('Component', 'Composant') : ui.mode === 'command' ? t('Command', 'Commande')
      : ui.mode === 'result' ? t('Result', 'Résultat') : '';
    // a component's name is the heading of its page, with its open items (componentDetail)
    const named = ui.mode === 'component' && ui.view !== 7 && ui.component && snapshot();
    return `<header class="top ${named ? 'slim' : ''}">
  <div class="brand"><img src="${esc(window.ARCHVOW_LOGO)}" alt="" class="logo">
    <div><div class="kind">${esc(kind ? 'Archvow · ' + kind : 'Archvow')}</div>${named ? '' : `<div class="title">${esc(what || t('Specifications → understandable components', 'Des specs aux composants compréhensibles'))}</div>`}
    <div class="project" title="${esc(S.project.root)}">⌂ ${esc(S.project.name)}</div></div></div>
  <div class="right">${reload}${log}</div></header>${progress}`;
  }

  /** The editor tab: the component, the command or the result chosen in the sidebar. */
  function editorBody() {
    if (ui.mode === 'component') return `<section class="detail-only">${componentDetail()}</section>`;
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
  <div class="row">${btn(t('Initialize Archvow', 'Initialiser Archvow'), 'init', '', S.readiness !== 'NOT_INITIALIZED')}
  ${btn(t('Configure Python', 'Configurer Python'), 'settings')}${btn(t('Retry', 'Réessayer'), 'refresh')}</div></section>`;
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
    return `<div class="card result"><div class="card-head"><b>▸ ${esc(t('Command result', 'Résultat de la commande'))}</b>
  <span class="row">${btn(t('Raw output', 'Sortie brute'), 'raw', attrs({ target }), r.running || !r.raw, raw ? 'on' : 'secondary')}
  ${btn(t('Copy', 'Copier'), 'copyResult', attrs({ target }), r.running || !r.raw, 'secondary')}
  ${btn('×', 'closeResult', attrs({ target }) + ` title="${esc(t('Close result', 'Fermer le résultat'))}"`, false, 'icon')}</span></div>
  <div class="card-body" data-scroll="result-${target}" title="${esc(r.args || '')}">${body}</div></div>`;
  }

  function confirmation(target) {
    const c = ui.confirm;
    if (!c || c.target !== target) return '';
    return `<div class="confirm"><div class="confirm-title">⚠ ${esc(t('Confirm', 'Confirmer'))}: ${esc(c.title)}</div>
  <pre>${esc(c.summary)}</pre><div class="command">${esc(c.command)}</div>
  <div class="row end">${btn(t('Cancel', 'Annuler'), 'cancelConfirm', '', false, 'secondary')}${btn(t('Confirm and run', 'Confirmer et exécuter'), 'confirm')}</div></div>`;
  }

  // ---------------------------------------------------------------- Overview

  function overviewTab() {
    const work = S.work;
    const branchWarning = needsFeatureBranch(S.branch) ? `<div class="warning" title="${esc(t('Advice only. Archvow does not automatically change Git branches.',
      'Conseil uniquement. Archvow ne change pas automatiquement de branche Git.'))}">${esc(t(`⚠ You are on ${S.branch}. For development, create a feature branch (e.g. feature/my-change) first.`,
      `⚠ Branche ${S.branch} : crée une branche de fonctionnalité (ex. feature/ma-modification) avant de développer.`))}</div>` : '';
    const toolbar = [
      btn('↻', 'refresh', '', false, 'icon'),
      btn('Check', 'run', attrs({ label: 'check', args: 'check --json', target: 'overview' })),
      btn('Next', 'run', attrs({ label: 'next', args: 'next --json', target: 'overview' })),
      btn('Full check', 'run', attrs({ label: 'full check', args: 'check --complete --json', target: 'overview' })),
      btn('Doctor', 'run', attrs({ label: 'doctor', args: 'doctor --json', target: 'overview' })),
      btn('Audit', 'run', attrs({ label: 'audit', args: 'audit --json', target: 'overview' })),
      btn(t('Project vow', 'Vow du projet'), 'openProjectVow'),
      btn('Budget / Next', 'budget'),
      btn('Diff', 'openCommand', attrs({ command: 'diff', field: 'base' })),
      btn('Review', 'openCommand', attrs({ command: 'review', field: 'base' })),
      `<label class="base" title="${esc(t('Git comparison base for Review / Diff / Budget', 'Base de comparaison Git pour Review / Diff / Budget'))}">Base: <input data-input="base" data-keep="base" value="${esc(ui.base)}" size="14"></label>`,
    ].join('');
    const sub = ui.overSub === 'checks' ? checksView() : architectureView();
    return `<section class="overview">
  <div class="strip"><b>${esc(t('Project status', 'État du projet'))}</b><span class="row">
    <button class="badge pending" data-a="quickList" data-i="0" title="${esc(t('See open items by component', 'Voir les éléments ouverts par composant'))}">${work.pending.length} ${esc(t('pending', 'en attente'))}</button>
    <button class="badge resolved" data-a="quickList" data-i="1" title="${esc(t('See completed items by component', 'Voir les éléments traités par composant'))}">${work.resolved.length} ${esc(t('resolved', 'traités'))}</button></span></div>
  ${branchWarning}
  <div class="toolbar"><div class="row wrap">${toolbar}</div><span class="muted" title="${esc(t('Current Git branch', 'Branche Git courante'))}">Git: ${esc(S.branch || '—')}</span></div>
  <nav class="tabs sub">${[['architecture', 'Architecture'], ['checks', t('Check & Gates', 'Contrôles et validations')]]
    .map(([id, l]) => `<button class="tab ${ui.overSub === id ? 'current' : ''}" data-a="overSub" data-sub="${id}">${esc(l)}</button>`).join('')}</nav>
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
    // the sidebar is narrow: one line per component (type, layer, state in the line and its tooltip)
    const rows = S.rows.map((r) => `<li data-a="openComponentRow" data-id="${esc(r.id)}" class="${r.pending ? 'pending' : 'done'} ${r.id === ui.component ? 'selected' : ''}"
  title="${esc([r.type, r.layer, r.container, localizedState(r.state)].filter((x) => x).join(' · ') + (r.state === 'drift' ? ' — ' + t('The code differs from its vow', 'Le code diffère de sa vow') : ''))}">
  <span class="line"><b>${esc(r.id)}</b> <span class="muted">· ${esc(r.type)} · ${esc(localizedState(r.state))}</span></span>${r.pending ? `<span class="count">${r.pending}</span>` : '<span class="ok">✓</span>'}</li>`).join('');
    return `<div class="card"><h3>${esc(name)}</h3>
  <p class="muted">${S.rows.length} ${esc(t('components', 'composants'))} · ${S.work.pending.length} ${esc(t('pending', 'en attente'))} · ${S.work.resolved.length} ${esc(t('resolved', 'traités'))}</p>
  ${S.rows.length ? `<ul class="list items arch" data-scroll="architecture">${rows}</ul>
  <p class="muted">${esc(t('Click a component to open it in the editor.', "Clique sur un composant pour l'ouvrir dans l'éditeur."))}</p>`
    : `<p>${esc(t('No components declared in the specification. Open Components or create one.', 'Aucun composant déclaré dans la spécification. Ouvre Composants ou crée-en un.'))}</p>`}</div>`;
  }

  function checksView() {
    const check = S.check;
    let totals;
    let items = [];
    if (!check) {
      totals = `<p class="tone-bad">${esc(t('No check result available', 'Résultat indisponible'))}</p><p class="muted">${esc(S.checkFailure || t('Run Check or verify the CLI.', 'Exécute le contrôle ou vérifie le CLI.'))}</p>`;
    } else {
      items = list(check, 'violations');
      const ok = check.ok === true;
      const rules = byRule(check);
      totals = `<p class="${ok ? 'tone-good' : 'tone-bad'}"><b>${esc(ok ? t('PASSED', 'VALIDÉ') : t('FAILED', 'NON VALIDÉ'))}</b> · ${Number(check.errors) || 0} ${esc(t('errors', 'erreurs'))} · ${Number(check.warnings) || 0} ${esc(t('warnings', 'avertissements'))}</p>
  ${rules ? `<p class="by-rule">${esc(rules)}</p>` : ''}
  <p class="muted">${esc(t('Select a diagnostic for details; double-click to open its rule.', 'Sélectionne un diagnostic pour voir son détail ; double-clique pour ouvrir la règle.'))}</p>`;
    }
    if (ui.checkSel >= items.length) ui.checkSel = 0;
    const rows = items.map((v, i) => `<li class="${i === ui.checkSel ? 'selected' : ''} sev-${esc(str(v, 'severity'))}" data-a="checkSel" data-i="${i}">
  <b>${esc(str(v, 'code'))}</b> <span class="chip ${str(v, 'severity') === 'error' ? 'bad' : 'warn'}">${esc(str(v, 'severity'))}</span> ${esc(short(str(v, 'message'), 160))}</li>`).join('');
    const selected = items[ui.checkSel];
    let detail = items.length ? '' : `<p class="muted">${esc(t('No detailed diagnostics.', 'Aucun diagnostic détaillé.'))}</p>`;
    if (selected) {
      const code = str(selected, 'code');
      const explanation = ruleExplanation(code);
      const file = str(selected, 'file');
      detail = `<div class="details"><h4>${esc(code)}${str(selected, 'title') ? ' — ' + esc(str(selected, 'title')) : ''}</h4><p>${esc(str(selected, 'message'))}</p>
  ${file ? `<p class="muted">${esc(file)}${Number(selected.line) ? ':' + Number(selected.line) : ''}</p>` : ''}
  ${explanation ? `<p><b>${esc(t('Explanation', 'Explication'))}:</b> ${esc(explanation)}</p>` : ''}
  <div class="row">${btn(t('Open rule…', 'Voir la règle…'), 'openRule', attrs({ code }))}${file ? btn(t('Open file', 'Ouvrir le fichier'), 'openFile', attrs({ file, line: Number(selected.line) || 0 })) : ''}</div></div>`;
    }
    return `<div class="card"><div class="card-head"><h3>${esc(t('Check & Gates', 'Contrôles et validations'))}</h3>${btn(t('Check reference', 'Référence des contrôles'), 'openChecks', '', false, 'secondary')}</div>
  ${totals}<ul class="list" data-scroll="checks">${rows}</ul>${detail}</div>`;
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
        return `<li class="component ${id === ui.component ? 'selected' : ''} ${p ? 'pending' : 'done'}" data-a="pick" data-id="${esc(id)}">${esc(id)} <span class="muted">· ${esc(str(comp, 'type'))}</span> ${p ? `<span class="count">${p}</span>` : '<span class="ok">✓</span>'}</li>`;
      }).join('')}</ul></li>`).join('')}</ul></li>`;
    }
    return `<div class="row">${filters.map(([id, l]) => btn(l, 'filter', attrs({ filter: id }) + ` title="${esc(ui.filter === id ? t('Current filter', 'Filtre actif') : t('Filter components', 'Filtrer les composants'))}"`, ui.filter === id, 'secondary small')).join('')}</div>
  <ul class="tree" data-scroll="tree">${tree || `<li class="muted">${esc(S.spec ? t('No component.', 'Aucun composant.') : t('Loading…', 'Chargement…'))}</li>`}</ul>`;
  }

  const VIEWS = () => [t('Decisions', 'Décisions'), 'Questions', t('Safety', 'Sécurité'), t('Assumptions', 'Hypothèses'), t('Rules', 'Règles'), t('Tasks', 'Tâches'), 'Full vow', t('Other / Project', 'Autres / Projet')];

  function componentDetail() {
    const actions = [
      btn(t('Clarify', 'Clarifier'), 'clarify'), btn(t('Open spec', 'Ouvrir la spec'), 'openSpec', '', false, 'secondary'),
      btn(t('Open code', 'Ouvrir le code'), 'openCode', '', false, 'secondary'),
      btn(t('Edit spec', 'Modifier la spec'), 'openCommand', attrs({ command: 'component edit', component: ui.component }), false, 'secondary'),
      btn(t('AI prompt', 'Instruction IA'), 'aiPrompt', '', false, 'secondary'),
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
    const viewBar = names.map((name, i) => {
      const label = i < 6 ? name + (counts.pending[i] > 0 ? ' · ' + counts.pending[i] : counts.total[i] > 0 ? ' ✓' : '') : name;
      const tip = i < 6 ? (counts.pending[i] > 0 ? `${counts.pending[i]} ${t('pending items', 'éléments en attente')}` : t('No open items', 'Aucun élément ouvert')) : '';
      return `<button class="tab ${ui.view === i ? 'current' : ''} ${i < 6 && counts.pending[i] > 0 ? 'has-pending' : ''}" data-a="view" data-i="${i}" title="${esc(tip)}">${esc(label)}</button>`;
    }).join('');
    let body;
    if (ui.view === 7) body = otherView();
    else if (!ui.component) body = `<p class="muted">${esc(t('Select a component in the Archvow sidebar.', 'Sélectionne un composant dans le panneau Archvow.'))}</p>`;
    else if (!view) body = `<p class="muted">${esc(t('Loading', 'Chargement'))}: ${esc(ui.component)}…</p>`;
    else body = [decisionsView, questionsView, safetyView, assumptionsView, rulesView, tasksView, vowView][ui.view](view, d);
    // Other / Project is the project's, not the component's: no component head over it (as in IntelliJ)
    const head = view && ui.view !== 7 ? `<div class="component-head"><h2>${esc(ui.component)}${openBadge(ui.component)}</h2><p class="meta muted">${esc(meta(view))}</p>
  <p class="description">${esc(str(view, 'description'))}</p>${links(view)}${examples(view)}${list(view, 'missing').length || (Array.isArray(view.missing) && view.missing.length)
    ? `<p class="tone-warn">${esc(t('Missing', 'Manquant'))}: ${esc(view.missing.map((x) => (typeof x === 'object' ? JSON.stringify(x) : x)).join(', '))}</p>` : ''}</div>` : '';
    // the head, the actions and the view chips over the whole width; under them, for the views whose
    // items are answered (decisions, questions, safety, assumptions), the items left and the answer right
    return `${head}<div class="row wrap actions">${actions.join('')}</div>
  <nav class="tabs views">${viewBar}</nav>${pendingExplanation()}
  <div class="view-body" data-scroll="view-${ui.view}">${body}</div>${confirmation('component')}${resultCard('component')}`;
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

  /** The items of a view on the left, the selected one's answer panel on the right (stacked when narrow). */
  function split(left, right) {
    return `<div class="split"><div class="split-list">${left}</div><aside class="split-answer">${right}</aside></div>`;
  }

  /** The items as cards: "n · question", then the recorded answer (green) with who and when, or "● Open". */
  function cards(kind, rows, label, card) {
    return `<ul class="cards" data-scroll="cards-${kind}">${rows.map((row, i) => {
      const key = label(row, i);
      const c = card(row, i);
      return `<li class="item-card ${ui.sel[kind] === key ? 'selected' : ''} ${c.open ? 'open' : 'done'}" data-a="sel" data-kind="${kind}" data-key="${esc(key)}">
  <div class="card-q"><b>${esc(c.number)}</b> · ${esc(c.title)}</div>
  ${c.open ? `<div class="card-open">● ${esc(c.state || t('Open', 'Ouvert'))}</div>${c.note ? `<div class="card-note">${esc(short(c.note, 160))}</div>` : ''}`
    : `<div class="card-a">${esc(short(c.answer || c.state || t('Resolved', 'Traité'), 220))}</div>`}
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

  /** Why the component's total can exceed a view's count (as the IntelliJ workspace says). */
  function pendingExplanation() {
    if (!ui.component) return '';
    let total = 0;
    let data = 0;
    let other = 0;
    for (const item of S.work.pending) {
      if (item.component !== ui.component) continue;
      total++;
      const category = item.preset.view || 'other';
      if (category === 'data') data++;
      if (category === 'other') other++;
    }
    if (!total) return '';
    return `<p class="muted small" title="${esc(t('The component total includes all its pending categories and active diagnostics.', 'Le total du composant inclut toutes ses catégories en attente et les diagnostics actifs.'))}">${esc(fr()
      ? `${total} en attente pour ${ui.component} · ${data} dans Données et tâches` + (other ? ` · ${other} dans Autres / Projet` : '')
      : `${total} pending for ${ui.component} · ${data} in Data & tasks` + (other ? ` · ${other} in Other / Project` : ''))}</p>`;
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

  /** The answer field and its buttons: secondary ones on the left, Cancel and the main one on the right. */
  function answerEditor(key, initial, placeholder, secondary, save, target, disabled = false) {
    return `<textarea class="answer-field" rows="5" data-input="draft" data-key="${esc(key)}" data-keep="${esc(key)}" placeholder="${esc(placeholder)}" ${disabled ? 'disabled' : ''}>${esc(draft(key, initial))}</textarea>
  <div class="answer-actions"><span class="row wrap">${secondary}</span>
  <span class="row">${btn(t('Cancel', 'Annuler'), 'cancelDraft', attrs({ key, target }) + ` title="${esc(t('Put back the recorded text', 'Remettre le texte enregistré'))}"`, disabled, 'secondary')}${save}</span></div>`;
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
        + answerEditor(key, answer || proposed || suggested, t('Your answer or proposal', 'Votre réponse ou proposition'), secondary,
          btn(t('Save answer', 'Enregistrer la réponse'), 'decide', attrs({ action: 'decision make' })), 'decision')
        + confirmation('decision') + resultCard('decision');
    }
    return split(left, right);
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
    return split(left, right);
  }

  function safetyView(view, d) {
    const rows = list(view, 'safety');
    const label = (r) => 'S' + str(r, 'number');
    const statusOf = (r, i) => (d && d.safety[i] ? d.safety[i].status : str(r, 'status'));
    const isOpen = (r, i) => statusOf(r, i) !== 'RESOLVED';
    const row = current('safety', rows, label, isOpen);
    const recorded = (r) => {
      const hazard = list(r, 'hazards')[0];
      if (str(r, 'dismissed')) return t('Not applicable: ', 'Non applicable : ') + str(r, 'dismissed');
      return hazard ? `${t('Hazard', 'Danger')} ${str(hazard, 'id')} · ${str(hazard, 'severity')}: ${str(hazard, 'description')}` : '';
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
      let block = `<p class="answer-q">${esc(str(row, 'question'))}</p>`;
      if (str(suggestion, 'hazard')) block += `<p class="hint"><b>${esc(t('Proposed hazard', 'Danger proposé'))}</b> ${esc(str(suggestion, 'hazard'))}</p>`;
      if (str(suggestion, 'invariant')) block += `<p class="hint"><b>${esc(t('Proposed invariant', 'Invariant proposé'))}</b> ${esc(str(suggestion, 'invariant'))}</p>`;
      if (!hazards.length && !str(suggestion, 'hazard') && str(row, 'status').toLowerCase() === 'open') {
        block += `<p class="tone-warn"><b>${esc(t('No hazard recorded yet. Declare one or mark the question not applicable.', "Aucun danger déclaré. Déclare un danger ou indique que la question ne s'applique pas."))}</b></p>`;
      }
      for (const h of hazards) {
        const guards = list(h, 'prevented_by');
        block += `<div class="recorded"><p><b>${esc(t('Hazard', 'Danger'))} ${esc(str(h, 'id'))} · ${esc(str(h, 'severity'))}</b><br>${esc(str(h, 'description'))}</p>`;
        for (const g of guards) {
          block += `<p class="tone-good">↳ <b>Invariant ${esc(str(g, 'id'))}</b> <span class="body">${esc(str(g, 'statement'))}</span>${g.tested === false ? ` <span class="muted">[${esc(t('test not verified', 'test non vérifié'))}]</span>` : ''}</p>`;
        }
        if (!guards.length && ['high', 'critical'].includes(str(h, 'severity').toLowerCase())) {
          block += `<p class="tone-warn"><b>ARCH030 — ${esc(t('An invariant is required before this hazard can be considered protected.', 'Un invariant est obligatoire pour que ce danger soit considéré protégé.'))}</b></p>`;
        }
        block += '</div>';
      }
      if (str(row, 'dismissed')) block += `<div class="recorded"><p><b>${esc(t('Not applicable', 'Non applicable'))}:</b> ${esc(str(row, 'dismissed'))}</p></div>`;
      const open = str(row, 'status').toLowerCase() === 'open';
      const missing = d && d.safety[i] ? d.safety[i].unprotected : '';
      const key = `s:${ui.component}:${str(row, 'number')}`;
      const severity = ui.severity[key] || 'high';
      const proposed = !!str(suggestion, 'hazard');
      const editor = open ? `<div class="row answer-label"><span>${esc(t('Hazard description or reason', 'Danger ou justification'))}</span>
  <label>${esc(t('Severity', 'Sévérité'))} <select data-change="severity" data-key="${esc(key)}">${['critical', 'high', 'medium', 'low'].map((s) => `<option ${s === severity ? 'selected' : ''}>${s}</option>`).join('')}</select></label></div>`
        + answerEditor(key, str(suggestion, 'hazard'), t('What must never happen, or why it cannot', "Ce qui ne doit jamais arriver, ou pourquoi c'est impossible"),
          btn(t('Not applicable', 'Non applicable'), 'safety', attrs({ action: 'hazard dismiss' }), false, 'secondary')
          + (proposed ? btn(str(suggestion, 'invariant') ? t('Use proposed hazard + invariant', 'Utiliser danger + invariant proposés') : t('Use proposed hazard', 'Utiliser le danger proposé'),
            'safety', attrs({ action: 'hazard suggested' }) + ` title="${esc(d && d.safety[i] ? d.safety[i].preview : '')}"`, false, 'secondary') : ''),
          btn(t('Declare hazard', 'Déclarer un danger'), 'safety', attrs({ action: 'hazard add' })), 'safety') : '';
      const more = `<div class="row wrap more">${missing ? btn(t('Add required invariant', "Ajouter l'invariant requis"), 'choose', attrs({ purpose: 'invariant add', only: 'true' })) : ''}
  ${btn(t('Add invariant…', 'Ajouter un invariant…'), 'choose', attrs({ purpose: 'invariant add' }), false, 'secondary small')}
  ${btn(t('Remove hazard…', 'Supprimer un danger…'), 'choose', attrs({ purpose: 'hazard remove' }), false, 'secondary small')}
  ${btn(t('Remove invariant…', 'Supprimer un invariant…'), 'choose', attrs({ purpose: 'invariant remove' }), false, 'secondary small')}</div>`;
      right = answerHead(`${t('Safety', 'Sécurité')} S${str(row, 'number')}${str(row, 'guide_word') ? ' · ' + str(row, 'guide_word') : ''}`, status, !pending)
        + block + editor + more + confirmation('safety') + resultCard('safety');
    }
    return split(left, right);
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
    return split(left, right);
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

  function vowView() {
    const text = S.vows[ui.component];
    if (text === undefined) {
      post({ type: 'vow', component: ui.component });
      return `<p class="muted">${esc(t('Loading', 'Chargement'))}…</p>`;
    }
    if (!text) return `<p class="muted">${esc(t('No spec.vow for this component.', 'Pas de spec.vow pour ce composant.'))}</p>`;
    const lines = text.split('\n').map((line) => {
      const m = /^(\s*)("(?:[^"\\]|\\.)*"|[A-Za-z_][A-Za-z0-9_./-]*)(.*)$/.exec(line);
      if (line.trimStart().startsWith('#') || !m) return `<span class="${line.trimStart().startsWith('#') ? 'comment' : ''}">${esc(line)}</span>`;
      const depth = Math.min(3, Math.floor(m[1].length / 2));
      return `${esc(m[1])}<span class="key${depth}">${esc(m[2])}</span>${esc(m[3])}`;
    });
    return `<div class="row">${btn(t('Open spec', 'Ouvrir la spec'), 'openSpec', '', false, 'secondary')}${btn(t('Project vow', 'Vow du projet'), 'openProjectVow', '', false, 'secondary')}</div><pre class="vow">${lines.join('\n')}</pre>`;
  }

  function otherView() {
    const pendingItems = S.work.otherPending;
    const resolvedItems = S.work.otherResolved;
    const items = ui.otherList === 0 ? pendingItems : resolvedItems;
    if (ui.sel.other >= items.length) ui.sel.other = -1;
    const rows = items.map((item, i) => `<li class="${i === ui.sel.other ? 'selected' : ''} ${item.pending ? 'pending' : 'done'}" data-a="otherSel" data-i="${i}">
  ${chip(item.kind, item.kind === 'ERROR' ? 'bad' : item.kind === 'RESOLVED' ? 'good' : 'warn')} <b>${esc(item.title)}</b><div class="muted small">${esc(short(item.detail, 150))}</div></li>`).join('');
    const item = items[ui.sel.other];
    let selected = '';
    if (item) {
      const secondary = { 'hazard add': t('Not applicable', 'Non applicable'), 'assumption accept': t('Reject', 'Rejeter'), 'decision accept': t('Edit answer', 'Autre réponse'),
        'schema-create': t('Edit reader dependencies', 'Modifier les dépendances du lecteur') }[item.command]
        || (['open-file', 'component edit', 'invariant add'].includes(item.command) && item.component ? t('Open component', 'Ouvrir le composant') : '');
      selected = `<div class="details"><h4>${esc(item.title)}</h4>${chip(item.pending ? t('Pending', 'En attente') : t('Resolved', 'Traité'), item.pending ? 'warn' : 'good')}
  <pre class="plain">${esc(item.detail)}</pre>${item.component ? `<p><b>${esc(t('Component', 'Composant'))}:</b> ${esc(item.component)}</p>` : ''}
  <div class="row">${btn(item.action, 'quickPrimary')}${secondary ? btn(secondary, 'quickSecondary', '', false, 'secondary') : ''}</div></div>${confirmation('quick')}${resultCard('quick')}`;
    }
    return `<p class="muted">${esc(t('Checks, unclassified and project-wide issues; no component assigned unless verified.', 'Contrôles, éléments non classés et problèmes globaux ; aucun composant attribué sans preuve.'))}</p>
  <nav class="tabs sub">${btn(`${t('Pending', 'En attente')} (${pendingItems.length})`, 'otherList', attrs({ i: 0 }), false, 'tab ' + (ui.otherList === 0 ? 'current' : ''))}
  ${btn(`${t('Resolved', 'Traités')} (${resolvedItems.length})`, 'otherList', attrs({ i: 1 }), false, 'tab ' + (ui.otherList === 1 ? 'current' : ''))}</nav>
  <ul class="list items" data-scroll="other">${rows || `<li class="muted">${esc(t('Nothing to display.', 'Rien à afficher.'))}</li>`}</ul>${selected}`;
  }

  // ---------------------------------------------------------------- Commands

  const LONG_TEXT = new Set(['description', 'answer', 'rationale', 'statement', 'question', 'text', 'words']);
  const option = (p) => (p.flags.length ? p.flags[p.flags.length - 1] : '');

  function setCommand(name, values, help) {
    ui.command = name;
    ui.form = Object.assign({}, values);
    ui.formHelp = help || '';
    if (ui.confirm && ui.confirm.target === 'command') ui.confirm = null;
    if (name === 'init') {
      if (!('no_input' in ui.form)) ui.form.no_input = 'true';
      post({ type: 'catalog', template: ui.form.template || '' });
    }
  }

  function commandsTab() {
    const filter = ui.commandFilter.toLowerCase();
    const items = I.commands.filter((c) => !filter || c.path.join(' ').includes(filter));
    const command = commandByName(ui.command);
    return `<section class="browser-only commands-list"><input data-input="commandFilter" data-keep="commandFilter" placeholder="${esc(t('Filter commands', 'Filtrer les commandes'))}" value="${esc(ui.commandFilter)}">
  <ul class="list items" data-scroll="commands">${items.map((c) => `<li class="${command && c.path.join(' ') === ui.command ? 'selected' : ''}" data-a="command" data-name="${esc(c.path.join(' '))}" title="${esc(c.description)}"><b>${esc(c.path.join(' '))}</b><div class="muted small">${esc(short(c.description || '', 90))}</div></li>`).join('')}</ul>
  <p class="muted small">${esc(t('A command opens its form in the editor.', "Une commande ouvre son formulaire dans l'éditeur."))}</p></section>`;
  }

  function commandForm(command) {
    const name = command.path.join(' ');
    const extra = name === 'specify' ? t('IDE mode: clarification without interactive terminal.', 'Mode IDE : clarification sans terminal interactif.')
      : name === 'rule add' ? t('One argument per line in words.', 'Un argument par ligne dans le champ words.')
        : t('JSON is added automatically when supported.', 'Le format JSON est activé automatiquement quand il est disponible.');
    let fields = '';
    for (const p of command.parameters) {
      if (name === 'init' && p.name === 'answer') continue;
      const value = ui.form[p.name] || '';
      const id = 'f-' + p.name;
      let control;
      if (p.boolean) {
        control = `<input type="checkbox" data-change="formBool" data-name="${esc(p.name)}" ${value === 'true' ? 'checked' : ''}>`;
      } else if (name === 'component add' && p.name === 'type') {
        control = `<input list="types" data-input="form" data-name="${esc(p.name)}" data-keep="${id}" value="${esc(value)}"><datalist id="types">${Object.keys(I.types).map((ty) => `<option value="${esc(ty)}">`).join('')}</datalist>`;
        const def = I.types[value];
        control += `<div class="muted small">${esc(def ? `${value} — ${def.description}${def.hint ? ' | ' + t('Example', 'Exemple') + ': ' + def.hint : ''}${def.pattern ? ' | name_pattern: ' + def.pattern : ''}`
          : t('Select a type; naming constraints depend on the template.', 'Choisis un type ; les contraintes de nom dépendent du modèle.'))}</div>`;
      } else if (p.choices.length) {
        control = `<select data-change="form" data-name="${esc(p.name)}"><option value=""></option>${p.choices.map((c) => `<option ${c === value ? 'selected' : ''}>${esc(c)}</option>`).join('')}</select>`;
      } else if (LONG_TEXT.has(p.name)) {
        control = `<textarea rows="3" data-input="form" data-name="${esc(p.name)}" data-keep="${id}">${esc(value)}</textarea>`;
      } else {
        control = `<input data-input="form" data-name="${esc(p.name)}" data-keep="${id}" value="${esc(value)}">`;
      }
      if (name === 'init' && p.name === 'dir') control = `<div class="row">${control}${btn(t('Browse…', 'Parcourir…'), 'browse', attrs({ field: 'dir' }), false, 'secondary')}</div>`;
      let help = p.help;
      if (name === 'component add' && p.name === 'spec') help += t('  ·  Example: US-001 or a ticket reference from your backlog (optional; do not enter a .vow path).', '  ·  Exemple : US-001 ou une référence de ticket (facultatif ; pas un chemin .vow).');
      if (name === 'component add' && p.name === 'type') help += t('  ·  Types from built-in templates; company templates may extend them.', "  ·  Types des modèles intégrés ; les modèles d'entreprise peuvent en ajouter.");
      fields += `<div class="field"><label>${p.required ? '* ' : ''}${esc(p.flags.length ? option(p) : p.name)}</label><div>${control}${help ? `<div class="muted small">${esc(help)}</div>` : ''}</div></div>`;
    }
    if (name === 'init') fields += initWizard();
    if (['decision make', 'decision propose', 'decision accept', 'question remove'].includes(name)) fields += questionPreview(name);
    return `<pre class="help">archvow ${esc(name)}\n${esc(command.description)}\n${esc(extra)}${fr() ? '\nDescription et options fournies par le moteur Archvow.' : ''}${ui.formHelp ? '\n\n' + esc(ui.formHelp) : ''}</pre>
  <div class="form">${fields}</div>
  <div class="row end">${btn(t('Run command', 'Exécuter la commande'), 'runForm')}</div>${confirmation('command')}${resultCard('command')}`;
  }

  function questionPreview(name) {
    const component = (ui.form.component || '').trim();
    const number = (name === 'decision accept' || name === 'question remove' ? ui.form.number : ui.form.question || '').trim();
    const view = S.snapshots[component];
    let hint;
    if (!component || !number) hint = `<i>${esc(t('Enter component and question number.', 'Saisis le composant et le numéro de question.'))}</i>`;
    else if (!view) hint = esc(t("Question not loaded. Click 'Load question' below.", 'Question non chargée. Clique sur « Charger la question ».'));
    else {
      const key = name === 'question remove' ? 'questions' : 'decisions';
      let match = list(view, key).find((r) => str(r, 'number') === number || str(r, 'key') === number);
      if (!match && name !== 'question remove') match = list(view, 'questions').find((r) => str(r, 'key') === number || str(r, 'number') === number);
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

  function chooseInitTemplate() {
    const requested = (ui.form.template || '').trim();
    const names = templates().map((x) => str(x, 'name'));
    const match = templates().find((x) => requested && (str(x, 'name') === requested || str(x, 'source') === requested || requested.endsWith('/' + str(x, 'name'))));
    if (match) ui.initTemplate = str(match, 'name');
    else if (requested) ui.initTemplate = ''; // a custom template path: its questions are not in this catalog
    else {
      ui.initTemplate = names.includes('python-app') ? 'python-app' : names[0] || '';
      if (ui.initTemplate) ui.form.template = ui.initTemplate;
    }
  }

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
        const choices = Array.isArray(q.choices) ? q.choices.map(String) : [];
        let control;
        if (q.multiple === true && choices.length) {
          const picked = new Set(value.split(',').map((x) => x.trim()));
          control = `<div class="row wrap">${choices.map((c) => `<label class="check"><input type="checkbox" data-change="initMulti" data-id="${esc(id)}" data-choice="${esc(c)}" ${picked.has(c) ? 'checked' : ''}> ${esc(c)}</label>`).join('')}</div>`;
        } else if (choices.length) {
          control = `<select data-change="initAnswer" data-id="${esc(id)}">${choices.map((c) => `<option ${c === value ? 'selected' : ''}>${esc(c)}</option>`).join('')}</select>`;
        } else {
          control = `<input data-input="initAnswer" data-id="${esc(id)}" data-keep="init-${esc(id)}" value="${esc(value)}">`;
        }
        questions += `<div class="field"><label title="${esc(str(q, 'prompt'))}">${esc(id)}${essential ? '  •' : ''}</label><div>${control}<div class="muted small">${esc(str(q, 'prompt'))}</div></div></div>`;
      }
    }
    const status = custom ? t('Custom template path: retaining your input; its questions are not in this catalog.', 'Modèle personnalisé : conserve le chemin saisi ; les questions ne sont pas connues.')
      : ui.catalogSource || t('Loading archvow catalog --json…', 'Chargement depuis archvow catalog --json…');
    return `<fieldset class="card"><legend>${esc(t('Template questions', 'Questions du modèle'))}</legend>
  <div class="row wrap"><label>${esc(t('Template', 'Modèle'))} <select data-change="initTemplate">${templates().map((x) => `<option ${str(x, 'name') === ui.initTemplate ? 'selected' : ''}>${esc(str(x, 'name'))}</option>`).join('')}</select></label>
  ${btn(t('Reload', 'Actualiser'), 'reloadCatalog', '', false, 'secondary')}
  <label class="check"><input type="checkbox" data-change="advanced" ${ui.advanced ? 'checked' : ''}> ${esc(t('Show advanced settings', 'Afficher les réglages avancés'))}</label></div>
  <p class="muted small">${esc(status)}</p><div class="form">${questions}</div></fieldset>
  <fieldset class="card"><legend>${esc(t('Other answers (optional)', 'Autres réponses (facultatif)'))}</legend>
  <textarea rows="2" data-input="form" data-name="answer" data-keep="f-answer" title="${esc(t('Optional extra answers, one KEY=VALUE per line', 'Réponses supplémentaires : KEY=VALUE par ligne'))}">${esc(ui.form.answer || '')}</textarea></fieldset>`;
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
    const first = all.find((item) => item.component && !isOther(item));
    if (first) {
      ui.filter = index === 0 ? 'pending' : 'resolved';
      selectComponent(first.component);
      ui.view = viewForItem(first);
      selectFromItem(first);
    } else {
      ui.filter = 'all';
      ui.view = 7;
      ui.otherList = index;
      ui.sel.other = (index === 0 ? S.work.otherPending : S.work.otherResolved).length ? 0 : -1;
    }
  }

  function quickItem() {
    const items = ui.otherList === 0 ? S.work.otherPending : S.work.otherResolved;
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
      case 'decision make': case 'decision accept': case 'decision propose': case 'hazard add': case 'hazard dismiss':
        if (!item.component) return;
        goToComponent(item.component);
        ui.view = item.command.startsWith('hazard') ? 2 : viewForItem(item);
        selectFromItem(item);
        return;
      case 'schema-create': {
        const container = list(S.spec, 'containers').find((c) => list(c, 'components').some((x) => str(x, 'id') === item.component));
        post({ type: 'openCommand', command: 'component add', values: Object.assign({ type: 'schema' }, container ? { container: str(container, 'id') } : {}), selected: ui.component });
        return;
      }
      case 'component edit': case 'invariant add':
        post({ type: 'openCommand', command: item.command, values: item.preset, selected: ui.component });
        return;
      case 'open-file':
        post({ type: 'openFile', file: item.preset.file || '', line: 0 });
        return;
      case 'open-diagnostics':
        post({ type: 'showChecksNav' });
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
      default:
        if (item.component) goToComponent(item.component);
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
    overSub: (e) => { ui.overSub = e.dataset.sub; },
    refresh: () => post({ type: 'refresh' }),
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
      const base = ui.base.trim();
      if (base) post({ type: 'run', label: 'next --base', args: ['next', '--base', base, '--json'], target: 'overview' });
    },
    openProjectVow: () => post({ type: 'openProjectVow' }),
    openChecks: () => post({ type: 'openChecks' }),
    openRule: (e) => post({ type: 'openRule', code: e.dataset.code }),
    openFile: (e) => post({ type: 'openFile', file: e.dataset.file, line: Number(e.dataset.line) || 0 }),
    quickList: (e) => {
      // the sidebar shows the components with such work; the editor tab opens the first of them
      const index = Number(e.dataset.i);
      const all = index === 0 ? S.work.pending : S.work.resolved;
      ui.tab = 'components';
      ui.filter = all.some((item) => item.component && !isOther(item)) ? (index === 0 ? 'pending' : 'resolved') : 'all';
      post({ type: 'quickList', index });
    },
    checkSel: (e) => { ui.checkSel = Number(e.dataset.i); },
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
    openCode: () => { if (ui.component) post({ type: 'openCode', component: ui.component }); },
    aiPrompt: () => { if (ui.component) post({ type: 'run', label: 'prompt', args: ['prompt', ui.component], target: 'component' }); },
    addRule: () => post({ type: 'openCommand', command: 'rule add', values: ui.component ? { words: ui.component + '\n' } : {}, selected: ui.component }),
    openCommand: (e) => {
      const values = {};
      if (e.dataset.component) values.component = e.dataset.component;
      if (e.dataset.number) values.number = e.dataset.number;
      if (e.dataset.field === 'base') values.base = ui.base.trim();
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
        question: str(row, 'question'), preview: d && d.safety[i] ? d.safety[i].preview : '' });
    },
    dataAction: () => {
      const d = derived();
      const item = d && d.data.find((x) => x.kind + ':' + x.id === ui.sel.data);
      if (!item) return;
      if (item.kind === 'COLUMN') post({ type: 'openCommand', command: 'column set', values: { component: ui.component, names: item.id }, selected: ui.component });
      else post({ type: 'taskPrompt', component: ui.component, task: item.id });
    },
    command: (e) => { ui.command = e.dataset.name; post({ type: 'openCommand', command: e.dataset.name, values: {}, selected: '' }); },
    runForm: () => post({ type: 'form', command: ui.command, values: ui.form, initAnswers: ui.command === 'init' ? initAnswers() : [], selected: ui.component }),
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
      case 'draft': ui.drafts[e.dataset.key] = e.value; return;
      case 'base': ui.base = e.value; return;
      case 'commandFilter': ui.commandFilter = e.value; render(); return;
      case 'form':
        ui.form[e.dataset.name] = e.value;
        if (['component', 'question', 'number', 'type'].includes(e.dataset.name)) render();
        return;
      case 'initAnswer': ui.initAnswers[e.dataset.id] = e.value; ui.initTouched[e.dataset.id] = true; return;
      default:
    }
  });

  document.body.addEventListener('change', (event) => {
    const e = event.target;
    switch (e.dataset.change) {
      case 'language': post({ type: 'language', mode: e.value }); return;
      case 'severity': ui.severity[e.dataset.key] = e.value; return;
      case 'form':
        ui.form[e.dataset.name] = e.value;
        if (ui.command === 'init' && e.dataset.name === 'template') post({ type: 'catalog', template: e.value });
        render();
        return;
      case 'formBool': ui.form[e.dataset.name] = e.checked ? 'true' : ''; return;
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
