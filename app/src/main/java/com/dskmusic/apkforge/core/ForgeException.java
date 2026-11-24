package com.dskmusic.apkforge.core;

public class ForgeException extends Exception {
    public ForgeException(String m) { super(Lang.t(m)); }
    public ForgeException(String m, Throwable c) { super(Lang.t(m), c); }
}
