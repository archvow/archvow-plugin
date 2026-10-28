// The briefs folders of archvow 1.17 (0.12.0): the init answer `briefs` lists the folders the spec is drawn
// from with --from (default `briefs`; a Spec Kit `specs` folder, say). The plugin offers their files and
// headings in the --from field, opens "Drawn from" at its heading, and turns archvow's refusal of a file
// outside them into the architect's init --force form, prefilled. archvow decides; the plugin only reads.
// The same rules as archvow's briefs.py and IntelliJ's ArchvowBriefs (BriefsRegression).
import * as fs from 'node:fs';
import * as path from 'node:path';

type Json = Record<string, unknown>;

/** Markdown or text: the files archvow reads as briefs. */
export const EXTENSIONS = ['.md', '.txt'];
const HEADING = /^(#{1,6})\s+(.+?)\s*#*\s*$/;

export interface Section { slug: string; title: string; line: number }
export interface BriefFile { file: string; sections: Section[] }
export interface Briefs { folders: string[]; files: BriefFile[] }

/** The project's briefs folders (show --json init_answers.briefs; `briefs` when unset). */
export function folders(spec: Json | null): string[] {
  const answers = (spec?.init_answers ?? {}) as Json;
  const value = answers.briefs;
  if (value === undefined || value === null) return ['briefs'];
  const items = Array.isArray(value) ? value.map(String) : String(value).split(',');
  const found = items.map((f) => f.trim().replace(/^\/+|\/+$/g, '')).filter((f) => f);
  return [...new Set(found)];
}

/** A heading as its anchor, as archvow: lower case, no accents, words joined by '-'. */
export function slug(heading: string): string {
  const ascii = heading.normalize('NFKD').replace(/[^\x00-\x7f]/g, '');
  return ascii.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
}

/** The headings of a Markdown text as anchors, repeated ones as -2, -3... (as GitLab and GitHub). */
export function sections(text: string): Section[] {
  const out: Section[] = [];
  const seen = new Set<string>();
  text.split(/\r?\n/).forEach((line, index) => {
    const m = HEADING.exec(line);
    if (!m) return;
    const key = slug(m[2]) || 'section';
    let unique = key;
    for (let k = 2; seen.has(unique); k++) unique = `${key}-${k}`;
    seen.add(unique);
    out.push({ slug: unique, title: m[2], line: index + 1 });
  });
  return out;
}

/** The declared folder a file is in, or ''. */
export function folderOf(file: string, declared: string[]): string {
  const f = file.replace(/\\/g, '/');
  return declared.find((d) => f === d || f.startsWith(d + '/')) ?? '';
}

/** Every Markdown or text file of the declared folders, with its headings (Markdown only). */
export function readBriefs(root: string, spec: Json | null, limit = 500): Briefs {
  const declared = folders(spec);
  const files: BriefFile[] = [];
  const walk = (dir: string): void => {
    let entries: fs.Dirent[];
    try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch { return; }
    for (const e of entries.sort((a, b) => a.name.localeCompare(b.name))) {
      if (files.length >= limit) return;
      const full = path.join(dir, e.name);
      if (e.isDirectory()) walk(full);
      else if (e.isFile() && EXTENSIONS.includes(path.extname(e.name))) {
        const file = path.relative(root, full).split(path.sep).join('/');
        let text = '';
        if (e.name.endsWith('.md')) try { text = fs.readFileSync(full, 'utf8'); } catch { text = ''; }
        files.push({ file, sections: sections(text) });
      }
    }
  };
  for (const folder of declared) walk(path.join(root, folder));
  files.sort((a, b) => a.file.localeCompare(b.file));
  return { folders: declared, files };
}

/** What the --from field offers: each file, then each of its headings as file#section. */
export function choices(briefs: Briefs): string[] {
  return briefs.files.flatMap((f) => [f.file, ...f.sections.map((s) => `${f.file}#${s.slug}`)]);
}

/** "specs/001-orders/plan.md#paying-an-invoice" -> the file and the 1-based line of that heading (1 without). */
export function locate(briefs: Briefs, ref: string): { file: string; line: number } {
  const [file, section] = ref.split('#', 2);
  const found = briefs.files.find((f) => f.file === file);
  const line = section && found ? found.sections.find((s) => s.slug === section)?.line ?? 1 : 1;
  return { file, line };
}

/** The `source` of a show --json view ({file, section}) as a reference: "file#section", "file", or ''. */
export function drawnFrom(view: Json | null): string {
  const source = view?.source;
  if (!source || typeof source !== 'object') return '';
  const s = source as Json;
  const file = typeof s.file === 'string' ? s.file : '';
  const section = typeof s.section === 'string' ? s.section : '';
  return file ? (section ? `${file}#${section}` : file) : '';
}

/**
 * archvow's way to add a briefs folder, in its refusal of --from or in ARCH039:
 * "archvow init <package> --force -a briefs=a,b" -> the init form's values (shown, then confirmed).
 */
export function addFolder(message: string): { package: string; answer: string } | null {
  const m = /archvow init (\S+) --force -a briefs=(\S+)/.exec(message ?? '');
  return m ? { package: m[1], answer: `briefs=${m[2]}` } : null;
}
