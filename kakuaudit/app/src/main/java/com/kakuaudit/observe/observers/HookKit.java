package com.kakuaudit.observe.observers;

import com.kakuaudit.observe.core.EventBus;
import com.kakuaudit.observe.core.KakuClock;
import com.kakuaudit.observe.core.ProtectedComponentRegistry;
import com.kakuaudit.observe.core.Redactor;
import com.kakuaudit.observe.core.ReflectionDiscovery;
import com.kakuaudit.observe.core.SafeGuard;
import com.kakuaudit.observe.report.Jsons;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Shared helper: hook discovered methods via XposedBridge.hookMethod using
 * reflection only (no static target names). afterHookedMethod logs metadata;
 * never modifies args/result (OBSERVE_ONLY).
 *
 * Xposed types are referenced reflectively so the module still compiles and
 * unit-tests on JVM without the Xposed jar at runtime.
 */
public final class HookKit {
    private HookKit() {}

    public interface AfterCall {
        void after(String className, String methodName, Object returnValue, long seq);
    }

    /**
     * Discover behavioral candidates in an already-loaded class, then hook each.
     * Returns number of hooked methods; records PROTECTED on failure.
     */
    public static int hookDiscovered(Class<?> clazz, ReflectionDiscovery.Query q,
                                     ClassLoader appLoader,
                                     ProtectedComponentRegistry prot,
                                     AtomicLong seq, EventBus bus,
                                     AfterCall after) {
        final List<ReflectionDiscovery.Hit> hits =
                ReflectionDiscovery.filterMethods(clazz, q);
        int hooked = 0;
        for (ReflectionDiscovery.Hit h : hits) {
            final String tag = "hook:" + clazz.getName() + "#" + h.method.getName();
            if (SafeGuard.isKilled(tag)) continue;
            boolean ok = SafeGuard.runSafe(tag, prot, () ->
                    hookMethod(h.method, clazz.getName(), h.method.getName(),
                            seq, bus, after));
            if (ok) hooked++;
            if (hooked >= 25) break; // bound: avoid over-hooking hot methods.
        }
        return hooked;
    }

    /**
     * Hook via the real XC_MethodHook subclass (XposedHookBridge), loaded
     * reflectively so JVM tests / non-Xposed processes never touch the API.
     * Never throws; returns false when Xposed is absent or the hook is
     * rejected. OBSERVE_ONLY: after-callback reads only, never writes.
     */
    public static boolean hookMethod(java.lang.reflect.Member m,
                                     final String className, final String methodName,
                                     final AtomicLong seq, final EventBus bus,
                                     final AfterCall after) {
        if (m == null || bus == null) return false;
        try {
            Class<?> bridge = Class.forName(
                    "com.kakuaudit.observe.observers.XposedHookBridge");
            java.lang.reflect.Method hook = bridge.getMethod("hook",
                    java.lang.reflect.Member.class, String.class, String.class,
                    AtomicLong.class, EventBus.class, AfterCall.class);
            Object unhook = hook.invoke(null, m, className, methodName,
                    seq, bus, after);
            return unhook != null;
        } catch (Throwable t) {
            return false; // Xposed absent (unit test) or hook rejected.
        }
    }

    static void emitMethodCall(EventBus bus, String clazz, String method, Object ret) {
        if (bus == null) return;
        String evt = UUID.randomUUID().toString();
        StringBuilder sb = new StringBuilder(384);
        sb.append("{");
        sb.append(Jsons.kv("schemaVersion", Jsons.q("3.0"))).append(",");
        sb.append(Jsons.kv("eventId", Jsons.q(evt))).append(",");
        sb.append(Jsons.kv("eventType", Jsons.q("METHOD_CALL"))).append(",");
        sb.append(Jsons.kv("class", Jsons.q(clazz))).append(",");
        sb.append(Jsons.kv("method", Jsons.q(method))).append(",");
        sb.append(Jsons.kv("observedAt", Jsons.q(KakuClock.utcNowIso()))).append(",");
        sb.append(Jsons.kv("monotonicTimeNanos", String.valueOf(KakuClock.monotonicNanos()))).append(",");
        // Return TYPE only, never raw value.
        String rt = ret == null ? "null" : ret.getClass().getName();
        sb.append(Jsons.kv("returnType", Jsons.q(rt)));
        sb.append("}");
        bus.publish(sb.toString(), System.nanoTime());
    }

    public static ReflectionDiscovery.Query q(String methodRegex) {
        ReflectionDiscovery.Query q = new ReflectionDiscovery.Query();
        if (methodRegex != null) q.methodName = Pattern.compile(methodRegex);
        return q;
    }
}
