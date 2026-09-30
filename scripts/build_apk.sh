#!/usr/bin/env bash
# بناء APK بدون Gradle (aapt2 + javac + d8 + zipalign + apksigner) — المسار المستخدم في التسليم.
# المتطلبات: JDK 17، ومجلد build-tools (aapt2,d8,apksigner,zipalign)، وandroid.jar.
set -euo pipefail

ROOT="${ROOT:-/home/user/build/module}"
BT="${BT:?set BT to Android build-tools dir}"
ANDROID_JAR="${ANDROID_JAR:?set ANDROID_JAR}"
JAVAC="${JAVAC:-javac}"
OUT="$ROOT/build"
VER="2.0.1"

rm -rf "$OUT"; mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/apk"

echo "[1/7] aapt2 compile resources"
"$BT/aapt2" compile --dir "$ROOT/app/src/main/res" -o "$OUT/gen/res.zip"

echo "[2/7] javac"
find "$ROOT/app/src/main/java" "$ROOT/libs/xposed-api82/src" -name '*.java' > "$OUT/sources.txt"
"$JAVAC" -encoding UTF-8 -source 17 -target 17 -nowarn -d "$OUT/classes" @"$OUT/sources.txt" \
  -classpath "$ANDROID_JAR"

echo "[3/7] d8"
"$BT/d8" --release --min-api 21 --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

echo "[4/7] aapt2 link"
cp "$ROOT/app/src/main/AndroidManifest.xml" "$OUT/apk/AndroidManifest.xml"
cp -r "$ROOT/app/src/main/assets" "$OUT/apk/assets"
"$BT/aapt2" link -o "$OUT/apk/base.apk" -I "$ANDROID_JAR" \
  --manifest "$OUT/apk/AndroidManifest.xml" -R "$OUT/gen/res.zip" --auto-add-overlay

echo "[5/7] package dex + assets"
cp "$OUT/dex/classes.dex" "$OUT/apk/classes.dex"
( cd "$OUT/apk" && cp base.apk unsigned.apk && zip -q -X unsigned.apk classes.dex && zip -q -X -r unsigned.apk assets )

echo "[6/7] zipalign"
"$BT/zipalign" -f -p 4 "$OUT/apk/unsigned.apk" "$OUT/apk/aligned.apk"

echo "[7/7] sign"
KS="$ROOT/scripts/updatekiller.keystore"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -v -keystore "$KS" -alias uk -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass updatekiller -keypass updatekiller \
    -dname "CN=GenSubs UpdateKiller, O=GenSubs Project"
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:updatekiller --key-pass pass:updatekiller \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$ROOT/build/GenSubs-UpdateKiller-v$VER.apk" "$OUT/apk/aligned.apk"

echo "OK → $ROOT/build/GenSubs-UpdateKiller-v$VER.apk"
