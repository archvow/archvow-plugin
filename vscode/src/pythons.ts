/**
 * The Pythons of this computer (0.26.1), for "Choose the Python for archvow": where each is, its version and the
 * archvow it has - as IntelliJ's ArchvowPythons. Pure functions, but probe (it runs each Python).
 */
import * as cp from 'child_process';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { tr } from './i18n';
import { wslProgram } from './cli';

export type Where = 'project' | 'venv' | 'ide' | 'system' | 'path';
export interface Found { path: string; where: Where; version: string; archvow: string }

/** Prints "3.12.8|2.6.2" (no archvow: "3.12.8|"); run from an empty folder, nothing there can be imported. */
export const PROBE = "import sys\ntry:\n    import importlib.metadata as m\n    v = m.version('archvow')\nexcept Exception:\n    v = ''\n"
  + "print('%d.%d.%d|%s' % (sys.version_info[0], sys.version_info[1], sys.version_info[2], v))";

/**
 * Where to look, in order, only what exists, each once (`key`: the same Python by two names is one): the project's
 * interpreter, the project's .venv, the Python extension's other environments, then the usual places of a Python.
 */
export function places(projectPython: string, root: string, known: string[], home: string, windows: boolean,
  pathVariable: string, children: (dir: string) => string[], exists: (p: string) => boolean, key: (p: string) => string): [string, Where][] {
  const out = new Map<string, [string, Where]>();
  const add = (p: string, where: Where): void => {
    const file = (p || '').trim();
    if (!file) return;
    const wsl = wslProgram(file, windows) !== null;
    if (!wsl && !exists(file)) return;
    const k = wsl ? file : key(file);
    if (!out.has(k)) out.set(k, [file, where]);
  };
  add(projectPython, 'project');
  const sep = windows ? '\\' : '/';
  const base = root.replace(/[\\/]+$/, '');
  if (base) for (const name of ['.venv', 'venv']) {
    if (windows) add([base, name, 'Scripts', 'python.exe'].join(sep), 'venv');
    add([base, name, 'bin', 'python'].join(sep), 'venv');
  }
  for (const k of known) add(k, 'ide');
  const usual: string[] = [];
  const newest = (names: string[]): string[] => [...names].sort().reverse();
  if (windows) {
    const local = process.env.LOCALAPPDATA;
    if (local) for (const d of newest(children(`${local}\\Programs\\Python`))) usual.push(`${local}\\Programs\\Python\\${d}\\python.exe`);
    for (const d of newest(children('C:\\'))) if (/^python3\d*$/i.test(d)) usual.push(`C:\\${d}\\python.exe`);
  } else {
    for (const dir of ['/opt/homebrew/bin', '/usr/local/bin', '/usr/bin']) {
      usual.push(`${dir}/python3`);
      for (let minor = 15; minor >= 9; minor--) usual.push(`${dir}/python3.${minor}`);
    }
    if (home) {
      for (const v of newest(children(`${home}/.pyenv/versions`))) usual.push(`${home}/.pyenv/versions/${v}/bin/python`);
      for (const conda of ['miniconda3', 'anaconda3', 'miniforge3']) usual.push(`${home}/${conda}/bin/python`);
    }
    usual.push('/opt/anaconda3/bin/python');
  }
  for (const u of usual) add(u, 'system');
  for (const dir of (pathVariable || '').split(windows ? ';' : ':')) {
    if (!dir) continue;
    for (const name of windows ? ['python.exe'] : ['python3', 'python']) add(dir + sep + name, 'path');
  }
  return [...out.values()];
}

/** What PROBE printed, read; null when it did not answer as a Python does. */
export function parse(file: string, where: Where, output: string): Found | null {
  for (const line of (output || '').trim().split(/\r?\n/)) {
    const m = /^(\d+\.\d+\.\d+)\|(.*)$/.exec(line.trim());
    if (m) return { path: file, where, version: m[1], archvow: m[2].trim() };
  }
  return null;
}

/** archvow 2.x needs Python 3.11 or later. */
export function recentEnough(f: Found): boolean {
  const [major, minor] = f.version.split('.').map(Number);
  return major > 3 || (major === 3 && minor >= 11);
}

/** The ones with archvow first; the order of places kept within each. */
export function ordered(found: Found[]): Found[] {
  return [...found.filter((f) => f.archvow), ...found.filter((f) => !f.archvow)];
}

export function whereText(where: string, fr: boolean): string {
  switch (where) {
    case 'project': return tr(fr, "the project's interpreter", "interpréteur du projet");
    case 'venv': return tr(fr, "the project's .venv", '.venv du projet');
    case 'ide': return tr(fr, 'an environment of the Python extension', "un environnement de l'extension Python");
    case 'path': return tr(fr, 'on the PATH', 'dans le PATH');
    default: return tr(fr, 'installed on this computer', 'installé sur cet ordinateur');
  }
}

/** "archvow 2.6.2" / "no archvow" / "too old for archvow (3.11+)". */
export function archvowText(f: Found, fr: boolean): string {
  if (f.archvow) return `archvow ${f.archvow}`;
  if (!f.version) return tr(fr, 'not checked', 'non vérifié');
  if (!recentEnough(f)) return tr(fr, 'too old for archvow (3.11+)', 'trop ancien pour archvow (3.11+)');
  return tr(fr, 'no archvow', 'sans archvow');
}

/** "archvow 2.6.2 with /usr/local/bin/python3.12 — the project's Python interpreter" (cli.ts's Candidate.source). */
export function runsText(fr: boolean, program: string, source: string, version: string): string {
  const what = version ? `archvow ${version}` : 'archvow';
  const where = ({
    python: tr(fr, 'the Python chosen for archvow (archvow.python)', 'le Python choisi pour archvow (archvow.python)'),
    ide: tr(fr, "the project's Python interpreter", "l'interpréteur Python du projet"),
    venv: tr(fr, "the project's .venv", 'le .venv du projet'),
    found: tr(fr, 'where pip or pipx installed it', "là où pip ou pipx l'a installé"),
  } as Record<string, string>)[source] ?? tr(fr, 'the archvow executable (archvow.executable)', "l'exécutable archvow (archvow.executable)");
  return tr(fr, `${what} with ${program} — ${where}`, `${what} avec ${program} — ${where}`);
}

/** How Automatic finds archvow, in order. */
export function automaticOrder(fr: boolean): string {
  return tr(fr, "Automatic runs the first Python that has archvow: 1. the project's Python interpreter (the Python extension's), "
    + "2. the project's .venv, 3. archvow on the PATH, 4. where pip or pipx install archvow.",
  "Automatique lance le premier Python qui a archvow : 1. l'interpréteur Python du projet (celui de l'extension Python), "
    + "2. le .venv du projet, 3. archvow dans le PATH, 4. là où pip ou pipx installent archvow.");
}

/** The same Python by two names is one; a venv's Python is its own, even as a link to its base Python. */
export function sameKey(file: string): string {
  if (fs.existsSync(path.join(path.dirname(path.dirname(file)), 'pyvenv.cfg'))) return path.resolve(file);
  try { return fs.realpathSync(file); } catch { return file; }
}

/** Runs PROBE with each Python (5 s each); a Python in WSL is listed, not run. */
export async function probe(list: [string, Where][], windows = process.platform === 'win32'): Promise<Found[]> {
  const empty = fs.mkdtempSync(path.join(os.tmpdir(), 'archvow-python-'));
  const out: Found[] = [];
  try {
    for (const [file, where] of list) {
      if (wslProgram(file, windows) !== null) { out.push({ path: file, where, version: '', archvow: '' }); continue; }
      const said = await new Promise<string | null>((resolve) => {
        cp.execFile(file, ['-c', PROBE], { cwd: empty, timeout: 5000, windowsHide: true }, (error, stdout) => resolve(error ? null : stdout));
      });
      const f = said === null ? null : parse(file, where, said);
      if (f !== null) out.push(f);
    }
  } finally {
    fs.rmSync(empty, { recursive: true, force: true });
  }
  return ordered(out);
}

/** places() on this computer. */
export function placesHere(projectPython: string, root: string, known: string[]): [string, Where][] {
  const windows = process.platform === 'win32';
  return places(projectPython, root, known, os.homedir(), windows, process.env.PATH ?? '',
    (dir) => { try { return fs.readdirSync(dir); } catch { return []; } },
    (p) => { try { return fs.statSync(p).isFile(); } catch { return false; } }, sameKey);
}
