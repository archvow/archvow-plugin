// Runs the compiled tests: node --test on every out/test/*.test.js, listed here rather than given as a
// glob - Node 20 (the oldest the extension supports) reads `node --test "out/test/*.test.js"` as one file
// name and finds nothing; Node 21+ expands it. Arguments name the files to run instead (one test file).
const { spawnSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const dir = path.join(__dirname, '..', 'out', 'test');
const files = process.argv.length > 2 ? process.argv.slice(2)
  : fs.readdirSync(dir).filter((f) => f.endsWith('.test.js')).sort().map((f) => path.join(dir, f));
if (files.length === 0) {
  console.error(`no test file in ${dir}: run npm run compile first`);
  process.exit(1);
}
const run = spawnSync(process.execPath, ['--test', ...files], { stdio: 'inherit' });
process.exit(run.status ?? 1);
