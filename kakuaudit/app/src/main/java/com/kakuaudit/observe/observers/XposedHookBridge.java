package com.kakuaudit.observe.observers;

import com.kakuaudit.observe.core.EventBus;
import com.kakuaudit.observe.core.SafeGuard;

import java.lang.reflect.Member;
import java.util.concurrent.atomic.AtomicLong;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Real Xposed bridge (compileOnly API, provided on-device by
 * LSPosed / LSPatch / NPatch / HKPatch — never bundled).
 *
 * Loaded ONLY via reflection from HookKit when the API exists, so JVM unit
 * tests and non-Xposed processes never touch it.
 *
 * Crash-safety: ThreadLocal reentrancy guard (a callback that re-triggers
 * its own hook — e.g. Runtime.load* — returns immediately instead of
 * recursing), plus SafeGuard isolation so no callback exception can ever
 * reach the target app. OBSERVE_ONLY: reads getResult(), never writes.
 */
public final class XposedHookBridge {
    private XposedHookBridge() {}

    /** Reentrancy guard: one active callback per thread. */
    private static final ThreadLocal<Boolean> IN_CALLBACK =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    static final class ObserveHook extends XC_MethodHook {
        private final String className;
        private final String methodName;
        private final AtomicLong seq;
        private final EventBus bus;
        private final HookKit.AfterCall after;

        ObserveHook(String className, String methodName,
                    AtomicLong seq, EventBus bus, HookKit.AfterCall after) {
            this.className = className;
            this.methodName = methodName;
            this.seq = seq;
            this.bus = bus;
            this.after = after;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            // Recursion guard — the #1 classic Xposed self-crash.
            if (Boolean.TRUE.equals(IN_CALLBACK.get())) return;
            IN_CALLBACK.set(Boolean.TRUE);
            try {
                SafeGuard.runSafe("cb:" + className + "#" + methodName, null, () -> {
                    Object res = null;
                    try {
                        res = param.getResult(); // READ ONLY — never setResult.
                    } catch (Throwable ignore) { /* unreadable result: log type only */ }
                    final Object r = res;
                    long s = seq.incrementAndGet();
                    if (after != null) {
                        SafeGuard.runSafe("after:" + className + "#" + methodName,
                                null, () -> after.after(className, methodName, r, s));
                    }
                    HookKit.emitMethodCall(bus, className, methodName, r);
                });
            } finally {
                IN_CALLBACK.set(Boolean.FALSE);
            }
        }
    }

    /** Returns the Unhook handle, or null on failure. Never throws. */
    public static XC_MethodHook.Unhook hook(Member m, String className,
                                            String methodName, AtomicLong seq,
                                            EventBus bus, HookKit.AfterCall after) {
        if (m == null || bus == null) return null;
        try {
            return XposedBridge.hookMethod(m,
                    new ObserveHook(className, methodName, seq, bus, after));
        } catch (Throwable t) {
            SafeGuard.runSafe("hook-fail:" + className + "#" + methodName,
                    null, () -> { throw t; });
            return null;
        }
    }
}
