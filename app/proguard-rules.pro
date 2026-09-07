# Arkforge Faith uses @JavascriptInterface methods called by the bundled UI.
-keepclassmembers class com.arkforge.faith.NativeBridge {
    @android.webkit.JavascriptInterface <methods>;
}
