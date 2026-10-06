package com.kakuaudit.observe.core;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Spec §7.3: path safety + §8 session-id with entropy. */
public final class PathSafety {
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._-]{1,128}$");
    private static final Pattern PKG = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$");

    private PathSafety() {}

    public static String normalizeSegment(String raw, String fallback) {
        if (raw == null) return fallback;
        // Reject traversal / separators / control chars.
        if (raw.contains("/") || raw.contains("\\") || raw.contains("..")
                || raw.contains("\0") || raw.contains("\n") || raw.length() > 128) {
            return fallback;
        }
        String s = raw.trim();
        if (!SAFE.matcher(s).matches()) {
            // Map unsafe -> deterministic safe token.
            int h = Math.abs(s.hashCode());
            return fallback + "-" + h;
        }
        return s;
    }

    public static boolean isPackageSafe(String pkg) {
        return pkg != null && pkg.length() <= 255 && PKG.matcher(pkg).matches();
    }

    /** e.g. 20261006-113500-a84f2c-7d91 : timestamp + entropy, never overwritten. */
    public static String newSessionId() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        java.text.SimpleDateFormat f =
                new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
        f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        String ts = f.format(new java.util.Date());
        return ts + "-" + uuid.substring(0, 6) + "-" + uuid.substring(6, 10);
    }
}
