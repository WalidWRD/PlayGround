package com.ashnapatch.xposed;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * إعدادات الموديول — النقطة الوحيدة التي يحتاج المطوّر تعديلها للصيانة.
 *
 * ملاحظة أمان: القائمة البيضاء تحمي شاشات الدخول/OTP/الدفع من الحجب الخطأ.
 * لا تضف نطاقات api.ashna.ai إلى قوائم الحجب أبداً.
 */
public final class Config {
    private Config() {}

    public static final boolean ENABLE_UPDATE_BLOCK = true;
    public static final boolean ENABLE_WARNING_FILTER = true;
    public static final boolean ENABLE_AD_BLOCK = true;
    /** حقن JS لإخفاء عناصر حوار التحديث/الإعلانات داخل WebView. */
    public static final boolean ENABLE_JS_HIDE = true;

    /** كلمات حوار التحديث الإجباري (عربي/إنجليزي) — تُقرأ مع مفاتيح PrefsManager الحية. */
    public static final Set<String> UPDATE_KEYWORDS = new HashSet<>(Arrays.asList(
            "force update", "update required", "mandatory update", "new version required",
            "please update", "update now", "update available", "update to continue",
            "new update available",
            "تحديث إجباري", "تحديث الزامي", "تحديث إلزامي", "يجب التحديث",
            "نسخة جديدة مطلوبة", "يرجى التحديث", "حدث الآن", "تحديث فوري",
            "اصدار جديد", "إصدار جديد", "نسخة جديدة", "قم بالتحديث",
            "التحديث مطلوب", "التحديث اجباري", "التحديث إلزامي"
    ));

    /** كلمات الرسائل التحذيرية/المزعجة (تطبيق أو مشغّل). */
    public static final Set<String> WARNING_KEYWORDS = new HashSet<>(Arrays.asList(
            "rate us", "rate our app", "قيّم التطبيق", "قيمنا",
            "promo", "promotion", "عرض ترويجي", "إعلان",
            "warning", "تحذير",
            "spin", "claim reward", "مكافأة", "اربح", "اشترك الآن",
            "operator message", "carrier message", "رسالة المشغل", "رسالة من المشغل"
    ));

    /**
     * القائمة البيضاء: أي حوار يحتوي إحدى هذه الكلمات لا يُحجب أبداً
     * (حماية من كسر تسجيل الدخول/التحقق/الدفع).
     */
    public static final Set<String> WHITELIST_KEYWORDS = new HashSet<>(Arrays.asList(
            "otp", "verification code", "رمز التحقق", "تحقق",
            "login", "sign in", "تسجيل الدخول", "دخول",
            "password", "كلمة المرور",
            "payment", "pay", "الدفع", "اشتراك", "plan"
    ));

    /** نطاقات الإعلانات المحجوبة على مستوى WebViewClient (لا تضف نطاقات التطبيق هنا). */
    public static final Set<String> AD_HOST_BLOCKLIST = new HashSet<>(Arrays.asList(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "googletagservices.com",
            "admob", "applovin.com", "unityads", "ironsrc.com", "ironsource",
            "facebook.net", "fbsbx.com", "auditude", "moatads.com",
            "ads.yahoo.com", "amazon-adsystem.com", "taboola.com", "outbrain.com",
            "popads", "propellerads"
    ));

    /** أسماء مرشحة لكلاسات الإعلانات — تُفحص بالانعكاس، وإن غابت يُتجاهل الهوك بصمت. */
    public static final String[] AD_CLASS_CANDIDATES = new String[]{
            "com.google.android.gms.ads.AdView",
            "com.google.android.gms.ads.InterstitialAd",
            "com.google.android.gms.ads.rewarded.RewardedAd",
            "com.applovin.mediation.ads.MaxAdView",
            "com.unity3d.ads.UnityAds",
            "com.ironsource.mediationsdk.IronSource",
            "com.facebook.ads.AdView"
    };

    /** أسماء مرشحة لكلاسات التحديث — تُفحص بالانعكاس فقط (AshnaAI 1.0.6 لا تحتويها أصلاً). */
    public static final String[] UPDATE_CLASS_CANDIDATES = new String[]{
            "com.google.android.play.core.appupdate.AppUpdateManagerImpl",
            "com.google.android.play.core.appupdate.AppUpdateManagerFactory",
            "expo.modules.updates.UpdatesController"
    };

    public static boolean containsAny(String haystack, Set<String> keywords) {
        if (haystack == null || haystack.isEmpty()) return false;
        String lower = haystack.toLowerCase();
        for (String k : keywords) {
            if (k != null && !k.isEmpty() && lower.contains(k.toLowerCase())) return true;
        }
        return false;
    }

    public static boolean isAdHost(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        for (String h : AD_HOST_BLOCKLIST) {
            if (lower.contains(h.toLowerCase())) return true;
        }
        return false;
    }
}
