// Where archvow 2.0 keeps a project's spec (contract 2.0), with no VS Code in it, so it is tested on ../contract:
//   .archvow/project.vow                                  the project
//   .archvow/features/<feature>/<feature>.vow             a feature: what the product does
//   .archvow/features/<feature>/components/<id>.vow       a component
//   .archvow/features/<feature>/proposals.vow             the AI's proposals for the feature's components
//   .archvow/doc/                                         archvow doc writes it (ignored by Git)
import * as fs from 'node:fs';
import * as path from 'node:path';

export const PROJECT_VOW = '.archvow/project.vow';
export const DOC_FOLDER = '.archvow/doc';

/** A project-relative path, with forward slashes. */
const posix = (rel: string): string => rel.replace(/\\/g, '/');

/** The component a project-relative path is the vow of, or null. */
export function componentOfPath(rel: string): string | null {
  const m = /^\.archvow\/features\/[^/]+\/components\/([^/]+)\.vow$/.exec(posix(rel));
  return m ? m[1] : null;
}

/** The feature a project-relative path belongs to (its vow, a component's vow, its proposals), or null. */
export function featureOfPath(rel: string): string | null {
  const m = /^\.archvow\/features\/([^/]+)\//.exec(posix(rel));
  return m ? m[1] : null;
}

/** The vow of a component in `root`, wherever its feature is; null when it has none. */
export function componentVow(root: string, id: string): string | null {
  const features = path.join(root, '.archvow', 'features');
  let names: string[];
  try {
    names = fs.readdirSync(features).sort();
  } catch {
    return null;
  }
  for (const feature of names) {
    const file = path.join(features, feature, 'components', id + '.vow');
    if (fs.existsSync(file)) return file;
  }
  return null;
}

/** The page `archvow doc <feature>` writes (`project` for the whole project). */
export function docPage(root: string, name: string): string {
  return path.join(root, '.archvow', 'doc', name + '.md');
}

/** What a vow is, from its path (absolute or relative): the banner over it says which command changes it. */
export function vowKind(file: string): { kind: 'project' | 'feature' | 'component' | 'proposals' | 'other'; name: string } {
  const parts = file.split(/[\\/]/);
  const name = parts[parts.length - 1];
  const stem = name.replace(/\.vow$/, '');
  if (name === 'proposals.vow') return { kind: 'proposals', name: parts[parts.length - 2] ?? '' };
  if (parts.length >= 2 && parts[parts.length - 2] === 'components') return { kind: 'component', name: stem };
  if (parts.length >= 3 && parts[parts.length - 3] === 'features' && parts[parts.length - 2] === stem) return { kind: 'feature', name: stem };
  if (name === 'project.vow') return { kind: 'project', name: '' };
  return { kind: 'other', name: stem };
}
