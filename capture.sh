#!/usr/bin/env bash
set -euo pipefail
time -p cd "$(dirname "$0")"
time -p test -n "${CAPTURE_URL:-}"
time -p test -n "${CAPTURE_DIR:-}"
time -p mkdir -p "$CAPTURE_DIR"
time -p node "${RUNTIME_DIR:-/home/runner/work/_temp/omgithub-runtime}/scripts/default-capture.mjs"
time -p test -f "$CAPTURE_DIR/final-desktop.png"
time -p test -f "$CAPTURE_DIR/final-mobile.png"
echo "capture ok: $CAPTURE_DIR"
