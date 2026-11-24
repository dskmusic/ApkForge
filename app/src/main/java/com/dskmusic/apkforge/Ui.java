package com.dskmusic.apkforge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Componentes visuales (tema oscuro moderno) creados por código. */
final class Ui {
    static final int BG = 0xFF0D1017, CARD = 0xFF171C27, CARD2 = 0xFF1F2636, LINE = 0xFF2A3347, TEXT = 0xFFE8EBF2, MUTED = 0xFF8D96AB, ACCENT = 0xFF4F8CFF, RED = 0xFFC0453F;

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static GradientDrawable round(int color, float rdp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(c, rdp));
        return g;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(CARD, 16, c));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(c, 14);
        l.setLayoutParams(lp);
        return l;
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static TextView label(Context c, String s) {
        TextView t = text(c, s, 12.5f, MUTED, false);
        t.setPadding(0, dp(c, 12), 0, dp(c, 4));
        return t;
    }

    static EditText edit(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint); e.setHintTextColor(0xFF5B6479); e.setTextColor(TEXT); e.setTextSize(15);
        e.setSingleLine(true); e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setBackground(round(CARD2, 10, c));
        int p = dp(c, 12);
        e.setPadding(p, p, p, p);
        return e;
    }

    static Button button(Context c, String s, int color) {
        Button b = new Button(c);
        b.setText(s); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setTextSize(14.5f); b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(round(color, 12, c));
        b.setStateListAnimator(null);
        int p = dp(c, 14);
        b.setPadding(p, dp(c, 10), p, dp(c, 10));
        b.setMinHeight(0); b.setMinimumHeight(0);
        return b;
    }

    static ImageButton iconButton(Context c, int res, int color) {
        ImageButton b = new ImageButton(c);
        b.setImageResource(res);
        b.setBackground(round(color, 12, c));
        int p = dp(c, 10);
        b.setPadding(p, p, p, p);
        b.setMinimumWidth(dp(c, 46)); b.setMinimumHeight(dp(c, 42));
        return b;
    }

    static CheckBox check(Context c, String s, boolean on) {
        CheckBox cb = new CheckBox(c);
        cb.setText(s); cb.setTextColor(TEXT); cb.setTextSize(14.5f); cb.setChecked(on);
        cb.setButtonTintList(ColorStateList.valueOf(ACCENT));
        return cb;
    }

    static void gap(LinearLayout l, int dpv) {
        View v = new View(l.getContext());
        l.addView(v, new LinearLayout.LayoutParams(1, dp(l.getContext(), dpv)));
    }

    static LinearLayout row(Context c) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    /** Diálogo moderno: tarjeta oscura redondeada con botones propios. */
    static android.app.Dialog show(Activity a, String title, String msg, View body) {
        android.app.Dialog d = new android.app.Dialog(a);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(round(CARD, 22, a));
        int p = dp(a, 22);
        box.setPadding(p, p, p, dp(a, 16));
        box.addView(text(a, title, 19, TEXT, true));
        if (msg != null) { TextView m = text(a, msg, 14.5f, MUTED, false); m.setLineSpacing(0, 1.15f); m.setPadding(0, dp(a, 10), 0, 0); box.addView(m); }
        if (body != null) box.addView(body);
        d.setContentView(box);
        android.view.Window w = d.getWindow();
        w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        w.setLayout(Math.min(a.getResources().getDisplayMetrics().widthPixels - dp(a, 32), dp(a, 420)), -2);
        w.setDimAmount(0.65f);
        d.show();
        return d;
    }

    static Button dlgBtn(Activity a, final android.app.Dialog d, String s, int color, final Runnable r) {
        Button b = button(a, s, color);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { d.dismiss(); if (r != null) r.run(); } });
        return b;
    }

    static void dialog(Activity a, String title, String msg, String ok, Runnable onOk, String cancel) {
        LinearLayout row = row(a);
        row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(a, 18), 0, 0);
        android.app.Dialog d = show(a, title, msg, row);
        if (cancel != null) {
            row.addView(dlgBtn(a, d, cancel, CARD2, null));
            row.addView(new Space(a), new LinearLayout.LayoutParams(dp(a, 8), 1));
        }
        row.addView(dlgBtn(a, d, ok, ok.equals("Borrar") ? RED : ACCENT, onOk));
    }

    /** Lista de acciones apiladas; colors[i] = color del botón i, acts[i] puede ser null. */
    static void choose(Activity a, String title, String[] labels, int[] colors, Runnable[] acts) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(a, 14), 0, 0);
        android.app.Dialog d = show(a, title, null, col);
        for (int i = 0; i < labels.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(a, 8);
            col.addView(dlgBtn(a, d, labels[i], colors[i], acts[i]), lp);
        }
    }

    static void alert(Activity a, String title, String msg) { dialog(a, title, msg, "Aceptar", null, null); }
    static void confirm(Activity a, String title, String msg, String ok, Runnable r) { dialog(a, title, msg, ok, r, "Cancelar"); }
}
