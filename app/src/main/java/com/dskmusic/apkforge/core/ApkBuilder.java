package com.dskmusic.apkforge.core;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/** Parchea base.apk y genera el APK firmado. Port de build() de forge.py. */
public final class ApkBuilder {
    public static final String PLACE_PKG = "com.dskmusic.template", PLACE_NAME = "__APPNAME__", PLACE_VNAME = "__VERSIONNAME__";
    private static final Set<String> TEXT_EXT = new HashSet<String>(Arrays.asList("html", "htm", "js", "mjs", "css", "json", "svg", "txt", "xml", "csv", "map", "md", "sfz", "glsl", "frag", "vert"));
    private static final Pattern ICON = Pattern.compile("res/mipmap-[a-z]+dpi-v4/ic_launcher(_fg|_bg)?\\.png");

    public static final class IconSpec { public String name, kind; public int size; }

    public static final class Params {
        public byte[] baseApk;
        public File webRoot;            // null en modo web
        public String entry = "";
        public String remoteUrl = "", fallbackHost = "";
        public String name, pkg, versionName = "1.0";
        public long versionCode;
        public Options opt = new Options();
        public Map<String, byte[]> icons;
        public Jks.Entry key; public String alias;
        public File out, tmpDir;
    }

    static Map<String, byte[]> readZip(byte[] data) throws IOException {
        Map<String, byte[]> m = new LinkedHashMap<String, byte[]>();
        ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(data));
        ZipEntry e;
        while ((e = z.getNextEntry()) != null) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[32768]; int n;
            while ((n = z.read(b)) >= 0) bo.write(b, 0, n);
            m.put(e.getName(), bo.toByteArray());
        }
        return m;
    }

    public static List<IconSpec> iconSpecs(byte[] baseApk) throws IOException, ForgeException {
        List<IconSpec> l = new ArrayList<IconSpec>();
        for (Map.Entry<String, byte[]> e : readZip(baseApk).entrySet()) {
            Matcher m = ICON.matcher(e.getKey());
            if (!m.matches()) continue;
            byte[] d = e.getValue();
            IconSpec s = new IconSpec();
            s.name = e.getKey(); s.kind = m.group(1) == null ? "" : m.group(1);
            s.size = ((d[16] & 0xFF) << 24) | ((d[17] & 0xFF) << 16) | ((d[18] & 0xFF) << 8) | (d[19] & 0xFF);
            l.add(s);
        }
        if (l.isEmpty()) throw new ForgeException("La plantilla base no contiene iconos reconocibles");
        return l;
    }

    private static boolean assetStore(String rel, long size) {
        int d = rel.lastIndexOf('.');
        String ext = d < 0 ? "" : rel.substring(d + 1).toLowerCase(Locale.ROOT);
        return !(TEXT_EXT.contains(ext) && size < 4L * 1024 * 1024);
    }

    private static String q(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c == '\n') b.append("\\n"); else if (c == '\r') b.append("\\r"); else if (c == '\t') b.append("\\t");
            else if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c);
        }
        return b.append('"').toString();
    }

    static String forgeJson(Params p) {
        Options o = p.opt;
        StringBuilder s = new StringBuilder("{");
        s.append("\"name\":").append(q(p.name)).append(",\"package\":").append(q(p.pkg)).append(",\"entry\":").append(q(p.entry));
        s.append(",\"orientation\":").append(q(o.orientation())).append(",\"fullscreen\":").append(o.fullscreen).append(",\"keepAwake\":").append(o.keepAwake);
        s.append(",\"bg\":").append(q(o.bg)).append(",\"selectText\":").append(o.selectText).append(",\"zoom\":").append(o.zoom);
        s.append(",\"pullRefresh\":").append(o.pullRefresh).append(",\"theme\":").append(q(o.theme)).append(",\"mobileViewport\":").append(o.mobileViewport);
        s.append(",\"loadDelay\":").append(Math.max(0, Math.min(5000, o.loadDelay)));
        if (p.remoteUrl.length() > 0) s.append(",\"remoteUrl\":").append(q(p.remoteUrl));
        if (p.fallbackHost.length() > 0) s.append(",\"fallbackHost\":").append(q(p.fallbackHost));
        return s.append("}").toString();
    }

    public static void build(Params p, Logger log) throws ForgeException, IOException {
        String name = p.name == null ? "" : p.name.trim();
        if (name.length() == 0 || name.length() > 60) throw new ForgeException("El nombre de la app debe tener entre 1 y 60 caracteres");
        if (!Names.validPackage(p.pkg)) throw new ForgeException("Nombre de paquete no válido: " + p.pkg);
        if (p.versionCode < 1 || p.versionCode > 2100000000L) throw new ForgeException("versionCode fuera de rango");
        p.name = name;
        Map<String, byte[]> base = readZip(p.baseApk);

        // limpieza goof en los .html de la raíz de la web
        if (p.webRoot != null && p.opt.cleanGoof) {
            int n = 0;
            for (String rel : Files2.listFiles(p.webRoot)) {
                String l = rel.toLowerCase(Locale.ROOT);
                if (!(l.endsWith(".html") || l.endsWith(".htm"))) continue;
                File f = new File(p.webRoot, rel);
                if (f.length() > 32L * 1024 * 1024) continue;
                String t = new String(Files2.readAll(f), "UTF-8");
                if (!t.toLowerCase(Locale.ROOT).contains("goof")) continue;
                Object[] r = Goof.cleanHtml(t);
                if ((Integer) r[1] > 0) { Files2.write(f, ((String) r[0]).getBytes("UTF-8")); n += (Integer) r[1]; }
            }
            if (n > 0) log.log("Limpieza goof: " + n + " bloques quitados (splash/aviso de dominio)");
        }

        p.tmpDir.mkdirs();
        File unsigned = new File(p.tmpDir, "unsigned.apk");
        ZipW zw = new ZipW(unsigned);
        try {
            Map<String, String> repl = new HashMap<String, String>();
            repl.put(PLACE_PKG, p.pkg); repl.put(PLACE_NAME, p.name); repl.put(PLACE_VNAME, p.versionName);
            String[] order = {"AndroidManifest.xml", "classes.dex", "resources.arsc"};
            for (String n : order) {
                byte[] d = base.get(n);
                if (d == null) throw new ForgeException("Falta " + n + " en la plantilla base");
                if (n.equals("AndroidManifest.xml")) d = Axml.patchManifest(d, repl, p.versionCode);
                else if (n.equals("resources.arsc")) d = Axml.patchArscPackage(d, p.pkg);
                zw.add(n, d, n.equals("resources.arsc"));
            }
            Set<String> skip = new HashSet<String>(Arrays.asList(order));
            for (Map.Entry<String, byte[]> e : base.entrySet()) {
                String n = e.getKey();
                if (skip.contains(n) || n.startsWith("META-INF/") || n.endsWith("/") || n.startsWith("assets/www/") || n.equals("assets/forge.json")) continue;
                byte[] d = p.icons != null && p.icons.containsKey(n) ? p.icons.get(n) : e.getValue();
                zw.add(n, d, n.endsWith(".png") && n.startsWith("res/"));
            }
            zw.add("assets/forge.json", forgeJson(p).getBytes("UTF-8"), false);
            if (p.webRoot != null) {
                for (String rel : Files2.listFiles(p.webRoot)) {
                    File f = new File(p.webRoot, rel);
                    zw.addFile("assets/www/" + rel, f, assetStore(rel, f.length()));
                }
            }
        } finally { zw.close(); }
        Signer.sign(unsigned, p.out, p.key, p.alias);
        unsigned.delete();
    }
}
