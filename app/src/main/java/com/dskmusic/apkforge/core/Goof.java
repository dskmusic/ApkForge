package com.dskmusic.apkforge.core;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Descarga de juegos de goof.gg y limpieza del splash / aviso de dominio. Port de goofdl.py. */
public final class Goof {
    public static final String HOST = "https://minigames.appversestudio.com";
    private static final String UA = "Mozilla/5.0";
    static final Pattern SPLASH = Pattern.compile("<script>\\(function\\(\\)\\{try\\{var d=document,s=d\\.createElement\\(\"div\"\\);s\\.id=\"goof-made\".*?\\}catch\\(e\\)\\{\\}\\}\\)\\(\\);</script>", Pattern.DOTALL);
    static final Pattern GUARDIA = Pattern.compile("\\(function\\(\\)\\{var H=\\[\"goof\\.gg\".*?copying it is prohibited \\(https://goof\\.gg/terms\\)\\.'\\)\\}\\)\\(\\);", Pattern.DOTALL);
    static final Pattern PATH = Pattern.compile("/assets/[A-Za-z0-9_./@%~+=!$&*-]*[A-Za-z0-9_@+=~]");
    public static final Pattern GOOF_URL = Pattern.compile("minigames\\.appversestudio\\.com/games/([\\w-]+)/");
    private static final Set<String> TEXT = new HashSet<String>(Arrays.asList(".js", ".mjs", ".css", ".json", ".html", ".htm", ".svg", ".txt", ".glsl", ".vert", ".frag", ".xml"));
    static final String SHIM = "<script>(function(){var H='" + HOST + "',O=location.origin;function f(u){u=String(u);return u.indexOf(H)===0?O+u.slice(H.length):u}"
            + "var F=window.fetch;window.fetch=function(u,o){return F.call(this,(typeof u==='string'||u instanceof URL)?f(u):u,o)};"
            + "var X=XMLHttpRequest.prototype.open;XMLHttpRequest.prototype.open=function(m,u){arguments[1]=f(u);return X.apply(this,arguments)};"
            + "[HTMLMediaElement,HTMLImageElement,HTMLScriptElement].forEach(function(C){var d=Object.getOwnPropertyDescriptor(C.prototype,'src');"
            + "if(d&&d.set)Object.defineProperty(C.prototype,'src',{get:d.get,set:function(v){d.set.call(this,f(v))},configurable:true})})})()</script>";

    /** @return {texto limpio, nº de bloques quitados} */
    public static Object[] cleanHtml(String t) {
        int n = 0;
        Matcher m = SPLASH.matcher(t);
        StringBuffer sb = new StringBuffer();
        while (m.find()) { n++; m.appendReplacement(sb, ""); }
        m.appendTail(sb);
        t = sb.toString();
        m = GUARDIA.matcher(t);
        sb = new StringBuffer();
        while (m.find()) { n++; m.appendReplacement(sb, ""); }
        m.appendTail(sb);
        return new Object[]{sb.toString(), n};
    }

    private static byte[] get(String url, int timeout) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(timeout); c.setReadTimeout(timeout * 2);
        c.setRequestProperty("User-Agent", UA);
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

    public static String gameId(String link) throws ForgeException, IOException {
        link = link.trim();
        Matcher m = GOOF_URL.matcher(link);
        if (m.find()) return m.group(1);
        m = Pattern.compile("^https?://(?:www\\.)?goof\\.gg/game/([\\w-]+)").matcher(link);
        if (!m.find()) throw new ForgeException("Enlace no válido (https://goof.gg/game/… o la URL del juego)");
        String page = new String(get("https://goof.gg/game/" + m.group(1), 20000), "UTF-8");
        Matcher g = Pattern.compile("og:image\" content=\"[^\"]*?/covers/([\\w-]+?)-[0-9a-f]{10}\\.\\w+").matcher(page);
        // juegos de usuario (UUID): sin portada /covers/, el propio id del enlace es el del juego
        return g.find() ? g.group(1) : m.group(1);
    }

    static Set<String> findPaths(String text) {
        Set<String> out = new TreeSet<String>();
        Matcher m = PATH.matcher(text);
        while (m.find()) if (!m.group().contains("/covers/")) out.add(m.group());
        return out;
    }

    /** Descarga el juego a dest (index.html limpio + recursos). Devuelve nº de recursos no encontrados. */
    public static int fetchInto(File dest, String link, long maxBytes, int maxFiles, Logger log) throws ForgeException, IOException {
        String gid = gameId(link);
        String idx = new String(get(HOST + "/games/" + gid + "/index.html", 20000), "UTF-8");
        Set<String> paths = findPaths(idx);
        Matcher pk = Pattern.compile("\"base\":\"" + Pattern.quote(HOST) + "(/assets/games/[^\"]+/)\",\"names\":(\\{.*?\\})").matcher(idx);
        if (pk.find()) { Matcher v = Pattern.compile(":\"([^\"]+)\"").matcher(pk.group(2)); while (v.find()) paths.add(pk.group(1) + v.group(1)); }
        Matcher lib = Pattern.compile("['\"]((?:music|sfx|images)/[\\w./-]+)['\"]").matcher(idx);
        while (lib.find()) paths.add("/assets/library/" + lib.group(1) + (lib.group(1).startsWith("images/") ? ".webp" : ".mp3"));

        String t = idx.replace(HOST, "").replace(HOST.replace("/", "\\/"), "");
        t = (String) cleanHtml(t)[0];
        int k = t.indexOf("<head>");
        t = k < 0 ? SHIM + t : t.substring(0, k + 6) + SHIM + t.substring(k + 6);
        Files2.write(new File(dest, "index.html"), t.getBytes("UTF-8"));

        final Set<String> seen = new HashSet<String>(); final List<String> fails = new ArrayList<String>();
        long total = t.length();
        List<String> queue = new ArrayList<String>(paths);
        ExecutorService ex = Executors.newFixedThreadPool(4);
        try {
            for (int rnd = 0; rnd < 4; rnd++) {
                List<String> q = new ArrayList<String>();
                for (String p : queue) if (!seen.contains(p)) q.add(p);
                if (q.isEmpty()) break;
                if (seen.size() + q.size() > maxFiles) throw new ForgeException("Demasiados recursos");
                log.log("Ronda " + (rnd + 1) + ": " + q.size() + " recursos");
                seen.addAll(q);
                List<Future<byte[]>> fs = new ArrayList<Future<byte[]>>();
                for (final String p : q) fs.add(ex.submit(new Callable<byte[]>() { public byte[] call() { try { return get(HOST + p, 20000); } catch (Exception e) { return null; } } }));
                Set<String> next = new TreeSet<String>();
                for (int i = 0; i < q.size(); i++) {
                    byte[] b;
                    try { b = fs.get(i).get(); } catch (Exception e) { b = null; }
                    String p = q.get(i);
                    if (b == null) { fails.add(p); continue; }
                    total += b.length;
                    if (total > maxBytes) throw new ForgeException("El juego supera el límite de " + (maxBytes >> 20) + " MB");
                    Files2.write(new File(dest, p.substring(1)), b);
                    String lp = p.toLowerCase(Locale.ROOT);
                    int dot = lp.lastIndexOf('.');
                    if (dot >= 0 && TEXT.contains(lp.substring(dot)) && b.length < 8 * 1024 * 1024) next.addAll(findPaths(new String(b, "UTF-8")));
                }
                queue = new ArrayList<String>(next);
            }
        } finally { ex.shutdownNow(); }
        for (String p : fails) log.log("FALLO (se pedirá al abrir la app) " + p);
        log.log(String.format(Locale.ROOT, "Descargado: %.1f MB, %d recursos, %d no encontrados", total / 1e6, seen.size() - fails.size(), fails.size()));
        return fails.size();
    }
}
