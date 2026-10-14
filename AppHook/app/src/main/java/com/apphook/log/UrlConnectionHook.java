package com.apphook.log;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;

/**
 * v3.1 — جانب Dart HTTP عبر JNI: أحياناً يمر عبر
 * {@code java.net.HttpURLConnection}. مراقبة (تسجيل URL) — بلا تعديل:
 *  - getInputStream: تسجيل الـ URL قبل القراءة.
 *  - getResponseCode: تسجيل الكود (يغطي فجوة HttpHook القديم).
 */
public final class UrlConnectionHook {
    private UrlConnectionHook() {}

    private static final String TAG = "AppHook.UrlConn";

    public static void install(XposedInterface fw, ClassLoader cl) {
        Class<?> c = FingerprintEngine.findClassQuiet(cl, "java.net.HttpURLConnection");
        if (c == null) {
            try {
                c = Class.forName("java.net.HttpURLConnection");
            } catch (Throwable ignored) {}
        }
        if (c == null) {
            SafeHook.logSafe(TAG, "HttpURLConnection absent");
            return;
        }
        hookIn(fw, c);
        hookCode(fw, c);
    }

    private static void hookIn(XposedInterface fw, Class<?> c) {
        Method m = FingerprintEngine.findMethodByFingerprint(c, "getInputStream", null, 0);
        if (m == null) {
            SafeHook.logSafe(TAG + ".getInputStream", "method-not-found");
            return;
        }
        final Method target = m;
        try {
            fw.hook(target)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            Object url = ReflectCall.call(chain.getThisObject(), "getURL");
                            SafeHook.logNetwork("HttpURLConnection",
                                    url != null ? String.valueOf(url) : "?", -1, -1);
                        } catch (Throwable t) {
                            SafeHook.logError(TAG + ".getInputStream", t);
                        }
                        return chain.proceed();
                    });
            SafeHook.log("Hook OK: HttpURLConnection.getInputStream -> " + m);
        } catch (Throwable t) {
            SafeHook.logError(TAG + ".getInputStream", t);
        }
    }

    private static void hookCode(XposedInterface fw, Class<?> c) {
        Method m = FingerprintEngine.findMethodByFingerprint(c, "getResponseCode",
                int.class, 0);
        if (m == null) {
            SafeHook.logSafe(TAG + ".getResponseCode", "method-not-found");
            return;
        }
        final Method target = m;
        try {
            fw.hook(target)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            Object orig = chain.proceed();
                            Object url = ReflectCall.call(chain.getThisObject(), "getURL");
                            SafeHook.logNetwork("HttpURLConnection",
                                    url != null ? String.valueOf(url) : "?",
                                    orig instanceof Integer ? (Integer) orig : -1, -1);
                            return orig;
                        } catch (Throwable t) {
                            SafeHook.logError(TAG + ".getResponseCode", t);
                            return chain.proceed();
                        }
                    });
            SafeHook.log("Hook OK: HttpURLConnection.getResponseCode -> " + m);
        } catch (Throwable t) {
            SafeHook.logError(TAG + ".getResponseCode", t);
        }
    }
}
