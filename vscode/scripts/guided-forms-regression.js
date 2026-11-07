// Headless regression of the form-only guided presentation. No VS Code runtime needed.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../..');
const read = (...pieces) => JSON.parse(fs.readFileSync(path.join(root, ...pieces), 'utf8'));
const forms = read('contract', 'forms', 'forms.json');
const commands = read('contract', 'cli', 'commands.json').commands;
const catalog = read('contract', 'cli', 'catalog.json');
let source = fs.readFileSync(path.join(root, 'vscode', 'media', 'workspace.js'), 'utf8');
source = source.replace("  render();\n  post({ type: 'ready' });\n}());", `  window.audit = { ui, I, commandsTab, commandStages, commandForm, skillForm, initWizard, templateWizard,
    keepActiveGuidedStepVisible, refreshCommandStepIndicators, setCatalog: (c) => { ui.catalog = c; }, setCommand };
  render();
  post({ type: 'ready' });
}());`);
assert.match(source, /window.audit =/);
const handlers = {};
const app = { innerHTML: '' };
const document = {
  getElementById: name => name === 'app' ? app : null,
  querySelectorAll: () => [], querySelector: () => null, activeElement: null,
  body: { addEventListener: (name, fn) => { handlers[name] = fn; } },
};
const window = { ARCHVOW_ROLE: 'editor', ARCHVOW_LOGO: '', addEventListener: (name, fn) => { handlers[name] = fn; },
  removeEventListener() {}, setTimeout };
const posted = [];
vm.runInNewContext(source, { document, window, console, setTimeout, clearTimeout,
  acquireVsCodeApi: () => ({ postMessage: msg => posted.push(msg) }), CSS: { escape: String } });
assert.ok(window.audit);
window.audit.setCatalog(catalog);
const state = { fr: false, project: { name: 'test', root: '/tmp' },
  spec: { containers: [] }, snapshots: {}, derived: {}, rows: [], choices: {},
  work: { pending: [], resolved: [], otherPending: [], otherResolved: [], pendingByComponent: {}, resolvedByComponent: {} },
  check: { errors: 0, warnings: 0, violations: [] }, install: {}, briefs: { files: [], folders: [] },
  ai: {}, aiTools: ['copilot', 'claude-code'], branch: 'feature/test' };
handlers.message({ data: { type: 'init', commands, forms, catalog, types: {}, icons: {} } });
handlers.message({ data: { type: 'state', state } });
const audit = window.audit;
const collapsedCommands = audit.commandsTab();
assert.match(collapsedCommands, /data-id="components" aria-expanded="false"/,
  'VS Code Commands groups are initially collapsed');
assert.doesNotMatch(collapsedCommands, /class="list items command-children"/,
  'collapsed groups do not show their child commands');
audit.ui.commandGroupsOpen.components = true;
const expandedCommands = audit.commandsTab();
assert.match(expandedCommands, /data-id="components" aria-expanded="true"/,
  'opening Components reveals its commands');
assert.match(expandedCommands, /class="list items command-children"/,
  'expanded commands preserve their original actions and descriptions');
audit.ui.commandGroupsOpen.components = false;
audit.ui.commandFilter = 'component edit';
assert.match(audit.commandsTab(), /data-id="components" aria-expanded="true"/,
  'filter results open the matching collapsed command group');
assert.match(audit.commandsTab(), /data-name="component edit"/,
  'filter still reveals the matching command');
audit.ui.commandFilter = '';
let total = 0;
for (const cmd of commands) {
  const name = cmd.path.join(' ');
  const omissions = name === 'init' ? new Set(['template', 'answer'])
    : name === 'template new' ? new Set(['answer', 'layer']) : new Set();
  const steps = audit.commandStages(cmd, cmd.parameters);
  const listed = steps.flatMap(s => s.names);
  for (const field of cmd.parameters.filter(p => !omissions.has(p.name))) {
    assert.equal(listed.filter(n => n === field.name).length, 1, `${name}.${field.name} must be on exactly one step`);
    total++;
  }
  if (name === 'init') assert.ok(steps.some(s => s.extra === 'init-template') && steps.some(s => s.extra === 'init-other'));
  if (name === 'template new') assert.ok(steps.some(s => s.extra === 'template-basics'));
}
// Every real command renders at every page: this catches a missing schema,
// a broken conditional control or a renderer that only works for component add.
for (const cmd of commands) {
  const name = cmd.path.join(' ');
  const values = name === 'template new' ? { extends: 'python-app', name: 'company-app' }
    : name === 'init' ? { package: 'sales-pipeline' } : {};
  audit.setCommand(name, values, '');
  const pages = audit.commandStages(cmd, cmd.parameters);
  for (let index = 0; index < Math.max(1, pages.length); index++) {
    audit.ui.formStep = index;
    const html = audit.commandForm(cmd);
    assert.match(html, /class="form-grid guided-command-layout"/, `${name}, page ${index}: entire form renders`);
    assert.match(html, /id="form-preview"/, `${name}, page ${index}: command preview is still available`);
  }
}
for (const template of catalog.templates) {
  audit.ui.initTemplate = template.name;
  const cmd = commands.find(c => c.path.join(' ') === 'init');
  const steps = audit.commandStages(cmd, cmd.parameters);
  const listed = steps.flatMap(s => s.questions || []);
  for (const q of template.questions) {
    assert.equal(listed.filter(id => id === q.id).length, 1, `${template.name}: missing ${q.id}`);
    total++;
  }
}
const component = commands.find(c => c.path.join(' ') === 'component add');
audit.setCommand('component add', { type: 'reader', name: 'orders', description: 'Read orders', route: '/orders' }, '');
for (const idx of [0, 1, 2, 3]) {
  audit.ui.formStep = idx;
  const html = audit.commandForm(component);
  assert.match(html, /data-a="formStage"/, `component add step ${idx} nav`);
  assert.match(html, /id="form-preview"/, 'right preview stays at all steps');
}
const previousName = audit.ui.form.name;
audit.ui.formStep = 0;
const first = audit.commandForm(component);
assert.match(first, /data-name="type"/, 'first page exposes component type');
assert.match(first, /data-name="name"/, 'first page exposes component name');
audit.ui.formStep = 1;
const second = audit.commandForm(component);
assert.match(second, /data-name="uses"/, 'second page retains advanced dependencies');
assert.equal(audit.ui.form.name, previousName, 'navigation does not reset values');
assert.match(second, /data-a="formPrevious"/);
audit.ui.formStep = 2;
assert.match(audit.commandForm(component), /data-name="route"/, 'repeatable routes are preserved');
audit.ui.formStep = 3;
assert.match(audit.commandForm(component), /class="guided-review"/, 'review includes all fields');
assert.match(audit.commandForm(component), /Read orders/, 'the review retains entered descriptions');
assert.equal(audit.ui.form.route, '/orders', 'route value is not altered');
// Completion state comes from required values, never from whether someone has
// merely clicked a step. It is a visual indicator, not a new execution gate.
audit.ui.formStep = 0;
assert.match(audit.commandForm(component), /guided-step complete current/, 'complete Basics is green');
audit.setCommand('component add', { name: 'orders', description: 'Read orders' }, '');
audit.ui.formStep = 0;
assert.match(audit.commandForm(component), /guided-step incomplete current/, 'missing type is orange');
// Required fields affect the FULL step indicator while editing, without
// rebuilding the form and losing the caret position.
audit.setCommand('component add', { type: 'reader', name: 'orders', description: 'Read orders' }, '');
const stepStates = Array.from({ length: audit.commandStages(component, component.parameters).length }, () => new Set());
const stepButtons = stepStates.map((classes) => ({
  classList: { toggle: (name, on) => on ? classes.add(name) : classes.delete(name) }, title: '' }));
document.querySelector = (selector) => selector === '.guided-command-column .guided-steps'
  ? { querySelectorAll: () => stepButtons } : null;
audit.refreshCommandStepIndicators();
assert.ok(stepStates[0].has('complete'), 'filled required fields show the green completed state');
audit.ui.form.type = '';
audit.refreshCommandStepIndicators();
assert.ok(stepStates[0].has('incomplete') && !stepStates[0].has('complete'),
  'removing a required value immediately makes the step incomplete');
document.querySelector = () => null;

// Confirmation spans BOTH form and preview, and the terminal is the only
// copy of the command. The grid shrinks both columns to make room.
audit.ui.confirm = { target: 'command', title: 'Create component', summary: 'Review', command: 'archvow component add reader orders' };
const confirmationHtml = audit.commandForm(component);
assert.match(confirmationHtml, /guided-command-layout has-confirm/, 'full-width confirmation gets its grid layout');
assert.ok(confirmationHtml.indexOf('class="confirm"') > confirmationHtml.indexOf('<aside class="form-pane"'),
  'confirmation comes after both upper columns');
assert.doesNotMatch(confirmationHtml, /<pre>Review<\/pre>/, 'no duplicate summary above terminal');
assert.equal((confirmationHtml.match(/archvow component add reader orders/g) || []).length, 2,
  'the confirm command occurs once visibly and once as the copy button value');
assert.match(confirmationHtml, /data-scroll="command-fields"/, 'fields scroll, not the step navigation');
assert.ok(confirmationHtml.indexOf('class="guided-steps"') < confirmationHtml.indexOf('data-scroll="command-fields"'),
  'navigation is sticky above the form scroller');
audit.ui.confirm = null;
// A Next click must bring the selected step into view horizontally, without
// scrolling the editor or moving the fields vertically.
const active = { getBoundingClientRect: () => ({ left: 360, right: 445 }) };
const strip = { scrollLeft: 0, getBoundingClientRect: () => ({ left: 0, right: 300 }),
  querySelector: () => active };
audit.keepActiveGuidedStepVisible(strip);
assert.equal(strip.scrollLeft, 153, 'hidden step on right becomes visible');
active.getBoundingClientRect = () => ({ left: -35, right: 65 });
audit.keepActiveGuidedStepVisible(strip);
assert.equal(strip.scrollLeft, 110, 'previous step on left becomes visible');
const css = fs.readFileSync(path.join(root, 'vscode', 'media', 'workspace.css'), 'utf8');
assert.match(css, /guided-command-layout\s*>\s*\.confirm\s*\{[^}]*grid-column:\s*1\s*\/\s*-1/s,
  'command confirmation spans form and preview columns');
assert.match(css, /guided-command-fields\s+\.guided-review\s*\{[^}]*overflow:\s*visible/s,
  'review has no nested scrollbar');
assert.match(css, /guided-step\.complete\s*\{[^}]*background:/s,
  'a complete step turns green across the whole button');
assert.match(css, /\.commands-list \.command-children\s*\{[^}]*padding-left:/s,
  'the VS Code command tree has the IntelliJ-like child indentation');
for (const skill of forms.ai_skills.order) {
  audit.ui.skill = skill;
  audit.ui.skillValues = { component: 'config', brief: 'briefs/specs.md#should-run', max: '5', tool: 'copilot' };
  for (let index = 0; index < 3; index++) {
    audit.ui.skillStep = index;
    const html = audit.skillForm();
    assert.match(html, /data-a="skillStage"/, `${skill} page ${index} must be navigable`);
    assert.match(html, /data-a="askAi"/, `${skill} page ${index} preserves copy actions`);
    assert.match(html, /WHAT WILL HAPPEN/, `${skill} page ${index} preserves explanation`);
    if (index === 2) assert.match(html, /class="guided-review"/, `${skill} review page`);
  }
  assert.equal(audit.ui.skillValues.component, 'config');
}
console.log(`Guided form coverage: ${commands.length} commands, ${catalog.templates.length} templates, ${forms.ai_skills.order.length} skills, ${total} parameter/answer checks; navigation and live preview OK`);
