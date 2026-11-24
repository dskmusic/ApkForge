package com.dskmusic.apkforge;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.*;

import com.dskmusic.apkforge.core.Jks;
import com.dskmusic.apkforge.core.Lang;

import java.util.List;

/** Ajustes: keystore (incluido o importado), alias, contraseñas (se guardan una vez) y prefijo de paquete. */
public class SettingsActivity extends Activity {
    private static final int REQ_KS = 10;
    private Prefs prefs;
    private KeystoreStore store;
    private TextView ksName;
    private EditText alias, sp, kp, prefix;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this); store = new KeystoreStore(this);
        Prefs.applyLang(this);
        Window w = getWindow(); w.setStatusBarColor(Ui.BG); w.setNavigationBarColor(Ui.BG);
        ScrollView sv = new ScrollView(this); sv.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16); root.setPadding(p, Ui.dp(this, 20), p, p);
        sv.addView(root, new FrameLayout.LayoutParams(-1, -2));
        setContentView(sv);

        root.addView(Ui.text(this, "Ajustes", 24, Ui.TEXT, true));
        Ui.gap(root, 14);

        LinearLayout lc = Ui.card(this);
        lc.addView(Ui.text(this, "Idioma", 16, Ui.TEXT, true));
        final String[] codes = {"auto", "es", "en"};
        Spinner lang = new Spinner(this);
        lang.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{Lang.t("Automático (sistema)"), "Español", "English"}));
        lang.setBackground(Ui.round(Ui.CARD2, 10, this));
        int cur = java.util.Arrays.asList(codes).indexOf(prefs.lang());
        lang.setSelection(Math.max(0, cur));
        lang.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (codes[pos].equals(prefs.lang())) return;
                prefs.setLang(codes[pos]); Prefs.applyLang(SettingsActivity.this); recreate();
            }
            public void onNothingSelected(AdapterView<?> p) { }
        });
        Ui.gap(lc, 8);
        lc.addView(lang);
        root.addView(lc);

        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Firma (keystore)", 16, Ui.TEXT, true));
        c.addView(Ui.label(this, "Keystore en uso"));
        ksName = Ui.text(this, prefs.ksName(), 14, Ui.TEXT, false);
        c.addView(ksName);
        Ui.gap(c, 8);
        LinearLayout r = Ui.row(this);
        Button ch = Ui.button(this, "Cambiar keystore…", Ui.CARD2);
        ch.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*");
            startActivityForResult(i, REQ_KS); } });
        Button rs = Ui.button(this, "Usar el de por defecto", Ui.CARD2);
        rs.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            try { store.restoreBundled(); prefs.setKsName(prefs.defaultKsName()); ksName.setText(prefs.ksName()); } catch (Exception e) { Ui.alert(SettingsActivity.this, "Error", String.valueOf(e.getMessage())); } } });
        r.addView(ch); Space s = new Space(this); r.addView(s, new LinearLayout.LayoutParams(Ui.dp(this, 8), 1)); r.addView(rs);
        c.addView(r);
        c.addView(Ui.label(this, "Alias de la clave"));
        alias = Ui.edit(this, "alias"); alias.setText(prefs.alias()); c.addView(alias);
        c.addView(Ui.label(this, "Contraseña del keystore (se guarda una sola vez)"));
        sp = Ui.edit(this, "••••••••"); sp.setText(prefs.storePass());
        sp.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD); c.addView(sp);
        c.addView(Ui.label(this, "Contraseña de la clave (vacío = la misma del keystore)"));
        kp = Ui.edit(this, "opcional"); kp.setText(prefs.keyPass());
        kp.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD); c.addView(kp);
        root.addView(c);

        LinearLayout c2 = Ui.card(this);
        c2.addView(Ui.text(this, "Paquete", 16, Ui.TEXT, true));
        c2.addView(Ui.label(this, "Prefijo de los paquetes (p. ej. com.dskmusic → com.dskmusic.nombre)"));
        prefix = Ui.edit(this, "com.dskmusic"); prefix.setText(prefs.prefix()); c2.addView(prefix);
        root.addView(c2);

        Button save = Ui.button(this, "Guardar y comprobar", Ui.ACCENT);
        save.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { save(); } });
        root.addView(save, new LinearLayout.LayoutParams(-1, Ui.dp(this, 50)));
        Ui.gap(root, 10);
        root.addView(Ui.text(this, "Las contraseñas se guardan en el almacenamiento privado de la app. Por defecto se firma con una clave de debug pública (como la de Android): sirve para probar, pero cualquiera podría firmar «actualizaciones» de tus apps. Para publicar, importa tu propio keystore.", 12, Ui.MUTED, false));
    }

    private void save() {
        String pf = prefix.getText().toString().trim();
        if (!pf.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+")) { Ui.alert(this, "Prefijo no válido", "Ejemplo: com.dskmusic"); return; }
        try {
            Jks.Entry e = Jks.load(store.read(), sp.getText().toString().toCharArray(), alias.getText().toString().trim(), kp.getText().toString().toCharArray());
            prefs.saveKeystore(alias.getText().toString().trim(), sp.getText().toString(), kp.getText().toString(), pf);
            Ui.alert(this, "Guardado", "Keystore correcto. Certificado: " + e.chain.get(0).getSubjectX500Principal().getName());
        } catch (Exception e) {
            Ui.alert(this, "No se pudo abrir el keystore", Lang.t(String.valueOf(e.getMessage())) + Lang.t("\n\nNo se ha guardado la contraseña."));
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent d) {
        super.onActivityResult(req, res, d);
        if (req != REQ_KS || res != RESULT_OK || d == null || d.getData() == null) return;
        Uri u = d.getData();
        try {
            store.importFrom(u);
            String n = u.getLastPathSegment();
            prefs.setKsName(n == null ? "keystore importado" : n.substring(n.lastIndexOf('/') + 1));
            ksName.setText(prefs.ksName());
            Ui.alert(this, "Keystore importado", "Escribe su alias y contraseña y pulsa «Guardar y comprobar».");
        } catch (Exception e) { Ui.alert(this, "Error", String.valueOf(e.getMessage())); }
    }
}
