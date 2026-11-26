package com.dskmusic.shell;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.widget.Toast;

import java.io.*;
import java.util.*;

/** Guarda archivos descargados por la web en Imágenes / Vídeos / Música / Descargas (MediaStore). */
class Saver {
    private final Activity act;
    private final String folder;
    private final List<Object[]> pending = new ArrayList<Object[]>();

    Saver(Activity a, String appName) {
        act = a;
        folder = clean(appName);
    }

    static String clean(String s) {
        s = s.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", " ").trim();
        if (s.length() == 0) s = "App";
        return s.length() > 60 ? s.substring(0, 60) : s;
    }

    /** "<App>_yyyyMMdd_HHmmss.ext": todo se guarda en Descargas/<App> con este nombre. */
    private String stamped(String name) {
        int dot = name.lastIndexOf('.');
        String ext = dot > 0 && name.length() - dot <= 6 ? name.substring(dot) : "";
        return folder + "_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ext;
    }

    String save(File tmp, String name, String mime, boolean share) {
        name = stamped(name);
        try {
            if (Build.VERSION.SDK_INT >= 29) return saveQ(tmp, name, mime, share);
            if (Build.VERSION.SDK_INT >= 23
                    && act.checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED) {
                synchronized (pending) { pending.add(new Object[]{tmp, name, mime, Boolean.valueOf(share)}); }
                act.runOnUiThread(new Runnable() {
                    public void run() {
                        act.requestPermissions(new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"}, MainActivity.REQ_STORAGE);
                    }
                });
                return "pending";
            }
            return saveLegacy(tmp, name, mime, share);
        } catch (Exception e) {
            tmp.delete();
            toast("No se pudo guardar: " + e.getMessage());
            return "err:" + e.getMessage();
        }
    }

    void onStoragePermission(boolean ok) {
        List<Object[]> list;
        synchronized (pending) { list = new ArrayList<Object[]>(pending); pending.clear(); }
        for (final Object[] p : list) {
            final File tmp = (File) p[0];
            if (!ok) { tmp.delete(); continue; }
            new Thread(new Runnable() {
                public void run() {
                    try { saveLegacy(tmp, (String) p[1], (String) p[2], ((Boolean) p[3]).booleanValue()); }
                    catch (Exception e) { tmp.delete(); toast("No se pudo guardar: " + e.getMessage()); }
                }
            }).start();
        }
        if (!ok) toast("Sin permiso de almacenamiento no se puede guardar");
    }

    private String saveQ(File tmp, String name, String mime, boolean share) throws IOException {
        Uri collection = Uri.parse("content://media/external/downloads");
        String dir = Environment.DIRECTORY_DOWNLOADS;
        ContentResolver cr = act.getContentResolver();
        ContentValues cv = new ContentValues();
        cv.put("_display_name", name);
        cv.put("mime_type", mime);
        cv.put("relative_path", dir + "/" + folder);
        cv.put("is_pending", 1);
        Uri uri = cr.insert(collection, cv);
        if (uri == null) throw new IOException("MediaStore rechazó el archivo");
        OutputStream os = cr.openOutputStream(uri);
        try { copy(new FileInputStream(tmp), os); } finally { try { os.close(); } catch (IOException e) { } }
        cv.clear();
        cv.put("is_pending", 0);
        cr.update(uri, cv, null, null);
        tmp.delete();
        toast("Guardado en " + dir + "/" + folder);
        if (share) shareUri(uri, mime);
        return "ok:" + dir + "/" + folder + "/" + name;
    }

    private String saveLegacy(File tmp, String name, String mime, boolean share) throws IOException {
        String dir = Environment.DIRECTORY_DOWNLOADS;
        File d = new File(Environment.getExternalStoragePublicDirectory(dir), folder);
        d.mkdirs();
        File f = new File(d, name);
        int n = 1;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        while (f.exists() && n < 1000) { f = new File(d, base + " (" + n + ")" + ext); n++; }
        copy(new FileInputStream(tmp), new FileOutputStream(f));
        tmp.delete();
        final String m = mime;
        final boolean sh = share;
        MediaScannerConnection.scanFile(act, new String[]{f.getAbsolutePath()}, new String[]{mime},
                new MediaScannerConnection.OnScanCompletedListener() {
                    public void onScanCompleted(String path, Uri uri) { if (sh && uri != null) shareUri(uri, m); }
                });
        toast("Guardado en " + dir + "/" + folder);
        return "ok:" + f.getAbsolutePath();
    }

    void shareUri(final Uri uri, final String mime) {
        act.runOnUiThread(new Runnable() {
            public void run() {
                try {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType(mime);
                    i.putExtra(Intent.EXTRA_STREAM, uri);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    act.startActivity(Intent.createChooser(i, null));
                } catch (Exception e) { }
            }
        });
    }

    void toast(final String t) {
        act.runOnUiThread(new Runnable() { public void run() { Toast.makeText(act, t, Toast.LENGTH_LONG).show(); } });
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        try {
            byte[] b = new byte[65536];
            int r;
            while ((r = in.read(b)) >= 0) out.write(b, 0, r);
            out.flush();
        } finally {
            try { in.close(); } catch (IOException e) { }
        }
    }
}
