#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${CAPTURE_URL:?Set CAPTURE_URL.}"
: "${CAPTURE_DIR:?Set CAPTURE_DIR.}"
: "${RUNTIME_DIR:?Set RUNTIME_DIR.}"
/usr/bin/time -p mkdir -p "$CAPTURE_DIR"
/usr/bin/time -p test -f "${RUNTIME_DIR}/scripts/default-capture.mjs"
/usr/bin/time -p node "${RUNTIME_DIR}/scripts/default-capture.mjs"
/usr/bin/time -p ls -lh "$CAPTURE_DIR"
