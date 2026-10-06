package com.kakuaudit.observe.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spec §3: packed / obfuscated / encrypted / dynamically-loaded components
 * are RECORDED, never defeated. Failure is isolated; analysis continues.
 */
public final class ProtectedComponentRegistry {
    public static final class Entry {
        public final String component;
        public final String reason; // OBFUSCATED|PACKED|ENCRYPTED|DYNAMICALLY_LOADED|ACCESS_DENIED|UNSUPPORTED_FORMAT
        public final String evidence;
        public final boolean continuedObservation = true;
        Entry(String c, String r, String e) { component = c; reason = r; evidence = e; }
    }

    private final Map<String, Entry> map = new ConcurrentHashMap<>();

    public void record(String component, String reason, String evidence) {
        if (component == null) component = "UNKNOWN";
        map.put(component, new Entry(component, reason, evidence));
    }

    public List<Entry> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(map.values()));
    }

    public int size() { return map.size(); }

    /** Heuristic hint only (DISCOVERY_HINT, confidence capped at 0.24). */
    public static String classifyLoaderChain(String chain) {
        if (chain == null) return "DYNAMICALLY_LOADED";
        String c = chain.toLowerCase();
        if (c.contains("alibaba") || c.contains("tencent") || c.contains("qihoo")
                || c.contains("bangcle") || c.contains("ijiami") || c.contains("secneo")
                || c.contains("packed") || c.contains("protect")) return "PACKED";
        if (c.contains("dexclassloader") || c.contains("pathclassloader")) return "DYNAMICALLY_LOADED";
        return "OBFUSCATED";
    }
}
