/**
 * After `archvow init` (0.26.1): when it initialized another folder (Folder, --dir), that folder is the project to
 * open - as IntelliJ's ArchvowInitTarget.
 */
import * as path from 'path';

/** The folder init initialized when it is not the workspace's own; null otherwise (nothing to open). */
export function folderToOpen(args: string[], root: string, platform: typeof path = path): string | null {
  if (args[0] !== 'init') return null;
  const at = args.indexOf('--dir');
  if (at < 0 || at + 1 >= args.length || !args[at + 1].trim()) return null;
  const base = root ? platform.resolve(root) : '';
  const dir = platform.resolve(base || '.', args[at + 1].trim());
  const same = (a: string, b: string): boolean => platform.relative(a, b) === '';
  return base && same(dir, base) ? null : dir;
}
