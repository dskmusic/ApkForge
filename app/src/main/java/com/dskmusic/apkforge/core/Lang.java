package com.dskmusic.apkforge.core;

import java.util.*;
import java.util.regex.*;

/**
 * Idioma de la app. Los textos del código están en español y t() los traduce al inglés cuando toca:
 * coincidencia exacta (a) o por patrón (rx) para textos con partes variables. Lo que no esté en la tabla se queda tal cual.
 * Para añadir otro idioma: una segunda tabla y un parámetro más en t().
 */
public final class Lang {
    private static volatile boolean es = false;
    private static final Map<String, String> M = new HashMap<String, String>();
    private static final List<Object[]> RX = new ArrayList<Object[]>();

    /** pref: "auto", "es" o "en". systemLang: idioma del sistema ("es", "en"…). Auto = español solo si el sistema es español; si no, inglés. */
    public static void set(String pref, String systemLang) {
        es = "es".equals(pref) || (!"en".equals(pref) && "es".equalsIgnoreCase(systemLang));
    }

    public static boolean isEs() { return es; }

    public static String t(String s) {
        if (es || s == null) return s;
        String e = M.get(s);
        if (e != null) return e;
        for (Object[] r : RX) {
            Matcher m = ((Pattern) r[0]).matcher(s);
            if (m.matches()) return m.replaceAll((String) r[1]);
        }
        return s;
    }

    private static void a(String es, String en) { M.put(es, en); }
    private static void rx(String regex, String en) { RX.add(new Object[]{Pattern.compile(regex, Pattern.DOTALL), en}); }
    /** Prefijo fijo + resto variable. */
    private static void pre(String es, String en) { rx(Pattern.quote(es) + "(.*)", Matcher.quoteReplacement(en) + "$1"); }

    static {
        // ---- pantalla principal
        a("⚙ Ajustes", "⚙ Settings");
        a("Convierte tu web, juego o enlace en un APK firmado, sin Android Studio.", "Turn your website, game or link into a signed APK, no Android Studio needed.");
        a("Convierte tu web o tu proyecto HTML en un APK firmado, sin Android Studio.", "Turn your website or HTML project into a signed APK, no Android Studio needed.");
        a("1 · Origen", "1 · Source");
        a("Carpeta", "Folder");
        a("Enlace goof", "Goof link");
        a("Ningún archivo elegido", "No file chosen");
        a("Ninguna carpeta elegida", "No folder chosen");
        a("2 · Datos de la app", "2 · App details");
        a("Nombre de la app", "App name");
        rx("Paquete \\(opcional; por defecto (.*)\\.<nombre>\\)", "Package (optional; default $1.<name>)");
        a("Versión", "Version");
        a("3 · Icono", "3 · Icon");
        a("Elegir…", "Choose…");
        a("Quitar", "Remove");
        a("En modo web, usar el icono (favicon) de la web", "In web mode, use the site's icon (favicon)");
        a("4 · Opciones", "4 · Options");
        a("Quitar splash y aviso de dominio de goof", "Remove goof splash and domain notice");
        a("Permitir modo vertical", "Allow portrait mode");
        a("Permitir modo horizontal", "Allow landscape mode");
        a("Selección de texto (pulsación larga)", "Text selection (long press)");
        a("Zoom con dos dedos", "Pinch to zoom");
        a("Arrastrar hacia abajo para recargar", "Pull down to refresh");
        a("Pantalla completa (oculta barras)", "Fullscreen (hides bars)");
        a("Mantener pantalla encendida", "Keep screen on");
        a("Adaptar a móvil si la web no lo está", "Adapt to mobile if the site is not");
        a("Tema", "Theme");
        a("Oscuro (por defecto)", "Dark (default)");
        a("Claro", "Light");
        a("Según el sistema", "Follow system");
        a("Retardo al abrir (ms; cortina del color de fondo mientras carga; 0 = sin retardo)", "Open delay (ms; background-color curtain while loading; 0 = no delay)");
        a("⚒  Generar APK", "⚒  Generate APK");
        a("Generando…", "Generating…");
        a("Instalar", "Install");
        a("Guardar como…", "Save as…");
        a("Compartir", "Share");
        a("↺ Empezar de nuevo", "↺ Start over");
        a("Elegir ZIP o APK…", "Choose ZIP or APK…");
        a("Con index.html en la raíz del ZIP (o dentro de una carpeta).", "With index.html at the ZIP root (or inside a folder).");
        a(" También vale el .apk de goof.", " A goof .apk also works.");
        a(" Funciona sin conexión.", " Works offline.");
        a("Elegir carpeta…", "Choose folder…");
        a("Se incluye todo lo de esa carpeta y de sus subcarpetas; la raíz es la del index.html más cercano.", "Everything in that folder and its subfolders is included; the root is the nearest index.html.");
        a("Enlace del juego", "Game link");
        a("Descarga el juego y sus recursos (necesita Internet al generar). Usa tus propios juegos.", "Downloads the game and its assets (needs Internet while generating). Use your own games.");
        a("URL de la web (la app la abrirá online)", "Website URL (the app will open it online)");
        a("🔍 Buscar icono de la web", "🔍 Find site icon");
        a("Icono propio", "Custom icon");
        a("Icono de la web (si no eliges otro)", "Site icon (unless you choose another)");
        a("Por defecto (DSK Apps)", "Default (DSK Apps)");
        a("Imagen demasiado grande", "Image too large");
        a("Icono", "Icon");
        a("El archivo no es una imagen válida", "The file is not a valid image");
        a("Guardado", "Saved");
        a("Escribe primero la URL de la web", "Enter the website URL first");
        a("Buscando icono…", "Looking for icon…");
        rx("No se encontró icono( \\(.*\\))?\\. Se usará el tuyo o el de por defecto\\.", "No icon found$1. Your own or the default one will be used.");
        rx("El icono de la web es de baja calidad \\((\\d+) px < 128\\)\\. Se usará el tuyo o el de por defecto\\.", "The site icon is low quality ($1 px < 128). Your own or the default one will be used.");
        a("Falta el nombre", "Name missing");
        a("Escribe el nombre de la app.", "Enter the app name.");
        a("Falta el archivo", "File missing");
        a("Elige un ZIP o APK.", "Choose a ZIP or APK.");
        a("Falta la carpeta", "Folder missing");
        a("Elige una carpeta.", "Choose a folder.");
        a("Falta el enlace", "Link missing");
        a("Pega el enlace de goof.gg.", "Paste the goof.gg link.");
        a("Falta la URL", "URL missing");
        a("Escribe la URL de la web.", "Enter the website URL.");
        a("Contraseña del keystore", "Keystore password");
        a("Aún no has guardado la contraseña del keystore. Se pide una sola vez en Ajustes.", "You have not saved the keystore password yet. It is asked only once, in Settings.");
        a("Ir a Ajustes", "Go to Settings");
        a("Cargando keystore…", "Loading keystore…");
        a("Leyendo el archivo…", "Reading file…");
        a("Copiando la carpeta…", "Copying folder…");
        pre("Icono web no disponible: ", "Site icon unavailable: ");
        a("\n\nRevisa la contraseña y el alias del keystore en Ajustes.", "\n\nCheck the keystore password and alias in Settings.");
        rx("✔ (.*) listo \\((.*)\\)", "✔ $1 ready ($2)");
        a("APK listo", "APK ready");
        a("Cerrar", "Close");
        a("Permiso necesario", "Permission required");
        a("Activa «Permitir de esta fuente» para ApkForge y vuelve a pulsar Instalar.", "Enable “Allow from this source” for ApkForge and tap Install again.");
        a("No se pudo instalar", "Could not install");
        a("APKs generados", "Generated APKs");
        a("Aún no hay ninguno.", "None yet.");
        a("Seleccionar todos", "Select all");
        a("Borrar", "Delete");
        rx("Borrar \\((\\d+)\\)", "Delete ($1)");
        a("Borrar APK", "Delete APK");
        rx("¿Borrar (\\d+) APK seleccionados?\\? No se puede deshacer\\.\n\n(.*)", "Delete $1 selected APK(s)? This cannot be undone.\n\n$2");
        rx("¿Borrar (.*)\\?", "Delete $1?");
        a("Aceptar", "OK");
        a("Cancelar", "Cancel");
        a("Usar", "Use");
        a("Confirmar al salir", "Confirm on exit");
        pre("Instalación: ", "Installation: ");
        a("Compartir APK", "Share APK");

        // ---- ajustes
        a("Ajustes", "Settings");
        a("Ayuda", "Help");
        a("Salir", "Exit");
        a("¿Quieres cerrar ApkForge?", "Do you want to close ApkForge?");
        a("Se está generando un APK. ¿Salir igualmente?", "An APK is being generated. Exit anyway?");
        a("Idioma", "Language");
        a("Automático (sistema)", "Automatic (system)");
        a("Firma (keystore)", "Signing (keystore)");
        a("Keystore en uso", "Keystore in use");
        a("Cambiar keystore…", "Change keystore…");
        a("Usar el de por defecto", "Use the default");
        a("Alias de la clave", "Key alias");
        a("Contraseña del keystore (se guarda una sola vez)", "Keystore password (saved only once)");
        a("Contraseña de la clave (vacío = la misma del keystore)", "Key password (empty = same as keystore)");
        a("opcional", "optional");
        a("Paquete", "Package");
        a("Prefijo de los paquetes (p. ej. com.dskmusic → com.dskmusic.nombre)", "Package prefix (e.g. com.dskmusic → com.dskmusic.name)");
        a("Guardar y comprobar", "Save and verify");
        a("Las contraseñas se guardan en el almacenamiento privado de la app. Por defecto se firma con una clave de debug pública (como la de Android): sirve para probar, pero cualquiera podría firmar «actualizaciones» de tus apps. Para publicar, importa tu propio keystore.",
          "Passwords are stored in the app's private storage. By default apps are signed with a public debug key (like Android's): fine for testing, but anyone could sign “updates” of your apps. To publish, import your own keystore.");
        a("Prefijo no válido", "Invalid prefix");
        a("Ejemplo: com.dskmusic", "Example: com.dskmusic");
        pre("Keystore correcto. Certificado: ", "Keystore OK. Certificate: ");
        a("No se pudo abrir el keystore", "Could not open the keystore");
        a("\n\nNo se ha guardado la contraseña.", "\n\nThe password was not saved.");
        a("Keystore importado", "Keystore imported");
        a("Escribe su alias y contraseña y pulsa «Guardar y comprobar».", "Enter its alias and password and tap “Save and verify”.");
        a("dskmusic.keystore (incluido)", "dskmusic.keystore (bundled)");
        a("debug.keystore (por defecto)", "debug.keystore (default)");
        a("keystore importado", "imported keystore");

        // ---- núcleo (errores y registro)
        a("La plantilla base no contiene iconos reconocibles", "The base template has no recognizable icons");
        a("El nombre de la app debe tener entre 1 y 60 caracteres", "The app name must be 1 to 60 characters");
        pre("Nombre de paquete no válido: ", "Invalid package name: ");
        a("versionCode fuera de rango", "versionCode out of range");
        rx("Limpieza goof: (\\d+) bloques quitados \\(splash/aviso de dominio\\)", "Goof cleanup: $1 blocks removed (splash/domain notice)");
        rx("Falta (.*) en la plantilla base", "Missing $1 in the base template");
        a("AXML: pool de strings no encontrado", "AXML: string pool not found");
        a("AXML: pool con estilos no soportado", "AXML: style pool not supported");
        a("AndroidManifest no es AXML", "AndroidManifest is not AXML");
        pre("Plantilla base: faltan marcadores ", "Base template: missing markers ");
        a("No se encontró versionCode en el manifest base", "versionCode not found in the base manifest");
        a("resources.arsc inválido", "Invalid resources.arsc");
        a("Nombre de paquete demasiado largo", "Package name too long");
        a("resources.arsc: no se encontró la tabla de paquete", "resources.arsc: package table not found");
        a("El archivo no es un ZIP válido", "The file is not a valid ZIP");
        pre("Ruta no permitida en el ZIP: ", "Path not allowed in ZIP: ");
        a("El ZIP tiene demasiados archivos", "The ZIP has too many files");
        rx("El contenido supera el límite de (\\d+) MB", "Content exceeds the $1 MB limit");
        a("No hay ningún archivo .html", "No .html file found");
        a("Enlace no válido (https://goof.gg/game/… o la URL del juego)", "Invalid link (https://goof.gg/game/… or the game URL)");
        a("No encuentro el id del juego en esa página", "Cannot find the game id on that page");
        a("Demasiados recursos", "Too many assets");
        rx("Ronda (\\d+): (\\d+) recursos", "Round $1: $2 assets");
        rx("El juego supera el límite de (\\d+) MB", "The game exceeds the $1 MB limit");
        pre("FALLO (se pedirá al abrir la app) ", "FAILED (will be requested when the app opens) ");
        rx("Descargado: (.*) MB, (\\d+) recursos, (\\d+) no encontrados", "Downloaded: $1 MB, $2 assets, $3 not found");
        a("El keystore no tiene claves privadas", "The keystore has no private keys");
        rx("No existe el alias «(.*)»\\. Disponibles: (.*)", "Alias “$1” does not exist. Available: $2");
        a("El alias no contiene una clave privada", "The alias does not contain a private key");
        a("Keystore JKS no válido", "Invalid JKS keystore");
        a("Contraseña del keystore incorrecta (o archivo dañado)", "Wrong keystore password (or damaged file)");
        a("Keystore JKS con entradas no reconocidas", "JKS keystore with unrecognized entries");
        a("Clave JKS no válida", "Invalid JKS key");
        a("Contraseña de la clave (alias) incorrecta", "Wrong key (alias) password");
        a("Tipo de clave no soportado", "Unsupported key type");
        a("DER inesperado", "Unexpected DER");
        a("La URL de la web debe empezar por http:// o https://", "The website URL must start with http:// or https://");
        pre("Modo web: ", "Web mode: ");
        rx("Entrada: (.*) \\| archivos: (\\d+)", "Entry: $1 | files: $2");
        pre("Paquete: ", "Package: ");
        rx("Listo: (.*) \\((\\d+) KB\\)", "Done: $1 ($2 KB)");
        a("Juego de goof con recursos externos: se completan desde el servidor de goof", "Goof game with external assets: completing from the goof server");
        rx("Aviso: no pude completar desde goof \\((.*)\\); se usa el archivo tal cual", "Notice: could not complete from goof ($1); using the file as is");
        pre("Falló la firma: ", "Signing failed: ");
        a("URL no válida", "Invalid URL");
        a("Dirección no permitida", "Address not allowed");
        a("Archivo demasiado grande", "File too large");
        a("Demasiadas redirecciones", "Too many redirects");
        a("La carpeta tiene demasiados archivos", "The folder has too many files");
        rx("La carpeta supera el límite de (\\d+) MB", "The folder exceeds the $1 MB limit");
        a("El icono no es una imagen válida (usa PNG o JPG)", "The icon is not a valid image (use PNG or JPG)");
        a("No se pudo leer el archivo", "Could not read the file");
        a("Archivo demasiado grande para ser un keystore", "File too large to be a keystore");
        a("No se pudo guardar el keystore", "Could not save the keystore");
    }

    private Lang() { }
}
