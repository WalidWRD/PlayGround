package com.pro.hook.universal.core;

/**
 * هوية الموديول — مصدر واحد للحقيقة (يُعرض في المدير + سجل التشغيل + التقرير).
 * عدّل هنا فقط عند إصدار نسخة جديدة.
 */
public final class ModuleInfo {
    private ModuleInfo() {}

    public static final String NAME = "UniversalHookPro";
    public static final String NAME_AR = "الموديول الاحترافي الشامل";
    public static final String PACKAGE = "com.pro.hook.universal";
    public static final String VERSION_NAME = "1.0.0";
    public static final int VERSION_CODE = 10000;

    // التوافق
    public static final int MIN_SDK = 26;          // Android 8.0
    public static final int TARGET_SDK = 35;       // Android 15
    public static final int COMPILE_SDK = 35;
    public static final int XPOSED_MIN_VERSION = 82;

    // البيئات المدعومة بدون روت
    public static final String[] SUPPORTED_PATCHERS =
            new String[]{"LSPosed", "LSPatch", "NPatch", "HKPatch (HKP)"};

    /**
     * الوصف المدمج (): مختصر جداً — يظهر في مدير الموديولات وسجل التشغيل.
     * يُستدعى من MainHook عند الإقلاع ومن InfoActivity ومن xposeddescription.
     */
    public static String describe() {
        return NAME + " v" + VERSION_NAME
                + " | انعكاس+بصمة بلا أسماء ثابتة"
                + " | آمن ضد الكراش"
                + " | كل الإصدارات/الحزم"
                + " | LSPatch/NPatch/HKP بلا روت";
    }

    /** سطر الميزات المختصر لسجل التشغيل */
    public static String featureLine() {
        return "[Features] fingerprint-search, crash-safe, version-agnostic, "
                + "packed-app-ready, lpparam.classLoader, no-root-patch";
    }
}
