package com.apphook.log;

import android.util.Log;

import io.github.libxposed.api.XposedInterface;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * طبقة الأمان والقياس — تعمل في المسارين (حديث + legacy).
 * كل Hook يعمل بوضع PROTECTIVE + try/catch مستقل + تسجيل موحد من 4 عناصر.
 */
public final class SafeHook {
    private SafeHook() {}

    public static final String TAG = "SA";
    private static final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    private static XposedInterface fw;

    public static void attach(XposedInterface framework) { fw = framework; }

    public static void log(String msg) {
        if (!Config.VERBOSE_LOG) return;
        try {
            if (fw != null) fw.log(Log.DEBUG, TAG, msg);
        } catch (Throwable ignored) {}
        try {
            Log.d(TAG, msg);
        } catch (Throwable ignored) {}
    }

    public static void logError(String where, Throwable t) {
        String m = where + " FAIL (ignored safely)";
        try {
            if (fw != null) fw.log(Log.ERROR, TAG, m, t);
        } catch (Throwable ignored) {}
        try {
            Log.e(TAG, m, t);
        } catch (Throwable ignored) {}
    }

    /**
     * تسجيل استدعاء مع قياس كامل: الفئة + الحدث + عدد الوسائط +
     * الأصلية + المُرقّعة (★ عند التغيير).
     */
    public static void recordCall(String category, String event,
                                  int argCount, Object result,
                                  Object patched) {
        String r = abbrev(String.valueOf(result), 60);
        String p = abbrev(String.valueOf(patched), 40);
        boolean changed = (result != patched);
        if (!changed && result != null) {
            changed = !result.equals(patched);
        }
        log("[" + category + "] " + event + " args=" + argCount
                + " result=" + r + (changed ? " -> " + p + " ★" : ""));
        Analyzer.record(category, event, argCount,
                String.valueOf(result), String.valueOf(patched), changed);
    }

    /** عدّاد ميزة مفتوحة مع كود الحالة (F000-F007) */
    public static void countOpen(String feature, String code) {
        AtomicInteger c = counters.get(feature);
        if (c == null) {
            c = new AtomicInteger(0);
            counters.put(feature, c);
        }
        log("[OPEN-" + code + "] " + feature + " x" + c.incrementAndGet());
        Analyzer.recordOpen(feature, code);
    }

    /** قياس الشبكة (مختصر) */
    public static void logNetwork(String method, String url, int code) {
        log("[Network] " + method + " " + url + " -> " + code);
        Analyzer.record("Net", method + " " + url, 0,
                String.valueOf(code), String.valueOf(code), false);
    }

    /** تخطي آمن مسجل */
    public static void logSafe(String hookPoint, String reason) {
        log("[Safe] " + hookPoint + " skipped: " + reason);
    }

    private static String abbrev(String s, int max) {
        if (s == null) return "null";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
