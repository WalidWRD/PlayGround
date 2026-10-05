package com.ashnapatch.xposed.hooks;

import android.webkit.WebResourceResponse;
import android.webkit.WebViewClient;

import com.ashnapatch.xposed.Config;
import com.ashnapatch.xposed.PrefsManager;
import com.ashnapatch.xposed.util.LogUtil;
import com.ashnapatch.xposed.util.ReflectionHelper;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * حجب الإعلانات بالكامل:
 * 1) اعتراض WebViewClient.shouldInterceptRequest (كلا الحملين) وإرجاع
 *    استجابة فارغة لنطاقات الإعلانات — يعمل مع إعلانات طبقة الويب
 *    (الحالة الفعلية في AshnaAI: لا SDK إعلاني أصلي).
 * 2) اعتراض shouldOverrideUrlLoading لمنع التنقل لنطاقات الإعلانات.
 * 3) تعطيل loadAd/showAd بالانعكاس إن وُجدت كلاسات إعلانية
 *    (غير موجودة في 1.0.6 — للتوافق مع الإصدارات الأخرى).
 *
 * إزالة المانيفست (AD_ID/خدمات الإعلانات) لا تتم من Xposed runtime؛
 * راجع MANIFEST_PATCH_GUIDE.md لخطوات NPatch/LSPatch عند إعادة التغليف.
 */
public final class AdBlocker {
    private AdBlocker() {}

    /** ذاكرة عناوين مُسجَّلة بسقف LRU (تمنع تسرّب الذاكرة في الجلسات الطويلة). */
    private static final Map<String, Boolean> loggedHosts =
            java.util.Collections.synchronizedMap(new LinkedHashMap<String, Boolean>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > 200;
                }
            });

    public static void apply(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PrefsManager.getAdBlock() && !Config.ENABLE_AD_BLOCK) {
            LogUtil.info("AdBlocker: disabled by user toggle — skipped.");
            return;
        }
        try {
            hookShouldIntercept();
        } catch (Throwable t) {
            LogUtil.warn("AdBlocker.intercept failed", t);
        }
        try {
            hookShouldOverride();
        } catch (Throwable t) {
            LogUtil.warn("AdBlocker.override failed", t);
        }
        try {
            hookWebViewLoadUrl();
        } catch (Throwable t) {
            LogUtil.warn("AdBlocker.loadurl failed", t);
        }
        try {
            disableAdSdks(lpparam);
        } catch (Throwable t) {
            LogUtil.warn("AdBlocker.sdk failed", t);
        }
    }

    private static WebResourceResponse emptyResponse() {
        try {
            return new WebResourceResponse("text/plain", "utf-8", 204, "No Content",
                    null, new ByteArrayInputStream(new byte[0]));
        } catch (Throwable t) {
            try {
                return new WebResourceResponse("text/plain", "utf-8",
                        new ByteArrayInputStream(new byte[0]));
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    private static void logOnce(String host) {
        try {
            if (loggedHosts.containsKey(host)) return;
            loggedHosts.put(host, Boolean.TRUE);
            LogUtil.info("Ad request blocked: " + host);
        } catch (Throwable ignored) {
        }
    }

    /** فحص موحّد: قائمة Config المدمجة + قائمة المستخدم المخصصة. */
    static boolean isBlocked(String url) {
        try {
            if (url == null || url.isEmpty()) return false;
            if (Config.isAdHost(url)) return true;
            return PrefsManager.isCustomAdHost(url);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void hookShouldIntercept() {
        // الحمل القديم: shouldInterceptRequest(WebView, String)
        try {
            XposedBridge.hookAllMethods(WebViewClient.class, "shouldInterceptRequest",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (!PrefsManager.getAdBlock()) return;
                                String url = extractUrl(param.args);
                                if (url != null && isBlocked(url)) {
                                    logOnce(shortHost(url));
                                    WebResourceResponse r = emptyResponse();
                                    if (r != null) param.setResult(r);
                                }
                            } catch (Throwable t) {
                                LogUtil.warn("intercept body failed", t);
                            }
                        }
                    });
            LogUtil.info("AdBlock: shouldInterceptRequest hooked.");
        } catch (Throwable t) {
            LogUtil.warn("shouldIntercept hook failed", t);
        }
    }

    private static void hookShouldOverride() {
        try {
            XposedBridge.hookAllMethods(WebViewClient.class, "shouldOverrideUrlLoading",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (!PrefsManager.getAdBlock()) return;
                                String url = extractUrl(param.args);
                                if (url != null && isBlocked(url)) {
                                    logOnce(shortHost(url));
                                    param.setResult(true); // مُنع التنقل
                                }
                            } catch (Throwable t) {
                                LogUtil.warn("override body failed", t);
                            }
                        }
                    });
            LogUtil.info("AdBlock: shouldOverrideUrlLoading hooked.");
        } catch (Throwable t) {
            LogUtil.warn("shouldOverride hook failed", t);
        }
    }

    /** استخراج الـ URL من وسائط الهوك باختلاف الإصدارات (String / Uri / WebResourceRequest). */
    private static String extractUrl(Object[] args) {
        try {
            if (args == null) return null;
            for (Object a : args) {
                if (a == null) continue;
                if (a instanceof String) {
                    String s = (String) a;
                    if (s.startsWith("http") || s.contains(".")) return s;
                } else {
                    try {
                        // WebResourceRequest.getUrl() -> Uri
                        Object url = a.getClass().getMethod("getUrl").invoke(a);
                        if (url != null) return String.valueOf(url);
                    } catch (Throwable ignored) {
                    }
                    String s = String.valueOf(a);
                    if (s.startsWith("http")) return s;
                }
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String shortHost(String url) {
        try {
            int len = Math.min(url.length(), 90);
            return url.substring(0, len);
        } catch (Throwable t) {
            return "ad-url";
        }
    }

    /** اعتراض WebView.loadUrl المباشر — يمنع تحميل نطاقات الإعلانات قبل بدء الطلب. */
    private static void hookWebViewLoadUrl() {
        try {
            XposedBridge.hookAllMethods(android.webkit.WebView.class, "loadUrl",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (!PrefsManager.getAdBlock()) return;
                                if (param.args == null || param.args.length < 1) return;
                                if (!(param.args[0] instanceof String)) return;
                                String url = (String) param.args[0];
                                if (url != null && isBlocked(url)) {
                                    logOnce(shortHost(url));
                                    param.setResult(null); // إلغاء التحميل (void)
                                }
                            } catch (Throwable t) {
                                LogUtil.warn("loadUrl body failed", t);
                            }
                        }
                    });
            LogUtil.info("AdBlock: WebView.loadUrl hooked.");
        } catch (Throwable t) {
            LogUtil.warn("loadUrl hook failed", t);
        }
    }

    /** تعطيل دوال الإعلانات إن وُجدت (بحث بالانعكاس — لا أسماء ثابتة ملزمة). */
    private static void disableAdSdks(final XC_LoadPackage.LoadPackageParam lpparam) {
        int hooked = 0;
        for (String cls : Config.AD_CLASS_CANDIDATES) {
            Class<?> c;
            try {
                c = ReflectionHelper.findFirstExisting(lpparam.classLoader, cls);
            } catch (Throwable t) {
                continue;
            }
            if (c == null) continue; // طبيعي في AshnaAI — لا SDK إعلاني.
            for (String m : new String[]{"loadAd", "show", "showAd", "load"}) {
                try {
                    int n = ReflectionHelper.hookAllMethodsSafe(c, m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (!PrefsManager.getAdBlock()) return;
                                LogUtil.info("Ad SDK call disabled (no-op).");
                                param.setResult(null);
                            } catch (Throwable t) {
                                LogUtil.warn("ad sdk body failed", t);
                            }
                        }
                    });
                    hooked += n;
                } catch (Throwable ignored) {
                }
            }
        }
        LogUtil.info("Ad SDK sweep done. Methods neutralized: " + hooked
                + " (0 = expected on AshnaAI 1.0.6, no native ads).");
    }
}
