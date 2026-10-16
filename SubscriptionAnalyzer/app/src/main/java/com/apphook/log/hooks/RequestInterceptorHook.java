package com.apphook.log.hooks;

import com.apphook.log.FingerprintEngine;
import com.apphook.log.ReflectCall;
import com.apphook.log.SafeHook;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;

/**
 * اعتراض الطلبات الصادرة عبر OkHttp (تسجيل + عناوين + Body).
 * مُكيَّف لـ API 102: HookKit + FingerprintEngine + ReflectCall
 * (لا XposedHelpers من libxposed — غير موجودة).
 */
public final class RequestInterceptorHook {
    private RequestInterceptorHook() {}

    private static final String TAG = "Net.Req";

    public static void install(XposedInterface fw, ClassLoader cl) {
        Class<?> chain = FingerprintEngine.findClassQuiet(cl,
                "okhttp3.internal.http.RealInterceptorChain");
        if (chain == null) {
            SafeHook.logSafe(TAG, "OkHttp absent");
            return;
        }
        try {
            boolean any = false;
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
                                try {
                                    Object request = c2.getArg(0);
                                    if (request == null) return c2.proceed();
                                    Object urlObj = ReflectCall.call(request, "url");
                                    String url = String.valueOf(urlObj);
                                    if (url.matches(".*\\.(css|js|png|jpg|jpeg|gif|woff|woff2|svg|ico).*")) {
                                        return c2.proceed();
                                    }
                                    Object methodObj = ReflectCall.call(request, "method");
                                    String method = String.valueOf(methodObj);
                                    String auth = null, ct = null;
                                    try {
                                        Object headers = ReflectCall.call(request, "headers");
                                        if (headers != null) {
                                            Object a = ReflectCall.call(headers, "get", "Authorization");
                                            Object c = ReflectCall.call(headers, "get", "Content-Type");
                                            if (a != null) auth = String.valueOf(a);
                                            if (c != null) ct = String.valueOf(c);
                                        }
                                    } catch (Throwable ignored) {}
                                    String bodyText = null;
                                    try {
                                        Object body = ReflectCall.call(request, "body");
                                        if (body != null) {
                                            Class<?> bufferCls = Class.forName("okio.Buffer", false, cl);
                                            Object buffer = bufferCls.newInstance();
                                            ReflectCall.call(body, "writeTo", buffer);
                                            Object txt = ReflectCall.call(buffer, "readUtf8");
                                            if (txt != null) bodyText = String.valueOf(txt);
                                        }
                                    } catch (Throwable ignored) {}
                                    SafeHook.recordCall(TAG, method + " " + url,
                                            bodyText != null ? 1 : 0,
                                            bodyText != null ? bodyText : "(no body)",
                                            bodyText != null ? bodyText : "(no body)");
                                    if (auth != null && !auth.equals("null") && auth.length() > 4) {
                                        SafeHook.log(TAG + "  auth: "
                                                + auth.substring(0, Math.min(40, auth.length())));
                                    }
                                    if (ct != null && !ct.equals("null")) {
                                        SafeHook.log(TAG + "  ct: " + ct);
                                    }
                                } catch (Throwable t) {
                                    SafeHook.logError(TAG, t);
                                }
                                return c2.proceed();
                            });
                    SafeHook.log("Hook OK: Net.Req.proceed -> " + m);
                    any = true;
                    break;
                } catch (Throwable t) {
                    SafeHook.logError(TAG, t);
                }
            }
            if (!any) SafeHook.logSafe(TAG + ".proceed", "signature differs");
            else SafeHook.log(TAG + ": OkHttp request interceptor installed");
        } catch (Throwable t) {
            SafeHook.logError(TAG + "-scan", t);
        }
    }
}
