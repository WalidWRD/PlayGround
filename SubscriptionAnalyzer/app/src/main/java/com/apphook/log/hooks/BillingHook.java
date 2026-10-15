package com.apphook.log.hooks;

import com.apphook.log.Config;
import com.apphook.log.FingerprintEngine;
import com.apphook.log.SafeHook;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;

/** Features #1-3: Billing — تسجيل دائم + ترقيع اختياري (F003). */
public final class BillingHook {
    private BillingHook() {}

    public static void install(XposedInterface fw, ClassLoader cl) {
        Class<?> pc = FingerprintEngine.findClassQuiet(cl,
                "com.android.billingclient.api.Purchase");
        if (pc == null) {
            SafeHook.logSafe("Billing", "PBL absent");
            return;
        }
        Method state = FingerprintEngine.findMethodByFingerprint(pc,
                "getPurchaseState", int.class, 0);
        HookKit.hookOne(fw, state, "Billing.getPurchaseState", chain -> {
            Object orig = chain.proceed();
            Object patched = orig;
            if (Config.PATCH_BILLING && orig instanceof Integer && ((Integer) orig) != 0) {
                patched = 0;
                SafeHook.countOpen("billing_state", "F003");
            }
            SafeHook.recordCall("Billing", "getPurchaseState", 0, orig, patched);
            return patched; // القيمة المرجعة من intercept هي النتيجة النهائية
        });
        Method ack = FingerprintEngine.findMethodByFingerprint(pc,
                "isAcknowledged", boolean.class, 0);
        HookKit.hookOne(fw, ack, "Billing.isAcknowledged", chain -> {
            Object orig = chain.proceed();
            Object patched = orig;
            if (Config.PATCH_BILLING && Boolean.FALSE.equals(orig)) {
                patched = true;
                SafeHook.countOpen("billing_ack", "F003");
            }
            SafeHook.recordCall("Billing", "isAcknowledged", 0, orig, patched);
            return patched;
        });
        Class<?> bc = FingerprintEngine.findClassQuiet(cl,
                "com.android.billingclient.api.BillingClient");
        Method ready = bc != null ? FingerprintEngine.findMethodByFingerprint(bc,
                "isReady", boolean.class, 0) : null;
        HookKit.hookOne(fw, ready, "Billing.isReady", chain -> {
            Object orig = chain.proceed();
            if (Config.PATCH_BILLING && Boolean.FALSE.equals(orig)) {
                SafeHook.countOpen("billing_ready", "F003");
                return true;
            }
            SafeHook.recordCall("Billing", "isReady", 0, orig, orig);
            return orig;
        });
        SafeHook.log("Billing: hooked (patch=" + Config.PATCH_BILLING + ")");
    }
}
