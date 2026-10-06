package com.kakuaudit.observe.report;

/** Minimal JSON string builder — no external deps, safe on-device + JVM tests. */
public final class Jsons {
    private Jsons() {}

    public static String q(String s) {
        if (s == null) return "null";
        StringBuilder o = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': o.append("\\\""); break;
                case '\\': o.append("\\\\"); break;
                case '\n': o.append("\\n"); break;
                case '\r': o.append("\\r"); break;
                case '\t': o.append("\\t"); break;
                default:
                    if (c < 0x20) o.append(String.format("\\u%04x", (int) c));
                    else o.append(c);
            }
            if (o.length() > 60000) break;
        }
        return o.append('"').toString();
    }

    public static String kv(String k, String vJson) {
        return q(k) + ":" + vJson;
    }
}
