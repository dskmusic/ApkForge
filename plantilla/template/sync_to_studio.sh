#!/bin/bash
# Copia el código de la plantilla (Forge) al proyecto de Android Studio, para que ambos sean idénticos.
#   ./sync_to_studio.sh          -> template/ ==> androidstudio/   (lo normal)
#   ./sync_to_studio.sh reverse  -> androidstudio/ ==> template/   (si editaste en Android Studio)
set -euo pipefail
cd "$(dirname "$0")/.."
T=template/src/com/dskmusic/shell; S=androidstudio/app/src/main/java/com/dskmusic/shell
SH_T=template/assets/forge/shim.js; SH_S=androidstudio/app/src/main/assets/forge/shim.js
if [ "${1:-}" = "reverse" ]; then
  cp "$S"/*.java "$T"/; cp "$SH_S" "$SH_T"; cp androidstudio/app/src/main/res/values/styles.xml template/res/values/styles.xml; cp androidstudio/app/src/main/res/values-night/styles.xml template/res/values-night/styles.xml; echo "androidstudio -> template (recuerda: template/build_base.sh)"
else
  cp "$T"/*.java "$S"/; cp "$SH_T" "$SH_S"; cp template/res/values/styles.xml androidstudio/app/src/main/res/values/styles.xml; cp template/res/values-night/styles.xml androidstudio/app/src/main/res/values-night/styles.xml; echo "template -> androidstudio"
fi
