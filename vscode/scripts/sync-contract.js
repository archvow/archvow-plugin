// Copies what archvow says about itself (../contract/cli: the command grammar, the template catalog,
// the meaning of each check code, the version) and contract/forms/forms.json into resources/archvow, packaged with the extension.
// Run by `npm run compile`, `npm test` and `npm run package`; the IntelliJ build does the same.
const fs = require('fs');
const path = require('path');
const from = path.join(__dirname, '..', '..', 'contract', 'cli');
const to = path.join(__dirname, '..', 'resources', 'archvow');
fs.mkdirSync(to, { recursive: true });
for (const name of ['commands.json', 'catalog.json', 'kinds.json', 'rules.md', 'rules.fr.md', 'version.txt']) {
  fs.copyFileSync(path.join(from, name), path.join(to, name));
}
// how the plugins lay out each command's form, and the trees' icons (the plugins' own files, not archvow's)
for (const name of ['forms.json', 'icons.json']) {
  fs.copyFileSync(path.join(__dirname, '..', '..', 'contract', 'forms', name), path.join(to, name));
}
