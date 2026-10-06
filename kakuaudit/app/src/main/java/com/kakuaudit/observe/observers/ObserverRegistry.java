package com.kakuaudit.observe.observers;

import com.kakuaudit.observe.core.ProtectedComponentRegistry;
import com.kakuaudit.observe.core.SafeGuard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Single place to add/remove observers. Each install is isolated: one
 * observer failing can never break the app or the other observers.
 */
public final class ObserverRegistry {
    private ObserverRegistry() {}

    public static List<KakuObserver> all() {
        List<KakuObserver> l = new ArrayList<>();
        l.add(new StoreObserver());
        l.add(new LifecycleObserver());
        l.add(new NativeNetObserver());
        return Collections.unmodifiableList(l);
    }

    public static void installAll(ClassLoader appLoader, ObserverCtx ctx) {
        for (KakuObserver o : all()) {
            // Config switch first (crash triage without rebuilding logic),
            // then SafeGuard kill-switch, each install fully isolated.
            // NativeNetObserver defaults OFF (Runtime.load* recursion risk).
            boolean def = !"NativeNetObserver".equals(o.name());
            if (!Config.observerEnabled(o.name(), def)) {
                ctx.prot.record(o.name(), "NOT_APPLICABLE",
                        "disabled-by-config-v3.1.1");
                continue;
            }
            if (SafeGuard.isKilled("observer:" + o.name())) continue;
            SafeGuard.runSafe("observer:" + o.name(), ctx.prot,
                    () -> o.install(appLoader, ctx));
        }
    }

    /** For tests / diagnostics. */
    public static List<String> names() {
        List<String> n = new ArrayList<>();
        for (KakuObserver o : all()) n.add(o.name());
        return n;
    }
}
