package com.kakuaudit.observe.observers;

import com.kakuaudit.observe.core.EventBus;
import com.kakuaudit.observe.core.ProtectedComponentRegistry;
import com.kakuaudit.observe.watchers.EntitlementAnalysisWatcher;
import com.kakuaudit.observe.watchers.LicenseAnalysisWatcher;
import com.kakuaudit.observe.watchers.SubscriptionAnalysisWatcher;

import java.util.concurrent.atomic.AtomicLong;

/** Shared install context passed to every observer (maintainable in one place). */
public final class ObserverCtx {
    public final EventBus bus;
    public final AtomicLong seq;
    public final ProtectedComponentRegistry prot;
    public final String pkg;
    public final String sessionId;
    public final LicenseAnalysisWatcher lic;
    public final EntitlementAnalysisWatcher ent;
    public final SubscriptionAnalysisWatcher sub;

    public ObserverCtx(EventBus bus, AtomicLong seq, ProtectedComponentRegistry prot,
                       String pkg, String sessionId,
                       LicenseAnalysisWatcher lic,
                       EntitlementAnalysisWatcher ent,
                       SubscriptionAnalysisWatcher sub) {
        this.bus = bus; this.seq = seq; this.prot = prot;
        this.pkg = pkg; this.sessionId = sessionId;
        this.lic = lic; this.ent = ent; this.sub = sub;
    }
}
