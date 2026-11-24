package com.dskmusic.apkforge;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.dskmusic.apkforge.core.ApkBuilder;
import com.dskmusic.apkforge.core.ForgeException;
import com.dskmusic.apkforge.core.Pipeline;

import java.io.ByteArrayOutputStream;
import java.util.*;

/** Genera los iconos (legacy + adaptativo) con android.graphics. Port de make_icons() de forge.py. */
final class IconMakerAndroid implements Pipeline.IconMaker {
    public Map<String, byte[]> make(byte[] bytes, List<ApkBuilder.IconSpec> specs) throws Exception {
        Bitmap src = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (src == null) throw new ForgeException("El icono no es una imagen válida (usa PNG o JPG)");
        int w = src.getWidth(), h = src.getHeight(), s = Math.min(w, h);
        Bitmap sq = Bitmap.createBitmap(src, (w - s) / 2, (h - s) / 2, s, s);
        int bgc = edgeColor(sq);
        Map<String, byte[]> out = new HashMap<String, byte[]>();
        for (ApkBuilder.IconSpec sp : specs) {
            Bitmap o = Bitmap.createBitmap(sp.size, sp.size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(o);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            if (sp.kind.length() == 0) {
                c.drawBitmap(Bitmap.createScaledBitmap(sq, sp.size, sp.size, true), 0, 0, p);
            } else if (sp.kind.equals("_bg")) {
                c.drawColor(bgc);
            } else {
                int inner = Math.round(sp.size * 0.62f), off = (sp.size - inner) / 2;
                c.drawBitmap(Bitmap.createScaledBitmap(sq, inner, inner, true), off, off, p);
            }
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            o.compress(Bitmap.CompressFormat.PNG, 100, bo);
            out.put(sp.name, bo.toByteArray());
        }
        return out;
    }

    private static int median(List<Integer> l) { Collections.sort(l); return l.get(l.size() / 2); }

    private static int edgeColor(Bitmap im) {
        int w = im.getWidth(), h = im.getHeight();
        List<Integer> r = new ArrayList<Integer>(), g = new ArrayList<Integer>(), b = new ArrayList<Integer>();
        List<int[]> pts = new ArrayList<int[]>();
        for (int k = 0; k < w; k += Math.max(1, w / 40)) { pts.add(new int[]{k, 0}); pts.add(new int[]{k, h - 1}); }
        for (int k = 0; k < h; k += Math.max(1, h / 40)) { pts.add(new int[]{0, k}); pts.add(new int[]{w - 1, k}); }
        for (int[] pt : pts) {
            int px = im.getPixel(pt[0], pt[1]);
            if (Color.alpha(px) > 128) { r.add(Color.red(px)); g.add(Color.green(px)); b.add(Color.blue(px)); }
        }
        if (r.isEmpty()) return Color.WHITE;
        return Color.rgb(median(r), median(g), median(b));
    }
}
