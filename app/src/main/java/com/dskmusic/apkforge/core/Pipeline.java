package com.dskmusic.apkforge.core;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/** Flujo completo: origen (ZIP / carpeta / enlace goof / web) -> APK firmado. */
public final class Pipeline {
    public interface IconMaker { Map<String, byte[]> make(byte[] iconBytes, List<ApkBuilder.IconSpec> specs) throws Exception; }
    public enum Mode { ZIP, FOLDER, GOOF, WEB }

    public static final class Request {
        public Mode mode;
        public File zipFile, folderDir;          // ZIP o carpeta ya copiada
        public String link = "", webUrl = "";
        public String name = "", pkg = "", versionName = "1.0", prefix = "com.dskmusic";
        public long versionCode = 0;
        public Options opt = new Options();
        public byte[] iconBytes, defaultIcon, baseApk;
        public Jks.Entry key; public String alias;
        public File workDir, outDir;
        public IconMaker iconMaker;
        public long maxBytes = 400L << 20; public int maxFiles = 20000;
    }

    private static final String[] JUNK = {"Jugar.pyw", "juego.json", "goof.json"};

    private static String find(String text, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(text);
        return m.find() ? m.group(1).replace("\\/", "/") : "";
    }

    public static File run(Request r, Logger log) throws Exception {
        Files2.deleteTree(r.workDir);
        r.workDir.mkdirs();
        File src = new File(r.workDir, "src");
        src.mkdirs();
        ApkBuilder.Params p = new ApkBuilder.Params();
        p.opt = r.opt; p.baseApk = r.baseApk;
        String name = r.name == null ? "" : r.name.trim();
        p.name = name;
        p.pkg = r.pkg != null && r.pkg.trim().length() > 0 ? r.pkg.trim().toLowerCase(Locale.ROOT) : r.prefix + "." + Names.slugify(name);
        p.versionName = r.versionName == null || r.versionName.trim().length() == 0 ? "1.0" : r.versionName.trim();
        p.versionCode = r.versionCode > 0 ? r.versionCode : System.currentTimeMillis() / 60000L;
        p.key = r.key; p.alias = r.alias;
        p.tmpDir = new File(r.workDir, "tmp");

        switch (r.mode) {
            case WEB:
                if (!r.webUrl.matches("(?i)^https?://[^\\s/]+.*")) throw new ForgeException("La URL de la web debe empezar por http:// o https://");
                p.remoteUrl = r.webUrl.trim();
                log.log("Modo web: " + p.remoteUrl);
                break;
            case GOOF: {
                Goof.fetchInto(src, r.link, r.maxBytes, r.maxFiles, log);
                p.webRoot = src; p.entry = "index.html"; p.fallbackHost = Goof.HOST;
                break;
            }
            default: {
                File dir = src;
                if (r.mode == Mode.ZIP) Files2.extractZip(r.zipFile, src, r.maxBytes, r.maxFiles);
                else dir = r.folderDir;
                Files2.Root root = Files2.detect(dir);
                p.webRoot = root.dir; p.entry = root.entry;
                log.log("Entrada: " + root.entry + " | archivos: " + Files2.listFiles(root.dir).size());
                completeFromGoof(p, r, log);
            }
        }
        if (r.mode != Mode.WEB) log.log("Paquete: " + p.pkg);

        byte[] ib = r.iconBytes != null ? r.iconBytes : r.defaultIcon;
        List<ApkBuilder.IconSpec> specs = ApkBuilder.iconSpecs(r.baseApk);
        p.icons = r.iconMaker.make(ib, specs);

        r.outDir.mkdirs();
        File real = new File(r.outDir, Names.apkFileName(p.pkg, r.prefix));
        File tmpOut = new File(r.workDir, "signed.apk");
        p.out = tmpOut;
        ApkBuilder.build(p, log);
        if (real.exists()) real.delete();
        if (!tmpOut.renameTo(real)) { Files2.copy(tmpOut, real); tmpOut.delete(); }
        Files2.deleteTree(new File(r.workDir, "src")); Files2.deleteTree(new File(r.workDir, "merged")); Files2.deleteTree(p.tmpDir);
        log.log("Listo: " + real.getName() + " (" + (real.length() >> 10) + " KB)");
        return real;
    }

    /** ZIP/carpeta de goof (juego.json, goof.json, Jugar.pyw): completa los recursos que falten; lo del usuario manda. */
    private static void completeFromGoof(ApkBuilder.Params p, Request r, Logger log) {
        try {
            File root = p.webRoot;
            String srcUrl = "";
            File jj = new File(root, "juego.json"), gj = new File(root, "goof.json");
            if (jj.isFile()) {
                String id = find(new String(Files2.readAll(jj), "UTF-8"), "id");
                if (id.matches("[\\w-]{8,64}")) srcUrl = Goof.HOST + "/games/" + id + "/index.html";
            }
            if (srcUrl.length() == 0 && gj.isFile()) {
                String u = find(new String(Files2.readAll(gj), "UTF-8"), "url");
                if (Goof.GOOF_URL.matcher(u).find()) srcUrl = u;
            }
            if (srcUrl.length() > 0 || new File(root, "Jugar.pyw").isFile()) p.fallbackHost = Goof.HOST;
            File entryF = new File(root, p.entry);
            if (srcUrl.length() > 0 && entryF.isFile() && Pattern.compile("/assets/\\w").matcher(new String(Files2.readAll(entryF), "ISO-8859-1")).find()) {
                log.log("Juego de goof con recursos externos: se completan desde el servidor de goof");
                File dl = new File(r.workDir, "dl"), merged = new File(r.workDir, "merged");
                dl.mkdirs();
                Goof.fetchInto(dl, srcUrl, r.maxBytes, r.maxFiles, log);
                Files2.copyTree(dl, merged);
                for (String rel : Files2.listFiles(root)) Files2.copy(new File(root, rel), new File(merged, rel));
                Files2.deleteTree(dl);
                p.webRoot = merged;
            }
        } catch (Exception e) {
            log.log("Aviso: no pude completar desde goof (" + e.getMessage() + "); se usa el archivo tal cual");
        }
        for (String j : JUNK) new File(p.webRoot, j).delete();
    }
}
