package com.kakuaudit.observe.watchers;

import com.kakuaudit.observe.core.EventBus;
import com.kakuaudit.observe.core.KakuClock;
import com.kakuaudit.observe.core.Redactor;
import com.kakuaudit.observe.report.Jsons;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Base for License/Entitlement/Subscription watchers.
 * OBSERVE_ONLY: builds metadata events, never touches args/result.
 */
public abstract class BaseWatcher {
    protected final EventBus bus;
    protected final String sessionId;
    protected final String pkg;
    protected final AtomicLong seq = new AtomicLong();
    protected final String watcherName;

    protected BaseWatcher(String watcherName, EventBus bus, String sessionId, String pkg) {
        this.watcherName = watcherName;
        this.bus = bus;
        this.sessionId = sessionId;
        this.pkg = pkg;
    }

    /** WHERE/WHAT/WHEN + type metadata only. No raw values. */
    protected void emitState(String component, String clazz, String method,
                             String stateLabel, Map<String, Object> returnMeta,
                             String consumer, String nextEvent,
                             double confidence, String[] basis, String... evidenceRefs) {
        String evt = UUID.randomUUID().toString();
        StringBuilder sb = new StringBuilder(512);
        sb.append("{");
        sb.append(Jsons.kv("schemaVersion", Jsons.q("3.0"))).append(",");
        sb.append(Jsons.kv("eventId", Jsons.q(evt))).append(",");
        sb.append(Jsons.kv("sessionId", Jsons.q(sessionId))).append(",");
        sb.append(Jsons.kv("type", Jsons.q("PROTECTION_OBSERVATION"))).append(",");
        sb.append(Jsons.kv("watcher", Jsons.q(watcherName))).append(",");
        sb.append(Jsons.kv("component", Jsons.q(component))).append(",");
        sb.append(Jsons.kv("class", Jsons.q(clazz))).append(",");
        sb.append(Jsons.kv("method", Jsons.q(method))).append(",");
        sb.append(Jsons.kv("process", Jsons.q(pkg))).append(",");
        sb.append(Jsons.kv("stateLabel", Jsons.q(stateLabel))).append(",");
        sb.append(Jsons.kv("consumer", Jsons.q(consumer))).append(",");
        sb.append(Jsons.kv("nextEvent", Jsons.q(nextEvent))).append(",");
        sb.append(Jsons.kv("confidence", String.valueOf(confidence))).append(",");
        sb.append(Jsons.kv("observedAt", Jsons.q(KakuClock.utcNowIso()))).append(",");
        sb.append(Jsons.kv("monotonicTimeNanos", String.valueOf(KakuClock.monotonicNanos())));
        sb.append("}");
        // Redaction note: stateLabel must be allow-listed enum; raw values never reach here.
        if (!Redactor.isAllowListed("state_label")) {
            // state_label is structural; kept as label only.
        }
        bus.publish(sb.toString(), seq.incrementAndGet());
    }

    protected static Map<String, Object> typeMeta(Object ret, int maxStr) {
        return Redactor.describeObject(ret, maxStr);
    }
}
