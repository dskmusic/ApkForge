package com.dskmusic.apkforge.core;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Utilidades de archivos: extracción segura de ZIP, detección de index.html, copia y borrado. */
public final class Files2 {
    public static final class Root { public File dir; public String entry; }

    public static void extractZip(File zip, File dest, long maxBytes, int maxFiles) throws ForgeException, IOException {
        ZipFile z;
        try { z = new ZipFile(zip); } catch (ZipException e) { throw new ForgeException("El archivo no es un ZIP válido"); }
        try {
            long total = 0; int n = 0;
            String root = dest.getCanonicalPath() + File.separator;
            for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements(); ) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                String name = e.getName().replace('\\', '/');
                String[] parts = name.split("/");
                List<String> ok = new ArrayList<String>();
                for (String p : parts) { if (p.length() == 0 || p.equals(".")) continue; if (p.equals("..") || p.indexOf('\0') >= 0) throw new ForgeException("Ruta no permitida en el ZIP: " + name); ok.add(p); }
                if (ok.isEmpty()) continue;
                String last = ok.get(ok.size() - 1);
                if (ok.get(0).equals("__MACOSX") || last.equals(".DS_Store") || last.equals("Thumbs.db") || last.startsWith("._")) continue;
                if (++n > maxFiles) throw new ForgeException("El ZIP tiene demasiados archivos");
                File f = new File(dest, join(ok));
                if (!f.getCanonicalPath().startsWith(root)) throw new ForgeException("Ruta no permitida en el ZIP: " + name);
                f.getParentFile().mkdirs();
                InputStream in = z.getInputStream(e);
                OutputStream out = new FileOutputStream(f);
                try {
                    byte[] b = new byte[65536]; int r;
                    while ((r = in.read(b)) >= 0) { total += r; if (total > maxBytes) throw new ForgeException("El contenido supera el límite de " + (maxBytes >> 20) + " MB"); out.write(b, 0, r); }
                } finally { in.close(); out.close(); }
            }
        } finally { z.close(); }
    }

    private static String join(List<String> l) { StringBuilder sb = new StringBuilder(); for (String s : l) { if (sb.length() > 0) sb.append('/'); sb.append(s); } return sb.toString(); }

    private static void walk(File d, String rel, List<String> out) {
        File[] fs = d.listFiles();
        if (fs == null) return;
        Arrays.sort(fs);
        for (File f : fs) {
            String r = rel.length() == 0 ? f.getName() : rel + "/" + f.getName();
            if (f.isDirectory()) walk(f, r, out); else out.add(r);
        }
    }

    public static List<String> listFiles(File dir) { List<String> l = new ArrayList<String>(); walk(dir, "", l); return l; }

    /** Busca el index.html más cercano a la raíz (o el primer .html) y devuelve su carpeta como raíz de la web. */
    public static Root detect(File dir) throws ForgeException {
        List<String> all = listFiles(dir), htmls = new ArrayList<String>();
        for (String p : all) { String l = p.toLowerCase(Locale.ROOT); if (l.endsWith(".html") || l.endsWith(".htm")) htmls.add(p); }
        if (htmls.isEmpty()) throw new ForgeException("No hay ningún archivo .html");
        Comparator<String> cmp = new Comparator<String>() {
            public int compare(String a, String b) { int da = depth(a), db = depth(b); return da != db ? da - db : a.compareTo(b); }
        };
        List<String> idx = new ArrayList<String>();
        for (String p : htmls) if (p.substring(p.lastIndexOf('/') + 1).equalsIgnoreCase("index.html")) idx.add(p);
        String chosen;
        if (!idx.isEmpty()) { Collections.sort(idx, cmp); chosen = idx.get(0); } else { Collections.sort(htmls, cmp); chosen = htmls.get(0); }
        Root r = new Root();
        int k = chosen.lastIndexOf('/');
        r.dir = k < 0 ? dir : new File(dir, chosen.substring(0, k));
        r.entry = chosen.substring(k + 1);
        return r;
    }

    private static int depth(String p) { int n = 0; for (int i = 0; i < p.length(); i++) if (p.charAt(i) == '/') n++; return n; }

    public static byte[] readAll(File f) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream((int) Math.min(f.length(), 1 << 24));
        InputStream in = new FileInputStream(f);
        try { byte[] b = new byte[65536]; int n; while ((n = in.read(b)) >= 0) bo.write(b, 0, n); } finally { in.close(); }
        return bo.toByteArray();
    }

    public static void write(File f, byte[] data) throws IOException {
        f.getParentFile().mkdirs();
        OutputStream o = new FileOutputStream(f);
        try { o.write(data); } finally { o.close(); }
    }

    public static void copy(File src, File dst) throws IOException {
        dst.getParentFile().mkdirs();
        InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst);
        try { byte[] b = new byte[65536]; int n; while ((n = in.read(b)) >= 0) out.write(b, 0, n); } finally { in.close(); out.close(); }
    }

    public static void copyTree(File srcDir, File dstDir) throws IOException {
        for (String rel : listFiles(srcDir)) copy(new File(srcDir, rel), new File(dstDir, rel));
    }

    public static void deleteTree(File f) {
        File[] fs = f.listFiles();
        if (fs != null) for (File c : fs) deleteTree(c);
        f.delete();
    }
}
