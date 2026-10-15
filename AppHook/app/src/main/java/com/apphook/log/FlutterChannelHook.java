package com.apphook.log;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * v5.2 — قناة Flutter ↔ Native **بدون Proxy** + ترقيع ردود Native→Dart.
 *
 * <p>الخلفية: v5.1 أثبتت أن شاشات WebView (pricing/credit-usage) تُرقّع
 * بنجاح بينما شاشات Flutter Native (main/settings/super-agent) تقرأ عبر
 * MethodChannel. هذه الطبقة تعترض الردود قبل وصولها لـ Dart:</p>
 * <ol>
 *   <li>{@code setMethodCallHandler}: قراءة فقط — ربط {@code onMethodCall}
 *       على فئة الـ handler الحقيقية (dedup لكل فئة).</li>
 *   <li>{@code onMethodCall}: تسجيل كل استدعاء غير ضجيج + ربط
 *       {@code success} على فئة الـ Result لكل (فئة:دالة).</li>
 *   <li>{@code success(value)}: ترقيع String-JSON (نفس قواعد الرصيد/
 *       الخطة) وMap (أرقام→9999999، free→plus، never_purchased→active)
 *       بنفس النوع دائماً.</li>
 * </ol>
 *
 * <p>ملاحظة API 102: لا يوجد XposedHelpers — Class.forName(cl) +
 * fw.hook(Method) PROTECTIVE + ReflectCall + hookAll الخاصة بنا.</p>
 */
public final class FlutterChannelHook {
    private FlutterChannelHook() {}

    private static final String TAG = "AppHook.Flutter";

    /** تُشارك مع LegacyBridge — لا تحذف */
    static final Set<String> hookedHandlers =
            Collections.synchronizedSet(new HashSet<String>());
    static final Set<String> hookedResults =
            Collections.synchronizedSet(new HashSet<String>());
    /** v5.2: ربط Result لكل (فئة:دالة) — يكشف ردود Native→Dart لكل استدعاء */
    static final Set<String> hookedResultKeys =
            Collections.synchronizedSet(new HashSet<String>());
    /** محجوز للتوافق — البوابة الفعلية مفتاح (فئة#دالة) في hookedResults */
    static boolean noteResultKey(String key) {
        return key != null && hookedResultKeys.add(key);
    }

    static final String[] PREMIUM_KEYWORDS = {
            // عام
            "premium", "vip", "pro", "paid", "subscri", "member", "entitle",
            "getsubscription", "checkpremium", "checkvip",
            // Genspark محتمل (ائتمان/خطة/حصة)
            "genspark", "credit", "quota", "plan", "tier", "upgrade",
            // متغيرات شائعة
            "is_pro", "isPro", "has_pro", "hasPro", "is_premium", "isPremium",
            "is_paid", "isPaid", "is_subscribed", "isSubscribed",
            "subscription_status", "subscriptionStatus", "subscription_active",
            "user_plan", "userPlan", "account_type", "accountType",
            "feature_flag", "featureFlag", "isPaid", "is_paid",
            "billing", "purchase"
    };

    /** ضجيج WebView/تنقل — يُتجاهل من السجل (v3.1 noise filter). */
    static final Set<String> NOISE;
    static {
        Set<String> n = new HashSet<String>();
        String[] a = {"onProgressChanged", "popRoute", "onPageStarted",
                "onPageFinished", "onLoadResource", "onReceivedError",
                "onConsoleMessage", "onDownloadStart", "onLoadStop",
                "onReceivedHttpError", "onPageCommitVisible", "onReceivedTitle",
                "onReceivedIcon", "updateKeepAlive", "loadUrl", "dispose"};
        for (String s : a) n.add(s);
        NOISE = Collections.unmodifiableSet(n);
    }

    static boolean isNoise(String m) {
        return m != null && NOISE.contains(m);
    }

    /** اعتراض يستقبل السلسلة — يُرجع القيمة النهائية (skip = لا proceed). */
    interface ChainRun {
        Object run(XposedInterface.Chain chain) throws Throwable;
    }

    public static void install(XposedInterface fw, ClassLoader cl) {
        SafeHook.log(TAG + ": installing v5.3 MethodChannel patch");
        Class<?> mc = FingerprintEngine.findClassQuiet(cl,
                "io.flutter.plugin.common.MethodChannel");
        if (mc == null) {
            SafeHook.logSafe("Flutter.MethodChannel", "not a Flutter app or engine absent");
            return;
        }
        hookInvokeMethod(fw, mc);
        hookSetHandler(fw, mc);
        SafeHook.log(TAG + ": v5.3 installed successfully");
    }

    /** مكافئ hookAllMethods: يربط كل declared عبر الهرم الوراثي. */
    static void hookAll(XposedInterface fw, Class<?> base, String name,
                        int minArgs, int maxArgs, String tag, ChainRun run) {
        Class<?> c = base;
        while (c != null && c != Object.class) {
            Method[] ms;
            try {
                ms = c.getDeclaredMethods();
            } catch (Throwable ignored) {
                break;
            }
            for (Method m : ms) {
                if (!m.getName().equals(name)) continue;
                int n = m.getParameterTypes().length;
                if (n < minArgs || (maxArgs >= 0 && n > maxArgs)) continue;
                try {
                    m.setAccessible(true);
                    final Method target = m;
                    fw.hook(target)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                try {
                                    return run.run(chain);
                                } catch (Throwable t) {
                                    SafeHook.logError(tag, t);
                                    try {
                                        return chain.proceed();
                                    } catch (Throwable t2) {
                                        return null;
                                    }
                                }
                            });
                    SafeHook.log("Hook OK: " + tag + " -> " + m);
                } catch (Throwable t) {
                    SafeHook.logError(tag, t);
                }
            }
            try {
                c = c.getSuperclass();
            } catch (Throwable ignored) {
                break;
            }
        }
    }

    /** 1) طلبات Dart → Native: فلترة ضجيج + ربط Result للمهتم به فقط. */
    private static void hookInvokeMethod(XposedInterface fw, Class<?> mc) {
        hookAll(fw, mc, "invokeMethod", 3, 3, "Flutter.invokeMethod", chain -> {
            try {
                Object mName = chain.getArg(0);
                Object res = chain.getArg(2);
                if (mName instanceof String) {
                    String name = (String) mName;
                    if (isNoise(name)) return chain.proceed(); // إسكات الضجيج
                    if (!isPremiumQuery(name)) return chain.proceed(); // المهم فقط
                    SafeHook.logChannel("MethodChannel", name,
                            new Object[]{chain.getArg(1)}, null);
                    SafeHook.countFeature("channel-query:" + name);
                    if (res != null) wrapResultSafe(fw, res.getClass(), name);
                }
            } catch (Throwable t) {
                SafeHook.logError("Flutter.invokeMethod", t);
            }
            return chain.proceed(); // لا نمنع الطلب — فقط نراقب ونربط الرد
        });
    }

    /** 2) تسجيل handler: قراءة فقط — نربط onMethodCall على فئته الحقيقية. */
    private static void hookSetHandler(XposedInterface fw, Class<?> mc) {
        hookAll(fw, mc, "setMethodCallHandler", 1, 1,
                "Flutter.setMethodCallHandler", chain -> {
                    try {
                        Object handler = chain.getArg(0);
                        if (handler != null) {
                            hookHandlerClass(fw, handler.getClass());
                        }
                    } catch (Throwable t) {
                        SafeHook.logError("Flutter.setMethodCallHandler", t);
                    }
                    return chain.proceed(); // الوسيط الأصلي يمر كما هو — بلا Proxy
                });
    }

    /** 3) ربط onMethodCall على فئة الـ handler (مرة واحدة لكل فئة). */
    static void hookHandlerClass(XposedInterface fw, Class<?> handlerClass) {
        if (handlerClass == null) return;
        if (ProxyMarker.isProxy(handlerClass)) return;
        if (!hookedHandlers.add(handlerClass.getName())) return; // dedup
        SafeHook.log(TAG + ": capture handler: " + handlerClass.getName());
        hookAll(fw, handlerClass, "onMethodCall", 2, 2,
                "Flutter.onMethodCall", chain -> {
                    Object call = chain.getArg(0);
                    Object result = chain.getArg(1);
                    if (call == null) return chain.proceed();
                    String mName = (String) ReflectCall.call(call, "method");
                    if (mName == null) return chain.proceed();
                    if (isNoise(mName)) return chain.proceed(); // إسكات الضجيج
                    // v5.2: سجل كل استدعاء (غير ضجيج) + اربط Result لهذه الدالة
                    SafeHook.log(TAG + " <- Dart: " + mName);
                    if (result != null) {
                        hookResultForMethod(fw, result.getClass(), mName);
                    }
                    if (!isPremiumQuery(mName)) return chain.proceed(); // المهم فقط يُعترض
                    SafeHook.log(TAG + " * Native: " + mName
                            + " [" + handlerClass.getSimpleName() + "]");
                    // طباعة الوسائط الكاملة للفهم (مختصرة وآمنة)
                    try {
                        Object args = ReflectCall.call(call, "arguments");
                        SafeHook.log(TAG + "   args = " + capped(String.valueOf(args)));
                    } catch (Throwable ignored) {}
                    if (result != null) {
                        SafeHook.log(TAG + "   result class = "
                                + result.getClass().getName());
                        // نرد مباشرة عبر كائن result الأصلي ثم نتخطى الأصلي
                        ReflectCall.call(result, "success", Boolean.TRUE);
                        SafeHook.countFeature("channel:" + mName);
                    } else {
                        SafeHook.log(TAG + " * intercepted (no result obj): " + mName);
                        SafeHook.countFeature("channel:" + mName);
                    }
                    return null; // تخطي آمن — بلا كراش
                });
        SafeHook.log(TAG + ": handler hooked: " + handlerClass.getName());
    }

    /**
     * v5.2: ربط success() لهذه الدالة تحديداً (مفتاح فئة:دالة).
     * يضمن ترقيع ردود Native→Dart لكل استدعاء حتى لو تشاركت الفئة.
     */
    static void hookResultForMethod(XposedInterface fw, Class<?> resultClass,
                                    String methodName) {
        if (resultClass == null || methodName == null) return;
        if (ProxyMarker.isProxy(resultClass)) return;
        // البوابة داخل wrapResultSafe (مفتاح فئة#دالة) — تفويض مباشر
        wrapResultSafe(fw, resultClass, methodName);
    }

    /** 4) ربط success/error/notImplemented على فئة الـ Result. */
    static void wrapResultSafe(XposedInterface fw, Class<?> resultClass) {
        wrapResultSafe(fw, resultClass, "?");
    }

    /**
     * v5.3: البوابة لكل (فئة:دالة) — نفس فئة الـ Result قد تخدم
     * استدعاءات مختلفة، وكل واحدة تستحق interceptor بسياقها.
     * الترقيع مبني على المحتوى (آمن للتكرار) والتسجيل يحمل اسم الدالة.
     */
    static void wrapResultSafe(XposedInterface fw, Class<?> resultClass,
                               final String methodName) {
        if (resultClass == null) return;
        if (ProxyMarker.isProxy(resultClass)) return; // لا نربط فئات Proxy أبداً
        String key;
        try {
            key = resultClass.getName() + "#" + methodName;
        } catch (Throwable ignored) {
            return;
        }
        if (!hookedResults.add(key)) return; // dedup لكل (فئة#دالة)
        // success: ترقيع بنفس النوع + تسجيل
        hookAll(fw, resultClass, "success", 1, 1, "Flutter.Result.success", chain -> {
            Object value = chain.getArg(0);
            Object mod = modifyResult(value);
            if (mod != value) {
                SafeHook.log(TAG + " * patched: " + methodName
                        + " (" + typeOf(value) + "->" + typeOf(mod) + ")");
                SafeHook.countFeature("result:" + methodName);
                return chain.proceed(new Object[]{mod}); // نفس النوع دائماً
            }
            return chain.proceed();
        });
        // error / notImplemented: تسجيل فقط (تشخيص v3.1)
        hookAll(fw, resultClass, "error", 3, 3, "Flutter.Result.error", chain -> {
            try {
                SafeHook.log(TAG + "   Result.error for " + methodName
                        + " code=" + chain.getArg(0));
            } catch (Throwable ignored) {}
            return chain.proceed();
        });
        hookAll(fw, resultClass, "notImplemented", 0, 0,
                "Flutter.Result.notImplemented", chain -> {
                    SafeHook.log(TAG + "   Result.notImplemented for " + methodName);
                    return chain.proceed();
                });
        SafeHook.log(TAG + ": Result hooked: " + resultClass.getName());
    }

    private static String capped(String s) {
        if (s == null) return "null";
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    private static String typeOf(Object o) {
        return o == null ? "null" : o.getClass().getSimpleName();
    }

    static boolean isPremiumQuery(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        for (String kw : PREMIUM_KEYWORDS) {
            if (lower.contains(kw.toLowerCase())) return true;
        }
        return false;
    }

    /** نفس النوع دائماً: String-JSON يُرقّع + Boolean/Integer/Map كما قبل. */
    static Object modifyResult(Object value) {
        if (value == null) return null;
        // v5.2: String تحمل JSON اشتراك/رصيد من Native → رقّعها
        if (value instanceof String) {
            String patched = patchChannelString((String) value);
            if (patched != value) {
                SafeHook.countFeature("channel-str");
            }
            return patched;
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? value : Boolean.TRUE;
        }
        if (value instanceof Integer) {
            return ((Integer) value) == 0 ? Integer.valueOf(1) : value;
        }
        if (value instanceof Map) {
            return patchChannelMap((Map<?, ?>) value);
        }
        if (value instanceof List) {
            try {
                for (Object item : (List<?>) value) modifyResult(item);
            } catch (Throwable ignored) {}
            return value;
        }
        return value;
    }

    /**
     * v5.2: ترقيع String-JSON لردود القناة (مرآة مختصرة لقواعد
     * SubscriptionPatch — نفس القيم: plus/active/9999999).
     */
    static String patchChannelString(String s) {
        if (s == null || s.length() < 2) return s;
        String p = s;
        try {
            p = p.replaceAll("\"balance\"\\s*:\\s*[0-9]+", "\"balance\":9999999");
            p = p.replaceAll("\"credit_balance\"\\s*:\\s*[0-9]+", "\"credit_balance\":9999999");
            p = p.replaceAll("\"credits\"\\s*:\\s*[0-9]+(?=[,}])", "\"credits\":9999999");
            p = p.replaceAll("\"credit\"\\s*:\\s*[0-9]+", "\"credit\":9999999");
            p = p.replaceAll("\"remaining\"\\s*:\\s*[0-9]+", "\"remaining\":9999999");
            p = p.replaceAll("\"plan\"\\s*:\\s*\"free\"", "\"plan\":\"plus\"");
            p = p.replaceAll("\"subType\"\\s*:\\s*\"free\"", "\"subType\":\"plus\"");
            p = p.replaceAll("\"membershipType\"\\s*:\\s*\"free\"", "\"membershipType\":\"plus\"");
            p = p.replaceAll("\"membership_status\"\\s*:\\s*\"free\"", "\"membership_status\":\"plus\"");
            p = p.replaceAll("\"is_active\"\\s*:\\s*false", "\"is_active\":true");
            p = p.replaceAll("\"isPremium\"\\s*:\\s*false", "\"isPremium\":true");
            p = p.replaceAll("\"premium\"\\s*:\\s*false", "\"premium\":true");
        } catch (Throwable ignored) {
            return s;
        }
        return p;
    }

    /**
     * v5.2: ترقيع Map لردود القناة — أرقام الرصيد→9999999،
     * free→plus لحقول الخطة/العضوية، never_purchased→active.
     */
    static Object patchChannelMap(Map<?, ?> m) {
        try {
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) m;
            for (Map.Entry<Object, Object> e : map.entrySet()) {
                String k = String.valueOf(e.getKey());
                Object v = e.getValue();
                if (v == null) continue;
                try {
                    if (v instanceof Number) {
                        String lk = k.toLowerCase();
                        if (lk.contains("balance") || lk.contains("credit")
                                || lk.contains("remaining") || lk.contains("available")) {
                            e.setValue(9999999);
                            SafeHook.countFeature("channel-map:" + k);
                        }
                    } else if (v instanceof String) {
                        String sv = (String) v;
                        if ("free".equals(sv) && (k.contains("plan") || k.contains("Type")
                                || k.contains("membership") || k.contains("status"))) {
                            e.setValue("plus");
                            SafeHook.countFeature("channel-map:" + k);
                        } else if ("never_purchased".equals(sv)) {
                            e.setValue("active");
                            SafeHook.countFeature("channel-map:" + k);
                        }
                    } else if (v instanceof Map) {
                        patchChannelMap((Map<?, ?>) v); // تعمق — خرائط متداخلة
                    } else if (v instanceof Boolean && !((Boolean) v)
                            && isPremiumQuery(k)) {
                        e.setValue(Boolean.TRUE);
                        SafeHook.countFeature("map:" + k);
                    } else if (v instanceof Integer && ((Integer) v) == 0
                            && isPremiumQuery(k)) {
                        e.setValue(Integer.valueOf(1));
                        SafeHook.countFeature("map:" + k);
                    }
                } catch (Throwable ignored) {}
            }
            return map;
        } catch (Throwable ignored) {
            return m;
        }
    }
}
