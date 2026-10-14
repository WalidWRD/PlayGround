package com.apphook.log;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;

/**
 * Feature 5+7: اعتراض الشبكة — OkHttp + HttpURLConnection.
 * الاستراتيجية: Response.body() و RealCall.execute() (مستقرة)،
 * ونتجنب RealInterceptorChain.proceed (هشّ - internal).
 */
public final class NetworkHook {
    private NetworkHook() {}

    public static void install(XposedInterface fw, ClassLoader cl) {
        hookOkHttpBody(fw, cl);
        hookRealCall(fw, cl);
        hookHttpConn(fw, cl);
    }

    private static void hookOkHttpBody(XposedInterface fw, ClassLoader cl) {
        Class<?> c = FingerprintEngine.findClassQuiet(cl, "okhttp3.Response");
        Method m = c != null ? FingerprintEngine.findMethodByFingerprint(
                c, "body", null, 0) : null;
        if (m == null) {
            SafeHook.logSafe("okhttp3.Response.body", "OkHttp absent or renamed");
            return;
        }
        try {
            fw.hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            Object body = chain.proceed();
                            try {
                                if (body != null) {
                                    Method stringM = FingerprintEngine.findMethodByFingerprint(
                                            body.getClass(), "string", String.class, 0);
                                    if (stringM != null) {
                                        // لا نستهلك الـ body الحقيقي (one-shot) — نكتفي بالمراقبة
                                        SafeHook.logNetwork("OkHttp", "response.body()", 200, -1);
                                    }
                                }
                            } catch (Throwable ignored) {}
                            return body;
                        } catch (Throwable t) {
                            SafeHook.logError("OkHttp.body", t);
                            return chain.proceed();
                        }
                    });
            SafeHook.log("Hook OK: okhttp3.Response.body -> " + m);
        } catch (Throwable t) {
            SafeHook.logError("OkHttp.body", t);
        }
    }

    private static void hookRealCall(XposedInterface fw, ClassLoader cl) {
        Class<?> c = FingerprintEngine.findClassQuiet(cl, "okhttp3.RealCall");
        Method m = c != null ? FingerprintEngine.findMethodByFingerprint(
                c, "execute", null, 0) : null;
        if (m == null) {
            SafeHook.logSafe("okhttp3.RealCall.execute", "absent");
            return;
        }
        try {
            fw.hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            Object resp = chain.proceed();
                            try {
                                Method reqM = FingerprintEngine.findMethodByFingerprint(
                                        resp.getClass(), "request", null, 0);
                                Method codeM = FingerprintEngine.findMethodByFingerprint(
                                        resp.getClass(), "code", int.class, 0);
                                String url = "?";
                                int code = -1;
                                if (reqM != null) {
                                    Object req = reqM.invoke(resp);
                                    if (req != null) {
                                        Method urlM = FingerprintEngine.findMethodByFingerprint(
                                                req.getClass(), "url", null, 0);
                                        if (urlM != null) {
                                            Object u = urlM.invoke(req);
                                            if (u != null) url = String.valueOf(u);
                                        }
                                    }
                                }
                                if (codeM != null) code = (int) codeM.invoke(resp);
                                SafeHook.logNetwork("OkHttp", url, code, -1);
                            } catch (Throwable ignored) {}
                            return resp;
                        } catch (Throwable t) {
                            SafeHook.logError("OkHttp.RealCall", t);
                            return chain.proceed();
                        }
                    });
            SafeHook.log("Hook OK: okhttp3.RealCall.execute -> " + m);
        } catch (Throwable t) {
            SafeHook.logError("OkHttp.RealCall", t);
        }
    }

    private static void hookHttpConn(XposedInterface fw, ClassLoader cl) {
        Class<?> c = FingerprintEngine.findClassQuiet(cl, "java.net.HttpURLConnection");
        if (c == null) {
            try {
                c = Class.forName("java.net.HttpURLConnection");
            } catch (Throwable ignored) {}
        }
        Method m = c != null ? FingerprintEngine.findMethodByFingerprint(
                c, "getResponseCode", int.class, 0) : null;
        if (m == null) {
            SafeHook.logSafe("HttpURLConnection.getResponseCode", "absent");
            return;
        }
        try {
            fw.hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            Object orig = chain.proceed();
                            SafeHook.logNetwork("HttpURLConnection", "getResponseCode",
                                    orig instanceof Integer ? (Integer) orig : -1, -1);
                            return orig; // مراقبة فقط — لا نجبر 200 (يكسر التدفق)
                        } catch (Throwable t) {
                            SafeHook.logError("HttpURLConnection", t);
                            return chain.proceed();
                        }
                    });
            SafeHook.log("Hook OK: HttpURLConnection.getResponseCode");
        } catch (Throwable t) {
            SafeHook.logError("HttpURLConnection", t);
        }
    }
}
