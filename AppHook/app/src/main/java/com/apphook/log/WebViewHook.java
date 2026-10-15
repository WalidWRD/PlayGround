package com.apphook.log;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * v4.2 — تعديل الاستجابات فعلياً (إصلاح regex v4.1).
 *
 * <p>سبب فشل v4.1 في رؤية الجسم: الشرط {@code /genspark\.ai/i} لا يطابق
 * URLs نسبية ({@code /api/user}) — فشل الشرط الأول → لم يُنفَّذ
 * {@code c.text()}. الحل: قائمة {@code SENSITIVE} على المسار فقط
 * ({@code /api/(user|payment/current_subscriptions|quota/plan_usage|...)})
 * + طباعة {@code ORIG[...]} (1500 حرف) + تعديل فعلي:</p>
 * <ul>
 *   <li>fetch → {@code new Response(patched, ...)} عند الاختلاف.</li>
 *   <li>XHR → {@code defineProperty(responseText/response)}.</li>
 *   <li>Bridge → {@code patchJson} قبل {@code postMessage}.</li>
 *   <li>25+ قاعدة JSON (اشتراك/خطة/حالة/trial/مصفوفات فارغة).</li>
 * </ul>
 *
 * <p>تكييف API 102: {@link FlutterChannelHook#hookAll} + {@link ReflectCall} +
 * {@link FingerprintEngine}. PROTECTIVE، بلا Proxy لوسائط أصلية، بلا استبدال
 * وسائط (الـ Proxy الوحيد هو callback الحقن ويُمرر فقط).</p>
 */
public final class WebViewHook {
    private WebViewHook() {}

    private static final String TAG = "AppHook.WebView";

    static final Set<String> capturedBridges =
            Collections.synchronizedSet(new HashSet<String>());
    static final Set<String> injectedPages =
            Collections.synchronizedSet(new HashSet<String>());
    static final Set<String> interceptedUrls =
            Collections.synchronizedSet(new HashSet<String>());

    // ═══ سكربت v4.2 — يعدل الاستجابات فعلياً ═══
    private static final String PATCH_JS =
            "(function(){"
            + "if(window.__apphook_v42)return;"
            + "window.__apphook_v42=true;"
            + "console.log('[AppHook] v4.2 loaded at '+location.href);"
            + "var SENSITIVE=/\\/api\\/(user|payment\\/current_subscriptions|quota\\/plan_usage|payment\\/get_credit_balance|payment\\/sub2\\/tier_config|payment\\/sub2\\/pending_schedule)/i;"
            + "function patchJson(t){"
            + "if(typeof t!=='string'||t.length===0)return t;var p=t;"
            + "p=p.replace(/\"is_subscribed\":\\s*false/gi,'\"is_subscribed\":true');"
            + "p=p.replace(/\"isSubscribed\":\\s*false/gi,'\"isSubscribed\":true');"
            + "p=p.replace(/\"is_premium\":\\s*false/gi,'\"is_premium\":true');"
            + "p=p.replace(/\"isPremium\":\\s*false/gi,'\"isPremium\":true');"
            + "p=p.replace(/\"is_vip\":\\s*false/gi,'\"is_vip\":true');"
            + "p=p.replace(/\"isVip\":\\s*false/gi,'\"isVip\":true');"
            + "p=p.replace(/\"premium\":\\s*false/gi,'\"premium\":true');"
            + "p=p.replace(/\"vip\":\\s*false/gi,'\"vip\":true');"
            + "p=p.replace(/\"plan\":\\s*\"free\"/gi,'\"plan\":\"pro\"');"
            + "p=p.replace(/\"plan_type\":\\s*\"free\"/gi,'\"plan_type\":\"pro\"');"
            + "p=p.replace(/\"planType\":\\s*\"free\"/gi,'\"planType\":\"pro\"');"
            + "p=p.replace(/\"tier\":\\s*\"free\"/gi,'\"tier\":\"pro\"');"
            + "p=p.replace(/\"user_type\":\\s*\"free\"/gi,'\"user_type\":\"pro\"');"
            + "p=p.replace(/\"userType\":\\s*\"free\"/gi,'\"userType\":\"pro\"');"
            + "p=p.replace(/\"subscription_tier\":\\s*\"free\"/gi,'\"subscription_tier\":\"pro\"');"
            + "p=p.replace(/\"subscriptionTier\":\\s*\"free\"/gi,'\"subscriptionTier\":\"pro\"');"
            + "p=p.replace(/\"status\":\\s*\"free\"/gi,'\"status\":\"active\"');"
            + "p=p.replace(/\"subscription_status\":\\s*\"none\"/gi,'\"subscription_status\":\"active\"');"
            + "p=p.replace(/\"subscriptionStatus\":\\s*\"none\"/gi,'\"subscriptionStatus\":\"active\"');"
            + "p=p.replace(/\"is_trial\":\\s*true/gi,'\"is_trial\":false');"
            + "p=p.replace(/\"isTrial\":\\s*true/gi,'\"isTrial\":false');"
            + "p=p.replace(/\"subscription\":\\s*null/gi,'\"subscription\":{\"active\":true,\"tier\":\"pro\"}');"
            + "p=p.replace(/\"subscriptions\":\\s*\\[\\]/gi,'\"subscriptions\":[{\"active\":true,\"tier\":\"pro\"}]');"
            + "p=p.replace(/\"data\":\\s*\\[\\]/g,'\"data\":[{\"active\":true,\"tier\":\"pro\"}]');"
            + "return p;}"
            + "if(window.fetch){var _f=window.fetch;"
            + "window.fetch=function(){"
            + "var u=(typeof arguments[0]==='string')?arguments[0]:(arguments[0]&&arguments[0].url)||'';"
            + "var sensitive=SENSITIVE.test(u);"
            + "if(sensitive)console.log('[AppHook] fetch* '+u);"
            + "return _f.apply(this,arguments).then(function(r){"
            + "if(!sensitive)return r;"
            + "try{var c=r.clone();"
            + "return c.text().then(function(t){"
            + "console.log('[AppHook] ORIG['+u+'] = '+t.substring(0,1500));"
            + "var patched=patchJson(t);"
            + "if(patched!==t){console.log('[AppHook] *PATCHED['+u+']');"
            + "return new Response(patched,{status:r.status,statusText:r.statusText,headers:r.headers});}"
            + "return r;}).catch(function(e){console.log('[AppHook] readErr '+e);return r;});"
            + "}catch(e){console.log('[AppHook] err '+e);return r;}});};}"
            + "var _XO=window.XMLHttpRequest;"
            + "if(_XO){var _op=_XO.prototype.open,_sd=_XO.prototype.send;"
            + "_XO.prototype.open=function(m,u){this.__au=u;return _op.apply(this,arguments);};"
            + "_XO.prototype.send=function(){var self=this;"
            + "var sensitive=SENSITIVE.test(self.__au||'');"
            + "if(sensitive){self.addEventListener('readystatechange',function(){"
            + "if(self.readyState===4){try{"
            + "console.log('[AppHook] XHR*['+self.__au+'] status='+self.status);"
            + "console.log('[AppHook] XHR ORIG = '+String(self.responseText).substring(0,1500));"
            + "var p=patchJson(self.responseText);"
            + "if(p!==self.responseText){console.log('[AppHook] *XHR PATCHED');"
            + "Object.defineProperty(self,'responseText',{value:p,configurable:true});"
            + "Object.defineProperty(self,'response',{value:p,configurable:true});}"
            + "}catch(e){}}});}"
            + "return _sd.apply(this,arguments);};" + "}"
            + "function hookBridge(){"
            + "if(!window.FlutterBridge||window.FlutterBridge.__v42)return false;"
            + "try{window.FlutterBridge.__v42=true;"
            + "var _p=window.FlutterBridge.postMessage;"
            + "window.FlutterBridge.postMessage=function(m){"
            + "try{var s=String(m);"
            + "console.log('[AppHook] bridge-> '+s.substring(0,800));"
            + "var p=patchJson(s);"
            + "if(p!==s){console.log('[AppHook] *bridge PATCHED');return _p.call(this,p);}"
            + "return _p.call(this,s);}catch(e){return _p.apply(this,arguments);}};"
            + "return true;}catch(e){return false;}}"
            + "hookBridge();"
            + "var _iv=setInterval(function(){if(hookBridge())clearInterval(_iv);},50);"
            + "})();";

    public static void install(XposedInterface fw, ClassLoader cl) {
        hookAddJsInterface(fw, cl);
        hookOnPageStarted(fw, cl);
        hookOnPageFinished(fw, cl);
        hookShouldInterceptRequest(fw, cl);
        hookConsoleMessage(fw, cl);
        hookEvaluateJavascript(fw, cl);
        SafeHook.log(TAG + ": v4.2 layers installed (real body patch)");
    }

    private static Class<?> sysClass(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void hookOnPageStarted(XposedInterface fw, ClassLoader cl) {
        Class<?> client = sysClass("android.webkit.WebViewClient");
        if (client == null) {
            client = FingerprintEngine.findClassQuiet(cl, "android.webkit.WebViewClient");
        }
        if (client == null) {
            SafeHook.logSafe(TAG + ".onPageStarted", "WebViewClient absent");
            return;
        }
        FlutterChannelHook.hookAll(fw, client, "onPageStarted", 3, 3,
                "WebView.onPageStarted", chain -> {
                    try {
                        Object url = chain.getArg(1);
                        if (!(url instanceof String)) return chain.proceed();
                        if (!((String) url).contains("genspark.ai")) return chain.proceed();
                        SafeHook.log(TAG + ": onPageStarted: " + capped((String) url, 220));
                        Object view = chain.getArg(0);
                        if (view != null) inject(view, "started");
                    } catch (Throwable t) {
                        SafeHook.logError(TAG + ".pageStarted", t);
                    }
                    return chain.proceed();
                });
    }

    private static void hookOnPageFinished(XposedInterface fw, ClassLoader cl) {
        Class<?> client = sysClass("android.webkit.WebViewClient");
        if (client == null) {
            client = FingerprintEngine.findClassQuiet(cl, "android.webkit.WebViewClient");
        }
        if (client == null) {
            SafeHook.logSafe(TAG + ".onPageFinished", "WebViewClient absent");
            return;
        }
        FlutterChannelHook.hookAll(fw, client, "onPageFinished", 2, 2,
                "WebView.onPageFinished", chain -> {
                    try {
                        Object url = chain.getArg(1);
                        if (!(url instanceof String)) return chain.proceed();
                        if (!((String) url).contains("genspark.ai")) return chain.proceed();
                        if (!injectedPages.add((String) url)) return chain.proceed();
                        SafeHook.log(TAG + ": onPageFinished: " + capped((String) url, 220));
                        Object view = chain.getArg(0);
                        if (view != null) inject(view, "finished");
                    } catch (Throwable t) {
                        SafeHook.logError(TAG + ".pageFinished", t);
                    }
                    return chain.proceed();
                });
    }

    static void injectPatchScript(Object view) {
        inject(view, "legacy");
    }

    static void inject(Object view, String phase) {
        if (view == null) return;
        try {
            SafeHook.log(TAG + ": injecting JS (" + phase + ") len=" + PATCH_JS.length());
            Object cb = null;
            try {
                Class<?> vcb = null;
                try {
                    vcb = Class.forName("android.webkit.ValueCallback");
                } catch (Throwable ignored) {}
                if (vcb != null) {
                    final String ph = phase;
                    cb = java.lang.reflect.Proxy.newProxyInstance(
                            vcb.getClassLoader() != null ? vcb.getClassLoader()
                                    : view.getClass().getClassLoader(),
                            new Class<?>[]{vcb},
                            (proxy, m, a) -> {
                                try {
                                    SafeHook.log(TAG + ": JS injected (" + ph + ") -> "
                                            + capped(String.valueOf(a != null && a.length > 0
                                                    ? a[0] : null), 120));
                                } catch (Throwable ignored) {}
                                return null;
                            });
                }
            } catch (Throwable ignored) {
                cb = null;
            }
            if (cb != null) {
                ReflectCall.call(view, "evaluateJavascript", PATCH_JS, cb);
            } else {
                ReflectCall.call(view, "evaluateJavascript", PATCH_JS, new Object[]{null});
            }
            SafeHook.log(TAG + ": evaluateJavascript called (" + phase + ")");
        } catch (Throwable t) {
            SafeHook.logError(TAG + ".inject-" + phase, t);
        }
    }

    private static void hookShouldInterceptRequest(XposedInterface fw, ClassLoader cl) {
        Class<?> client = sysClass("android.webkit.WebViewClient");
        if (client == null) {
            client = FingerprintEngine.findClassQuiet(cl, "android.webkit.WebViewClient");
        }
        if (client == null) {
            SafeHook.logSafe(TAG + ".shouldInterceptRequest", "WebViewClient absent");
            return;
        }
        FlutterChannelHook.hookAll(fw, client, "shouldInterceptRequest", 2, 2,
                "WebView.shouldInterceptRequest", chain -> {
                    try {
                        String url = extractUrl(chain.getArg(1));
                        if (url != null && url.contains("genspark.ai")
                                && interceptedUrls.add(url)) {
                            SafeHook.logNetwork("WebView", capped(url, 220), -1, -1);
                        }
                    } catch (Throwable t) {
                        SafeHook.logError(TAG + ".intercept", t);
                    }
                    return chain.proceed();
                });
    }

    private static String extractUrl(Object req) {
        if (req == null) return null;
        if (req instanceof String) return (String) req;
        Object uri = ReflectCall.call(req, "getUrl");
        return uri != null ? String.valueOf(uri) : null;
    }

    private static void hookConsoleMessage(XposedInterface fw, ClassLoader cl) {
        Class<?> chrome = sysClass("android.webkit.WebChromeClient");
        if (chrome == null) {
            chrome = FingerprintEngine.findClassQuiet(cl, "android.webkit.WebChromeClient");
        }
        if (chrome == null) {
            SafeHook.logSafe(TAG + ".onConsoleMessage", "WebChromeClient absent");
            return;
        }
        FlutterChannelHook.hookAll(fw, chrome, "onConsoleMessage", 1, 1,
                "WebView.onConsoleMessage", chain -> {
                    try {
                        Object msg = chain.getArg(0);
                        String text = null;
                        String level = "?";
                        if (msg != null) {
                            Object mm = ReflectCall.call(msg, "message");
                            text = mm != null ? String.valueOf(mm) : String.valueOf(msg);
                            Object lv = ReflectCall.call(msg, "messageLevel");
                            if (lv != null) level = String.valueOf(lv);
                        }
                        SafeHook.log(TAG + "-JS[" + level + "]: " + capped(text, 1500));
                    } catch (Throwable t) {
                        SafeHook.logError(TAG + ".console", t);
                    }
                    return chain.proceed();
                });
    }

    private static void hookEvaluateJavascript(XposedInterface fw, ClassLoader cl) {
        Class<?> wv = sysClass("android.webkit.WebView");
        if (wv == null) wv = FingerprintEngine.findClassQuiet(cl, "android.webkit.WebView");
        if (wv == null) return;
        FlutterChannelHook.hookAll(fw, wv, "evaluateJavascript", 1, 2,
                "WebView.evaluateJavascript", chain -> {
                    try {
                        Object js = chain.getArg(0);
                        if (js instanceof String) {
                            String s = (String) js;
                            SafeHook.log(TAG + ": evalJS "
                                    + capped(s.length() > 120 ? s.substring(0, 120) : s, 130));
                        }
                    } catch (Throwable t) {
                        SafeHook.logError(TAG + ".evalJS", t);
                    }
                    return chain.proceed();
                });
    }

    private static void hookAddJsInterface(XposedInterface fw, ClassLoader cl) {
        Class<?> wv = sysClass("android.webkit.WebView");
        if (wv == null) wv = FingerprintEngine.findClassQuiet(cl, "android.webkit.WebView");
        if (wv == null) {
            SafeHook.logSafe(TAG + ".addJavascriptInterface", "WebView absent");
            return;
        }
        FlutterChannelHook.hookAll(fw, wv, "addJavascriptInterface", 2, 2,
                "WebView.addJavascriptInterface", chain -> {
                    try {
                        Object obj = chain.getArg(0);
                        Object name = chain.getArg(1);
                        SafeHook.log(TAG + ": addJavascriptInterface(" + name + ")");
                        if (obj != null) hookBridge(fw, obj.getClass());
                    } catch (Throwable t) {
                        SafeHook.logError(TAG + ".addJS", t);
                    }
                    return chain.proceed();
                });
    }

    static void hookBridgeObject(XposedInterface fw, Class<?> bridgeClass, String name) {
        hookBridge(fw, bridgeClass);
    }

    static void hookBridge(XposedInterface fw, Class<?> cls) {
        if (cls == null || ProxyMarker.isProxy(cls)) return;
        if (!capturedBridges.add(cls.getName())) return;
        SafeHook.log(TAG + ": hooking bridge: " + cls.getName());
        try {
            for (Method m : cls.getDeclaredMethods()) {
                SafeHook.log(TAG + "   method: " + m.getName()
                        + java.util.Arrays.toString(m.getParameterTypes()));
            }
        } catch (Throwable ignored) {}
        FlutterChannelHook.hookAll(fw, cls, "postMessage", 1, -1,
                "WebView.postMessage", chain -> {
                    try {
                        SafeHook.log(TAG + " * postMessage");
                        for (Object a : chain.getArgs()) {
                            SafeHook.log(TAG + "   arg = " + capped(String.valueOf(a), 800));
                        }
                    } catch (Throwable t) {
                        SafeHook.logError(TAG + ".postMessage", t);
                    }
                    return chain.proceed();
                });
    }

    private static String capped(String s, int n) {
        if (s == null) return "null";
        return s.length() > n ? s.substring(0, n) + "..." : s;
    }

    static boolean isPayUrl(String url) {
        if (url == null) return false;
        String l = url.toLowerCase();
        return l.contains("subscri") || l.contains("premium") || l.contains("billing")
                || l.contains("pay") || l.contains("checkout") || l.contains("plan")
                || l.contains("upgrade") || l.contains("purchase");
    }

    static boolean isApiUrl(String url) {
        if (url == null) return false;
        String l = url.toLowerCase();
        return l.contains("genspark.ai/api") || l.contains("/api/subscription")
                || l.contains("/api/user") || l.contains("/api/billing")
                || l.contains("/api/plan") || l.contains("/api/account")
                || l.contains("/api/credit");
    }

    static boolean isSubJs(String s) {
        if (s == null) return false;
        String l = s.toLowerCase();
        return l.contains("premium") || l.contains("subscription")
                || l.contains("billing") || l.contains("plan");
    }

    static boolean isSubPayload(String s) {
        if (s == null) return false;
        String l = s.toLowerCase();
        return l.contains("premium") || l.contains("subscri") || l.contains("plan")
                || l.contains("upgrade") || l.contains("billing");
    }
}
