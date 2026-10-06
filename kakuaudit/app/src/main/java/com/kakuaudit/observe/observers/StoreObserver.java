package com.kakuaudit.observe.observers;

import com.kakuaudit.observe.core.ReflectionDiscovery;

/**
 * Billing / license / entitlement observation WITHOUT static names:
 * candidates + behavioral pattern come from the stored blueprint
 * (assets/module-blueprint.json), scanned via lpparam.classLoader.
 * Name match alone = DISCOVERY_HINT (≤0.24) until a runtime call is observed.
 */
public final class StoreObserver implements KakuObserver {
    @Override public String name() { return "StoreObserver"; }

    @Override public void install(ClassLoader appLoader, ObserverCtx ctx) {
        for (String cname : BlueprintStore.candidates("billingCandidates",
                "com.android.billingclient.api.BillingClient",
                "com.android.billingclient.api.PurchasesUpdatedListener",
                "com.android.vending.billing.IInAppBillingService")) {
            Class<?> c = ReflectionDiscovery.loadBest(appLoader, cname);
            if (c == null) {
                ctx.prot.record(cname, "DYNAMICALLY_LOADED",
                        "not-present-in-this-version");
                continue;
            }
            ReflectionDiscovery.Query q = HookKit.q(BlueprintStore.pattern(
                    "billingMethodPattern",
                    "(?i)(purchase|quer|license|entitle|subscri|consum|acknowledge)"));
            HookKit.hookDiscovered(c, q, appLoader, ctx.prot, ctx.seq, ctx.bus,
                    (clazz, method, ret, s) -> {
                        String lower = (clazz + method).toLowerCase();
                        if (lower.contains("licen")) {
                            ctx.lic.onLicenseState(clazz, method, ret,
                                    "UNKNOWN", "NOT_OBSERVED");
                        } else if (lower.contains("entitle")) {
                            ctx.ent.onEntitlement(clazz, method, ret,
                                    "UNKNOWN", "NOT_OBSERVED");
                        } else {
                            ctx.sub.onSubscription(clazz, method, ret,
                                    "UNKNOWN", "NOT_OBSERVED");
                        }
                    });
        }
    }
}
