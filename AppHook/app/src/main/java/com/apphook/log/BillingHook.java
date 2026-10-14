package com.apphook.log;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;

/**
 * Feature 1+2: Google Play Billing — تجاوز حالة الشراء + حقن الاستعلام.
 * PBL v5-v8 مستقرة (Purchase/BillingClient محمية بـ keep).
 */
public final class BillingHook {
    private BillingHook() {}

    public static void install(XposedInterface fw, ClassLoader cl) {
        hookPurchaseState(fw, cl);
        hookIsAcknowledged(fw, cl);
        hookIsReady(fw, cl);
        hookPurchasesList(fw, cl);
        hookQueryPurchases(fw, cl);
    }

    private static void hookExecQuiet(XposedInterface fw, Method m,
                                      String event, HookActionProxy action) {
        if (m == null) {
            SafeHook.logSafe(event, "method-not-found (version/arch differs)");
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
                            try {
                                return chain.proceed();
                            } catch (Throwable t2) {
                                return null;
                            }
                        }
                    });
            SafeHook.log("Hook OK: " + event + " -> " + m);
        } catch (Throwable t) {
            SafeHook.logError(event, t);
        }
    }

    // getPurchaseState() -> int : 0=PURCHASED — نجبر 0
    private static void hookPurchaseState(XposedInterface fw, ClassLoader cl) {
        Class<?> c = FingerprintEngine.findClassQuiet(cl,
                "com.android.billingclient.api.Purchase");
        Method m = c != null ? FingerprintEngine.findMethodByFingerprint(
                c, "getPurchaseState", int.class, 0) : null;
        if (m == null && c != null) {
            // fallback: أي getPurchase* بلا وسائط يرجع int
            m = FingerprintEngine.findMethodByFingerprint(c, "getPurchase", int.class, 0);
        }
        hookExecQuiet(fw, m, "Purchase.getPurchaseState", (chain, orig) -> {
            SafeHook.logBilling("getPurchaseState", 0, orig);
            if (orig instanceof Integer && ((Integer) orig) != 0) {
                SafeHook.logBilling("getPurchaseState -> forced 0", 0, 0);
                return 0;
            }
            return orig;
        });
    }

    private static void hookIsAcknowledged(XposedInterface fw, ClassLoader cl) {
        Class<?> c = FingerprintEngine.findClassQuiet(cl,
                "com.android.billingclient.api.Purchase");
        Method m = c != null ? FingerprintEngine.findMethodByFingerprint(
                c, "isAcknowledged", boolean.class, 0) : null;
        hookExecQuiet(fw, m, "Purchase.isAcknowledged", (chain, orig) -> {
            SafeHook.logBilling("isAcknowledged", 0, orig);
            if (Boolean.FALSE.equals(orig)) {
                SafeHook.logBilling("isAcknowledged -> forced true", 0, true);
                return true;
            }
            return orig;
        });
    }

    private static void hookIsReady(XposedInterface fw, ClassLoader cl) {
        Class<?> c = FingerprintEngine.findClassQuiet(cl,
                "com.android.billingclient.api.BillingClient");
        Method m = c != null ? FingerprintEngine.findMethodByFingerprint(
                c, "isReady", boolean.class, 0) : null;
        hookExecQuiet(fw, m, "BillingClient.isReady", (chain, orig) -> {
            if (Boolean.FALSE.equals(orig)) {
                SafeHook.logBilling("isReady -> forced true", 0, true);
                return true;
            }
            return orig;
        });
    }

    private static void hookPurchasesList(XposedInterface fw, ClassLoader cl) {
        Class<?> c = FingerprintEngine.findClassQuiet(cl,
                "com.android.billingclient.api.Purchase$PurchasesResult");
        Method m = c != null ? FingerprintEngine.findMethodByFingerprint(
                c, "getPurchasesList", java.util.List.class, 0) : null;
        hookExecQuiet(fw, m, "PurchasesResult.getPurchasesList", (chain, orig) -> {
            int size = (orig instanceof java.util.List) ? ((java.util.List<?>) orig).size() : -1;
            SafeHook.logBilling("getPurchasesList", 0, size);
            return orig; // مراقبة فقط — الحقن الكامل يتطلب مصنع Purchase (هش) فنتجنبه
        });
    }

    private static void hookQueryPurchases(XposedInterface fw, ClassLoader cl) {
        Class<?> c = FingerprintEngine.findClassQuiet(cl,
                "com.android.billingclient.api.BillingClient");
        if (c == null) {
            SafeHook.logSafe("BillingClient.queryPurchasesAsync", "BillingClient absent");
            return;
        }
        try {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals("queryPurchasesAsync")) continue;
                try {
                    m.setAccessible(true);
                    final Method target = m;
                    fw.hook(target)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                SafeHook.logBilling("queryPurchasesAsync", chain.getArgs().size(), "-");
                                try {
                                    return chain.proceed();
                                } finally {
                                    SafeHook.logBilling("queryPurchasesAsync complete", 0, 0);
                                }
                            });
                    SafeHook.log("Hook OK: BillingClient.queryPurchasesAsync -> " + m);
                    return;
                } catch (Throwable t) {
                    SafeHook.logError("queryPurchasesAsync", t);
                }
            }
            SafeHook.logSafe("BillingClient.queryPurchasesAsync", "signature differs (PBL v9?)");
        } catch (Throwable t) {
            SafeHook.logError("queryPurchasesAsync-scan", t);
        }
    }
}
