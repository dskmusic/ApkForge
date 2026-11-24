package com.dskmusic.apkforge;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.io.*;

/** Instalación del APK generado con PackageInstaller (el sistema pide confirmación al usuario). */
public final class Installer {
    static final String ACTION = "com.dskmusic.apkforge.INSTALL_RESULT";

    /** @return true si ya se puede instalar; false si se abrió el ajuste «instalar apps desconocidas». */
    static boolean ensureAllowed(Activity a) {
        if (Build.VERSION.SDK_INT >= 26) {
            boolean ok = true;
            try { ok = (Boolean) a.getPackageManager().getClass().getMethod("canRequestPackageInstalls").invoke(a.getPackageManager()); } catch (Exception e) { }
            if (!ok) {
                Intent i = new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", Uri.parse("package:" + a.getPackageName()));
                a.startActivity(i);
                return false;
            }
        }
        return true;
    }

    static void install(Context c, File apk) throws IOException {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        int id = pi.createSession(sp);
        PackageInstaller.Session s = pi.openSession(id);
        try {
            OutputStream out = s.openWrite("app.apk", 0, apk.length());
            InputStream in = new FileInputStream(apk);
            try { byte[] b = new byte[65536]; int n; while ((n = in.read(b)) >= 0) out.write(b, 0, n); s.fsync(out); } finally { in.close(); out.close(); }
            Intent it = new Intent(c, Receiver.class).setAction(ACTION);
            int flags = Build.VERSION.SDK_INT >= 31 ? (1 << 25) : 0;   // FLAG_MUTABLE
            PendingIntent pend = PendingIntent.getBroadcast(c, id, it, flags);
            s.commit(pend.getIntentSender());
        } catch (IOException e) { s.abandon(); throw e; }
    }

    /** Recibe el estado de la instalación y lanza la pantalla de confirmación del sistema. */
    public static final class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
            if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = (Intent) i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) { confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(confirm); }
            } else if (st != PackageInstaller.STATUS_SUCCESS) {
                String m = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                android.widget.Toast.makeText(c, "Instalación: " + (m == null ? "error " + st : m), android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }
}
