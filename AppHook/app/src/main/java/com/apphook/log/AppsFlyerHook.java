package com.apphook.log;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;

/**
 * v3.1 — رصد أحداث AppsFlyer (يتتبع أحداث الشراء → يحدد لحظة التحقق).
 * hookAll بلا توقيع (يتغير بين الإصدارات) — تسجيل أحداث الشراء/الاشتراك فقط.
 */
public final class AppsFlyerHook {
    private AppsFlyerHook() {}

    private static final String TAG = "AppHook.AppsFlyer";

    public static void install(XposedInterface fw, ClassLoader cl) {
        Class<?> af = FingerprintEngine.findClassQuiet(cl, "com.appsflyer.AppsFlyerLib");
        if (af == null) {
            SafeHook.logSafe(TAG + ".logEvent", "AppsFlyer absent");
            return;
        }
        try {
            boolean any = false;
            for (Method m : af.getDeclaredMethods()) {
                if (!m.getName().equals("logEvent")) continue;
                try {
                    m.setAccessible(true);
                    final Method target = m;
                    fw.hook(target)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                try {
                                    for (Object a : chain.getArgs()) {
                                        if (a instanceof String
                                                && isPurchaseEvent((String) a)) {
                                            SafeHook.log(TAG + ": event: " + a);
                                            SafeHook.countFeature("appsflyer:" + a);
                                            break;
                                        }
                                    }
                                } catch (Throwable t) {
                                    SafeHook.logError(TAG + ".logEvent", t);
                                }
                                return chain.proceed();
                            });
                    SafeHook.log("Hook OK: AppsFlyer.logEvent -> " + m);
                    any = true;
                } catch (Throwable t) {
                    SafeHook.logError(TAG + ".logEvent", t);
                }
            }
            if (!any) SafeHook.logSafe(TAG + ".logEvent", "no overload matched");
        } catch (Throwable t) {
            SafeHook.logError(TAG + ".scan", t);
        }
    }

    static boolean isPurchaseEvent(String e) {
        if (e == null) return false;
        String l = e.toLowerCase();
        return l.contains("purchase") || l.contains("subscri")
                || l.contains("premium") || l.contains("billing")
                || l.contains("af_purchase");
    }
}
