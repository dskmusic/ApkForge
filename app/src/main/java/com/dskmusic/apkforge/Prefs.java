package com.dskmusic.apkforge;

import android.content.Context;
import android.content.SharedPreferences;

import com.dskmusic.apkforge.core.Lang;

/** Ajustes persistentes. Las contraseñas se guardan una vez en el almacenamiento privado de la app. */
final class Prefs {
    private final SharedPreferences sp;
    private final boolean priv;
    private final Context ctx;
    Prefs(Context c) { ctx = c.getApplicationContext(); sp = c.getSharedPreferences("apkforge", Context.MODE_PRIVATE); priv = KeystoreStore.hasPrivate(c); }
    // Sin keystore privado (build de GitHub) el de debug va con alias/clave públicos: ya funciona sin configurar nada.
    String alias() { return sp.getString("alias", priv ? "dskmusic" : "androiddebugkey"); }
    String storePass() { return sp.getString("storePass", priv ? "" : "android"); }
    String keyPass() { return sp.getString("keyPass", ""); }
    /** Prefijo de paquete: el guardado en Ajustes; por defecto com.dskmusic en tu build local y com.<nombre del dispositivo> en el de GitHub. */
    String prefix() { return sp.getString("prefix", BuildConfig.GOOF ? "com.dskmusic" : devicePrefix()); }
    private String devicePrefix() {
        String n = null;
        try { n = android.provider.Settings.Global.getString(ctx.getContentResolver(), "device_name"); } catch (Exception e) { }
        if (n == null || n.trim().length() == 0) n = android.os.Build.MODEL;
        return "com." + com.dskmusic.apkforge.core.Names.slugify(n);
    }
    String ksName() { return Lang.t(sp.getString("ksName", defaultKsName())); }
    /** "auto" | "es" | "en" */
    String lang() { return sp.getString("lang", "auto"); }
    void setLang(String l) { sp.edit().putString("lang", l).apply(); }
    /** Aplica el idioma elegido (auto = el del sistema; si no es español, inglés). */
    static void applyLang(Context c) { Lang.set(new Prefs(c).lang(), java.util.Locale.getDefault().getLanguage()); }
    void saveKeystore(String alias, String storePass, String keyPass, String prefix) {
        sp.edit().putString("alias", alias).putString("storePass", storePass).putString("keyPass", keyPass).putString("prefix", prefix).apply();
    }
    String defaultKsName() { return priv ? "dskmusic.keystore (incluido)" : "debug.keystore (por defecto)"; }
    void setKsName(String n) { sp.edit().putString("ksName", n).apply(); }
}
