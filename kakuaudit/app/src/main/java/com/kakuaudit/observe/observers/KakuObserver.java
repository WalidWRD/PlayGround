package com.kakuaudit.observe.observers;

import com.kakuaudit.observe.core.EventBus;
import com.kakuaudit.observe.core.ProtectedComponentRegistry;
import com.kakuaudit.observe.watchers.EntitlementAnalysisWatcher;
import com.kakuaudit.observe.watchers.LicenseAnalysisWatcher;
import com.kakuaudit.observe.watchers.SubscriptionAnalysisWatcher;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Extension point for all observers. To add a new observer:
 * 1) implement this interface, 2) register in ObserverRegistry.
 * Never throw — install() must isolate its own failures.
 */
public interface KakuObserver {
    String name();
    void install(ClassLoader appLoader, ObserverCtx ctx);
}
