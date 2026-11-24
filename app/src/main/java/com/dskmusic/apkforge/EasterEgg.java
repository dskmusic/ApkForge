package com.dskmusic.apkforge;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.util.Random;

/** Easter egg: lluvia de código estilo Matrix ("System Failure") con «MADE WITH ♥ BY DSK» en un recuadro. Se cierra tocando o con atrás. */
final class EasterEgg {
    static void show(Activity a) {
        final Dialog d = new Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        Rain r = new Rain(a);
        r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } });
        d.setContentView(r);
        Window w = d.getWindow();
        if (w != null) {
            w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            w.setLayout(-1, -1);
            if (Build.VERSION.SDK_INT >= 28) w.getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        d.show();   // atrás cierra el diálogo por defecto
    }

    private static final class Rain extends View {
        private static final String GLYPHS = "01ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉ<>{}[]()/=+*#$%&;:?!_";
        private static final String MSG_A = "MADE WITH", MSG_B = "BY DSK";
        private static final int TRAIL = 14;
        private static final int GREEN = Color.rgb(70, 255, 70), LIGHT = Color.rgb(110, 255, 100);
        private final Random rnd = new Random();
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), t = new Paint(Paint.ANTI_ALIAS_FLAG), box = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path heart = new Path();
        private final RectF area = new RectF();
        private final long t0 = System.nanoTime();
        private int cols, rows, cell;
        private char[][] g;
        private float[] head, speed;

        Rain(Context c) {
            super(c);
            setBackgroundColor(Color.BLACK);
            p.setTypeface(Typeface.MONOSPACE);
            t.setTypeface(Typeface.create("serif-monospace", Typeface.BOLD));   // aspecto de máquina de escribir
            t.setLetterSpacing(0.07f);
            heart.moveTo(0, 0.45f);
            heart.cubicTo(-0.95f, -0.15f, -0.5f, -0.85f, 0, -0.35f);
            heart.cubicTo(0.5f, -0.85f, 0.95f, -0.15f, 0, 0.45f);
            heart.close();
        }

        private char rc() { return GLYPHS.charAt(rnd.nextInt(GLYPHS.length())); }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            cell = Ui.dp(getContext(), 14);
            // la lluvia solo ocupa un recuadro central: nunca llega a los bordes de la pantalla
            float mx = w * 0.09f, my = h * 0.09f;
            area.set(mx, my, w - mx, h - my);
            cols = Math.max(1, (int) (area.width() / cell)); rows = Math.max(1, (int) (area.height() / cell));
            g = new char[cols][rows]; head = new float[cols]; speed = new float[cols];
            for (int i = 0; i < cols; i++) {
                for (int j = 0; j < rows; j++) g[i][j] = rc();
                head[i] = -rnd.nextInt(rows + TRAIL); speed[i] = 0.10f + rnd.nextFloat() * 0.30f;
            }
            p.setTextSize(cell * 0.95f);
        }

        @Override protected void onDraw(Canvas c) {
            if (g == null) return;
            float fade = Ui.dp(getContext(), 46);
            // lluvia (se desvanece hacia los bordes del recuadro)
            for (int i = 0; i < cols; i++) {
                head[i] += speed[i];
                if (head[i] - TRAIL > rows) { head[i] = -rnd.nextInt(rows / 3 + 1); speed[i] = 0.10f + rnd.nextFloat() * 0.30f; }
                int hr = (int) head[i];
                float x = area.left + i * cell;
                float fx = Math.min(1f, Math.min(x - area.left, area.right - x) / fade);
                for (int k = 0; k < TRAIL; k++) {
                    int row = hr - k;
                    if (row < 0 || row >= rows) continue;
                    if (rnd.nextInt(28) == 0) g[i][row] = rc();
                    float y = area.top + (row + 1) * cell;
                    float f = fx * Math.min(1f, Math.min(y - area.top, area.bottom - y) / fade);
                    int al = (int) (235 * (1f - k / (float) TRAIL) * Math.max(0f, f));
                    if (al <= 0) continue;
                    p.setColor(k == 0 ? Color.argb(Math.min(255, al + 20), 215, 255, 225) : Color.argb(al, 0, 255, 90));
                    c.drawText(String.valueOf(g[i][row]), x, y, p);
                }
            }
            float W = getWidth(), H = getHeight(), sec = (System.nanoTime() - t0) / 1e9f;

            // tamaño del texto: que quepa en el recuadro (con margen) y no pase de un tamaño agradable
            float boxW = W * 0.9f;
            t.setTextSize(100);
            float total100 = t.measureText(MSG_A + " ") + 100 * 1.0f + t.measureText(" " + MSG_B);
            float size = Math.min(100 * (boxW - Ui.dp(getContext(), 36)) / total100, Ui.dp(getContext(), 34));
            t.setTextSize(size);
            float wA = t.measureText(MSG_A + " "), hw = size * 1.0f, wB = t.measureText(" " + MSG_B);
            float bw = wA + hw + wB + Ui.dp(getContext(), 36), bh = size * 2.0f;
            float bx = (W - bw) / 2f, by = (H - bh) / 2f, cy = H / 2f + size * 0.33f;

            // recuadro estilo "system failure": fondo casi negro verdoso, borde verde brillante con halo y líneas de barrido CRT
            box.setStyle(Paint.Style.FILL); box.setColor(Color.argb(245, 0, 26, 10));
            c.drawRect(bx, by, bx + bw, by + bh, box);
            box.setColor(Color.argb(38, 0, 0, 0));
            for (float yy = by; yy < by + bh; yy += Ui.dp(getContext(), 3)) c.drawRect(bx, yy, bx + bw, yy + Ui.dp(getContext(), 1), box);
            box.setStyle(Paint.Style.STROKE); box.setStrokeWidth(Ui.dp(getContext(), 2.5f));
            box.setColor(Color.rgb(120, 255, 110)); box.setShadowLayer(Ui.dp(getContext(), 6), 0, 0, Color.argb(200, 60, 255, 60));
            c.drawRect(bx, by, bx + bw, by + bh, box);
            box.clearShadowLayer();

            String all = MSG_A + " ♥ " + MSG_B;   // solo para contar letras
            int shown = Math.min(all.length(), (int) (sec * 12));
            float x0 = bx + (bw - (wA + hw + wB)) / 2f;
            boolean done = shown == all.length();
            int step = (int) (sec * 10);
            boolean flick = done && (step % 17 == 0 || step % 29 == 0);   // parpadeo de monitor antiguo
            if (flick) x0 += rnd.nextInt(5) - 2;
            int col = flick ? Color.argb(150 + rnd.nextInt(60), 110, 255, 100) : LIGHT;
            line(c, x0, cy, size, shown, col, 1f + 0.12f * (float) Math.sin(sec * 6));
            // cursor parpadeante tras lo escrito
            if (!done || ((int) (sec * 2)) % 2 == 0) {
                float cx = x0 + width(shown, wA, hw, wB, MSG_A, MSG_B);
                t.setColor(GREEN);
                c.drawRect(cx + 3, cy - size * 0.8f, cx + 3 + size * 0.45f, cy + 2, t);
            }
            postInvalidateOnAnimation();
        }

        /** Ancho de lo ya escrito (texto A, hueco del corazón, texto B). */
        private float width(int shown, float wA, float hw, float wB, String a, String b) {
            int na = a.length() + 1;
            if (shown <= na) return t.measureText((a + " ").substring(0, shown));
            if (shown <= na + 1) return wA + (shown >= na + 1 ? hw : 0);
            String bb = (" " + b);
            return wA + hw + t.measureText(bb.substring(0, Math.min(bb.length(), shown - na - 1)));
        }

        /** Dibuja "MADE WITH ♥ BY DSK": el corazón es un dibujo verde (no el emoji rojo) y late. */
        private void line(Canvas c, float x, float y, float size, int shown, int color, float heartScale) {
            String a = MSG_A + " ", b = " " + MSG_B;
            int na = a.length();
            t.setColor(color); t.setShadowLayer(size * 0.38f, 0, 0, Color.argb(220, 40, 255, 40));
            c.drawText(a, 0, Math.min(shown, na), x, y, t);
            float wA = t.measureText(a), hw = size;
            if (shown > na) {
                c.save();
                c.translate(x + wA + hw / 2f, y - size * 0.32f);
                float s = size * 0.52f * heartScale; c.scale(s, s);
                Paint hp = new Paint(Paint.ANTI_ALIAS_FLAG);
                hp.setColor(color); hp.setShadowLayer(0.5f, 0, 0, Color.argb(220, 40, 255, 40));
                c.drawPath(heart, hp);
                c.restore();
                if (shown > na + 1) c.drawText(b, 0, Math.min(b.length(), shown - na - 1), x + wA + hw, y, t);
            }
            t.clearShadowLayer();
        }
    }
}
