package com.apphook.log;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;

/**
 * v3.1 — مسار WebView (flutter_inappwebview): الاشتراك قد يكون صفحة داخل
 * WebView. مراقبة فقط (لا حظر — كسر الصفحة يُفقد التشخيص):
 *  - WebView.addJavascriptInterface(obj, name): تسجيل أسماء الجسور.
 *  - WebViewClient.shouldInterceptRequest: بصيغتيه (String القديمة و
 *    WebResourceRequest API21+) — تسجيل URLs المشتبهة (اشتراك/دفع).
 */
public final class WebViewHook {
    private WebViewHook() {}

    private static final String TAG = "AppHook.WebView";

    public static void install(XposedInterface fw, ClassLoader cl) {
        hookJsBridge(fw);
        hookShouldIntercept(fw, cl);
    }

    private static Class<?> sysClass(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void hookJsBridge(XposedInterface fw) {
        Class<?> wv = sysClass("android.webkit.WebView");
        if (wv == null) {
            SafeHook.logSafe(TAG + ".addJavascriptInterface", "WebView absent");
            return;
        }
        Method m = FingerprintEngine.findMethodByFingerprint(wv,
                "addJavascriptInterface", void.class, 2);
        if (m == null) {
            SafeHook.logSafe(TAG + ".addJavascriptInterface", "method-not-found");
            return;
        }
        final Method target = m;
        try {
            fw.hook(target)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            Object name = chain.getArg(1);
                            SafeHook.log(TAG + ": JS bridge: " + name);
                            Object obj = chain.getArg(0);
                            if (obj != null) {
                                SafeHook.log(TAG + "   iface = "
                                        + obj.getClass().getName());
                            }
                        } catch (Throwable t) {
                            SafeHook.logError(TAG + ".jsBridge", t);
                        }
                        return chain.proceed();
                    });
            SafeHook.log("Hook OK: WebView.addJavascriptInterface -> " + m);
        } catch (Throwable t) {
            SafeHook.logError(TAG + ".jsBridge", t);
        }
    }

    /** نربط الصيغتين معاً (String و WebResourceRequest) — مراقبة فقط. */
    private static void hookShouldIntercept(XposedInterface fw, ClassLoader cl) {
        Class<?> client = sysClass("android.webkit.WebViewClient");
        if (client == null) {
            client = FingerprintEngine.findClassQuiet(cl,
                    "android.webkit.WebViewClient");
        }
        if (client == null) {
            SafeHook.logSafe(TAG + ".shouldInterceptRequest", "WebViewClient absent");
            return;
        }
        try {
            boolean any = false;
            for (Method m : client.getDeclaredMethods()) {
                if (!m.getName().equals("shouldInterceptRequest")) continue;
                if (m.getParameterTypes().length != 2) continue;
                try {
                    m.setAccessible(true);
                    final Method target = m;
                    fw.hook(target)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                try {
                                    String url = extractUrl(chain.getArg(1));
                                    if (url != null && isPayUrl(url)) {
                                        SafeHook.log(TAG + ": WebView REQ: " + url);
                                        SafeHook.logNetwork("WebView", url, -1, -1);
                                    }
                                } catch (Throwable t) {
                                    SafeHook.logError(TAG + ".intercept", t);
                                }
                                return chain.proceed(); // لا حظر أبداً
                            });
                    SafeHook.log("Hook OK: WebViewClient.shouldInterceptRequest -> " + m);
                    any = true;
                } catch (Throwable t) {
                    SafeHook.logError(TAG + ".intercept", t);
                }
            }
            if (!any) SafeHook.logSafe(TAG + ".shouldInterceptRequest", "no 2-arg overload");
        } catch (Throwable t) {
            SafeHook.logError(TAG + ".intercept-scan", t);
        }
    }

    private static String extractUrl(Object req) {
        if (req == null) return null;
        if (req instanceof String) return (String) req;
        try {
            Object uri = ReflectCall.call(req, "getUrl");
            if (uri != null) return String.valueOf(uri);
        } catch (Throwable ignored) {}
        return null;
    }

    static boolean isPayUrl(String url) {
        if (url == null) return false;
        String l = url.toLowerCase();
        return l.contains("subscri") || l.contains("premium") || l.contains("billing")
                || l.contains("pay") || l.contains("checkout") || l.contains("plan")
                || l.contains("upgrade") || l.contains("purchase");
    }
}
