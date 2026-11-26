package com.dskmusic.shell;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.*;
import java.util.zip.CRC32;

public class MainActivity extends Activity {
    static final int REQ_FILE = 4711, REQ_MEDIA = 4712, REQ_STORAGE = 4713;

    private WebView web;
    private FrameLayout root;
    private LocalServer server;
    private Saver saver;
    private JSONObject cfg;
    private boolean fullscreen;
    private String origin;
    private String remoteUrl = "", remoteHost = "", shimJs = null;
    private volatile boolean trusted = true;
    private View cover;
    private boolean revealed = false;
    private final Handler ui = new Handler();

    /** Quita la cortina del color de fondo tras la primera carga + un pequeño retardo (evita ver la interfaz a medio cargar). */
    private void reveal() {
        if (revealed || cover == null) return;
        revealed = true;
        ui.postDelayed(new Runnable() {
            public void run() {
                if (cover == null) return;
                final View c = cover;
                cover = null;
                c.animate().alpha(0f).setDuration(220).withEndAction(new Runnable() {
                    public void run() { root.removeView(c); }
                }).start();
            }
        }, Math.max(0, cfg.optInt("loadDelay", 600)));
    }

    boolean isTrusted() { return trusted; }

    /** Modo web: ¿la URL es del mismo sitio (host o subdominio) que la web de la app? */
    private boolean sameSite(String url) {
        try {
            Uri u = Uri.parse(url);
            String sc = u.getScheme();
            if (sc == null || !(sc.equals("http") || sc.equals("https"))) return false;
            String h = u.getHost();
            if (h == null) return false;
            h = h.toLowerCase();
            if (h.startsWith("www.")) h = h.substring(4);
            return h.equals(remoteHost) || h.endsWith("." + remoteHost);
        } catch (Exception e) { return false; }
    }

    private boolean isInternal(String url) {
        return remoteUrl.length() > 0 ? sameSite(url) : url.startsWith(origin);
    }

    private void setupRemote() {
        Uri u = Uri.parse(remoteUrl);
        String h = u.getHost() == null ? "" : u.getHost().toLowerCase();
        remoteHost = h.startsWith("www.") ? h.substring(4) : h;
        origin = u.getScheme() + "://" + u.getAuthority();
        try {
            InputStream is = getAssets().open("forge/shim.js");
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) >= 0) bo.write(buf, 0, r);
            is.close();
            shimJs = new String(bo.toByteArray(), "UTF-8");
        } catch (Exception e) { shimJs = null; }
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
    }

    private void showError() {
        String fg = dark ? "#e8e8e8" : "#202020", btn = "#4f8cff";
        String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                + "<body style='margin:0;background:" + String.format("#%06X", 0xFFFFFF & bgColor) + ";color:" + fg
                + ";font-family:sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh;text-align:center'>"
                + "<div style='font-size:46px'>&#128268;</div><p style='font-size:17px;padding:0 24px'>Sin conexi&oacute;n con el servidor</p>"
                + "<button onclick='location.href=" + JSONObject.quote(remoteUrl).replace("'", "\\'")
                + "' style='background:" + btn + ";color:#fff;border:0;border-radius:10px;padding:12px 26px;font-size:16px'>Reintentar</button></body></html>";
        web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    private ValueCallback<Uri[]> fileCb;
    private PermissionRequest pendingPerm;
    private View customView;
    private WebChromeClient.CustomViewCallback customCb;
    private WebChromeClient chrome;
    private boolean dark = true;
    private int bgColor = Color.BLACK;
    private TextView ptrView;
    private float ptrStartY, ptrMax, density;
    private boolean ptrActive, ptrArmed;

    @Override
    protected void attachBaseContext(Context base) {
        // Tema forzado (oscuro por defecto): hace que prefers-color-scheme y los recursos values-night sigan la opción elegida
        String theme = "dark";
        try { theme = readConfig(base.getAssets()).optString("theme", "dark"); } catch (Exception e) { }
        if ("dark".equals(theme) || "light".equals(theme)) {
            Configuration c = new Configuration(base.getResources().getConfiguration());
            c.uiMode = (c.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | ("dark".equals(theme) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
            base = base.createConfigurationContext(c);
        }
        super.attachBaseContext(base);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        cfg = loadConfig();
        density = getResources().getDisplayMetrics().density;
        String appName = cfg.optString("name", "App");
        fullscreen = cfg.optBoolean("fullscreen", true);
        String theme = cfg.optString("theme", "dark");
        if ("system".equals(theme))
            dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        else dark = !"light".equals(theme);
        String bgCfg = cfg.optString("bg", "");
        int bg = bgCfg.length() > 0 ? parseColor(bgCfg) : (dark ? Color.BLACK : Color.WHITE);
        bgColor = bg;

        String ori = cfg.optString("orientation", "auto");
        if ("portrait".equals(ori)) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        else if ("landscape".equals(ori)) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        if (cfg.optBoolean("keepAwake", true)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setupCutout();

        root = new FrameLayout(this);
        root.setBackgroundColor(bg);
        web = new WebView(this);
        web.setBackgroundColor(bg);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        if (cfg.optInt("loadDelay", 600) > 0) {
            cover = new View(this);
            cover.setBackgroundColor(bg);
            root.addView(cover, new FrameLayout.LayoutParams(-1, -1));
            ui.postDelayed(new Runnable() { public void run() { revealed = false; reveal(); } }, 15000); // seguro: nunca se queda tapado
        }
        setContentView(root);

        remoteUrl = cfg.optString("remoteUrl", "");
        saver = new Saver(this, appName);
        if (remoteUrl.length() > 0) setupRemote(); else startServer();
        configureWebView(appName);
        applyFullscreen();

        try {
            long lut = getPackageManager().getPackageInfo(getPackageName(), 0).lastUpdateTime;
            SharedPreferences sp = getSharedPreferences("forge", MODE_PRIVATE);
            if (sp.getLong("lut", 0) != lut) {
                web.clearCache(true);
                sp.edit().putLong("lut", lut).commit();
            }
        } catch (Exception e) { }

        web.loadUrl(remoteUrl.length() > 0 ? remoteUrl : origin + "/");
    }

    // ---------------------------------------------------------------- servidor

    private void startServer() {
        final AssetManager am = getAssets();
        LocalServer.Source src = new LocalServer.Source() {
            public LocalServer.Entry open(String path) throws IOException {
                try {
                    AssetFileDescriptor fd = am.openFd(path);
                    long len = fd.getLength();
                    if (len >= 0) return new LocalServer.Entry(fd.createInputStream(), len);
                    fd.close();
                } catch (FileNotFoundException e) {
                    // no existe o está comprimido: se lee entero abajo
                }
                InputStream is = am.open(path);
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[32768];
                int r;
                while ((r = is.read(buf)) >= 0) bo.write(buf, 0, r);
                is.close();
                byte[] all = bo.toByteArray();
                return new LocalServer.Entry(new ByteArrayInputStream(all), all.length);
            }
        };
        CRC32 crc = new CRC32();
        crc.update(getPackageName().getBytes());
        int preferred = 20000 + (int) (crc.getValue() % 40000);
        server = new LocalServer(src, cfg.optString("entry", "index.html"));
        server.setMobileViewport(cfg.optBoolean("mobileViewport", true));
        final String fbHost = cfg.optString("fallbackHost", "");
        if (fbHost.length() > 0) {
            final File fbDir = new File(getFilesDir(), "fb");
            server.setFallback(new LocalServer.Source() {
                public LocalServer.Entry open(String rel) throws IOException {
                    File f = new File(fbDir, rel), mf = new File(fbDir, rel + ".mime");
                    if (!f.getCanonicalPath().startsWith(fbDir.getCanonicalPath() + File.separator)) throw new FileNotFoundException();
                    if (!f.isFile()) {
                        java.net.HttpURLConnection c = null;
                        File tmp = new File(fbDir, rel + ".part");
                        try {
                            tmp.getParentFile().mkdirs();
                            c = (java.net.HttpURLConnection) new java.net.URL(fbHost + "/" + Uri.encode(rel, "/@~!$&'()*+,;=:")).openConnection();
                            c.setConnectTimeout(15000);
                            c.setReadTimeout(30000);
                            c.setRequestProperty("User-Agent", "Mozilla/5.0");
                            if (c.getResponseCode() != 200) throw new FileNotFoundException();
                            String ct = c.getContentType();
                            InputStream in = c.getInputStream();
                            OutputStream o = new FileOutputStream(tmp);
                            byte[] buf = new byte[32768];
                            int r;
                            while ((r = in.read(buf)) >= 0) o.write(buf, 0, r);
                            o.close();
                            in.close();
                            if (ct != null) { OutputStream mo = new FileOutputStream(mf); mo.write(ct.getBytes("UTF-8")); mo.close(); }
                            if (!tmp.renameTo(f)) throw new IOException();
                        } catch (IOException ex) {
                            tmp.delete();
                            throw ex;
                        } finally {
                            if (c != null) c.disconnect();
                        }
                    }
                    String mime = null;
                    try {
                        if (mf.isFile()) {
                            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(mf), "UTF-8"));
                            mime = br.readLine();
                            br.close();
                        }
                    } catch (IOException ex) { }
                    return new LocalServer.Entry(new FileInputStream(f), f.length(), mime);
                }
            });
        }
        try {
            int port = server.start(preferred);
            origin = "http://127.0.0.1:" + port;
        } catch (IOException e) {
            finish();
        }
    }

    // ---------------------------------------------------------------- webview

    private void configureWebView(String appName) {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        boolean zoom = cfg.optBoolean("zoom", true);
        s.setSupportZoom(zoom);
        s.setBuiltInZoomControls(zoom);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        if (!cfg.optBoolean("selectText", true)) {
            web.setLongClickable(false);
            web.setHapticFeedbackEnabled(false);
            web.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) { return true; }
            });
        }
        if (cfg.optBoolean("pullRefresh", true)) setupPullToRefresh();
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.addJavascriptInterface(new Bridge(this, saver, appName, cfg.toString()), "ForgeNative");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                if (isInternal(url) || url.startsWith("blob:") || url.startsWith("about:") || url.startsWith("data:")) return false;
                openExternal(url);
                return true;
            }

            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap icon) {
                if (remoteUrl.length() == 0) return;
                trusted = sameSite(url);
                if (trusted && shimJs != null) v.evaluateJavascript(shimJs, null);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                reveal();
                if (remoteUrl.length() == 0 || !trusted) return;
                if (shimJs != null) v.evaluateJavascript(shimJs, null);
                if (cfg.optBoolean("mobileViewport", true))
                    v.evaluateJavascript("(function(){if(!document.querySelector('meta[name=viewport]')&&document.head){var m=document.createElement('meta');m.name='viewport';m.content='width=device-width,initial-scale=1';document.head.appendChild(m)}})()", null);
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (remoteUrl.length() > 0 && req.isForMainFrame()) showError();
            }
        });

        chrome = new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    startActivityForResult(p.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest r) {
                runOnUiThread(new Runnable() {
                    public void run() { handlePermission(r); }
                });
            }

            @Override
            public void onShowCustomView(View v, CustomViewCallback cb) {
                if (customView != null) { cb.onCustomViewHidden(); return; }
                customView = v;
                customCb = cb;
                root.addView(v, new FrameLayout.LayoutParams(-1, -1));
                web.setVisibility(View.GONE);
                immersive();
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                root.removeView(customView);
                customView = null;
                web.setVisibility(View.VISIBLE);
                if (customCb != null) customCb.onCustomViewHidden();
                customCb = null;
                applyFullscreen();
            }
        };
        web.setWebChromeClient(chrome);

        web.setDownloadListener(new DownloadListener() {
            public void onDownloadStart(String url, String ua, String cd, String mime, long len) {
                String fn = URLUtil.guessFileName(url, cd, mime);
                if (url.startsWith("blob:") || url.startsWith("data:") || isInternal(url)) {
                    web.evaluateJavascript("window.__forgeSaveUrl&&window.__forgeSaveUrl("
                            + JSONObject.quote(url) + "," + JSONObject.quote(fn) + ")", null);
                } else {
                    openExternal(url);
                }
            }
        });
    }

    private void handlePermission(PermissionRequest r) {
        if (!isInternal(r.getOrigin().toString())) { r.deny(); return; }
        java.util.ArrayList<String> need = new java.util.ArrayList<String>();
        for (String res : r.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res)) need.add("android.permission.CAMERA");
            else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res)) need.add("android.permission.RECORD_AUDIO");
        }
        if (Build.VERSION.SDK_INT < 23) { r.grant(r.getResources()); return; }
        java.util.ArrayList<String> missing = new java.util.ArrayList<String>();
        for (String p : need) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        if (missing.isEmpty()) { r.grant(r.getResources()); return; }
        if (pendingPerm != null) pendingPerm.deny();
        pendingPerm = r;
        requestPermissions(missing.toArray(new String[0]), REQ_MEDIA);
    }

    private void openExternal(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(i);
        } catch (Exception e) { }
    }

    // ---------------------------------------------------------------- ciclo de vida

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_FILE) {
            if (fileCb != null) {
                fileCb.onReceiveValue(res == RESULT_OK ? WebChromeClient.FileChooserParams.parseResult(res, data) : null);
                fileCb = null;
            }
            return;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        boolean ok = results.length > 0;
        for (int r : results) if (r != PackageManager.PERMISSION_GRANTED) ok = false;
        if (code == REQ_MEDIA && pendingPerm != null) {
            if (ok) pendingPerm.grant(pendingPerm.getResources()); else pendingPerm.deny();
            pendingPerm = null;
        } else if (code == REQ_STORAGE) {
            saver.onStoragePermission(ok);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
        web.pauseTimers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        web.resumeTimers();
    }

    @Override
    protected void onDestroy() {
        if (server != null) server.stop();
        if (web != null) { root.removeView(web); web.destroy(); }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (customView != null) { chrome.onHideCustomView(); return; }
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) { if (customView != null) immersive(); else applyFullscreen(); }
    }

    // ---------------------------------------------------------------- pantalla

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void applyFullscreen() {
        if (fullscreen) immersive();
        else {
            int f = View.SYSTEM_UI_FLAG_VISIBLE;
            if (!dark && Build.VERSION.SDK_INT >= 23) f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            getWindow().getDecorView().setSystemUiVisibility(f);
            try { getWindow().setStatusBarColor(bgColor); getWindow().setNavigationBarColor(bgColor); } catch (Throwable t) { }
        }
    }

    // ---------------------------------------------------------------- pull to refresh (propio, sin androidx)

    private void setupPullToRefresh() {
        ptrMax = 96 * density;
        ptrView = new TextView(this);
        int sz = (int) (40 * density);
        ptrView.setText("\u21BB");
        ptrView.setTextSize(22);
        ptrView.setGravity(Gravity.CENTER);
        ptrView.setTextColor(dark ? Color.WHITE : Color.parseColor("#222222"));
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(dark ? Color.parseColor("#2A2F3E") : Color.WHITE);
        gd.setStroke((int) density, dark ? Color.parseColor("#444B63") : Color.parseColor("#CCCCCC"));
        ptrView.setBackground(gd);
        ptrView.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(sz, sz, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        root.addView(ptrView, lp);
        web.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        ptrActive = !web.canScrollVertically(-1);
                        ptrArmed = false;
                        ptrStartY = e.getY();
                        break;
                    case MotionEvent.ACTION_POINTER_DOWN:
                        ptrActive = false;
                        hidePtr();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (ptrActive) {
                            float dy = e.getY() - ptrStartY;
                            if (dy > 16 * density) {
                                float p = Math.min(dy, ptrMax);
                                ptrView.setVisibility(View.VISIBLE);
                                ptrView.setTranslationY(p * 0.7f - 32 * density);
                                ptrView.setRotation(p / ptrMax * 270f);
                                ptrView.setAlpha(Math.min(1f, p / ptrMax + 0.25f));
                                ptrArmed = dy >= ptrMax;
                            } else if (dy < -8 * density) {
                                ptrActive = false;
                                hidePtr();
                            }
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        if (ptrActive && ptrArmed) web.reload();
                        ptrActive = false;
                        hidePtr();
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        ptrActive = false;
                        hidePtr();
                        break;
                }
                return false;
            }
        });
    }

    private void hidePtr() {
        if (ptrView != null) ptrView.setVisibility(View.GONE);
        ptrArmed = false;
    }

    private void setupCutout() {
        try {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.getClass().getField("layoutInDisplayCutoutMode").setInt(lp, Build.VERSION.SDK_INT >= 30 ? 3 : 1);
            getWindow().setAttributes(lp);
        } catch (Throwable t) { }
    }

    private JSONObject loadConfig() {
        try {
            return readConfig(getAssets());
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static JSONObject readConfig(AssetManager am) throws Exception {
        {
            InputStream is = am.open("forge.json");
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = is.read(buf)) >= 0) bo.write(buf, 0, r);
            is.close();
            return new JSONObject(new String(bo.toByteArray(), "UTF-8"));
        }
    }

    private static int parseColor(String c) {
        try { return Color.parseColor(c); } catch (Exception e) { return Color.BLACK; }
    }
}
