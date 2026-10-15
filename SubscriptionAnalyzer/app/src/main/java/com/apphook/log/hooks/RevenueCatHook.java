package com.apphook.log.hooks;

import com.apphook.log.Config;
import com.apphook.log.FingerprintEngine;
import com.apphook.log.SafeHook;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;

/** Features #4-5: RevenueCat — تسجيل دائم + ترقيع اختياري (F001). */
public final class RevenueCatHook {
    private RevenueCatHook() {}

    public static void install(XposedInterface fw, ClassLoader cl) {
        Class<?> ei = FingerprintEngine.findClassQuiet(cl,
                "com.revenuecat.purchases.EntitlementInfo");
        if (ei == null) {
            SafeHook.logSafe("RevenueCat", "SDK absent");
            return;
        }
        Method active = FingerprintEngine.findMethodByFingerprint(ei,
                "isActive", boolean.class, 0);
        HookKit.hookOne(fw, active, "RevenueCat.isActive", chain -> {
            Object orig = chain.proceed();
            Object patched = orig;
            if (Config.PATCH_REVENUECAT && Boolean.FALSE.equals(orig)) {
                patched = true;
                SafeHook.countOpen("rc_entitlement", "F001");
            }
            SafeHook.recordCall("RevenueCat", "isActive", 0, orig, patched);
            return patched;
        });
        Method issue = FingerprintEngine.findMethodByFingerprint(ei,
                "getBillingIssueDetectedAt", null, 0);
        HookKit.hookOne(fw, issue, "RevenueCat.getBillingIssueDetectedAt", chain -> {
            Object orig = chain.proceed();
            if (Config.PATCH_REVENUECAT && orig != null) {
                SafeHook.countOpen("rc_issue_cleared", "F001");
                SafeHook.recordCall("RevenueCat", "getBillingIssueDetectedAt",
                        0, orig, null);
                return null;
            }
            SafeHook.recordCall("RevenueCat", "getBillingIssueDetectedAt",
                    0, orig, orig);
            return orig;
        });
        SafeHook.log("RevenueCat: hooked (patch=" + Config.PATCH_REVENUECAT + ")");
    }
}
