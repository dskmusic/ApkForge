package com.dskmusic.apkforge.core;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.*;

/** Descarga una web/PWA (https://dominio/carpeta/) a una carpeta para usarla sin conexión. Solo archivos de su misma carpeta. */
public final class WebCrawl {
    public static final class Result {
        public File dir;
        public int ok;
        public long bytes;
        public final List<String> failed = new ArrayList<String>();     // 404, error de red…
        public final Set<String> external = new TreeSet<String>();      // otros dominios u otras carpetas del mismo dominio
    }

    private static final Set<String> TEXT = new HashSet<String>(Arrays.asList("html", "htm", "js", "mjs", "css", "json", "svg", "webmanifest", "xml", "txt"));
    private static final Pattern ATTR = Pattern.compile("(?:src|href|data-src|poster)\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSSURL = Pattern.compile("url\\(\\s*[\"']?([^\"')]+)[\"']?\\s*\\)|@import\\s+[\"']([^\"']+)[\"']");
    private static final Pattern LIT = Pattern.compile("[\"'`]([^\"'`\\s]+\\.(?:js|mjs|css|json|png|jpe?g|gif|webp|svg|ico|mp3|wav|ogg|m4a|mp4|webm|woff2?|ttf|otf|wasm|txt|html|glb|gltf|bin|webmanifest))(?:\\?[^\"'`]*)?[\"'`]");

    private static byte[] get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[32768]; int n;
            while ((n = in.read(b)) >= 0) bo.write(b, 0, n);
            in.close();
            return bo.toByteArray();
        } finally { c.disconnect(); }
    }

    private static String ext(String p) {
        int d = p.lastIndexOf('.');
        return d < 0 || d < p.lastIndexOf('/') ? "" : p.substring(d + 1).toLowerCase(Locale.ROOT);
    }

    /** Referencias a archivos dentro de un texto (HTML, CSS, JS, JSON). */
    private static Set<String> refs(String t) {
        Set<String> out = new LinkedHashSet<String>();
        Matcher m = ATTR.matcher(t); while (m.find()) out.add(m.group(1));
        m = CSSURL.matcher(t); while (m.find()) out.add(m.group(1) != null ? m.group(1) : m.group(2));
        m = LIT.matcher(t); while (m.find()) out.add(m.group(1));
        return out;
    }

    /** Descarga a dest. Lanza ForgeException si no se puede ni leer la página de entrada. */
    public static Result crawl(File dest, String link, long maxBytes, int maxFiles, Logger log, AtomicBoolean cancel) throws ForgeException, IOException {
        URL u = new URL(link.trim());
        String origin = u.getProtocol() + "://" + u.getAuthority();
        String path = u.getPath().length() == 0 ? "/" : u.getPath();
        String entryName = "";
        if (!path.endsWith("/")) {
            String last = path.substring(path.lastIndexOf('/') + 1);
            if (last.indexOf('.') >= 0) { entryName = last; path = path.substring(0, path.lastIndexOf('/') + 1); } else path += "/";
        }
        final String base = path;                         // carpeta de la app en el servidor, p. ej. /apila2/
        byte[] idx;
        try { idx = get(origin + base + entryName); }
        catch (IOException e) { throw new ForgeException("No se pudo leer la página (" + e.getMessage() + ")"); }
        String idxText = new String(idx, "UTF-8");
        if (idxText.toLowerCase(Locale.ROOT).indexOf("<html") < 0 && idxText.toLowerCase(Locale.ROOT).indexOf("<!doctype") < 0)
            throw new ForgeException("Esa dirección no devuelve una página web");

        final Result res = new Result(); res.dir = dest;
        Files2.write(new File(dest, "index.html"), rewrite(idxText, base).getBytes("UTF-8"));
        res.bytes = idx.length; res.ok = 1;

        final Set<String> seen = new HashSet<String>();
        seen.add("index.html");
        List<String[]> queue = new ArrayList<String[]>();   // {ruta relativa, URL de contexto para resolver}
        addRefs(queue, seen, refs(idxText), origin, base, origin + base + entryName, res);
        ExecutorService ex = Executors.newFixedThreadPool(4);
        try {
            for (int rnd = 0; rnd < 6 && !queue.isEmpty(); rnd++) {
                if (cancel.get()) throw new ForgeException("Cancelado");
                if (res.ok + queue.size() > maxFiles) throw new ForgeException("Demasiados archivos");
                log.log("Ronda " + (rnd + 1) + ": " + queue.size() + " archivos");
                List<Future<byte[]>> fs = new ArrayList<Future<byte[]>>();
                for (final String[] q : queue) fs.add(ex.submit(new Callable<byte[]>() { public byte[] call() { try { return get(q[1]); } catch (Exception e) { return null; } } }));
                List<String[]> next = new ArrayList<String[]>();
                for (int i = 0; i < queue.size(); i++) {
                    if (cancel.get()) throw new ForgeException("Cancelado");
                    String rel = queue.get(i)[0], url = queue.get(i)[1];
                    byte[] b;
                    try { b = fs.get(i).get(); } catch (Exception e) { b = null; }
                    if (b == null) { res.failed.add(rel); continue; }
                    res.bytes += b.length; res.ok++;
                    if (res.bytes > maxBytes) throw new ForgeException("La web supera el límite de " + (maxBytes >> 20) + " MB");
                    String e = ext(rel);
                    if (TEXT.contains(e) && b.length < 8 * 1024 * 1024) {
                        String t = new String(b, "UTF-8");
                        addRefs(next, seen, refs(t), origin, base, url, res);
                        if (e.equals("js") || e.equals("mjs") || e.equals("html") || e.equals("htm")) b = rewrite(t, base).getBytes("UTF-8");
                    }
                    Files2.write(new File(dest, rel), b);
                    log.log(String.format(Locale.ROOT, "~%d archivos · %.1f MB", res.ok, res.bytes / 1e6));   // "~": solo estado (no va al registro)
                }
                queue = next;
            }
        } finally { ex.shutdownNow(); }
        return res;
    }

    /** /carpeta/x -> x (la app se sirve desde la raíz). ponytail: en CSS y en import() de módulos no se corrige. */
    private static String rewrite(String t, String base) {
        if (base.equals("/")) return t;
        return t.replaceAll("([\"'`(])" + Pattern.quote(base), "$1");
    }

    private static void addRefs(List<String[]> q, Set<String> seen, Set<String> refs, String origin, String base, String ctx, Result res) {
        for (String r : refs) {
            r = r.trim();
            if (r.length() == 0 || r.startsWith("#") || r.startsWith("data:") || r.startsWith("blob:") || r.startsWith("javascript:") || r.startsWith("mailto:") || r.startsWith("tel:") || r.contains("${")) continue;
            try {
                URL a = new URL(new URL(ctx), r);
                if (!a.getProtocol().startsWith("http")) continue;
                String full = a.getProtocol() + "://" + a.getAuthority();
                String p = a.getPath();
                if (!full.equals(origin)) { res.external.add(a.getHost()); continue; }
                if (!p.startsWith(base)) { res.external.add(a.getHost() + p); continue; }
                String rel = URLDecoder.decode(p.substring(base.length()), "UTF-8");
                if (rel.length() == 0 || rel.endsWith("/") || rel.contains("..") || ext(rel).length() == 0) continue;   // carpetas / rutas de SPA
                if (seen.add(rel)) q.add(new String[]{rel, full + p});
            } catch (Exception e) { }
        }
    }
}
