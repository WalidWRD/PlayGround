#!/usr/bin/env bash
# Serve the static preview for Genspark UpdateKiller (Android Xposed module).
# The built static directory is ./public (contains index.html, no build step).
set -euo pipefail

/usr/bin/time -p bash -c 'cd "$0" && pwd' "$(dirname "$0")"
cd "$(dirname "$0")"
PROJECT_ROOT="$PWD"
/usr/bin/time -p test -f "$PROJECT_ROOT/public/index.html"

PORT="${PORT:-3000}"
STATIC_DIR="$PROJECT_ROOT/public"
WEB_DIR="${OPENCODE_WEB_DIR:-/home/runner/work/_temp/omgithub-web}"

/usr/bin/time -p mkdir -p "$WEB_DIR"
/usr/bin/time -p node -e "require('fs').writeFileSync(process.argv[1], JSON.stringify({project: process.argv[2], directory: process.argv[3]}))" "$WEB_DIR/deployment-output.json" "$PROJECT_ROOT" "$STATIC_DIR"

echo "Serving $STATIC_DIR on port $PORT (project $PROJECT_ROOT)"
/usr/bin/time -p node -e "
const http = require('http');
const fs = require('fs');
const path = require('path');
const root = process.argv[1];
const port = Number(process.argv[2]);
const mime = {'.html':'text/html; charset=utf-8','.css':'text/css','.js':'application/javascript','.json':'application/json','.png':'image/png','.svg':'image/svg+xml','.ico':'image/x-icon','.webmanifest':'application/manifest+json'};
const server = http.createServer((req, res) => {
  try {
    const url = new URL(req.url, 'http://localhost');
    let p = path.normalize(path.join(root, decodeURIComponent(url.pathname)));
    if (!p.startsWith(root)) { res.writeHead(403); res.end(); return; }
    if (fs.existsSync(p) && fs.statSync(p).isDirectory()) p = path.join(p, 'index.html');
    const data = fs.readFileSync(p);
    res.writeHead(200, {'Content-Type': mime[path.extname(p)] || 'application/octet-stream', 'Cache-Control': 'no-cache'});
    res.end(data);
  } catch (e) { res.writeHead(404); res.end('Not found'); }
});
server.listen(port, '0.0.0.0', () => console.log('Listening on ' + port));
" "$STATIC_DIR" "$PORT"
