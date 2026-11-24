package com.dskmusic.apkforge;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

import java.util.Random;

/** Easter egg: lluvia de código estilo Matrix con «Made with ❤ by DSK» (con glitch). Se cierra tocando o con atrás. */
final class EasterEgg {
    static void show(Activity a) {
        Dialog d = new Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final Dialog dd = d;
        Rain r = new Rain(a);
        r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { dd.dismiss(); } });
        d.setContentView(r);
        d.show();   // atrás cierra el diálogo por defecto
    }

    private static final class Rain extends View {
        private static final String GLYPHS = "01ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉ<>{}[]()/\\=+*#$%&;:?!_";
        private static final String MSG_A = "Made with ", MSG_H = "❤", MSG_B = " by DSK";
        private static final int TRAIL = 16;
        private final Random rnd = new Random();
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), t = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final long t0 = System.nanoTime();
        private int cols, rows, cell;
        private char[][] g;
        private float[] head, speed;

        Rain(Context c) {
            super(c);
            setBackgroundColor(Color.BLACK);
            p.setTypeface(Typeface.MONOSPACE);
            t.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            t.setTextAlign(Paint.Align.LEFT);
        }

        private char rc() { return GLYPHS.charAt(rnd.nextInt(GLYPHS.length())); }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            cell = Ui.dp(getContext(), 15);
            cols = w / cell + 1; rows = h / cell + 1;
            g = new char[cols][rows]; head = new float[cols]; speed = new float[cols];
            for (int i = 0; i < cols; i++) {
                for (int j = 0; j < rows; j++) g[i][j] = rc();
                head[i] = -rnd.nextInt(rows + TRAIL); speed[i] = 0.12f + rnd.nextFloat() * 0.35f;
            }
            p.setTextSize(cell * 0.95f);
        }

        @Override protected void onDraw(Canvas c) {
            if (g == null) return;
            // lluvia
            for (int i = 0; i < cols; i++) {
                head[i] += speed[i];
                if (head[i] - TRAIL > rows) { head[i] = -rnd.nextInt(rows / 2 + 1); speed[i] = 0.12f + rnd.nextFloat() * 0.35f; }
                int hr = (int) head[i];
                for (int k = 0; k < TRAIL; k++) {
                    int row = hr - k;
                    if (row < 0 || row >= rows) continue;
                    if (rnd.nextInt(30) == 0) g[i][row] = rc();
                    int al = (int) (230 * (1f - k / (float) TRAIL));
                    p.setColor(k == 0 ? Color.argb(255, 210, 255, 220) : Color.argb(al, 0, 255, 90));
                    c.drawText(String.valueOf(g[i][row]), i * cell, (row + 1) * cell, p);
                }
            }
            // texto: aparece letra a letra, el corazón late y de vez en cuando hay glitch
            float sec = (System.nanoTime() - t0) / 1e9f;
            String all = MSG_A + MSG_H + MSG_B;
            int shown = Math.min(all.length(), (int) (sec * 14));
            float size = Math.min(getWidth() / 11f, Ui.dp(getContext(), 38));
            t.setTextSize(size);
            float wA = t.measureText(MSG_A), wH = t.measureText(MSG_H), wB = t.measureText(MSG_B);
            float x = (getWidth() - (wA + wH + wB)) / 2f, y = getHeight() / 2f;
            // banda oscura detrás del texto para que se lea sobre la lluvia
            p.setColor(Color.argb(170, 0, 0, 0));
            c.drawRect(0, y - size * 1.2f, getWidth(), y + size * 0.55f, p);
            boolean glitch = shown == all.length() && ((int) (sec * 10) % 17 == 0 || (int) (sec * 10) % 29 == 0);
            if (glitch) {
                float j = size * 0.12f + rnd.nextFloat() * size * 0.1f;
                draw(c, x - j, y + rnd.nextInt(5) - 2, shown, Color.argb(200, 255, 0, 60), 1f);
                draw(c, x + j, y, shown, Color.argb(200, 0, 230, 255), 1f);
            }
            float pulse = 1f + 0.18f * (float) Math.sin(sec * 6);
            draw(c, x, y, shown, Color.WHITE, pulse);
            // cursor parpadeante
            if (shown < all.length() || ((int) (sec * 2)) % 2 == 0) {
                t.setColor(Color.argb(255, 0, 255, 90));
                float cx = x + t.measureText(all.substring(0, shown));
                c.drawRect(cx + 4, y - size * 0.85f, cx + 4 + size * 0.45f, y + 2, t);
            }
            postInvalidateOnAnimation();
        }

        private void draw(Canvas c, float x, float y, int shown, int color, float heartScale) {
            int n = MSG_A.length();
            t.setColor(color);
            c.drawText(MSG_A, 0, Math.min(shown, n), x, y, t);
            float wA = t.measureText(MSG_A);
            if (shown > n) {
                c.save();
                float hx = x + wA, wH = t.measureText(MSG_H);
                c.scale(heartScale, heartScale, hx + wH / 2, y - t.getTextSize() * 0.3f);
                t.setColor(color == Color.WHITE ? Color.rgb(255, 77, 106) : color);
                c.drawText(MSG_H, hx, y, t);
                c.restore();
                t.setColor(color);
                if (shown > n + 1) c.drawText(MSG_B, 0, Math.min(MSG_B.length(), shown - n - 1), hx + wH, y, t);
            }
        }
    }
}
