package com.apphook.log;

import com.apphook.log.hooks.PrefsHook;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Method;

/**
 * جسر legacy — مرآة طبقات v6.0 فوق API القديم مع بوابات Config.
 * التسجيل عبر SafeHook.recordCall (يعمل بلا XposedInterface).
 * كل Hook داخل try/catch مستقل (PROTECTIVE يدوي).
 */
final class LegacyBridge {
    private LegacyBridge() {}

    static void installAll(XC_LoadPackage.LoadPackageParam lp) {
        ClassLoader cl = lp.classLoader;
        safe(() -> hookBilling(cl));
        safe(() -> hookFlutterChannel(cl));
        safe(() -> hookPrefs(cl));
        safe(() -> hookJson(cl));
        safe(() -> hookWebView(cl));
        safe(() -> hookNetwork(cl));
        XposedBridge.log("[SA] legacy install done [MODE=" + Config.modeName() + "]");
    }

    private interface Job { void run() throws Throwable; }

    private static void safe(Job j) {
        try {
            j.run();
        } catch (Throwable t) {
            XposedBridge.log("[SA/Safe] skipped");
        }
    }

    private static XC_MethodHook after(HookLegacy a) {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                try {
                    Object r = a.run(p);
                    if (r != After.KEEP) p.setResult(r);
                } catch (Throwable ignored) {}
            }
        };
    }

    private interface HookLegacy {
        Object run(XC_MethodHook.MethodHookParam p) throws Throwable;
    }

    private static final class After {
        static final Object KEEP = new Object();
    }

    // --- Billing (F003, gated) ---
    private static void hookBilling(ClassLoader cl) {
        try {
            Class<?> pc = XposedHelpers.findClass(
                    "com.android.billingclient.api.Purchase", cl);
            for (Method m : pc.getDeclaredMethods()) {
                if (m.getParameterTypes().length != 0) continue;
                if (m.getName().equals("getPurchaseState")) {
                    try {
                        XposedBridge.hookMethod(m, after(p -> {
                            Object o = p.getResult();
                            Object q = o;
                            if (Config.PATCH_BILLING && o instanceof Integer
                                    && ((Integer) o) != 0) {
                                q = 0;
                                SafeHook.countOpen("billing_state", "F003");
                            }
                            SafeHook.recordCall("Billing", "getPurchaseState", 0, o, q);
                            if (q != o) return q;
                            return After.KEEP;
                        }));
                    } catch (Throwable ignored) {}
                } else if (m.getName().equals("isAcknowledged")) {
                    try {
                        XposedBridge.hookMethod(m, after(p -> {
                            Object o = p.getResult();
                            Object q = o;
                            if (Config.PATCH_BILLING && Boolean.FALSE.equals(o)) {
                                q = true;
                                SafeHook.countOpen("billing_ack", "F003");
                            }
                            SafeHook.recordCall("Billing", "isAcknowledged", 0, o, q);
                            if (q != o) return q;
                            return After.KEEP;
                        }));
                    } catch (Throwable ignored) {}
                }
            }
            XposedBridge.log("[SA] Hook OK: Billing(legacy)");
        } catch (Throwable ignored) {}
    }

    // --- Flutter channel (F004, gated, per-method) ---
    private static final java.util.Set<String> legacyHandlers =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());
    private static final java.util.Set<String> legacyResults =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    private static void hookFlutterChannel(ClassLoader cl) {
        Class<?> mc;
        try {
            mc = XposedHelpers.findClass("io.flutter.plugin.common.MethodChannel", cl);
        } catch (Throwable ignored) {
            return;
        }
        try {
            XposedBridge.hookAllMethods(mc, "setMethodCallHandler", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (p.args.length > 0 && p.args[0] != null) {
                            hookLegacyHandler(p.args[0].getClass());
                        }
                    } catch (Throwable ignored) {}
                }
            });
            XposedBridge.log("[SA] Hook OK: Flutter.register(legacy)");
        } catch (Throwable ignored) {}
    }

    private static void hookLegacyHandler(Class<?> h) {
        if (h == null || ProxyMarker.isProxy(h)) return;
        if (!legacyHandlers.add(h.getName())) return;
        try {
            XposedBridge.hookAllMethods(h, "onMethodCall", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (p.args.length < 2 || p.args[0] == null) return;
                        Object mn;
                        try {
                            mn = XposedHelpers.callMethod(p.args[0], "method");
                        } catch (Throwable ignored) {
                            return;
                        }
                        if (!(mn instanceof String)) return;
                        String method = (String) mn;
                        Object args = null;
                        try {
                            args = XposedHelpers.callMethod(p.args[0], "arguments");
                        } catch (Throwable ignored) {}
                        SafeHook.recordCall("Flutter.in", method, 0, args, args);
                        if (p.args[1] != null) {
                            hookLegacyResult(p.args[1].getClass(), method);
                        }
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}
    }

    private static void hookLegacyResult(Class<?> r, String method) {
        if (r == null || ProxyMarker.isProxy(r) || method == null) return;
        if (!legacyResults.add(r.getName() + "#" + method)) return;
        try {
            XposedBridge.hookAllMethods(r, "success", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (p.args.length < 1) return;
                        Object o = p.args[0];
                        Object q = o;
                        if (Config.PATCH_FLUTTER) {
                            q = com.apphook.log.hooks.FlutterChannelHook.patch(o);
                            if (q != o) SafeHook.countOpen("flutter_result:" + method, "F004");
                        }
                        SafeHook.recordCall("Flutter.res", "success(" + method + ")",
                                1, o, q);
                        if (q != o) p.args[0] = q;
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}
    }

    // --- Prefs (F006, gated: bool/int/string) ---
    private static void hookPrefs(ClassLoader cl) {
        Class<?> sp;
        try {
            sp = XposedHelpers.findClass("android.app.SharedPreferencesImpl", cl);
        } catch (Throwable t1) {
            try {
                sp = Class.forName("android.app.SharedPreferencesImpl");
            } catch (Throwable t2) {
                return;
            }
        }
        for (Method m : sp.getDeclaredMethods()) {
            try {
                if (m.getName().equals("getBoolean") && m.getParameterTypes().length == 2) {
                    XposedBridge.hookMethod(m, after(p -> {
                        Object o = p.getResult();
                        Object k = p.args[0];
                        Object q = o;
                        if (Config.PATCH_PREFS && k instanceof String
                                && PrefsHook.isPremiumKey((String) k)
                                && Boolean.FALSE.equals(o)) {
                            q = true;
                            SafeHook.countOpen("prefs_bool:" + k, "F006");
                        }
                        if (k instanceof String && PrefsHook.isPremiumKey((String) k)) {
                            SafeHook.recordCall("Prefs", "getBoolean(" + k + ")", 2, o, q);
                            if (q != o) return q;
                        }
                        return After.KEEP;
                    }));
                } else if (m.getName().equals("getInt") && m.getParameterTypes().length == 2) {
                    XposedBridge.hookMethod(m, after(p -> {
                        Object o = p.getResult();
                        Object k = p.args[0];
                        Object q = o;
                        if (Config.PATCH_PREFS && o instanceof Integer) {
                            int v = (Integer) o;
                            boolean bal = k instanceof String
                                    && PrefsHook.isBalanceKey((String) k);
                            if (bal && Config.ENABLE_BALANCE_FREEZE && v < 100) {
                                q = Config.FROZEN_BALANCE;
                                SafeHook.countOpen("prefs.int.balance", "F002");
                            } else if (k instanceof String
                                    && PrefsHook.isPremiumKey((String) k) && v < 1000000) {
                                q = 9999999;
                                SafeHook.countOpen("prefs_int:" + k, "F006");
                            }
                        }
                        if (k instanceof String
                                && (PrefsHook.isPremiumKey((String) k)
                                || PrefsHook.isBalanceKey((String) k))) {
                            SafeHook.recordCall("Prefs", "getInt(" + k + ")", 2, o, q);
                            if (q != o) return q;
                        }
                        return After.KEEP;
                    }));
                } else if (m.getName().equals("getString") && m.getParameterTypes().length == 2) {
                    XposedBridge.hookMethod(m, after(p -> {
                        try {
                            Object r = p.getResult();
                            if (!(r instanceof String)) return After.KEEP;
                            String s = (String) r;
                            Object k = p.args[0];
                            boolean interesting = (k instanceof String
                                    && PrefsHook.isPremiumKey((String) k))
                                    || PrefsHook.containsPremium(s);
                            if (!interesting) return After.KEEP;
                            Object q = s;
                            if (Config.PATCH_PREFS) {
                                String pq = SubscriptionPatch.patchJson(s);
                                if (pq != s) {
                                    q = pq;
                                    SafeHook.countOpen("prefs_str:" + k, "F006");
                                }
                            }
                            SafeHook.recordCall("Prefs", "getString(" + k + ")", 2, s, q);
                            if (q != s) return q;
                        } catch (Throwable ignored) {}
                        return After.KEEP;
                    }));
                }
            } catch (Throwable ignored) {}
        }
        XposedBridge.log("[SA] Hook OK: Prefs(legacy)");
    }

    // --- Json bool (F005, gated) ---
    private static void hookJson(ClassLoader cl) {
        try {
            Class<?> j = Class.forName("org.json.JSONObject");
            for (Method m : j.getDeclaredMethods()) {
                if (!m.getName().equals("getBoolean")
                        || m.getParameterTypes().length != 1) continue;
                try {
                    XposedBridge.hookMethod(m, after(p -> {
                        Object o = p.getResult();
                        Object k = p.args[0];
                        Object q = o;
                        if (Config.PATCH_JSON && k instanceof String
                                && com.apphook.log.hooks.JsonHook.isPremium((String) k)
                                && Boolean.FALSE.equals(o)) {
                            q = true;
                            SafeHook.countOpen("json_bool:" + k, "F005");
                        }
                        if (k instanceof String
                                && com.apphook.log.hooks.JsonHook.isPremium((String) k)) {
                            SafeHook.recordCall("Json", "getBoolean(" + k + ")", 1, o, q);
                            if (q != o) return q;
                        }
                        return After.KEEP;
                    }));
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    // --- WebView: console [SA] + chunks injection ---
    private static void hookWebView(ClassLoader cl) {
        try {
            Class<?> wv = Class.forName("android.webkit.WebView");
            for (Method m : wv.getDeclaredMethods()) {
                if (m.getName().equals("setWebChromeClient")
                        && m.getParameterTypes().length == 1) {
                    try {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam p) {
                                try {
                                    if (p.args.length > 0 && p.args[0] != null) {
                                        hookChromeActual(p.args[0].getClass());
                                    }
                                } catch (Throwable ignored) {}
                            }
                        });
                    } catch (Throwable ignored) {}
                }
            }
            XposedBridge.log("[SA] Hook OK: WebView.client(legacy)");
        } catch (Throwable ignored) {}
        try {
            Class<?> client = Class.forName("android.webkit.WebViewClient");
            for (Method m : client.getDeclaredMethods()) {
                if (!m.getName().equals("onPageFinished")
                        || m.getParameterTypes().length != 2) continue;
                try {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                Object wv2 = p.args[0];
                                Object u = p.args[1];
                                SafeHook.recordCall("WebView", "onPageFinished", 1, u, u);
                                if (wv2 instanceof android.webkit.WebView) {
                                    injectLegacy((android.webkit.WebView) wv2);
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private static final java.util.Set<String> legacyChrome =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    private static void hookChromeActual(Class<?> actual) {
        if (actual == null || !legacyChrome.add(actual.getName())) return;
        try {
            XposedBridge.hookAllMethods(actual, "onConsoleMessage", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        String text = null;
                        if (p.args.length == 1 && p.args[0] != null) {
                            try {
                                Object msg = XposedHelpers.callMethod(p.args[0], "message");
                                if (msg != null) text = String.valueOf(msg);
                            } catch (Throwable ignored) {
                                text = String.valueOf(p.args[0]);
                            }
                        } else if (p.args.length >= 1 && p.args[0] instanceof String) {
                            text = (String) p.args[0];
                        }
                        if (text != null && (text.startsWith("[SA]")
                                || text.startsWith("[FREEZE]") || text.startsWith("[ROTATE]"))) {
                            SafeHook.recordCall("WebView.js", abbrev(text, 80),
                                    p.args.length, text, text);
                        }
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}
    }

    private static final java.util.Set<String> legacyInjected =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    private static void injectLegacy(android.webkit.WebView view) {
        String key;
        try {
            key = view.getClass().getName() + "@" + System.identityHashCode(view);
        } catch (Throwable ignored) {
            return;
        }
        if (!legacyInjected.add(key)) return;
        try {
            final android.webkit.WebView v = view;
            final java.util.List<String> chunks =
                    new java.util.ArrayList<String>(
                            com.apphook.log.hooks.WebViewHook.patchChunks());
            v.post(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < chunks.size(); i++) {
                        final String c = chunks.get(i);
                        try {
                            if (i == 0) {
                                v.loadUrl(c);
                            } else {
                                v.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            v.loadUrl(c);
                                        } catch (Throwable ignored) {}
                                    }
                                }, i * 300);
                            }
                        } catch (Throwable ignored) {}
                    }
                    // v6.2 legacy: freeze + rotation-detector بعد الشرائح
                    try {
                        v.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    v.loadUrl("javascript:"
                                            + com.apphook.log.hooks.BalanceFreezeHook.JS_FREEZE);
                                } catch (Throwable ignored) {}
                                try {
                                    com.apphook.log.hooks.AccountRotationHook.injectDetector(v);
                                } catch (Throwable ignored) {}
                            }
                        }, chunks.size() * 300 + 200);
                    } catch (Throwable ignored) {}
                }
            });
            if (legacyInjected.size() > 200) legacyInjected.clear();
        } catch (Throwable ignored) {}
    }

    // --- Network watch (record-only) ---
    private static void hookNetwork(ClassLoader cl) {
        try {
            Class<?> c = Class.forName("java.net.HttpURLConnection");
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals("getResponseCode")
                        || m.getParameterTypes().length != 0) continue;
                try {
                    XposedBridge.hookMethod(m, after(p -> After.KEEP));
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private static String abbrev(String s, int max) {
        if (s == null) return "null";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
