// Installing archvow, guided (0.12.0): which Python, from where, the command shown before it runs, and
// nothing run without "Confirm and run". The same rules as IntelliJ's ArchvowInstall (InstallRegression).
import * as assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { test } from 'node:test';
import { displayCommand, installCommand, installPython, installSource, offer, pipArgs, requirement, venvPythons } from '../install';

const ROOT = path.resolve(__dirname, '..', '..');

test('the source: pip\'s own configuration by default, an index URL, a wheel file or a folder of wheels', () => {
  assert.deepEqual(installSource(''), { kind: 'pip', value: '' });
  assert.deepEqual(installSource(undefined), { kind: 'pip', value: '' });
  assert.deepEqual(installSource(' https://nexus.acme.local/repository/pypi/simple '), { kind: 'index', value: 'https://nexus.acme.local/repository/pypi/simple' });
  assert.deepEqual(installSource('/lab/pypi'), { kind: 'wheel', value: '/lab/pypi' });
  assert.equal(requirement('1.17.0'), 'archvow==1.17.0');
  assert.deepEqual(pipArgs('1.17.0', installSource('')), ['-m', 'pip', 'install', 'archvow==1.17.0']);
  assert.deepEqual(pipArgs('1.17.0', installSource('https://nexus/simple')), ['-m', 'pip', 'install', '--index-url', 'https://nexus/simple', 'archvow==1.17.0']);
  assert.deepEqual(pipArgs('1.17.0', installSource('/w/archvow-1.17.0-py3-none-any.whl')), ['-m', 'pip', 'install', '/w/archvow-1.17.0-py3-none-any.whl']);
  assert.deepEqual(pipArgs('1.17.0', installSource('/lab/pypi')), ['-m', 'pip', 'install', '--no-index', '--find-links', '/lab/pypi', 'archvow==1.17.0']);
});

test('the Python: the project\'s venv first, then the IDE\'s interpreter, then archvow.python; never a bare "python"', () => {
  assert.deepEqual(venvPythons('/p', false), ['/p/.venv/bin/python', '/p/venv/bin/python']);
  assert.equal(venvPythons('C:\\p', true)[0], 'C:\\p\\.venv\\Scripts\\python.exe');
  const only = (...files: string[]) => (f: string) => files.includes(f);
  assert.equal(installPython('/p', '/usr/bin/python3.12', '/x/python', only('/p/venv/bin/python'), false), '/p/venv/bin/python');
  assert.equal(installPython('/p', '/opt/py/bin/python3', '/x/python', only(), false), '/opt/py/bin/python3');
  assert.equal(installPython('/p', 'python', '/x/python', only(), false), '/x/python');
  assert.equal(installPython('/p', 'python3', 'python', only(), false), null);
  assert.equal(installPython('/p', '', '', only(), false), null);
});

test('the command: no shell, the Python itself; a Python in WSL through wsl.exe with Linux paths', () => {
  const plain = installCommand('/p/.venv/bin/python', '/p', '1.17.0', installSource(''), false);
  assert.deepEqual(plain, { program: '/p/.venv/bin/python', args: ['-m', 'pip', 'install', 'archvow==1.17.0'] });
  assert.equal(displayCommand(plain), '/p/.venv/bin/python -m pip install archvow==1.17.0');
  const wsl = installCommand('\\\\wsl.localhost\\Ubuntu\\home\\m\\.venv\\bin\\python3', 'C:\\work\\shop', '1.17.0',
    installSource('C:\\wheels\\archvow-1.17.0-py3-none-any.whl'), true);
  assert.deepEqual(wsl, { program: 'wsl.exe', args: ['-d', 'Ubuntu', '--cd', '/mnt/c/work/shop', '--exec', '/home/m/.venv/bin/python3',
    '-m', 'pip', 'install', '/mnt/c/wheels/archvow-1.17.0-py3-none-any.whl'] });
  const shown = offer('missing', '1.17.0', '/p/.venv/bin/python', '/p', installSource(''), false);
  assert.deepEqual(shown, { reason: 'missing', version: '1.17.0', python: '/p/.venv/bin/python',
    command: '/p/.venv/bin/python -m pip install archvow==1.17.0', source: "pip's configuration" });
  assert.deepEqual(offer('version', '1.17.0', null, '/p', installSource(''), false).python, '');
});

test('nothing runs without "Confirm and run": the page asks, the host shows the command, pip runs on the token', () => {
  const page = fs.readFileSync(path.join(ROOT, 'media', 'workspace.js'), 'utf8');
  const host = fs.readFileSync(path.join(ROOT, 'src', 'workspace.ts'), 'utf8');
  assert.match(page, /install: \(\) => post\(\{ type: 'install' \}\)/);
  assert.match(page, /\$\{installCard\(\)\}/);
  assert.match(page, /confirmation\('welcome'\)/);
  // 'install' only asks; pip runs only from a confirmed token
  const asked = host.slice(host.indexOf("case 'install':"), host.indexOf("case 'confirmed':"));
  assert.match(asked, /this\.askInstall\(from\)/);
  assert.doesNotMatch(asked, /runInstall|spawn/);
  assert.match(host, /if \(this\.pendingInstall\.has\(String\(m\.token\)\)\) return this\.runInstall\(String\(m\.token\), from\);/);
  assert.match(host, /spawn\(job\.program, job\.args, \{ cwd: wsl \? os\.homedir\(\) : this\.root, shell: false/);
  // no install at startup: only askInstall (a click) builds a pending install
  assert.equal((host.match(/this\.pendingInstall\.set\(/g) ?? []).length, 1);
  const manifest = JSON.parse(fs.readFileSync(path.join(ROOT, 'package.json'), 'utf8'));
  assert.equal(manifest.contributes.configuration.properties['archvow.installFrom'].default, '');
});
