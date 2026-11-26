# Si activas minifyEnabled true, el puente JS necesita conservar sus métodos
-keepclassmembers class com.dskmusic.shell.Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
