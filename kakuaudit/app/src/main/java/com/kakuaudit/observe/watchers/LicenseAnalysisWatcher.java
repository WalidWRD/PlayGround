package com.kakuaudit.observe.watchers;

import com.kakuaudit.observe.core.EventBus;

/** Spec §4: observes license state transitions, metadata-only. */
public final class LicenseAnalysisWatcher extends BaseWatcher {
    public LicenseAnalysisWatcher(EventBus bus, String sessionId, String pkg) {
        super("LicenseAnalysisWatcher", bus, sessionId, pkg);
    }

    public void onLicenseState(String clazz, String method, Object returnValue,
                               String consumer, String nextEvent) {
        // Only record presence/type — never the license payload itself.
        String label = returnValue == null ? "NULL" : "NON_NULL_"
                + returnValue.getClass().getSimpleName();
        emitState("License", clazz, method, label,
                typeMeta(returnValue, 512), consumer, nextEvent,
                0.6, new String[]{"DIRECT_RUNTIME_CALL"});
    }
}
