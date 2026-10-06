package com.kakuaudit.observe.observers;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runtime view of the precomputed analysis (assets/module-blueprint.json).
 * The module reads candidates/patterns from the stored blueprint instead of
 * re-analyzing on every launch. Falls back to built-in defaults when the
 * asset is missing so old installs never crash.
 */
public final class BlueprintStore {
    private static volatile String rawJson;
    private static final Object LOCK = new Object();

    private BlueprintStore() {}

    /** Load once from module assets (never from the target app). */
    public static void load(android.content.Context moduleCtx) {
        if (rawJson != null || moduleCtx == null) return;
        synchronized (LOCK) {
            if (rawJson != null) return;
            rawJson = readAsset(moduleCtx, "module-blueprint.json");
            Config.loadFrom(moduleCtx);
        }
    }

    /** For unit tests: inject JSON directly. */
    public static void setForTests(String json) {
        rawJson = json == null ? "" : json;
    }

    static String readAsset(android.content.Context ctx, String name) {
        try {
            java.io.InputStream is = ctx.getAssets().open(name);
            byte[] buf = new byte[65536];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = is.read(buf)) > 0) {
                sb.append(new String(buf, 0, n, "UTF-8"));
                if (sb.length() > 60000) break;
            }
            try { is.close(); } catch (Throwable ignore) {}
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    public static List<String> candidates(String key, String... defaults) {
        List<String> fromFile = stringArray(key);
        if (!fromFile.isEmpty()) return fromFile;
        List<String> d = new ArrayList<>();
        for (String s : defaults) d.add(s);
        return d;
    }

    public static String pattern(String key, String def) {
        String v = stringValue(key);
        return v == null || v.isEmpty() ? def : v;
    }

    // Minimal JSON extraction (string arrays + string values only) — no deps.
    static List<String> stringArray(String key) {
        List<String> out = new ArrayList<>();
        try {
            String json = rawJson;
            if (json == null || json.isEmpty()) return out;
            Matcher m = Pattern.compile("\"" + Pattern.quote(key)
                    + "\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL).matcher(json);
            if (!m.find()) return out;
            Matcher sm = Pattern.compile("\"([^\"]+)\"").matcher(m.group(1));
            while (sm.find()) out.add(sm.group(1));
        } catch (Throwable ignore) {}
        return out;
    }

    static String stringValue(String key) {
        try {
            String json = rawJson;
            if (json == null || json.isEmpty()) return null;
            Matcher m = Pattern.compile("\"" + Pattern.quote(key)
                    + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
            return m.find() ? m.group(1) : null;
        } catch (Throwable ignore) {
            return null;
        }
    }
}
