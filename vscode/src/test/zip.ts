// A small zip reader for the package tests (package.test.ts): the .vsix and the IntelliJ .zip are zip
// files, and the IntelliJ plugin's .jar inside its .zip too. Reads the central directory, then each
// entry (stored or deflated). No dependency: what a CI image has is Node.
import * as zlib from 'node:zlib';

export type Zip = Map<string, () => Buffer>;

/** The entries of a zip held in memory: name -> its content, read when asked. */
export function readZip(data: Buffer): Zip {
  // the end of central directory record: its signature, searched backwards (a comment may follow it)
  let end = -1;
  for (let i = data.length - 22; i >= Math.max(0, data.length - 22 - 0xffff); i--) {
    if (data.readUInt32LE(i) === 0x06054b50) { end = i; break; }
  }
  if (end < 0) throw new Error('not a zip file (no end of central directory)');
  const count = data.readUInt16LE(end + 10);
  let at = data.readUInt32LE(end + 16);
  const entries: Zip = new Map();
  for (let n = 0; n < count; n++) {
    if (data.readUInt32LE(at) !== 0x02014b50) throw new Error(`zip: bad central directory entry ${n}`);
    const method = data.readUInt16LE(at + 10);
    const compressed = data.readUInt32LE(at + 20);
    const nameLength = data.readUInt16LE(at + 28);
    const extraLength = data.readUInt16LE(at + 30);
    const commentLength = data.readUInt16LE(at + 32);
    const local = data.readUInt32LE(at + 42);
    const name = data.toString('utf8', at + 46, at + 46 + nameLength);
    at += 46 + nameLength + extraLength + commentLength;
    entries.set(name, () => {
      if (data.readUInt32LE(local) !== 0x04034b50) throw new Error(`zip: bad local header for ${name}`);
      const start = local + 30 + data.readUInt16LE(local + 26) + data.readUInt16LE(local + 28);
      const raw = data.subarray(start, start + compressed);
      if (method === 0) return Buffer.from(raw);
      if (method === 8) return zlib.inflateRawSync(raw);
      throw new Error(`zip: ${name} uses compression method ${method}`);
    });
  }
  return entries;
}
