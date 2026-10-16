package com.apphook.log.hooks;

import com.apphook.log.Config;
import com.apphook.log.FingerprintEngine;
import com.apphook.log.ProxyMarker;
import com.apphook.log.ReflectCall;
import com.apphook.log.SafeHook;

import io.github.libxposed.api.XposedInterface;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Feature #6: Flutter MethodChannel — v6.3 active patch.
 * تسجيل مُرشَّح (interesting only) + ترقيع Result بالقيم الموحّدة.
 * بلا Proxy، بلا استبدال وسائط.
 */
public final class FlutterChannelHook {
    private FlutterChannelHook() {}

    private static final String TAG = "Flutter";
    private static final Set<String> seenHandlers =
            Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> seenResults =
            Collections.synchronizedSet(new HashSet<String>());

    public static void install(XposedInterface fw, ClassLoader cl) {
        Class<?> mc = FingerprintEngine.findClassQuiet(cl,
                "io.flutter.plugin.common.MethodChannel");
        if (mc == null) {
            SafeHook.logSafe(TAG, "not a Flutter app or engine absent");
            return;
        }
        // Dart → Native (تسجيل المثير فقط)
        HookKit.hookAll(fw, mc, "invokeMethod", 3, 3, TAG + ".out", chain -> {
            try {
                Object m = chain.getArg(0);
                Object a = chain.getArg(1);
                Object res = chain.getArg(2);
                if (m instanceof String && isInteresting((String) m, a)) {
                    SafeHook.recordCall(TAG + ".out", (String) m,
                            3, abbrev(a), abbrev(a));
                }
                if (res != null) hookResultFor(fw, res.getClass());
            } catch (Throwable t) {
                SafeHook.logError(TAG + ".out", t);
            }
            return chain.proceed();
        });
        // Native → Dart (ربط handler)
        HookKit.hookAll(fw, mc, "setMethodCallHandler", 1, 1,
                TAG + ".register", chain -> {
                    try {
                        Object h = chain.getArg(0);
                        if (h != null && seenHandlers.add(h.getClass().getName())) {
                            SafeHook.log(TAG + ": handler " + h.getClass().getName());
                            hookHandler(fw, h.getClass());
                        }
                    } catch (Throwable t) {
                        SafeHook.logError(TAG + ".register", t);
                    }
                    return chain.proceed();
                });
        SafeHook.log(TAG + ": hooked v6.3 (active patch)");
    }

    private static void hookHandler(XposedInterface fw, Class<?> cls) {
        if (cls == null || ProxyMarker.isProxy(cls)) return;
        HookKit.hookAll(fw, cls, "onMethodCall", 2, 2, TAG + ".in", chain -> {
            try {
                Object call = chain.getArg(0);
                Object res = chain.getArg(1);
                if (call == null || res == null) return chain.proceed();
                String method;
                Object args = null;
                try {
                    method = String.valueOf(ReflectCall.call(call, "method"));
                    args = ReflectCall.call(call, "arguments");
                } catch (Throwable ignored) {
                    return chain.proceed();
                }
                if (isInteresting(method, args)) {
                    int argc = args instanceof Object[] ? ((Object[]) args).length : 0;
                    SafeHook.recordCall(TAG + ".in", method, argc, abbrev(args), abbrev(args));
                }
                hookResultFor(fw, res.getClass());
            } catch (Throwable t) {
                SafeHook.logError(TAG + ".in", t);
            }
            return chain.proceed();
        });
    }

    private static void hookResultFor(XposedInterface fw, Class<?> cls) {
        if (cls == null || ProxyMarker.isProxy(cls)) return;
        if (!seenResults.add(cls.getName())) return;
        HookKit.hookAll(fw, cls, "success", 1, 1, TAG + ".res", chain -> {
            try {
                Object orig = chain.getArg(0);
                Object patched = orig;
                if (Config.PATCH_FLUTTER) {
                    patched = patch(orig);
                    if (patched != orig) {
                        SafeHook.countOpen("flutter.result", "F004");
                    }
                }
                if (looksRelevant(orig)) {
                    SafeHook.recordCall(TAG + ".res", "success", 1,
                            abbrev(orig), abbrev(patched));
                }
                if (patched != orig) return chain.proceed(new Object[]{patched});
            } catch (Throwable t) {
                SafeHook.logError(TAG + ".res", t);
            }
            return chain.proceed();
        });
    }

    private static boolean isInteresting(String method, Object args) {
        if (method == null) return false;
        String l = method.toLowerCase();
        if (l.contains("subscri") || l.contains("premium") || l.contains("plan")
                || l.contains("member") || l.contains("balance") || l.contains("credit")
                || l.contains("user") || l.contains("quota") || l.contains("entitle")) {
            return true;
        }
        if (args != null) {
            String s = String.valueOf(args);
            if (s.length() > 5 && (s.contains("plan") || s.contains("balance")
                    || s.contains("premium") || s.contains("credit"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksRelevant(Object v) {
        if (v == null) return false;
        String s = String.valueOf(v);
        if (s.length() < 5) return false;
        return s.contains("plan") || s.contains("balance") || s.contains("credit")
                || s.contains("premium") || s.contains("member") || s.contains("subscri");
    }

    public static Object patch(Object v) {
        try {
            if (v instanceof String) return patchJson((String) v);
            if (v instanceof Map) {
                patchMap((Map<?, ?>) v);
                return v;
            }
            if (v instanceof List) {
                for (Object item : (List<?>) v) patch(item);
                return v;
            }
        } catch (Throwable ignored) {}
        return v;
    }

    static String patchJson(String s) {
        if (s == null || s.length() < 5) return s;
        String p = s;
        String BV = Config.FROZEN_BALANCE_STR;
        String F = Config.FORCED_PLAN;
        try {
            p = p.replaceAll("\"balance\":\\s*-?[0-9]+", "\"balance\":" + BV);
            p = p.replaceAll("\"credit_balance\":\\s*-?[0-9]+", "\"credit_balance\":" + BV);
            p = p.replaceAll("\"credits\":\\s*-?[0-9]+(?=[,}])", "\"credits\":" + BV);
            p = p.replaceAll("\"credit\":\\s*-?[0-9]+", "\"credit\":" + BV);
            p = p.replaceAll("\"remaining\":\\s*-?[0-9]+", "\"remaining\":" + BV);
            p = p.replaceAll("\"available\":\\s*-?[0-9]+", "\"available\":" + BV);
            p = p.replaceAll("\"consumed\":\\s*[0-9]+", "\"consumed\":0");
            p = p.replaceAll("\"used\":\\s*[0-9]+", "\"used\":0");
            p = p.replaceAll("\"spent\":\\s*[0-9]+", "\"spent\":0");
            p = p.replaceAll("\"plan\":\\s*\"free\"", "\"plan\":\"" + F + "\"");
            p = p.replaceAll("\"subType\":\\s*\"free\"", "\"subType\":\"" + F + "\"");
            p = p.replaceAll("\"membershipType\":\\s*\"free\"", "\"membershipType\":\"" + F + "\"");
            p = p.replaceAll("\"tier\":\\s*\"free\"", "\"tier\":\"" + F + "\"");
            p = p.replaceAll("\"status\":\\s*\"never_purchased\"", "\"status\":\"active\"");
            p = p.replaceAll("\"is_active\":\\s*false", "\"is_active\":true");
            p = p.replaceAll("\"isPremium\":\\s*false", "\"isPremium\":true");
            p = p.replaceAll("\"isVip\":\\s*false", "\"isVip\":true");
            p = p.replaceAll("\"isPro\":\\s*false", "\"isPro\":true");
            p = p.replaceAll("\"premium\":\\s*false", "\"premium\":true");
            p = p.replaceAll("\"vip\":\\s*false", "\"vip\":true");
        } catch (Throwable ignored) {}
        return p;
    }

    static void patchMap(Map<?, ?> m) {
        try {
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) m;
            for (Map.Entry<Object, Object> e : map.entrySet()) {
                String k = String.valueOf(e.getKey()).toLowerCase();
                Object v = e.getValue();
                try {
                    if (v instanceof Number) {
                        if (k.contains("balance") || k.contains("credit")
                                || k.contains("remaining") || k.contains("available")) {
                            e.setValue(Config.FROZEN_BALANCE);
                        } else if (k.contains("consumed") || k.contains("used")
                                || k.contains("spent")) {
                            e.setValue(0);
                        }
                    } else if (v instanceof String) {
                        String sv = (String) v;
                        if ("free".equals(sv) && (k.contains("plan") || k.contains("type")
                                || k.contains("membership") || k.contains("tier"))) {
                            e.setValue(Config.FORCED_PLAN);
                        } else if ("never_purchased".equals(sv) || "inactive".equals(sv)) {
                            e.setValue(Config.FORCED_STATUS);
                        }
                    } else if (v instanceof Boolean) {
                        if (k.contains("premium") || k.contains("vip")
                                || k.contains("pro") || k.contains("active")
                                || k.contains("paid") || k.contains("subscri")) {
                            e.setValue(Boolean.TRUE);
                        }
                    } else if (v instanceof Map) {
                        patchMap((Map<?, ?>) v);
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private static String abbrev(Object o) {
        if (o == null) return "null";
        String s;
        try {
            s = String.valueOf(o);
        } catch (Throwable ignored) {
            return "?";
        }
        return s.length() > 120 ? s.substring(0, 120) + "..." : s;
    }
}
