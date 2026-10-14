package com.apphook.log;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Method;
import java.util.List;

/**
 * جسر legacy يعيد تنفيذ نفس الميزات السبع فوق API القديم
 * (de.robv) — كل Hook داخل try/catch مستقل (PROTECTIVE يدوي).
 */
final class LegacyBridge {
    private LegacyBridge() {}

    static void installAll(XC_LoadPackage.LoadPackageParam lp) {
        ClassLoader cl = lp.classLoader;
        safe(() -> hookFlutterChannel(cl));
        safe(() -> hookPrefs(cl));
        safe(() -> hookJson(cl));
        safe(() -> hookWebViewLegacy(cl));
        safe(() -> hookAppsFlyerLegacy(cl));
        safe(() -> hookUrlConnLegacy(cl));
        safe(() -> hookBilling(cl));
        safe(() -> hookRevenueCat(cl));
        safe(() -> hookPremium(cl, lp.packageName));
        safe(() -> hookNetwork(cl));
        safe(() -> hookIntegrity(cl));
    }

    private interface Job { void run() throws Throwable; }

    private static void safe(Job j) {
        try {
            j.run();
        } catch (Throwable t) {
            XposedBridge.log("[AppHook/Safe] skipped: " + t);
        }
    }

    private static XC_MethodHook after(HookActionProxyLegacy a) {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                try {
                    Object r = a.run(p);
                    if (r != After.KEEP) p.setResult(r);
                } catch (Throwable t) {
                    XposedBridge.log("[AppHook/Safe] hook body ignored: " + t);
                }
            }
        };
    }

    private interface HookActionProxyLegacy {
        Object run(XC_MethodHook.MethodHookParam p) throws Throwable;
    }

    private static final class After {
        static final Object KEEP = new Object();
    }

    // --- Billing ---
    private static void hookBilling(ClassLoader cl) {
        hookBoolInt(cl, "com.android.billingclient.api.Purchase",
                "getPurchaseState", 0, "[Billing] getPurchaseState");
        hookBoolInt(cl, "com.android.billingclient.api.Purchase",
                "isAcknowledged", true, "[Billing] isAcknowledged");
        hookBoolInt(cl, "com.android.billingclient.api.BillingClient",
                "isReady", true, "[Billing] isReady");
        hookWatch(cl, "com.android.billingclient.api.Purchase$PurchasesResult",
                "getPurchasesList", "[Billing] getPurchasesList");
        try {
            Class<?> bc = XposedHelpers.findClass(
                    "com.android.billingclient.api.BillingClient", cl);
            for (Method m : bc.getDeclaredMethods()) {
                if (!m.getName().equals("queryPurchasesAsync")) continue;
                try {
                    XposedBridge.hookMethod(m, after(p -> {
                        XposedBridge.log("[AppHook] [Billing] queryPurchasesAsync");
                        return After.KEEP;
                    }));
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    // --- RevenueCat ---
    private static void hookRevenueCat(ClassLoader cl) {
        hookBoolInt(cl, "com.revenuecat.purchases.EntitlementInfo",
                "isActive", true, "[Billing] RevenueCat.isActive");
        try {
            Class<?> c = XposedHelpers.findClass(
                    "com.revenuecat.purchases.EntitlementInfo", cl);
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().toLowerCase().contains("billingissue")) {
                    try {
                        XposedBridge.hookMethod(m, after(p -> {
                            if (p.getResult() != null) return null;
                            return After.KEEP;
                        }));
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    // --- Premium flags ---
    private static void hookPremium(ClassLoader cl, String pkg) {
        String[] cands = {pkg + ".UserInfo", pkg + ".User",
                pkg + ".model.User", pkg + ".data.User"};
        String[] tokens = {"isPremium", "isVip", "isPro", "isSubscribed",
                "hasPremium", "hasVip", "isPaid", "isMember", "isGold"};
        for (String cn : cands) {
            Class<?> c;
            try {
                c = XposedHelpers.findClass(cn, cl);
            } catch (Throwable ignored) {
                continue;
            }
            for (String tk : tokens) {
                for (Method m : c.getDeclaredMethods()) {
                    try {
                        if (!m.getName().equalsIgnoreCase(tk)
                                && !m.getName().toLowerCase().contains(tk.toLowerCase())) continue;
                        if (m.getParameterTypes().length != 0) continue;
                        Class<?> r = m.getReturnType();
                        if (r == boolean.class || r == Boolean.class) {
                            XposedBridge.hookMethod(m, after(p -> {
                                if (Boolean.FALSE.equals(p.getResult())) return true;
                                return After.KEEP;
                            }));
                        } else if (r == int.class || r == Integer.class) {
                            XposedBridge.hookMethod(m, after(p -> {
                                if (Integer.valueOf(0).equals(p.getResult())) return 1;
                                return After.KEEP;
                            }));
                        }
                    } catch (Throwable ignored) {}
                }
            }
            try {
                List<FingerprintEngine.Hit> hits = FingerprintEngine.scanPremiumFlags(c);
                for (FingerprintEngine.Hit h : hits) {
                    try {
                        final boolean isBool = h.method.getReturnType() == boolean.class
                                || h.method.getReturnType() == Boolean.class;
                        XposedBridge.hookMethod(h.method, after(p -> {
                            if (isBool && Boolean.FALSE.equals(p.getResult())) return true;
                            if (!isBool && Integer.valueOf(0).equals(p.getResult())) return 1;
                            return After.KEEP;
                        }));
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
    }

    // --- Network (watch only) ---
    private static void hookNetwork(ClassLoader cl) {
        hookWatch(cl, "okhttp3.Response", "body", "[Network] OkHttp.body");
        hookWatch(cl, "okhttp3.RealCall", "execute", "[Network] OkHttp.execute");
        try {
            Class<?> c = Class.forName("java.net.HttpURLConnection");
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals("getResponseCode") && m.getParameterTypes().length == 0) {
                    try {
                        XposedBridge.hookMethod(m, after(p -> After.KEEP));
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    // --- Integrity (watch) ---
    private static void hookIntegrity(ClassLoader cl) {
        try {
            Class<?> c = XposedHelpers.findClass(
                    "com.google.android.play.core.integrity.IntegrityManager", cl);
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().toLowerCase().contains("integritytoken")) {
                    try {
                        XposedBridge.hookMethod(m, after(p -> After.KEEP));
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void hookBoolInt(ClassLoader cl, String cls, String method,
                                    Object forced, String tag) {
        try {
            Class<?> c = XposedHelpers.findClass(cls, cl);
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(method)) continue;
                try {
                    XposedBridge.hookMethod(m, after(p -> {
                        Object o = p.getResult();
                        if (forced instanceof Boolean && Boolean.FALSE.equals(o)) return true;
                        if (forced instanceof Integer && Integer.valueOf(0).equals(o)) return 0;
                        if (forced instanceof Integer && ((Integer) forced) == 1
                                && Integer.valueOf(0).equals(o)) return 1;
                        return After.KEEP;
                    }));
                    XposedBridge.log("[AppHook] Hook OK: " + tag);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
            XposedBridge.log("[AppHook/Safe] " + tag + " skipped (absent)");
        }
    }

    private static void hookWatch(ClassLoader cl, String cls, String method, String tag) {
        try {
            Class<?> c = XposedHelpers.findClass(cls, cl);
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(method)) continue;
                try {
                    XposedBridge.hookMethod(m, after(p -> After.KEEP));
                    XposedBridge.log("[AppHook] Hook OK: " + tag);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
            XposedBridge.log("[AppHook/Safe] " + tag + " skipped (absent)");
        }
    }

    // --- v3.0.1: Flutter MethodChannel (legacy) — بلا Proxy، بلا استبدال وسائط ---
    private static final java.util.Set<String> legacyHandlers =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());
    private static final java.util.Set<String> legacyResults =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    private static void hookFlutterChannel(ClassLoader cl) {
        Class<?> mc;
        try {
            mc = XposedHelpers.findClass("io.flutter.plugin.common.MethodChannel", cl);
        } catch (Throwable ignored) {
            XposedBridge.log("[AppHook/Safe] Flutter.MethodChannel skipped (not a Flutter app)");
            return;
        }
        try {
            for (Method m : mc.getDeclaredMethods()) {
                if (!m.getName().equals("invokeMethod") || m.getParameterTypes().length != 3) continue;
                try {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                Object n = p.args[0];
                                if (n instanceof String
                                        && FlutterChannelHook.isPremiumQuery((String) n)) {
                                    XposedBridge.log("[AppHook] [Flutter] query: " + n);
                                }
                                if (p.args[2] != null) hookLegacyResult(p.args[2].getClass());
                            } catch (Throwable ignored) {}
                        }
                    });
                    XposedBridge.log("[AppHook] Hook OK: Flutter.invokeMethod");
                } catch (Throwable ignored) {}
            }
            // قراءة فقط: نربط onMethodCall على فئة الـ handler — لا نمس p.args
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
            XposedBridge.log("[AppHook] Hook OK: Flutter.setMethodCallHandler (read-only)");
        } catch (Throwable ignored) {}
    }

    /** ربط onMethodCall على الفئة الحقيقية (مرة واحدة) — legacy API. */
    private static void hookLegacyHandler(Class<?> h) {
        if (h == null || ProxyMarker.isProxy(h)) return;
        if (!legacyHandlers.add(h.getName())) return;
        try {
            XposedBridge.hookAllMethods(h, "onMethodCall", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (p.args.length < 2 || p.args[0] == null) return;
                        Object mName;
                        try {
                            mName = XposedHelpers.callMethod(p.args[0], "method");
                        } catch (Throwable ignored) {
                            return;
                        }
                        if (!(mName instanceof String)) return;
                        XposedBridge.log("[AppHook] [Flutter] handler: " + mName);
                        if (FlutterChannelHook.isPremiumQuery((String) mName)) {
                            if (p.args[1] != null) {
                                try {
                                    XposedHelpers.callMethod(p.args[1], "success", Boolean.TRUE);
                                } catch (Throwable ignored) {}
                            }
                            p.setResult(null); // تخطي آمن للأصلي
                            XposedBridge.log("[AppHook] [Flutter] intercepted: " + mName);
                        } else if (p.args[1] != null) {
                            hookLegacyResult(p.args[1].getClass());
                        }
                    } catch (Throwable ignored) {}
                }
            });
            XposedBridge.log("[AppHook] handler hooked: " + h.getName());
        } catch (Throwable ignored) {}
    }

    /** ربط success(value) — نفس النوع دائماً (legacy API). */
    private static void hookLegacyResult(Class<?> r) {
        if (r == null || ProxyMarker.isProxy(r)) return;
        if (!legacyResults.add(r.getName())) return;
        try {
            XposedBridge.hookAllMethods(r, "success", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (p.args.length < 1) return;
                        Object v = p.args[0];
                        Object mod = FlutterChannelHook.modifyResult(v);
                        if (mod != v) {
                            p.args[0] = mod; // نفس النوع — آمن
                            XposedBridge.log("[AppHook] [Flutter] Result.success patched");
                        }
                    } catch (Throwable ignored) {}
                }
            });
            XposedBridge.log("[AppHook] Result hooked: " + r.getName());
        } catch (Throwable ignored) {}
    }

    // --- v3: SharedPreferences (legacy) ---
    private static void hookPrefs(ClassLoader cl) {
        Class<?> sp;
        try {
            sp = XposedHelpers.findClass("android.app.SharedPreferencesImpl", cl);
        } catch (Throwable t1) {
            try {
                sp = Class.forName("android.app.SharedPreferencesImpl");
            } catch (Throwable t2) {
                XposedBridge.log("[AppHook/Safe] Prefs skipped (impl absent)");
                return;
            }
        }
        for (Method m : sp.getDeclaredMethods()) {
            try {
                if (m.getName().equals("getBoolean") && m.getParameterTypes().length == 2) {
                    XposedBridge.hookMethod(m, after(p -> {
                        Object k = p.args[0];
                        if (k instanceof String && PrefsHook.isPremiumKey((String) k)
                                && Boolean.FALSE.equals(p.getResult())) return true;
                        return After.KEEP;
                    }));
                    XposedBridge.log("[AppHook] Hook OK: Prefs.getBoolean");
                } else if (m.getName().equals("getInt") && m.getParameterTypes().length == 2) {
                    XposedBridge.hookMethod(m, after(p -> {
                        Object k = p.args[0];
                        if (k instanceof String && PrefsHook.isPremiumKey((String) k)
                                && Integer.valueOf(0).equals(p.getResult())) return 1;
                        return After.KEEP;
                    }));
                    XposedBridge.log("[AppHook] Hook OK: Prefs.getInt");
                }
            } catch (Throwable ignored) {}
        }
    }

    // --- v3: JSON (legacy) ---
    private static void hookJson(ClassLoader cl) {
        try {
            Class<?> g = XposedHelpers.findClass("com.google.gson.Gson", cl);
            for (Method m : g.getDeclaredMethods()) {
                if (!m.getName().equals("fromJson") || m.getParameterTypes().length != 2) continue;
                if (m.getParameterTypes()[0] != String.class) continue;
                try {
                    XposedBridge.hookMethod(m, after(p -> {
                        try {
                            Object o = p.getResult();
                            if (o != null) JsonHook.patchObject(o);
                        } catch (Throwable ignored) {}
                        return After.KEEP;
                    }));
                    XposedBridge.log("[AppHook] Hook OK: Gson.fromJson");
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        try {
            Class<?> j = Class.forName("org.json.JSONObject");
            for (Method m : j.getDeclaredMethods()) {
                if (!m.getName().equals("getBoolean") || m.getParameterTypes().length != 1) continue;
                try {
                    XposedBridge.hookMethod(m, after(p -> {
                        Object k = p.args[0];
                        if (k instanceof String && JsonHook.isPremiumKey((String) k)
                                && Boolean.FALSE.equals(p.getResult())) return true;
                        return After.KEEP;
                    }));
                    XposedBridge.log("[AppHook] Hook OK: JSONObject.getBoolean");
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    // --- v3.1: WebView (legacy) — مراقبة فقط ---
    private static void hookWebViewLegacy(ClassLoader cl) {
        try {
            Class<?> wv = Class.forName("android.webkit.WebView");
            for (Method m : wv.getDeclaredMethods()) {
                if (!m.getName().equals("addJavascriptInterface")
                        || m.getParameterTypes().length != 2) continue;
                try {
                    final int nameIdx = m.getParameterTypes()[1] == String.class ? 1 : 0;
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                XposedBridge.log("[AppHook] [WebView] JS bridge: "
                                        + p.args[nameIdx]);
                            } catch (Throwable ignored) {}
                        }
                    });
                    XposedBridge.log("[AppHook] Hook OK: WebView.addJavascriptInterface");
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        try {
            Class<?> client = Class.forName("android.webkit.WebViewClient");
            for (Method m : client.getDeclaredMethods()) {
                if (!m.getName().equals("shouldInterceptRequest")
                        || m.getParameterTypes().length != 2) continue;
                try {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                Object r = p.args[1];
                                String url = r instanceof String ? (String) r : null;
                                if (url == null && r != null) {
                                    try {
                                        Object uri = XposedHelpers.callMethod(r, "getUrl");
                                        if (uri != null) url = String.valueOf(uri);
                                    } catch (Throwable ignored) {}
                                }
                                if (url != null && WebViewHook.isPayUrl(url)) {
                                    XposedBridge.log("[AppHook] [WebView] REQ: " + url);
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
                    XposedBridge.log("[AppHook] Hook OK: shouldInterceptRequest");
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    // --- v3.1: AppsFlyer (legacy) — مراقبة أحداث الشراء ---
    private static void hookAppsFlyerLegacy(ClassLoader cl) {
        Class<?> af;
        try {
            af = XposedHelpers.findClass("com.appsflyer.AppsFlyerLib", cl);
        } catch (Throwable ignored) {
            return;
        }
        try {
            XposedBridge.hookAllMethods(af, "logEvent", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        for (Object a : p.args) {
                            if (a instanceof String
                                    && AppsFlyerHook.isPurchaseEvent((String) a)) {
                                XposedBridge.log("[AppHook] [AppsFlyer] event: " + a);
                                break;
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            });
            XposedBridge.log("[AppHook] Hook OK: AppsFlyer.logEvent");
        } catch (Throwable ignored) {}
    }

    // --- v3.1: HttpURLConnection getInputStream (legacy) — مراقبة ---
    private static void hookUrlConnLegacy(ClassLoader cl) {
        Class<?> c;
        try {
            c = Class.forName("java.net.HttpURLConnection");
        } catch (Throwable ignored) {
            return;
        }
        for (Method m : c.getDeclaredMethods()) {
            if (!m.getName().equals("getInputStream")
                    || m.getParameterTypes().length != 0) continue;
            try {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            Object u = XposedHelpers.callMethod(p.thisObject, "getURL");
                            XposedBridge.log("[AppHook] [Network] HttpURLConnection "
                                    + (u != null ? u : "?"));
                        } catch (Throwable ignored) {}
                    }
                });
                XposedBridge.log("[AppHook] Hook OK: HttpURLConnection.getInputStream");
            } catch (Throwable ignored) {}
        }
    }
}
