package com.dskmusic.apkforge;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Window;
import android.widget.*;

import com.dskmusic.apkforge.core.Lang;

/** Ayuda: cómo funciona la app y qué formato debe tener cada origen. Texto bilingüe (ES/EN) según el idioma elegido. */
public class HelpActivity extends Activity {
    private LinearLayout root;

    private static String s(String es, String en) { return Lang.isEs() ? es : en; }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.applyLang(this);
        Window w = getWindow(); w.setStatusBarColor(Ui.BG); w.setNavigationBarColor(Ui.BG);
        ScrollView sv = new ScrollView(this); sv.setBackgroundColor(Ui.BG);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16); root.setPadding(p, Ui.dp(this, 20), p, p);
        sv.addView(root, new FrameLayout.LayoutParams(-1, -2));
        setContentView(sv);

        root.addView(Ui.text(this, s("Ayuda", "Help"), 24, Ui.TEXT, true));
        Ui.gap(root, 14);

        card(s("Qué hace ApkForge", "What ApkForge does"),
            s("Convierte una web o un proyecto HTML en una app Android (APK) firmada e instalable, todo en tu móvil y sin Android Studio.",
              "Turns a website or an HTML project into a signed, installable Android app (APK), entirely on your phone and without Android Studio."));

        card(s("Uso rápido", "Quick start"),
            s("1. Elige el origen (ZIP/APK, carpeta" + (BuildConfig.GOOF ? ", enlace goof" : "") + " o web).\n"
              + "2. Escribe el nombre (se propone solo a partir del archivo, la carpeta o la URL) y, si quieres, paquete, versión e icono.\n"
              + "3. Ajusta las opciones y pulsa «Generar APK».\n"
              + "4. Cuando termine: Instalar, Compartir o Guardar como…",
              "1. Choose the source (ZIP/APK, folder" + (BuildConfig.GOOF ? ", goof link" : "") + " or website).\n"
              + "2. Type the name (it is suggested from the file, folder or URL) and, if you want, package, version and icon.\n"
              + "3. Adjust the options and tap “Generate APK”.\n"
              + "4. When it finishes: Install, Share or Save as…"));

        card(s("Origen: ZIP / APK", "Source: ZIP / APK"),
            s("El ZIP debe contener un index.html: en la raíz o dentro de una única carpeta. Se usa el index.html más cercano a la raíz.\n\n"
              + "Estructura ideal:", "The ZIP must contain an index.html: at the root or inside a single folder. The index.html closest to the root is used.\n\n"
              + "Ideal structure:"),
            "mi-juego.zip\n├── index.html\n├── css/\n│   └── style.css\n├── js/\n│   └── game.js\n├── img/\n└── audio/",
            s("• Rutas relativas: css/style.css (nunca /css/… ni C:\\…).\n"
              + "• Mayúsculas y minúsculas cuentan: Logo.png no es logo.png.\n"
              + "• Todo lo necesario va dentro del ZIP: así funciona sin conexión.\n"
              + "• Si la web usa servicios externos (APIs, fuentes, CDN), necesitará Internet.\n"
              + "• No metas ZIP dentro del ZIP. Se ignoran __MACOSX, .DS_Store y Thumbs.db.\n"
              + "• Límites: 400 MB y 20 000 archivos.\n"
              + (BuildConfig.GOOF ? "• También vale el .apk de un juego de goof.\n" : ""),
              "• Relative paths: css/style.css (never /css/… or C:\\…).\n"
              + "• Case matters: Logo.png is not logo.png.\n"
              + "• Put everything the app needs inside the ZIP: that way it works offline.\n"
              + "• If the site uses external services (APIs, fonts, CDNs), it will need Internet.\n"
              + "• Do not nest ZIPs. __MACOSX, .DS_Store and Thumbs.db are ignored.\n"
              + "• Limits: 400 MB and 20,000 files.\n"
              + (BuildConfig.GOOF ? "• A goof game .apk also works.\n" : "")));

        card(s("Origen: Carpeta", "Source: Folder"),
            s("Elige la carpeta del proyecto con el selector del sistema. Se incluye todo lo que hay dentro, subcarpetas incluidas, y la raíz de la app es la carpeta del index.html más cercano. Aplican las mismas reglas que en el ZIP.\n\n"
              + "Elige la carpeta del proyecto, no una carpeta superior con muchas cosas distintas.",
              "Pick the project folder with the system picker. Everything inside, subfolders included, is bundled, and the app root is the folder of the closest index.html. The same rules as for ZIPs apply.\n\n"
              + "Choose the project folder itself, not a parent folder holding many unrelated things."));

        card(s("Origen: Web", "Source: Website"),
            s("La app abre tu web online en una ventana propia, como una app. Escribe la URL completa empezando por https://. Necesita Internet para funcionar.\n"
              + "El botón «Buscar icono de la web» intenta usar el favicon de la página (mínimo 128 px; si es menor, se avisa).",
              "The app opens your website online in its own window, like an app. Type the full URL starting with https://. It needs Internet to work.\n"
              + "The “Find site icon” button tries to use the page's favicon (minimum 128 px; you are warned if it is smaller)."));

        if (BuildConfig.GOOF) card(s("Origen: Enlace goof", "Source: Goof link"),
            s("Pega el enlace https://goof.gg/game/… de un juego. La app descarga el juego y sus recursos (necesita Internet al generar) y los incluye en el APK. Usa solo tus propios juegos.",
              "Paste a https://goof.gg/game/… game link. The app downloads the game and its assets (needs Internet while generating) and bundles them into the APK. Use your own games only."));

        card(s("Icono", "Icon"),
            s("Imagen PNG o JPG cuadrada, idealmente 512×512 px. Android la recorta con la forma que use el móvil (círculo, cuadrado redondeado…), así que deja las partes importantes en el centro y evita texto pegado a los bordes.\n"
              + "Si no eliges ninguno, se usa el favicon (modo web, si está marcado) o el icono por defecto.",
              "A square PNG or JPG image, ideally 512×512 px. Android crops it to the shape the phone uses (circle, rounded square…), so keep the important parts in the center and avoid text near the edges.\n"
              + "If you pick none, the favicon is used (web mode, if checked) or the default icon."));

        card(s("Nombre, paquete y versión", "Name, package and version"),
            s("• Nombre: el que se verá bajo el icono.\n"
              + "• Paquete: identificador único, en minúsculas, como com.tuempresa.nombre. Si lo dejas vacío se construye con el prefijo de Ajustes + el nombre.\n"
              + "• Versión: texto que ve el usuario (1.0, 1.1…). versionCode: «auto» sube solo con el tiempo.\n"
              + "• Para actualizar una app ya instalada: mismo paquete, misma firma y versionCode mayor.",
              "• Name: what appears under the icon.\n"
              + "• Package: unique lowercase identifier, like com.yourcompany.name. If empty, it is built from the prefix in Settings + the name.\n"
              + "• Version: text shown to the user (1.0, 1.1…). versionCode: “auto” increases by itself over time.\n"
              + "• To update an app that is already installed: same package, same signature and a higher versionCode."));

        card(s("Opciones", "Options"),
            s((BuildConfig.GOOF ? "• Quitar splash y aviso de goof: elimina la pantalla de bienvenida y el aviso de dominio.\n" : "")
              + "• Modo vertical / horizontal: orientaciones permitidas (si no marcas ninguna, se permiten ambas).\n"
              + "• Selección de texto, zoom con dos dedos, arrastrar para recargar.\n"
              + "• Pantalla completa: oculta las barras del sistema.\n"
              + "• Mantener pantalla encendida: útil para juegos y presentaciones.\n"
              + "• Adaptar a móvil: añade el viewport si la web no está preparada.\n"
              + "• Tema: oscuro, claro o el del sistema.\n"
              + "• Retardo al abrir: muestra una cortina del color de fondo mientras carga (0 = sin retardo).",
              (BuildConfig.GOOF ? "• Remove goof splash and notice: removes the welcome screen and the domain notice.\n" : "")
              + "• Portrait / landscape mode: allowed orientations (if you check none, both are allowed).\n"
              + "• Text selection, pinch to zoom, pull to refresh.\n"
              + "• Fullscreen: hides the system bars.\n"
              + "• Keep screen on: handy for games and presentations.\n"
              + "• Adapt to mobile: adds the viewport if the site is not mobile-ready.\n"
              + "• Theme: dark, light or system.\n"
              + "• Open delay: shows a background-color curtain while loading (0 = no delay)."));

        card(s("Firma (Ajustes)", "Signing (Settings)"),
            s("Todo APK debe ir firmado. Por defecto se usa una clave de debug pública incluida en la app: sirve para probar y repartir, pero no para publicar en tiendas.\n"
              + "Para usar la tuya: Ajustes → Cambiar keystore… (.jks, .keystore o .p12), escribe alias y contraseña una sola vez y quedan recordados.\n"
              + "Las apps firmadas con claves distintas no se pueden actualizar unas sobre otras: hay que desinstalar la anterior.",
              "Every APK must be signed. By default a public debug key bundled in the app is used: fine for testing and sharing, not for publishing to stores.\n"
              + "To use yours: Settings → Change keystore… (.jks, .keystore or .p12), enter alias and password once and they are remembered.\n"
              + "Apps signed with different keys cannot be updated over each other: uninstall the old one first."));

        card(s("Instalar, compartir y guardar", "Install, share and save"),
            s("• Los APK generados aparecen en la lista inferior. Tócalos para Instalar, Compartir, Guardar como… o Borrar.\n"
              + "• Marca varios para compartirlos o borrarlos de una vez.\n"
              + "• Se guardan en el almacenamiento privado de ApkForge (se pierden al desinstalarlo): usa Compartir o Guardar como… para conservarlos.\n"
              + "• La primera vez, Android te pedirá permitir «instalar apps desconocidas» para ApkForge.",
              "• Generated APKs appear in the list below. Tap one to Install, Share, Save as… or Delete.\n"
              + "• Check several to share or delete them at once.\n"
              + "• They are stored in ApkForge's private storage (lost if you uninstall it): use Share or Save as… to keep them.\n"
              + "• The first time, Android will ask you to allow “install unknown apps” for ApkForge."));

        card(s("Si algo falla", "Troubleshooting"),
            s("• Pantalla en blanco al abrir: casi siempre rutas absolutas (/js/app.js) o mayúsculas distintas; usa rutas relativas.\n"
              + "• «No hay ningún archivo .html»: falta index.html o el ZIP está vacío o anidado.\n"
              + "• Error de contraseña: revisa alias y contraseñas en Ajustes.\n"
              + "• No se instala encima: la firma o el paquete son distintos; desinstala la versión anterior.\n"
              + "• Se pasa de tamaño: reduce o comprime imágenes y audio (límite 400 MB).",
              "• Blank screen on launch: almost always absolute paths (/js/app.js) or different letter case; use relative paths.\n"
              + "• “No .html file found”: index.html is missing, or the ZIP is empty or nested.\n"
              + "• Password error: check the alias and passwords in Settings.\n"
              + "• Will not install over the old one: signature or package differ; uninstall the previous version.\n"
              + "• Too big: shrink or compress images and audio (400 MB limit)."));
    }

    private void card(String title, String body) { card(title, body, null, null); }

    /** body (texto), code (bloque monoespaciado opcional) y tail (texto tras el bloque). */
    private void card(String title, String body, String code, String tail) {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, title, 16, Ui.TEXT, true));
        TextView t = Ui.text(this, body, 14, Ui.MUTED, false);
        t.setLineSpacing(0, 1.15f); t.setPadding(0, Ui.dp(this, 8), 0, 0);
        c.addView(t);
        if (code != null) {
            TextView k = Ui.text(this, code, 13, Ui.TEXT, false);
            k.setTypeface(Typeface.MONOSPACE);
            k.setBackground(Ui.round(Ui.CARD2, 10, this));
            int pd = Ui.dp(this, 12); k.setPadding(pd, pd, pd, pd);
            k.setTextIsSelectable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = Ui.dp(this, 10); lp.bottomMargin = Ui.dp(this, 10);
            c.addView(k, lp);
        }
        if (tail != null) {
            TextView t2 = Ui.text(this, tail, 14, Ui.MUTED, false);
            t2.setLineSpacing(0, 1.15f);
            c.addView(t2);
        }
        root.addView(c);
    }

}
