package com.genspark.updatekiller.hooks;

import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.SubscriptionBridge;
import com.genspark.updatekiller.UxLog;
import de.robv.android.xposed.XC_MethodHook;
import java.util.Locale;

/**
 * Server verification bypass — level 2 (parse-time).
 *
 * HttpHook patches raw bodies, but the app may also read cached/persisted
 * JSON or use stacks we don't hook (Cronet, Dart http, WebView bridge).
 * This hook spoofs org.json getters directly, so ANY server payload the
 * client parses reports lifetime subscription:
 *  - optBoolean/getBoolean(isSubscribed/isPro/...) -> true
 *  - optString/getString(status) -> "active", (plan) -> "pro"
 *  - optLong/optInt/getLong (expiry) -> 2100-01-01
 *
 * Gated by forceSubscribed/forceCredits/ultraFree. Safe: only touches subscription-looking
 * keys, everything else passes through untouched.
 */
public final class ServerCheckBypassHook {
    private ServerCheckBypassHook() {
    }

    public static int install(ClassLoader classLoader) {
        if (!Config.get().forceSubscribed && !Config.get().forceCredits && !Config.get().ultraFree) {
            UxLog.i("ServerCheckBypassHook: disabled (all spoof flags false)");
            return 0;
        }
        int hooks = 0;
        try {
            Class<?> json = Reflect.findClass("org.json.JSONObject", classLoader);
            if (json == null) {
                try {
                    json = Class.forName("org.json.JSONObject");
                } catch (Throwable ignored) {
                }
            }
            if (json == null) {
                UxLog.i("ServerCheckBypassHook: JSONObject not found");
                return 0;
            }
            final Class<?> J = json;

            // optBoolean(String[, boolean]) — most server checks use opt*
            for (java.lang.reflect.Method m : J.getDeclaredMethods()) {
                String n = m.getName();
                Class<?>[] p = m.getParameterTypes();
                Class<?> r = m.getReturnType();
                try {
                    if (("optBoolean".equals(n) || "getBoolean".equals(n))
                            && p.length >= 1 && p[0] == String.class
                            && (r == boolean.class || r == Boolean.class)) {
                        if (Reflect.hook(m, boolHook())) {
                            hooks++;
                        }
                    } else if (("optString".equals(n) || "getString".equals(n))
                            && p.length >= 1 && p[0] == String.class
                            && r == String.class) {
                        if (Reflect.hook(m, stringHook())) {
                            hooks++;
                        }
                    } else if (("optLong".equals(n) || "getLong".equals(n)
                            || "optInt".equals(n) || "getInt".equals(n))
                            && p.length >= 1 && p[0] == String.class
                            && (r == long.class || r == int.class
                            || r == Long.class || r == Integer.class)) {
                        if (Reflect.hook(m, longHook())) {
                            hooks++;
                        }
                    }
                } catch (Throwable t) {
                    Guard.record("ServerCheckBypassHook.one", t);
                }
            }
        } catch (Throwable t) {
            Guard.record("ServerCheckBypassHook.install", t);
        }
        UxLog.i("ServerCheckBypassHook: " + hooks + " hook(s) — server verification bypassed (lifetime)");
        return hooks;
    }

    private static XC_MethodHook boolHook() {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (!Config.get().forceSubscribed && !Config.get().forceCredits && !Config.get().ultraFree) {
                        return;
                    }
                    if (param.args == null || param.args.length < 1) {
                        return;
                    }
                    String key = String.valueOf(param.args[0]);
                    if (!SubscriptionBridge.isServerSpoofKey(key)) {
                        return;
                    }
                    String lk = key.toLowerCase(Locale.ROOT);
                    // status-like keys are strings, not booleans — skip here.
                    if (lk.equals("status") || lk.contains("status") || lk.equals("plan")
                            || lk.equals("tier") || lk.contains("expir")) {
                        return;
                    }
                    Object res = param.getResult();
                    if (res instanceof Boolean && (Boolean) res) {
                        return; // already true
                    }
                    param.setResult(true);
                } catch (Throwable t) {
                    Guard.record("ServerCheckBypassHook.bool", t);
                }
            }
        };
    }

    private static XC_MethodHook stringHook() {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (!Config.get().forceSubscribed && !Config.get().forceCredits && !Config.get().ultraFree) {
                        return;
                    }
                    if (param.args == null || param.args.length < 1) {
                        return;
                    }
                    String key = String.valueOf(param.args[0]);
                    if (!SubscriptionBridge.isServerSpoofKey(key)) {
                        return;
                    }
                    String lk = key.toLowerCase(Locale.ROOT);
                    Object res = param.getResult();
                    String cur = res == null ? "" : String.valueOf(res);
                    if (lk.equals("status") || lk.contains("status") || lk.equals("state")) {
                        if (!"active".equals(cur)) {
                            param.setResult("active");
                        }
                    } else if (lk.equals("plan") || lk.equals("tier") || lk.equals("entitlement")) {
                        if (!"pro".equalsIgnoreCase(cur)) {
                            param.setResult("pro");
                        }
                    } else if (lk.equals("planid") || lk.equals("plan_id")) {
                        if (!"pro_lifetime".equals(cur)) {
                            param.setResult("pro_lifetime");
                        }
                    } else if (lk.contains("expir") || lk.contains("validuntil")
                            || lk.contains("valid_until") || lk.contains("expiration")) {
                        if (!SubscriptionBridge.LIFETIME_EXPIRY_ISO.equals(cur)
                                && !String.valueOf(SubscriptionBridge.LIFETIME_EXPIRY_MS).equals(cur)
                                && !String.valueOf(SubscriptionBridge.LIFETIME_EXPIRY_SEC).equals(cur)) {
                            param.setResult(SubscriptionBridge.LIFETIME_EXPIRY_ISO);
                        }
                    }
                } catch (Throwable t) {
                    Guard.record("ServerCheckBypassHook.str", t);
                }
            }
        };
    }

    private static XC_MethodHook longHook() {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (!Config.get().forceSubscribed && !Config.get().forceCredits && !Config.get().ultraFree) {
                        return;
                    }
                    if (param.args == null || param.args.length < 1) {
                        return;
                    }
                    String key = String.valueOf(param.args[0]);
                    if (!SubscriptionBridge.isServerSpoofKey(key)) {
                        return;
                    }
                    String lk = key.toLowerCase(Locale.ROOT);
                    if (!(lk.contains("expir") || lk.contains("periodend")
                            || lk.equals("exp") || lk.contains("valid"))) {
                        return;
                    }
                    Object res = param.getResult();
                    long cur = 0L;
                    try {
                        cur = Long.parseLong(String.valueOf(res));
                    } catch (Throwable ignored) {
                        return;
                    }
                    if (cur == SubscriptionBridge.LIFETIME_EXPIRY_MS
                            || cur == SubscriptionBridge.LIFETIME_EXPIRY_SEC) {
                        return;
                    }
                    java.lang.reflect.Member mm = param.method;
                    Class<?> rt = (mm instanceof java.lang.reflect.Method)
                            ? ((java.lang.reflect.Method) mm).getReturnType() : null;
                    if (rt != null && (rt == int.class || rt == Integer.class)) {
                        // int can't hold 2100-ms; clamp to MAX (still ~2038, app treats as far future)
                        param.setResult(Integer.MAX_VALUE);
                    } else {
                        // ms (>1e12) vs sec heuristic
                        param.setResult(cur > 100000000000L
                                ? SubscriptionBridge.LIFETIME_EXPIRY_MS
                                : SubscriptionBridge.LIFETIME_EXPIRY_SEC);
                    }
                } catch (Throwable t) {
                    Guard.record("ServerCheckBypassHook.long", t);
                }
            }
        };
    }
}
