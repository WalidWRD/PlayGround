package com.kakuaudit.observe.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Crash-safety gate: every hook / observer / background task runs isolated.
 * - No exception ever propagates to the target app.
 * - Per-tag failure counting + kill-switch (disable noisy observer after threshold).
 * - Background threads are daemon + UncaughtExceptionHandler + named.
 *
 * Maintainer note: wrap ALL new hooks with SafeGuard.runSafe() and ALL new
 * threads with SafeGuard.thread(). See MAINTENANCE.md.
 */
public final class SafeGuard {
    private static final int KILL_THRESHOLD = 20;
    private static final Map<String, AtomicInteger> failures = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> killed = new ConcurrentHashMap<>();

    private SafeGuard() {}

    public interface SafeRun { void run() throws Throwable; }

    /** Returns false if the tag was killed or the run failed (never throws). */
    public static boolean runSafe(String tag, ProtectedComponentRegistry prot, SafeRun r) {
        if (isKilled(tag)) return false;
        try {
            r.run();
            return true;
        } catch (Throwable t) {
            int n = failures.computeIfAbsent(tag, k -> new AtomicInteger()).incrementAndGet();
            if (prot != null) {
                prot.record(tag, "FAILED",
                        t.getClass().getSimpleName() + "#fail" + n);
            }
            log("SafeGuard[" + tag + "] fail#" + n + ": " + t.getClass().getSimpleName());
            if (n >= KILL_THRESHOLD) {
                killed.put(tag, Boolean.TRUE);
                log("SafeGuard[" + tag + "] KILLED (kill-switch) to protect target app.");
            }
            return false;
        }
    }

    public static boolean isKilled(String tag) {
        return Boolean.TRUE.equals(killed.get(tag));
    }

    public static void resetForTests() {
        failures.clear();
        killed.clear();
    }

    /** Daemon thread that can never crash the app. */
    public static Thread thread(String name, SafeRun r,
                                ProtectedComponentRegistry prot) {
        Thread t = new Thread(() -> runSafe("thread:" + name, prot, r), name);
        t.setDaemon(true);
        t.setUncaughtExceptionHandler((th, e) ->
                log("Uncaught in " + th.getName() + ": " + e.getClass().getSimpleName()));
        return t;
    }

    static void log(String s) {
        try {
            android.util.Log.w("KakuAudit", s);
        } catch (Throwable ignore) {}
        // Also mirror to Xposed log when present (boot log visibility).
        try {
            Class<?> b = Class.forName("de.robv.android.xposed.XposedBridge");
            b.getMethod("log", String.class).invoke(null, "[KakuAudit] " + s);
        } catch (Throwable ignore) {}
    }

    /** For unit tests. */
    public static int failureCount(String tag) {
        AtomicInteger a = failures.get(tag);
        return a == null ? 0 : a.get();
    }
}
