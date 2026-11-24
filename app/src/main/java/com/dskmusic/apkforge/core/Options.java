package com.dskmusic.apkforge.core;

/** Opciones de la app generada (mismas que forge.py). */
public class Options {
    public boolean allowPortrait = true, allowLandscape = false, selectText = false, zoom = false, pullRefresh = false;
    public boolean mobileViewport = true, fullscreen = true, keepAwake = false, cleanGoof = true;
    public String theme = "dark", bg = "";
    public int loadDelay = 700;
    public String orientation() { return allowPortrait == allowLandscape ? "auto" : (allowPortrait ? "portrait" : "landscape"); }
}
