#!/usr/bin/env bash
# Capture desktop + mobile screenshots of the exact $CAPTURE_URL into $CAPTURE_DIR.
# Exit 75 = temporary navigation/browser infra failure; exit 1 = script/rendering defect.
# Leaves the app running; capture output stays outside the source tree.
set -euo pipefail
time -p cd "$(dirname "$0")"
if [[ -z "${CAPTURE_URL:-}" || -z "${CAPTURE_DIR:-}" ]]; then
  echo "Set CAPTURE_URL and CAPTURE_DIR." >&2
  exit 1
fi
time -p mkdir -p "$CAPTURE_DIR"
if [[ -z "${RUNTIME_DIR:-}" ]]; then
  echo "RUNTIME_DIR is not set." >&2
  exit 1
fi
time -p test -f "$RUNTIME_DIR/scripts/default-capture.mjs"
time -p node "$RUNTIME_DIR/scripts/default-capture.mjs"
code=$?
time -p test -f "$CAPTURE_DIR/final-desktop.png"
time -p test -f "$CAPTURE_DIR/final-mobile.png"
exit "$code"
