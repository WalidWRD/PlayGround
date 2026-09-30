package com.genspark.updatekiller;

import com.genspark.updatekiller.Guard;

/* loaded from: classes.dex */
public final class Guardian {
    private Guardian() {
    }

    public static void record(String str, Throwable th) {
        Guard.record(str, th);
    }

    public static void run(String str, Guard.Action action) {
        Guard.run(str, action);
    }

    public static <T> T value(String str, Guard.Func<T> func, T t) {
        return (T) Guard.value(str, func, t);
    }

    public static boolean isTripped() {
        return Guard.isTripped();
    }
}
