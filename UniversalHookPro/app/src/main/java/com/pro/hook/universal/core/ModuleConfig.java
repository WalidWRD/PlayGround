package com.pro.hook.universal.core;

/**
 * إعداد الموديول — التعامل مع أي إصدار (أقل/أعلى) وبغض النظر عن اسم الحزمة.
 *
 * <p>القاعدة: لا أسماء ثابتة. كل الاستهداف عبر {@code lpparam.classLoader}
 * + البحث بالانعكاس والبصمة. هذا الملف فقط للإعدادات العامة
 * (قوائم الاستثناء، حدود الإصدارات، مفاتيح التفعيل).</p>
 */
public final class ModuleConfig {
    private ModuleConfig() {}

    /**
     * "*" = استهداف شامل (أي حزمة). للتضييق ضع أسماء حزم هنا.
     * ملاحظة: حتى مع "*" لا نستخدم أسماء كلاسات ثابتة — البحث بالبصمة فقط.
     */
    public static final String[] TARGET_PACKAGES = new String[]{"*"};

    /** حزم نظام لا نحقن فيها أبداً (تجنّب البوت لوب والكراش). */
    public static final String[] PACKAGE_BLOCKLIST = new String[]{
            "android",
            "com.android.systemui",
            "com.android.phone",
            "com.pro.hook.universal" // نفس الموديول
    };

    /** اقبل أي إصدار تطبيق (أقل/أعلى). null = بلا حدود. */
    public static final Long MIN_APP_VERSION_CODE = null;
    public static final Long MAX_APP_VERSION_CODE = null;

    /** مفاتيح الصيانة والتطوير */
    public static final boolean ENABLE_FINGERPRINT_CACHE = true; // عدم التحليل كل مرة
    public static final boolean ENABLE_DELAYED_SCAN_FOR_PACKED_APPS = true;
    public static final boolean ENABLE_VERBOSE_LOG = false; // سجل التشغيل مختصر
    public static final int  MAX_CLASSES_TO_SCAN = 20000;   // سقف أداء
    public static final long SCAN_TIMEOUT_MS = 8000;

    public static boolean isBlocked(String pkg) {
        if (pkg == null) return true;
        for (String b : PACKAGE_BLOCKLIST) {
            if (pkg.equals(b)) return true;
        }
        return false;
    }

    public static boolean isTargeted(String pkg) {
        if (isBlocked(pkg)) return false;
        if (TARGET_PACKAGES.length == 1 && "*".equals(TARGET_PACKAGES[0])) return true;
        for (String t : TARGET_PACKAGES) {
            if (pkg.equals(t)) return true;
        }
        return false;
    }
}
