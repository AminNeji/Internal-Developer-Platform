// Regenerates the PDFs in docs/ from the HTML next to this file.
// Run from demo/, which has Playwright installed: node ../docs/src/render.mjs
import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const require = createRequire(path.join(here, '../../demo/package.json'));
const { chromium } = require('playwright');

const browser = await chromium.launch();
for (const name of ['runbook-apply', 'runbook-destroy', 'project-guide']) {
  const page = await browser.newPage();
  await page.goto(pathToFileURL(path.join(here, `${name}.html`)).href);
  await page.pdf({
    path: path.join(here, '..', `${name}.pdf`),
    format: 'A4',
    printBackground: true,
    preferCSSPageSize: true,
    displayHeaderFooter: true,
    headerTemplate: '<span></span>',
    footerTemplate: '<div style="font:8px Segoe UI,Arial;color:#5b6878;width:100%;text-align:center"><span class="pageNumber"></span> / <span class="totalPages"></span></div>',
  });
  await page.close();
}
await browser.close();
