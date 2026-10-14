package com.pro.hook.universal.util;

import android.util.Log;

import de.robv.android.xposed.XposedBridge;

/**
 * لوج موحد: XposedBridge + logcat. مختصر في سجل التشغيل.
 */
public final class LogX {
    private LogX() {}

    public static final String TAG = "UniversalHookPro";

    public static void i(String msg) {
        try { XposedBridge.log("[" + TAG + "] " + msg); } catch (Throwable ignored) {}
        try { Log.i(TAG, msg); } catch (Throwable ignored) {}
    }

    public static void w(String msg) {
        try { XposedBridge.log("[" + TAG + "/WARN] " + msg); } catch (Throwable ignored) {}
        try { Log.w(TAG, msg); } catch (Throwable ignored) {}
    }

    public static void e(String msg) {
        try { XposedBridge.log("[" + TAG + "/ERR] " + msg); } catch (Throwable ignored) {}
        try { Log.e(TAG, msg); } catch (Throwable ignored) {}
    }
}
