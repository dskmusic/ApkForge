package com.dskmusic.shell;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Mini servidor HTTP (solo 127.0.0.1) que sirve los recursos de la app: soporta Range (audio/vídeo), HEAD y MIME correctos. */
public class LocalServer {

    public interface Source { Entry open(String path) throws IOException; }

    public static class Entry {
        public final InputStream in;
        public final long length;
        public final String mime;
        public Entry(InputStream in, long length) { this(in, length, null); }
        public Entry(InputStream in, long length, String mime) { this.in = in; this.length = length; this.mime = mime; }
    }

    private static final String SHIM_TAG = "<script src=\"/__forge/shim.js\"></script>";

    private final Source src;
    private Source fallback;
    public void setFallback(Source f) { fallback = f; }
    private final String entry;
    private ServerSocket ss;
    private ExecutorService pool;
    private volatile boolean running;
    private volatile boolean mobileViewport = true;
    private int port;

    public LocalServer(Source src, String entry) {
        this.src = src;
        this.entry = (entry == null || entry.length() == 0) ? "index.html" : entry;
    }

    public int getPort() { return port; }

    /** Si la página no define <meta viewport>, se añade uno móvil (width=device-width). */
    public void setMobileViewport(boolean on) { mobileViewport = on; }

    public int start(int preferred) throws IOException {
        ServerSocket s = null;
        for (int i = 0; i < 300 && s == null; i++) {
            int p = preferred + i;
            if (p > 65000) p = 20000 + (p - 65000);
            ServerSocket c = new ServerSocket();
            try {
                c.setReuseAddress(true);
                c.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), p), 64);
                s = c;
                port = p;
            } catch (IOException e) {
                try { c.close(); } catch (IOException x) { }
            }
        }
        if (s == null) throw new IOException("sin puerto libre");
        ss = s;
        pool = Executors.newCachedThreadPool();
        running = true;
        Thread t = new Thread(new Runnable() {
            public void run() {
                while (running) {
                    try {
                        final Socket c = ss.accept();
                        pool.execute(new Runnable() { public void run() { handle(c); } });
                    } catch (Exception e) {
                        if (!running) break;
                    }
                }
            }
        }, "forge-accept");
        t.setDaemon(true);
        t.start();
        return port;
    }

    public void stop() {
        running = false;
        try { if (ss != null) ss.close(); } catch (IOException e) { }
        if (pool != null) pool.shutdownNow();
    }

    // ------------------------------------------------------------------

    private void handle(Socket s) {
        try {
            s.setSoTimeout(20000);
            s.setTcpNoDelay(true);
            InputStream in = new BufferedInputStream(s.getInputStream(), 4096);
            OutputStream out = new BufferedOutputStream(s.getOutputStream(), 65536);
            String line = readLine(in);
            if (line == null || line.length() == 0) return;
            String[] parts = line.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String target = parts[1];
            Map<String, String> h = new HashMap<String, String>();
            String hl;
            int n = 0;
            while ((hl = readLine(in)) != null && hl.length() > 0 && n++ < 100) {
                int c = hl.indexOf(':');
                if (c > 0) h.put(hl.substring(0, c).trim().toLowerCase(Locale.US), hl.substring(c + 1).trim());
            }
            if (!method.equals("GET") && !method.equals("HEAD")) {
                simple(out, 405, "Method Not Allowed");
                return;
            }
            boolean head = method.equals("HEAD");

            int q = target.indexOf('?');
            if (q >= 0) target = target.substring(0, q);
            int f = target.indexOf('#');
            if (f >= 0) target = target.substring(0, f);
            if (target.startsWith("http://") || target.startsWith("https://")) {
                int sl = target.indexOf('/', target.indexOf("//") + 2);
                target = sl < 0 ? "/" : target.substring(sl);
            }
            String path = decode(target);
            if (path == null) { simple(out, 400, "Bad Request"); return; }

            boolean trailing = path.endsWith("/");
            StringBuilder sb = new StringBuilder();
            for (String seg : path.split("/")) {
                if (seg.length() == 0 || seg.equals(".")) continue;
                if (seg.equals("..") || seg.indexOf('\\') >= 0 || seg.indexOf('\0') >= 0) { simple(out, 403, "Forbidden"); return; }
                if (sb.length() > 0) sb.append('/');
                sb.append(seg);
            }
            String rel = sb.toString();
            boolean shim = rel.equals("__forge/shim.js");
            if (rel.length() == 0) rel = entry;
            else if (trailing) rel = rel + "/index.html";
            String key = shim ? "forge/shim.js" : "www/" + rel;

            Entry e = null;
            try { e = src.open(key); } catch (IOException ex) { e = null; }
            if (e == null && !shim && !trailing && rel.indexOf('.') < 0) {
                Entry d = null;
                try { d = src.open("www/" + rel + "/index.html"); } catch (IOException ex) { d = null; }
                if (d != null) {
                    try { d.in.close(); } catch (IOException ex) { }
                    Map<String, String> r = new LinkedHashMap<String, String>();
                    r.put("Location", "/" + encodePath(rel) + "/");
                    r.put("Content-Length", "0");
                    writeHead(out, 301, "Moved Permanently", r);
                    out.flush();
                    return;
                }
            }
            if (e == null && !shim && fallback != null && rel.startsWith("assets/")) {
                try { e = fallback.open(rel); } catch (IOException ex) { e = null; }
            }
            if (e == null) { simple(out, 404, "Not Found"); return; }

            try {
                String mime = e.mime != null ? e.mime : mimeOf(rel);
                Map<String, String> r = new LinkedHashMap<String, String>();
                r.put("Content-Type", mime);
                r.put("Cache-Control", "no-cache");
                r.put("Connection", "close");

                if (mime.startsWith("text/html")) {
                    byte[] body = inject(readAll(e.in, 32L * 1024 * 1024), mobileViewport);
                    r.put("Content-Length", String.valueOf(body.length));
                    writeHead(out, 200, "OK", r);
                    if (!head) out.write(body);
                    out.flush();
                    return;
                }

                long length = e.length;
                if (length < 0) {
                    byte[] all = readAll(e.in, 512L * 1024 * 1024);
                    try { e.in.close(); } catch (IOException x) { }
                    e = new Entry(new ByteArrayInputStream(all), all.length);
                    length = all.length;
                }
                r.put("Accept-Ranges", "bytes");
                long start = 0, end = length - 1;
                boolean partial = false;
                String rg = h.get("range");
                if (rg != null && rg.startsWith("bytes=") && length > 0) {
                    String spec = rg.substring(6).trim();
                    int cm = spec.indexOf(',');
                    if (cm >= 0) spec = spec.substring(0, cm);
                    int d = spec.indexOf('-');
                    if (d >= 0) {
                        String a = spec.substring(0, d).trim(), b = spec.substring(d + 1).trim();
                        try {
                            if (a.length() == 0) {
                                long last = Long.parseLong(b);
                                start = Math.max(0, length - last);
                                end = length - 1;
                            } else {
                                start = Long.parseLong(a);
                                end = b.length() == 0 ? length - 1 : Math.min(Long.parseLong(b), length - 1);
                            }
                            if (start > end || start >= length) {
                                r.put("Content-Range", "bytes */" + length);
                                r.put("Content-Length", "0");
                                writeHead(out, 416, "Range Not Satisfiable", r);
                                out.flush();
                                return;
                            }
                            partial = true;
                        } catch (NumberFormatException nf) {
                            partial = false;
                            start = 0;
                            end = length - 1;
                        }
                    }
                }
                long count = end - start + 1;
                if (length == 0) count = 0;
                r.put("Content-Length", String.valueOf(count));
                if (partial) r.put("Content-Range", "bytes " + start + "-" + end + "/" + length);
                writeHead(out, partial ? 206 : 200, partial ? "Partial Content" : "OK", r);
                if (!head && count > 0) {
                    skipFully(e.in, start);
                    byte[] buf = new byte[65536];
                    long left = count;
                    while (left > 0) {
                        int rd = e.in.read(buf, 0, (int) Math.min(buf.length, left));
                        if (rd < 0) break;
                        out.write(buf, 0, rd);
                        left -= rd;
                    }
                }
                out.flush();
            } finally {
                try { e.in.close(); } catch (IOException x) { }
            }
        } catch (Exception ex) {
            // cliente cerrado o error: ignorar
        } finally {
            try { s.close(); } catch (IOException x) { }
        }
    }

    // ------------------------------------------------------------------

    static byte[] inject(byte[] html, boolean viewport) {
        try {
            String s = new String(html, "ISO-8859-1");
            String low = s.toLowerCase(Locale.US);
            String tag = SHIM_TAG;
            if (viewport && !hasViewport(low)) {
                tag = "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,viewport-fit=cover\">" + SHIM_TAG;
            }
            int pos = -1;
            String[] tags = {"<head", "<html"};
            for (String t : tags) {
                int i = low.indexOf(t);
                if (i >= 0) {
                    char nx = low.length() > i + t.length() ? low.charAt(i + t.length()) : '>';
                    if (nx == '>' || nx == ' ' || nx == '\n' || nx == '\r' || nx == '\t') {
                        int gt = low.indexOf('>', i);
                        if (gt >= 0) { pos = gt + 1; break; }
                    }
                }
            }
            if (pos < 0) {
                int di = low.indexOf("<!doctype");
                if (di >= 0 && di < 64) {
                    int gt = low.indexOf('>', di);
                    pos = gt >= 0 ? gt + 1 : 0;
                } else pos = 0;
            }
            return (s.substring(0, pos) + tag + s.substring(pos)).getBytes("ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            return html;
        }
    }

    private static boolean hasViewport(String low) {
        int i = 0;
        while ((i = low.indexOf("viewport", i)) >= 0) {
            int a = low.lastIndexOf('<', i);
            int b = low.lastIndexOf('>', i);
            if (a > b && low.substring(a, Math.min(low.length(), i + 12)).startsWith("<meta")) return true;
            i += 8;
        }
        return false;
    }

    private static void simple(OutputStream out, int code, String msg) throws IOException {
        byte[] b = (code + " " + msg).getBytes("UTF-8");
        Map<String, String> r = new LinkedHashMap<String, String>();
        r.put("Content-Type", "text/plain; charset=utf-8");
        r.put("Content-Length", String.valueOf(b.length));
        r.put("Connection", "close");
        writeHead(out, code, msg, r);
        out.write(b);
        out.flush();
    }

    private static void writeHead(OutputStream out, int code, String msg, Map<String, String> h) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(code).append(' ').append(msg).append("\r\n");
        for (Map.Entry<String, String> en : h.entrySet()) sb.append(en.getKey()).append(": ").append(en.getValue()).append("\r\n");
        sb.append("\r\n");
        out.write(sb.toString().getBytes("ISO-8859-1"));
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
            if (sb.length() > 16384) return null;
        }
        if (c < 0 && sb.length() == 0) return null;
        return sb.toString();
    }

    private static byte[] readAll(InputStream in, long max) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[32768];
        int r;
        long tot = 0;
        while ((r = in.read(buf)) >= 0) {
            bo.write(buf, 0, r);
            tot += r;
            if (tot > max) throw new IOException("archivo demasiado grande");
        }
        return bo.toByteArray();
    }

    private static void skipFully(InputStream in, long n) throws IOException {
        while (n > 0) {
            long k = in.skip(n);
            if (k <= 0) {
                if (in.read() < 0) return;
                k = 1;
            }
            n -= k;
        }
    }

    private static String decode(String s) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '%' && i + 2 < s.length()) {
                    int v = Integer.parseInt(s.substring(i + 1, i + 3), 16);
                    bo.write(v);
                    i += 2;
                } else if (c == '%') {
                    return null;
                } else {
                    byte[] b = String.valueOf(c).getBytes("UTF-8");
                    if (Character.isHighSurrogate(c) && i + 1 < s.length()) {
                        b = s.substring(i, i + 2).getBytes("UTF-8");
                        i++;
                    }
                    bo.write(b, 0, b.length);
                }
            }
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    private static String encodePath(String p) {
        try {
            StringBuilder sb = new StringBuilder();
            for (String seg : p.split("/")) {
                if (sb.length() > 0) sb.append('/');
                sb.append(URLEncoder.encode(seg, "UTF-8").replace("+", "%20"));
            }
            return sb.toString();
        } catch (UnsupportedEncodingException e) {
            return p;
        }
    }

    static String mimeOf(String name) {
        int d = name.lastIndexOf('.');
        String ext = d < 0 ? "" : name.substring(d + 1).toLowerCase(Locale.US);
        switch (ext) {
            case "html": case "htm": return "text/html";
            case "js": case "mjs": return "text/javascript; charset=utf-8";
            case "css": return "text/css; charset=utf-8";
            case "json": case "map": case "webmanifest": return "application/json; charset=utf-8";
            case "txt": case "sfz": case "md": case "csv": return "text/plain; charset=utf-8";
            case "xml": return "application/xml; charset=utf-8";
            case "svg": return "image/svg+xml";
            case "png": return "image/png";
            case "jpg": case "jpeg": return "image/jpeg";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            case "avif": return "image/avif";
            case "ico": return "image/x-icon";
            case "bmp": return "image/bmp";
            case "mp3": return "audio/mpeg";
            case "ogg": case "oga": return "audio/ogg";
            case "opus": return "audio/ogg; codecs=opus";
            case "wav": return "audio/wav";
            case "m4a": return "audio/mp4";
            case "aac": return "audio/aac";
            case "flac": return "audio/flac";
            case "mid": case "midi": return "audio/midi";
            case "mp4": case "m4v": return "video/mp4";
            case "webm": return "video/webm";
            case "ogv": return "video/ogg";
            case "woff": return "font/woff";
            case "woff2": return "font/woff2";
            case "ttf": return "font/ttf";
            case "otf": return "font/otf";
            case "wasm": return "application/wasm";
            case "glb": return "model/gltf-binary";
            case "gltf": return "model/gltf+json";
            case "pdf": return "application/pdf";
            case "zip": return "application/zip";
            default: return "application/octet-stream";
        }
    }
}
