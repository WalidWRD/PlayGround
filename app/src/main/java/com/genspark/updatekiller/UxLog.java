package com.genspark.updatekiller;

import de.robv.android.xposed.XposedBridge;

/** تسجيل آمن (لا يرمي أبدًا) مع بادئة موحّدة تظهر في logcat. */
public final class UxLog {

    public static final String TAG = "GensparkUpdateKiller";

    private UxLog() { }

    private static void out(String level, String msg) {
        try {
            boolean verbose = Config.get().verbose;
            if ("D".equals(level) && !verbose) return;
            XposedBridge.log(TAG + " [" + level + "] v" + BuildInfo.VERSION_NAME + " " + msg);
        } catch (Throwable ignored) { }
    }

    public static void d(String m) { out("D", m); }
    public static void i(String m) { out("I", m); }
    public static void w(String m) { out("W", m); }
    public static void e(String m) { out("E", m); }
}
