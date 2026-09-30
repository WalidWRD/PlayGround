#!/usr/bin/env bash
# GenSubs UpdateKiller preview server (static public/ directory).
# Writes deployment-output.json, serves foreground on PORT (default 3000).
set -euo pipefail

time -p cd "$(dirname "$0")"
time -p bash -c 'PROJECT_ROOT="$PWD"; echo "project root: $PROJECT_ROOT"'
PORT="${PORT:-3000}"
STATIC_DIR="$PWD/public"
WEB_DIR="${OPENCODE_WEB_DIR:-/home/runner/work/_temp/omgithub-web}"
PROJECT_ROOT="$PWD"
export PORT PROJECT_ROOT STATIC_DIR WEB_DIR

/usr/bin/time -p test -f "$STATIC_DIR/index.html"
/usr/bin/time -p mkdir -p "$WEB_DIR"
/usr/bin/time -p bash -c 'command -v node >/dev/null || { echo "node is required" >&2; exit 1; }'

# No npm dependencies for this static preview; build step = sync release APK into public/.
/usr/bin/time -p bash -c '
  if [ -f build/GenSubs-UpdateKiller-v2.0.1.apk ]; then
    cp -f build/GenSubs-UpdateKiller-v2.0.1.apk public/GenSubs-UpdateKiller-v2.0.1.apk
  fi
'
/usr/bin/time -p node -e '
const fs = require("fs");
const project = process.env.PROJECT_ROOT || process.cwd();
const dir = process.env.STATIC_DIR;
const web = process.env.WEB_DIR;
fs.writeFileSync(web + "/deployment-output.json", JSON.stringify({ project, directory: dir }));
console.log("deployment-output.json -> " + web + "/deployment-output.json");
'
echo "Serving $STATIC_DIR on 0.0.0.0:$PORT (foreground)"
/usr/bin/time -p node -e '
const http = require("http");
const fs = require("fs");
const path = require("path");
const root = process.env.STATIC_DIR;
const port = Number(process.env.PORT || "3000");
const mime = { ".html":"text/html; charset=utf-8", ".js":"application/javascript", ".css":"text/css", ".json":"application/json", ".svg":"image/svg+xml", ".png":"image/png", ".jpg":"image/jpeg", ".apk":"application/vnd.android.package-archive" };
const server = http.createServer((req, res) => {
  try {
    const url = new URL(req.url, "http://localhost");
    let p = path.resolve(root, "." + decodeURIComponent(url.pathname));
    if (p !== path.resolve(root) && !p.startsWith(path.resolve(root) + path.sep)) { res.writeHead(404); res.end(); return; }
    if (fs.statSync(p).isDirectory()) p = path.join(p, "index.html");
    res.setHeader("Content-Type", mime[path.extname(p)] || "application/octet-stream");
    res.setHeader("Cache-Control", "no-cache");
    res.end(fs.readFileSync(p));
  } catch (e) { res.writeHead(404); res.end("Not found"); }
});
server.listen(port, "0.0.0.0", () => console.log("listening on " + port));
'
