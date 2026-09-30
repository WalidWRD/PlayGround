package com.genspark.updatekiller;

/** مُعرَّف متوافق: كل نداءات الحماية تمرّ من هنا إلى Guard. */
public final class Guardian {
    private Guardian() { }
    public static void record(String where, Throwable t) { Guard.record(where, t); }
    public static void run(String where, Guard.Action a) { Guard.run(where, a); }
    public static <T> T value(String where, Guard.Func<T> f, T fallback) { return Guard.value(where, f, fallback); }
    public static boolean isTripped() { return Guard.isTripped(); }
}
