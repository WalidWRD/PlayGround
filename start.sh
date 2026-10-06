#!/usr/bin/env bash
set -euo pipefail
time -p cd "$(dirname "$0")"
time -p test -f "$PWD/dist/index.html"
time -p mkdir -p "${OPENCODE_WEB_DIR:-/home/runner/work/_temp/omgithub-web}"
time -p python3 -c "import json,os; d=os.environ.get('OPENCODE_WEB_DIR','/home/runner/work/_temp/omgithub-web'); open(os.path.join(d,'deployment-output.json'),'w').write(json.dumps({'project':os.getcwd(),'directory':os.path.join(os.getcwd(),'dist')}))"
time -p cat "${OPENCODE_WEB_DIR:-/home/runner/work/_temp/omgithub-web}/deployment-output.json"
echo
PORT="${PORT:-3000}"
exec python3 -m http.server "$PORT" --directory "$PWD/dist"
