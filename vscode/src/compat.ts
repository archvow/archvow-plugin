// Which archvow this plugin works with (1.0.0): one range, contract/archvow-range.txt (">=3.4,<4"), stated in the
// README, both Marketplace texts and the header of the tool window, and checked against what is installed. The
// plugin has its own version; the range says which archvow versions it works with. The IntelliJ plugin says the
// same (ArchvowCompat.java, CompatRegression); both are tested on contract/compat/expected.json.

/** A range of archvow versions, ">=3.4,<4": from `low` included to `high` left out. */
export interface ArchvowRange {
  text: string;
  low: number[];
  high: number[];
}

/** ">=3.4,<4" -> {low: [3, 0], high: [4]}; null when it is not that shape. */
export function parseRange(text: string): ArchvowRange | null {
  const m = /^\s*>=\s*(\d+(?:\.\d+)*)\s*,\s*<\s*(\d+(?:\.\d+)*)\s*$/.exec(text);
  if (m === null) return null;
  const numbers = (s: string): number[] => s.split('.').map(Number);
  return { text: text.trim(), low: numbers(m[1]), high: numbers(m[2]) };
}

/** -1, 0 or 1: two versions compared number by number, a missing number being 0. */
export function compareVersions(a: number[], b: number[]): number {
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    const d = (a[i] ?? 0) - (b[i] ?? 0);
    if (d !== 0) return d < 0 ? -1 : 1;
  }
  return 0;
}

function numbers(version: string): number[] | null {
  const m = /(\d+)\.(\d+)\.(\d+)/.exec(version);
  return m === null ? null : [Number(m[1]), Number(m[2]), Number(m[3])];
}

/** Where a version stands against the range. */
export function placeOf(version: string, range: ArchvowRange): 'below' | 'in' | 'above' | 'unknown' {
  const v = numbers(version);
  if (v === null) return 'unknown';
  if (compareVersions(v, range.low) < 0) return 'below';
  return compareVersions(v, range.high) < 0 ? 'in' : 'above';
}

/** The archvow a project pins (`archvow==2.8.0` in its requirements.txt or pyproject.toml, as archvow's init writes
 *  it), with the file it is in; null when it pins none. */
export function projectPin(files: { name: string; text: string }[]): { version: string; file: string } | null {
  for (const file of files) {
    const m = /(?:^|[\s"'[,])archvow\s*==\s*(\d+\.\d+\.\d+)/m.exec(file.text);
    if (m) return { version: m[1], file: file.name };
  }
  return null;
}

/** What the plugin says of the installed archvow, and the archvow its Install button puts ('' = it offers none). */
export interface Compatibility {
  state: 'ok' | 'missing' | 'older' | 'newer';
  /** The sentence for the person ('' when ok). */
  text: string;
  /** The version Install puts: the project's pin when it is in the range, else the one the plugin is tested with;
   *  never one over a pin outside the range. */
  install: string;
}

/**
 * The installed archvow (its version and contract number; null or '' when it did not answer) against the range,
 * the contract the plugin speaks (the same MAJOR, a MINOR at least the plugin's), the archvow the plugin is tested
 * with, and the project's pin.
 */
export function compatibility(installed: { version: string; contract: string } | null, range: ArchvowRange,
  wanted: string, tested: string, pin: { version: string; file: string } | null, fr = false): Compatibility {
  const tr = (en: string, f: string): string => (fr ? f : en);
  const pinFits = pin !== null && placeOf(pin.version, range) === 'in';
  const install = pin === null ? tested : pinFits ? pin.version : '';
  const pinned = pin === null ? ''
    : tr(` This project pins archvow==${pin.version} (${pin.file}).`, ` Ce projet fixe archvow==${pin.version} (${pin.file}).`);
  const upgrade = tr(` Its architects upgrade the project on a branch - pip install archvow==${tested}, then archvow init <package> --force, review and merge (archvow's CHANGELOG says what changes); the plugin installs nothing over the pin.`,
    ` Ses architectes mettent le projet à jour sur une branche - pip install archvow==${tested}, puis archvow init <package> --force, relecture et merge (le CHANGELOG d'archvow dit ce qui change) ; le plugin n'installe rien par-dessus le choix du projet.`);
  if (installed === null || installed.version === '') {
    const what = tr(`archvow is not installed for this project; this plugin works with archvow ${range.text}.`,
      `archvow n'est pas installé pour ce projet ; ce plugin fonctionne avec archvow ${range.text}.`);
    return { state: 'missing', install, text: what + pinned + (install
      ? tr(` Install it: python -m pip install archvow==${install}`, ` L'installer : python -m pip install archvow==${install}`)
      : upgrade) };
  }
  const have = /^\s*(\d+)\.(\d+)\s*$/.exec(installed.contract);
  const need = /^\s*(\d+)\.(\d+)\s*$/.exec(wanted);
  const contract = have && need ? compareVersions([+have[1], +have[2]], [+need[1], +need[2]]) : -1;
  const place = placeOf(installed.version, range);
  const v = installed.version;
  if (place === 'above' || (have && need && +have[1] > +need[1])) {
    return { state: 'newer', install: '', text: tr(`archvow ${v} is newer than this plugin works with (archvow ${range.text}): update the plugin.`,
      `archvow ${v} est plus récent que ce avec quoi ce plugin fonctionne (archvow ${range.text}) : mets le plugin à jour.`) };
  }
  if (place !== 'in' || contract < 0) {
    const what = tr(`archvow ${v} is older than this plugin works with (archvow ${range.text}).`,
      `archvow ${v} est plus ancien que ce avec quoi ce plugin fonctionne (archvow ${range.text}).`);
    return { state: 'older', install, text: what + pinned + (install
      ? tr(` Install archvow ${install}.`, ` Installer archvow ${install}.`) : upgrade) };
  }
  return { state: 'ok', install: '', text: '' };
}

/** The header of the tool window: "archvow 3.4.1 ✓ · this plugin works with archvow >=3.4,<4". */
export function headerLine(version: string, state: Compatibility['state'], range: ArchvowRange, fr = false): string {
  const mark = state === 'ok' ? '✓' : '✗';
  const what = version ? `archvow ${version} ${mark}` : `archvow ${mark}`;
  return fr ? `${what} · ce plugin fonctionne avec archvow ${range.text}` : `${what} · this plugin works with archvow ${range.text}`;
}
