package com.dskmusic.apkforge;

import android.content.Context;
import android.content.SharedPreferences;

/** Ajustes persistentes. Las contraseñas se guardan una vez en el almacenamiento privado de la app. */
final class Prefs {
    private final SharedPreferences sp;
    private final boolean priv;
    Prefs(Context c) { sp = c.getSharedPreferences("apkforge", Context.MODE_PRIVATE); priv = KeystoreStore.hasPrivate(c); }
    // Sin keystore privado (build de GitHub) el de debug va con alias/clave públicos: ya funciona sin configurar nada.
    String alias() { return sp.getString("alias", priv ? "dskmusic" : "androiddebugkey"); }
    String storePass() { return sp.getString("storePass", priv ? "" : "android"); }
    String keyPass() { return sp.getString("keyPass", ""); }
    String prefix() { return sp.getString("prefix", "com.dskmusic"); }
    String ksName() { return sp.getString("ksName", defaultKsName()); }
    void saveKeystore(String alias, String storePass, String keyPass, String prefix) {
        sp.edit().putString("alias", alias).putString("storePass", storePass).putString("keyPass", keyPass).putString("prefix", prefix).apply();
    }
    String defaultKsName() { return priv ? "dskmusic.keystore (incluido)" : "debug.keystore (por defecto)"; }
    void setKsName(String n) { sp.edit().putString("ksName", n).apply(); }
}
