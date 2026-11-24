package com.dskmusic.apkforge;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.*;

import com.dskmusic.apkforge.core.*;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final int REQ_ZIP = 1, REQ_FOLDER = 2, REQ_ICON = 3, REQ_SAVE = 4;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private LinearLayout root, srcBox, listBox, resultCard;
    private Pipeline.Mode mode = Pipeline.Mode.ZIP;
    private final Map<Pipeline.Mode, Button> chips = new LinkedHashMap<Pipeline.Mode, Button>();
    private TextView zipName, folderName, logView, iconInfo, resName;
    private EditText link, webUrl, name, pkg, vname, vcode, delay;
    private ImageView iconPreview;
    private CheckBox cbWebIcon, cbPortrait, cbLandscape, cbSelect, cbZoom, cbPull, cbViewport, cbFull, cbAwake, cbClean;
    private Spinner theme;
    private Button genBtn;
    private Uri zipUri, folderUri;
    private byte[] customIcon, webIcon;
    private boolean running = false;
    private File lastApk, pendingSave;
    private final Set<String> selected = new HashSet<String>();

    private File apksDir() { File d = new File(getFilesDir(), "apks"); d.mkdirs(); return d; }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        Window w = getWindow();
        w.setStatusBarColor(Ui.BG); w.setNavigationBarColor(Ui.BG);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        sv.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        root.setPadding(p, Ui.dp(this, 20), p, p);
        sv.addView(root, new ViewGroupLP());
        setContentView(sv);
        build();
        selectMode(Pipeline.Mode.ZIP);
        refreshList();
    }

    private static class ViewGroupLP extends FrameLayout.LayoutParams { ViewGroupLP() { super(-1, -2); } }

    // ------------------------------------------------------------------ UI
    private void build() {
        // cabecera
        LinearLayout head = Ui.row(this);
        ImageView logo = new ImageView(this);
        int rid = getResources().getIdentifier("ic_launcher", "mipmap", getPackageName());
        if (rid != 0) logo.setImageResource(rid);
        head.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46)));
        TextView t = Ui.text(this, "ApkForge", 24, Ui.TEXT, true);
        t.setPadding(Ui.dp(this, 12), 0, 0, 0);
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        Button gear = Ui.button(this, "⚙ Ajustes", Ui.CARD2);
        gear.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { startActivity(new Intent(MainActivity.this, SettingsActivity.class)); } });
        head.addView(gear);
        root.addView(head);
        TextView sub = Ui.text(this, BuildConfig.GOOF ? "Convierte tu web, juego o enlace en un APK firmado, sin Android Studio." : "Convierte tu web o tu proyecto HTML en un APK firmado, sin Android Studio.", 13, Ui.MUTED, false);
        sub.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 16));
        root.addView(sub);

        // origen
        LinearLayout c1 = Ui.card(this);
        c1.addView(Ui.text(this, "1 · Origen", 16, Ui.TEXT, true));
        LinearLayout chipRow = Ui.row(this);
        // BuildConfig.GOOF solo es true en tu build local (con keystore.properties); el de GitHub no incluye goof.
        String[] labels = BuildConfig.GOOF ? new String[]{"ZIP / APK", "Carpeta", "Enlace goof", "Web"} : new String[]{"ZIP / APK", "Carpeta", "Web"};
        Pipeline.Mode[] modes = BuildConfig.GOOF ? new Pipeline.Mode[]{Pipeline.Mode.ZIP, Pipeline.Mode.FOLDER, Pipeline.Mode.GOOF, Pipeline.Mode.WEB} : new Pipeline.Mode[]{Pipeline.Mode.ZIP, Pipeline.Mode.FOLDER, Pipeline.Mode.WEB};
        for (int i = 0; i < modes.length; i++) {
            final Pipeline.Mode m = modes[i];
            Button bt = Ui.button(this, labels[i], Ui.CARD2);
            bt.setTextSize(12.5f);
            bt.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10));
            bt.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { selectMode(m); } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.rightMargin = i < modes.length - 1 ? Ui.dp(this, 6) : 0;
            chipRow.addView(bt, lp);
            chips.put(m, bt);
        }
        Ui.gap(c1, 12);
        c1.addView(chipRow);
        srcBox = new LinearLayout(this);
        srcBox.setOrientation(LinearLayout.VERTICAL);
        c1.addView(srcBox);
        zipName = Ui.text(this, "Ningún archivo elegido", 13, Ui.MUTED, false);
        folderName = Ui.text(this, "Ninguna carpeta elegida", 13, Ui.MUTED, false);
        link = Ui.edit(this, "https://goof.gg/game/…");
        webUrl = Ui.edit(this, "https://midominio.com/mi-app/");
        root.addView(c1);

        // datos
        LinearLayout c2 = Ui.card(this);
        c2.addView(Ui.text(this, "2 · Datos de la app", 16, Ui.TEXT, true));
        c2.addView(Ui.label(this, "Nombre de la app"));
        name = Ui.edit(this, "Micro Madness"); c2.addView(name);
        c2.addView(Ui.label(this, "Paquete (opcional; por defecto " + prefs.prefix() + ".<nombre>)"));
        pkg = Ui.edit(this, prefs.prefix() + ".micromadness"); c2.addView(pkg);
        LinearLayout vr = Ui.row(this);
        LinearLayout a1 = new LinearLayout(this); a1.setOrientation(LinearLayout.VERTICAL);
        a1.addView(Ui.label(this, "Versión")); vname = Ui.edit(this, "1.0"); vname.setText("1.0"); a1.addView(vname);
        LinearLayout a2 = new LinearLayout(this); a2.setOrientation(LinearLayout.VERTICAL);
        a2.addView(Ui.label(this, "versionCode (auto)")); vcode = Ui.edit(this, "auto"); vcode.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); a2.addView(vcode);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, -2, 1); l1.rightMargin = Ui.dp(this, 10);
        vr.addView(a1, l1); vr.addView(a2, new LinearLayout.LayoutParams(0, -2, 1));
        c2.addView(vr);
        root.addView(c2);

        // icono
        LinearLayout c3 = Ui.card(this);
        c3.addView(Ui.text(this, "3 · Icono", 16, Ui.TEXT, true));
        LinearLayout ir = Ui.row(this);
        iconPreview = new ImageView(this);
        iconPreview.setBackground(Ui.round(Ui.CARD2, 14, this));
        iconPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ir.addView(iconPreview, new LinearLayout.LayoutParams(Ui.dp(this, 72), Ui.dp(this, 72)));
        LinearLayout ib = new LinearLayout(this); ib.setOrientation(LinearLayout.VERTICAL);
        ib.setPadding(Ui.dp(this, 12), 0, 0, 0);
        iconInfo = Ui.text(this, "", 13, Ui.MUTED, false);
        ib.addView(iconInfo);
        LinearLayout ibr = Ui.row(this);
        Button pick = Ui.button(this, "Elegir…", Ui.CARD2);
        pick.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pick("image/*", REQ_ICON, false); } });
        Button clr = Ui.button(this, "Quitar", Ui.CARD2);
        clr.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { customIcon = null; updateIcon(); } });
        ibr.addView(pick); Space sp = new Space(this); ibr.addView(sp, new LinearLayout.LayoutParams(Ui.dp(this, 8), 1)); ibr.addView(clr);
        ib.addView(ibr);
        ir.addView(ib, new LinearLayout.LayoutParams(0, -2, 1));
        c3.addView(ir);
        cbWebIcon = Ui.check(this, "En modo web, usar el icono (favicon) de la web", true);
        c3.addView(cbWebIcon);
        root.addView(c3);
        updateIcon();

        // opciones
        LinearLayout c4 = Ui.card(this);
        c4.addView(Ui.text(this, "4 · Opciones", 16, Ui.TEXT, true));
        cbClean = Ui.check(this, "Quitar splash y aviso de dominio de goof", BuildConfig.GOOF);
        cbPortrait = Ui.check(this, "Permitir modo vertical", true);
        cbLandscape = Ui.check(this, "Permitir modo horizontal", false);
        cbSelect = Ui.check(this, "Selección de texto (pulsación larga)", false);
        cbZoom = Ui.check(this, "Zoom con dos dedos", false);
        cbPull = Ui.check(this, "Arrastrar hacia abajo para recargar", false);
        cbFull = Ui.check(this, "Pantalla completa (oculta barras)", true);
        cbAwake = Ui.check(this, "Mantener pantalla encendida", false);
        cbViewport = Ui.check(this, "Adaptar a móvil si la web no lo está", true);
        for (CheckBox cb : BuildConfig.GOOF ? new CheckBox[]{cbClean, cbPortrait, cbLandscape, cbSelect, cbZoom, cbPull, cbFull, cbAwake, cbViewport} : new CheckBox[]{cbPortrait, cbLandscape, cbSelect, cbZoom, cbPull, cbFull, cbAwake, cbViewport}) c4.addView(cb);
        c4.addView(Ui.label(this, "Tema"));
        theme = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Oscuro (por defecto)", "Claro", "Según el sistema"});
        theme.setAdapter(ad);
        theme.setBackground(Ui.round(Ui.CARD2, 10, this));
        c4.addView(theme);
        c4.addView(Ui.label(this, "Retardo al abrir (ms; cortina del color de fondo mientras carga; 0 = sin retardo)"));
        delay = Ui.edit(this, "700"); delay.setText("700"); delay.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        c4.addView(delay);
        root.addView(c4);

        genBtn = Ui.button(this, "⚒  Generar APK", Ui.ACCENT);
        genBtn.setTextSize(17);
        genBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { generate(); } });
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-1, Ui.dp(this, 54));
        gl.bottomMargin = Ui.dp(this, 14);
        root.addView(genBtn, gl);

        // registro
        logView = Ui.text(this, "", 12, Ui.MUTED, false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setVisibility(View.GONE);
        LinearLayout lc = Ui.card(this);
        lc.addView(logView);
        lc.setVisibility(View.GONE);
        lc.setTag("logcard");
        root.addView(lc);

        // resultado
        resultCard = Ui.card(this);
        resultCard.setBackground(Ui.round(0xFF14301E, 16, this));
        resName = Ui.text(this, "", 15, Ui.TEXT, true);
        resultCard.addView(resName);
        Ui.gap(resultCard, 10);
        LinearLayout rr = Ui.row(this);
        Button inst = Ui.button(this, "Instalar", Ui.ACCENT);
        inst.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (lastApk != null) install(lastApk); } });
        Button save = Ui.button(this, "Guardar como…", Ui.CARD2);
        save.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (lastApk != null) saveAs(lastApk); } });
        ImageButton shr = Ui.iconButton(this, R.drawable.ic_share, Ui.CARD2);
        shr.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (lastApk != null) share(lastApk); } });
        Button again = Ui.button(this, "↺ Empezar de nuevo", Ui.CARD2);
        again.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { resetForm(); } });
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, -2, 1); wl.rightMargin = Ui.dp(this, 8);
        LinearLayout.LayoutParams wl2 = new LinearLayout.LayoutParams(0, -2, 1); wl2.rightMargin = Ui.dp(this, 8);
        rr.addView(inst, wl); rr.addView(save, wl2); rr.addView(shr, new LinearLayout.LayoutParams(-2, Ui.dp(this, 42)));
        resultCard.addView(rr);
        Ui.gap(resultCard, 8);
        resultCard.addView(again);
        resultCard.setVisibility(View.GONE);
        root.addView(resultCard);

        // lista
        listBox = Ui.card(this);
        root.addView(listBox);

        // pie
        TextView foot = new TextView(this);
        SpannableString ss = new SpannableString("Made with ❤ by DSK");
        int h = ss.toString().indexOf('❤');
        ss.setSpan(new ForegroundColorSpan(0xFFFF4D6A), h, h + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int by = ss.toString().indexOf("DSK");
        ss.setSpan(new ClickableSpan() {
            public void onClick(View v) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://dskmusic.com"))); }
            public void updateDrawState(TextPaint ds) { ds.setColor(Ui.ACCENT); ds.setUnderlineText(false); ds.setFakeBoldText(true); }
        }, by, by + 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        foot.setText(ss);
        foot.setMovementMethod(LinkMovementMethod.getInstance());
        foot.setTextColor(Ui.MUTED); foot.setTextSize(13); foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 24));
        root.addView(foot, new LinearLayout.LayoutParams(-1, -2));
    }

    private void selectMode(Pipeline.Mode m) {
        mode = m;
        for (Map.Entry<Pipeline.Mode, Button> e : chips.entrySet()) e.getValue().setBackground(Ui.round(e.getKey() == m ? Ui.ACCENT : Ui.CARD2, 12, this));
        srcBox.removeAllViews();
        Ui.gap(srcBox, 12);
        if (m == Pipeline.Mode.ZIP) {
            Button b = Ui.button(this, "Elegir ZIP o APK…", Ui.CARD2);
            b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pick("*/*", REQ_ZIP, false); } });
            srcBox.addView(b); Ui.gap(srcBox, 6); srcBox.addView(zipName);
            srcBox.addView(Ui.text(this, "Con index.html en la raíz del ZIP (o dentro de una carpeta)." + (BuildConfig.GOOF ? " También vale el .apk de goof." : "") + " Funciona sin conexión.", 12, Ui.MUTED, false));
        } else if (m == Pipeline.Mode.FOLDER) {
            Button b = Ui.button(this, "Elegir carpeta…", Ui.CARD2);
            b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_FOLDER); } });
            srcBox.addView(b); Ui.gap(srcBox, 6); srcBox.addView(folderName);
            srcBox.addView(Ui.text(this, "Se incluye todo lo de esa carpeta y de sus subcarpetas; la raíz es la del index.html más cercano.", 12, Ui.MUTED, false));
        } else if (m == Pipeline.Mode.GOOF) {
            srcBox.addView(Ui.label(this, "Enlace del juego"));
            detach(link); srcBox.addView(link);
            srcBox.addView(Ui.text(this, "Descarga el juego y sus recursos (necesita Internet al generar). Usa tus propios juegos.", 12, Ui.MUTED, false));
        } else {
            srcBox.addView(Ui.label(this, "URL de la web (la app la abrirá online)"));
            detach(webUrl); srcBox.addView(webUrl);
            Button b = Ui.button(this, "🔍 Buscar icono de la web", Ui.CARD2);
            b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { fetchWebIcon(); } });
            Ui.gap(srcBox, 8); srcBox.addView(b);
        }
    }

    private void detach(View v) { if (v.getParent() != null) ((android.view.ViewGroup) v.getParent()).removeView(v); }

    private void updateIcon() {
        byte[] b = customIcon != null ? customIcon : null;
        if (b != null) { iconPreview.setImageBitmap(BitmapFactory.decodeByteArray(b, 0, b.length)); iconInfo.setText("Icono propio"); }
        else if (webIcon != null) { iconPreview.setImageBitmap(BitmapFactory.decodeByteArray(webIcon, 0, webIcon.length)); iconInfo.setText("Icono de la web (si no eliges otro)"); }
        else {
            int rid = getResources().getIdentifier("default_icon", "drawable", getPackageName());
            if (rid != 0) iconPreview.setImageResource(rid); else iconPreview.setImageDrawable(null);
            iconInfo.setText("Por defecto (DSK Apps)");
        }
    }

    private void pick(String type, int req, boolean multi) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(type);
        if (req == REQ_ZIP) i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed", "application/vnd.android.package-archive", "application/octet-stream"});
        startActivityForResult(i, req);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        try {
            if (req == REQ_ZIP) { zipUri = u; zipName.setText(displayName(u)); if (name.getText().length() == 0) name.setText(stripExt(displayName(u))); }
            else if (req == REQ_FOLDER) { folderUri = u; folderName.setText(Uri.decode(u.getLastPathSegment() == null ? u.toString() : u.getLastPathSegment())); }
            else if (req == REQ_ICON) {
                InputStream in = getContentResolver().openInputStream(u);
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] b = new byte[16384]; int n;
                while ((n = in.read(b)) >= 0) { bo.write(b, 0, n); if (bo.size() > 12 << 20) throw new IOException("Imagen demasiado grande"); }
                in.close();
                customIcon = bo.toByteArray();
                if (BitmapFactory.decodeByteArray(customIcon, 0, customIcon.length) == null) { customIcon = null; Ui.alert(this, "Icono", "El archivo no es una imagen válida"); }
                updateIcon();
            } else if (req == REQ_SAVE && pendingSave != null) {
                InputStream in = new FileInputStream(pendingSave);
                OutputStream out = getContentResolver().openOutputStream(u);
                try { byte[] b = new byte[65536]; int n; while ((n = in.read(b)) >= 0) out.write(b, 0, n); } finally { in.close(); out.close(); }
                Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) { Ui.alert(this, "Error", String.valueOf(e.getMessage())); }
    }

    private String displayName(Uri u) {
        android.database.Cursor c = getContentResolver().query(u, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null);
        try { if (c != null && c.moveToFirst()) return c.getString(0); } finally { if (c != null) c.close(); }
        return u.getLastPathSegment();
    }

    private static String stripExt(String s) { int d = s == null ? -1 : s.lastIndexOf('.'); return d > 0 ? s.substring(0, d) : (s == null ? "" : s); }

    // ------------------------------------------------------------------ icono web
    private void fetchWebIcon() {
        final String u = webUrl.getText().toString().trim();
        if (u.length() == 0) { Ui.alert(this, "Icono", "Escribe primero la URL de la web"); return; }
        iconInfo.setText("Buscando icono…");
        new Thread(new Runnable() { public void run() {
            IconFetch.Result r = null; String err = null;
            try { r = IconFetch.find(u); } catch (Exception e) { err = e.getMessage(); }
            final IconFetch.Result rr = r; final String ee = err;
            ui.post(new Runnable() { public void run() {
                if (rr == null) { webIcon = null; updateIcon(); Ui.alert(MainActivity.this, "Icono", "No se encontró icono" + (ee != null ? " (" + ee + ")" : "") + ". Se usará el tuyo o el de por defecto."); }
                else if (!rr.good) { webIcon = null; updateIcon(); Ui.alert(MainActivity.this, "Icono", "El icono de la web es de baja calidad (" + rr.size + " px < 128). Se usará el tuyo o el de por defecto."); }
                else { webIcon = rr.png; updateIcon(); }
            } });
        } }).start();
    }

    // ------------------------------------------------------------------ generar
    private void log(final String s) {
        ui.post(new Runnable() { public void run() {
            ((View) logView.getParent()).setVisibility(View.VISIBLE);
            logView.setVisibility(View.VISIBLE);
            logView.append(s + "\n");
        } });
    }

    private void generate() {
        if (running) return;
        final String nm = name.getText().toString().trim();
        if (nm.length() == 0) { Ui.alert(this, "Falta el nombre", "Escribe el nombre de la app."); return; }
        if (mode == Pipeline.Mode.ZIP && zipUri == null) { Ui.alert(this, "Falta el archivo", "Elige un ZIP o APK."); return; }
        if (mode == Pipeline.Mode.FOLDER && folderUri == null) { Ui.alert(this, "Falta la carpeta", "Elige una carpeta."); return; }
        if (mode == Pipeline.Mode.GOOF && link.getText().toString().trim().length() == 0) { Ui.alert(this, "Falta el enlace", "Pega el enlace de goof.gg."); return; }
        if (mode == Pipeline.Mode.WEB && webUrl.getText().toString().trim().length() == 0) { Ui.alert(this, "Falta la URL", "Escribe la URL de la web."); return; }
        if (prefs.storePass().length() == 0) {
            Ui.confirm(this, "Contraseña del keystore", "Aún no has guardado la contraseña del keystore. Se pide una sola vez en Ajustes.", "Ir a Ajustes",
                    new Runnable() { public void run() { startActivity(new Intent(MainActivity.this, SettingsActivity.class)); } });
            return;
        }
        running = true; genBtn.setEnabled(false); genBtn.setText("Generando…");
        logView.setText(""); resultCard.setVisibility(View.GONE);

        final Pipeline.Request r = new Pipeline.Request();
        r.mode = mode; r.name = nm; r.pkg = pkg.getText().toString().trim();
        r.versionName = vname.getText().toString(); r.prefix = prefs.prefix();
        String vc = vcode.getText().toString().trim();
        try { r.versionCode = vc.length() == 0 ? 0 : Long.parseLong(vc); } catch (NumberFormatException e) { r.versionCode = 0; }
        Options o = r.opt;
        o.cleanGoof = cbClean.isChecked(); o.allowPortrait = cbPortrait.isChecked(); o.allowLandscape = cbLandscape.isChecked();
        if (!o.allowPortrait && !o.allowLandscape) { o.allowPortrait = true; o.allowLandscape = true; }
        o.selectText = cbSelect.isChecked(); o.zoom = cbZoom.isChecked(); o.pullRefresh = cbPull.isChecked();
        o.fullscreen = cbFull.isChecked(); o.keepAwake = cbAwake.isChecked(); o.mobileViewport = cbViewport.isChecked();
        o.theme = theme.getSelectedItemPosition() == 1 ? "light" : theme.getSelectedItemPosition() == 2 ? "system" : "dark";
        try { o.loadDelay = Integer.parseInt(delay.getText().toString().trim()); } catch (NumberFormatException e) { o.loadDelay = 700; }
        r.link = link.getText().toString().trim(); r.webUrl = webUrl.getText().toString().trim();
        r.iconBytes = customIcon;
        final boolean useWebIcon = mode == Pipeline.Mode.WEB && cbWebIcon.isChecked() && customIcon == null;
        final String alias = prefs.alias(), sp = prefs.storePass(), kp = prefs.keyPass();
        r.alias = alias;
        r.workDir = new File(getCacheDir(), "work"); r.outDir = apksDir();
        r.iconMaker = new IconMakerAndroid();

        new Thread(new Runnable() { public void run() {
            try {
                r.baseApk = readAsset("base.apk"); r.defaultIcon = readAsset("default_icon.png");
                log("Cargando keystore…");
                r.key = Jks.load(new KeystoreStore(MainActivity.this).read(), sp.toCharArray(), alias, kp.toCharArray());
                if (mode == Pipeline.Mode.ZIP) {
                    log("Leyendo el archivo…");
                    File z = new File(getCacheDir(), "in.zip");
                    copyUri(zipUri, z);
                    r.zipFile = z;
                } else if (mode == Pipeline.Mode.FOLDER) {
                    log("Copiando la carpeta…");
                    File d = new File(getCacheDir(), "folder");
                    Files2.deleteTree(d);
                    new FolderCopy(getContentResolver(), r.maxBytes, r.maxFiles).copy(folderUri, d);
                    r.folderDir = d;
                }
                if (useWebIcon) {
                    byte[] wi = webIcon;
                    if (wi == null) { try { IconFetch.Result ir = IconFetch.find(r.webUrl); if (ir != null && ir.good) wi = ir.png; } catch (Exception e) { log("Icono web no disponible: " + e.getMessage()); } }
                    r.iconBytes = wi;
                }
                final File apk = Pipeline.run(r, new Logger() { public void log(String m) { MainActivity.this.log(m); } });
                if (zipFileExists(r)) r.zipFile.delete();
                ui.post(new Runnable() { public void run() { done(apk, null); } });
            } catch (final Throwable e) {
                ui.post(new Runnable() { public void run() { done(null, e); } });
            }
        } }).start();
    }

    private boolean zipFileExists(Pipeline.Request r) { return r.zipFile != null && r.zipFile.exists(); }

    private void done(File apk, Throwable err) {
        running = false; genBtn.setEnabled(true); genBtn.setText("⚒  Generar APK");
        if (err != null) {
            String m = err.getMessage() == null ? err.toString() : err.getMessage();
            if (m.contains("Password verification") || m.toLowerCase(Locale.ROOT).contains("contraseña"))
                m += "\n\nRevisa la contraseña y el alias del keystore en Ajustes.";
            Ui.alert(this, "Error", m);
            return;
        }
        lastApk = apk;
        resName.setText("✔ " + apk.getName() + " listo (" + fmt(apk.length()) + ")");
        resultCard.setVisibility(View.VISIBLE);
        refreshList();
        Ui.choose(this, "APK listo", new String[]{"Instalar", "Compartir", "Cerrar"}, new int[]{Ui.ACCENT, Ui.CARD2, Ui.CARD2},
                new Runnable[]{new Runnable() { public void run() { install(lastApk); } }, new Runnable() { public void run() { share(lastApk); } }, null});
    }

    private byte[] readAsset(String n) throws IOException {
        InputStream in = getAssets().open(n);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try { byte[] b = new byte[65536]; int k; while ((k = in.read(b)) >= 0) bo.write(b, 0, k); } finally { in.close(); }
        return bo.toByteArray();
    }

    private void copyUri(Uri u, File dst) throws IOException {
        InputStream in = getContentResolver().openInputStream(u);
        OutputStream out = new FileOutputStream(dst);
        try { byte[] b = new byte[65536]; int n; while ((n = in.read(b)) >= 0) out.write(b, 0, n); } finally { in.close(); out.close(); }
    }

    private void resetForm() {
        name.setText(""); pkg.setText(""); vname.setText("1.0"); vcode.setText(""); link.setText(""); webUrl.setText("");
        zipUri = null; folderUri = null; customIcon = null; webIcon = null; lastApk = null;
        zipName.setText("Ningún archivo elegido"); folderName.setText("Ninguna carpeta elegida");
        resultCard.setVisibility(View.GONE); logView.setText(""); ((View) logView.getParent()).setVisibility(View.GONE);
        updateIcon();
    }

    // ------------------------------------------------------------------ instalar / guardar
    private void install(File f) {
        if (!Installer.ensureAllowed(this)) { Ui.alert(this, "Permiso necesario", "Activa «Permitir de esta fuente» para ApkForge y vuelve a pulsar Instalar."); return; }
        try { Installer.install(this, f); } catch (Exception e) { Ui.alert(this, "No se pudo instalar", String.valueOf(e.getMessage())); }
    }

    private void saveAs(File f) {
        pendingSave = f;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/vnd.android.package-archive");
        i.putExtra(Intent.EXTRA_TITLE, f.getName());
        startActivityForResult(i, REQ_SAVE);
    }

    // ------------------------------------------------------------------ lista
    private static String fmt(long n) { return n >= 1 << 20 ? String.format(Locale.US, "%.1f MB", n / 1048576.0) : (n >> 10) + " KB"; }

    private void refreshList() {
        listBox.removeAllViews();
        File[] fs = apksDir().listFiles();
        listBox.addView(Ui.text(this, "APKs generados", 16, Ui.TEXT, true));
        if (fs == null || fs.length == 0) { listBox.addView(Ui.label(this, "Aún no hay ninguno.")); return; }
        Arrays.sort(fs, new Comparator<File>() { public int compare(File a, File b) { return Long.compare(b.lastModified(), a.lastModified()); } });
        selected.retainAll(namesOf(fs));
        LinearLayout top = Ui.row(this);
        final CheckBox all = Ui.check(this, "Seleccionar todos", selected.size() == fs.length);
        final Button del = Ui.button(this, selected.isEmpty() ? "Borrar" : "Borrar (" + selected.size() + ")", Ui.RED);
        del.setEnabled(!selected.isEmpty());
        final ImageButton shr = Ui.iconButton(this, R.drawable.ic_share, Ui.ACCENT);
        shr.setEnabled(!selected.isEmpty()); shr.setAlpha(selected.isEmpty() ? 0.4f : 1f);
        shr.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            List<File> l = new ArrayList<File>(); for (String n : selected) l.add(new File(apksDir(), n)); share(l); } });
        top.addView(all, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, Ui.dp(this, 42)); sl.rightMargin = Ui.dp(this, 8);
        top.addView(shr, sl); top.addView(del);
        listBox.addView(top);
        final File[] fsf = fs;
        all.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            selected.clear(); if (all.isChecked()) selected.addAll(namesOf(fsf)); refreshList(); } });
        del.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            final List<String> sel = new ArrayList<String>(selected);
            Ui.confirm(MainActivity.this, "Borrar APK", "¿Borrar " + sel.size() + " APK seleccionado" + (sel.size() > 1 ? "s" : "") + "? No se puede deshacer.\n\n" + join(sel), "Borrar",
                    new Runnable() { public void run() { for (String n : sel) new File(apksDir(), n).delete(); selected.clear(); if (lastApk != null && !lastApk.exists()) resultCard.setVisibility(View.GONE); refreshList(); } });
        } });
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        for (final File f : fs) {
            LinearLayout r = Ui.row(this);
            r.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
            final CheckBox cb = new CheckBox(this);
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
            cb.setChecked(selected.contains(f.getName()));
            cb.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                if (cb.isChecked()) selected.add(f.getName()); else selected.remove(f.getName()); refreshList(); } });
            r.addView(cb);
            LinearLayout tx = new LinearLayout(this); tx.setOrientation(LinearLayout.VERTICAL);
            tx.addView(Ui.text(this, f.getName(), 14.5f, Ui.TEXT, true));
            tx.addView(Ui.text(this, df.format(new Date(f.lastModified())) + " · " + fmt(f.length()), 12, Ui.MUTED, false));
            tx.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { actions(f); } });
            r.addView(tx, new LinearLayout.LayoutParams(0, -2, 1));
            listBox.addView(r);
        }
    }

    private static List<String> namesOf(File[] fs) { List<String> l = new ArrayList<String>(); for (File f : fs) l.add(f.getName()); return l; }
    private static String join(List<String> l) { StringBuilder sb = new StringBuilder(); for (String s : l) { if (sb.length() > 0) sb.append('\n'); sb.append(s); } return sb.toString(); }

    private void actions(final File f) {
        Ui.choose(this, f.getName(), new String[]{"Instalar", "Compartir", "Guardar como…", "Borrar"},
                new int[]{Ui.ACCENT, Ui.CARD2, Ui.CARD2, Ui.RED},
                new Runnable[]{
                    new Runnable() { public void run() { install(f); } },
                    new Runnable() { public void run() { share(f); } },
                    new Runnable() { public void run() { saveAs(f); } },
                    new Runnable() { public void run() { Ui.confirm(MainActivity.this, "Borrar APK", "¿Borrar " + f.getName() + "?", "Borrar", new Runnable() { public void run() { f.delete(); selected.remove(f.getName()); refreshList(); } }); } }});
    }

    private void share(File f) { share(Collections.singletonList(f)); }

    private void share(List<File> fs) {
        ArrayList<Uri> us = new ArrayList<Uri>();
        for (File f : fs) us.add(ApkShare.uriFor(this, f));
        Intent i = new Intent(us.size() == 1 ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE);
        i.setType("application/vnd.android.package-archive");
        if (us.size() == 1) i.putExtra(Intent.EXTRA_STREAM, us.get(0)); else i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, us);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Compartir"));
    }

    @Override public void onConfigurationChanged(Configuration c) { super.onConfigurationChanged(c); }
}
