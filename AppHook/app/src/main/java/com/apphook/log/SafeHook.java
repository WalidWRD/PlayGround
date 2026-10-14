package com.apphook.log;

import android.util.Log;

import io.github.libxposed.api.XposedInterface;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * طبقة الأمان والقياس — تمنع أي كراش يكسر التطبيق.
 * كل Hook يعمل بوضع PROTECTIVE + try/catch مستقل + تسجيل شامل.
 */
public final class SafeHook {
    private SafeHook() {}

    public static final String TAG = "AppHook";
    private static final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    private static XposedInterface fw;

    public static void attach(XposedInterface framework) { fw = framework; }

    public static void log(String msg) {
        try { if (fw != null) fw.log(Log.DEBUG, TAG, msg); } catch (Throwable ignored) {}
        try { Log.d(TAG, msg); } catch (Throwable ignored) {}
    }

    public static void logError(String where, Throwable t) {
        String m = where + " FAIL (ignored safely): " + t;
        try { if (fw != null) fw.log(Log.ERROR, TAG, m, t); } catch (Throwable ignored) {}
        try { Log.e(TAG, m, t); } catch (Throwable ignored) {}
    }

    /** قياس: استدعاءات الفوترة — اسم الحدث + عدد الوسائط + رمز النتيجة */
    public static void logBilling(String event, int argCount, Object result) {
        log("[Billing] " + event + " args=" + argCount + " result=" + result);
    }

    /** قياس: الميزات المدفوعة المفتوحة */
    public static void countFeature(String feature) {
        AtomicInteger c = counters.get(feature);
        if (c == null) {
            c = new AtomicInteger(0);
            counters.put(feature, c);
        }
        log("[Feature] " + feature + " unlocked: " + c.incrementAndGet() + " times");
    }

    /** قياس: حالة الشبكة */
    public static void logNetwork(String method, String url, int code, long size) {
        log("[Network] " + method + " " + url + " -> " + code + " (" + size + "B)");
    }

    /** قياس: قنوات Flutter — القناة + الدالة + أنواع الوسائط/النتيجة (مختصر) */
    public static void logChannel(String channel, String method, Object[] args, Object result) {
        StringBuilder sb = new StringBuilder("[Flutter] ");
        sb.append(channel).append(".").append(method).append("(");
        if (args != null) {
            for (int i = 0; i < Math.min(args.length, 3); i++) {
                if (i > 0) sb.append(", ");
                Object a = args[i];
                sb.append(a == null ? "null" : a.getClass().getSimpleName());
            }
        }
        sb.append(") -> ");
        sb.append(result == null ? "null" : result.getClass().getSimpleName());
        log(sb.toString());
    }

    /** قياس: عزل الكراش */
    public static void logSafe(String hookPoint, String reason) {
        log("[Safe] " + hookPoint + " skipped: " + reason);
    }
}
