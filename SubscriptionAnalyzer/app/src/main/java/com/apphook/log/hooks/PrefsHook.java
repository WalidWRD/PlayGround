package com.apphook.log.hooks;

import com.apphook.log.Config;
import com.apphook.log.FingerprintEngine;
import com.apphook.log.SafeHook;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;

/**
 * Features #10-11: SharedPreferences — تسجيل دائم + ترقيع اختياري (F006).
 * getBoolean→true / getInt الصغير→9999999 / getString-JSON بنفس القواعد.
 */
public final class PrefsHook {
    private PrefsHook() {}

    public static void install(XposedInterface fw, ClassLoader cl) {
        Class<?> sp = FingerprintEngine.findClassQuiet(cl,
                "android.app.SharedPreferencesImpl");
        if (sp == null) {
            try {
                sp = Class.forName("android.app.SharedPreferencesImpl");
            } catch (Throwable ignored) {}
        }
        if (sp == null) {
            SafeHook.logSafe("Prefs", "SharedPreferencesImpl absent");
            return;
        }
        Method b = FingerprintEngine.findMethodByFingerprint(sp,
                "getBoolean", boolean.class, 2);
        HookKit.hookOne(fw, b, "Prefs.getBoolean", chain -> {
            Object orig = chain.proceed();
            try {
                String k = (String) chain.getArg(0);
                Object patched = orig;
                if (Config.PATCH_PREFS && isPremiumKey(k) && Boolean.FALSE.equals(orig)) {
                    patched = true;
                    SafeHook.countOpen("prefs_bool:" + k, "F006");
                }
                if (isPremiumKey(k)) {
                    SafeHook.recordCall("Prefs", "getBoolean(" + k + ")", 2, orig, patched);
                    if (patched != orig) return patched;
                }
            } catch (Throwable t) {
                SafeHook.logError("Prefs.getBoolean", t);
            }
            return orig;
        });
        Method gi = FingerprintEngine.findMethodByFingerprint(sp,
                "getInt", int.class, 2);
        HookKit.hookOne(fw, gi, "Prefs.getInt", chain -> {
            Object orig = chain.proceed();
            try {
                String k = (String) chain.getArg(0);
                Object patched = orig;
                if (Config.PATCH_PREFS && isPremiumKey(k) && orig instanceof Integer
                        && ((Integer) orig) < 1000000) {
                    patched = 9999999;
                    SafeHook.countOpen("prefs_int:" + k, "F006");
                }
                if (isPremiumKey(k)) {
                    SafeHook.recordCall("Prefs", "getInt(" + k + ")", 2, orig, patched);
                    if (patched != orig) return patched;
                }
            } catch (Throwable t) {
                SafeHook.logError("Prefs.getInt", t);
            }
            return orig;
        });
        Method gs = FingerprintEngine.findMethodByFingerprint(sp,
                "getString", String.class, 2);
        HookKit.hookOne(fw, gs, "Prefs.getString", chain -> {
            Object orig = chain.proceed();
            try {
                String k = (String) chain.getArg(0);
                if (!(orig instanceof String)) return orig;
                String s = (String) orig;
                boolean interesting = isPremiumKey(k) || containsPremium(s);
                if (!interesting) return orig;
                Object patched = orig;
                if (Config.PATCH_PREFS) {
                    String q = com.apphook.log.SubscriptionPatch.patchJson(s);
                    if (q != s) {
                        patched = q;
                        SafeHook.countOpen("prefs_str:" + k, "F006");
                    }
                }
                SafeHook.recordCall("Prefs", "getString(" + k + ")", 2,
                        abbrev(s), abbrev(patched));
                return patched;
            } catch (Throwable t) {
                SafeHook.logError("Prefs.getString", t);
            }
            return orig;
        });
        SafeHook.log("Prefs: hooked (patch=" + Config.PATCH_PREFS + ")");
    }

    public static boolean isPremiumKey(String k) {
        if (k == null) return false;
        String l = k.toLowerCase();
        return l.contains("premium") || l.contains("vip") || l.contains("pro")
                || l.contains("paid") || l.contains("subscri") || l.contains("entitle")
                || l.contains("balance") || l.contains("credit") || l.contains("plan")
                || l.contains("membership");
    }

    public static boolean containsPremium(String s) {
        if (s == null || s.length() < 5) return false;
        return s.contains("plan") || s.contains("balance") || s.contains("credit")
                || s.contains("premium") || s.contains("isPremium") || s.contains("vip")
                || s.contains("subscri") || s.contains("membership");
    }

    private static String abbrev(Object o) {
        if (o == null) return "null";
        String s;
        try {
            s = String.valueOf(o);
        } catch (Throwable ignored) {
            return "?";
        }
        return s.length() > 100 ? s.substring(0, 100) + "..." : s;
    }
}
