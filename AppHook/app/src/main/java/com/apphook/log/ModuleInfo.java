package com.apphook.log;

import android.util.Log;

import io.github.libxposed.api.XposedInterface;

/**
 * هوية الموديول + الوصف المدمج ().
 * مصدر واحد للحقيقة: يُعرض في مدير الموديولات وسجل التشغيل.
 */
public final class ModuleInfo {
    private ModuleInfo() {}

    public static final String NAME = "AppHook";
    public static final String PACKAGE = "com.apphook.log";
    public static final String VERSION_NAME = "4.2.0";
    public static final int VERSION_CODE = 420;

    public static final int MIN_SDK = 26;      // Android 8.0
    public static final int TARGET_SDK = 35;   // Android 15
    public static final int LIB_MIN_API = 101;
    public static final int LIB_TARGET_API = 102;

    /** الوصف المدمج (): مختصر جداً — مدير الموديولات + سجل التشغيل */
    public static String describe() {
        return NAME + " v" + VERSION_NAME
                + " | WebView v4.2: fetch+XHR+bridge patch"
                + " | proxy-free | انعكاس+بصمة"
                + " | آمن ضد الكراش"
                + " | LSPatch/NPatch/HKP بلا روت";
    }

    public static void logStartup(XposedInterface fw) {
        try {
            fw.log(Log.INFO, "AppHook", describe());
            fw.log(Log.INFO, "AppHook",
                    "[Features] webview-js-inject, flutterbridge-hook, webapi-monitor, "
                    + "flutter-channel, prefs-override, gson-patch, "
                    + "billing-fallback, packed-app-ready, protective-mode");
        } catch (Throwable ignored) {}
    }
}
