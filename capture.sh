#!/usr/bin/env bash
# Capture desktop + mobile screenshots of the exact CAPTURE_URL into CAPTURE_DIR.
# Exit 75 = temporary navigation/browser infrastructure failure.
# Exit 1  = script or rendering defect. Leaves the app running.
set -euo pipefail

time -p cd "$(dirname "$0")"
time -p bash -c 'echo "capture: CAPTURE_URL=${CAPTURE_URL:-<unset>} CAPTURE_DIR=${CAPTURE_DIR:-<unset>}"'

if [[ -z "${CAPTURE_URL:-}" || -z "${CAPTURE_DIR:-}" ]]; then
  echo "Set CAPTURE_URL and CAPTURE_DIR." >&2
  exit 1
fi
if [[ -z "${RUNTIME_DIR:-}" ]]; then
  echo "RUNTIME_DIR is required." >&2
  exit 1
fi

/usr/bin/time -p mkdir -p "$CAPTURE_DIR"
/usr/bin/time -p node "${RUNTIME_DIR}/scripts/default-capture.mjs"
status=$?
if [[ $status -ne 0 ]]; then
  echo "capture backend exited $status" >&2
  exit $status
fi
/usr/bin/time -p test -f "$CAPTURE_DIR/final-desktop.png"
/usr/bin/time -p test -f "$CAPTURE_DIR/final-mobile.png"
/usr/bin/time -p ls -lh "$CAPTURE_DIR/final-desktop.png" "$CAPTURE_DIR/final-mobile.png"
echo "capture OK -> $CAPTURE_DIR"
