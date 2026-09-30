package com.genspark.updatekiller;

import de.robv.android.xposed.XposedBridge;

/* loaded from: classes.dex */
public final class UxLog {
    public static final String TAG = "GensparkUpdateKiller";

    private UxLog() {
    }

    private static void out(String str, String str2) {
        try {
            if (!"D".equals(str) || Config.get().verbose) {
                XposedBridge.log("GensparkUpdateKiller [" + str + "] v2.0.3 " + str2);
            }
        } catch (Throwable unused) {
        }
    }

    public static void d(String str) {
        out("D", str);
    }

    public static void i(String str) {
        out("I", str);
    }

    public static void w(String str) {
        out("W", str);
    }

    public static void e(String str) {
        out("E", str);
    }
}
