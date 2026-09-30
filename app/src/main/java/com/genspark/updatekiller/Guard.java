package com.genspark.updatekiller;

import java.util.concurrent.atomic.AtomicInteger;

/* loaded from: classes.dex */
public final class Guard {
    private static final AtomicInteger FAILURES = new AtomicInteger(0);
    private static volatile int threshold = 20;
    private static volatile boolean tripped = false;

    public interface Action {
        void run() throws Throwable;
    }

    public interface Func<T> {
        T run() throws Throwable;
    }

    private Guard() {
    }

    public static boolean isTripped() {
        return tripped;
    }

    public static int failures() {
        return FAILURES.get();
    }

    public static void setThreshold(int i) {
        if (i > 0) {
            threshold = i;
        }
    }

    public static void run(String str, Action action) {
        if (tripped || action == null) {
            return;
        }
        try {
            action.run();
        } catch (Throwable th) {
            record(str, th);
        }
    }

    public static <T> T value(String str, Func<T> func, T t) {
        if (tripped || func == null) {
            return t;
        }
        try {
            return func.run();
        } catch (Throwable th) {
            record(str, th);
            return t;
        }
    }

    public static void record(String str, Throwable th) {
        try {
            int incrementAndGet = FAILURES.incrementAndGet();
            UxLog.w("Guard/" + str + " → " + th.getClass().getSimpleName() + ": " + th.getMessage());
            if (incrementAndGet < threshold || tripped) {
                return;
            }
            tripped = true;
            UxLog.e("Kill-Switch tripped after " + incrementAndGet + " failures — all hooks disabled to protect the app");
        } catch (Throwable unused) {
        }
    }

    public static void reset() {
        FAILURES.set(0);
        tripped = false;
    }
}
