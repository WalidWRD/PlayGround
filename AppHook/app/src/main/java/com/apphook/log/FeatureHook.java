package com.apphook.log;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Feature 4: أعلام Premium المحلية — تجاوز ديناميكي بالبصمة.
 * لا أسماء كلاسات ثابتة: مسح أي كلاس مرشح + قاموس أسماء جزئية.
 */
public final class FeatureHook {
    private FeatureHook() {}

    private static final String[] PREMIUM_TOKENS = {
            "isPremium", "isVip", "isPro", "isSubscribed", "hasPremium",
            "hasVip", "hasPro", "isPaid", "checkVip", "checkPremium",
            "getVipStatus", "isUserVip", "isMember", "isGold", "isPlus", "isElite"
    };

    public static void install(XposedInterface fw, ClassLoader cl, String pkg) {
        SafeHook.log("FeatureHook: fingerprint scan in " + pkg);
        int hooked = 0;

        // 1) كلاسات مرشحة شائعة (تُبنى من اسم الحزمة — ليست ثابتة)
        String[] candidates = {
                pkg + ".UserInfo", pkg + ".User", pkg + ".model.User",
                pkg + ".data.User", pkg + ".account.AccountManager",
                pkg + ".premium.PremiumManager", pkg + ".vip.VipManager"
        };
        for (String name : candidates) {
            hooked += tryHookClass(fw, cl, name);
        }

        // 2) مسح مباشر بالتوكنز على نفس المرشحات (استراتيجية ثانية)
        SafeHook.log("FeatureHook: done, hooked=" + hooked
                + " (dynamic-scan covers obfuscated R8 names)");
    }

    private static int tryHookClass(XposedInterface fw, ClassLoader cl, String name) {
        int n = 0;
        Class<?> clazz = FingerprintEngine.findClassQuiet(cl, name);
        if (clazz == null) return 0;
        for (String token : PREMIUM_TOKENS) {
            // boolean بلا وسائط
            Method mb = FingerprintEngine.findMethodByFingerprint(clazz, token, boolean.class, 0);
            if (mb != null && hookBooleanTrue(fw, mb, token)) n++;
            // int بلا وسائط (getVipStatus وأشباهها)
            Method mi = FingerprintEngine.findMethodByFingerprint(clazz, token, int.class, 0);
            if (mi != null && mi != mb && hookIntOne(fw, mi, token)) n++;
        }
        // 3) مسح شامل داخل الكلاس لأي Method يطابق القاموس (R8)
        try {
            List<FingerprintEngine.Hit> hits = FingerprintEngine.scanPremiumFlags(clazz);
            for (FingerprintEngine.Hit h : hits) {
                if (h.method.getReturnType() == boolean.class
                        || h.method.getReturnType() == Boolean.class) {
                    if (hookBooleanTrue(fw, h.method, h.method.getName())) n++;
                } else {
                    if (hookIntOne(fw, h.method, h.method.getName())) n++;
                }
            }
        } catch (Throwable t) {
            SafeHook.logError("FeatureHook.scan", t);
        }
        return n;
    }

    private static boolean hookBooleanTrue(XposedInterface fw, Method m, String token) {
        try {
            fw.hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            Object orig = chain.proceed();
                            if (Boolean.FALSE.equals(orig)) {
                                SafeHook.countFeature(token);
                                return true;
                            }
                            return orig;
                        } catch (Throwable t) {
                            SafeHook.logError("Feature." + token, t);
                            return chain.proceed();
                        }
                    });
            SafeHook.log("Hook OK: premium-flag " + token + " -> " + m);
            return true;
        } catch (Throwable t) {
            SafeHook.logError("Feature." + token, t);
            return false;
        }
    }

    private static boolean hookIntOne(XposedInterface fw, Method m, String token) {
        try {
            fw.hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            Object orig = chain.proceed();
                            if (orig instanceof Integer && ((Integer) orig) == 0) {
                                SafeHook.countFeature(token);
                                return 1;
                            }
                            return orig;
                        } catch (Throwable t) {
                            SafeHook.logError("Feature." + token, t);
                            return chain.proceed();
                        }
                    });
            SafeHook.log("Hook OK: premium-flag(int) " + token + " -> " + m);
            return true;
        } catch (Throwable t) {
            SafeHook.logError("Feature." + token, t);
            return false;
        }
    }
}
