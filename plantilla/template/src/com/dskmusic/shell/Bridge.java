package com.dskmusic.shell;

import android.app.Activity;
import android.content.Intent;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import java.io.*;
import java.util.*;

/** Puente JS -> Android ("ForgeNative"): guardar archivos por trozos, compartir texto, toast. */
class Bridge {
    private final Activity act;
    private final Saver saver;
    private final String appName;
    private final String options;
    private final Map<String, Object[]> open = new HashMap<String, Object[]>();
    private final Random rnd = new Random();

    Bridge(Activity a, Saver s, String appName, String options) {
        this.options = options;
        this.act = a;
        this.saver = s;
        this.appName = appName;
    }

    private boolean ok() { return !(act instanceof MainActivity) || ((MainActivity) act).isTrusted(); }

    @JavascriptInterface
    public synchronized String begin(String name, String mime) {
        if (!ok()) return "";
        try {
            String id = Long.toHexString(System.nanoTime()) + Integer.toHexString(rnd.nextInt());
            File f = File.createTempFile("dl_", ".tmp", act.getCacheDir());
            open.put(id, new Object[]{f, new FileOutputStream(f), name, mime});
            return id;
        } catch (IOException e) {
            return "";
        }
    }

    @JavascriptInterface
    public synchronized boolean chunk(String id, String b64) {
        Object[] o = open.get(id);
        if (o == null) return false;
        try {
            ((OutputStream) o[1]).write(Base64.decode(b64, Base64.DEFAULT));
            return true;
        } catch (Exception e) {
            abort(id);
            return false;
        }
    }

    @JavascriptInterface
    public synchronized String end(String id, boolean share) {
        Object[] o = open.remove(id);
        if (o == null) return "err:id";
        File f = (File) o[0];
        try { ((OutputStream) o[1]).close(); } catch (IOException e) { }
        String name = fixName((String) o[2], (String) o[3]);
        String mime = guessMime(name, (String) o[3]);
        return saver.save(f, name, mime, share);
    }

    @JavascriptInterface
    public synchronized void abort(String id) {
        Object[] o = open.remove(id);
        if (o == null) return;
        try { ((OutputStream) o[1]).close(); } catch (IOException e) { }
        ((File) o[0]).delete();
    }

    @JavascriptInterface
    public void shareText(String text) {
        if (!ok()) return;
        final String t = text == null ? "" : text;
        act.runOnUiThread(new Runnable() {
            public void run() {
                try {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_TEXT, t);
                    act.startActivity(Intent.createChooser(i, null));
                } catch (Exception e) { }
            }
        });
    }

    @JavascriptInterface
    public void toast(String t) { if (ok()) saver.toast(t); }

    @JavascriptInterface
    public String appName() { return appName; }

    @JavascriptInterface
    public String options() { return options; }

    private static String fixName(String name, String mime) {
        if (name == null) name = "";
        name = name.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_").trim();
        if (name.length() > 120) name = name.substring(name.length() - 120);
        if (name.length() == 0) name = "descarga";
        if (name.lastIndexOf('.') < 0 && mime != null && mime.length() > 0) {
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            if (ext != null) name = name + "." + ext;
        }
        return name;
    }

    private static String guessMime(String name, String mime) {
        if (mime != null && mime.length() > 0 && !mime.equals("application/octet-stream")) {
            int sc = mime.indexOf(';');
            return sc > 0 ? mime.substring(0, sc).trim() : mime;
        }
        int d = name.lastIndexOf('.');
        if (d >= 0) {
            String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(d + 1).toLowerCase(Locale.US));
            if (m != null) return m;
        }
        return "application/octet-stream";
    }
}
