package com.ashnapatch.xposed.hooks;

import android.webkit.WebViewClient;

import com.ashnapatch.xposed.PrefsManager;
import com.ashnapatch.xposed.util.LogUtil;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * التشخيص الشبكي — قراءة فقط، للمستقبل.
 *
 * الهدف: عند ظهور حوار تحديث/إعلان جديد من طبقة الويب مستقبلاً، يفعّل
 * المستخدم مفتاح "التشخيص الشبكي" من شاشة التحكم، فيُسجَّل في LSPosed log
 * المضيف+المسار (بلا query) للطلبات المشتبهة — ثم يضيفها لقوائمه المخصصة.
 *
 * ضمانات النطاق الآمن:
 * - لا يعترض ولا يعدّل ولا يحجب أي طلب (afterHookedMethod للتسجيل فقط).
 * - لا يسجّل أجسام الردود ولا أي query (رموز/كلمات مرور) — المضيف+المسار فقط.
 * - يتجاهل مسارات المصادقة/الاشتراك/الدفع/الترخيص كلياً (لا تُسجَّل حتى).
 * - مغلق افتراضياً — يُفعَّل يدوياً عند الحاجة فقط.
 */
public final class NetworkDiag {
    private NetworkDiag() {}

    /** كلمات تستحق التسجيل التشخيصي (تحديث/إعلانات/تهيئة ويب). */
    private static final String[] DIAG_KEYWORDS = new String[]{
            "version", "update", "config", "modal", "popup", "banner",
            "ads", "promo", "announce", "notice"
    };

    /** مسارات حساسة: تُتجاهل كلياً ولا تُسجَّل أبداً (مصادقة/دفع/ترخيص). */
    private static final String[] SENSITIVE = new String[]{
            "api/user", "api/otp", "otp", "api/session", "session",
            "api/auth", "auth", "api/plan", "plan", "payment",
            "payment-history", "connector", "login", "password",
            "token", "license", "pairip", "check_license", "verify"
    };

    private static volatile boolean hooked;

    public static void apply() {
        synchronized (NetworkDiag.class) {
            if (hooked) return;
            try {
                // تسجيل عناوين الطلبات المشتبهة بعد اكتمال الاعتراض (قراءة فقط).
                XposedBridge.hookAllMethods(WebViewClient.class, "shouldInterceptRequest",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                try {
                                    if (!PrefsManager.getNetDiag()) return;
                                    String url = extractUrl(param.args);
                                    if (url == null) return;
                                    if (isSensitive(url)) return;
                                    if (!matchesDiag(url)) return;
                                    LogUtil.info("NetDiag: " + sanitize(url));
                                } catch (Throwable t) {
                                    LogUtil.warn("netdiag body failed", t);
                                }
                            }
                        });
                LogUtil.info("NetworkDiag: observer active (read-only, opt-in).");
            } catch (Throwable t) {
                LogUtil.warn("NetworkDiag intercept hook failed", t);
            }
            try {
                // تسجيل أخطاء تحميل الويب (رمز الخطأ + عنوان معقّم) — يساعد تشخيص المستقبل.
                XposedBridge.hookAllMethods(WebViewClient.class, "onReceivedError",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                try {
                                    if (!PrefsManager.getNetDiag()) return;
                                    String url = extractUrl(param.args);
                                    if (url == null || isSensitive(url)) return;
                                    LogUtil.info("NetDiag error page: " + sanitize(url));
                                } catch (Throwable t) {
                                    LogUtil.warn("netdiag error body failed", t);
                                }
                            }
                        });
            } catch (Throwable t) {
                LogUtil.warn("NetworkDiag error hook failed", t);
            }
            hooked = true;
        }
    }

    private static boolean matchesDiag(String url) {
        try {
            String lower = url.toLowerCase();
            for (String k : DIAG_KEYWORDS) {
                if (lower.contains(k)) return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isSensitive(String url) {
        try {
            String lower = url.toLowerCase();
            for (String s : SENSITIVE) {
                if (lower.contains(s)) return true;
            }
            return false;
        } catch (Throwable t) {
            return true;
        }
    }

    /** مضيف+مسار فقط: يُزال الـ query والـ fragment، ويُقصَّر لـ 160 حرفاً. */
    static String sanitize(String url) {
        try {
            String s = url;
            int q = s.indexOf('?');
            if (q >= 0) s = s.substring(0, q);
            int f = s.indexOf('#');
            if (f >= 0) s = s.substring(0, f);
            s = s.trim();
            if (s.length() > 160) s = s.substring(0, 160) + "…";
            return s.isEmpty() ? "(empty-url)" : s;
        } catch (Throwable t) {
            return "(unprintable-url)";
        }
    }

    /** استخراج الـ URL باختلاف الإصدارات (String / WebResourceRequest.getUrl). */
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
}
