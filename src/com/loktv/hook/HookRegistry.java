package com.loktv.hook;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * v2.2.0: deduplicates hooks across Engine re-applies.
 * Packed apps are applied up to 3 times (late dex); without this registry
 * the same Method would be hooked twice, stacking replacements and
 * wasting time. Keys are class#name+signature strings. Never throws.
 */
public final class HookRegistry {

    private static final Set<String> sHooked = new HashSet<String>();

    /** Returns true if this method was already hooked (caller should skip). */
    public static synchronized boolean alreadyHooked(Method m) {
        if (m == null) return true;
        return sHooked.contains(key(m));
    }

    /** Marks a method as hooked. */
    public static synchronized void markHooked(Method m) {
        if (m == null) return;
        sHooked.add(key(m));
        if (sHooked.size() > 20000) {
            // safety cap: drop oldest half (order not guaranteed, acceptable)
            int i = 0;
            java.util.Iterator<String> it = sHooked.iterator();
            while (it.hasNext() && i < 10000) {
                it.next();
                it.remove();
                i++;
            }
        }
    }

    /** Number of unique hooked methods (for the summary log). */
    public static synchronized int size() {
        return sHooked.size();
    }

    private static String key(Method m) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(m.getDeclaringClass().getName()).append('#').append(m.getName());
            Class<?>[] ps = m.getParameterTypes();
            sb.append('(').append(ps == null ? 0 : ps.length).append(')');
            for (int i = 0; ps != null && i < ps.length; i++) {
                sb.append(':').append(ps[i] == null ? "?" : ps[i].getName());
            }
            Class<?> r = m.getReturnType();
            sb.append("->").append(r == null ? "?" : r.getName());
            return sb.toString();
        } catch (Throwable t) {
            try {
                return String.valueOf(m);
            } catch (Throwable ignored) {
                return "unknown";
            }
        }
    }

    private HookRegistry() {}
}
