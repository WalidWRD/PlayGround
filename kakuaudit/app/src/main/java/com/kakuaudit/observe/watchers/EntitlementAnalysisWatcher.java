package com.kakuaudit.observe.watchers;

import com.kakuaudit.observe.core.EventBus;

public final class EntitlementAnalysisWatcher extends BaseWatcher {
    public EntitlementAnalysisWatcher(EventBus bus, String sessionId, String pkg) {
        super("EntitlementAnalysisWatcher", bus, sessionId, pkg);
    }

    public void onEntitlement(String clazz, String method, Object returnValue,
                              String consumer, String nextEvent) {
        String label = returnValue == null ? "NULL" : "NON_NULL_"
                + returnValue.getClass().getSimpleName();
        emitState("Entitlement", clazz, method, label,
                typeMeta(returnValue, 512), consumer, nextEvent,
                0.6, new String[]{"DIRECT_RUNTIME_CALL"});
    }
}
