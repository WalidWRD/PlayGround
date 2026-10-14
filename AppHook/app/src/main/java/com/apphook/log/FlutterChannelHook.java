package com.apphook.log;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * v3.0.1 — اعتراض قناة Flutter ↔ Native **بدون Proxy** (إصلاح كراش v3.0).
 *
 * <p>سبب كراش v3.0: استبدال وسيط {@code setMethodCallHandler} بكائن
 * {@code $Proxy} رفضه {@code Method.invoke} لأن النوع الدقيق
 * ({@code MethodChannel$MethodCallHandler}) لا يطابق الفئة الديناميكية —
 * {@code IllegalArgumentException} فوري.</p>
 *
 * <p>القاعدة الجديدة: **لا نستبدل أي وسيط أبداً**. بدلاً من ذلك:</p>
 * <ol>
 *   <li>{@code invokeMethod}: مراقبة فقط + ربط {@code success} على فئة
 *       الـ Result الحقيقية.</li>
 *   <li>{@code setMethodCallHandler}: قراءة فقط — نربط {@code onMethodCall}
 *       على فئة الـ handler الحقيقية (تشمل الموروث والـ lambdas).</li>
 *   <li>اعتراض استعلام اشتراك: نستدعي {@code result.success(TRUE)} الأصلي
 *       ثم **نتخطى** الأصلي بإرجاع null دون {@code proceed()}.</li>
 *   <li>ترقيع {@code success(value)}: نفس النوع دائماً عبر
 *       {@code proceed(newArgs)} — لا {@code ClassCastException}.</li>
 *   <li>مجموعتا dedup تمنعان الـ double-hook (ضجيج الـ 60+ سطر).</li>
 * </ol>
 *
 * <p>ملاحظة API 102: لا يوجد XposedHelpers — نستخدم Class.forName(cl)
 * + fw.hook(Method) PROTECTIVE + ReflectCall + hookAll الخاصة بنا.</p>
 */
public final class FlutterChannelHook {
    private FlutterChannelHook() {}

    private static final String TAG = "AppHook.Flutter";

    /** تُشارك مع LegacyBridge — لا تحذف */
    static final Set<String> hookedHandlers =
            Collections.synchronizedSet(new HashSet<String>());
    static final Set<String> hookedResults =
            Collections.synchronizedSet(new HashSet<String>());

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
        Class<?> mc = FingerprintEngine.findClassQuiet(cl,
                "io.flutter.plugin.common.MethodChannel");
        if (mc == null) {
            SafeHook.logSafe("Flutter.MethodChannel", "not a Flutter app or engine absent");
            return;
        }
        hookInvokeMethod(fw, mc);
        hookSetHandler(fw, mc);
        SafeHook.log(TAG + ": MethodChannel hooked (crash-safe, proxy-free)");
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
        hookAll(fw, handlerClass, "onMethodCall", 2, 2,
                "Flutter.onMethodCall", chain -> {
                    Object call = chain.getArg(0);
                    Object result = chain.getArg(1);
                    if (call == null) return chain.proceed();
                    String mName = (String) ReflectCall.call(call, "method");
                    if (mName == null) return chain.proceed();
                    if (isNoise(mName)) return chain.proceed(); // إسكات الضجيج
                    if (!isPremiumQuery(mName)) return chain.proceed(); // المهم فقط
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
                    }
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

    /** 4) ربط success/error/notImplemented على فئة الـ Result (مرة واحدة). */
    static void wrapResultSafe(XposedInterface fw, Class<?> resultClass) {
        wrapResultSafe(fw, resultClass, "?");
    }

    static void wrapResultSafe(XposedInterface fw, Class<?> resultClass,
                               final String methodName) {
        if (resultClass == null) return;
        if (ProxyMarker.isProxy(resultClass)) return; // لا نربط فئات Proxy أبداً
        if (!hookedResults.add(resultClass.getName())) return; // dedup
        // success: ترقيع بنفس النوع + تسجيل
        hookAll(fw, resultClass, "success", 1, 1, "Flutter.Result.success", chain -> {
            Object value = chain.getArg(0);
            SafeHook.log(TAG + "   Result.success for " + methodName
                    + " arg=" + capped(String.valueOf(value)));
            Object mod = modifyResult(value);
            if (mod != value) {
                SafeHook.log(TAG + ": Result.success patched ("
                        + typeOf(value) + "->" + typeOf(mod) + ")");
                SafeHook.countFeature("result:success");
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

    /** نفس النوع دائماً: Boolean→Boolean / Integer→Integer / Map/List داخلها. */
    static Object modifyResult(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean) {
            return ((Boolean) value) ? value : Boolean.TRUE;
        }
        if (value instanceof Integer) {
            return ((Integer) value) == 0 ? Integer.valueOf(1) : value;
        }
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) value;
            try {
                for (Map.Entry<Object, Object> e : map.entrySet()) {
                    String k = String.valueOf(e.getKey());
                    if (isPremiumQuery(k)) {
                        Object v = e.getValue();
                        if (v instanceof Boolean && !((Boolean) v)) {
                            e.setValue(Boolean.TRUE);
                            SafeHook.countFeature("map:" + k);
                        } else if (v instanceof Integer && ((Integer) v) == 0) {
                            e.setValue(Integer.valueOf(1));
                            SafeHook.countFeature("map:" + k);
                        }
                    }
                }
            } catch (Throwable ignored) {}
            return map;
        }
        if (value instanceof List) {
            try {
                for (Object item : (List<?>) value) modifyResult(item);
            } catch (Throwable ignored) {}
            return value;
        }
        return value;
    }
}
