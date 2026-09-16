import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const docsDir = dirname(fileURLToPath(import.meta.url));
const files = [
  'README.md',
  '01-tong-quan.md',
  '02-cai-dat-va-khoi-dong.md',
  '03-accessibility-automation.md',
  '04-media-va-bao-cao.md',
  '05-web-portal-va-script.md',
  '06-kien-truc.md',
  '07-xu-ly-su-co.md',
  '08-script-data-model.md',
  '09-action-data-model.md',
  '10-mo-ta-action.md',
  '11-action-definitions.md',
  '12-lay-danh-sach-step-hien-tai.md'
];

const embeddedDocs = Object.fromEntries(
  files.map(file => [file, readFileSync(join(docsDir, file), 'utf8')])
);
const indexPath = join(docsDir, 'index.html');
const html = readFileSync(indexPath, 'utf8');
const fallbackStartMarker = '  <!-- Fallback data lets the viewer work when index.html is opened via file://. -->';
const docsListMarker = '    const docs = [';
const fallbackStart = html.indexOf(fallbackStartMarker);
const docsListStart = html.indexOf(docsListMarker, fallbackStart);

if (fallbackStart < 0 || docsListStart < 0) {
  throw new Error('Cannot find the embedded documentation markers in index.html');
}

const safeJson = JSON.stringify(embeddedDocs, null, 2).replaceAll('</script', '<\\/script');
const generated = [
  fallbackStartMarker,
  '  <script id="embedded-docs" type="application/json">',
  safeJson,
  '  </script>',
  '  <script>',
  "    const embeddedDocs = JSON.parse(document.querySelector('#embedded-docs').textContent);",
  html.slice(docsListStart)
].join('\n');

writeFileSync(indexPath, html.slice(0, fallbackStart) + generated);
console.log(`Embedded ${files.length} Markdown documents into index.html`);
