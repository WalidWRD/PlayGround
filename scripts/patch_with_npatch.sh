#!/usr/bin/env bash
# دمج الموديول في التطبيق بدون روت: LSPatch / NPatch / HKP
# 1) LSPatch (سطر أوامر)  →  lspatch -m patch -o genspark_patched.apk <genspark.apk> GensparkUpdateKiller.apk
# 2) NPatch (جافا)        →  java -jar NPatch.jar -m <module.apk> <genspark.apk> -o genspark_patched.apk
# 3) HKP Patch (تطبيق)    →  اختر الحزمة ثم الموديول ثم Patch
set -euo pipefail
APP="${1:-genspark.apk}"; MOD="${2:-GensparkUpdateKiller-v1.2.0.apk}"; OUT="${3:-genspark_patched.apk}"
if command -v lspatch >/dev/null 2>&1; then
  lspatch -m patch -o "$OUT" "$APP" "$MOD"
elif [ -f NPatch.jar ]; then
  java -jar NPatch.jar -m "$MOD" "$APP" -o "$OUT"
else
  echo "ثبّت lspatch أو ضع NPatch.jar بجوار السكربت، أو استخدم واجهة HKP Patch."; exit 1
fi
echo "OK → $OUT  (ثم: adb install -r $OUT && adb logcat -s GensparkUpdateKiller)"
