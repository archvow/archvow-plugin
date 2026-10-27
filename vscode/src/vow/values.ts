// The values of a .vow line, as org.archvow.vow.core.VowValues and VowJson (Java). Same messages.
import { classify, NUMBER, TokenKind } from './scanner';

/** One item of a list value, with its offsets inside the value. */
export interface Item {
  text: string;
  start: number;
  end: number;
}

/** Character.isWhitespace of Java: what Java's isBlank/stripTrailing remove. */
export function isJavaWhitespace(c: string): boolean {
  const code = c.charCodeAt(0);
  if (code === 0x09 || code === 0x0a || code === 0x0b || code === 0x0c || code === 0x0d) return true;
  if (code >= 0x1c && code <= 0x20) return true;
  if (code === 0xa0 || code === 0x2007 || code === 0x202f) return false; // no-break spaces
  return code === 0x1680 || (code >= 0x2000 && code <= 0x200a) || code === 0x2028 || code === 0x2029
    || code === 0x205f || code === 0x3000;
}

export function javaIsBlank(s: string): boolean {
  for (const c of s) {
    if (!isJavaWhitespace(c)) return false;
  }
  return true;
}

export function javaStripTrailing(s: string): string {
  let end = s.length;
  while (end > 0 && isJavaWhitespace(s.charAt(end - 1))) end--;
  return s.substring(0, end);
}

/**
 * What is wrong with a value, or null when archvow reads it: a word, "text", a number,
 * true/false/null, [], {}, [a, "b c", 1] or `= <JSON>`.
 */
export function check(value: string): string | null {
  if (value.startsWith('= ') || value === '=') {
    if (value.length <= 2) {
      return '`=` needs JSON after it';
    }
    const error = checkJson(value.substring(2));
    return error === null ? null : '`= ...` is not valid JSON (' + error + ')';
  }
  if (value === '[]' || value === '{}') {
    return null;
  }
  if (value.startsWith('[')) {
    if (!value.endsWith(']')) {
      return 'a list starts with [ and ends with ]';
    }
    const items: Item[] = [];
    const error = split(value.substring(1, value.length - 1), 1, items);
    if (error !== null) {
      return error;
    }
    for (const item of items) {
      const e = scalar(item.text);
      if (e !== null) {
        return e;
      }
    }
    return null;
  }
  return scalar(value);
}

/** The items of a list value (`[a, b]`), with offsets in the value; empty for any other value. */
export function items(value: string | null): Item[] {
  const out: Item[] = [];
  if (value !== null && value.startsWith('[') && value.endsWith(']') && value.length > 2) {
    split(value.substring(1, value.length - 1), 1, out);
  }
  return out;
}

function split(text: string, offset: number, out: Item[]): string | null {
  let i = 0;
  const n = text.length;
  while (i < n) {
    while (i < n && text.charAt(i) === ' ') i++;
    if (i >= n) break;
    const start = i;
    if (text.charAt(i) === '"') {
      const close = closingQuote(text, i);
      if (close < 0) {
        return 'text in quotes with no closing quote';
      }
      i = close + 1;
    } else {
      while (i < n && text.charAt(i) !== ',' && text.charAt(i) !== ' ') i++;
    }
    out.push({ text: text.substring(start, i), start: start + offset, end: i + offset });
    while (i < n && text.charAt(i) === ' ') i++;
    if (i < n) {
      if (text.charAt(i) !== ',') {
        return 'list values are separated by commas';
      }
      i++;
      if (javaIsBlank(text.substring(i))) {
        return 'a comma with no value after it';
      }
    }
  }
  return null;
}

function scalar(text: string): string | null {
  if (text.startsWith('"')) {
    const close = closingQuote(text, 0);
    if (close < 0 || close !== text.length - 1) {
      return 'text in quotes must be the whole value (one closing quote, at the end)';
    }
    return null;
  }
  return classify(text) === TokenKind.BAD ? '`' + text + '`: put text with spaces or signs in double quotes' : null;
}

function closingQuote(text: string, open: number): number {
  let i = open + 1;
  while (i < text.length) {
    const c = text.charAt(i);
    if (c === '\\') {
      i += 2;
      continue;
    }
    if (c === '"') return i;
    i++;
  }
  return -1;
}

/** The text of a quoted value or key, without its quotes and escapes. */
export function unquote(quoted: string): string {
  if (quoted.length < 2 || !quoted.startsWith('"') || !quoted.endsWith('"')) {
    return quoted;
  }
  let out = '';
  for (let i = 1; i < quoted.length - 1; i++) {
    const c = quoted.charAt(i);
    if (c === '\\' && i + 1 < quoted.length - 1) {
      const e = quoted.charAt(++i);
      switch (e) {
        case 'n':
          out += '\n';
          break;
        case 't':
          out += '\t';
          break;
        case 'r':
          out += '\r';
          break;
        case 'u':
          if (i + 4 < quoted.length) {
            out += String.fromCharCode(parseInt(quoted.substring(i + 1, i + 5), 16));
            i += 4;
          }
          break;
        default:
          out += e;
      }
    } else {
      out += c;
    }
  }
  return out;
}

/** What is wrong with a JSON text, or null when it is one valid JSON value (Python's json reads it). */
export function checkJson(text: string): string | null {
  let i = 0;
  const s = text;
  const fail = (what: string): never => {
    throw new JsonError(what + ' at column ' + (i + 1));
  };
  const space = (): void => {
    while (i < s.length && ' \t\r\n'.indexOf(s.charAt(i)) >= 0) i++;
  };
  const expect = (c: string): void => {
    if (i >= s.length || s.charAt(i) !== c) fail("expecting '" + c + "'");
    i++;
  };
  const word = (w: string): void => {
    if (!s.startsWith(w, i)) fail('expecting a value');
    i += w.length;
  };
  const string = (): void => {
    i++;
    while (i < s.length) {
      const c = s.charAt(i);
      if (c === '\\') {
        i += 2;
        continue;
      }
      if (c === '"') {
        i++;
        return;
      }
      if (c.charCodeAt(0) < 0x20) fail('a control character in a string');
      i++;
    }
    fail('a string with no closing quote');
  };
  const number = (): void => {
    const start = i;
    if (i < s.length && s.charAt(i) === '-') {
      i++;
      if (s.startsWith('Infinity', i)) {
        i += 8;
        return;
      }
    }
    while (i < s.length && '0123456789.eE+-'.indexOf(s.charAt(i)) >= 0) i++;
    if (!NUMBER.test(s.substring(start, i))) {
      i = start;
      fail('expecting a value');
    }
  };
  const value = (): void => {
    if (i >= s.length) fail('expecting a value');
    switch (s.charAt(i)) {
      case '{':
        object();
        break;
      case '[':
        array();
        break;
      case '"':
        string();
        break;
      case 't':
        word('true');
        break;
      case 'f':
        word('false');
        break;
      case 'n':
        word('null');
        break;
      case 'N':
        word('NaN');
        break;
      case 'I':
        word('Infinity');
        break;
      default:
        number();
    }
  };
  const object = (): void => {
    i++;
    space();
    if (i < s.length && s.charAt(i) === '}') {
      i++;
      return;
    }
    for (;;) {
      space();
      if (i >= s.length || s.charAt(i) !== '"') fail('expecting a property name in double quotes');
      string();
      space();
      expect(':');
      space();
      value();
      space();
      if (i < s.length && s.charAt(i) === ',') {
        i++;
        continue;
      }
      expect('}');
      return;
    }
  };
  const array = (): void => {
    i++;
    space();
    if (i < s.length && s.charAt(i) === ']') {
      i++;
      return;
    }
    for (;;) {
      space();
      value();
      space();
      if (i < s.length && s.charAt(i) === ',') {
        i++;
        continue;
      }
      expect(']');
      return;
    }
  };
  try {
    space();
    value();
    space();
    if (i !== text.length) {
      return 'extra data at column ' + (i + 1);
    }
    return null;
  } catch (e) {
    if (e instanceof JsonError) return e.message;
    throw e;
  }
}

class JsonError extends Error {}
