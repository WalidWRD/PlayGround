package com.ashnapatch.xposed;

/**
 * معلومات الموديول المدمجة (Built-in).
 * تُطبع في سجل التشغيل (XposedBridge.log) عند إقلاع الحزمة المستهدفة،
 * وتظهر في مدير الموديولات عبر xposeddescription في المانيفست.
 *
 * للصيانة: حدّث MODULE_VERSION و CHANGELOG مع كل إصدار.
 */
public final class ModuleInfo {
    private ModuleInfo() {}

    public static final String MODULE_NAME = "AshnaAI Patch";
    public static final String MODULE_VERSION = "1.4.0";
    public static final int MODULE_VERSION_CODE = 5;

    public static final String TARGET_APP_NAME = "AshnaAI";
    public static final String TARGET_PACKAGE = "ai.ashna.mobile";
    /** نسخة التطبيق التي بُني عليها التحليل؛ الموديول مصمم ليعمل مع الأعلى والأقل. */
    public static final String TARGET_APP_ANALYZED_VERSION = "1.0.6 (10006)";

    public static final String DESCRIPTION =
            MODULE_NAME + " v" + MODULE_VERSION
            + " | للتطبيق: " + TARGET_APP_NAME + " (" + TARGET_PACKAGE + ")"
            + " نسخة التحليل " + TARGET_APP_ANALYZED_VERSION + " — متوافق مع الأعلى والأقل"
            + " | الميزات: [1] تعطيل حوار التحديث الإجباري/الفوري"
            + " [2] كتم الرسائل التحذيرية المزعجة (تطبيق/مشغّل) مع قائمة بيضاء"
            + " [3] حجب الإعلانات (WebViewClient + loadUrl + حقن JS + قوائم مخصصة)"
            + " [4] شاشة تحكم بمفاتيح وقوائم حيّة + تشخيص شبكي قراءة فقط"
            + " + بطاقة اشتراك محلية (تذكير يدوي + فتح التطبيق الرسمي)";

    /** محدَّث — يُعرض في مدير الموديولات وسجل التشغيل مع كل إصدار. */
    public static final String CHANGELOG =
            "محدَّث v1.4.0 — الجديد: بطاقة حالة اشتراك محلية (اسم الخطة + تاريخ انتهاء"
            + " + عدّاد أيام + زر فتح التطبيق الرسمي — تذكير على جهازك فقط،"
            + " لا يقرأ الخادم ولا يعدّل الاشتراك).";

    /** نطاق الدعم: لا يلمس المصادقة/الاشتراك/الدفع/فحص الترخيص — خارج النطاق عمداً. */
    public static final String SCOPE_NOTE =
            "نطاق آمن: لا يعترض api/user otp session auth plan payment-history connectors"
            + " ولا يمس PairIP/LicenseActivity — أي حوار تحديث/إعلان مصدره طبقة الويب يُعالج على مستوى العرض فقط.";
}
