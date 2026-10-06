package com.kakuaudit.observe.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Spec §10: privacy-by-default. Metadata-only; raw values need allow-list.
 * All redaction happens BEFORE persistence (runtime.jsonl / report / index / crash / html).
 */
public final class Redactor {

    /** Fields that must never be persisted in raw form. */
    private static final Set<String> DENY_KEYS;
    /** Case-insensitive substring match on key names. */
    private static final Pattern DENY_PATTERN = Pattern.compile(
            "(password|passwd|pwd|authorization|bearer|jwt|refresh.?token|access.?token|" +
            "id.?token|cookie|set-cookie|api.?key|x-api-key|purchase.?token|order.?id|" +
            "payment|card.?number|cvv|session.?secret|private.?key|otp|one.?time|" +
            "credentials|secret|auth.?token)",
            Pattern.CASE_INSENSITIVE);

    /** Small allow-list of demonstrably non-sensitive enum/state labels. */
    private static final Set<String> ALLOW_KEYS;

    static {
        Set<String> deny = new HashSet<>();
        deny.add("password"); deny.add("authorization"); deny.add("jwt");
        deny.add("cookie"); deny.add("api_key"); deny.add("purchase_token");
        deny.add("refresh_token"); deny.add("private_key"); deny.add("otp");
        DENY_KEYS = Collections.unmodifiableSet(deny);

        Set<String> allow = new HashSet<>();
        allow.add("feature_state"); allow.add("lifecycle_state");
        allow.add("http_method"); allow.add("status_code");
        allow.add("result_type"); allow.add("state_label");
        ALLOW_KEYS = Collections.unmodifiableSet(allow);
    }

    public static final class Redaction {
        public final String field;
        public final String category;
        public final String action = "REDACTED";
        public final String reason;
        public final String originalType;
        public final int originalLength;
        public Redaction(String field, String category, String reason,
                         String originalType, int originalLength) {
            this.field = field;
            this.category = category;
            this.reason = reason;
            this.originalType = originalType;
            this.originalLength = originalLength;
        }
    }

    private Redactor() {}

    /** True if this key must be redacted regardless of value. */
    public static boolean mustRedact(String key) {
        if (key == null) return false;
        String k = key.toLowerCase(Locale.US);
        if (DENY_KEYS.contains(k)) return true;
        return DENY_PATTERN.matcher(key).find();
    }

    public static boolean isAllowListed(String key) {
        return key != null && ALLOW_KEYS.contains(key.toLowerCase(Locale.US));
    }

    /** Metadata descriptor for a String value (bounded length, no raw). */
    public static Map<String, Object> describeString(String value, int maxLen) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", "String");
        m.put("null", value == null);
        if (value != null) {
            m.put("length", Math.min(value.length(), maxLen));
            m.put("truncated", value.length() > maxLen);
        }
        return m;
    }

    public static Map<String, Object> describeObject(Object o, int maxString) {
        Map<String, Object> m = new HashMap<>();
        if (o == null) {
            m.put("type", "null");
            m.put("null", true);
            return m;
        }
        m.put("type", o.getClass().getName());
        m.put("null", false);
        if (o instanceof CharSequence) {
            m.put("length", Math.min(((CharSequence) o).length(), maxString));
        } else if (o instanceof java.util.Collection) {
            m.put("size", ((java.util.Collection<?>) o).size());
        } else if (o instanceof java.util.Map) {
            m.put("size", ((java.util.Map<?, ?>) o).size());
        }
        return m;
    }

    /** Sanitize a URL: strip query values known to carry credentials/ids. */
    public static String sanitizeUrl(String url) {
        if (url == null) return null;
        int q = url.indexOf('?');
        if (q < 0) return url.length() > 512 ? url.substring(0, 512) : url;
        String base = url.substring(0, q);
        // Keep keys, drop values: ?a=REDACTED&b=REDACTED
        String query = url.substring(q + 1);
        StringBuilder out = new StringBuilder(base).append('?');
        String[] pairs = query.split("&");
        for (int i = 0; i < pairs.length; i++) {
            if (i > 0) out.append('&');
            String p = pairs[i];
            int eq = p.indexOf('=');
            String k = eq < 0 ? p : p.substring(0, eq);
            out.append(k).append("=REDACTED");
            if (out.length() > 512) break;
        }
        return out.toString();
    }
}
