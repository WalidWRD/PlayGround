package com.apphook.log.hooks;

import com.apphook.log.Config;
import com.apphook.log.FingerprintEngine;
import com.apphook.log.SafeHook;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;

/** Feature JSON/org.json — v6.3 موسّع (bool/int/long/string) + قياس F005/F002. */
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
                if (Config.PATCH_JSON && isPremiumBool(k) && Boolean.FALSE.equals(orig)) {
                    patched = true;
                    SafeHook.countOpen("json.bool:" + k, "F005");
                }
                if (isPremiumBool(k)) {
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
                Object patched = orig;
                if (Config.PATCH_JSON && isBalanceKey(k) && orig instanceof Integer
                        && ((Integer) orig) < 1000000) {
                    patched = Config.FROZEN_BALANCE;
                    SafeHook.countOpen("json.int.balance:" + k, "F002");
                }
                if (isBalanceKey(k) || isPremiumBool(k)) {
                    SafeHook.recordCall("Json", "getInt(" + k + ")", 1, orig, patched);
                    if (patched != orig) return patched;
                }
            } catch (Throwable t) {
                SafeHook.logError("Json.getInt", t);
            }
            return orig;
        });
        Method gl = FingerprintEngine.findMethodByFingerprint(j,
                "getLong", long.class, 1);
        HookKit.hookOne(fw, gl, "Json.getLong", chain -> {
            Object orig = chain.proceed();
            try {
                String k = (String) chain.getArg(0);
                Object patched = orig;
                if (Config.PATCH_JSON && isBalanceKey(k) && orig instanceof Long
                        && ((Long) orig) < 1000000L) {
                    patched = (long) Config.FROZEN_BALANCE;
                    SafeHook.countOpen("json.long.balance:" + k, "F002");
                }
                if (isBalanceKey(k)) {
                    SafeHook.recordCall("Json", "getLong(" + k + ")", 1, orig, patched);
                    if (patched != orig) return patched;
                }
            } catch (Throwable t) {
                SafeHook.logError("Json.getLong", t);
            }
            return orig;
        });
        Method gs = FingerprintEngine.findMethodByFingerprint(j,
                "getString", String.class, 1);
        HookKit.hookOne(fw, gs, "Json.getString", chain -> {
            Object orig = chain.proceed();
            try {
                String k = (String) chain.getArg(0);
                Object patched = orig;
                if (Config.PATCH_JSON && isPlanKey(k) && orig instanceof String
                        && "free".equalsIgnoreCase((String) orig)) {
                    patched = Config.FORCED_PLAN;
                    SafeHook.countOpen("json.str.plan:" + k, "F005");
                }
                if (isPlanKey(k)) {
                    SafeHook.recordCall("Json", "getString(" + k + ")", 1, orig, patched);
                    if (patched != orig) return patched;
                }
            } catch (Throwable t) {
                SafeHook.logError("Json.getString", t);
            }
            return orig;
        });
        SafeHook.log("Json: hooked v6.3 (bool/int/long/string)");
    }

    static boolean isBalanceKey(String k) {
        if (k == null) return false;
        String l = k.toLowerCase();
        return l.contains("balance") || l.contains("credit") || l.contains("quota")
                || l.contains("remaining") || l.contains("available");
    }

    static boolean isPremiumBool(String k) {
        if (k == null) return false;
        String l = k.toLowerCase();
        return l.contains("premium") || l.contains("vip") || l.contains("pro")
                || l.contains("paid") || l.contains("subscri") || l.contains("entitle")
                || l.contains("is_active") || l.contains("active");
    }

    static boolean isPlanKey(String k) {
        if (k == null) return false;
        String l = k.toLowerCase();
        return l.contains("plan") || l.contains("tier") || l.contains("membership")
                || l.contains("subscri");
    }

    /** توافق Legacy (نفس الحزمة القديمة كانت تستدعي isPremium). */
    public static boolean isPremium(String k) {
        return isPremiumBool(k) || isPlanKey(k) || isBalanceKey(k);
    }
}
