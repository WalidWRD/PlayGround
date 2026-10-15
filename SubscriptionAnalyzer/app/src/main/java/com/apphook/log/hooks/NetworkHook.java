package com.apphook.log.hooks;

import com.apphook.log.FingerprintEngine;
import com.apphook.log.ReflectCall;
import com.apphook.log.SafeHook;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;

/** Feature #12: Network — تسجيل فقط دائماً (لا تعديل أبداً). */
public final class NetworkHook {
    private NetworkHook() {}

    public static void install(XposedInterface fw, ClassLoader cl) {
        hookOkHttp(fw, cl);
        hookHttpUrlConnection(fw, cl);
        SafeHook.log("Net: hooked (record-only)");
    }

    private static void hookOkHttp(XposedInterface fw, ClassLoader cl) {
        Class<?> chain = FingerprintEngine.findClassQuiet(cl,
                "okhttp3.internal.http.RealInterceptorChain");
        if (chain == null) {
            SafeHook.logSafe("Net.okhttp", "internal chain absent (known P0 risk)");
            return;
        }
        try {
            for (Method m : chain.getDeclaredMethods()) {
                if (!m.getName().equals("proceed") || m.getParameterTypes().length != 1) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                    final Method target = m;
                    fw.hook(target)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(c2 -> {
                                Object resp = null;
                                try {
                                    resp = c2.proceed();
                                } catch (Throwable t) {
                                    SafeHook.logError("Net.okhttp", t);
                                    return c2.proceed();
                                }
                                try {
                                    Object rq = c2.getArg(0);
                                    Object url = rq != null ? ReflectCall.call(rq, "url") : null;
                                    Object code = ReflectCall.call(resp, "code");
                                    SafeHook.recordCall("Net.okhttp", "proceed", 1,
                                            url + " [" + code + "]", url + " [" + code + "]");
                                } catch (Throwable ignored) {}
                                return resp;
                            });
                    SafeHook.log("Hook OK: Net.okhttp.proceed -> " + m);
                    return;
                } catch (Throwable t) {
                    SafeHook.logError("Net.okhttp", t);
                }
            }
            SafeHook.logSafe("Net.okhttp.proceed", "signature differs");
        } catch (Throwable t) {
            SafeHook.logError("Net.okhttp-scan", t);
        }
    }

    private static void hookHttpUrlConnection(XposedInterface fw, ClassLoader cl) {
        Class<?> c = FingerprintEngine.findClassQuiet(cl, "java.net.HttpURLConnection");
        if (c == null) {
            try {
                c = Class.forName("java.net.HttpURLConnection");
            } catch (Throwable ignored) {}
        }
        if (c == null) return;
        Method m = FingerprintEngine.findMethodByFingerprint(c,
                "getResponseCode", int.class, 0);
        HookKit.hookOne(fw, m, "Net.http.getResponseCode", chain -> {
            Object orig = chain.proceed();
            try {
                Object url = ReflectCall.call(chain.getThisObject(), "getURL");
                SafeHook.recordCall("Net.http", "getResponseCode", 0,
                        orig + " @ " + url, orig + " @ " + url);
            } catch (Throwable t) {
                SafeHook.logError("Net.http", t);
            }
            return orig;
        });
    }
}
