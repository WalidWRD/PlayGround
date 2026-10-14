#!/usr/bin/env bash
# Capture desktop + mobile screenshots of CAPTURE_URL into CAPTURE_DIR.
# Exit 75 = temporary navigation/browser infra failure; exit 1 = script/rendering defect.
set -euo pipefail

cd "$(dirname "$0")"
/usr/bin/time -p bash -c 'echo "capture working dir: $0"' "$PWD"

if [[ -z "${CAPTURE_URL:-}" ]]; then
  echo "capture.sh: CAPTURE_URL is not set (script defect)." >&2
  exit 1
fi
if [[ -z "${CAPTURE_DIR:-}" ]]; then
  echo "capture.sh: CAPTURE_DIR is not set (script defect)." >&2
  exit 1
fi
if [[ -z "${RUNTIME_DIR:-}" ]]; then
  echo "capture.sh: RUNTIME_DIR is not set (script defect)." >&2
  exit 1
fi

/usr/bin/time -p mkdir -p "$CAPTURE_DIR"
/usr/bin/time -p bash -c 'echo "capturing $0 into $1"' "$CAPTURE_URL" "$CAPTURE_DIR"

rc=0
/usr/bin/time -p node "$RUNTIME_DIR/scripts/default-capture.mjs" || rc=$?
/usr/bin/time -p bash -c 'echo "capture tool exit: $0"' "$rc"

if [[ "$rc" -eq 75 ]]; then
  echo "capture.sh: temporary browser/infrastructure failure." >&2
  exit 75
fi
if [[ "$rc" -ne 0 ]]; then
  echo "capture.sh: capture tool failed with exit $rc." >&2
  exit 1
fi

/usr/bin/time -p bash -c '
set -euo pipefail
dir="$0"
for f in final-desktop.png final-mobile.png; do
  p="$dir/$f"
  if [[ ! -s "$p" ]]; then echo "capture.sh: missing or empty $p (rendering defect)." >&2; exit 1; fi
  echo "ok: $p"
done
' "$CAPTURE_DIR"

/usr/bin/time -p ls -la "$CAPTURE_DIR"
echo "capture.sh: done."
