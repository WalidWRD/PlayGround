package com.apphook.log.hooks;

import com.apphook.log.FingerprintEngine;
import com.apphook.log.SafeHook;

import io.github.libxposed.api.XposedInterface;

/**
 * بوابات التحديث الإجباري — اكتشاف وتسجيل البصمة فقط.
 * لا تعديل (التعديل هنا قد يمنع التطبيق من العمل نهائياً).
 */
public final class UpdateGateHook {
    private UpdateGateHook() {}

    public static void install(XposedInterface fw, ClassLoader cl) {
        String[] candidates = {
                "com.google.android.play.core.appupdate.AppUpdateManager",
                "com.google.android.play.core.appupdate.AppUpdateManagerFactory"
        };
        for (String name : candidates) {
            Class<?> c = FingerprintEngine.findClassQuiet(cl, name);
            if (c == null) continue;
            SafeHook.log("UpdateGate: found " + name);
            SafeHook.recordCall("UpdateGate", "class_found", 0, name, name);
        }
        SafeHook.log("UpdateGate: scan done");
    }
}
