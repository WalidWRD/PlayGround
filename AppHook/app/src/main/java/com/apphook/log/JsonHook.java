package com.apphook.log;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * اعتراض طبقة JSON (قبل وصول استجابة الخادم لـ Flutter/Dart).
 *  - Gson.fromJson(String, Class): ترقيع نص JSON المدخل أولاً (v4.3:
 *    نفس 30+ قاعدة SubscriptionPatch) ثم ترقيع حقول الكائن الناتج.
 *  - Moshi: رصد وجود (توقيع متغير).
 *  - org.json: getBoolean→true / getInt (balance→999999) /
 *    getString ("free"→"plus" لحقول العضوية).
 */
public final class JsonHook {
    private JsonHook() {}

    private static final String TAG = "AppHook.Json";

    public static void install(XposedInterface fw, ClassLoader cl) {
        hookGson(fw, cl);
        watchMoshi(cl);
        hookOrgJson(fw, cl);
        hookOrgJsonInt(fw, cl);
        hookOrgJsonString(fw, cl);
    }

    private static void hookGson(XposedInterface fw, ClassLoader cl) {
        Class<?> g = FingerprintEngine.findClassQuiet(cl, "com.google.gson.Gson");
        if (g == null) {
            SafeHook.logSafe(TAG + ".Gson", "absent");
            return;
        }
        try {
            boolean any = false;
            for (Method m : g.getDeclaredMethods()) {
                if (!m.getName().equals("fromJson")) continue;
                if (m.getParameterTypes().length != 2) continue;
                if (m.getParameterTypes()[0] != String.class) continue;
                try {
                    m.setAccessible(true);
                    final Method target = m;
                    fw.hook(target)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                try {
                                    // v4.3: رقّع نص JSON المدخل قبل التحليل
                                    Object in0 = chain.getArg(0);
                                    if (in0 instanceof String) {
                                        String patched =
                                                SubscriptionPatch.patchJson((String) in0);
                                        if (patched != in0) {
                                            SafeHook.log("[Json] *PATCHED[gson:"
                                                    + typeOf(chain.getArg(1)) + "]");
                                            SafeHook.countFeature("gson-input");
                                            return chain.proceed(
                                                    new Object[]{patched, chain.getArg(1)});
                                        }
                                    }
                                    Object obj = chain.proceed();
                                    if (obj != null) {
                                        String cn = obj.getClass().getSimpleName().toLowerCase();
                                        if (cn.contains("subscri") || cn.contains("premium")
                                                || cn.contains("user") || cn.contains("entitle")) {
                                            SafeHook.log(TAG + ": Gson parsed "
                                                    + obj.getClass().getSimpleName() + " — patching");
                                            patchObject(obj);
                                        }
                                    }
                                    return obj;
                                } catch (Throwable t) {
                                    SafeHook.logError(TAG + ".Gson", t);
                                    return chain.proceed();
                                }
                            });
                    SafeHook.log("Hook OK: Gson.fromJson -> " + m);
                    any = true;
                } catch (Throwable t) {
                    SafeHook.logError(TAG + ".Gson", t);
                }
            }
            if (!any) SafeHook.logSafe(TAG + ".Gson.fromJson", "signature differs");
        } catch (Throwable t) {
            SafeHook.logError(TAG + ".Gson-scan", t);
        }
    }

    private static void watchMoshi(ClassLoader cl) {
        Class<?> c = FingerprintEngine.findClassQuiet(cl, "com.squareup.moshi.Moshi");
        if (c == null) {
            SafeHook.logSafe(TAG + ".Moshi", "absent");
            return;
        }
        SafeHook.log(TAG + ": Moshi present — monitoring only");
    }

    private static void hookOrgJson(XposedInterface fw, ClassLoader cl) {
        Class<?> j = FingerprintEngine.findClassQuiet(cl, "org.json.JSONObject");
        if (j == null) {
            try {
                j = Class.forName("org.json.JSONObject");
            } catch (Throwable ignored) {}
        }
        if (j == null) {
            SafeHook.logSafe(TAG + ".org.json", "absent");
            return;
        }
        Method m = FingerprintEngine.findMethodByFingerprint(j, "getBoolean",
                boolean.class, 1);
        if (m == null) {
            SafeHook.logSafe(TAG + ".JSONObject.getBoolean", "method-not-found");
            return;
        }
        final Method target = m;
        try {
            fw.hook(target)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            Object orig = chain.proceed();
                            String key = (String) chain.getArg(0);
                            if (isPremiumKey(key) && Boolean.FALSE.equals(orig)) {
                                SafeHook.countFeature("json:" + key);
                                return true;
                            }
                            return orig;
                        } catch (Throwable t) {
                            SafeHook.logError(TAG + ".getBoolean", t);
                            return chain.proceed();
                        }
                    });
            SafeHook.log("Hook OK: JSONObject.getBoolean -> " + m);
        } catch (Throwable t) {
            SafeHook.logError(TAG + ".getBoolean", t);
        }
    }

    /** v4.3: getInt — balance/credits الصغيرة → 999999 */
    private static void hookOrgJsonInt(XposedInterface fw, ClassLoader cl) {
        Class<?> j = orgJson(cl);
        if (j == null) {
            SafeHook.logSafe(TAG + ".org.json-int", "absent");
            return;
        }
        Method m = FingerprintEngine.findMethodByFingerprint(j, "getInt", int.class, 1);
        if (m == null) {
            SafeHook.logSafe(TAG + ".JSONObject.getInt", "method-not-found");
            return;
        }
        final Method target = m;
        try {
            fw.hook(target)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            Object orig = chain.proceed();
                            String key = (String) chain.getArg(0);
                            Integer forced = SubscriptionPatch.patchInt(key, orig);
                            if (forced != null) {
                                SafeHook.log(TAG + ": getInt(" + key + ") "
                                        + orig + "->" + forced);
                                SafeHook.countFeature("json-int:" + key);
                                return forced;
                            }
                            return orig;
                        } catch (Throwable t) {
                            SafeHook.logError(TAG + ".getInt", t);
                            return chain.proceed();
                        }
                    });
            SafeHook.log("Hook OK: JSONObject.getInt -> " + m);
        } catch (Throwable t) {
            SafeHook.logError(TAG + ".getInt", t);
        }
    }

    /** v4.3: getString — "free"→"plus" لحقول العضوية/الخطة */
    private static void hookOrgJsonString(XposedInterface fw, ClassLoader cl) {
        Class<?> j = orgJson(cl);
        if (j == null) {
            SafeHook.logSafe(TAG + ".org.json-str", "absent");
            return;
        }
        Method m = FingerprintEngine.findMethodByFingerprint(j, "getString",
                String.class, 1);
        if (m == null) {
            SafeHook.logSafe(TAG + ".JSONObject.getString", "method-not-found");
            return;
        }
        final Method target = m;
        try {
            fw.hook(target)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            Object orig = chain.proceed();
                            String key = (String) chain.getArg(0);
                            String forced = SubscriptionPatch.patchString(key, orig);
                            if (forced != null) {
                                SafeHook.log(TAG + ": getString(" + key + ") "
                                        + orig + "->" + forced);
                                SafeHook.countFeature("json-str:" + key);
                                return forced;
                            }
                            return orig;
                        } catch (Throwable t) {
                            SafeHook.logError(TAG + ".getString", t);
                            return chain.proceed();
                        }
                    });
            SafeHook.log("Hook OK: JSONObject.getString -> " + m);
        } catch (Throwable t) {
            SafeHook.logError(TAG + ".getString", t);
        }
    }

    private static Class<?> orgJson(ClassLoader cl) {
        Class<?> j = FingerprintEngine.findClassQuiet(cl, "org.json.JSONObject");
        if (j == null) {
            try {
                j = Class.forName("org.json.JSONObject");
            } catch (Throwable ignored) {}
        }
        return j;
    }

    private static String typeOf(Object o) {
        return o == null ? "null" : o.toString();
    }
    static void patchObject(Object obj) {
        try {
            for (Field f : obj.getClass().getDeclaredFields()) {
                String n = f.getName().toLowerCase();
                if (!n.contains("premium") && !n.contains("vip") && !n.contains("pro")
                        && !n.contains("paid") && !n.contains("subscri")
                        && !n.contains("entitle") && !n.contains("member")) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (v instanceof Boolean && !((Boolean) v)) {
                        f.set(obj, Boolean.TRUE);
                        SafeHook.countFeature("field:" + f.getName());
                    } else if (v instanceof Integer && ((Integer) v) == 0) {
                        f.set(obj, 1);
                        SafeHook.countFeature("field:" + f.getName());
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    static boolean isPremiumKey(String key) {
        if (key == null) return false;
        String l = key.toLowerCase();
        return l.contains("premium") || l.contains("vip") || l.contains("pro")
                || l.contains("paid") || l.contains("subscri") || l.contains("entitle")
                || l.contains("member");
    }
}
