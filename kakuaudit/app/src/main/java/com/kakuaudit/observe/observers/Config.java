package com.kakuaudit.observe.observers;

/**
 * Crash-triage switches (assets/kakuaudit-config.json).
 * NativeNetObserver (Runtime.load* hooks) is the classic recursion risk, so
 * it ships DISABLED by default in v3.1.1: enable it only after the target
 * proves stable with Store + Lifecycle observers.
 */
public final class Config {
    private static volatile String rawJson;

    private Config() {}

    static void loadFrom(android.content.Context moduleCtx) {
        if (rawJson != null) return;
        rawJson = BlueprintStore.readAsset(moduleCtx, "kakuaudit-config.json");
    }

    /** For unit tests. */
    public static void setForTests(String json) {
        rawJson = json == null ? "" : json;
    }

    public static void resetForTests() {
        rawJson = null;
        BlueprintStore.setForTests("");
    }

    public static boolean observerEnabled(String name, boolean def) {
        try {
            if (rawJson == null || rawJson.isEmpty()) return def;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "\"" + java.util.regex.Pattern.quote(name) + "\"\\s*:\\s*(true|false)")
                    .matcher(rawJson);
            if (m.find()) return Boolean.parseBoolean(m.group(1));
        } catch (Throwable ignore) {}
        return def;
    }
}
