#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [ -z "$SDK" ] || [ ! -d "$SDK" ]; then
  echo "请先设置 ANDROID_SDK_ROOT 或 ANDROID_HOME 指向 Android SDK" >&2
  exit 1
fi
BT="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)"
AJ="$(ls -d "$SDK"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1)"
if [ -z "$BT" ] || [ -z "$AJ" ]; then
  echo "找不到 build-tools 或 platforms/android-*/android.jar" >&2
  exit 1
fi
AAPT2="$BT/aapt2.exe"
JAVAC="javac"
D8="$BT/d8.bat"
ZIPALIGN="$BT/zipalign.exe"
APKSIG="$BT/lib/apksigner.jar"
ZX="${ZXING_JAR:-libs/zxing-core-3.5.3.jar}"

if [ ! -f "$ZX" ]; then echo "缺少 ZXing：$ZX" >&2; exit 1; fi

rm -rf build gen classes dex
mkdir -p build gen classes dex res/drawable

"$AAPT2" compile --dir res -o build/res.zip
"$AAPT2" link -o build/base.apk -I "$AJ" --manifest AndroidManifest.xml \
  -R build/res.zip --java gen --min-sdk-version 26 --target-sdk-version 34 \
  --version-code 7 --version-name 1.6

find src gen -name "*.java" > build/sources.txt
"$JAVAC" -encoding UTF-8 --release 11 -classpath "$AJ;$ZX" -d classes @build/sources.txt 2>&1

"$D8" --lib "$AJ" --min-api 26 --output dex $(find classes -name "*.class") "$ZX"

python - <<'PY'
import zipfile, shutil, os
shutil.copy("build/base.apk", "build/unsigned.apk")
with zipfile.ZipFile("build/unsigned.apk", "a", zipfile.ZIP_DEFLATED) as z:
    z.write("dex/classes.dex", "classes.dex")
PY

"$ZIPALIGN" -f 4 build/unsigned.apk build/aligned.apk

# 签名密钥不放进仓库：通过环境变量传入，或放在本机被 .gitignore 的文件里。
#   GDUF_KEYSTORE  密钥库路径（默认 gduf-release.keystore）
#   GDUF_KS_PASS   密钥库口令（必填）
#   GDUF_KEY_PASS  密钥口令（默认同密钥库口令）
#   GDUF_KEY_ALIAS 别名（默认 gduf）
KS="${GDUF_KEYSTORE:-gduf-release.keystore}"
KS_PASS="${GDUF_KS_PASS:?请先设置 GDUF_KS_PASS（不要写进脚本）}"
KEY_PASS="${GDUF_KEY_PASS:-$KS_PASS}"
ALIAS="${GDUF_KEY_ALIAS:-gduf}"

if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias "$ALIAS" -keyalg RSA -keysize 2048 -validity 10000     -storepass "$KS_PASS" -keypass "$KEY_PASS"     -dname "CN=GDUF, OU=Student, O=GDUF, L=Guangzhou, ST=Guangdong, C=CN" >/dev/null 2>&1
fi

java -jar "$APKSIG" sign --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KEY_PASS"   --out build/GDUFQR.apk build/aligned.apk

ls -l build/GDUFQR.apk
"$BT/aapt2.exe" dump badging build/GDUFQR.apk 2>/dev/null | head -3 || true
