# Fuentes de la plantilla (base.apk) de APK Forge

- `androidstudio/` → proyecto de Android Studio (abrir esta carpeta). Contiene los Java en `app/src/main/java/com/dskmusic/shell/`: MainActivity, LocalServer, **Bridge (ForgeNative)** y **Saver** (escriben los archivos descargados), y `app/src/main/assets/forge/shim.js`.
- `template/` → misma plantilla para compilar sin Gradle (`build_base.sh` → `base/base.apk`). `sync_to_studio.sh` copia los Java/shim entre `template/` y `androidstudio/` (`reverse` = al revés).
- Para generar `base.apk`: ejecuta `CREAR_BASE_APK.bat` (raíz del proyecto). No uses `androidstudio/` para eso: no lleva los marcadores de plantilla.
- Guardado de archivos: `shim.js` intercepta `<a download>`/blob/data/share y llama a `ForgeNative.begin/chunk/end` (Bridge.java) → `Saver.java` (MediaStore en Android 10+, ruta clásica en ≤28). Todo va a `Descargas/<App>/<App>_aaaaMMdd_HHmmss.<ext>`.
