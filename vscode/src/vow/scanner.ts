// The .vow scanner: the same tokens as the IntelliJ plugin's org.archvow.vow.core.VowScanner (Java).
// ../contract/vow-reader/ holds what the Java scanner gives on every sample; test/vowReader.test.ts
// checks this one gives the same. Change both together.

/** The kinds of token a .vow file is cut into. Every character of a file belongs to exactly one token. */
export enum TokenKind {
  /** Spaces (or tabs) at the start of a line. */
  INDENT = 'INDENT',
  /** Spaces inside a line. */
  WHITE_SPACE = 'WHITE_SPACE',
  /** The end of a line. */
  NEWLINE = 'NEWLINE',
  /** A line starting with `#`. */
  COMMENT = 'COMMENT',
  /** `vow` on the first line. */
  HEADER_KEYWORD = 'HEADER_KEYWORD',
  /** The format version on the first line: `1`. */
  HEADER_VERSION = 'HEADER_VERSION',
  /** What the file is: `project`, `component`, `proposals`. */
  HEADER_KIND = 'HEADER_KIND',
  /** The name on the first line: the component's id. */
  HEADER_NAME = 'HEADER_NAME',
  /** The keyword starting a line, one kind per depth (2 spaces a level): 0, 1, 2, 3 and deeper. */
  KEY_0 = 'KEY_0',
  KEY_1 = 'KEY_1',
  KEY_2 = 'KEY_2',
  KEY_3 = 'KEY_3',
  /** Text in double quotes. */
  STRING = 'STRING',
  /** An integer or decimal number. */
  NUMBER = 'NUMBER',
  /** `true`, `false`, `null`. */
  LITERAL = 'LITERAL',
  /** An id: `H1`, `INV2`, `R1`, `clean_orders.H1`. */
  ID = 'ID',
  /** A person or group: `@alice`, `@acme/architects`, or an e-mail: `alice@acme.com`. */
  PERSON = 'PERSON',
  /** A date or a date-time: `2026-10-21`, `2026-10-21T10:00:00+00:00`. */
  DATE = 'DATE',
  /** Any other word. */
  WORD = 'WORD',
  LBRACKET = 'LBRACKET',
  RBRACKET = 'RBRACKET',
  COMMA = 'COMMA',
  /** `{}`: an empty group. */
  EMPTY_GROUP = 'EMPTY_GROUP',
  /** The `=` of `key = <JSON>`. */
  JSON_MARK = 'JSON_MARK',
  /** The JSON after `key = `. */
  JSON = 'JSON',
  /** Characters that cannot be read. */
  BAD = 'BAD',
}

export interface Token {
  kind: TokenKind;
  start: number;
  end: number;
  /** The state at the start of the token: scanning may restart there. */
  state: number;
}

// modes: where the scanner is on its line
const LINE_START = 0;
const VALUE = 1;
const HEADER = 2;
const JSON_REST = 3;
const MODE_MASK = 3;
const HEADER_SEEN = 4; // the first line of the vow was read
const HEADER_WORD_SHIFT = 3; // which word of the first line comes next (0..3)

export const KEY = /^[A-Za-z_][A-Za-z0-9_./\-]*$/;
export const WORD = /^[A-Za-z0-9_@][A-Za-z0-9_@./:+\-]*$/;
export const NUMBER = /^-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?$/;
const DATE = /^[0-9]{4}-[0-9]{2}-[0-9]{2}(?:T[0-9:.]+(?:Z|[+-][0-9]{2}:[0-9]{2})?)?$/;
const ID = /^(?:[a-z][a-z0-9_]*\.)?[A-Z]+[0-9]+$/;
const PERSON = /^(?:@[A-Za-z0-9_.\-]+(?:\/[A-Za-z0-9_.\-]+)*|[A-Za-z0-9_.+\-]+@[A-Za-z0-9\-]+(?:\.[A-Za-z0-9\-]+)+)$/;

function isSpace(c: string): boolean {
  return c === ' ' || c === '\t' || c === '\r';
}

/** `KEY_0` for depth 0 ... `KEY_3` for depth 3 and deeper. */
export function depthKind(depth: number): TokenKind {
  switch (Math.min(depth, 3)) {
    case 0:
      return TokenKind.KEY_0;
    case 1:
      return TokenKind.KEY_1;
    case 2:
      return TokenKind.KEY_2;
    default:
      return TokenKind.KEY_3;
  }
}

/** What kind of value a bare word is. */
export function classify(word: string): TokenKind {
  if (word === 'true' || word === 'false' || word === 'null') {
    return TokenKind.LITERAL;
  }
  if (NUMBER.test(word)) {
    return TokenKind.NUMBER;
  }
  if (DATE.test(word)) {
    return TokenKind.DATE;
  }
  if (PERSON.test(word)) {
    return TokenKind.PERSON;
  }
  if (ID.test(word)) {
    return TokenKind.ID;
  }
  return WORD.test(word) ? TokenKind.WORD : TokenKind.BAD;
}

/**
 * Cuts the text of a .vow file into tokens, one at a time. Never fails: every character belongs to a
 * token, characters it cannot read are BAD. It knows no keyword list: any keyword is a key of its depth.
 */
export class Scanner {
  private buffer = '';
  private limit = 0;
  private tokenStart = 0;
  private tokenEnd = 0;
  private current: TokenKind | null = null;
  private state = 0;
  private nextState = 0;

  /** Starts scanning `buffer` from `start` (exclusive `end`) in `state`. */
  start(buffer: string, start: number, end: number, state: number): void {
    this.buffer = buffer;
    this.limit = end;
    this.tokenEnd = start;
    this.nextState = state;
    this.advance();
  }

  /** The current token, or null at the end. */
  token(): Token | null {
    return this.current === null
      ? null
      : { kind: this.current, start: this.tokenStart, end: this.tokenEnd, state: this.state };
  }

  /** Moves to the next token. */
  advance(): void {
    this.tokenStart = this.tokenEnd;
    this.state = this.nextState;
    if (this.tokenStart >= this.limit) {
      this.current = null;
      return;
    }
    switch (this.state & MODE_MASK) {
      case LINE_START:
        this.lineStart();
        break;
      case HEADER:
        this.header();
        break;
      case JSON_REST:
        this.jsonRest();
        break;
      default:
        this.value();
    }
  }

  private at(i: number): string {
    return this.buffer.charAt(i);
  }

  private lineEnd(from: number): number {
    let i = from;
    while (i < this.limit && this.at(i) !== '\n') {
      i++;
    }
    return i;
  }

  private emit(k: TokenKind, to: number, mode: number, base: number = this.state): void {
    this.current = k;
    this.tokenEnd = to;
    this.nextState = (base & ~MODE_MASK) | mode;
  }

  private lineStart(): void {
    const i = this.tokenStart;
    const c = this.at(i);
    if (c === '\n') {
      this.emit(TokenKind.NEWLINE, i + 1, LINE_START);
      return;
    }
    if (c === ' ' || c === '\t' || c === '\r') {
      let j = i;
      while (j < this.limit && (this.at(j) === ' ' || this.at(j) === '\t' || this.at(j) === '\r')) {
        j++;
      }
      this.emit(TokenKind.INDENT, j, LINE_START);
      return;
    }
    if (c === '#') {
      this.emit(TokenKind.COMMENT, this.lineEnd(i), LINE_START);
      return;
    }
    if ((this.state & HEADER_SEEN) === 0) {
      // the first line of the vow
      const seen = this.state | HEADER_SEEN;
      const j = this.wordEnd(i);
      if (j - i === 3 && this.buffer.substring(i, j) === 'vow') {
        this.current = TokenKind.HEADER_KEYWORD;
        this.tokenEnd = j;
        this.nextState = (seen & ~MODE_MASK & ~(3 << HEADER_WORD_SHIFT)) | HEADER | (1 << HEADER_WORD_SHIFT);
        return;
      }
      this.key(i, seen);
      return;
    }
    this.key(i, this.state);
  }

  private key(i: number, base: number): void {
    const keyKind = this.keyOfDepth(i);
    if (this.at(i) === '"') {
      const close = this.closingQuote(i);
      this.emit(close < 0 ? TokenKind.BAD : keyKind, close < 0 ? this.lineEnd(i) : close + 1, VALUE, base);
      return;
    }
    let j = i;
    while (j < this.limit && !isSpace(this.at(j)) && this.at(j) !== '\n') {
      j++;
    }
    const ok = KEY.test(this.buffer.substring(i, j));
    this.emit(ok ? keyKind : TokenKind.BAD, j, VALUE, base);
  }

  /** The key kind of a keyword starting at `i`: its depth is its column / 2 (2 spaces a level). */
  private keyOfDepth(i: number): TokenKind {
    let column = 0;
    while (i - column - 1 >= 0 && this.at(i - column - 1) !== '\n') {
      column++;
    }
    return depthKind(Math.floor(column / 2));
  }

  private header(): void {
    const i = this.tokenStart;
    const c = this.at(i);
    if (c === '\n') {
      this.emit(TokenKind.NEWLINE, i + 1, LINE_START);
      return;
    }
    if (isSpace(c)) {
      this.emit(TokenKind.WHITE_SPACE, this.spaceEnd(i), HEADER);
      return;
    }
    const word = (this.state >> HEADER_WORD_SHIFT) & 3;
    const j = this.wordEnd(i);
    const kinds = [TokenKind.BAD, TokenKind.HEADER_VERSION, TokenKind.HEADER_KIND, TokenKind.HEADER_NAME];
    this.current = kinds[word]; // BAD: more words than a first line has
    this.tokenEnd = j;
    const next = word === 0 ? 0 : Math.min(word + 1, 4) & 3; // after the name: 0 (BAD from then on)
    this.nextState = (this.state & ~MODE_MASK & ~(3 << HEADER_WORD_SHIFT)) | HEADER | (next << HEADER_WORD_SHIFT);
  }

  private jsonRest(): void {
    const i = this.tokenStart;
    if (this.at(i) === '\n') {
      this.emit(TokenKind.NEWLINE, i + 1, LINE_START);
      return;
    }
    this.emit(TokenKind.JSON, this.lineEnd(i), JSON_REST);
  }

  private value(): void {
    const i = this.tokenStart;
    const c = this.at(i);
    if (c === '\n') {
      this.emit(TokenKind.NEWLINE, i + 1, LINE_START);
    } else if (isSpace(c)) {
      this.emit(TokenKind.WHITE_SPACE, this.spaceEnd(i), VALUE);
    } else if (c === '"') {
      const close = this.closingQuote(i);
      this.emit(TokenKind.STRING, close < 0 ? this.lineEnd(i) : close + 1, VALUE);
    } else if (c === '[') {
      this.emit(TokenKind.LBRACKET, i + 1, VALUE);
    } else if (c === ']') {
      this.emit(TokenKind.RBRACKET, i + 1, VALUE);
    } else if (c === ',') {
      this.emit(TokenKind.COMMA, i + 1, VALUE);
    } else if (c === '{') {
      const pair = i + 1 < this.limit && this.at(i + 1) === '}';
      this.emit(pair ? TokenKind.EMPTY_GROUP : TokenKind.BAD, i + (pair ? 2 : 1), VALUE);
    } else if (c === '=' && (i + 1 >= this.limit || this.at(i + 1) === ' ' || this.at(i + 1) === '\n')) {
      this.emit(TokenKind.JSON_MARK, i + 1, JSON_REST);
    } else {
      let j = i;
      while (
        j < this.limit &&
        !isSpace(this.at(j)) &&
        this.at(j) !== '\n' &&
        this.at(j) !== ',' &&
        this.at(j) !== ']' &&
        this.at(j) !== '"'
      ) {
        j++;
      }
      this.emit(classify(this.buffer.substring(i, j)), Math.max(j, i + 1), VALUE);
    }
  }

  private closingQuote(open: number): number {
    let i = open + 1;
    while (i < this.limit && this.at(i) !== '\n') {
      if (this.at(i) === '\\') {
        i += 2;
        continue;
      }
      if (this.at(i) === '"') {
        return i;
      }
      i++;
    }
    return -1;
  }

  private spaceEnd(i: number): number {
    let j = i;
    while (j < this.limit && isSpace(this.at(j))) {
      j++;
    }
    return j;
  }

  private wordEnd(i: number): number {
    let j = i;
    while (j < this.limit && !isSpace(this.at(j)) && this.at(j) !== '\n') {
      j++;
    }
    return j;
  }
}

/** Every token of `text`, from the start. */
export function tokenize(text: string): Token[] {
  const scanner = new Scanner();
  scanner.start(text, 0, text.length, 0);
  const tokens: Token[] = [];
  for (let t = scanner.token(); t !== null; scanner.advance(), t = scanner.token()) {
    tokens.push(t);
  }
  return tokens;
}
