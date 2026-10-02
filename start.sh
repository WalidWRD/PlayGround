#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
PROJECT_ROOT="$PWD"
DIST_DIR="$PROJECT_ROOT/dist"
PORT="${PORT:-3000}"
export PORT
OPENCODE_WEB_DIR="${OPENCODE_WEB_DIR:-/home/runner/work/_temp/omgithub-web}"
/usr/bin/time -p test -f "$DIST_DIR/index.html"
/usr/bin/time -p test -d "$DIST_DIR"
if /usr/bin/time -p test -f "$PROJECT_ROOT/package.json"; then
  if /usr/bin/time -p test -f "$PROJECT_ROOT/package-lock.json"; then
    /usr/bin/time -p npm ci --no-audit --no-fund
  else
    /usr/bin/time -p npm install --no-audit --no-fund
  fi
  if /usr/bin/time -p npm run --silent build --if-present; then
    true
  fi
else
  /usr/bin/time -p echo "no package.json: static dist, skipping install/build"
fi
/usr/bin/time -p mkdir -p "$OPENCODE_WEB_DIR"
/usr/bin/time -p python3 -c "import json,os; root=os.environ.get('PROJECT_ROOT_OVERRIDE') or '$PROJECT_ROOT'; d='$DIST_DIR'; o=os.environ.get('OPENCODE_WEB_DIR','/home/runner/work/_temp/omgithub-web'); open(o+'/deployment-output.json','w').write(json.dumps({'project': root, 'directory': d}))"
/usr/bin/time -p cat "$OPENCODE_WEB_DIR/deployment-output.json"
/usr/bin/time -p echo "serving $DIST_DIR on PORT=$PORT"
/usr/bin/time -p python3 --version
exec /usr/bin/time -p python3 -m http.server "$PORT" --directory "$DIST_DIR" --bind 0.0.0.0
