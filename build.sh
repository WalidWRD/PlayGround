#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# LOKTV Hook Pro - reproducible build script (no Android Studio / no gradle)
# Requires: JDK 17+, Android SDK build-tools (d8, aapt2, zipalign, apksigner)
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
# --- JDK auto-detect -------------------------------------------------------
if ! command -v javac >/dev/null 2>&1; then
  JH="$(dirname "$(dirname "$(find /home/user/jdk /usr/lib/jvm -maxdepth 4 -name javac -type f 2>/dev/null | head -1)")")"
  [ -n "$JH" ] && export JAVA_HOME="$JH" && export PATH="$JH/bin:$PATH"
fi
export JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(command -v javac)")")}"
export PATH="$JAVA_HOME/bin:$PATH"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/home/user/android-sdk}}"
BT="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)"
PLATFORM="$(ls -d "$SDK"/platforms/android-* 2>/dev/null | sort -V | tail -1)"
ANDROID_JAR="$PLATFORM/android.jar"
OUT="$ROOT/build"
DIST="$ROOT/dist"
# APK file name always follows android:versionName in the manifest.
VER="$(grep -o 'android:versionName="[^"]*"' "$ROOT/app/AndroidManifest.xml" | cut -d'"' -f2)"
APK_NAME="LOKTV-Hook-Pro-v${VER}.apk"

echo "[*] build-tools : $BT"
echo "[*] platform    : $PLATFORM"

rm -rf "$OUT" && mkdir -p "$OUT/classes" "$OUT/dex" "$DIST"

# 1) compile java ------------------------------------------------------------
# NOTE: src-xposed contains COMPILE-ONLY stubs of the Xposed API.
# They must be on javac's classpath but MUST NOT be packaged into
# classes.dex. The real de.robv.android.xposed classes are provided at
# runtime by LSPosed / LSPatch / NPatch / HKP. Shipping the stubs in the
# APK shadows the real framework (hookMethod() becomes a no-op) and the
# module silently does nothing.
find "$ROOT/src-xposed" -name '*.java' > "$OUT/stub-sources.txt"
javac -source 8 -target 8 -nowarn -encoding UTF-8 \
      -bootclasspath "$ANDROID_JAR" \
      -classpath "$ANDROID_JAR" \
      -d "$OUT/stub-classes" @"$OUT/stub-sources.txt" 2>&1 | grep -v 'bootstrap class path' || true
find "$ROOT/src" -name '*.java' > "$OUT/sources.txt"
javac -source 8 -target 8 -nowarn -encoding UTF-8 \
      -bootclasspath "$ANDROID_JAR" \
      -classpath "$ANDROID_JAR:$OUT/stub-classes" \
      -d "$OUT/classes" @"$OUT/sources.txt" 2>&1 | grep -v 'bootstrap class path' || true
echo "[+] javac ok  ($(find "$OUT/classes" -name '*.class' | wc -l) classes, stubs excluded)"
if find "$OUT/classes" -path '*de/robv*' -name '*.class' | grep -q .; then
  echo "[!] FATAL: stub classes leaked into module output" >&2
  exit 1
fi

# 2) dex ---------------------------------------------------------------------
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
"$BT/d8" --release --min-api 21 --lib "$ANDROID_JAR" --lib "$OUT/stub-classes" \
          --output "$OUT/dex" @"$OUT/classes.txt"
echo "[+] d8 ok"

# 3) resources ---------------------------------------------------------------
"$BT/aapt2" compile --dir "$ROOT/app/res" -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" \
      -I "$ANDROID_JAR" \
      --manifest "$ROOT/app/AndroidManifest.xml" \
      -A "$ROOT/app/assets" \
      --min-sdk-version 21 --target-sdk-version 34 \
      "$OUT/res.zip"
echo "[+] aapt2 ok"

# 4) merge dex + assets ------------------------------------------------------
cd "$OUT" && rm -rf stage && mkdir stage && cd stage
unzip -qo ../base.apk
cp ../dex/classes.dex ./classes.dex
zip -q -r -X ../unsigned.apk . -x 'META-INF/*'
cd "$OUT"
echo "[+] package ok"

# 5) align + sign ------------------------------------------------------------
KS="$ROOT/keystore/loktv.keystore"
mkdir -p "$ROOT/keystore"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -v -keystore "$KS" -storepass loktv123 -keypass loktv123 \
          -alias loktv -keyalg RSA -keysize 2048 -validity 10950 \
          -dname "CN=LOKTV Hook Pro, OU=Mod, O=LOKTV, L=, S=, C=SA" >/dev/null 2>&1
  echo "[+] keystore generated"
fi
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/zipalign" -c -p 4 "$OUT/aligned.apk" \
  || { echo "[!] FATAL: APK not page-aligned, refusing to sign" >&2; exit 1; }
echo "[+] align check ok"
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:loktv123 --key-pass pass:loktv123 \
                --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
                --v4-signing-enabled false \
                --out "$DIST/$APK_NAME" "$OUT/aligned.apk"
"$BT/apksigner" verify --print-certs "$DIST/$APK_NAME" | head -4
echo "[*] cert SHA-256 (must match previous install, else uninstall first):"
"$BT/apksigner" verify --print-certs "$DIST/$APK_NAME" 2>/dev/null | grep -i 'SHA-256' | head -1
ls -lh "$DIST/$APK_NAME"
echo "[✓] BUILD COMPLETE"
