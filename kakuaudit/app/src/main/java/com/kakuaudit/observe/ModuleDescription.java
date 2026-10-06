package com.kakuaudit.observe;

/**
 * Single source of truth for the embedded module description.
 * Shown in: (1) module manager via xposeddescription, (2) boot log via
 * XposedBridge.log, (3) in-app MainActivity. Update on every release.
 */
public final class ModuleDescription {
    public static final String MODULE_NAME = "KakuAudit Observe";
    public static final String VERSION = "3.1.1";
    public static final int VERSION_CODE = 311;
    public static final String SPEC = "KakuAudit v3 (OBSERVE_ONLY)";
    /** Target apps: scope-based — any user-selected package, no hard-coded target. */
    public static final String TARGET = "أي تطبيق يختاره المستخدم (scope) — بدون روت عبر LSPatch/NPatch/HKPatch";
    public static final String MODE = "OBSERVE_ONLY — مراقبة وتوثيق فقط، بدون تجاوز/تعطيل/تزوير";

    public static final String[] FEATURES = {
            "هوك انعكاسي عبر lpparam.classLoader بدون أسماء ثابتة (يتعامل مع الإصدارات الأعلى والأقل)",
            "حماية من الكراشات: عزل كل هوك + حارس reentrancy + kill-switch + لا حجب لخيط التطبيق",
            "مراقب Native/Runtime معطّل افتراضيًا (يُفعّل من kakuaudit-config.json بعد ثبات الهدف)",
            "دعم التطبيقات المضغوطة/المحمية: تسجيل PROTECTED_OR_UNAVAILABLE ومواصلة المراقبة",
            "مراقبو License/Entitlement/Subscription + Activity/WebView/Network (ميتاداتا فقط)",
            "تخزين بدون روت: MediaStore/SAF/legacy/app-private + كتابة ذرية + جلسات لا تُكتب فوق بعضها",
            "blueprint محفوظ (assets/module-blueprint.json) يغني عن إعادة التحليل كل مرة"
    };

    /** Short one-line text for the manifest xposeddescription field. */
    public static final String SHORT =
            "KakuAudit Observe v3.1.1 — OBSERVE_ONLY، انعكاسي متعدد الإصدارات، "
            + "آمن ضد الكراشات (reentrancy-guard)، بدون روت (LSPatch/NPatch/HKPatch). الهدف: أي حزمة يختارها المستخدم.";

    private ModuleDescription() {}

    public static String full() {
        StringBuilder sb = new StringBuilder();
        sb.append(MODULE_NAME).append(" v").append(VERSION)
          .append(" (").append(SPEC).append(")\n")
          .append("الوضع: ").append(MODE).append("\n")
          .append("يعمل على: ").append(TARGET).append("\n")
          .append("الميزات الجديدة:\n");
        for (String f : FEATURES) sb.append(" • ").append(f).append("\n");
        return sb.toString();
    }

    /** Write to Xposed boot log (reflective — safe without the API jar). */
    public static void logToXposed(String extra) {
        String msg = "[KakuAudit] " + SHORT + (extra == null ? "" : " | " + extra);
        try {
            Class<?> b = Class.forName("de.robv.android.xposed.XposedBridge");
            b.getMethod("log", String.class).invoke(null, msg);
        } catch (Throwable ignore) {}
        try {
            android.util.Log.i("KakuAudit", msg);
        } catch (Throwable ignore) {}
    }
}
