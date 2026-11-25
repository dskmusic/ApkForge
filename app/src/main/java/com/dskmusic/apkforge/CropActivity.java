package com.dskmusic.apkforge;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import com.dskmusic.apkforge.core.Lang;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Recorte 1:1 del icono: arrastrar, zoom y giro con dos dedos, giro 90°, y marco ajustable por las esquinas. Devuelve la ruta de un PNG 512x512. */
public class CropActivity extends Activity {
    static final String OUT = "out";
    private CropView cv;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.BG); getWindow().setNavigationBarColor(Ui.BG);
        Bitmap bmp = null;
        try {
            Uri u = getIntent().getData();
            BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
            InputStream in = getContentResolver().openInputStream(u); BitmapFactory.decodeStream(in, null, o); in.close();
            int ss = 1; while (Math.max(o.outWidth, o.outHeight) / ss > 2048) ss *= 2;
            o = new BitmapFactory.Options(); o.inSampleSize = ss;
            in = getContentResolver().openInputStream(u); bmp = BitmapFactory.decodeStream(in, null, o); in.close();
        } catch (Exception e) { /* bmp null */ }
        if (bmp == null) { Ui.alert(this, "Icono", "El archivo no es una imagen válida"); setResult(RESULT_CANCELED); return; }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Ui.BG);
        cv = new CropView(this, bmp);
        root.addView(cv, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout bar = Ui.row(this);
        int p = Ui.dp(this, 10); bar.setPadding(p, p, p, p);
        bar.addView(btn("⟲", Ui.CARD2, new Runnable() { public void run() { cv.rotate(-90); } }), w(0.6f));
        bar.addView(btn("⟳", Ui.CARD2, new Runnable() { public void run() { cv.rotate(90); } }), w(0.6f));
        bar.addView(btn("Cancelar", Ui.CARD2, new Runnable() { public void run() { setResult(RESULT_CANCELED); finish(); } }), w(1));
        bar.addView(btn("Usar", Ui.ACCENT, new Runnable() { public void run() { save(); } }), w(1));
        root.addView(bar);
        setContentView(root);
    }

    private LinearLayout.LayoutParams w(float wt) { LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, -2, wt); l.leftMargin = Ui.dp(this, 4); l.rightMargin = Ui.dp(this, 4); return l; }

    private View btn(String s, int col, final Runnable r) {
        android.widget.Button b = Ui.button(this, s, col);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        return b;
    }

    private void save() {
        try {
            File f = new File(getCacheDir(), "icon_crop.png");
            FileOutputStream fo = new FileOutputStream(f);
            try { cv.render(512).compress(Bitmap.CompressFormat.PNG, 100, fo); } finally { fo.close(); }
            setResult(RESULT_OK, new Intent().putExtra(OUT, f.getAbsolutePath()));
        } catch (Exception e) { setResult(RESULT_CANCELED); }
        finish();
    }

    static final class CropView extends View {
        private final Bitmap bmp;
        private final Matrix m = new Matrix();
        private final RectF fr = new RectF();
        private final Paint pDim = new Paint(), pLine = new Paint(Paint.ANTI_ALIAS_FLAG), pImg = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Matrix m0 = new Matrix();
        private final RectF fr0 = new RectF();
        private final android.view.GestureDetector gd;
        private boolean init;
        private int corner = -1; // 0..3 = TL TR BR BL, -1 = imagen
        private float lx, ly, lx2, ly2;
        private int ptrs;
        private final float hit;

        CropView(Context c, Bitmap b) {
            super(c); bmp = b; hit = Ui.dp(c, 36);
            gd = new android.view.GestureDetector(c, new android.view.GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDoubleTap(MotionEvent e) { m.set(m0); fr.set(fr0); invalidate(); return true; }
            });
            pDim.setColor(0xB0000000);
            pLine.setColor(Color.WHITE); pLine.setStyle(Paint.Style.STROKE); pLine.setStrokeWidth(Ui.dp(c, 2));
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            if (init) return; init = true;
            float s = Math.min(w, h) * 0.8f;
            fr.set((w - s) / 2, (h - s) / 2, (w + s) / 2, (h + s) / 2);
            float k = s / Math.min(bmp.getWidth(), bmp.getHeight()); // la imagen cubre el marco
            m.setScale(k, k);
            m.postTranslate(w / 2f - bmp.getWidth() * k / 2, h / 2f - bmp.getHeight() * k / 2);
            m0.set(m); fr0.set(fr);
        }

        void rotate(float deg) { m.postRotate(deg, fr.centerX(), fr.centerY()); fix(); invalidate(); }

        /** El marco nunca sale de la imagen: sube el zoom si hace falta y recoloca el centro dentro. */
        private void fix() {
            float[] v = new float[9]; m.getValues(v);
            float sc = (float) Math.hypot(v[Matrix.MSCALE_X], v[Matrix.MSKEW_Y]), th = (float) Math.atan2(v[Matrix.MSKEW_Y], v[Matrix.MSCALE_X]);
            float ext = (float) (Math.abs(Math.cos(th)) + Math.abs(Math.sin(th)));
            float need = fr.width() * ext / Math.min(bmp.getWidth(), bmp.getHeight());
            if (sc < need) { m.postScale(need / sc, need / sc, fr.centerX(), fr.centerY()); sc = need; }
            float h = fr.width() * ext / (2 * sc);
            Matrix inv = new Matrix();
            if (!m.invert(inv)) return;
            float[] c = {fr.centerX(), fr.centerY()};
            inv.mapPoints(c);
            float nx = Math.max(h, Math.min(bmp.getWidth() - h, c[0])), ny = Math.max(h, Math.min(bmp.getHeight() - h, c[1]));
            if (nx == c[0] && ny == c[1]) return;
            float[] p = {nx, ny};
            m.mapPoints(p);
            m.postTranslate(fr.centerX() - p[0], fr.centerY() - p[1]);
        }

        Bitmap render(int size) {
            Bitmap o = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Matrix t = new Matrix(m);
            t.postTranslate(-fr.left, -fr.top);
            float k = size / fr.width(); t.postScale(k, k);
            new Canvas(o).drawBitmap(bmp, t, pImg);
            return o;
        }

        @Override protected void onDraw(Canvas c) {
            c.drawBitmap(bmp, m, pImg);
            c.save(); c.clipRect(fr, Region.Op.DIFFERENCE); c.drawPaint(pDim); c.restore();
            c.drawRect(fr, pLine);
            pLine.setAlpha(90); pLine.setStrokeWidth(Ui.dp(getContext(), 1));
            float t = fr.width() / 3;
            for (int i = 1; i < 3; i++) { c.drawLine(fr.left + t * i, fr.top, fr.left + t * i, fr.bottom, pLine); c.drawLine(fr.left, fr.top + t * i, fr.right, fr.top + t * i, pLine); }
            c.drawCircle(fr.centerX(), fr.centerY(), fr.width() / 2, pLine); // máscara redonda del launcher
            pLine.setAlpha(255); pLine.setStrokeWidth(Ui.dp(getContext(), 2));
            float r = Ui.dp(getContext(), 7);
            float[][] cs = {{fr.left, fr.top}, {fr.right, fr.top}, {fr.right, fr.bottom}, {fr.left, fr.bottom}};
            for (float[] p : cs) c.drawCircle(p[0], p[1], r, pLine);
        }

        private int cornerAt(float x, float y) {
            float[][] cs = {{fr.left, fr.top}, {fr.right, fr.top}, {fr.right, fr.bottom}, {fr.left, fr.bottom}};
            for (int i = 0; i < 4; i++) if (Math.abs(x - cs[i][0]) < hit && Math.abs(y - cs[i][1]) < hit) return i;
            return -1;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            gd.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    corner = cornerAt(e.getX(), e.getY()); lx = e.getX(); ly = e.getY(); ptrs = 1; break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (corner >= 0) break;
                    ptrs = 2; lx = e.getX(0); ly = e.getY(0); lx2 = e.getX(1); ly2 = e.getY(1); break;
                case MotionEvent.ACTION_MOVE:
                    if (corner >= 0) { resize(e.getX(), e.getY()); }
                    else if (ptrs == 2 && e.getPointerCount() >= 2) {
                        float x1 = e.getX(0), y1 = e.getY(0), x2 = e.getX(1), y2 = e.getY(1);
                        float d0 = (float) Math.hypot(lx2 - lx, ly2 - ly), d1 = (float) Math.hypot(x2 - x1, y2 - y1);
                        float a0 = (float) Math.toDegrees(Math.atan2(ly2 - ly, lx2 - lx)), a1 = (float) Math.toDegrees(Math.atan2(y2 - y1, x2 - x1));
                        float cx0 = (lx + lx2) / 2, cy0 = (ly + ly2) / 2, cx1 = (x1 + x2) / 2, cy1 = (y1 + y2) / 2;
                        if (d0 > 10) { m.postScale(d1 / d0, d1 / d0, cx0, cy0); m.postRotate(a1 - a0, cx0, cy0); }
                        m.postTranslate(cx1 - cx0, cy1 - cy0);
                        lx = x1; ly = y1; lx2 = x2; ly2 = y2;
                    } else if (ptrs == 1) { m.postTranslate(e.getX() - lx, e.getY() - ly); lx = e.getX(); ly = e.getY(); }
                    fix(); invalidate(); break;
                case MotionEvent.ACTION_POINTER_UP:
                    ptrs = 1; int k = e.getActionIndex() == 0 ? 1 : 0; lx = e.getX(k); ly = e.getY(k); break;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: corner = -1; ptrs = 0; break;
            }
            return true;
        }

        /** Mueve una esquina manteniendo 1:1 y la esquina opuesta fija. */
        private void resize(float x, float y) {
            float ax = corner == 0 || corner == 3 ? fr.right : fr.left, ay = corner == 0 || corner == 1 ? fr.bottom : fr.top;
            float sx = corner == 0 || corner == 3 ? -1 : 1, sy = corner == 0 || corner == 1 ? -1 : 1;
            float s = Math.max(Ui.dp(getContext(), 60), Math.max((x - ax) * sx, (y - ay) * sy));
            // el marco no puede salirse de la pantalla
            s = Math.min(s, Math.min(sx < 0 ? ax : getWidth() - ax, sy < 0 ? ay : getHeight() - ay));
            float nx = ax + sx * s, ny = ay + sy * s;
            fr.set(Math.min(ax, nx), Math.min(ay, ny), Math.max(ax, nx), Math.max(ay, ny));
        }
    }
}
