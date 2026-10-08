#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# LOKTV Hook Pro - release packager.
# Produces the 3 deliverables for every development in dist/:
#   1) LOKTV-Hook-Pro-vX.apk          (signed module)
#   2) LOKTV-Hook-Pro-SOURCE-vX.zip   (full source snapshot)
#   3) LOKTV-Hook-Pro-ANALYSIS-vX.md  (analysis / results / improvements)
# Run AFTER build.sh. Version is always read from app/AndroidManifest.xml.
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
VER="$(grep -o 'android:versionName="[^"]*"' "$ROOT/app/AndroidManifest.xml" | cut -d'"' -f2)"
DIST="$ROOT/dist"
SRC_ZIP="$DIST/LOKTV-Hook-Pro-SOURCE-v${VER}.zip"
ANALYSIS_MD="$DIST/LOKTV-Hook-Pro-ANALYSIS-v${VER}.md"
APK="$DIST/LOKTV-Hook-Pro-v${VER}.apk"

echo "[*] version : $VER"

# 0) APK must exist (built by build.sh) --------------------------------------
if [ ! -f "$APK" ]; then
  echo "[!] FATAL: $APK not found, run build.sh first" >&2
  exit 1
fi
echo "[+] apk     : $(basename "$APK")"

# 1) source snapshot ----------------------------------------------------------
rm -f "$SRC_ZIP"
cd "$ROOT"
zip -q -r -X "$SRC_ZIP" \
  src src-xposed app build.sh package.sh docs README.md Agents.md \
  -x '*/.DS_Store'
echo "[+] source  : $(basename "$SRC_ZIP") ($(du -h "$SRC_ZIP" | cut -f1))"

# 2) analysis file (latest docs/ANALYSIS-v*.md matching this version) ---------
SRC_ANALYSIS="$(ls -t "$ROOT"/docs/ANALYSIS-v${VER}.md 2>/dev/null | head -1)"
if [ -z "$SRC_ANALYSIS" ]; then
  SRC_ANALYSIS="$(ls -t "$ROOT"/docs/ANALYSIS-*.md 2>/dev/null | head -1)"
fi
if [ -z "$SRC_ANALYSIS" ]; then
  echo "[!] FATAL: no docs/ANALYSIS-*.md found" >&2
  exit 1
fi
cp "$SRC_ANALYSIS" "$ANALYSIS_MD"
echo "[+] analysis: $(basename "$ANALYSIS_MD") (from $(basename "$SRC_ANALYSIS"))"

# 3) verify -------------------------------------------------------------------
echo "--- deliverables ---"
ls -lh "$APK" "$SRC_ZIP" "$ANALYSIS_MD"
unzip -l "$SRC_ZIP" | tail -n 3
echo "[✓] PACKAGE COMPLETE"
