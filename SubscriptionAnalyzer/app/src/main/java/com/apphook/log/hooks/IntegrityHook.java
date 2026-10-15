package com.apphook.log.hooks;

import com.apphook.log.Config;
import com.apphook.log.FingerprintEngine;
import com.apphook.log.SafeHook;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;

/** Integrity — تسجيل دائماً؛ التعديل مغلق افتراضياً (PATCH_INTEGRITY=false). */
public final class IntegrityHook {
    private IntegrityHook() {}

    public static void install(XposedInterface fw, ClassLoader cl) {
        Class<?> im = FingerprintEngine.findClassQuiet(cl,
                "com.google.android.play.core.integrity.IntegrityManager");
        if (im == null) {
            SafeHook.logSafe("Integrity", "Play Integrity absent");
            return;
        }
        Method target = null;
        try {
            for (Method m : im.getDeclaredMethods()) {
                if (m.getName().toLowerCase().contains("integritytoken")) {
                    m.setAccessible(true);
                    target = m;
                    break;
                }
            }
        } catch (Throwable t) {
            SafeHook.logError("Integrity.scan", t);
        }
        HookKit.hookOne(fw, target, "Integrity.requestToken", chain -> {
            Object orig;
            try {
                orig = chain.proceed();
            } catch (Throwable t) {
                SafeHook.logError("Integrity", t);
                throw t;
            }
            SafeHook.recordCall("Integrity", "requestIntegrityToken", 1,
                    "(request)", "(request)");
            if (Config.PATCH_INTEGRITY) {
                SafeHook.countOpen("integrity_block", "F007");
            }
            return orig;
        });
        SafeHook.log("Integrity: hooked (record-only)");
    }
}
