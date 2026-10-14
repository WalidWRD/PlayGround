package com.apphook.log;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;

/**
 * Feature 3: RevenueCat — تفعيل الاستحقاقات.
 * الأساس: EntitlementInfo.isActive() -> true (مستقرة).
 * البديل: مراقبة CustomerInfo.getEntitlements (حساسة لـ R8).
 */
public final class RevenueCatHook {
    private RevenueCatHook() {}

    public static void install(XposedInterface fw, ClassLoader cl) {
        hookEntitlementActive(fw, cl);
        hookBillingIssue(fw, cl);
        watchCustomerInfo(fw, cl);
    }

    private static void hookQuiet(XposedInterface fw, Method m, String event,
                                  HookActionProxy action) {
        if (m == null) {
            SafeHook.logSafe(event, "method-not-found");
            return;
        }
        try {
            fw.hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            Object orig = chain.proceed();
                            return action.run(chain, orig);
                        } catch (Throwable t) {
                            SafeHook.logError(event, t);
                            return chain.proceed();
                        }
                    });
            SafeHook.log("Hook OK: " + event + " -> " + m);
        } catch (Throwable t) {
            SafeHook.logError(event, t);
        }
    }

    private static void hookEntitlementActive(XposedInterface fw, ClassLoader cl) {
        Class<?> c = FingerprintEngine.findClassQuiet(cl,
                "com.revenuecat.purchases.EntitlementInfo");
        Method m = c != null ? FingerprintEngine.findMethodByFingerprint(
                c, "isActive", boolean.class, 0) : null;
        if (m == null && c != null) {
            m = FingerprintEngine.findMethodByFingerprint(c, "active", boolean.class, 0);
        }
        hookQuiet(fw, m, "EntitlementInfo.isActive", (chain, orig) -> {
            SafeHook.logBilling("RevenueCat.isActive", 0, orig);
            if (Boolean.FALSE.equals(orig)) {
                SafeHook.countFeature("revenuecat-entitlement");
                return true;
            }
            return orig;
        });
    }

    private static void hookBillingIssue(XposedInterface fw, ClassLoader cl) {
        Class<?> c = FingerprintEngine.findClassQuiet(cl,
                "com.revenuecat.purchases.EntitlementInfo");
        Method m = c != null ? FingerprintEngine.findMethodByFingerprint(
                c, "getBillingIssueDetectedAt", java.util.Date.class, 0) : null;
        if (m == null && c != null) {
            m = FingerprintEngine.findMethodByFingerprint(c, "billingIssue", null, 0);
        }
        hookQuiet(fw, m, "EntitlementInfo.billingIssue", (chain, orig) -> {
            if (orig != null) {
                SafeHook.log("[Billing] billingIssueDetectedAt cleared (" + orig + " -> null)");
                return null;
            }
            return orig;
        });
    }

    private static void watchCustomerInfo(XposedInterface fw, ClassLoader cl) {
        Class<?> c = FingerprintEngine.findClassQuiet(cl,
                "com.revenuecat.purchases.CustomerInfo");
        Method m = c != null ? FingerprintEngine.findMethodByFingerprint(
                c, "getEntitlements", null, 0) : null;
        hookQuiet(fw, m, "CustomerInfo.getEntitlements(watch)", (chain, orig) -> {
            SafeHook.logBilling("RevenueCat.getEntitlements", 0, orig != null ? "present" : "null");
            return orig;
        });
    }
}
