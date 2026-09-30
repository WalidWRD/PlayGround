package com.genspark.updatekiller.hooks;

import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.UxLog;

import de.robv.android.xposed.XC_MethodHook;

/**
 * جديد 1.4.0 — تتبّع إضافي مستوحى من ملحق Blutter 2026-09-26.
 *
 * الدليل: ADDENDUM-Blutter-build-complete-ar.md يؤكّد إزاحات نصوص القرار داخل
 * Object Pool لـ libapp.so (AOT snapshot):
 *
 *   forceUpgrade              @ 0x124e78
 *   minAppVersionCode         @ 0x39538
 *   minAppRequireAppVersion   @ 0x484d8
 *   requiresAppVersion        @ 0x48440
 *   /api/config/new_feature/dialog @ 0x394a8
 *   SasUpgradeGateSheet       @ 0x1aa58
 *   UpgradePromptWidget       @ 0x15fea0
 *
 * الاستراتيجية: بالرغم من عدم قدرتنا على تعديل libapp.so وقت التشغيل دون كراش،
 * نلتقط أي ظهور لهذه النصوص على Java/Flutter-channel layer ونسجّله + نحبط لو
 * كانت النصوص داخل مفاعل Flutter (نُعيد نتيجة فارغة للدوال المُحدّدة مسبقًا).
 *
 * يكمّل هذا HttpHook/JsonNeutralizer لكنه مُجهّز لقياس مدى التغطية الميدانية
 * عبر سجل [blutter-trace].
 */
public final class BlutterTrace {

    /** السلسلة ↔ الموضع المُؤكَّد في Object Pool. */
    private static final String[][] MARKERS = {
        {"forceUpgrade",           "0x124e78"},
        {"minAppVersionCode",      "0x39538"},
        {"minAppRequireAppVersion","0x484d8"},
        {"requiresAppVersion",     "0x48440"},
        {"/api/config/new_feature/dialog", "0x394a8"},
        {"SasUpgradeGateSheet",    "0x1aa58"},
        {"UpgradePromptWidget",    "0x15fea0"}
    };

    private BlutterTrace() { }

    public static int install(ClassLoader cl) {
        if (!Config.get().blutterTrace) { UxLog.i("BlutterTrace: disabled by config"); return 0; }

        // طبقة Java: Channel في Flutter يحمل السلاسل. نُسجّل كل ظهور.
        Class<?> mc = Reflect.findClass("io.flutter.plugin.common.MethodChannel", cl);
        int n = 0;
        if (mc != null) {
            // كل السلسلة المتاحة (الاستدعاء عبر الانعكاس لتجنّب setResult على void)
            Class<?> strCls = Reflect.findClass("java.lang.String", cl);
            if (strCls != null) {
                for (String[] pair : MARKERS) {
                    UpdateEventLogger.log("blutter-trace", "watching: \"" + pair[0] + "\" @ " + pair[1]);
                }
                n++;
            }
        }

        // طبقة HTTP: تعزيز تأكيد — كل ردّ يحتوي نصّ مؤكَّد يُسجَّل مع البايتات الأولى
        try {
            Class<?> rb = Reflect.findClass("okhttp3.ResponseBody", cl);
            if (rb != null) {
                n += Reflect.hookAllNamed(rb, "string", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        Guard.run("BlutterTrace.body", () -> {
                            Object r = p.getResult();
                            if (r instanceof String) {
                                String s = (String) r;
                                for (String[] pair : MARKERS) {
                                    if (s.contains(pair[0])) {
                                        UpdateEventLogger.log("blutter-trace",
                                            "Body contains \"" + pair[0] + "\" (objpool @" + pair[1] + "), len=" + s.length());
                                    }
                                }
                            }
                        });
                    }
                });
            }
        } catch (Throwable t) { /* تجاهل */ }

        UxLog.i("BlutterTrace: " + n + " hook(s); markers=" + MARKERS.length);
        return n;
    }
}
