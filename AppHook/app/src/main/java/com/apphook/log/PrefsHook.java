package com.apphook.log;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;

/**
 * Flutter يخزّن عبر shared_preferences بمفاتيح تبدأ عادةً بـ "flutter.".
 * نعترض SharedPreferencesImpl (كلاس النظام — نجلبه من boot loader عند غيابه
 * في loader التطبيق) ونُجبر getBoolean→true / getInt 0→1 للمفاتيح المشتبهة.
 */
public final class PrefsHook {
    private PrefsHook() {}

    private static final String TAG = "AppHook.Prefs";

    static final String[] PREMIUM_KEYS = {
            "premium", "vip", "pro", "paid", "subscri", "member",
            "entitlement", "purchase", "billing", "is_pro",
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
        hookWatch(fw, sp, "getString");
        hookWatch(fw, sp, "getAll");
        SafeHook.log(TAG + ": SharedPreferences hooked");
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
                            if (ret == int.class && Integer.valueOf(0).equals(orig)) {
                                SafeHook.log(TAG + ": getInt(" + key + ") 0->1");
                                SafeHook.countFeature("prefs_int:" + key);
                                return 1;
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
