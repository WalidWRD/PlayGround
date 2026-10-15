package com.apphook.log;

/**
 * إعدادات التشغيل — تُضبط وقت البناء بإعادة التجميع.
 *
 * <p>MODE = ANALYZE (افتراضي): تسجيل فقط، صفر تعديل، تقرير كامل.
 * MODE = PATCH: تعديل نشط مع قياس كامل. يمكن تفعيل طبقة واحدة
 * بعينها للاختبار المعزول (بقية الطبقات تبقى تسجيلاً فقط).</p>
 */
public final class Config {
    private Config() {}

    // ═══ الوضع العام ═══
    public static final boolean PATCH_MODE = false; // ANALYZE افتراضياً

    // ═══ قنوات التعديل الفردية ═══
    public static final boolean PATCH_BILLING = PATCH_MODE;
    public static final boolean PATCH_REVENUECAT = PATCH_MODE;
    public static final boolean PATCH_FLUTTER = PATCH_MODE;
    public static final boolean PATCH_WEBVIEW = PATCH_MODE;
    public static final boolean PATCH_PREFS = PATCH_MODE;
    public static final boolean PATCH_JSON = PATCH_MODE;
    public static final boolean PATCH_INTEGRITY = false; // دائماً off — قد تكسر التطبيق
    public static final boolean PATCH_UPDATE_GATE = PATCH_MODE;

    // ═══ Reporting ═══
    public static final boolean WRITE_REPORT = true;
    public static final String REPORT_PATH = "/sdcard/SubscriptionAnalyzer/";
    public static final boolean VERBOSE_LOG = true;

    /** اسم الحزمة المستهدفة وقت التشغيل (يُضبط من MainHook/LegacyHook) */
    public static volatile String runtimePackage = "unknown";

    public static String modeName() {
        return PATCH_MODE ? "PATCH" : "ANALYZE";
    }
}
