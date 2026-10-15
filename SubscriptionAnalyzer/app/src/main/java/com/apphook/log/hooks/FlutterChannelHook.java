package com.apphook.log.hooks;

import com.apphook.log.Config;
import com.apphook.log.FingerprintEngine;
import com.apphook.log.ProxyMarker;
import com.apphook.log.ReflectCall;
import com.apphook.log.SafeHook;
import com.apphook.log.SubscriptionPatch;

import io.github.libxposed.api.XposedInterface;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Feature #6: Flutter MethodChannel — تسجيل الاتجاهين + ترقيع Result
 * اختياري (F004). بلا Proxy، بلا استبدال وسائط (قاعدة v3.0.1).
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
        // Dart → Native (تسجيل فقط)
        HookKit.hookAll(fw, mc, "invokeMethod", 3, 3, TAG + ".out", chain -> {
            try {
                Object m = chain.getArg(0);
                Object a = chain.getArg(1);
                Object res = chain.getArg(2);
                SafeHook.recordCall(TAG + ".out", String.valueOf(m),
                        3, abbrev(a), abbrev(a));
                if (res != null) hookResultFor(fw, res.getClass(), String.valueOf(m));
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
        SafeHook.log(TAG + ": hooked (patch=" + Config.PATCH_FLUTTER + ")");
    }

    private static void hookHandler(XposedInterface fw, Class<?> cls) {
        if (cls == null || ProxyMarker.isProxy(cls)) return;
        HookKit.hookAll(fw, cls, "onMethodCall", 2, 2, TAG + ".in", chain -> {
            try {
                Object call = chain.getArg(0);
                Object res = chain.getArg(1);
                if (call == null) return chain.proceed();
                String method;
                Object args = null;
                try {
                    method = String.valueOf(ReflectCall.call(call, "method"));
                    args = ReflectCall.call(call, "arguments");
                } catch (Throwable ignored) {
                    return chain.proceed();
                }
                int argc = args instanceof Object[] ? ((Object[]) args).length : 0;
                SafeHook.recordCall(TAG + ".in", method, argc, abbrev(args), abbrev(args));
                if (res != null) hookResultFor(fw, res.getClass(), method);
            } catch (Throwable t) {
                SafeHook.logError(TAG + ".in", t);
            }
            return chain.proceed();
        });
    }

    private static void hookResultFor(XposedInterface fw, Class<?> cls, String method) {
        if (cls == null || ProxyMarker.isProxy(cls)) return;
        if (!seenResults.add(cls.getName() + "#" + method)) return;
        HookKit.hookAll(fw, cls, "success", 1, 1, TAG + ".res", chain -> {
            try {
                Object orig = chain.getArg(0);
                Object patched = orig;
                if (Config.PATCH_FLUTTER) {
                    patched = patch(orig);
                    if (patched != orig) {
                        SafeHook.countOpen("flutter_result:" + method, "F004");
                    }
                }
                SafeHook.recordCall(TAG + ".res", "success(" + method + ")",
                        1, abbrev(orig), abbrev(patched));
                if (patched != orig) return chain.proceed(new Object[]{patched});
            } catch (Throwable t) {
                SafeHook.logError(TAG + ".res", t);
            }
            return chain.proceed();
        });
    }

    public static Object patch(Object v) {
        try {
            if (v instanceof String) {
                String p = SubscriptionPatch.patchJson((String) v);
                return p;
            }
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

    static void patchMap(Map<?, ?> m) {
        try {
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) m;
            for (Map.Entry<Object, Object> e : map.entrySet()) {
                String k = String.valueOf(e.getKey()).toLowerCase();
                Object v = e.getValue();
                try {
                    if (v instanceof Number && (k.contains("balance") || k.contains("credit"))) {
                        e.setValue(9999999);
                    } else if (v instanceof String) {
                        String sv = (String) v;
                        if ("free".equals(sv) && (k.contains("plan") || k.contains("type")
                                || k.contains("membership"))) {
                            e.setValue("plus");
                        } else if ("never_purchased".equals(sv)) {
                            e.setValue("active");
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
