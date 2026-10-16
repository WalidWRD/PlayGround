package com.apphook.log;

/**
 * إعدادات التشغيل — v6.3: PATCH+FREEZE+ROTATE موحّد.
 *
 * <p>القيمة الموحّدة للرصيد 9999999 في كل الطبقات (كانت 100 في Prefs
 * مقابل 9999999 في WebView). مطابقة الحزم ديناميكية عبر
 * {@link #isTargetPackage} لدعم الكلونات (.hook/.test).</p>
 */
public final class Config {
    private Config() {}

    // ═══ الوضع العام ═══
    public static final boolean PATCH_MODE = true;

    // ═══ اسم الحزمة – دعم الكلونات ═══
    // يقبل أي حزمة تبدأ بأحد هذه البادئات
    public static final String[] PACKAGE_PREFIXES = {
        "ai.mainfunc.genspark",
        "ai.genspark",
        "com.genspark",
        "genspark."
    };

    // ═══ الطبقات ═══
    public static final boolean PATCH_BILLING       = true;
    public static final boolean PATCH_REVENUECAT    = true;
    public static final boolean PATCH_FLUTTER       = true;
    public static final boolean PATCH_WEBVIEW       = true;
    public static final boolean PATCH_PREFS         = true;
    public static final boolean PATCH_JSON          = true;
    public static final boolean PATCH_NETWORK       = true;
    public static final boolean PATCH_INTEGRITY     = false; // دائماً off — قد تكسر التطبيق
    public static final boolean PATCH_UPDATE_GATE   = PATCH_MODE;

    // ═══ Request/Response ═══
    public static final boolean PATCH_REQUESTS      = true;
    public static final boolean PATCH_RESPONSES     = true;

    // ═══ Balance Freeze – القيمة الموحَّدة ═══
    public static final boolean ENABLE_BALANCE_FREEZE = true;
    public static final int     FROZEN_BALANCE        = 9999999;  // ← موحَّد
    public static final String  FROZEN_BALANCE_STR    = "9999999";
    public static final boolean LOG_ORIGINAL_BALANCE  = true;

    // ═══ Plan Enforcement ═══
    public static final String  FORCED_PLAN           = "plus";
    public static final String  FORCED_PLAN_DISPLAY   = "Genspark Plus";
    public static final String  FORCED_STATUS         = "active";

    // ═══ Account Rotation ═══
    public static final boolean ENABLE_ROTATION       = true;
    public static final String  FREE_MODEL_NAME       = "gpt-4o-mini";
    public static final String  DEFAULT_PLAN          = "plus";

    // ═══ Reporting ═══
    public static final boolean WRITE_REPORT          = true;
    public static final String  REPORT_PATH           = "/sdcard/SubscriptionAnalyzer/";
    public static final boolean VERBOSE_LOG           = true;

    /** اسم الحزمة المستهدفة وقت التشغيل (يُضبط من MainHook/LegacyHook) */
    public static volatile String runtimePackage = "unknown";

    public static String modeName() {
        return PATCH_MODE ? "PATCH" : "ANALYZE";
    }

    /** هل الحزمة مستهدفة؟ (دعم الكلونات) */
    public static boolean isTargetPackage(String pkg) {
        if (pkg == null) return false;
        for (String prefix : PACKAGE_PREFIXES) {
            if (pkg.startsWith(prefix)) return true;
        }
        return false;
    }
}
