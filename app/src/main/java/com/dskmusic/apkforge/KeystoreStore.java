package com.dskmusic.apkforge;

import android.content.Context;
import android.net.Uri;

import java.io.*;

import com.dskmusic.apkforge.core.Files2;

/** Keystore de firma: el incluido (assets: el privado del autor si existe, si no el de debug) o uno importado por el usuario desde Ajustes. */
final class KeystoreStore {
    private final Context ctx;
    KeystoreStore(Context c) { ctx = c.getApplicationContext(); }
    private File file() { return new File(ctx.getFilesDir(), "keystore.bin"); }

    byte[] read() throws IOException {
        File f = file();
        if (!f.exists()) restoreBundled();
        return Files2.readAll(f);
    }

    /** true si este build lleva el keystore privado del autor (local, fuera de git); si no, se usa el de debug público. */
    static boolean hasPrivate(Context c) {
        try { c.getAssets().open("dskmusic.keystore").close(); return true; } catch (IOException e) { return false; }
    }

    void restoreBundled() throws IOException {
        InputStream in = ctx.getAssets().open(hasPrivate(ctx) ? "dskmusic.keystore" : "debug.keystore");
        OutputStream out = new FileOutputStream(file());
        try { byte[] b = new byte[8192]; int n; while ((n = in.read(b)) >= 0) out.write(b, 0, n); } finally { in.close(); out.close(); }
    }

    void importFrom(Uri uri) throws IOException {
        InputStream in = ctx.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("No se pudo leer el archivo");
        File tmp = new File(ctx.getFilesDir(), "keystore.tmp");
        OutputStream out = new FileOutputStream(tmp);
        try {
            byte[] b = new byte[8192]; int n; long tot = 0;
            while ((n = in.read(b)) >= 0) { tot += n; if (tot > 2 << 20) throw new IOException("Archivo demasiado grande para ser un keystore"); out.write(b, 0, n); }
        } finally { in.close(); out.close(); }
        File f = file();
        f.delete();
        if (!tmp.renameTo(f)) throw new IOException("No se pudo guardar el keystore");
    }
}
