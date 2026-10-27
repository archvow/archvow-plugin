// The words of a vow that name something else, as org.archvow.vow.core.VowLinks (Java): a component in
// `uses` or `components`, a hazard in `prevents`, the Python module of `module`.
import { Line } from './document';
import { items, unquote } from './values';

export type LinkKind = 'COMPONENT' | 'HAZARD' | 'MODULE';

export interface Link {
  kind: LinkKind;
  target: string;
  start: number;
  end: number;
}

/** The links of one line of a vow (offsets in the file). */
export function linksOf(line: Line): Link[] {
  const links: Link[] = [];
  if (line.key === null || line.value === null) return links;
  const kind: LinkKind | null =
    line.key === 'uses' || line.key === 'components' ? 'COMPONENT'
      : line.key === 'prevents' ? 'HAZARD'
        : line.key === 'module' ? 'MODULE'
          : null;
  if (kind === null) return links;
  if (kind === 'MODULE') {
    const word = line.word();
    if (word !== null) links.push({ kind, target: word, start: line.valueStart, end: line.valueStart + word.length });
    return links;
  }
  for (const item of items(line.value)) {
    links.push({ kind, target: unquote(item.text), start: line.valueStart + item.start, end: line.valueStart + item.end });
  }
  return links;
}

/** The files a module may live in, relative to the project root (the folder holding .archvow/). */
export function moduleFiles(module: string): string[] {
  const path = module.split('.').join('/');
  return ['src/' + path + '.py', path + '.py', 'src/' + path + '/__init__.py', path + '/__init__.py'];
}

/** For `clean_orders.H1`: `clean_orders`; for `H1`: null (the same vow). */
export function hazardComponent(id: string): string | null {
  const dot = id.lastIndexOf('.');
  return dot < 0 ? null : id.substring(0, dot);
}

/** For `clean_orders.H1` or `H1`: `H1`. */
export function hazardShortId(id: string): string {
  return id.substring(id.lastIndexOf('.') + 1);
}
