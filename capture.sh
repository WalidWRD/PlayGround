#!/usr/bin/env bash
# Capture desktop + mobile screenshots of CAPTURE_URL into CAPTURE_DIR.
# Exit 75: temporary navigation/browser infrastructure failure.
# Exit 1: script error or rendering defect (missing content/blank page).
set -euo pipefail

/usr/bin/time -p test -n "${CAPTURE_URL:-}"
/usr/bin/time -p test -n "${CAPTURE_DIR:-}"
/usr/bin/time -p mkdir -p "$CAPTURE_DIR"

export CAPTURE_URL CAPTURE_DIR
/usr/bin/time -p node -e "
const fs = require('fs');
const path = require('path');
const os = require('os');
const { createRequire } = require('node:module');
const runtime = path.join(os.homedir(), '.local/share/omgithub-playwright');
const req = createRequire(path.join(runtime, 'package.json'));
const { chromium } = req('playwright');
const cfg = JSON.parse(fs.readFileSync(path.join(runtime, process.platform === 'darwin' ? 'metal.json' : 'linux.json'), 'utf8'));
if (process.platform === 'linux') {
  try { process.env.DISPLAY ||= ':' + fs.readFileSync(path.join(runtime, 'display'), 'utf8').trim(); } catch {}
}
const url = process.env.CAPTURE_URL;
const out = process.env.CAPTURE_DIR;
const transient = (msg) => { const e = new Error(msg); e.exitCode = 75; throw e; };
const defect = (msg) => { const e = new Error(msg); e.exitCode = 1; throw e; };
(async () => {
  let browser;
  try {
    browser = await chromium.launch({ ...cfg.browser.launchOptions, timeout: 30000 }).catch((e) => transient('browser launch: ' + e.message));
    for (const [name, w, h] of [['desktop', 1440, 900], ['mobile', 390, 844]]) {
      const page = await browser.newPage({ viewport: { width: w, height: h } }).catch((e) => transient('new page: ' + e.message));
      page.setDefaultTimeout(30000);
      const resp = await page.goto(url, { waitUntil: 'load', timeout: 45000 }).catch((e) => transient('goto: ' + e.message));
      const st = resp ? resp.status() : 0;
      if (!resp || !resp.ok()) {
        if (!resp || [408, 429, 500, 502, 503, 504].includes(st)) transient('HTTP ' + st + ' loading preview');
        else defect('HTTP ' + st + ' loading preview');
      }
      await page.locator('body').waitFor({ state: 'visible' }).catch((e) => transient('body wait: ' + e.message));
      await page.waitForFunction(() => document.fonts.status === 'loaded').catch(() => {});
      await page.waitForTimeout(1500);
      const title = await page.title().catch(() => '');
      const textLen = await page.evaluate(() => (document.body ? document.body.innerText.length : 0)).catch(() => 0);
      if (!textLen || textLen < 20) defect('rendering defect: page text too short (' + textLen + ' chars, title=' + JSON.stringify(title) + ')');
      await page.screenshot({ path: path.join(out, 'final-' + name + '.png'), timeout: 30000 }).catch((e) => {
        if (e.name === 'TimeoutError' || !browser.isConnected()) transient('screenshot: ' + e.message);
        throw e;
      });
      console.log('captured ' + name + ' (' + w + 'x' + h + ') title=' + JSON.stringify(title));
      await page.close();
    }
  } catch (e) { console.error(e.message); process.exitCode = e.exitCode || 1; }
  finally { if (browser) await browser.close().catch((e) => { console.error(e.message); process.exitCode ||= 75; }); }
})();
"
status=$?
/usr/bin/time -p test -f "$CAPTURE_DIR/final-desktop.png"
/usr/bin/time -p test -f "$CAPTURE_DIR/final-mobile.png"
exit "$status"
