// A .vow file read as a tree of lines, with the problems that keep archvow from reading it: the same
// reader as org.archvow.vow.core.VowDocument (Java), with the same messages. Tested against it.
import { KEY, WORD } from './scanner';
import { check, javaStripTrailing, unquote } from './values';

/** The version of the format this reader knows. */
export const FORMAT_VERSION = 1;

/** One line of the file. */
export class Line {
  key: string | null = null; // the keyword (unquoted), null for blank, comment and header lines
  keyStart = 0;
  keyEnd = 0;
  value: string | null = null; // the text after the keyword, null when there is none
  valueStart = 0;
  readonly children: Line[] = [];
  parent: Line | null = null;

  constructor(
    readonly number: number, // 1-based
    readonly start: number, // offset of the line's first character
    readonly end: number, // offset of its end (before the newline)
    readonly indent: number, // spaces before the keyword
    readonly blank: boolean,
    readonly comment: boolean,
    readonly header: boolean,
  ) {}

  /** The end offset of this line and everything under it. */
  blockEnd(): number {
    return this.children.length === 0 ? this.end : this.children[this.children.length - 1].blockEnd();
  }

  /** The value as one word (an item's id, a name), or null. */
  word(): string | null {
    if (this.value === null) return null;
    const v = javaTrim(this.value);
    return WORD.test(v) ? v : null;
  }
}

/** Something wrong in the file: an error keeps archvow from reading it; a warning is a likely mistake. */
export interface Problem {
  line: number;
  start: number;
  end: number;
  message: string;
  warning: boolean;
}

/** String.trim of Java: removes the characters up to the space. */
function javaTrim(s: string): string {
  let start = 0;
  let end = s.length;
  while (start < end && s.charCodeAt(start) <= 0x20) start++;
  while (end > start && s.charCodeAt(end - 1) <= 0x20) end--;
  return s.substring(start, end);
}

export class VowDocument {
  readonly lines: Line[] = [];
  /** The lines at the top level (not indented), in order. */
  readonly roots: Line[] = [];
  readonly problems: Problem[] = [];
  /** `project`, `component`, `proposals`... (null when the first line is missing). */
  kind: string | null = null;
  /** The name on the first line (a component's id), or null. */
  name: string | null = null;
  version: string | null = null;

  private constructor(private readonly text: string) {}

  /** Reads a whole file. Never throws: what cannot be read becomes a problem. */
  static parse(text: string): VowDocument {
    const doc = new VowDocument(text);
    doc.readLines();
    doc.buildTree();
    return doc;
  }

  private problem(line: number, start: number, end: number, message: string, warning = false): void {
    this.problems.push({ line, start, end, message, warning });
  }

  private readLines(): void {
    const text = this.text;
    const length = text.length;
    let start = 0;
    let number = 1;
    let headerSeen = false;
    while (start <= length) {
      let end = start;
      while (end < length && text.charAt(end) !== '\n') end++;
      const lineEnd = end > start && text.charAt(end - 1) === '\r' ? end - 1 : end;
      let i = start;
      while (i < lineEnd && (text.charAt(i) === ' ' || text.charAt(i) === '\t')) i++;
      const blank = i === lineEnd;
      const comment = !blank && text.charAt(i) === '#';
      const header = !blank && !comment && !headerSeen;
      const line = new Line(number, start, lineEnd, i - start, blank, comment, header);
      if (!blank && !comment) {
        if (!header && text.substring(start, i).includes('\t')) {
          this.problem(number, start, i, 'a tab in the indentation - two spaces per level');
        }
        if (header) {
          headerSeen = true;
          this.readHeader(line, i);
        } else {
          this.readKeyAndValue(line, i);
        }
      }
      this.lines.push(line);
      if (end >= length) break;
      start = end + 1;
      number++;
    }
    if (!headerSeen) {
      this.problem(1, 0, Math.min(length, 1),
        'empty - a vow starts with `vow ' + FORMAT_VERSION + ' project|component <id>|proposals`');
    }
  }

  private readHeader(line: Line, from: number): void {
    const words = javaTrim(this.text.substring(from, line.end)).split(/[ \t\n\x0B\f\r]+/);
    if (words.length < 3 || words.length > 4 || words[0] !== 'vow') {
      this.problem(line.number, from, line.end, 'not a vow - the first line is `vow ' + FORMAT_VERSION
        + ' <kind> [<name>]`, e.g. `vow 1 component clean_orders`');
      return;
    }
    this.version = words[1];
    this.kind = words[2];
    this.name = words.length === 4 ? words[3] : null;
    if (this.version !== String(FORMAT_VERSION)) {
      this.problem(line.number, from, line.end, 'format version ' + this.version
        + ' - this editor support reads version ' + FORMAT_VERSION + ' (update the plugin)');
    }
  }

  private readKeyAndValue(line: Line, i: number): void {
    const text = this.text;
    if (line.indent % 2 !== 0) {
      this.problem(line.number, line.start, i, 'indented by ' + line.indent + ' spaces - two spaces per level');
    }
    let keyEnd: number;
    if (text.charAt(i) === '"') {
      const close = this.closingQuote(i, line.end);
      if (close < 0) {
        this.problem(line.number, i, line.end, 'a key in quotes with no closing quote');
        return;
      }
      line.key = unquote(text.substring(i, close + 1));
      keyEnd = close + 1;
    } else {
      keyEnd = i;
      while (keyEnd < line.end && text.charAt(keyEnd) !== ' ' && text.charAt(keyEnd) !== '\t') keyEnd++;
      const key = text.substring(i, keyEnd);
      if (!KEY.test(key)) {
        this.problem(line.number, i, keyEnd, '`' + key + '` is not a keyword');
        return;
      }
      line.key = key;
    }
    line.keyStart = i;
    line.keyEnd = keyEnd;
    if (keyEnd < line.end && text.charAt(keyEnd) !== ' ') {
      this.problem(line.number, keyEnd, line.end, 'a space is needed after `' + line.key + '`');
      return;
    }
    let v = keyEnd;
    while (v < line.end && text.charAt(v) === ' ') v++;
    if (v < line.end) {
      line.value = javaStripTrailing(text.substring(v, line.end));
      line.valueStart = v;
      const error = check(line.value);
      if (error !== null) {
        this.problem(line.number, v, line.end, error);
      }
    }
  }

  private closingQuote(open: number, limit: number): number {
    let i = open + 1;
    while (i < limit) {
      const c = this.text.charAt(i);
      if (c === '\\') {
        i += 2;
        continue;
      }
      if (c === '"') return i;
      i++;
    }
    return -1;
  }

  private buildTree(): void {
    const stack: Line[] = []; // the open blocks, outermost first
    for (const line of this.lines) {
      if (line.key === null) continue; // blank, comment, header or unreadable
      while (stack.length > 0 && stack[stack.length - 1].indent >= line.indent) stack.pop();
      const parent = stack.length === 0 ? null : stack[stack.length - 1];
      const expected = parent === null ? 0 : parent.indent + 2;
      if (line.indent > expected) {
        this.problem(line.number, line.start, line.keyStart,
          'indented more than the line above allows (' + expected + ' spaces here)');
      }
      line.parent = parent;
      (parent === null ? this.roots : parent.children).push(line);
      stack.push(line);
    }
    this.checkBlocks(this.roots);
  }

  private checkBlocks(siblings: Line[]): void {
    const seen = new Map<string, Line>();
    for (const line of siblings) {
      const block = line.children.length > 0;
      const key = line.key as string;
      if (line.value === null && !block) {
        this.problem(line.number, line.keyStart, line.keyEnd, '`' + key
          + '` needs a value (or lines under it, indented by two spaces)', true);
      }
      if (line.value !== null && block && line.word() === null) {
        const first = line.children[0];
        this.problem(first.number, first.start, first.end, '`' + key
          + '` has a value: nothing may be indented under it (only an item\'s id or name may'
          + ' stand on its line, e.g. `hazard H1`)');
      }
      if (!block) {
        // items (`decision 1`, `hazard H1`...) repeat; a plain key appears once
        const before = seen.get(key);
        if (before === undefined) {
          seen.set(key, line);
        } else {
          this.problem(line.number, line.keyStart, line.keyEnd, '`' + key + '` appears twice (line '
            + before.number + ')', true);
        }
      }
      this.checkBlocks(line.children);
    }
  }
}

