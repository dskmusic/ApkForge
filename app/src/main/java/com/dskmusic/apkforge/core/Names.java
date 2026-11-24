package com.dskmusic.apkforge.core;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

public final class Names {
    private static final Set<String> KW = new HashSet<String>(Arrays.asList(("abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for goto if implements import instanceof int interface long native new package private protected public return short static strictfp super switch synchronized this throw throws transient try void volatile while true false null").split(" ")));
    private static final Pattern PKG = Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+");

    public static String slugify(String name) {
        String s = Normalizer.normalize(name, Normalizer.Form.NFKD).replaceAll("[^\\p{ASCII}]", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (s.length() == 0) s = "app";
        if (Character.isDigit(s.charAt(0))) s = "app" + s;
        if (KW.contains(s)) s = "app" + s;
        return s.length() > 40 ? s.substring(0, 40) : s;
    }

    public static boolean validPackage(String p) {
        if (!PKG.matcher(p).matches() || p.length() > 120) return false;
        for (String x : p.split("\\.")) if (KW.contains(x)) return false;
        return true;
    }

    public static String apkFileName(String pkg, String prefix) {
        String pre = prefix + ".";
        String n = pkg.startsWith(pre) ? pkg.substring(pre.length()) : pkg;
        return n.replace(".", "-") + ".apk";
    }
}
