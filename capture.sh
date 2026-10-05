#!/usr/bin/env bash
# capture.sh — screenshot the running app without disturbing it.
# - Reads CAPTURE_URL (exact URL to open) and CAPTURE_DIR (output outside source).
# - Captures desktop (1440x900) and mobile (390x844) as
#   final-desktop.png / final-mobile.png in CAPTURE_DIR.
# - Closes only its own browser; leaves the app server running.
# - Exit 75: temporary navigation / browser infrastructure failure.
# - Exit 1: script or rendering defect.
set -euo pipefail
cd "$(dirname "$0")"
: "${CAPTURE_URL:?Set CAPTURE_URL to the exact URL to capture.}"
: "${CAPTURE_DIR:?Set CAPTURE_DIR to the output directory (outside source).}"
: "${RUNTIME_DIR:?Set RUNTIME_DIR to the runtime scripts directory.}"
/usr/bin/time -p mkdir -p "$CAPTURE_DIR"
/usr/bin/time -p node "$RUNTIME_DIR/scripts/default-capture.mjs"
/usr/bin/time -p test -f "$CAPTURE_DIR/final-desktop.png"
/usr/bin/time -p test -f "$CAPTURE_DIR/final-mobile.png"
/usr/bin/time -p ls -la "$CAPTURE_DIR"
echo "Capture done: $CAPTURE_DIR/final-desktop.png + $CAPTURE_DIR/final-mobile.png"
