package com.genspark.updatekiller.hooks;

import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Discovery;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.SelfTest;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.UxLog;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;

/**
 * جديد 2.0.0 — «اكتشاف Flutter» الديناميكي + اعتراض القنوات وقت التشغيل.
 *
 * لا أسماء فئات ثابتة: كل الأصناف تُكتشف من فهرس DEX:
 *  1) MethodChannel (وأي صنف مشابه) → نربط setMethodCallHandler ونُغلّف المُعالِج
 *     الأصلي في Proxy (java.lang.reflect.Proxy) — فيمرّ كل نداء عبر مُعترِضنا:
 *       • نداءات تحديث محفوفة المخاطر (openStoreListing) تُحيدة آمنًا.
 *       • أي نداء آخر يُمرَّر كما هو (سلوك التطبيق الأصلي محفوظ).
 *     هذا يعمل مع أي إصدار Flutter لأن الواجهة تُملأ بالانعكاس.
 *  2) Result.success(Object) → إعادة كتابة خرائط package_info
 *     (version / buildNumber / versionCode) بقيم مُزوَّرة، فتظل الدالة التي تقارن
 *     localVersionCode &lt; minAppVersionCode غير محقّقة حتى لو قرأ Flutter الإصدار من Dart.
 */
public final class FlutterDiscoveryHook {

    /** نداءات تُحيدة بالكامل (آمنة لأن عقدها success(null)). */
    private static final String[] NEUTRALIZE = { "openStoreListing" };

    /** نداءات تُسجَّل فقط (لا تُعدَّل — حماية من كسر عقود Dart). */
    private static final String[] OBSERVE = {
        "checkUpdate", "forceUpdate", "appUpdate", "updateNow", "installUpdate",
        "getAppVersion", "upgrade", "shouldUpgrade"
    };

    private static final Set<String> HOOKED = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static volatile int total = 0;

    private FlutterDiscoveryHook() { }

    public static int install(ClassLoader cl) {
        final Config cfg = Config.get();
        if (!cfg.flutterDiscovery || cl == null) return 0;

        int before = total;
        try {
            // ── 1) قنوات Flutter → تغليف المُعالِج ──
            List<Class<?>> channels = Discovery.classesByName(cl, new String[]{"methodchannel"}, 40);
            for (Class<?> mc : channels) {
                if (mc == null || mc.isInterface()) continue;
                if (!HOOKED.add("wrap:" + mc.getName())) continue;
                for (Method m : Reflect.methodsNamed(mc, "setMethodCallHandler")) {
                    if (m.getParameterTypes().length != 1) continue;
                    final Class<?> handlerType = m.getParameterTypes()[0];
                    if (handlerType == null || !handlerType.isInterface()) continue;
                    if (Reflect.hook(m, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            Guard.run("FlutterDiscovery.setMethodCallHandler", () -> {
                                if (p.args == null || p.args.length != 1 || p.args[0] == null) return;
                                Object original = p.args[0];
                                if (Proxy.isProxyClass(original.getClass())) return;
                                p.args[0] = wrapHandler(handlerType, original);
                                UxLog.d("FlutterDiscovery: channel handler intercepted (" + original.getClass().getName() + ")");
                            });
                        }
                    })) total++;
                }
            }

            // ── 2) إعادة كتابة نتيجة package_info ──
            if (cfg.flutterResultRewrite) {
                List<Class<?>> results = Discovery.classesByName(cl, new String[]{"result"}, 60);
                for (Class<?> r : results) {
                    if (r == null || r.isInterface()) continue;
                    String ln = r.getName().toLowerCase(java.util.Locale.US);
                    if (ln.indexOf("methodchannel") < 0 && ln.indexOf("flutter") < 0) continue;
                    if (!HOOKED.add("result:" + r.getName())) continue;
                    total += Reflect.hookBySignature(r, new String[]{"success"},
                            new String[]{"java.lang.Object"}, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            Guard.run("FlutterDiscovery.Result.success", () -> {
                                if (p.args == null || p.args.length != 1) return;
                                Object a = p.args[0];
                                if (!(a instanceof Map)) return;
                                Map<?, ?> in = (Map<?, ?>) a;
                                if (!looksLikePackageInfo(in)) return;
                                Map<Object, Object> out = rewritePackageInfo(in);
                                p.args[0] = out;
                                UpdateEventLogger.log("channel", "package_info.Result rewritten → version="
                                        + out.get("version") + ", buildNumber=" + out.get("buildNumber")
                                        + " (منع ظهور شرط الحد الأدنى للإصدار)");
                                UxLog.i("FlutterDiscovery: package_info rewritten ("
                                        + in.get("version") + " → " + out.get("version") + ")");
                            });
                        }
                    });
                }
            }
        } catch (Throwable t) {
            Guard.record("FlutterDiscovery.install", t);
        }

        int added = total - before;
        if (added > 0) {
            SelfTest.record("flutterDiscovery", total);
            UxLog.i("FlutterDiscovery: +" + added + " hook(s) (total " + total + ")");
        }
        return total;
    }

    private static Object wrapHandler(Class<?> handlerType, final Object original) {
        InvocationHandler ih = new InvocationHandler() {
            @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                final String mn = (method == null) ? "" : method.getName();
                if ("onMethodCall".equals(mn) && args != null && args.length >= 2) {
                    String callName = safeStr(Reflect.invokeNoArg(args[0], "getMethod"));
                    String argStr = safeStr(Reflect.invokeNoArg(args[0], "arguments"));
                    boolean neutralize = matches(NEUTRALIZE, callName) || matches(NEUTRALIZE, argStr);
                    if (neutralize) {
                        success(args[1], null);
                        UpdateEventLogger.log("channel", "neutralized channel call '" + callName
                                + "'" + (argStr.length() > 0 ? " args=" + argStr : ""));
                        UxLog.i("FlutterDiscovery: neutralized '" + callName + "'");
                        return null;
                    }
                    if (matches(OBSERVE, callName) || matches(OBSERVE, argStr)) {
                        UpdateEventLogger.log("channel", "observed update-related call '" + callName
                                + "'" + (argStr.length() > 0 ? " args=" + argStr : ""));
                        UxLog.d("FlutterDiscovery: observed '" + callName + "'");
                    }
                }
                if ("toString".equals(mn)) return "GenSubsFlutterInterceptor";
                if ("hashCode".equals(mn)) return Integer.valueOf(System.identityHashCode(proxy));
                if ("equals".equals(mn) && args != null && args.length == 1) return Boolean.valueOf(proxy == args[0]);
                try {
                    if (!method.isAccessible()) method.setAccessible(true);
                    return method.invoke(original, args);
                } catch (Throwable t) {
                    Guard.record("FlutterDiscovery.forward(" + mn + ")", t);
                    return null;
                }
            }
        };
        try {
            return Proxy.newProxyInstance(handlerType.getClassLoader(), new Class<?>[]{handlerType}, ih);
        } catch (Throwable t) {
            Guard.record("FlutterDiscovery.newProxyInstance", t);
            return original;
        }
    }

    private static void success(Object result, Object value) {
        try {
            if (result == null) return;
            for (Method m : Reflect.methodsNamed(result.getClass(), "success")) {
                try { m.setAccessible(true); } catch (Throwable ignored) { }
                Object[] a = new Object[m.getParameterTypes().length];
                if (a.length == 1) a[0] = value;
                if (a.length <= 1) {
                    try { m.invoke(result, a); return; } catch (Throwable ignored) { }
                }
            }
            Reflect.invoke(result, null, "success", new Object[]{value});
        } catch (Throwable t) {
            Guard.record("FlutterDiscovery.success", t);
        }
    }

    static boolean looksLikePackageInfo(Map<?, ?> m) {
        if (m == null || m.isEmpty()) return false;
        boolean name = m.containsKey("appName") || m.containsKey("packageName");
        boolean ver = m.containsKey("version") || m.containsKey("buildNumber") || m.containsKey("versionCode");
        return name && ver;
    }

    private static Map<Object, Object> rewritePackageInfo(Map<?, ?> in) {
        Config cfg = Config.get();
        Map<Object, Object> out = new HashMap<Object, Object>();
        for (Map.Entry<?, ?> e : in.entrySet()) {
            Object k = e.getKey();
            Object v = e.getValue();
            String ks = String.valueOf(k);
            if ("version".equals(ks)) v = cfg.spoofVersionName;
            else if ("buildNumber".equals(ks)) v = String.valueOf(cfg.spoofVersionCode);
            else if ("versionCode".equals(ks)) v = Integer.valueOf(cfg.spoofVersionCode);
            out.put(k, v);
        }
        return out;
    }

    private static boolean matches(String[] arr, String s) {
        if (s == null) return false;
        for (String a : arr) if (a != null && s.indexOf(a) >= 0) return true;
        return false;
    }

    private static String safeStr(Object o) {
        return (o == null) ? "" : String.valueOf(o);
    }
}
