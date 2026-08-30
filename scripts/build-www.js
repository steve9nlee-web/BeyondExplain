// Assembles the Capacitor web directory (www/) from the app sources.
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const www = path.join(root, 'www');

fs.rmSync(www, { recursive: true, force: true });
fs.mkdirSync(www, { recursive: true });
fs.copyFileSync(path.join(root, 'index.html'), path.join(www, 'index.html'));
for (const dir of ['css', 'js']) {
  fs.cpSync(path.join(root, dir), path.join(www, dir), { recursive: true });
}
console.log('www/ assembled');
