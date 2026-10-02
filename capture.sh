#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${CAPTURE_URL:?Set CAPTURE_URL.}"
: "${CAPTURE_DIR:?Set CAPTURE_DIR.}"
RUNTIME_FALLBACK="/home/runner/work/_temp/omgithub-runtime"
RUNTIME_DIR="${RUNTIME_DIR:-$RUNTIME_FALLBACK}"
export CAPTURE_URL CAPTURE_DIR RUNTIME_DIR
/usr/bin/time -p echo "capturing $CAPTURE_URL into $CAPTURE_DIR"
/usr/bin/time -p mkdir -p "$CAPTURE_DIR"
/usr/bin/time -p test -n "$CAPTURE_URL"
/usr/bin/time -p test -n "$CAPTURE_DIR"
/usr/bin/time -p node "$RUNTIME_DIR/scripts/default-capture.mjs"
/usr/bin/time -p test -f "$CAPTURE_DIR/final-desktop.png"
/usr/bin/time -p test -f "$CAPTURE_DIR/final-mobile.png"
/usr/bin/time -p ls -lh "$CAPTURE_DIR/final-desktop.png" "$CAPTURE_DIR/final-mobile.png"
