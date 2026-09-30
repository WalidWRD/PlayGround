package com.genspark.updatekiller;

/* loaded from: classes.dex */
public final class BuildInfo {
    public static final String AUTHOR = "UpdateKiller Project";
    public static final String HOMEPAGE = "https://www.genspark.ai/";
    public static final String MODULE_NAME = "Genspark UpdateKiller";
    public static final String RELEASE_DATE = "2026-09-30";
    public static final String TARGET_APP = "Genspark AI";
    public static final int VERSION_CODE = 2003;
    public static final String VERSION_LABEL = "v2.0.3 (build 2003)";
    public static final String VERSION_NAME = "2.0.3";
    public static final String TARGET_PKG_PRO = "ai.mainfunc.genspark.pro";
    public static final String TARGET_PKG_FREE = "ai.mainfunc.genspark";
    public static final String[] PACKAGE_NAMES = {TARGET_PKG_PRO, TARGET_PKG_FREE};

    public static String describe() {
        return "Genspark UpdateKiller v2.0.3 (build 2003)\nTarget: Genspark AI — ai.mainfunc.genspark.pro (و ai.mainfunc.genspark)\nAuthor: UpdateKiller Project  ·  Release: 2026-09-30\nEngine: lpparam.classLoader + signature/reflection lookup (no hard-coded names).\nSafety: Guard auto Kill-Switch after configurable failures — never crashes the host app.\nDeploy: LSPatch / NPatch / HKP-patch, no root required.\nFixes in v2.0.3: PREMIUM correction — infinite credits (999999999, reads+writes+server JSON), free ultra mode (enabled, no deduction), subscription-banner removal, credit-exhaustion dialog blocking (was leaking through payment-keyword exemption).\nFixes in v2.0.2: LIFETIME subscription (expiry 2100-01-01) + server-verification bypass on 3 levels (raw HTTP body, parsed JSON, org.json getters).\nFixes in v2.0.1: installable manifest (versionCode/versionName, minSdk26/targetSdk34, launcher UI, xposedminversion 93), MethodChannel ALL overloads (incl. Result callback), VersionSpoof longVersionCode + all getPackageInfo overloads, NativeHook chunked scan + app-native-dir lookup, Discovery cleanup.\n--- Features (21 hooks) ---\n  1) Forced-update bypass (instant + optional dialogs)\n  2) Version spoofing (package_info + PackageManager fallback)\n  3) JSON neutralizing (forceUpgrade→false, minAppVersionCode→0,\n     minAppRequireAppVersion→\"\") in any HTTP response\n  4) Play Core In-App Update neutralization (3 layers)\n  5) Store-intent blocking + WebView store blocking\n  6) Configurable store-redirect URL (default: https://www.genspark.ai/)\n  7) SharedPreferences flag forcing\n  8) Hot-reload config + configurable Kill-Switch\n  9) Native libapp.so probe (Blutter ObjectPool markers)\n 10) Flutter MethodChannel monitoring\n 11) Auto event-log (timestamps ms)\n 12) Ad blocker (AdMob + WebView deny-list)\n 13) Warning/promo dialog blocker\n 14) Official genspark.ai redirect bypass\n 15) Anti-crash safety layer\n 16) Login/onboarding gate bypass\n 17) Auto-login (native + WebView + Flutter)\n 18) Google Sign-In Flutter channel spoof\n 19) \"سجّل الدخول\" banner blocker (4 layers)\n 20) SubscriptionBridge — synthesizeActive(): forces paid subscription\n 21) SubscriptionEnforcer — SharedPreferences isSubscribed/isPro → TRUE\nEnv: works under LSPatch / NPatch / HKP-patch, no root required.";
    }

    public static String shortDescription() {
        return "Genspark UpdateKiller v2.0.0 (build 2000)\n\n■ التطبيق المُستهدف: Genspark AI — ai.mainfunc.genspark.pro (وai.mainfunc.genspark)\n■ الإصدار المُحلَّل: versionName 2.9.8 / versionCode 29800 (minSdk 28, targetSdk 36)\n\n■ الميزات الرئيسية (٢١ خطّاف):\n  1) تعطيل التحديث الإجباري وحوار التحديث الفوري/الاختياري\n  2) تزوير رقم واسم الإصدار (package_info + PackageManager fallback)\n  3) تعديل حقول القرار في JSON (forceUpgrade→false، minAppVersionCode→0)\n  4) تعطيل SharedPreferences الخاصة بحوار التحديث\n  5) تحويل زرّ «حدّث الآن» إلى رابط ويب + منع روابط المتجر داخل WebView\n  6) إبطال Play Core In-App Update (3 طبقات)\n  7) إعادة تحميل الإعدادات تلقائيًا (Hot-Reload) + Kill-Switch قابل للضبط\n  8) مراقبة قنوات Flutter MethodChannel + الحوارات الأصلية\n  9) تسجيل تلقائي لأي حدث في ملف log مع وقت ميلي ثانية\n 10) مانع إعلانات (AdMob SDK + WebView host denylist)\n 11) فحص libapp.so (Blutter ObjectPool — forceUpgrade @ 0x124e78)\n 12) تخطّي شاشة تسجيل الدخول والـ Onboarding (prefs flags + activity guard)\n 13) تسجيل دخول تلقائي (EditText fill + WebView/OAuth JS + Flutter) — معطّل افتراضيًا\n 14) سبوف Google Sign-In Flutter (يعبر بوابة الدخول بدون حساب Google)\n 15) إزالة ديالوج «سجّل الدخول» (الشريط الأحمر) — 4 طبقات\n 16) تخطّي تحويل الموقع الرسمي (genspark.ai)\n 17) طبقة Anti-crash (try/eat + Kill-Switch تلقائي)\n 18) مراقب Blutter Trace (ObjectPool)\n 19) مانع حوارات التحذير/الإعلان\n 20) SubscriptionBridge: فرض اشتراك فعّال (plan=pro، status=active)\n 21) SubscriptionEnforcer: إجبار أعلام الاشتراك على true\n\n■ تحرير v2.0.0: اشتراك إجباري (ليس اختياري) — كل استعلام Flutter يُفعّل الحساب، وكل SharedPreferences تتضمّن اشتراك=true.\n\n■ المحرّك: lpparam.classLoader + بحث بالاسم وبالانعكاس (يشتغل على R8/ProGuard والحزم المضغوطة/المُشفّرة).\n■ الأمان ضد الكراش: Guard.run/value + Kill-Switch تلقائي بعد 20 فشلًا افتراضيًا.\n■ البيئة: LSPatch / NPatch / HKP-patch بدون روت — يصلح للتطبيقات المضغوطة والمحمية.\n\n■ الإعداد: /data/local/tmp/genspark_updatekiller.json\n■ السجل: logcat -s GensparkUpdateKiller";
    }

    private BuildInfo() {
    }
}
