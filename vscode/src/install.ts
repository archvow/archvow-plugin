// Installing archvow, guided (0.12.0): never on its own, never at startup. When archvow cannot start, or
// does not speak the plugin's contract, the page offers "Install archvow <version>"; the pip command
// is shown and runs only after "Confirm and run", with no shell, in the project folder. The same rules as
// the IntelliJ plugin (ArchvowInstall).
import * as path from 'node:path';
import { linuxPath, wslArgument, wslProgram } from './cli';

/** Where pip takes archvow from (setting archvow.installFrom). */
export interface InstallSource {
  /** pip: pip's own configuration (pip.conf, PIP_INDEX_URL); index: an index URL; wheel: a .whl file or a folder of wheels. */
  kind: 'pip' | 'index' | 'wheel';
  value: string;
}

/** archvow.installFrom: '' = pip's own configuration, an http(s) URL = an index, anything else = a wheel file or folder. */
export function installSource(setting: string | undefined): InstallSource {
  const value = (setting ?? '').trim();
  if (!value) return { kind: 'pip', value: '' };
  if (/^https?:\/\//i.test(value)) return { kind: 'index', value };
  return { kind: 'wheel', value };
}

/** The Pythons of the project's own virtual environments, in the order they are tried. */
export function venvPythons(root: string, windows: boolean): string[] {
  const join = windows ? path.win32.join : path.posix.join;
  const names = ['.venv', 'venv'];
  const unix = names.map((n) => join(root, n, 'bin', 'python'));
  const win = names.map((n) => join(root, n, 'Scripts', 'python.exe'));
  return windows ? [...win, ...unix] : unix;
}

/** "python" or "python3" alone: a name, not an interpreter of the project. */
function bareName(python: string): boolean {
  return /^(python3?|py)(\.exe)?$/i.test(python.trim());
}

/**
 * The Python archvow is installed into: the project's venv first, then the interpreter the IDE uses for the
 * project, then archvow.python. Never a bare system "python" chosen for the person: null means ask them
 * (Configure Python).
 */
export function installPython(root: string, interpreter: string, pythonSetting: string, exists: (file: string) => boolean,
  windows = process.platform === 'win32'): string | null {
  const venv = venvPythons(root, windows).find((p) => exists(p));
  if (venv) return venv;
  for (const candidate of [interpreter, pythonSetting]) {
    const p = (candidate || '').trim();
    if (p && !bareName(p)) return p;
  }
  return null;
}

/** "1.23.0" -> archvow==1.23.0: the archvow the plugin is tested with, exactly (any archvow of its contract is
 *  accepted once installed, but Install puts the tested one). */
export function requirement(version: string): string {
  return `archvow==${version}`;
}

/** pip's arguments for one source. */
export function pipArgs(version: string, source: InstallSource): string[] {
  const wanted = requirement(version);
  if (source.kind === 'index') return ['-m', 'pip', 'install', '--index-url', source.value, wanted];
  if (source.kind === 'wheel') {
    return source.value.toLowerCase().endsWith('.whl')
      ? ['-m', 'pip', 'install', source.value]
      : ['-m', 'pip', 'install', '--no-index', '--find-links', source.value, wanted];
  }
  return ['-m', 'pip', 'install', wanted];
}

/** The program and arguments that install archvow; a Python in WSL runs through wsl.exe, as archvow does. */
export function installCommand(python: string, root: string, version: string, source: InstallSource,
  windows = process.platform === 'win32'): { program: string; args: string[] } {
  const args = pipArgs(version, source);
  const wsl = wslProgram(python, windows);
  if (wsl) {
    const folder = linuxPath(root);
    if (folder === null) throw new Error(`WSL cannot reach the project folder: ${root}`);
    return { program: 'wsl.exe', args: [...(wsl.distro ? ['-d', wsl.distro] : []), '--cd', folder, '--exec', wsl.path, ...args.map(wslArgument)] };
  }
  return { program: python, args };
}

/** The command as a person reads it in the confirmation. */
export function displayCommand(command: { program: string; args: string[] }): string {
  return [command.program, ...command.args].map((a) => (/^[A-Za-z0-9_@%+=:,./\\*-]+$/.test(a) ? a : JSON.stringify(a))).join(' ');
}

/** Why the install card shows: archvow missing, or an archvow that does not speak the plugin's contract. */
export type InstallReason = 'missing' | 'version' | '';

export interface InstallOffer {
  reason: InstallReason;
  /** The archvow the plugin is tested with ("1.23.0"): what Install puts. */
  version: string;
  /** The Python it goes into ('' = none found: the person chooses one). */
  python: string;
  /** The command, as shown ('' when there is no Python). */
  command: string;
  /** Where from, in words. */
  source: string;
}

export function offer(reason: InstallReason, version: string, python: string | null, root: string, source: InstallSource,
  windows = process.platform === 'win32'): InstallOffer {
  let command = '';
  if (python) {
    try {
      command = displayCommand(installCommand(python, root, version, source, windows));
    } catch (error) {
      command = '';
      python = null;
      void error;
    }
  }
  const where = source.kind === 'pip' ? "pip's configuration" : source.kind === 'index' ? source.value : source.value;
  return { reason, version, python: python ?? '', command, source: where };
}
