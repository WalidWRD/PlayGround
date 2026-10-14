package com.apphook.log;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;

/**
 * اعتراض شبكة Native (Retrofit/OkHttp/Cronet).
 *  - okhttp3.Response.body + RealCall.execute: مراقبة (مستقرة).
 *  - RealInterceptorChain.proceed: محاولة اختيارية — internal هشّ (P0 معروف)
 *    الفشل فيه مسجّل [Safe] ولا يوقف بقية الطبقات.
 *  - Cronet (يستخدمه Flutter أحياناً): رصد وجود فقط.
 */
public final class HttpHook {
    private HttpHook() {}

    private static final String TAG = "AppHook.Http";

    public static void install(XposedInterface fw, ClassLoader cl) {
        hookOkHttpBody(fw, cl);
        hookRealCall(fw, cl);
        hookChainOptional(fw, cl);
        watchCronet(cl);
    }

    private static void hookOkHttpBody(XposedInterface fw, ClassLoader cl) {
        Class<?> c = FingerprintEngine.findClassQuiet(cl, "okhttp3.Response");
        Method m = c != null ? FingerprintEngine.findMethodByFingerprint(c, "body", null, 0) : null;
        if (m == null) {
            SafeHook.logSafe(TAG + ".Response.body", "OkHttp absent or renamed");
            return;
        }
        final Method target = m;
        try {
            fw.hook(target)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            Object body = chain.proceed();
                            if (body != null) {
                                String url = "?";
                                try {
                                    Object req = ReflectCall.call(chain.getThisObject(), "request");
                                    Object u = req != null ? ReflectCall.call(req, "url") : null;
                                    if (u != null) url = String.valueOf(u);
                                } catch (Throwable ignored) {}
                                SafeHook.logNetwork("OkHttp", url, 200, -1);
                            }
                            return body;
                        } catch (Throwable t) {
                            SafeHook.logError(TAG + ".body", t);
                            return chain.proceed();
                        }
                    });
            SafeHook.log("Hook OK: " + TAG + ".Response.body -> " + m);
        } catch (Throwable t) {
            SafeHook.logError(TAG + ".body", t);
        }
    }

    private static void hookRealCall(XposedInterface fw, ClassLoader cl) {
        Class<?> c = FingerprintEngine.findClassQuiet(cl, "okhttp3.RealCall");
        Method m = c != null ? FingerprintEngine.findMethodByFingerprint(c, "execute", null, 0) : null;
        if (m == null) {
            SafeHook.logSafe(TAG + ".RealCall.execute", "absent");
            return;
        }
        final Method target = m;
        try {
            fw.hook(target)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            Object resp = chain.proceed();
                            try {
                                Object reqM = ReflectCall.call(resp, "request");
                                Object codeM = ReflectCall.call(resp, "code");
                                String url = "?";
                                int code = -1;
                                if (reqM != null) {
                                    Object u = ReflectCall.call(reqM, "url");
                                    if (u != null) url = String.valueOf(u);
                                }
                                if (codeM instanceof Integer) code = (Integer) codeM;
                                SafeHook.logNetwork("OkHttp", url, code, -1);
                            } catch (Throwable ignored) {}
                            return resp;
                        } catch (Throwable t) {
                            SafeHook.logError(TAG + ".RealCall", t);
                            return chain.proceed();
                        }
                    });
            SafeHook.log("Hook OK: " + TAG + ".RealCall.execute -> " + m);
        } catch (Throwable t) {
            SafeHook.logError(TAG + ".RealCall", t);
        }
    }

    /** اختياري وهشّ — أي فشل = [Safe] ومتابعة. */
    private static void hookChainOptional(XposedInterface fw, ClassLoader cl) {
        Class<?> c = FingerprintEngine.findClassQuiet(cl,
                "okhttp3.internal.http.RealInterceptorChain");
        if (c == null) {
            SafeHook.logSafe(TAG + ".Chain.proceed", "internal absent (P0 known-risk, ignored)");
            return;
        }
        try {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals("proceed") || m.getParameterTypes().length != 1) continue;
                try {
                    m.setAccessible(true);
                    final Method target = m;
                    fw.hook(target)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                try {
                                    Object resp = chain.proceed();
                                    try {
                                        Object arg0 = chain.getArg(0);
                                        Object u = arg0 != null ? ReflectCall.call(arg0, "url") : null;
                                        Object code = ReflectCall.call(resp, "code");
                                        SafeHook.logNetwork("Retrofit",
                                                u != null ? String.valueOf(u) : "?",
                                                code instanceof Integer ? (Integer) code : -1, -1);
                                    } catch (Throwable ignored) {}
                                    return resp;
                                } catch (Throwable t) {
                                    SafeHook.logError(TAG + ".Chain", t);
                                    return chain.proceed();
                                }
                            });
                    SafeHook.log("Hook OK: " + TAG + ".Chain.proceed -> " + m);
                    return;
                } catch (Throwable t) {
                    SafeHook.logError(TAG + ".Chain", t);
                }
            }
            SafeHook.logSafe(TAG + ".Chain.proceed", "signature differs (ignored)");
        } catch (Throwable t) {
            SafeHook.logError(TAG + ".Chain-scan", t);
        }
    }

    private static void watchCronet(ClassLoader cl) {
        Class<?> c = FingerprintEngine.findClassQuiet(cl, "org.chromium.net.UrlRequest");
        if (c == null) {
            SafeHook.logSafe(TAG + ".Cronet", "absent");
            return;
        }
        SafeHook.log(TAG + ": Cronet present (Flutter may use it) — monitoring only");
    }
}
