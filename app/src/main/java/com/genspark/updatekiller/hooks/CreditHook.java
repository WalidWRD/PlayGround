package com.genspark.updatekiller.hooks;

import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.SubscriptionBridge;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.UxLog;
import de.robv.android.xposed.XC_MethodHook;
import java.util.Locale;

/**
 * v2.0.3: infinite credits + free ultra mode.
 *
 * 3 layers:
 *  1) Flutter MethodChannel credit/balance/quota/ultra queries -> infinity payload.
 *  2) SharedPreferences writes (putInt/putLong) for credit keys -> INFINITE.
 *  3) SharedPreferences reads (getInt/getLong) for credit keys -> INFINITE
 *     (covers cached/persisted balances the server already wrote).
 *
 * Gated by Config.forceCredits (credits) and Config.ultraFree (ultra).
 */
public final class CreditHook {
    private CreditHook() {
    }

    public static int install(ClassLoader classLoader) {
        if (!Config.get().forceCredits && !Config.get().ultraFree) {
            UxLog.i("CreditHook: disabled (forceCredits=false, ultraFree=false)");
            return 0;
        }
        int hooks = hookFlutterCredits(classLoader)
                + hookPrefsWrites(classLoader)
                + hookPrefsReads(classLoader);
        UxLog.i("CreditHook: " + hooks + " hook(s) — credits=infinity, ultra=free");
        UpdateEventLogger.log("credits", "infinite credits + free ultra active");
        return hooks;
    }

    static boolean isCreditQuery(String lower) {
        return lower.contains("credit") || lower.contains("balance")
                || lower.contains("quota") || lower.contains("coin")
                || lower.contains("point") || lower.contains("remaining")
                || lower.contains("allowance") || lower.contains("usage")
                || lower.contains("ultra");
    }

    static boolean isCreditKey(String key) {
        if (key == null) {
            return false;
        }
        String k = key.toLowerCase(Locale.ROOT);
        return k.contains("credit") || k.contains("balance") || k.contains("quota")
                || k.contains("coin") || (k.contains("point") && !k.contains("appoint"))
                || k.contains("remaining") || k.contains("allowance")
                || k.contains("ultra");
    }

    private static int hookFlutterCredits(ClassLoader classLoader) {
        Class<?> ch = Reflect.findClass("io.flutter.plugin.common.MethodChannel", classLoader);
        if (ch == null) {
            return 0;
        }
        try {
            int n = Reflect.hookAllNamed(ch, "invokeMethod", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if ((!Config.get().forceCredits && !Config.get().ultraFree)
                                || param.args == null || param.args.length < 1) {
                            return;
                        }
                        String method = String.valueOf(param.args[0]);
                        if (method == null) {
                            return;
                        }
                        if (!isCreditQuery(method.toLowerCase(Locale.ROOT))) {
                            return;
                        }
                        param.setResult(SubscriptionBridge.synthesizeCredits(method));
                        UpdateEventLogger.log("credits", "FORCED infinity for: " + method);
                    } catch (Throwable t) {
                        Guard.record("CreditHook.flutter", t);
                    }
                }
            });
            return Math.max(n, 0);
        } catch (Throwable t) {
            Guard.record("CreditHook.flutter-reflect", t);
            return 0;
        }
    }

    private static int hookPrefsWrites(ClassLoader classLoader) {
        Class<?> ed = Reflect.findClass("android.app.SharedPreferencesImpl$EditorImpl", classLoader);
        if (ed == null) {
            return 0;
        }
        int hooks = 0;
        // putInt / putLong (credits stored as numbers)
        try {
            for (java.lang.reflect.Method m : ed.getDeclaredMethods()) {
                if (!"putInt".equals(m.getName()) && !"putLong".equals(m.getName())) {
                    continue;
                }
                Class<?>[] p = m.getParameterTypes();
                if (p.length != 2 || p[0] != String.class) {
                    continue;
                }
                if (Reflect.hook(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            if (!Config.get().forceCredits || param.args == null || param.args.length < 2) {
                                return;
                            }
                            String key = String.valueOf(param.args[0]);
                            if (!isCreditKey(key)) {
                                return;
                            }
                            java.lang.reflect.Member mm = param.method;
                            Class<?> rt = (mm instanceof java.lang.reflect.Method)
                                    ? ((java.lang.reflect.Method) mm).getReturnType() : null;
                            // putLong vs putInt: keep type-correct infinity
                            if (rt != null && param.method.getName().equals("putLong")) {
                                param.args[1] = (long) SubscriptionBridge.INFINITE_CREDITS;
                            } else {
                                param.args[1] = SubscriptionBridge.INFINITE_CREDITS;
                            }
                            UpdateEventLogger.log("credits", "FORCED write '" + key + "'=inf");
                        } catch (Throwable t) {
                            Guard.record("CreditHook.write", t);
                        }
                    }
                })) {
                    hooks++;
                }
            }
        } catch (Throwable t) {
            Guard.record("CreditHook.write-reflect", t);
        }
        // putBoolean ultra flags -> true ; putString ultra/mode -> pro
        try {
            for (java.lang.reflect.Method m : ed.getDeclaredMethods()) {
                if (!"putBoolean".equals(m.getName())) {
                    continue;
                }
                Class<?>[] p = m.getParameterTypes();
                if (p.length != 2 || p[0] != String.class) {
                    continue;
                }
                if (Reflect.hook(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            if (!Config.get().ultraFree || param.args == null || param.args.length < 2) {
                                return;
                            }
                            String key = String.valueOf(param.args[0]);
                            if (key == null) {
                                return;
                            }
                            String lk = key.toLowerCase(Locale.ROOT);
                            if (lk.contains("ultra")) {
                                param.args[1] = Boolean.TRUE;
                                UpdateEventLogger.log("credits", "FORCED ultra '" + key + "'=true");
                            }
                        } catch (Throwable t) {
                            Guard.record("CreditHook.ultra", t);
                        }
                    }
                })) {
                    hooks++;
                    break; // one putBoolean hook is enough (key-checked inside)
                }
            }
        } catch (Throwable t) {
            Guard.record("CreditHook.ultra-reflect", t);
        }
        return hooks;
    }

    private static int hookPrefsReads(ClassLoader classLoader) {
        // SharedPreferencesImpl.getInt/getLong — force cached balances to infinity.
        Class<?> sp = Reflect.findClass("android.app.SharedPreferencesImpl", classLoader);
        if (sp == null) {
            sp = Reflect.findClass("android.content.SharedPreferences", classLoader);
        }
        if (sp == null) {
            return 0;
        }
        int hooks = 0;
        try {
            for (java.lang.reflect.Method m : sp.getMethods()) {
                String n = m.getName();
                if (!"getInt".equals(n) && !"getLong".equals(n)) {
                    continue;
                }
                Class<?>[] p = m.getParameterTypes();
                if (p.length < 1 || p[0] != String.class) {
                    continue;
                }
                if (Reflect.hook(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (!Config.get().forceCredits || param.args == null || param.args.length < 1) {
                                return;
                            }
                            String key = String.valueOf(param.args[0]);
                            if (!isCreditKey(key)) {
                                return;
                            }
                            if ("getLong".equals(param.method.getName())) {
                                param.setResult((long) SubscriptionBridge.INFINITE_CREDITS);
                            } else {
                                param.setResult(SubscriptionBridge.INFINITE_CREDITS);
                            }
                        } catch (Throwable t) {
                            Guard.record("CreditHook.read", t);
                        }
                    }
                })) {
                    hooks++;
                }
            }
        } catch (Throwable t) {
            Guard.record("CreditHook.read-reflect", t);
        }
        return hooks;
    }
}
