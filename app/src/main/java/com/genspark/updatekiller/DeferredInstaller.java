package com.genspark.updatekiller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * جديد 2.0.0 — مُثبِّت مُؤجَّل متعدّد المرور.
 *
 * السبب: في التطبيقات **المضغوطة/المحمية/المشوّشة** تُحمَّل كثير من الفئات بعد الإقلاع،
 * وفي التطبيقات التي تستخدم ClassLoader مُخصّصًا قد لا تكون الفئة موجودة في أول لحظة.
 * لذلك لا نُثبّت الخطّافات مرة واحدة ثم نستسلم، بل:
 *   1) نُنفّذ مهام التثبيت في عدّة مرورات زمنية (0، 0.4s، 1.5s، 4s، 10s، 25s، 60s).
 *   2) ونُعيد المحاولة فورًا عند كل فئة «مُهمّة» تُحمَّل (عبر Discovery.loadClass observer).
 *   3) وكل مهمة تُعلَن «منجزة» فقط عند نجاح ربط خطّاف واحد على الأقل.
 */
public final class DeferredInstaller {

    public interface Task {
        String name();
        int run(ClassLoader cl);
    }

    private static final List<Task> TASKS = Collections.synchronizedList(new ArrayList<Task>());
    private static final Set<String> DONE = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final long[] PASSES = {0L, 400L, 1500L, 4000L, 10000L, 25000L, 60000L};

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "GensparkInstaller");
            t.setDaemon(true);
            return t;
        }
    });

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean KICK_QUEUED = new AtomicBoolean(false);
    private static volatile ClassLoader loader;
    private static volatile int passesRun = 0;
    private static volatile Runnable onComplete;

    private DeferredInstaller() { }

    public static synchronized void add(Task t) {
        if (t == null) return;
        TASKS.add(t);
    }

    public static int passesRun() { return passesRun; }

    public static String pendingList() {
        StringBuilder sb = new StringBuilder();
        try {
            synchronized (TASKS) {
                for (Task t : TASKS) {
                    if (DONE.contains(t.name())) continue;
                    if (sb.length() > 0) sb.append(',');
                    sb.append(t.name());
                }
            }
        } catch (Throwable ignored) { }
        return sb.toString();
    }

    public static void start(ClassLoader cl, Runnable completeCallback) {
        loader = cl;
        onComplete = completeCallback;
        if (!STARTED.compareAndSet(false, true)) return;
        EXEC.execute(new Runnable() {
            @Override public void run() {
                long prev = 0L;
                for (int i = 0; i < PASSES.length; i++) {
                    long wait = PASSES[i] - prev;
                    prev = PASSES[i];
                    if (wait > 0) {
                        try { Thread.sleep(wait); } catch (Throwable ignored) { }
                    }
                    pass(i);
                    if (allDone()) break;
                }
                passesRun = PASSES.length;
                try {
                    Runnable cb = onComplete;
                    if (cb != null) cb.run();
                } catch (Throwable t) { Guard.record("DeferredInstaller.complete", t); }
            }
        });
    }

    /** إعادة محاولة فورية عند تحميل فئة مُهمّة (لا تتكدّس: نداء واحد لكل دورة). */
    public static void kick(String reason) {
        if (!STARTED.get()) return;
        if (!KICK_QUEUED.compareAndSet(false, true)) return;
        EXEC.execute(new Runnable() {
            @Override public void run() {
                KICK_QUEUED.set(false);
                if (allDone()) return;
                UxLog.d("DeferredInstaller: retry triggered (" + reason + ")");
                runPending(-1);
            }
        });
    }

    private static void pass(int idx) {
        UxLog.d("DeferredInstaller: pass " + (idx + 1) + "/" + PASSES.length);
        runPending(idx);
    }

    private static void runPending(int idx) {
        ClassLoader cl = loader;
        if (cl == null) return;
        Task[] arr;
        synchronized (TASKS) { arr = TASKS.toArray(new Task[0]); }
        for (Task t : arr) {
            if (DONE.contains(t.name())) continue;
            if (Guard.isTripped()) return;
            final int[] got = new int[1];
            final Task task = t;
            Guard.run("DeferredInstaller/" + t.name(), new Guard.Action() {
                @Override public void run() {
                    got[0] = task.run(cl);
                }
            });
            if (got[0] > 0) {
                DONE.add(t.name());
                SelfTest.addHooks(t.name(), got[0]);
                UxLog.i("DeferredInstaller: '" + t.name() + "' installed (" + got[0] + " hook(s)) at pass " + (idx + 1));
            } else if (idx >= PASSES.length - 1) {
                SelfTest.record("unresolved." + t.name(), 0);
                UxLog.w("DeferredInstaller: '" + t.name() + "' unresolved after all passes (0 hooks)");
            }
        }
    }

    private static boolean allDone() {
        synchronized (TASKS) {
            for (Task t : TASKS) if (!DONE.contains(t.name())) return false;
        }
        return true;
    }
}
