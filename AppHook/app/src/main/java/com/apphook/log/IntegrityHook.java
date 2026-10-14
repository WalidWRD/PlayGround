package com.apphook.log;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;

/**
 * Feature 6: Play Integrity — تحييد كشف التعديل.
 * نعترض requestIntegrityToken ونراقب StandardIntegrityManager.
 * ملاحظة: نُرجع المسار الأصلي عند الفشل حتى لا نكسر التطبيق.
 */
public final class IntegrityHook {
    private IntegrityHook() {}

    public static void install(XposedInterface fw, ClassLoader cl) {
        hookRequestToken(fw, cl);
        watchStandard(fw, cl);
    }

    private static void hookRequestToken(XposedInterface fw, ClassLoader cl) {
        Class<?> c = FingerprintEngine.findClassQuiet(cl,
                "com.google.android.play.core.integrity.IntegrityManager");
        if (c == null) {
            SafeHook.logSafe("IntegrityManager.requestIntegrityToken", "Play Integrity absent");
            return;
        }
        Method target = null;
        try {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().toLowerCase().contains("integritytoken")) {
                    m.setAccessible(true);
                    target = m;
                    break;
                }
            }
        } catch (Throwable t) {
            SafeHook.logError("Integrity.scan", t);
        }
        if (target == null) {
            SafeHook.logSafe("IntegrityManager.requestIntegrityToken", "signature differs");
            return;
        }
        final Method tm = target;
        try {
            fw.hook(tm)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        SafeHook.log("[Integrity] requestIntegrityToken intercepted");
                        try {
                            return chain.proceed(); // مراقبة افتراضياً — التعديل العدواني يكسر التطبيق
                        } catch (Throwable t) {
                            SafeHook.logError("Integrity", t);
                            throw t;
                        }
                    });
            SafeHook.log("Hook OK: IntegrityManager.requestIntegrityToken -> " + tm);
        } catch (Throwable t) {
            SafeHook.logError("Integrity", t);
        }
    }

    private static void watchStandard(XposedInterface fw, ClassLoader cl) {
        Class<?> c = FingerprintEngine.findClassQuiet(cl,
                "com.google.android.play.core.integrity.StandardIntegrityManager");
        if (c == null) {
            SafeHook.logSafe("StandardIntegrityManager", "absent");
            return;
        }
        SafeHook.log("StandardIntegrityManager present — monitoring only");
    }
}
