package com.dskmusic.apkforge;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/** Busca el icono de una web (apple-touch-icon, icon, manifest, favicon.ico). Port de iconfetch.py. */
final class IconFetch {
    static final int GOOD_PX = 128;
    static final class Result { byte[] png; int size; String source; boolean good; }

    private static byte[] get(String url, String[] finalUrl) throws Exception {
        for (int hop = 0; hop < 5; hop++) {
            URL u = new URL(url);
            if (!u.getProtocol().equals("http") && !u.getProtocol().equals("https")) throw new Exception("URL no válida");
            for (InetAddress a : InetAddress.getAllByName(u.getHost()))
                if (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress() || a.isMulticastAddress())
                    throw new Exception("Dirección no permitida");
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(10000); c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (ApkForge icon fetch)");
            try {
                int code = c.getResponseCode();
                if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) { url = new URL(u, c.getHeaderField("Location")).toString(); continue; }
                if (code != 200) throw new Exception("HTTP " + code);
                InputStream in = c.getInputStream();
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] b = new byte[16384]; int n;
                while ((n = in.read(b)) >= 0) { bo.write(b, 0, n); if (bo.size() > 3 << 20) throw new Exception("Archivo demasiado grande"); }
                in.close();
                if (finalUrl != null) finalUrl[0] = url;
                return bo.toByteArray();
            } finally { c.disconnect(); }
        }
        throw new Exception("Demasiadas redirecciones");
    }

    private static int sizeOf(String s) {
        Matcher m = Pattern.compile("(\\d+)x\\d+").matcher(s == null ? "" : s);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    static Result find(String site) throws Exception {
        if (!site.toLowerCase(Locale.ROOT).startsWith("http")) site = "https://" + site;
        String[] fu = new String[1];
        String html = new String(get(site, fu), "UTF-8");
        String base = fu[0];
        List<Object[]> cands = new ArrayList<Object[]>();   // {size, url, source}
        Matcher lm = Pattern.compile("<link\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html);
        String manifest = null;
        while (lm.find()) {
            String tag = lm.group();
            String rel = attr(tag, "rel").toLowerCase(Locale.ROOT), href = attr(tag, "href");
            if (href.length() == 0) continue;
            if (rel.contains("manifest")) manifest = href;
            else if (rel.contains("icon")) {
                int sz = sizeOf(attr(tag, "sizes"));
                if (rel.contains("apple-touch-icon") && sz == 0) sz = 180;
                cands.add(new Object[]{sz, new URL(new URL(base), href).toString(), "link"});
            }
        }
        if (manifest != null) {
            try {
                String mu = new URL(new URL(base), manifest).toString();
                JSONArray ic = new JSONObject(new String(get(mu, null), "UTF-8")).optJSONArray("icons");
                for (int i = 0; ic != null && i < ic.length(); i++) {
                    JSONObject o = ic.getJSONObject(i);
                    String[] ss = o.optString("sizes").split(" ");
                    cands.add(new Object[]{sizeOf(ss[ss.length - 1]), new URL(new URL(mu), o.optString("src")).toString(), "manifest"});
                }
            } catch (Exception e) { }
        }
        cands.add(new Object[]{0, new URL(new URL(base), "/favicon.ico").toString(), "favicon.ico"});
        Collections.sort(cands, new Comparator<Object[]>() { public int compare(Object[] a, Object[] b) { return (Integer) b[0] - (Integer) a[0]; } });
        Result best = null; int tried = 0;
        for (Object[] c : cands) {
            if (tried++ >= 8) break;
            try {
                byte[] d = get((String) c[1], null);
                Bitmap bm = BitmapFactory.decodeByteArray(d, 0, d.length);
                if (bm == null) continue;
                int px = Math.min(bm.getWidth(), bm.getHeight());
                if (best == null || px > best.size) {
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    bm.compress(Bitmap.CompressFormat.PNG, 100, bo);
                    best = new Result(); best.png = bo.toByteArray(); best.size = px; best.source = c[2] + " " + c[1]; best.good = px >= GOOD_PX;
                }
                if (px >= GOOD_PX) break;
            } catch (Exception e) { }
        }
        return best;
    }

    private static String attr(String tag, String name) {
        Matcher m = Pattern.compile("\\b" + name + "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", Pattern.CASE_INSENSITIVE).matcher(tag);
        if (!m.find()) return "";
        return m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3);
    }
}
