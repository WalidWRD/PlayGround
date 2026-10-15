package com.apphook.log;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;

/**
 * v5.4 — Flutter يخزّن عبر shared_preferences: booleans/ints بأسماء
 * مفاتيح مشبوهة + كتل JSON كاملة في getString (هذا ما يقرأه الـ plugin
 * فعلياً: plan/balance/isPremium في String واحد). الثلاثة مُرقّعة هنا.
 */
public final class PrefsHook {
    private PrefsHook() {}

    private static final String TAG = "AppHook.Prefs";

    static final String[] PREMIUM_KEYS = {
            "premium", "vip", "pro", "paid", "subscri", "member",
            "entitlement", "purchase", "billing", "is_pro",
            "balance", "credit", "plan", "membership",
            "flutter.subscription", "flutter.premium", "flutter.vip"
    };

    public static void install(XposedInterface fw, ClassLoader cl) {
        Class<?> sp = FingerprintEngine.findClassQuiet(cl,
                "android.app.SharedPreferencesImpl");
        if (sp == null) {
            try {
                sp = Class.forName("android.app.SharedPreferencesImpl");
            } catch (Throwable ignored) {}
        }
        if (sp == null) {
            // fallback: واجهة SharedPreferences — نبحث عن أي تنفيذ عبر getAll
            SafeHook.logSafe(TAG, "SharedPreferencesImpl absent — trying interface watch");
            watchInterface(fw, cl);
            return;
        }
        hookGetter(fw, sp, "getBoolean", boolean.class);
        hookGetter(fw, sp, "getInt", int.class);
        hookJsonString(fw, sp); // v5.4: ترقيع JSON المخزن — الثغرة الحاسمة
        hookWatch(fw, sp, "getAll");
        SafeHook.log(TAG + ": v5.4 hooked (bool/int/string)");
    }

    private static void hookGetter(XposedInterface fw, Class<?> sp,
                                   String name, Class<?> ret) {
        Method m = FingerprintEngine.findMethodByFingerprint(sp, name, ret, 2);
        if (m == null) {
            SafeHook.logSafe(TAG + "." + name, "method-not-found");
            return;
        }
        try {
            fw.hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            Object orig = chain.proceed();
                            String key = (String) chain.getArg(0);
                            if (!isPremiumKey(key)) return orig;
                            if (ret == boolean.class && Boolean.FALSE.equals(orig)) {
                                SafeHook.log(TAG + ": getBoolean(" + key + ") false->true");
                                SafeHook.countFeature("prefs_bool:" + key);
                                return true;
                            }
                            // v5.4: أي رصيد صغير → 9999999 (كان 0→1 فقط)
                            if (ret == int.class && orig instanceof Integer
                                    && ((Integer) orig) < 1000000) {
                                SafeHook.log(TAG + ": getInt(" + key + ") "
                                        + orig + "->9999999");
                                SafeHook.countFeature("prefs_int:" + key);
                                return 9999999;
                            }
                            return orig;
                        } catch (Throwable t) {
                            SafeHook.logError(TAG + "." + name, t);
                            return chain.proceed();
                        }
                    });
            SafeHook.log("Hook OK: Prefs." + name + " -> " + m);
        } catch (Throwable t) {
            SafeHook.logError(TAG + "." + name, t);
        }
    }

    /**
     * v5.4 — الثغرة الحاسمة: shared_preferences plugin يخزن كل شيء
     * كـ JSON String تحت مفتاح واحد. نرقّع المحتوى بنفس قواعد
     * SubscriptionPatch (65 قاعدة) — فقط للنصوص المشتبهة.
     */
    private static void hookJsonString(XposedInterface fw, Class<?> sp) {
        Method m = FingerprintEngine.findMethodByFingerprint(sp, "getString",
                String.class, 2);
        if (m == null) {
            SafeHook.logSafe(TAG + ".getString", "method-not-found");
            return;
        }
        final Method target = m;
        try {
            fw.hook(target)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            Object orig = chain.proceed();
                            if (!(orig instanceof String)) return orig;
                            String s = (String) orig;
                            if (!containsPremium(s)) return orig;
                            String patched = SubscriptionPatch.patchJson(s);
                            if (patched != s) {
                                String key = String.valueOf(chain.getArg(0));
                                SafeHook.log(TAG + ": getString(" + key
                                        + ") JSON patched len=" + s.length());
                                SafeHook.countFeature("prefs_json:" + key);
                                return patched;
                            }
                            return orig;
                        } catch (Throwable t) {
                            SafeHook.logError(TAG + ".getString", t);
                            return chain.proceed();
                        }
                    });
            SafeHook.log("Hook OK: Prefs.getString(JSON) -> " + m);
        } catch (Throwable t) {
            SafeHook.logError(TAG + ".getString", t);
        }
    }

    /** بوابة رخيصة: JSON يستحق الترقيع فقط إن حمل إشارة اشتراك/رصيد */
    static boolean containsPremium(String s) {
        if (s == null || s.length() < 5) return false;
        return s.contains("plan") || s.contains("balance") || s.contains("credit")
                || s.contains("premium") || s.contains("isPremium") || s.contains("vip")
                || s.contains("subscri") || s.contains("membership")
                || s.contains("entitle");
    }

    private static void hookWatch(XposedInterface fw, Class<?> sp, String name) {
        Method m = FingerprintEngine.findMethodByFingerprint(sp, name, null, -1);
        if (m == null) {
            // جرّب التوقيع الشائع getString(String,String)
            m = FingerprintEngine.findMethodByFingerprint(sp, name, String.class, 2);
        }
        if (m == null) return;
        final Method target = m;
        try {
            fw.hook(target)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            Object orig = chain.proceed();
                            try {
                                Object k = chain.getArgs().size() > 0 ? chain.getArg(0) : null;
                                if (k instanceof String && isPremiumKey((String) k)) {
                                    SafeHook.log(TAG + ": " + name + "(" + k + ") -> "
                                            + (orig == null ? "null"
                                            : orig.getClass().getSimpleName()
                                            + (orig instanceof String
                                            ? " len=" + ((String) orig).length() : "")));
                                }
                            } catch (Throwable ignored) {}
                            return orig;
                        } catch (Throwable t) {
                            SafeHook.logError(TAG + "." + name, t);
                            return chain.proceed();
                        }
                    });
            SafeHook.log("Hook OK: Prefs." + name + " -> " + m);
        } catch (Throwable t) {
            SafeHook.logError(TAG + "." + name, t);
        }
    }

    private static void watchInterface(XposedInterface fw, ClassLoader cl) {
        Class<?> c = FingerprintEngine.findClassQuiet(cl,
                "android.content.SharedPreferences");
        if (c == null) {
            SafeHook.logSafe(TAG, "SharedPreferences interface absent");
            return;
        }
        SafeHook.log(TAG + ": interface present — impl hooks deferred safely");
    }

    static boolean isPremiumKey(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase();
        for (String kw : PREMIUM_KEYS) {
            if (lower.contains(kw.toLowerCase())) return true;
        }
        return false;
    }
}
