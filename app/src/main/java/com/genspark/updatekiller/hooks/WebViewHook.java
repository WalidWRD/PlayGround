package com.genspark.updatekiller.hooks;

import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UxLog;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;

/**
 * جديد 1.2.0 — منع فتح صفحة متجر Play من داخل WebView (متصفح داخلي للتحديث).
 * يعمل بطريقتين مدОтّلة للكراش:
 *  1) beforeHookedMethod على WebViewClient.shouldOverrideUrlLoading (يُعيد boolean)
 *     → نُحوّل رابط المتجر إلى رابط آمن ونُعيد false (دع الوِب‌فيو يفتحه عاديًا).
 *  2) WebView.loadUrl(String) → نُعدّل وسيط السلسلة نفسه (لا setResult إطلاقًا).
 */
public final class WebViewHook {

    private WebViewHook() { }

    public static int install(ClassLoader cl) {
        if (!Config.get().blockWebViewStore) { UxLog.i("WebViewHook: disabled by config"); return 0; }
        int n = 0;

        Class<?> wvc = Reflect.findClass("android.webkit.WebViewClient", cl);
        if (wvc != null) {
            n += Reflect.hookBySignature(wvc, new String[]{"shouldOverrideUrlLoading"},
                    new String[]{"android.webkit.WebView", "java.lang.String"}, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Guard.run("WebViewHook.sOUL(String)", () -> {
                        if (p.args != null && p.args.length >= 2 && p.args[1] instanceof String) {
                            String url = (String) p.args[1];
                            if (isStoreUrl(url)) {
                                p.args[1] = Config.get().storeRedirectUrl;
                                p.setResult(Boolean.FALSE);
                                UxLog.i("WebViewHook: store url redirected → " + Config.get().storeRedirectUrl + " (was: " + url + ")");
                            }
                        }
                    });
                }
            });
            n += Reflect.hookBySignature(wvc, new String[]{"shouldOverrideUrlLoading"},
                    new String[]{"android.webkit.WebView", "android.webkit.WebResourceRequest"}, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Guard.run("WebViewHook.sOUL(req)", () -> {
                        if (p.args == null || p.args.length < 2 || p.args[1] == null) return;
                        Object req = p.args[1];
                        Object u = Reflect.getField(req, "url");
                        String url = (u != null) ? u.toString() : null;
                        if (isStoreUrl(url)) {
                            // لا يمكن تعديل WebResourceRequest (صنف غير قابل للتغيير) → نلغي التحميل
                            p.setResult(Boolean.TRUE);
                            UxLog.i("WebViewHook: blocked store request → " + url);
                        }
                    });
                }
            });
        }

        Class<?> wv = Reflect.findClass("android.webkit.WebView", cl);
        if (wv != null) {
            n += Reflect.hookBySignature(wv, new String[]{"loadUrl"},
                    new String[]{"java.lang.String"}, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Guard.run("WebViewHook.loadUrl", () -> {
                        if (p.args != null && p.args.length >= 1 && p.args[0] instanceof String) {
                            String url = (String) p.args[0];
                            if (isStoreUrl(url)) {
                                p.args[0] = Config.get().storeRedirectUrl;
                                UxLog.i("WebViewHook: loadUrl(store) redirected → " + Config.get().storeRedirectUrl);
                            }
                        }
                    });
                }
            });
        }

        UxLog.i("WebViewHook: " + n + " hook(s)");
        return n;
    }

    static boolean isStoreUrl(String url) {
        if (url == null) return false;
        try {
            if (url.contains("market://")) return true;
            if (url.contains("play.google.com/store")) return true;
            if (url.contains("apps/details?id=")) return true;
            if (url.startsWith("market")) return true;
            return false;
        } catch (Throwable t) {
            return false;
        }
    }
}
