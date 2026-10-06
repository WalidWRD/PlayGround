package com.kakuaudit.observe.core;

/** Spec §12: wall-clock (human) + monotonic nanos (ordering authority). */
public final class KakuClock {
    private KakuClock() {}

    public static String utcNowIso() {
        java.text.SimpleDateFormat f =
                new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                        java.util.Locale.US);
        f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return f.format(new java.util.Date());
    }

    /** Monotonic source for ordering/durations. Never use wall clock for causality. */
    public static long monotonicNanos() {
        return System.nanoTime();
    }

    public static String clockSource() {
        return "System.nanoTime+UTC";
    }
}
