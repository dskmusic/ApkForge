#!/bin/bash
# Compila la plantilla base (se hace UNA vez; el servidor solo parchea el resultado).
# Requisitos (Debian/Ubuntu): apt install aapt dalvik-exchange android-sdk-platform-23 default-jdk-headless
set -euo pipefail
cd "$(dirname "$0")"
ANDROID_JAR=${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}
OUT=../base; B=$(mktemp -d)
mkdir -p "$B/classes" "$OUT"
javac --release 8 -Xlint:-options -encoding UTF-8 -cp "$ANDROID_JAR" -d "$B/classes" $(find src -name '*.java')
dalvik-exchange --dex --min-sdk-version=21 --output="$B/classes.dex" "$B/classes"
aapt package -f -M AndroidManifest.xml -S res -A assets -I "$ANDROID_JAR" -F "$B/base.apk"
(cd "$B" && zip -q -X base.apk classes.dex)
cp "$B/base.apk" "$OUT/base.apk"
rm -rf "$B"
echo "OK -> $OUT/base.apk"
