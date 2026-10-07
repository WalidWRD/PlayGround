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

echo "[*] build-tools : $BT"
echo "[*] platform    : $PLATFORM"

rm -rf "$OUT" && mkdir -p "$OUT/classes" "$OUT/dex" "$DIST"

# 1) compile java ------------------------------------------------------------
find "$ROOT/src" "$ROOT/src-xposed" -name '*.java' > "$OUT/sources.txt"
javac -source 8 -target 8 -nowarn -encoding UTF-8 \
      -bootclasspath "$ANDROID_JAR" \
      -classpath "$ANDROID_JAR" \
      -d "$OUT/classes" @"$OUT/sources.txt" 2>&1 | grep -v 'bootstrap class path' || true
echo "[+] javac ok  ($(find "$OUT/classes" -name '*.class' | wc -l) classes)"

# 2) dex ---------------------------------------------------------------------
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
"$BT/d8" --release --min-api 21 --lib "$ANDROID_JAR" \
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
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:loktv123 --key-pass pass:loktv123 \
                --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
                --out "$DIST/LOKTV-Hook-Pro-v2.1.0.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify --print-certs "$DIST/LOKTV-Hook-Pro-v2.1.0.apk" | head -4
ls -lh "$DIST/LOKTV-Hook-Pro-v2.1.0.apk"
echo "[✓] BUILD COMPLETE"
