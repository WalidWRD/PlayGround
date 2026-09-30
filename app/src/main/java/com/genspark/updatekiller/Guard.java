package com.genspark.updatekiller;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * حارس الأمان: يلفّ كل عمل خطّاف حتى لا يتسرّب أي استثناء إلى التطبيق الهدف.
 * - أي استثناء يُسجَّل ولا يُرمى.
 * - مفتاح إيقاف تلقائي (Kill-Switch): بعد العتبة المُهيَّأة (killSwitchThreshold، افتراضي 20)
 *   فشلًا تُعطَّل كل الخطّافات تلقائيًا
 *   لضمان ألا يكسر الموديول التطبيق أبدًا.
 */
public final class Guard {

    public interface Action { void run() throws Throwable; }
    public interface Func<T> { T run() throws Throwable; }

    private static final AtomicInteger FAILURES = new AtomicInteger(0);
    private static volatile int threshold = 20;
    private static volatile boolean tripped = false;

    private Guard() { }

    public static boolean isTripped() { return tripped; }
    public static int failures() { return FAILURES.get(); }

    /** تحديث عتبة مفتاح الإيقاف من الإعدادات (Config). */
    public static void setThreshold(int t) { if (t > 0) threshold = t; }

    /** ينفّذ عملًا محميًا. لا يرمي أبدًا. */
    public static void run(String where, Action a) {
        if (tripped) return;
        try { a.run(); }
        catch (Throwable t) { record(where, t); }
    }

    /** ينفّذ دالة محمية ويُعيد القيمة الافتراضية عند أي فشل. لا يرمي أبدًا. */
    public static <T> T value(String where, Func<T> f, T fallback) {
        if (tripped) return fallback;
        try { return f.run(); }
        catch (Throwable t) { record(where, t); return fallback; }
    }

    /** يعترض أي Throwable من كود الـhook. يُستدعى من داخل before/afterHookedMethod. */
    public static void record(String where, Throwable t) {
        try {
            int n = FAILURES.incrementAndGet();
            UxLog.w("Guard/" + where + " → " + t.getClass().getSimpleName() + ": " + t.getMessage());
            if (n >= threshold && !tripped) {
                tripped = true;
                UxLog.e("Kill-Switch tripped after " + n + " failures — all hooks disabled to protect the app");
            }
        } catch (Throwable ignored) { }
    }

    /** يعيد ضبط الحارس (للتشخيص فقط). */
    public static void reset() { FAILURES.set(0); tripped = false; }
}
