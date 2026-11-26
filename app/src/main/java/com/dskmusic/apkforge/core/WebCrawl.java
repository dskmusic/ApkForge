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
        public String title;                                            // <title> o nombre del manifest (puede ser null)
        public byte[] icon;                                             // icono más grande del manifest (puede ser null)
        public final List<String> failed = new ArrayList<String>();     // 404, error de red… (solo archivos que la web pide seguro)
        public final Set<String> external = new TreeSet<String>();      // otros dominios u otras carpetas del mismo dominio
    }

    private static final Set<String> TEXT = new HashSet<String>(Arrays.asList("html", "htm", "js", "mjs", "css", "json", "svg", "webmanifest", "xml", "txt"));
    private static final Pattern ATTR = Pattern.compile("(?:src|href|data-src|poster)\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSSURL = Pattern.compile("url\\(\\s*[\"']?([^\"')]+)[\"']?\\s*\\)|@import\\s+[\"']([^\"']+)[\"']");
    private static final Pattern LIT = Pattern.compile("[\"'`]([^\"'`\\s]+\\.(?:js|mjs|css|json|png|jpe?g|gif|webp|svg|ico|mp3|wav|ogg|m4a|mp4|webm|woff2?|ttf|otf|wasm|txt|html|glb|gltf|bin|webmanifest))(?:\\?[^\"'`]*)?[\"'`]");
    private static final Pattern OBJ = Pattern.compile("\\{[^{}]*\\}");
    private static final Pattern SRC = Pattern.compile("\"src\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SIZES = Pattern.compile("\"sizes\"\\s*:\\s*\"(\\d+)x");
    private static final Pattern MNAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>([^<]+)</title>", Pattern.CASE_INSENSITIVE);

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

    /** Referencias a archivos de un texto: atributos HTML y url() de CSS (lit=false) o cadenas con pinta de ruta (lit=true, solo suposiciones). */
    private static Set<String> refs(String t, boolean lit) {
        Set<String> out = new LinkedHashSet<String>();
        Matcher m;
        if (lit) { m = LIT.matcher(t); while (m.find()) out.add(m.group(1)); return out; }
        m = ATTR.matcher(t); while (m.find()) out.add(m.group(1));
        m = CSSURL.matcher(t); while (m.find()) out.add(m.group(1) != null ? m.group(1) : m.group(2));
        return out;
    }

    private static boolean isManifest(String rel) { return rel.endsWith("manifest.json") || rel.endsWith(".webmanifest"); }

    /** Descarga a dest. Lanza ForgeException si no se puede ni leer la página de entrada. */
    public static Result crawl(File dest, String link, long maxBytes, int maxFiles, Logger log, AtomicBoolean cancel) throws ForgeException, IOException {
        URL u = new URL(link.trim());
        final String origin = u.getProtocol() + "://" + u.getAuthority();
        String path = u.getPath().length() == 0 ? "/" : u.getPath();
        String entryName = "";
        if (!path.endsWith("/")) {
            String last = path.substring(path.lastIndexOf('/') + 1);
            if (last.indexOf('.') >= 0) { entryName = last; path = path.substring(0, path.lastIndexOf('/') + 1); } else path += "/";
        }
        final String base = path;                         // carpeta de la app en el servidor, p. ej. /apila2/
        final String page = origin + base + entryName;
        byte[] idx;
        try { idx = get(page); }
        catch (IOException e) { throw new ForgeException("No se pudo leer la página (" + e.getMessage() + ")"); }
        String idxText = new String(idx, "UTF-8");
        String low = idxText.toLowerCase(Locale.ROOT);
        if (low.indexOf("<html") < 0 && low.indexOf("<!doctype") < 0) throw new ForgeException("Esa dirección no devuelve una página web");

        final Result res = new Result(); res.dir = dest;
        Files2.write(new File(dest, "index.html"), rewrite(idxText, base, "index.html").getBytes("UTF-8"));
        res.bytes = idx.length; res.ok = 1;
        Matcher tm = TITLE.matcher(idxText);
        String htmlTitle = tm.find() ? tm.group(1).trim() : null;
        String manName = null; String iconRel = null; int iconSize = -1;

        final Set<String> seen = new HashSet<String>();
        seen.add("index.html");
        List<String[]> queue = new ArrayList<String[]>();   // {ruta relativa, URL, "1" si la web lo pide seguro}
        addRefs(queue, seen, refs(idxText, false), origin, base, page, page, false, true, res);
        addRefs(queue, seen, refs(idxText, true), origin, base, page, page, false, false, res);
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
                    boolean firm = queue.get(i)[2].equals("1");
                    byte[] b;
                    try { b = fs.get(i).get(); } catch (Exception e) { b = null; }
                    if (b == null) { if (firm) res.failed.add(rel); continue; }   // lo que solo "parecía" una ruta en un JS no cuenta como fallo
                    res.bytes += b.length; res.ok++;
                    if (res.bytes > maxBytes) throw new ForgeException("La web supera el límite de " + (maxBytes >> 20) + " MB");
                    String e = ext(rel);
                    if (TEXT.contains(e) && b.length < 8 * 1024 * 1024) {
                        String t = new String(b, "UTF-8");
                        boolean js = e.equals("js") || e.equals("mjs");
                        boolean man = isManifest(rel);
                        addRefs(next, seen, refs(t, false), origin, base, url, page, js, man || (!js && !e.equals("json")), res);
                        addRefs(next, seen, refs(t, true), origin, base, url, page, js, man, res);
                        if (isManifest(rel)) {
                            Matcher mn = MNAME.matcher(t); if (manName == null && mn.find()) manName = mn.group(1).trim();
                            Matcher mo = OBJ.matcher(t);
                            while (mo.find()) {
                                Matcher ms = SRC.matcher(mo.group()), mz = SIZES.matcher(mo.group());
                                if (!ms.find() || !mz.find()) continue;
                                int sz = Integer.parseInt(mz.group(1));
                                String r2 = relOf(url, ms.group(1), origin, base);
                                if (r2 != null && sz > iconSize) { iconSize = sz; iconRel = r2; }
                            }
                        }
                        if (e.equals("html") || e.equals("htm") || e.equals("css") || js) b = rewrite(t, base, rel).getBytes("UTF-8");
                    }
                    Files2.write(new File(dest, rel), b);
                    log.log(String.format(Locale.ROOT, "~%d archivos · %.1f MB", res.ok, res.bytes / 1e6));   // "~": solo estado (no va al registro)
                }
                queue = next;
            }
        } finally { ex.shutdownNow(); }
        String t = manName != null ? manName : htmlTitle;
        if (t != null && t.length() > 0) res.title = t.length() > 40 ? t.substring(0, 40) : t;
        if (iconRel != null) { File f = new File(dest, iconRel); if (f.isFile()) res.icon = Files2.readAll(f); }
        return res;
    }

    /** /carpeta/x -> ruta relativa al archivo rel (la app se sirve desde la raíz). En JS, las rutas de fetch/src van relativas a la página, y los import, al módulo. */
    private static String rewrite(String t, String base, String rel) {
        if (base.equals("/")) return t;
        StringBuilder up = new StringBuilder();
        for (int i = 0; i < rel.length(); i++) if (rel.charAt(i) == '/') up.append("../");
        String qb = Pattern.quote(base), upr = Matcher.quoteReplacement(up.toString());
        String e = ext(rel);
        if (e.equals("css") || e.equals("html") || e.equals("htm")) return t.replaceAll("([\"'(])" + qb, "$1" + upr);
        t = t.replaceAll("((?:from|import)\\s*\\(?\\s*[\"'`])" + qb, "$1" + upr);
        return t.replaceAll("([\"'`(])" + qb, "$1");
    }

    /** Ruta relativa (dentro de la app) de una referencia, o null si no es un archivo de la app. Anota en res los externos si se pasa. */
    private static String relOf(String ctx, String r, String origin, String base) {
        try {
            r = r.trim();
            if (r.length() == 0 || r.startsWith("#") || r.startsWith("data:") || r.startsWith("blob:") || r.startsWith("javascript:") || r.startsWith("mailto:") || r.startsWith("tel:")) return null;
            if (r.indexOf("${") >= 0 || r.indexOf('{') >= 0 || r.indexOf('<') >= 0 || r.indexOf('*') >= 0) return null;
            URL a = new URL(new URL(ctx), r);
            if (!a.getProtocol().startsWith("http") || !(a.getProtocol() + "://" + a.getAuthority()).equals(origin) || !a.getPath().startsWith(base)) return null;
            String rel = URLDecoder.decode(a.getPath().substring(base.length()), "UTF-8");
            if (rel.length() == 0 || rel.endsWith("/") || rel.contains("..") || ext(rel).length() == 0) return null;   // carpetas / rutas de SPA
            return rel;
        } catch (Exception e) { return null; }
    }

    private static void addRefs(List<String[]> q, Set<String> seen, Set<String> refs, String origin, String base, String fileUrl, String pageUrl, boolean js, boolean firm, Result res) {
        for (String r : refs) {
            // en JS, "img/a.png" suele ser relativo a la página (fetch, Audio, src); "./x" y "../x" (import) lo son al propio archivo
            String ctx = js && !(r.startsWith("./") || r.startsWith("../")) ? pageUrl : fileUrl;
            String rel = relOf(ctx, r, origin, base);
            if (rel == null) {
                try {   // externo de verdad (solo lo que la web pide seguro): otro dominio u otra carpeta
                    String t = r.trim();
                    if (firm && !t.startsWith("data:") && !t.startsWith("#") && t.indexOf('{') < 0 && t.indexOf('<') < 0 && t.indexOf('*') < 0) {
                        URL a = new URL(new URL(ctx), t);
                        if (a.getProtocol().startsWith("http") && ext(a.getPath()).length() > 0) {
                            if (!(a.getProtocol() + "://" + a.getAuthority()).equals(origin)) res.external.add(a.getHost());
                            else if (!a.getPath().startsWith(base)) res.external.add(a.getHost() + a.getPath());
                        }
                    }
                } catch (Exception e) { }
                continue;
            }
            if (seen.add(rel)) try { q.add(new String[]{rel, new URL(new URL(ctx), r).toString().replaceAll("[?#].*$", ""), firm ? "1" : "0"}); } catch (Exception e) { }
        }
    }
}
