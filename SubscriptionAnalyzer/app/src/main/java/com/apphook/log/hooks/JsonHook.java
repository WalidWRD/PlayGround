package com.apphook.log.hooks;

import com.apphook.log.Config;
import com.apphook.log.FingerprintEngine;
import com.apphook.log.SafeHook;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;

/** Feature JSON/org.json — تسجيل دائم + ترقيع اختياري (F005). */
public final class JsonHook {
    private JsonHook() {}

    public static void install(XposedInterface fw, ClassLoader cl) {
        Class<?> j = FingerprintEngine.findClassQuiet(cl, "org.json.JSONObject");
        if (j == null) {
            try {
                j = Class.forName("org.json.JSONObject");
            } catch (Throwable ignored) {}
        }
        if (j == null) {
            SafeHook.logSafe("Json", "org.json absent");
            return;
        }
        Method gb = FingerprintEngine.findMethodByFingerprint(j,
                "getBoolean", boolean.class, 1);
        HookKit.hookOne(fw, gb, "Json.getBoolean", chain -> {
            Object orig = chain.proceed();
            try {
                String k = (String) chain.getArg(0);
                Object patched = orig;
                if (Config.PATCH_JSON && isPremium(k) && Boolean.FALSE.equals(orig)) {
                    patched = true;
                    SafeHook.countOpen("json_bool:" + k, "F005");
                }
                if (isPremium(k)) {
                    SafeHook.recordCall("Json", "getBoolean(" + k + ")", 1, orig, patched);
                    if (patched != orig) return patched;
                }
            } catch (Throwable t) {
                SafeHook.logError("Json.getBoolean", t);
            }
            return orig;
        });
        Method gi = FingerprintEngine.findMethodByFingerprint(j,
                "getInt", int.class, 1);
        HookKit.hookOne(fw, gi, "Json.getInt", chain -> {
            Object orig = chain.proceed();
            try {
                String k = (String) chain.getArg(0);
                if (isPremium(k)) {
                    SafeHook.recordCall("Json", "getInt(" + k + ")", 1, orig, orig);
                }
            } catch (Throwable t) {
                SafeHook.logError("Json.getInt", t);
            }
            return orig;
        });
        SafeHook.log("Json: hooked (patch=" + Config.PATCH_JSON + ")");
    }

    public static boolean isPremium(String k) {
        if (k == null) return false;
        String l = k.toLowerCase();
        return l.contains("premium") || l.contains("vip") || l.contains("pro")
                || l.contains("paid") || l.contains("subscri") || l.contains("entitle")
                || l.contains("balance") || l.contains("credit") || l.contains("plan")
                || l.contains("membership");
    }
}
